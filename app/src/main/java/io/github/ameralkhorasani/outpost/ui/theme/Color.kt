package io.github.ameralkhorasani.outpost.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware colour accessors used throughout the UI.
 *
 * These read from [LocalAppColors] rather than being fixed values, so every screen
 * follows the active theme without each call site having to know which one is active.
 * They are composable getters, so they can only be read inside a @Composable scope -
 * that is what makes them react to a theme change.
 */

val AppBackground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.background

val AppSurface: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface

val AppSurfaceVariant: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceVariant

val NavBackground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.navBackground

val PrimaryAccent: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryAccent

val CpuBlue: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.cpuBlue

val RamPurple: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.ramPurple

val DiskOrange: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.diskOrange

val AlertRed: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.alertRed

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary

val TextDisabled: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textDisabled
