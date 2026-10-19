package com.example.triqx.utils

import com.example.triqx.data.local.ContactEntity
import java.net.URLDecoder
import java.text.Normalizer

/**
 * Matches an incoming notification to a priority (VIP) contact.
 *
 * Replaces the old substring matching, which produced frequent false positives:
 *  - "Raj" matched "Rajesh", "Rajkot Traders", "Suraj"... (plain `contains`)
 *  - any message whose BODY merely mentioned a VIP's name/number/email ("tell Raj I said hi")
 *    was treated as coming from that VIP
 *  - phone numbers only matched if formatted identically ("+91 98765 43210" vs "9876543210" failed)
 *  - lookup keys matched with `contains`, so a short key could match unrelated URIs
 *
 * Every signal is scored; the highest-scoring contact above [MIN_SCORE] wins. Strong
 * identifiers (contact URI, email, phone) beat names, and names must match whole words.
 * Pure Kotlin (no android.* imports) so it can be unit tested on the JVM.
 */
object ContactMatcher {

    data class Match(val contact: ContactEntity, val score: Int, val reason: String)

    private const val MIN_SCORE = 40

    private const val SCORE_LOOKUP_URI = 100
    private const val SCORE_EMAIL_SENDER = 90
    private const val SCORE_PHONE = 80
    private const val SCORE_NAME_EXACT_TITLE = 70
    private const val SCORE_NAME_EXACT_SENDER = 70
    private const val SCORE_NAME_TITLE_SEGMENT = 60
    private const val SCORE_NAME_BODY_PREFIX = 50
    private const val SCORE_EMAIL_IN_TEXT = 45
    private const val SCORE_NAME_WORD_IN_TITLE = 40

    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    // Runs of digits allowing common phone separators: +91 98765-43210, (022) 2345 6789
    private val PHONE_REGEX = Regex("""\+?\d[\d\s\-().]{6,}\d""")
    // WhatsApp/Telegram title decorations: "Name (3 messages)", "Name • 2 new"
    private val TITLE_COUNTER_REGEX = Regex("""\(\s*\d+\s+[^)]*\)|[•·]\s*\d+\s+\w+""")
    private val NON_WORD_REGEX = Regex("""[^\p{L}\p{M}\p{N}]+""")
    // Group/channel separators: "Family: Raj", "Raj @ Project X", "Raj | Team", "Raj - Team"
    private val TITLE_SEGMENT_SPLIT = Regex("""\s*[:@|•·]\s*|\s+[-–—]\s+""")

    /**
     * @param title notification title / conversation title
     * @param text notification body text (only its "Sender:" prefix is used for names)
     * @param senderNames names of the latest message sender(s) from MessagingStyle, if any
     * @param senderEmails emails from Person URIs / mailto: people
     * @param contactLookupUris content://com.android.contacts/... URIs attached to the notification
     */
    fun findBestMatch(
        contacts: List<ContactEntity>,
        title: String?,
        text: String?,
        senderNames: Collection<String> = emptyList(),
        senderEmails: Collection<String> = emptyList(),
        contactLookupUris: Collection<String> = emptyList()
    ): Match? {
        if (contacts.isEmpty()) return null

        val normTitle = normalizeName(stripTitleDecorations(title))
        val titleSegments = stripTitleDecorations(title)
            .split(TITLE_SEGMENT_SPLIT)
            .map { normalizeName(it) }
            .filter { it.isNotEmpty() }
        val normSenders = senderNames.map { normalizeName(it) }.filter { it.isNotEmpty() }.toSet()
        val bodyPrefix = text?.substringBefore('\n')?.let { line ->
            val idx = line.indexOf(':')
            if (idx in 1..60) normalizeName(line.substring(0, idx)) else null
        }

        val strongEmails = (senderEmails + extractEmails(title)).map { it.lowercase().trim() }.toSet()
        val textEmails = extractEmails(text).map { it.lowercase().trim() }.toSet()
        val phoneCandidates = (extractPhones(title) + senderNames.flatMap { extractPhones(it) }).toSet()
        // Keep both the raw (URL-encoded) segment and its decoded form; stored keys may be either.
        val lookupKeys = contactLookupUris.flatMap { uri ->
            listOfNotNull(extractLookupKey(uri, decode = false), extractLookupKey(uri, decode = true))
        }.toSet()

        var best: Match? = null
        for (contact in contacts) {
            val m = scoreContact(
                contact, normTitle, titleSegments, normSenders, bodyPrefix,
                strongEmails, textEmails, phoneCandidates, lookupKeys
            ) ?: continue
            if (best == null || m.score > best.score) best = m
        }
        return best?.takeIf { it.score >= MIN_SCORE }
    }

