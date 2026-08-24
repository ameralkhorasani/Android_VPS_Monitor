package io.github.ameralkhorasani.outpost.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A saved local port forward: `localPort` on the phone carries traffic to
 * `remoteHost:remotePort` as seen from the VPS, over the SSH connection.
 *
 * This is `ssh -L localPort:remoteHost:remotePort`. The point of pinning the local port
 * rather than taking whatever the OS offers is that the phone's browser can then be
 * pointed at a URL you can predict and type - http://localhost:8090 - instead of an
 * ephemeral one that changes on every connect.
 */
@Entity(
    tableName = "port_forwards",
    indices = [Index("serverId")]
)
data class PortForwardEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val serverId: String,
    val label: String,
    /** Host to reach *from the VPS*. Loopback covers a service bound to 127.0.0.1 there. */
    val remoteHost: String = "127.0.0.1",
    val remotePort: Int,
    /** Port opened on the phone's loopback. Same as the remote port by default. */
    val localPort: Int,
    /** Opened as soon as the Ports screen is shown. */
    val autoStart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** What to type into the phone's browser. */
    val localUrl: String get() = "http://localhost:$localPort/"
}
