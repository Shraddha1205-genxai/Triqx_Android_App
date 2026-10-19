package com.example.triqx.data.local

/**
 * Standard categories for tracked obligations and priorities.
 */
enum class ObligationCategory(val displayName: String) {
    Payment("Payment"),
    Work("Work"),
    Personal("Personal"),
    Security("Security"),
    Health("Health"),
    Other("Other");

    companion object {
        fun fromString(value: String?): ObligationCategory {
            if (value.isNullOrBlank()) return Other
            return entries.firstOrNull {
                it.name.equals(value.trim(), ignoreCase = true) ||
                it.displayName.equals(value.trim(), ignoreCase = true)
            } ?: Other
        }
    }
}

/**
 * Priority and urgency levels for tracked obligations.
 */
enum class ObligationUrgency(val displayName: String) {
    Critical("Critical"),
    High("High"),
    Medium("Medium"),
    Low("Low");

    companion object {
        fun fromString(value: String?): ObligationUrgency {
            if (value.isNullOrBlank()) return Medium
            return entries.firstOrNull {
                it.name.equals(value.trim(), ignoreCase = true) ||
                it.displayName.equals(value.trim(), ignoreCase = true)
            } ?: Medium
        }
    }
}
