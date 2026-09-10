package com.example.triqx.service.email

/**
 * Supported email providers for 1-stage AI notification replies.
 */
enum class EmailProvider(
    val id: String,
    val displayName: String,
    val supportedPackages: List<String>
) {
    GMAIL(
        id = "GMAIL",
        displayName = "Google / Gmail",
        supportedPackages = listOf("com.google.android.gm")
    ),
    OUTLOOK(
        id = "OUTLOOK",
        displayName = "Microsoft / Outlook",
        supportedPackages = listOf("com.microsoft.office.outlook")
    );

    companion object {
        fun fromString(value: String?): EmailProvider = when (value?.uppercase()?.trim()) {
            "OUTLOOK" -> OUTLOOK
            else -> GMAIL
        }

        fun fromPackageName(packageName: String): EmailProvider? {
            return entries.firstOrNull { provider ->
                provider.supportedPackages.any { pkg ->
                    packageName.contains(pkg, ignoreCase = true) || pkg.contains(packageName, ignoreCase = true)
                }
            } ?: when {
                packageName.contains("outlook", ignoreCase = true) -> OUTLOOK
                packageName.contains("gm", ignoreCase = true) -> GMAIL
                else -> null
            }
        }
    }
}
