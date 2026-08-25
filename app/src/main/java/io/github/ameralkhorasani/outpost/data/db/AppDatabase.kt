package io.github.ameralkhorasani.outpost.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.ameralkhorasani.outpost.data.model.PortForwardEntity
import io.github.ameralkhorasani.outpost.data.model.ServerEntity

@Database(
    entities = [ServerEntity::class, PortForwardEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun portForwardDao(): PortForwardDao

    companion object {
        /**
         * Adds the code-server columns. Written as a real migration rather than a
         * destructive fallback so existing servers - and their stored SSH keys -
         * survive the upgrade.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE servers ADD COLUMN codeServerPort INTEGER NOT NULL DEFAULT 8080"
                )
                db.execSQL(
                    "ALTER TABLE servers ADD COLUMN encryptedCodeServerPassword TEXT"
                )
            }
        }

        /** Adds cached live metrics and per-server alert thresholds. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE servers ADD COLUMN lastCpuPercent REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE servers ADD COLUMN lastRamPercent REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE servers ADD COLUMN lastDiskPercent REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE servers ADD COLUMN alertsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE servers ADD COLUMN alertCpuAbove INTEGER NOT NULL DEFAULT 90")
                db.execSQL("ALTER TABLE servers ADD COLUMN alertRamAbove INTEGER NOT NULL DEFAULT 90")
                db.execSQL("ALTER TABLE servers ADD COLUMN alertDiskAbove INTEGER NOT NULL DEFAULT 85")
                db.execSQL("ALTER TABLE servers ADD COLUMN alertSslExpiryDays INTEGER NOT NULL DEFAULT 7")
            }
        }

        /** Adds saved SSH local port forwards. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS port_forwards (
                        id TEXT NOT NULL PRIMARY KEY,
                        serverId TEXT NOT NULL,
                        label TEXT NOT NULL,
                        remoteHost TEXT NOT NULL,
                        remotePort INTEGER NOT NULL,
                        localPort INTEGER NOT NULL,
                        autoStart INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_port_forwards_serverId " +
                        "ON port_forwards (serverId)"
                )
            }
        }
    }
}
