package com.speedora.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

val LocalReducedMotion = compositionLocalOf { false }
val LocalAnimationIntensity = compositionLocalOf { 1.0f }

private val SpeedoraLightScheme = lightColorScheme(
    primary = SpeedoraColors.RoyalBlue,
    onPrimary = SpeedoraColors.PureWhite,
    primaryContainer = SpeedoraColors.SoftBlue,
    onPrimaryContainer = SpeedoraColors.MidnightNavy,
    secondary = SpeedoraColors.DeepBlue,
    onSecondary = SpeedoraColors.PureWhite,
    secondaryContainer = SpeedoraColors.LightGray,
    onSecondaryContainer = SpeedoraColors.MidnightNavy,
    tertiary = SpeedoraColors.MidnightNavy,
    onTertiary = SpeedoraColors.PureWhite,
    background = SpeedoraColors.PureWhite,
    onBackground = SpeedoraColors.MidnightNavy,
    surface = SpeedoraColors.PureWhite,
    onSurface = SpeedoraColors.MidnightNavy,
    surfaceVariant = SpeedoraColors.LightGray,
    onSurfaceVariant = SpeedoraColors.TextSecondary,
    outline = SpeedoraColors.BorderSubtle,
    outlineVariant = SpeedoraColors.SoftBlue,
    error = SpeedoraColors.ErrorCrimson,
    onError = SpeedoraColors.PureWhite,
    errorContainer = SpeedoraColors.ErrorSoft
)

private val SpeedoraDarkScheme = darkColorScheme(
    primary = SpeedoraColors.RoyalBlue,
    onPrimary = SpeedoraColors.PureWhite,
    primaryContainer = SpeedoraColors.DeepBlue,
    onPrimaryContainer = SpeedoraColors.SoftBlue,
    secondary = SpeedoraColors.SoftBlue,
    onSecondary = SpeedoraColors.MidnightNavy,
    secondaryContainer = SpeedoraColors.DarkSurfaceElevated,
    onSecondaryContainer = SpeedoraColors.DarkTextPrimary,
    tertiary = SpeedoraColors.SoftBlue,
    onTertiary = SpeedoraColors.MidnightNavy,
    background = SpeedoraColors.DarkBackground,
    onBackground = SpeedoraColors.DarkTextPrimary,
    surface = SpeedoraColors.DarkSurface,
    onSurface = SpeedoraColors.DarkTextPrimary,
    surfaceVariant = SpeedoraColors.DarkSurfaceElevated,
    onSurfaceVariant = SpeedoraColors.DarkTextSecondary,
    outline = SpeedoraColors.DarkBorder,
    outlineVariant = SpeedoraColors.DarkSurfaceElevated,
    error = SpeedoraColors.ErrorCrimson,
    onError = SpeedoraColors.PureWhite
)

private val SpeedoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SpeedoraTheme(
    darkTheme: Boolean = false,
    reducedMotion: Boolean = false,
    animationIntensity: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SpeedoraDarkScheme else SpeedoraLightScheme

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalAnimationIntensity provides if (reducedMotion) 0f else animationIntensity.coerceIn(0f, 1f)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpeedoraTypography,
            shapes = SpeedoraShapes,
            content = content
        )
    }
}
