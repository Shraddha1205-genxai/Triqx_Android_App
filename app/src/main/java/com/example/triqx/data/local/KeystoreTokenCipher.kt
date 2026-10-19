package com.example.triqx.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable key held in the Android Keystore (hardware-backed where available).
 * Output format: Base64(iv[12] || ciphertext+tag).
 *
 * `androidx.security:security-crypto` (EncryptedSharedPreferences) is deprecated; this is the small,
 * dependency-free replacement.
 */
class KeystoreTokenCipher(private val alias: String = "triqx_token_key_v2") : TokenCipher {

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(iv + ciphertext)
        } catch (e: Exception) {
            // Keystore failures here are transient far more often than not (locked, busy, daemon restart).
            throw TokenCipherException("encrypt failed: ${e.message}", permanent = false, cause = e)
        }
    }

    override fun decrypt(blob: String): String {
        val raw: ByteArray
        try {
            raw = Base64.getDecoder().decode(blob)
        } catch (e: IllegalArgumentException) {
            throw TokenCipherException("stored token is not valid base64", permanent = true, cause = e)
        }
        if (raw.size <= IV_SIZE) {
            throw TokenCipherException("stored token is truncated", permanent = true)
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, raw, 0, IV_SIZE))
            return String(cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            // Wrong key or corrupted data: this ciphertext can never be recovered.
            throw TokenCipherException("token authentication failed (key changed or data corrupt)", permanent = true, cause = e)
        } catch (e: GeneralSecurityException) {
            throw TokenCipherException("decrypt failed: ${e.message}", permanent = false, cause = e)
        } catch (e: Exception) {
            throw TokenCipherException("decrypt failed: ${e.message}", permanent = false, cause = e)
        }
    }
}
