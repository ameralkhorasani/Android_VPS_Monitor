package io.github.ameralkhorasani.outpost

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.ameralkhorasani.outpost.core.crash.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class OutpostApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Earliest possible hook - before Hilt builds its component graph - so a failure
        // during start-up is still recorded rather than vanishing.
        runCatching { CrashReporter.install(base) }
    }

    override fun onCreate() {
        super.onCreate()

        // Register BouncyCastle for OpenSSH and Ed25519 key support in sshj. Guarded:
        // provider registration must never be the reason the app cannot start.
        runCatching {
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }.onFailure {
            Log.e("OutpostApp", "BouncyCastle registration failed", it)
        }
    }
}
