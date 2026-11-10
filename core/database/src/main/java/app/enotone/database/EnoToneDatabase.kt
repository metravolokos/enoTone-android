package app.enotone.database

import androidx.room.AutoMigration
import androidx.room.Database
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
}
