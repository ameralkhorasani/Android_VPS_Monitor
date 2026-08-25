package io.github.ameralkhorasani.outpost.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.ameralkhorasani.outpost.data.model.PortForwardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortForwardDao {

    @Query("SELECT * FROM port_forwards WHERE serverId = :serverId ORDER BY localPort ASC")
    fun getForServer(serverId: String): Flow<List<PortForwardEntity>>

    @Query("SELECT * FROM port_forwards WHERE id = :id")
    suspend fun getById(id: String): PortForwardEntity?

    @Query("SELECT * FROM port_forwards WHERE autoStart = 1")
    suspend fun getAutoStart(): List<PortForwardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(forward: PortForwardEntity)

    @Delete
    suspend fun delete(forward: PortForwardEntity)

    @Query("UPDATE port_forwards SET autoStart = :autoStart WHERE id = :id")
    suspend fun setAutoStart(id: String, autoStart: Boolean)

    /** Keeps forwards from outliving the server they belong to. */
    @Query("DELETE FROM port_forwards WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)
}
