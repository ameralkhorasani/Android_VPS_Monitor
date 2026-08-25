package io.github.ameralkhorasani.outpost.ui.screens.logs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import net.schmizz.sshj.SSHClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Something the viewer can read on the remote host.
 *
 * A VPS is as likely to keep its logs in the systemd journal as in a file - most current
 * distributions ship no `/var/log/syslog` at all - so the journal is a source in its own
 * right rather than a special case bolted onto a file path.
 */
data class LogSource(
    /** What the dropdown shows. */
    val label: String,
    /** Absolute path, empty for the journal. */
    val path: String = "",
    val isJournal: Boolean = false,
    /**
     * The SSH user cannot read this directly, but passwordless sudo reaches it. Recorded
     * per source because a server usually grants some of `/var/log` and not the rest.
     */
    val needsSudo: Boolean = false
) {
    val key: String get() = if (isJournal) "journal" else path
}

data class LogsUiState(
    val server: ServerEntity? = null,
    /** Sources actually found on this host, discovered on connect. */
    val sources: List<LogSource> = emptyList(),
    val selectedSource: LogSource? = null,
    val mode: String = "Tail", // Tail or Head
    val lineWrapEnabled: Boolean = true,
    val logOutput: String = "",
    /** True while the host is being asked what it can read. */
    val isDiscovering: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val sshRepository: SshRepository
) : ViewModel() {

    private companion object {
        const val LINES = 300

        /** Reading a long file or a large journal is slower than a metrics probe. */
        const val READ_TIMEOUT_SECONDS = 30L

        /**
         * Paths worth offering to interpolate into a shell command.
         *
         * Anything the user types is matched against this first: the path ends up inside
         * single quotes in a command line, and this keeps a quote or a `$(...)` out of it.
         */
        val SAFE_PATH = Regex("^/[A-Za-z0-9._/@+-]*$")

        /**
         * Asks the host what it actually has, rather than assuming.
         *
         * Every candidate is probed twice: once as the SSH user, and - only if that
         * fails - through passwordless sudo. `sudo` is never run without `-n`, because a
         * sudo that decides to prompt for a password on a channel with no terminal hangs
         * until the command times out, which is what made the viewer sit empty forever.
         */
        val DISCOVERY_COMMAND = """
            export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}PATH"
            if command -v journalctl >/dev/null 2>&1; then
              if journalctl -n 1 --no-pager >/dev/null 2>&1; then echo JOURNAL=direct
              elif sudo -n journalctl -n 1 --no-pager >/dev/null 2>&1; then echo JOURNAL=sudo
              else echo JOURNAL=denied
              fi
            else
              echo JOURNAL=none
            fi
            for f in /var/log/syslog /var/log/messages /var/log/auth.log /var/log/secure \
                     /var/log/kern.log /var/log/boot.log /var/log/cloud-init.log \
                     /var/log/nginx/access.log /var/log/nginx/error.log \
                     /var/log/apache2/access.log /var/log/apache2/error.log \
                     /var/log/mysql/error.log /var/log/*.log; do
              [ -f "${'$'}f" ] || continue
              if head -c 1 "${'$'}f" >/dev/null 2>&1; then echo "FILE=${'$'}f"
              elif sudo -n head -c 1 "${'$'}f" >/dev/null 2>&1; then echo "SUDO=${'$'}f"
              fi
            done
        """.trimIndent()

        const val NOTHING_READABLE =
            "This user cannot read any log on the server, and passwordless sudo is not " +
                "available either.\n\nOn the server run:\n" +
                "  sudo usermod -aG adm,systemd-journal <user>\n" +
                "then sign out and reconnect. Or open a specific file with Custom path."
    }

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    private var initialized = false

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.w("OutpostLogs", "Log fetch failed", error)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isDiscovering = false,
            errorMessage = error.message ?: error.javaClass.simpleName
        )
    }

    fun initialize(serverId: String) {
        if (initialized) return
        initialized = true

        viewModelScope.launch(errorHandler) {
            val server = serverDao.getServerById(serverId)
            if (server == null) {
                _uiState.value = _uiState.value.copy(
                    isDiscovering = false,
                    errorMessage = "Server configuration not found"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(server = server)
            discoverAndLoad(server)
        }
    }

    fun selectSource(source: LogSource) {
        _uiState.value = _uiState.value.copy(selectedSource = source)
        refresh()
    }

    /**
     * Opens a path the user typed, for the logs an application keeps somewhere of its
     * own. The path is added to the dropdown so it can be switched back to.
     */
    fun openCustomPath(rawPath: String) {
        val path = rawPath.trim()
        if (!SAFE_PATH.matches(path)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Enter an absolute path such as /var/log/nginx/error.log"
            )
            return
        }

        val existing = _uiState.value.sources.firstOrNull { it.key == path }
        if (existing != null) {
            selectSource(existing)
            return
        }

        // Readability is unknown until it is read; the command falls back to sudo on its
        // own, and any refusal comes back as the log body rather than as silence.
        val source = LogSource(label = path, path = path)
        _uiState.value = _uiState.value.copy(
            sources = _uiState.value.sources + source,
            selectedSource = source
        )
        refresh()
    }

    fun setMode(mode: String) {
        _uiState.value = _uiState.value.copy(mode = mode)
        refresh()
    }

    fun toggleLineWrap() {
        _uiState.value = _uiState.value.copy(lineWrapEnabled = !_uiState.value.lineWrapEnabled)
    }

    /** Re-reads the selected source, re-running discovery first if it found nothing. */
    fun refresh() {
        val server = _uiState.value.server ?: return
        viewModelScope.launch(errorHandler) {
            if (_uiState.value.selectedSource == null) {
                discoverAndLoad(server)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                withConnection(server) { client -> readSelected(client) }
            }
        }
    }

    private suspend fun discoverAndLoad(server: ServerEntity) {
        _uiState.value = _uiState.value.copy(isDiscovering = true, errorMessage = null)

        withConnection(server) { client ->
            val output = sshRepository
                .executeCommand(client, DISCOVERY_COMMAND, READ_TIMEOUT_SECONDS)
                .getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        isDiscovering = false,
                        errorMessage = error.message ?: "Could not list the logs on this server"
                    )
                    return@withConnection
                }

            val sources = parseSources(output)
            if (sources.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    sources = emptyList(),
                    selectedSource = null,
                    isDiscovering = false,
                    errorMessage = NOTHING_READABLE
                )
                return@withConnection
            }

            _uiState.value = _uiState.value.copy(
                sources = sources,
                // Keep the current choice across a re-discovery, so a refresh does not
                // silently move the user back to the journal.
                selectedSource = sources.firstOrNull { it.key == _uiState.value.selectedSource?.key }
                    ?: sources.first(),
                isDiscovering = false,
                isLoading = true
            )
            readSelected(client)
        }
    }

    private suspend fun readSelected(client: SSHClient) {
        val source = _uiState.value.selectedSource ?: return

        sshRepository.executeCommand(
            client,
            readCommand(source, _uiState.value.mode),
            READ_TIMEOUT_SECONDS
        ).fold(
            onSuccess = { output ->
                _uiState.value = _uiState.value.copy(
                    logOutput = output.ifBlank { "(this log is empty)" },
                    isLoading = false,
                    errorMessage = null
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to read ${source.label}"
                )
            }
        )
    }

    /**
     * Builds the read command.
     *
     * stderr is folded into stdout on purpose: a "Permission denied" belongs on screen
     * where it explains the empty pane, not thrown away into a blank view.
     */
    private fun readCommand(source: LogSource, mode: String): String {
        val sudo = if (source.needsSudo) "sudo -n " else ""

        if (source.isJournal) {
            // --no-pager matters: without it journalctl pipes itself through less on some
            // distributions and never returns on a channel with no terminal.
            return if (mode == "Head") {
                "${sudo}journalctl --no-pager 2>&1 | head -n $LINES"
            } else {
                "${sudo}journalctl --no-pager -n $LINES 2>&1"
            }
        }

        val quoted = "'${source.path}'"
        val tool = if (mode == "Head") "head" else "tail"
        // A path discovered as readable can still fail later (log rotation changes the
        // owner), so every file read keeps the sudo fallback behind it.
        return "${sudo}$tool -n $LINES $quoted 2>/dev/null || " +
            "sudo -n $tool -n $LINES $quoted 2>&1"
    }

    private fun parseSources(output: String): List<LogSource> {
        val sources = mutableListOf<LogSource>()
        val seen = mutableSetOf<String>()

        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val separator = trimmed.indexOf('=')
            if (separator <= 0) return@forEach
            val key = trimmed.substring(0, separator)
            val value = trimmed.substring(separator + 1)

            when (key) {
                "JOURNAL" -> when (value) {
                    "direct" -> sources.add(
                        LogSource(label = "System journal", isJournal = true)
                    )
                    "sudo" -> sources.add(
                        LogSource(label = "System journal (sudo)", isJournal = true, needsSudo = true)
                    )
                }

                "FILE", "SUDO" -> {
                    if (value.isNotBlank() && seen.add(value)) {
                        sources.add(
                            LogSource(
                                label = if (key == "SUDO") "$value (sudo)" else value,
                                path = value,
                                needsSudo = key == "SUDO"
                            )
                        )
                    }
                }
            }
        }

        return sources
    }

    /**
     * Runs [block] on a fresh session and always closes it.
     *
     * The viewer reads on demand rather than holding a session open, and a read that
     * throws - or the screen closing mid-read - must still hand the session back, or
     * they pile up until sshd stops accepting new ones.
     */
    private suspend fun withConnection(
        server: ServerEntity,
        block: suspend (SSHClient) -> Unit
    ) {
        val client = sshRepository.connect(server, keepAlive = false).getOrElse { error ->
            _uiState.value = _uiState.value.copy(
                isDiscovering = false,
                isLoading = false,
                errorMessage = error.message ?: "SSH connection error"
            )
            return
        }

        try {
            block(client)
        } finally {
            withContext(NonCancellable) { sshRepository.disconnect(client) }
        }
    }
}
