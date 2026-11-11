package app.enotone.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RemoteTrackEntity::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
    exportSchema = true,
)
abstract class EnoToneDatabase : RoomDatabase() {
    abstract fun remoteTrackDao(): RemoteTrackDao

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
