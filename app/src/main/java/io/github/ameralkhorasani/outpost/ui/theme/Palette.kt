package io.github.ameralkhorasani.outpost.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colour roles, resolved per theme.
 *
 * The screens refer to these by role (surface, textPrimary, cpuBlue...) rather than to
 * fixed constants, so a single palette swap re-themes the whole UI. Metric colours are
 * part of the palette because the dark accents used on near-black do not carry enough
 * contrast on a white background.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val navBackground: Color,
    val primaryAccent: Color,
    val cpuBlue: Color,
    val ramPurple: Color,
    val diskOrange: Color,
    val alertRed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val isLight: Boolean
)

val DarkAppColors = AppColors(
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    navBackground = Color(0xFF161618),
    primaryAccent = Color(0xFF34D399),
    cpuBlue = Color(0xFF60A5FA),
    ramPurple = Color(0xFFA78BFA),
    diskOrange = Color(0xFFFB923C),
    alertRed = Color(0xFFF87171),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF9CA3AF),
    textDisabled = Color(0xFF6B7280),
    isLight = false
)

/**
 * Light theme. The accent colours are the 600-weight versions of the dark palette's
 * 400-weight tones: the lighter shades read as washed out on white and fail contrast
 * checks for text and thin chart lines.
 */
val LightAppColors = AppColors(
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBEEF2),
    navBackground = Color(0xFFFFFFFF),
    primaryAccent = Color(0xFF059669),
    cpuBlue = Color(0xFF2563EB),
    ramPurple = Color(0xFF7C3AED),
    diskOrange = Color(0xFFEA580C),
    alertRed = Color(0xFFDC2626),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF5B6472),
    textDisabled = Color(0xFF9CA3AF),
    isLight = true
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
