package io.github.ameralkhorasani.outpost.data.preferences

import android.content.Context
import io.github.ameralkhorasani.outpost.data.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App preferences, persisted in SharedPreferences and exposed as state flows so the UI
 * reacts to a change immediately rather than on next launch.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private companion object {
        const val PREFS_FILE = "outpost_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_PROBE_ON_STARTUP = "probe_on_startup"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _probeOnStartup = MutableStateFlow(prefs.getBoolean(KEY_PROBE_ON_STARTUP, true))
    val probeOnStartup: StateFlow<Boolean> = _probeOnStartup.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, false))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setProbeOnStartup(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROBE_ON_STARTUP, enabled).apply()
        _probeOnStartup.value = enabled
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        _keepScreenOn.value = enabled
    }
}
