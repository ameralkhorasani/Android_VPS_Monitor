package io.github.ameralkhorasani.outpost.core.crash

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records uncaught exceptions to a file so a crash can be diagnosed from the device,
 * without needing a USB cable and logcat.
 *
 * It also shields the app from background threads it does not own. sshj runs its own
 * reader and keep-alive threads; when one of those dies with an exception, the default
 * handler tears down the whole process even though the app itself is fine. Those are
 * logged and contained. Anything on the main thread, or from our own code, is still
 * passed to the platform handler so genuine bugs stay visible.
 */
object CrashReporter {

    private const val TAG = "OutpostCrash"
    private const val LOG_FILE = "crash_log.txt"
    private const val MAX_BYTES = 64 * 1024

    /** Thread-name prefixes belonging to the SSH library rather than to app code. */
    private val CONTAINED_THREAD_PREFIXES = listOf("sshj-", "outpost-code-tunnel")

    fun install(context: Context) {
        // Not `context.applicationContext`: install() runs from attachBaseContext, and
        // getApplicationContext() is still null that early in the Application lifecycle.
        // Capturing it there left the handler holding null and every record() attempt
        // failing silently - so start-up crashes wrote no log at all, which is exactly
        // when the log matters most. The base context resolves filesDir perfectly well.
        val appContext = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(appContext, thread, throwable) }

            val isLibraryThread = CONTAINED_THREAD_PREFIXES.any { thread.name.startsWith(it) }
            if (isLibraryThread && thread.name != "main") {
                // Contained: the SSH session is lost, but the UI stays alive and the
                // screen shows its own connection error.
                Log.w(TAG, "Contained crash on library thread '${thread.name}'", throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun record(context: Context, thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            append("=== $timestamp ===\n")
            append("thread: ${thread.name}\n")
            append(stackTrace)
            append('\n')
        }

        val file = File(context.filesDir, LOG_FILE)
        // Keep the newest entries only, so the file cannot grow without bound.
        if (file.exists() && file.length() > MAX_BYTES) {
            runCatching { file.writeText(file.readText().takeLast(MAX_BYTES / 2)) }
        }
        file.appendText(entry)

        // Second copy somewhere a file manager can reach, for when the app cannot start
        // far enough to show the log itself:
        // Android/data/io.github.ameralkhorasani.outpost/files/crash_log.txt
        runCatching {
            context.getExternalFilesDir(null)?.let { dir ->
                File(dir, LOG_FILE).appendText(entry)
            }
        }
    }

    /** Where the externally readable copy lives, for display in the UI. */
    fun externalLogPath(context: Context): String? =
        context.applicationContext.getExternalFilesDir(null)?.let { "$it/$LOG_FILE" }

    fun readLog(context: Context): String? {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        return if (file.exists() && file.length() > 0) file.readText() else null
    }

    fun clearLog(context: Context) {
        val app = context.applicationContext
        File(app.filesDir, LOG_FILE).delete()
        runCatching { app.getExternalFilesDir(null)?.let { File(it, LOG_FILE).delete() } }
    }
}