    private fun scoreContact(
        contact: ContactEntity,
        normTitle: String,
        titleSegments: List<String>,
        normSenders: Set<String>,
        bodyPrefix: String?,
        strongEmails: Set<String>,
        textEmails: Set<String>,
        phoneCandidates: Set<String>,
        lookupKeys: Set<String>
    ): Match? {
        var bestScore = 0
        var reason = ""
        fun consider(score: Int, why: String) {
            if (score > bestScore) { bestScore = score; reason = why }
        }

        // 1. Contact lookup key (exact path segment, never substring)
        val key = contact.lookupKey?.trim()
        if (!key.isNullOrEmpty() && lookupKeys.any { it == key }) consider(SCORE_LOOKUP_URI, "lookupUri")

        // 2. Email — exact address equality
        val contactEmails = contact.emails.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (contactEmails.any { it in strongEmails }) consider(SCORE_EMAIL_SENDER, "email")
        else if (contactEmails.any { it in textEmails }) consider(SCORE_EMAIL_IN_TEXT, "emailInText")

        // 3. Phone — compare normalized digits, tolerant of country code / formatting
        if (contact.phoneNumbers.any { p -> phoneCandidates.any { c -> phonesEqual(p, c) } }) {
            consider(SCORE_PHONE, "phone")
        }

        // 4. Names (display + official), whole-word only
        val names = listOfNotNull(contact.displayName, contact.officialName)
            .map { normalizeName(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        for (name in names) {
            when {
                normTitle == name -> consider(SCORE_NAME_EXACT_TITLE, "titleExact")
                name in normSenders -> consider(SCORE_NAME_EXACT_SENDER, "senderExact")
                titleSegments.size > 1 && titleSegments.any { it == name } -> consider(SCORE_NAME_TITLE_SEGMENT, "titleSegment")
                bodyPrefix != null && bodyPrefix == name -> consider(SCORE_NAME_BODY_PREFIX, "bodyPrefix")
                // Whole-word phrase inside the title, only for reasonably specific names
                name.length >= 3 && containsWholeWords(normTitle, name) -> consider(SCORE_NAME_WORD_IN_TITLE, "titleWord")
            }
        }

        return if (bestScore > 0) Match(contact, bestScore, reason) else null
    }

    // ---------------- Normalization helpers (internal for tests) ----------------

    /** Lowercase, Unicode-compatibility-normalized, punctuation/emoji collapsed to single spaces.
     *  Combining marks are KEPT so Devanagari and other Indic names are not mangled. */
    internal fun normalizeName(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return Normalizer.normalize(s, Normalizer.Form.NFKC)
            .lowercase()
            .replace(NON_WORD_REGEX, " ")
            .trim()
    }

    internal fun stripTitleDecorations(title: String?): String =
        title.orEmpty().replace(TITLE_COUNTER_REGEX, " ").replace("~", " ").trim()

    internal fun containsWholeWords(haystack: String, needle: String): Boolean {
        if (haystack.isEmpty() || needle.isEmpty()) return false
        // Both already normalized to space-separated tokens, so pad and search for the token sequence.
        return " $haystack ".contains(" $needle ")
    }

    internal fun digitsOnly(s: String): String = s.filter { it.isDigit() }

    /**
     * Phones match when their significant digits match:
     *  - both have >= 10 digits -> compare the last 10 (ignores +91 / 0 / 0091 prefixes)
     *  - otherwise require full equality with at least 7 digits (landlines, short numbers)
     */
    internal fun phonesEqual(a: String, b: String): Boolean {
        val da = digitsOnly(a)
        val db = digitsOnly(b)
        if (da.length < 7 || db.length < 7) return false
        return if (da.length >= 10 && db.length >= 10) da.takeLast(10) == db.takeLast(10) else da == db
    }

    internal fun extractEmails(s: String?): List<String> =
        if (s.isNullOrBlank()) emptyList() else EMAIL_REGEX.findAll(s).map { it.value }.toList()

    internal fun extractPhones(s: String?): List<String> =
        if (s.isNullOrBlank()) emptyList()
        else PHONE_REGEX.findAll(s).map { it.value }.filter { digitsOnly(it).length >= 7 }.toList()

    /** content://com.android.contacts/contacts/lookup/<key>/<id> -> <key> (URL-decoded). */
    internal fun extractLookupKey(uri: String, decode: Boolean = true): String? {
        val segments = uri.substringAfter("://", uri).split('/')
        val idx = segments.indexOf("lookup")
        if (idx < 0 || idx + 1 >= segments.size) return null
        val raw = segments[idx + 1]
        if (raw.isBlank()) return null
        if (!decode) return raw
        return try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }
}
