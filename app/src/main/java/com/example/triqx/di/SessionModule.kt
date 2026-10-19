package com.example.triqx.di

import android.content.Context
import com.example.triqx.data.local.KeystoreTokenCipher
import com.example.triqx.data.local.SecureTokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideSecureTokenStore(@ApplicationContext context: Context): SecureTokenStore =
        SecureTokenStore(
            prefs = context.getSharedPreferences("triqx_tokens_v2", Context.MODE_PRIVATE),
            cipher = KeystoreTokenCipher()
        )
}
