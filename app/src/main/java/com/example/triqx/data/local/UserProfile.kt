package com.example.triqx.data.local

/**
 * Data class representing a user profile.
 *
 * @param firstName User's first name (mandatory for completing setup)
 * @param lastName User's last name (optional)
 * @param phoneNumbers List of phone numbers (first is the verified login number)
 * @param emails List of email addresses (optional)
 * @param aboutMe Bio / summary about the user (optional)
 * @param professionalDetails Job title, role, company, or organization (optional)
 * @param isFirstLogin True if the user has just logged in and has not completed profile setup
 */
data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val aboutMe: String = "",
    val professionalDetails: String = "",
    val isFirstLogin: Boolean = true
) {
    val fullName: String
        get() {
            val parts = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }
            return if (parts.isEmpty()) "User" else parts.joinToString(" ")
        }

    val primaryPhone: String
        get() = phoneNumbers.firstOrNull() ?: ""

    val primaryEmail: String?
        get() = emails.firstOrNull()

    val initials: String
        get() {
            val f = firstName.trim().firstOrNull()?.uppercaseChar()
            val l = lastName.trim().firstOrNull()?.uppercaseChar()
            return when {
                f != null && l != null -> "$f$l"
                f != null -> "$f"
                l != null -> "$l"
                else -> "U"
            }
        }
}
