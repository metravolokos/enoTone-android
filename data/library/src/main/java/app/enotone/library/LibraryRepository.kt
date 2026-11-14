package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueTrack
import app.enotone.playback.WebDavPlaybackTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val ACCOUNT_KEY_LENGTH = 64

/** One snapshot for the entire screen: no per-tap Room or network reads. */
data class LibrarySnapshot(
    val tracks: List<OfflineTrackRow>,
    val preparedById: Map<String, QueueTrack>,
)

interface LibraryRepository {
    suspend fun load(email: String): LibrarySnapshot
    suspend fun refreshAlbum(email: String, password: String, ids: List<String>): AlbumTagRefreshReport
    suspend fun readSavedQueue(): PersistedQueueState?
    /** Null is reserved for test fakes that model only an in-memory snapshot. */
    suspend fun searchTrackIds(email: String, query: String): Set<String>? = null
    suspend fun saveDurationIfUnknown(
        sourceKey: String,
        trackId: String,
        durationMs: Long,
    ): Boolean = false
}

/** Pure conversion used by the Room implementation and ordinary JVM tests. */
object LibraryCatalogMapper {
    fun map(sourceKey: String, remote: List<RemoteTrackEntity>): LibrarySnapshot {
        val catalogue = OfflineTrackCatalog.build(sourceKey, remote)
        val byId = remote.associateBy { it.id }
        val prepared = catalogue.mapNotNull { shown ->
            val item = byId[shown.id] ?: return@mapNotNull null
            val size = item.remoteSize
            if (item.missingScans != 0 || size == null || size <= 0L) null
            else if (runCatching { WebDavPlaybackTarget.url(item.encodedPath) }.isFailure) null
            else QueueTrack(
                id = shown.id,
                title = shown.title,
                encodedPath = item.encodedPath,
                remoteSize = size,
                remoteEtag = item.remoteEtag,
                artist = shown.artist,
                album = shown.album,
            )
        }.associateBy { it.id }
        return LibrarySnapshot(catalogue, prepared)
    }
}

/**
 * The checkpoint reader is injected: the data module does not own the
 * Android service lifecycle. All I/O, including JSON checkpoint reads,
 * is dispatched off main. Room's database is the process singleton.
 */
class RoomLibraryRepository(
    private val database: EnoToneDatabase,
    private val io: CoroutineDispatcher,
    private val savedQueueReader: () -> PersistedQueueState?,
) : LibraryRepository {
    override suspend fun load(email: String): LibrarySnapshot = withContext(io) {
        val key = LibrarySnapshotPlanner.accountKey(email)
        val remote = database.remoteTrackDao().allForSource(key)
        TrackSearchIndex(database).reconcile(key, remote)
        LibraryCatalogMapper.map(key, remote)
    }

    override suspend fun searchTrackIds(email: String, query: String): Set<String> =
        withContext(io) {
            val expression = LibraryText.ftsMatchExpression(query) ?: return@withContext emptySet()
            val key = LibrarySnapshotPlanner.accountKey(email)
            database.trackSearchDao().searchTrackIds(key, expression).toSet()
        }

    override suspend fun refreshAlbum(
        email: String,
        password: String,
        ids: List<String>,
    ): AlbumTagRefreshReport = withContext(io) {
        AlbumTagRefresher(database).refresh(email, password, ids)
    }

    override suspend fun readSavedQueue(): PersistedQueueState? =
        withContext(io) { savedQueueReader() }

    override suspend fun saveDurationIfUnknown(
        sourceKey: String,
        trackId: String,
        durationMs: Long,
    ): Boolean = withContext(io) {
        if (durationMs <= 0L || sourceKey.length != ACCOUNT_KEY_LENGTH ||
            sourceKey.any { it !in "0123456789abcdef" }) return@withContext false
        database.remoteTrackDao().saveDurationIfUnknown(
            trackId, sourceKey, durationMs,
        ) == 1
    }
}
