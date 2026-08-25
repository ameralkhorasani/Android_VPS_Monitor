package io.github.ameralkhorasani.outpost.ssh.tunnel

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * One SSH local port forward: `ssh -L localPort:remoteHost:remotePort`.
 *
 * Traffic to the phone's loopback on [localPort] is carried inside the authenticated SSH
 * connection and emerges on the VPS, which then dials `remoteHost:remotePort` from its own
 * side. The remote service therefore never has to listen on a public interface, and no
 * firewall port has to be opened.
 *
 * The listening socket is bound to loopback, so only this phone can reach it - but every
 * app on the phone can, which is the point: Chrome opening http://localhost:8090 hits the
 * same socket the app opened.
 */
class SshPortForward {

    private var serverSocket: ServerSocket? = null
    private var forwarder: LocalPortForwarder? = null
    private var listenThread: Thread? = null

    /** Loopback port on the phone, or -1 when not running. */
    @Volatile
    var localPort: Int = -1
        private set

    val isRunning: Boolean
        get() = forwarder?.isRunning == true

    /**
     * Opens the forward and returns the local port actually bound.
     *
     * [requestedLocalPort] of 0 lets the OS choose a free port, which is what the code
     * editor wants since its URL is only ever handled inside the app. Anything else pins
     * the port so the address stays typeable in a browser - at the cost of failing when
     * something else already holds it.
     */
    fun start(
        client: SSHClient,
        remotePort: Int,
        remoteHost: String = "127.0.0.1",
        requestedLocalPort: Int = 0
    ): Result<Int> {
        stop()
        return try {
            val loopback = InetAddress.getLoopbackAddress()
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(loopback, requestedLocalPort))
            serverSocket = socket

            val boundPort = socket.localPort
            val params = Parameters(loopback.hostAddress, boundPort, remoteHost, remotePort)
            val fwd = client.newLocalPortForwarder(params, socket)
            forwarder = fwd

            val thread = Thread({
                try {
                    // Blocks until close() interrupts this thread and closes the socket.
                    fwd.listen(Thread.currentThread())
                } catch (_: Exception) {
                }
            }, "outpost-forward-$boundPort")
            thread.isDaemon = true
            thread.start()
            listenThread = thread

            localPort = boundPort
            Result.success(boundPort)
        } catch (e: Exception) {
            stop()
            Result.failure(describeBindFailure(e, requestedLocalPort))
        }
    }

    /**
     * "Address already in use" and "Permission denied" are the two failures a user can
     * actually do something about, so they get told which one it is.
     */
    private fun describeBindFailure(e: Exception, port: Int): Exception {
        val raw = e.message.orEmpty()
        return when {
            port in 1..1023 && raw.contains("Permission denied", ignoreCase = true) ->
                Exception(
                    "Android does not let an app listen on port $port. Ports below 1024 " +
                        "are reserved - use something above 1024 locally and point it at " +
                        "the remote port you want."
                )

            raw.contains("Address already in use", ignoreCase = true) || raw.contains("EADDRINUSE") ->
                Exception(
                    "Local port $port is already taken on this phone, most likely by a " +
                        "forward that is still open. Stop it, or choose another local port."
                )

            else -> Exception(raw.ifBlank { "Could not open the forward (${e.javaClass.simpleName})" }, e)
        }
    }

    fun stop() {
        try {
            forwarder?.close()
        } catch (_: Exception) {
        }
        try {
            serverSocket?.takeIf { !it.isClosed }?.close()
        } catch (_: Exception) {
        }
        try {
            listenThread?.interrupt()
        } catch (_: Exception) {
        }
        forwarder = null
        serverSocket = null
        listenThread = null
        localPort = -1
    }
}
