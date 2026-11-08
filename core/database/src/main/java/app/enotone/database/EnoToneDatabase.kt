package app.enotone.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RemoteTrackEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class EnoToneDatabase : RoomDatabase() {
    abstract fun remoteTrackDao(): RemoteTrackDao
}
