package io.github.ameralkhorasani.outpost.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.ameralkhorasani.outpost.data.model.ThemeMode

@Composable
fun OutpostTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colors = if (useDark) DarkAppColors else LightAppColors

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = colors.primaryAccent,
            secondary = colors.cpuBlue,
            tertiary = colors.ramPurple,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceVariant,
            error = colors.alertRed,
            onPrimary = colors.background,
            onSecondary = colors.background,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary
        )
    } else {
        lightColorScheme(
            primary = colors.primaryAccent,
            secondary = colors.cpuBlue,
            tertiary = colors.ramPurple,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceVariant,
            error = colors.alertRed,
            // White on the deeper light-theme accent. Using the background colour here
            // (as the dark scheme does) would put near-white text on a light green fill.
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // `as?`, not `as`: the composition's context is not guaranteed to be the
            // Activity, and system-bar tinting is not worth crashing over.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            runCatching {
                window.statusBarColor = colors.background.toArgb()
                window.navigationBarColor = colors.navBackground.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                // Dark system icons on a light bar, light icons on a dark one.
                insetsController.isAppearanceLightStatusBars = colors.isLight
                insetsController.isAppearanceLightNavigationBars = colors.isLight
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
