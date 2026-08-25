package io.github.ameralkhorasani.outpost.data.model

data class DockerContainer(
    val id: String,
    val name: String,
    val image: String,
    /** Raw docker state: running, exited, paused, restarting, created, dead. */
    val state: String,
    /** Human-readable status, e.g. "Up 3 hours" or "Exited (0) 2 days ago". */
    val status: String,
    val ports: String = "",
    val cpuPercent: Float = 0f,
    val memUsedMb: Float = 0f,
    val memLimitMb: Float = 0f,
    val memPercent: Float = 0f
) {
    val isRunning: Boolean get() = state.equals("running", ignoreCase = true)
    val isPaused: Boolean get() = state.equals("paused", ignoreCase = true)

    /** Short id, as docker itself displays it. */
    val shortId: String get() = id.take(12)
}

/**
 * Whether the remote host can run docker commands at all, and how.
 */
data class DockerAvailability(
    val installed: Boolean = false,
    /** docker is installed and the SSH user can actually talk to the daemon. */
    val usable: Boolean = false,
    /** Command prefix that works: "docker" or "sudo -n docker". */
    val commandPrefix: String = "docker",
    val version: String? = null,
    val message: String? = null
)
