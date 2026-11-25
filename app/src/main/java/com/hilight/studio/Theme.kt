package com.hilight.studio

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pixel-flavoured Material 3.
 *
 * Two things carry most of the "this is a Pixel app" feeling: wallpaper-derived dynamic colour
 * (Material You, API 31+), and shapes that are rounder than stock Material. Everything else — the
 * springy motion, the tonal card stack, the pill navigation — is in PixelUi.kt.
 */

/** Used when the user turns wallpaper colours off, or on a device without dynamic colour. */
private val FallbackDark = darkColorScheme(
    primary = Color(0xFFB69DFF),
    onPrimary = Color(0xFF2B1667),
    primaryContainer = Color(0xFF422C7F),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = Color(0xFF7FD8E8),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF004E5A),
    onSecondaryContainer = Color(0xFFB0EDFB),
    tertiary = Color(0xFFFFB1C8),
    background = Color(0xFF121116),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF121116),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF48454E),
    onSurfaceVariant = Color(0xFFC9C5D0),
    surfaceContainer = Color(0xFF1E1D22),
    surfaceContainerHigh = Color(0xFF29282D),
    surfaceContainerHighest = Color(0xFF343238),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF48454E),
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF5B3FBF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF006876),
    secondaryContainer = Color(0xFFB0EDFB),
    background = Color(0xFFFEF7FF),
    surface = Color(0xFFFEF7FF),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

/** Material 3 Expressive shapes: extra rounded container surfaces. */
private val PixelShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(40.dp),
)

/** Material 3 Expressive typography: bolder headlines and medium weight body. */
private val PixelTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
    )
}

@Composable
fun HiLightTheme(
    dynamicColor: Boolean = true,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = PixelShapes,
        typography = PixelTypography,
        content = content,
    )
}
