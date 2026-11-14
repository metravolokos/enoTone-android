package app.enotone.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(
    entities = [RemoteTrackEntity::class, PlaylistEntity::class, PlaylistItemEntity::class],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
    exportSchema = true,
)
abstract class EnoToneDatabase : RoomDatabase() {
    abstract fun remoteTrackDao(): RemoteTrackDao
    abstract fun playlistDao(): PlaylistDao

    /** Playlist rows are removed before tracks; item foreign keys then cascade in one commit. */
    suspend fun deleteAccountData(sourceKey: String): Int = withTransaction {
        playlistDao().deleteAllForSource(sourceKey)
        remoteTrackDao().deleteAllForSource(sourceKey)
    }

    companion object {
        /** File name is part of the on-device contract: renaming it loses the library. */
        const val FILE_NAME = "enotone-local-library.db"

        @Volatile
        private var instance: EnoToneDatabase? = null

        /**
         * One RoomDatabase per process (ADR-0074, M6-0/2). Separate instances for
         * UI and foreground services do not share an InvalidationTracker and
         * compete for SQLite write locks. Callers MUST NOT close the returned
         * instance: it lives as long as the process.
         */
        fun getInstance(context: Context): EnoToneDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EnoToneDatabase::class.java,
                    FILE_NAME,
                ).build().also { instance = it }
            }
    }
}
