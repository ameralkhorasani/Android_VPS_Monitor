package io.github.ameralkhorasani.outpost.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val encryptedPrivateKey: String, // AES-encrypted OpenSSH key in Base64
    val publicKey: String? = null,
    val keyPassphrase: String? = null, // Encrypted if present
    val osType: String = "linux",
    val healthScore: Int = 100,
    val isOnline: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    // Port code-server listens on remotely (always bound to the VPS loopback)
    val codeServerPort: Int = 8080,
    // AES-encrypted code-server login password, used for auto sign-in
    val encryptedCodeServerPassword: String? = null,

    // Last metrics actually observed over SSH, so the Overview can show real numbers
    // for a server without holding a connection open to it.
    val lastCpuPercent: Float = 0f,
    val lastRamPercent: Float = 0f,
    val lastDiskPercent: Float = 0f,

    // Per-server alert thresholds
    val alertsEnabled: Boolean = true,
    val alertCpuAbove: Int = 90,
    val alertRamAbove: Int = 90,
    val alertDiskAbove: Int = 85,
    val alertSslExpiryDays: Int = 7
)
