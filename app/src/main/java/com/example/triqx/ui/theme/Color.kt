package com.example.triqx.ui.theme

import androidx.compose.ui.graphics.Color

// Google Material Design 3 (Drive, Messages, Gmail) Light Palette
val GooglePrimaryLight = Color(0xFF0B57D0) // Google Blue #0B57D0
val GoogleOnPrimaryLight = Color(0xFFFFFFFF)
val GooglePrimaryContainerLight = Color(0xFFD3E3FD) // Google Light Blue Container #D3E3FD
val GoogleOnPrimaryContainerLight = Color(0xFF041E49)

val GoogleSecondaryLight = Color(0xFF00639B) // Google Ocean Accent #00639B
val GoogleOnSecondaryLight = Color(0xFFFFFFFF)
val GoogleSecondaryContainerLight = Color(0xFFC2E7FF) // Google Active Pill Color #C2E7FF
val GoogleOnSecondaryContainerLight = Color(0xFF001D35)

val GoogleTertiaryLight = Color(0xFF006874) // Google Teal
val GoogleOnTertiaryLight = Color(0xFFFFFFFF)
val GoogleTertiaryContainerLight = Color(0xFF97F0FF)
val GoogleOnTertiaryContainerLight = Color(0xFF001F24)

val GoogleBackgroundLight = Color(0xFFF8F9FA) // Google Clean Off-White
val GoogleOnBackgroundLight = Color(0xFF1F1F1F)
val GoogleSurfaceLight = Color(0xFFFFFFFF)
val GoogleOnSurfaceLight = Color(0xFF1F1F1F)
val GoogleSurfaceVariantLight = Color(0xFFE1E3E1)
val GoogleOnSurfaceVariantLight = Color(0xFF444746)
val GoogleOutlineLight = Color(0xFF74777F)
val GoogleOutlineVariantLight = Color(0xFFC4C7D0)

val GoogleSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val GoogleSurfaceContainerLowLight = Color(0xFFF0F4F9) // Drive card container
val GoogleSurfaceContainerLight = Color(0xFFE9EEF6) // Drive bottom bar container
val GoogleSurfaceContainerHighLight = Color(0xFFE1E7F0) // Drive search bar container
val GoogleSurfaceContainerHighestLight = Color(0xFFD7DFEC)

// Google Material Design 3 (Drive, Messages, Gmail) Dark Palette
val GooglePrimaryDark = Color(0xFFA8C7FA) // Google Dark Blue Accent #A8C7FA
val GoogleOnPrimaryDark = Color(0xFF062E6F)
val GooglePrimaryContainerDark = Color(0xFF0842A0)
val GoogleOnPrimaryContainerDark = Color(0xFFD3E3FD)

val GoogleSecondaryDark = Color(0xFF7FCFFF)
val GoogleOnSecondaryDark = Color(0xFF003355)
val GoogleSecondaryContainerDark = Color(0xFF004A77) // Google Dark Active Pill Indicator
val GoogleOnSecondaryContainerDark = Color(0xFFC2E7FF)

val GoogleTertiaryDark = Color(0xFF4FD8EB)
val GoogleOnTertiaryDark = Color(0xFF00363D)
val GoogleTertiaryContainerDark = Color(0xFF004F58)
val GoogleOnTertiaryContainerDark = Color(0xFF97F0FF)

val GoogleBackgroundDark = Color(0xFF111318) // Google Dark Canvas
val GoogleOnBackgroundDark = Color(0xFFE2E2E5)
val GoogleSurfaceDark = Color(0xFF111318)
val GoogleOnSurfaceDark = Color(0xFFE2E2E5)
val GoogleSurfaceVariantDark = Color(0xFF44474E)
val GoogleOnSurfaceVariantDark = Color(0xFFC4C7D0)
val GoogleOutlineDark = Color(0xFF8E9199)
val GoogleOutlineVariantDark = Color(0xFF44474E)

val GoogleSurfaceContainerLowestDark = Color(0xFF0C0E13)
val GoogleSurfaceContainerLowDark = Color(0xFF1A1C20)
val GoogleSurfaceContainerDark = Color(0xFF1E2024) // Google Dark bottom bar container
val GoogleSurfaceContainerHighDark = Color(0xFF282A2E) // Google Dark search bar container
val GoogleSurfaceContainerHighestDark = Color(0xFF33353A)

val GoogleErrorLight = Color(0xFFBA1A1A)
val GoogleOnErrorLight = Color(0xFFFFFFFF)
val GoogleErrorContainerLight = Color(0xFFFFDAD6)
val GoogleOnErrorContainerLight = Color(0xFF410002)

val GoogleErrorDark = Color(0xFFFFB4AB)
val GoogleOnErrorDark = Color(0xFF690005)
val GoogleErrorContainerDark = Color(0xFF93000A)
val GoogleOnErrorContainerDark = Color(0xFFFFDAD6)

// Google Avatar Colors (Drive, Gmail, Contacts)
object PixelAvatarColors {
    private val colorPairs = listOf(
        Color(0xFF1A73E8) to Color(0xFFFFFFFF), // Google Blue
        Color(0xFF1E8E3E) to Color(0xFFFFFFFF), // Google Green
        Color(0xFFE8710A) to Color(0xFFFFFFFF), // Google Orange
        Color(0xFFD93025) to Color(0xFFFFFFFF), // Google Red
        Color(0xFF129EAF) to Color(0xFFFFFFFF), // Google Teal
        Color(0xFF9334E6) to Color(0xFFFFFFFF), // Google Purple
        Color(0xFFF9AB00) to Color(0xFF3D1700)  // Google Yellow
    )

    fun getColorsForName(name: String): Pair<Color, Color> {
        if (name.isBlank()) return colorPairs[0]
        val index = kotlin.math.abs(name.trim().hashCode()) % colorPairs.size
        return colorPairs[index]
    }
}