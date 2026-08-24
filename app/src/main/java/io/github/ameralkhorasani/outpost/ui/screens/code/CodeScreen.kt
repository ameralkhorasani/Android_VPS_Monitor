package io.github.ameralkhorasani.outpost.ui.screens.code

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.ameralkhorasani.outpost.ui.theme.AlertRed
import io.github.ameralkhorasani.outpost.ui.theme.NavBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeScreen(
    serverId: String,
    viewModel: CodeViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) {
        viewModel.initialize(serverId)
    }

    // Leaving the editor should exit the screen rather than walk the SPA history back.
    BackHandler(enabled = true) { onNavigateBack() }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webView?.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "VS Code",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                        )
                        Text(
                            uiState.server?.let { "${it.username}@${it.host} · tunnelled" } ?: "",
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
                    if (uiState.stage == CodeStage.READY) {
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextSecondary)
                        }
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (uiState.desktopMode) "Switch to mobile layout" else "Switch to desktop layout") },
                            onClick = {
                                menuExpanded = false
                                viewModel.toggleDesktopMode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restart code-server") },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.restartCodeServer()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy code-server password") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.revealPassword()?.let {
                                    clipboard.setText(AnnotatedString(it))
                                }
                            }
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
        ) {
            if (uiState.stage == CodeStage.READY && pageProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = PrimaryAccent,
                    trackColor = AppBackground
                )
            }

            // An existing install listening on a public interface is reachable from the
            // internet, not just through this tunnel. Worth saying out loud.
            val bind = uiState.remoteStatus?.bindAddr
            if (uiState.stage == CodeStage.READY && bind != null &&
                !bind.startsWith("127.0.0.1") && !bind.startsWith("localhost")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlertRed.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = AlertRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "code-server is bound to $bind - it is reachable from outside this tunnel.",
                        style = MaterialTheme.typography.bodySmall.copy(color = AlertRed)
                    )
                }
            }

            when (uiState.stage) {
                CodeStage.CONNECTING, CodeStage.CHECKING, CodeStage.TUNNELING ->
                    StatusPane(message = uiState.statusText)

                CodeStage.NEEDS_SETUP ->
                    SetupPrompt(
                        message = uiState.statusText,
                        port = uiState.server?.codeServerPort ?: 8080,
                        installed = uiState.remoteStatus?.installed == true,
                        sshTarget = uiState.server?.let { "${it.username}@${it.host}" }.orEmpty(),
                        onPortChange = { viewModel.updatePort(it) },
                        onConnectAnyway = { viewModel.connectAnyway() },
                        onInstall = { viewModel.runSetup() }
                    )

                CodeStage.INSTALLING ->
                    SetupConsole(lines = uiState.setupLog, statusText = uiState.statusText)

                CodeStage.ERROR ->
                    ErrorPane(
                        message = uiState.errorMessage ?: "Something went wrong",
                        log = uiState.setupLog,
                        onRetry = { viewModel.retry() }
                    )

                CodeStage.READY -> {
                    val url = uiState.editorUrl
                    if (url == null) {
                        StatusPane(message = "Preparing the editor...")
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                val view = WebView(ctx)
                                view.configureForCodeServer(uiState.desktopMode)

                                CookieManager.getInstance().let { cookies ->
                                    cookies.setAcceptCookie(true)
                                    // Keeps the code-server session cookie across restarts.
                                    cookies.setAcceptThirdPartyCookies(view, true)
                                }

                                view.webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(v: WebView?, newProgress: Int) {
                                        pageProgress = newProgress
                                    }
                                }

                                view.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val target = request?.url ?: return false
                                        // Keep the tunnelled editor in the WebView; send
                                        // anything else (docs, marketplace links) to the browser.
                                        if (target.host == "127.0.0.1" || target.host == "localhost") {
                                            return false
                                        }
                                        runCatching {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_VIEW, target).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                        return true
                                    }

                                    // Main-frame failures only. Sub-resource errors are
                                    // routine on a loading IDE and must not tear down a
                                    // session that is otherwise working.
                                    override fun onReceivedError(
                                        v: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(v, request, error)
                                        if (request?.isForMainFrame != true) return
                                        val reason = error?.description?.toString()
                                            ?: "connection refused"
                                        viewModel.onEditorLoadFailed(reason)
                                    }

                                    override fun onPageFinished(v: WebView?, pageUrl: String?) {
                                        super.onPageFinished(v, pageUrl)
                                        val payload = uiState.autoLoginPayload
                                        if (v != null && payload != null &&
                                            pageUrl?.contains("/login") == true
                                        ) {
                                            v.evaluateJavascript(autoLoginScript(payload), null)
                                        }
                                    }
                                }

                                view.loadUrl(url)
                                webView = view
                                view
                            },
                            update = { view ->
                                view.applyUserAgent(uiState.desktopMode)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // A layout switch only takes effect once the page is re-fetched. Track the previous
    // value so the initial composition does not reload a page that just started loading.
    var lastDesktopMode by remember { mutableStateOf(uiState.desktopMode) }
    LaunchedEffect(uiState.desktopMode) {
        if (uiState.desktopMode != lastDesktopMode) {
            lastDesktopMode = uiState.desktopMode
            if (uiState.stage == CodeStage.READY) webView?.reload()
        }
    }
}

