package io.github.ameralkhorasani.outpost.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.ameralkhorasani.outpost.core.crash.CrashReporter

/**
 * Shown on the launch after a crash, instead of the normal UI.
 *
 * Built from plain Android views on purpose: no Compose, no dependency injection, no
 * database and no SSH. Whatever brought the app down, this screen still renders, so the
 * stack trace can always be read and copied off the device.
 */
object SafeModeScreen {

    fun show(activity: Activity, log: String, onContinue: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }

        root.addView(TextView(activity).apply {
            text = "Outpost stopped unexpectedly"
            setTextColor(Color.WHITE)
            textSize = 20f
        })

        root.addView(TextView(activity).apply {
            text = "The details below identify the cause. Copy them and send them over.\n" +
                "A copy is also saved at:\n" +
                (CrashReporter.externalLogPath(activity) ?: "Android/data/io.github.ameralkhorasani.outpost/files/crash_log.txt")
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 12f
            setPadding(0, dp(8), 0, dp(12))
        })

        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroll.addView(TextView(activity).apply {
            text = log
            setTextColor(Color.parseColor("#F87171"))
            textSize = 10f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        })
        root.addView(scroll)

        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }

        buttons.addView(Button(activity).apply {
            text = "Copy"
            setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Outpost crash", log))
                Toast.makeText(activity, "Crash log copied", Toast.LENGTH_SHORT).show()
            }
        })

        buttons.addView(Button(activity).apply {
            text = "Clear & continue"
            setOnClickListener {
                CrashReporter.clearLog(activity)
                onContinue()
            }
        })

        root.addView(buttons)
        activity.setContentView(root)
    }
}
