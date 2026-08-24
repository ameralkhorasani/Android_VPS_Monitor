package io.github.ameralkhorasani.outpost.ssh.tunnel

import io.github.ameralkhorasani.outpost.ssh.SshRepository
import android.util.Log
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.model.PortForwardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import javax.inject.Inject
import javax.inject.Singleton

/** A forward that is currently open. */
data class ActiveTunnel(
    val forwardId: String,
    val serverId: String,
    val serverName: String,
    val label: String,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int
) {
    val localUrl: String get() = "http://localhost:$localPort/"
}

/**
 * Owns every open port forward in the process, and the SSH connections carrying them.
 *
 * Deliberately not tied to a ViewModel: a forward has to keep working while the user is
 * in Chrome looking at http://localhost:8090, which is precisely when the screen that
 * started it has been destroyed. TunnelService keeps the process alive; this holds the
 * sockets.
 *
 * One SSH connection is shared by all forwards to the same server and dropped when its
 * last forward closes.
 */
@Singleton
class TunnelManager @Inject constructor(
    private val serverDao: ServerDao,
    private val sshRepository: SshRepository
) {
    private val mutex = Mutex()
    private val clients = mutableMapOf<String, SSHClient>()
    private val forwards = mutableMapOf<String, SshPortForward>()

    private val _active = MutableStateFlow<List<ActiveTunnel>>(emptyList())
    val active: StateFlow<List<ActiveTunnel>> = _active.asStateFlow()

    fun isRunning(forwardId: String): Boolean =
        _active.value.any { it.forwardId == forwardId }

    /**
     * Opens [forward], reusing the server's existing SSH connection when there is one.
     */
    suspend fun start(forward: PortForwardEntity): Result<ActiveTunnel> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _active.value.firstOrNull { it.forwardId == forward.id }?.let {
                    return@withContext Result.success(it)
                }

                val server = serverDao.getServerById(forward.serverId)
                    ?: return@withContext Result.failure(
                        Exception("The server this forward belongs to no longer exists.")
                    )

                val client = existingClient(forward.serverId) ?: run {
                    val result = sshRepository.connect(server, keepAlive = true)
                    val connected = result.getOrElse { error ->
                        return@withContext Result.failure(error)
                    }
                    clients[forward.serverId] = connected
                    connected
                }

                val tunnel = SshPortForward()
                val boundPort = tunnel.start(
                    client = client,
                    remotePort = forward.remotePort,
                    remoteHost = forward.remoteHost,
                    requestedLocalPort = forward.localPort
                ).getOrElse { error ->
                    releaseClientIfUnused(forward.serverId)
                    return@withContext Result.failure(error)
                }

                forwards[forward.id] = tunnel
                val entry = ActiveTunnel(
                    forwardId = forward.id,
                    serverId = forward.serverId,
                    serverName = server.name,
                    label = forward.label,
                    localPort = boundPort,
                    remoteHost = forward.remoteHost,
                    remotePort = forward.remotePort
                )
                _active.value = _active.value + entry
                Result.success(entry)
            }
        }

    suspend fun stop(forwardId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val serverId = _active.value.firstOrNull { it.forwardId == forwardId }?.serverId
            runCatching { forwards.remove(forwardId)?.stop() }
            _active.value = _active.value.filterNot { it.forwardId == forwardId }
            if (serverId != null) releaseClientIfUnused(serverId)
        }
    }

    suspend fun stopAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            forwards.values.forEach { runCatching { it.stop() } }
            forwards.clear()
            _active.value = emptyList()
            clients.values.forEach { client ->
                runCatching { client.connection.keepAlive.interrupt() }
                runCatching { if (client.isConnected) client.disconnect() }
            }
            clients.clear()
        }
    }

    /**
     * Drops forwards whose SSH connection has died, so the UI stops claiming they are up.
     * Called by the service on its own schedule.
     */
    suspend fun pruneDead() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dead = _active.value.filter { entry ->
                val client = clients[entry.serverId]
                client == null || !client.isConnected || forwards[entry.forwardId]?.isRunning != true
            }
            if (dead.isEmpty()) return@withLock

            dead.forEach { entry ->
                Log.i("OutpostTunnel", "Dropping dead forward ${entry.label} (${entry.localPort})")
                runCatching { forwards.remove(entry.forwardId)?.stop() }
            }
            _active.value = _active.value - dead.toSet()
            dead.map { it.serverId }.distinct().forEach { releaseClientIfUnused(it) }
        }
    }

    private fun existingClient(serverId: String): SSHClient? {
        val client = clients[serverId] ?: return null
        if (client.isConnected && client.isAuthenticated) return client
        clients.remove(serverId)
        runCatching { client.disconnect() }
        return null
    }

    /** Closes a server's connection once nothing is forwarding over it any more. */
    private fun releaseClientIfUnused(serverId: String) {
        if (_active.value.any { it.serverId == serverId }) return
        val client = clients.remove(serverId) ?: return
        runCatching { client.connection.keepAlive.interrupt() }
        runCatching { if (client.isConnected) client.disconnect() }
    }
}
