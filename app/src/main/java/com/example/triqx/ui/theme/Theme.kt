package com.example.triqx.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GoogleDarkColorScheme = darkColorScheme(
    primary = GooglePrimaryDark,
    onPrimary = GoogleOnPrimaryDark,
    primaryContainer = GooglePrimaryContainerDark,
    onPrimaryContainer = GoogleOnPrimaryContainerDark,
    secondary = GoogleSecondaryDark,
    onSecondary = GoogleOnSecondaryDark,
    secondaryContainer = GoogleSecondaryContainerDark,
    onSecondaryContainer = GoogleOnSecondaryContainerDark,
    tertiary = GoogleTertiaryDark,
    onTertiary = GoogleOnTertiaryDark,
    tertiaryContainer = GoogleTertiaryContainerDark,
    onTertiaryContainer = GoogleOnTertiaryContainerDark,
    background = GoogleBackgroundDark,
    onBackground = GoogleOnBackgroundDark,
    surface = GoogleSurfaceDark,
    onSurface = GoogleOnSurfaceDark,
    surfaceVariant = GoogleSurfaceVariantDark,
    onSurfaceVariant = GoogleOnSurfaceVariantDark,
    surfaceContainerLowest = GoogleSurfaceContainerLowestDark,
    surfaceContainerLow = GoogleSurfaceContainerLowDark,
    surfaceContainer = GoogleSurfaceContainerDark,
    surfaceContainerHigh = GoogleSurfaceContainerHighDark,
    surfaceContainerHighest = GoogleSurfaceContainerHighestDark,
    outline = GoogleOutlineDark,
    outlineVariant = GoogleOutlineVariantDark,
    error = GoogleErrorDark,
    onError = GoogleOnErrorDark,
    errorContainer = GoogleErrorContainerDark,
    onErrorContainer = GoogleOnErrorContainerDark
)

private val GoogleLightColorScheme = lightColorScheme(
    primary = GooglePrimaryLight,
    onPrimary = GoogleOnPrimaryLight,
    primaryContainer = GooglePrimaryContainerLight,
    onPrimaryContainer = GoogleOnPrimaryContainerLight,
    secondary = GoogleSecondaryLight,
    onSecondary = GoogleOnSecondaryLight,
    secondaryContainer = GoogleSecondaryContainerLight,
    onSecondaryContainer = GoogleOnSecondaryContainerLight,
    tertiary = GoogleTertiaryLight,
    onTertiary = GoogleOnTertiaryLight,
    tertiaryContainer = GoogleTertiaryContainerLight,
    onTertiaryContainer = GoogleOnTertiaryContainerLight,
    background = GoogleBackgroundLight,
    onBackground = GoogleOnBackgroundLight,
    surface = GoogleSurfaceLight,
    onSurface = GoogleOnSurfaceLight,
    surfaceVariant = GoogleSurfaceVariantLight,
    onSurfaceVariant = GoogleOnSurfaceVariantLight,
    surfaceContainerLowest = GoogleSurfaceContainerLowestLight,
    surfaceContainerLow = GoogleSurfaceContainerLowLight,
    surfaceContainer = GoogleSurfaceContainerLight,
    surfaceContainerHigh = GoogleSurfaceContainerHighLight,
    surfaceContainerHighest = GoogleSurfaceContainerHighestLight,
    outline = GoogleOutlineLight,
    outlineVariant = GoogleOutlineVariantLight,
    error = GoogleErrorLight,
    onError = GoogleOnErrorLight,
    errorContainer = GoogleErrorContainerLight,
    onErrorContainer = GoogleOnErrorContainerLight
)

@Composable
fun TriqxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GoogleDarkColorScheme
        else -> GoogleLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}