package app.enotone.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RemoteTrackEntity::class], version = 1, exportSchema = true)
abstract class EnoToneDatabase : RoomDatabase() {
    abstract fun remoteTrackDao(): RemoteTrackDao
}
