package io.github.ameralkhorasani.outpost.ui.screens.terminal

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.ameralkhorasani.outpost.ui.theme.AlertRed
import io.github.ameralkhorasani.outpost.ui.theme.LocalAppColors
import io.github.ameralkhorasani.outpost.ui.theme.NavBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary

/**
 * The JS side of the terminal talks to the app through this object.
 *
 * Every method here runs on the WebView's JavaBridge thread, not the main thread, so
 * anything that touches Compose state is posted across.
 */
class KotlinBridge(
    private val inputHandler: (String) -> Unit,
    private val resizeHandler: (Int, Int) -> Unit,
    private val readyHandler: () -> Unit,
    private val modifierHandler: (Boolean, Boolean) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun sendInput(base64Data: String) {
        inputHandler(base64Data)
    }

    @JavascriptInterface
    fun onResize(cols: Int, rows: Int) {
        main.post { resizeHandler(cols, rows) }
    }

    @JavascriptInterface
    fun onReady() {
        main.post { readyHandler() }
    }

    @JavascriptInterface
    fun onModifiersChanged(ctrl: Boolean, alt: Boolean) {
        main.post { modifierHandler(ctrl, alt) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    serverId: String,
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }
    var keyboardShownOnce by remember { mutableStateOf(false) }

    val isLightTheme = LocalAppColors.current.isLight
    val terminalThemeName = if (isLightTheme) "light" else "dark"
    // Painting the WebView itself avoids a dark flash before the page paints.
    val terminalBackground = (if (isLightTheme) Color.White else AppBackground).toArgb()

    fun showKeyboard() {
        val webView = webViewInstance ?: return
        // All three are needed: the WebView has to hold Android focus, xterm's hidden
        // textarea has to hold DOM focus, and only then does the IME have something to
        // attach to. Missing any one of them is a terminal you cannot type into.
        webView.requestFocus()
        webView.evaluateJavascript("focusTerminal()", null)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val webView = webViewInstance ?: return
        webView.evaluateJavascript("blurTerminal()", null)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(webView.windowToken, 0)
    }

    // A theme change has to reach the already-loaded page too, not just new ones.
    LaunchedEffect(terminalThemeName, webViewInstance) {
        webViewInstance?.evaluateJavascript("applyTerminalTheme('$terminalThemeName')", null)
    }

    LaunchedEffect(serverId) {
        viewModel.initialize(serverId)
    }

    LaunchedEffect(webViewInstance) {
        webViewInstance?.let { webView ->
            viewModel.terminalOutput.collect { base64Data ->
                webView.post {
                    webView.evaluateJavascript("writeTerminalData('$base64Data')", null)
                }
            }
        }
    }

    // Raise the keyboard once the shell is actually live. Doing it any earlier means
    // typing into a connection that is not up yet.
    LaunchedEffect(uiState.isConnected, webViewInstance) {
        if (uiState.isConnected && webViewInstance != null && !keyboardShownOnce) {
            keyboardShownOnce = true
            showKeyboard()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.server?.name ?: "SSH Terminal",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                        )
                        Text(
                            buildString {
                                append(uiState.server?.let { "${it.username}@${it.host}:${it.port}" } ?: "")
                                if (uiState.cols > 0) append("  ·  ${uiState.cols}×${uiState.rows}")
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showKeyboard() }) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = "Show keyboard",
                            tint = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            webViewInstance?.evaluateJavascript("clearTerminal()", null)
                            viewModel.reconnect()
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reconnect",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Without this the soft keyboard covers the bottom of the terminal and
                // the accessory row, so the prompt you are typing at is off screen.
                .imePadding()
        ) {
            // The WebView stays mounted through connecting, errors and reconnects, with
            // status drawn over it. Swapping it out for a status pane meant a fresh
            // WebView - and a fresh page load - every time the connection hiccuped, which
            // threw away the scrollback and the focus along with it.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            // The WebView must be able to take focus in touch mode,
                            // otherwise tapping it never moves Android's focus there and
                            // the IME has nothing to open against.
                            isFocusable = true
                            isFocusableInTouchMode = true
                            settings.setNeedInitialFocus(true)

                            addJavascriptInterface(
                                KotlinBridge(
                                    inputHandler = { base64Input -> viewModel.sendInputBase64(base64Input) },
                                    resizeHandler = { cols, rows -> viewModel.onTerminalResize(cols, rows) },
                                    readyHandler = { requestFocus() },
                                    modifierHandler = { ctrl, alt ->
                                        ctrlArmed = ctrl
                                        altArmed = alt
                                    }
                                ),
                                "kotlinBridge"
                            )

                            setBackgroundColor(terminalBackground)

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Match the terminal palette to the app theme.
                                    view?.evaluateJavascript(
                                        "applyTerminalTheme('$terminalThemeName')",
                                        null
                                    )
                                    webViewInstance = view
                                }
                            }

                            loadUrl("file:///android_asset/terminal/index.html")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (uiState.isConnecting) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(AppBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryAccent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Establishing Secure SSH Connection...", color = TextSecondary)
                        }
                    }
                } else if (uiState.errorMessage != null) {
                    // A bar rather than a full pane: whatever the shell printed before it
                    // died is usually the explanation, so it stays readable underneath.
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = AppSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                uiState.errorMessage ?: "",
                                color = AlertRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    webViewInstance?.evaluateJavascript("clearTerminal()", null)
                                    viewModel.reconnect()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryAccent,
                                    contentColor = AppBackground
                                )
                            ) {
                                Text("Reconnect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Bottom ANSI Key Accessory Toolbar
            AccessoryKeyboardRow(
                ctrlArmed = ctrlArmed,
                altArmed = altArmed,
                onKeyClick = { keySeq -> viewModel.sendKeySequence(keySeq) },
                onToggleCtrl = {
                    val next = !ctrlArmed
                    ctrlArmed = next
                    webViewInstance?.evaluateJavascript("setPendingCtrl($next)", null)
                    showKeyboard()
                },
                onToggleAlt = {
                    val next = !altArmed
                    altArmed = next
                    webViewInstance?.evaluateJavascript("setPendingAlt($next)", null)
                    showKeyboard()
                },
                onPaste = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val text = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                    if (!text.isNullOrEmpty()) viewModel.sendKeySequence(text)
                },
                onHideKeyboard = { hideKeyboard() }
            )
        }
    }
}

