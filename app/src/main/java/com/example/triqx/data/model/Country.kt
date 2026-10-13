package com.example.triqx.data.model

data class Country(
    val name: String,
    val code: String,       // e.g. "+91"
    val isoCode: String,    // e.g. "IN"
    val flagEmoji: String   // e.g. "🇮🇳"
)

object CountryList {
    val defaultCountry = Country(
        name = "India",
        code = "+91",
        isoCode = "IN",
        flagEmoji = "🇮🇳"
    )

    val countries: List<Country> = listOf(
        Country("India", "+91", "IN", "🇮🇳"),
        Country("United States", "+1", "US", "🇺🇸"),
        Country("United Kingdom", "+44", "GB", "🇬🇧"),
        Country("Canada", "+1", "CA", "🇨🇦"),
        Country("Australia", "+61", "AU", "🇦🇺"),
        Country("United Arab Emirates", "+971", "AE", "🇦🇪"),
        Country("Saudi Arabia", "+966", "SA", "🇸🇦"),
        Country("Singapore", "+65", "SG", "🇸🇬"),
        Country("Germany", "+49", "DE", "🇩🇪"),
        Country("France", "+33", "FR", "🇫🇷"),
        Country("Japan", "+81", "JP", "🇯🇵"),
        Country("China", "+86", "CN", "🇨🇳"),
        Country("Brazil", "+55", "BR", "🇧🇷"),
        Country("South Africa", "+27", "ZA", "🇿🇦"),
        Country("Russia", "+7", "RU", "🇷🇺"),
        Country("Indonesia", "+62", "ID", "🇮🇩"),
        Country("Malaysia", "+60", "MY", "🇲🇾"),
        Country("Netherlands", "+31", "NL", "🇳🇱"),
        Country("Italy", "+39", "IT", "🇮🇹"),
        Country("Spain", "+34", "ES", "🇪🇸"),
        Country("Mexico", "+52", "MX", "🇲🇽"),
        Country("South Korea", "+82", "KR", "🇰🇷"),
        Country("New Zealand", "+64", "NZ", "🇳🇿"),
        Country("Bangladesh", "+880", "BD", "🇧🇩"),
        Country("Pakistan", "+92", "PK", "🇵🇰"),
        Country("Nepal", "+977", "NP", "🇳🇵"),
        Country("Sri Lanka", "+94", "LK", "🇱🇰"),
        Country("Philippines", "+63", "PH", "🇵🇭"),
        Country("Vietnam", "+84", "VN", "🇻🇳"),
        Country("Thailand", "+66", "TH", "🇹🇭"),
        Country("Turkey", "+90", "TR", "🇹🇷"),
        Country("Egypt", "+20", "EG", "🇪🇬"),
        Country("Nigeria", "+234", "NG", "🇳🇬"),
        Country("Kenya", "+254", "KE", "🇰🇪"),
        Country("Ireland", "+353", "IE", "🇮🇪"),
        Country("Switzerland", "+41", "CH", "🇨🇭"),
        Country("Sweden", "+46", "SE", "🇸🇪"),
        Country("Norway", "+47", "NO", "🇳🇴"),
        Country("Denmark", "+45", "DK", "🇩🇰"),
        Country("Finland", "+358", "FI", "🇫🇮"),
        Country("Poland", "+48", "PL", "🇵🇱"),
        Country("Portugal", "+351", "PT", "🇵🇹"),
        Country("Austria", "+43", "AT", "🇦🇹"),
        Country("Belgium", "+32", "BE", "🇧🇪"),
        Country("Greece", "+30", "GR", "🇬🇷"),
        Country("Israel", "+972", "IL", "🇮🇱"),
        Country("Argentina", "+54", "AR", "🇦🇷"),
        Country("Colombia", "+57", "CO", "🇨🇴"),
        Country("Chile", "+56", "CL", "🇨🇱"),
        Country("Qatar", "+974", "QA", "🇶🇦"),
        Country("Kuwait", "+965", "KW", "🇰🇼"),
        Country("Oman", "+968", "OM", "🇴🇲"),
        Country("Bahrain", "+973", "BH", "🇧🇭"),
        Country("Hong Kong", "+852", "HK", "🇭🇰"),
        Country("Taiwan", "+886", "TW", "🇹🇼"),
        Country("Ukraine", "+380", "UA", "🇺🇦"),
        Country("Romania", "+40", "RO", "🇷🇴"),
        Country("Czech Republic", "+420", "CZ", "🇨🇿"),
        Country("Hungary", "+36", "HU", "🇭🇺"),
        Country("Ghana", "+233", "GH", "🇬🇭"),
        Country("Morocco", "+212", "MA", "🇲🇦"),
        Country("Tanzania", "+255", "TZ", "🇹🇿"),
        Country("Uganda", "+256", "UG", "🇺🇬"),
        Country("Ethiopia", "+251", "ET", "🇪🇹"),
        Country("Iran", "+98", "IR", "🇮🇷"),
        Country("Iraq", "+964", "IQ", "🇮🇶"),
        Country("Jordan", "+962", "JO", "🇯🇴"),
        Country("Lebanon", "+961", "LB", "🇱🇧"),
        Country("Peru", "+51", "PE", "🇵🇪"),
        Country("Venezuela", "+58", "VE", "🇻🇪"),
        Country("Ecuador", "+593", "EC", "🇪🇨")
    )

    fun findByCodeOrIso(input: String): Country? {
        val clean = input.trim()
        return countries.firstOrNull {
            it.code.equals(clean, ignoreCase = true) ||
            it.isoCode.equals(clean, ignoreCase = true) ||
            it.name.equals(clean, ignoreCase = true)
        }
    }
}
