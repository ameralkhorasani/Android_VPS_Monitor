package io.github.ameralkhorasani.outpost.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.ameralkhorasani.outpost.core.crash.CrashReporter
import io.github.ameralkhorasani.outpost.data.preferences.SettingsRepository
import io.github.ameralkhorasani.outpost.ui.navigation.AppNavigation
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.OutpostTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the previous run died, show the trace instead of racing into the same
        // crash. This screen uses no Compose, DI, database or SSH, so it renders even
        // when the failure is in one of those.
        val pendingCrash = runCatching { CrashReporter.readLog(this) }.getOrNull()
        if (pendingCrash != null) {
            SafeModeScreen.show(this, pendingCrash) { startNormalUi() }
            return
        }

        startNormalUi()
    }

    private fun startNormalUi() {
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState()
            val keepScreenOn by settingsRepository.keepScreenOn.collectAsState()

            // Applied at the window level so it covers every screen, and cleared again
            // the moment the setting is turned off.
            DisposableEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            OutpostTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBackground
                ) {
                    var selectedServerId by remember { mutableStateOf<String?>(null) }

                    AppNavigation(
                        selectedServerId = selectedServerId,
                        onServerSelected = { serverId ->
                            selectedServerId = serverId
                        }
                    )
                }
            }
        }
    }
}