@Composable
fun AccessoryKeyboardRow(
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onKeyClick: (String) -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onPaste: () -> Unit,
    onHideKeyboard: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NavBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccessoryKeyButton("ESC") { onKeyClick("\u001b") }
            AccessoryKeyButton("TAB") { onKeyClick("\t") }
            // CTRL and ALT arm the next keystroke rather than sending a fixed sequence,
            // so Ctrl+C, Ctrl+Z, Ctrl+L and Ctrl+R all work from the soft keyboard.
            AccessoryKeyButton("CTRL", active = ctrlArmed, onClick = onToggleCtrl)
            AccessoryKeyButton("ALT", active = altArmed, onClick = onToggleAlt)
            AccessoryKeyButton("^C") { onKeyClick("\u0003") }
            AccessoryKeyButton("^D") { onKeyClick("\u0004") }
            AccessoryKeyButton("^Z") { onKeyClick("\u001a") }
            AccessoryKeyButton("/") { onKeyClick("/") }
            AccessoryKeyButton("|") { onKeyClick("|") }
            AccessoryKeyButton("-") { onKeyClick("-") }
            AccessoryKeyButton("~") { onKeyClick("~") }
            AccessoryKeyButton("HOME") { onKeyClick("\u001b[H") }
            AccessoryKeyButton("END") { onKeyClick("\u001b[F") }
            AccessoryKeyButton("PGUP") { onKeyClick("\u001b[5~") }
            AccessoryKeyButton("PGDN") { onKeyClick("\u001b[6~") }
            AccessoryKeyButton("▲") { onKeyClick("\u001b[A") }
            AccessoryKeyButton("▼") { onKeyClick("\u001b[B") }
            AccessoryKeyButton("◀") { onKeyClick("\u001b[D") }
            AccessoryKeyButton("▶") { onKeyClick("\u001b[C") }
            AccessoryKeyButton("PASTE", onClick = onPaste)
            AccessoryKeyButton("HIDE ⌨", onClick = onHideKeyboard)
        }
    }
}

@Composable
fun AccessoryKeyButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) PrimaryAccent else AppSurface,
            contentColor = if (active) AppBackground else TextPrimary
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(38.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
