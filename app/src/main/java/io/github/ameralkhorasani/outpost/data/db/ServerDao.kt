package io.github.ameralkhorasani.outpost.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerById(id: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity)

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Delete
    suspend fun deleteServer(server: ServerEntity)

    @Query("UPDATE servers SET isOnline = :isOnline, healthScore = :healthScore, lastSeenTimestamp = :timestamp WHERE id = :id")
    suspend fun updateServerStatus(id: String, isOnline: Boolean, healthScore: Int, timestamp: Long = System.currentTimeMillis())

    /** Records a real metrics sample taken over SSH. */
    @Query(
        """
        UPDATE servers SET
            isOnline = :isOnline,
            healthScore = :healthScore,
            lastCpuPercent = :cpuPercent,
            lastRamPercent = :ramPercent,
            lastDiskPercent = :diskPercent,
            lastSeenTimestamp = :timestamp
        WHERE id = :id
        """
    )
    suspend fun updateLiveStats(
        id: String,
        isOnline: Boolean,
        healthScore: Int,
        cpuPercent: Float,
        ramPercent: Float,
        diskPercent: Float,
        timestamp: Long = System.currentTimeMillis()
    )

    /** Marks a server unreachable without clobbering its last known metrics. */
    @Query("UPDATE servers SET isOnline = 0, healthScore = 0 WHERE id = :id")
    suspend fun markOffline(id: String)

    @Query(
        """
        UPDATE servers SET
            alertsEnabled = :enabled,
            alertCpuAbove = :cpuAbove,
            alertRamAbove = :ramAbove,
            alertDiskAbove = :diskAbove,
            alertSslExpiryDays = :sslExpiryDays
        WHERE id = :id
        """
    )
    suspend fun updateAlertSettings(
        id: String,
        enabled: Boolean,
        cpuAbove: Int,
        ramAbove: Int,
        diskAbove: Int,
        sslExpiryDays: Int
    )
}