/**
 * WebView configuration code-server needs: JS, DOM storage, web workers backed by
 * local storage, and cookies to keep the login session.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForCodeServer(desktopMode: Boolean) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(false)
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        mediaPlaybackRequiresUserGesture = false
        // The tunnel is plain HTTP over loopback; the SSH channel provides the encryption.
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = WebSettings.LOAD_DEFAULT
        // No need to reach the phone's filesystem from the editor page.
        allowFileAccess = false
        allowContentAccess = false
    }
    applyUserAgent(desktopMode)
    isFocusable = true
    isFocusableInTouchMode = true
}

/**
 * code-server serves a cut-down layout to mobile user agents. Presenting a desktop UA
 * gives the full IDE, which is what makes this usable on a tablet or with a keyboard.
 */
private fun WebView.applyUserAgent(desktopMode: Boolean) {
    // Read the pristine default rather than settings.userAgentString: once we have
    // written the desktop UA, the latter no longer describes the mobile original,
    // so toggling back would be a no-op.
    val default = WebSettings.getDefaultUserAgent(context)
    settings.userAgentString = if (desktopMode) {
        default
            .replace(Regex("Linux; Android [^;)]*(; [^)]*)?"), "X11; Linux x86_64")
            .replace(" Mobile", "")
    } else {
        default
    }
}

/**
 * Fills in and submits the code-server login form. The password travels as base64 so
 * quotes and shell-unfriendly characters cannot break out of the JS string.
 */
private fun autoLoginScript(base64Password: String): String = """
    (function() {
      try {
        var pw = atob('$base64Password');
        var input = document.querySelector('input[type=password]');
        if (!input) return;
        var setter = Object.getOwnPropertyDescriptor(
          window.HTMLInputElement.prototype, 'value'
        ).set;
        setter.call(input, pw);
        input.dispatchEvent(new Event('input', { bubbles: true }));
        var form = input.form || document.querySelector('form');
        if (form) form.submit();
      } catch (e) {}
    })();
""".trimIndent()

@Composable
private fun StatusPane(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PrimaryAccent)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SetupPrompt(
    message: String,
    port: Int,
    installed: Boolean,
    sshTarget: String,
    onPortChange: (Int) -> Unit,
    onConnectAnyway: () -> Unit,
    onInstall: () -> Unit
) {
    var portText by remember(port) { mutableStateOf(port.toString()) }
    val parsedPort = portText.toIntOrNull()
    val portValid = parsedPort != null && parsedPort in 1..65535
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Code,
            contentDescription = null,
            tint = PrimaryAccent,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Nothing answered on port $port",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Main action: Install or Start code-server
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = PrimaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (installed) "Start your code-server" else "Install code-server",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                SetupBullet("Installs VS Code directly on your VPS over SSH.")
                SetupBullet("Runs securely over loopback; never exposed to the internet.")
                SetupBullet("Generates a random login password stored securely on this device.")

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        contentColor = AppBackground
                    )
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (installed) "Start code-server" else "Install & start code-server",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced: Manual Forwarding
        OutlinedButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (showAdvanced) "Hide Advanced Settings" else "Show Advanced Settings")
        }

        if (showAdvanced) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Forward an existing service",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Point this at whatever is already serving on the VPS. Equivalent to:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "ssh -L $portText:127.0.0.1:$portText $sshTarget",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { input -> portText = input.filter { it.isDigit() }.take(5) },
                        label = { Text("Remote port") },
                        singleLine = true,
                        isError = portText.isNotEmpty() && !portValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            val target = parsedPort ?: return@OutlinedButton
                            if (target != port) onPortChange(target) else onConnectAnyway()
                        },
                        enabled = portValid,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (parsedPort != null && parsedPort != port) {
                                "Connect on port $parsedPort"
                            } else {
                                "Connect anyway"
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SetupBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("•", color = PrimaryAccent)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
    }
}

@Composable
private fun SetupConsole(lines: List<String>, statusText: String) {
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = PrimaryAccent,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AppBackground)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(lines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (line.contains("ERROR", ignoreCase = true)) AlertRed else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ErrorPane(message: String, log: List<String>, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Could not open the editor",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = AlertRed))

        if (log.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface)
            ) {
                LazyColumn(modifier = Modifier.padding(12.dp)) {
                    items(log.takeLast(60)) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryAccent,
                contentColor = AppBackground
            )
        ) {
            Text("Retry", fontWeight = FontWeight.Bold)
        }
    }
}
