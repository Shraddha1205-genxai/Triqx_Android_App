package com.example.triqx.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Dimens {
    // Spacing
    val SpacingNone: Dp = 0.dp
    val SpacingMicro: Dp = 4.dp
    val SpacingSmall: Dp = 8.dp
    val SpacingMedium: Dp = 12.dp
    val SpacingStandard: Dp = 16.dp
    val SpacingLarge: Dp = 20.dp
    val SpacingExtraLarge: Dp = 24.dp
    val SpacingHuge: Dp = 32.dp

    // Corner Radii
    val CardCornerRadius: Dp = 16.dp
    val DialogCornerRadius: Dp = 24.dp
    val InputCornerRadius: Dp = 16.dp
    val ChipCornerRadius: Dp = 20.dp
    val BadgeCornerRadius: Dp = 12.dp
    val SquircleCornerRadius: Dp = 14.dp

    // Shapes
    val CardShape = RoundedCornerShape(CardCornerRadius)
    val DialogShape = RoundedCornerShape(DialogCornerRadius)
    val InputShape = RoundedCornerShape(InputCornerRadius)
    val SquircleShape = RoundedCornerShape(SquircleCornerRadius)
    val BadgeShape = RoundedCornerShape(BadgeCornerRadius)

    // Component Dimensions
    val ButtonHeight: Dp = 50.dp
    val SearchBarHeight: Dp = 52.dp
    val KeypadButtonHeight: Dp = 52.dp
    val TopBarPillHeight: Dp = 4.dp

    // Icon Sizes
    val IconSizeSmall: Dp = 16.dp
    val IconSizeMedium: Dp = 20.dp
    val IconSizeStandard: Dp = 24.dp
    val IconSizeLarge: Dp = 32.dp
    val IconSizeAvatar: Dp = 40.dp

    // Avatar Sizes
    val AvatarSizeSmall: Dp = 32.dp
    val AvatarSizeMedium: Dp = 44.dp
    val AvatarSizeLarge: Dp = 56.dp
    val AvatarSizeExtraLarge: Dp = 80.dp
}
