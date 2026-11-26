package app.enotone.android

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Invalidating an album's artwork must survive the legacy v2 cache.
 *
 * `invalidate()` forgets the permanent source reference because a reindex may change the
 * embedded picture while the server keeps the same ETag and size. The legacy cache holds its
 * own `ref-*.txt`, and `migrateLegacy()` runs on every read: if that legacy file cannot be
 * removed, only a forget marker keeps the stale master out of permanent storage.
 *
 * These tests call the production `migrateLegacy` directly, so they fail when the marker gate
 * is removed — the previous version only asserted file presence and passed either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkInvalidateLegacyTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val email = "synthetic@example.test"
    private lateinit var permanent: File
    private lateinit var legacy: File

    @Before
    fun clean() {
        permanent = File(application.filesDir, "enotone-artwork-v2").apply { deleteRecursively() }
        legacy = File(application.cacheDir, "enotone-artwork-v2").apply { deleteRecursively() }
    }

    private fun target() = PlayingArtworkTarget(
        id = "synthetic",
        encodedPath = "/AllMusic/synthetic.mp3",
        remoteSize = 1024L,
        remoteEtag = "\"etag\"",
    )

    /** Seeds a legacy ref and master exactly as an older install would have left them. */
    private fun seedLegacy(): String {
        val key = sourceKeyOf()
        legacy.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.WEBP, 88, it)
        }.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        File(legacy, "art-$digest.webp").writeBytes(bytes)
        File(legacy, "ref-$key.txt").writeText(digest, Charsets.US_ASCII)
        return key
    }

    @Test
    fun `a forget marker keeps the legacy master out of permanent storage`() {
        val repository = ArtworkPreviewRepository(application)
        val key = seedLegacy()
        // The state after an invalidation whose legacy delete failed: ref gone, marker set.
        permanent.mkdirs()
        File(permanent, "forget-$key.txt").writeText("1", Charsets.US_ASCII)

        repository.migrateLegacyForTest(key)

        assertFalse(
            "the marker must stop migrateLegacy() from restoring the stale copy",
            File(permanent, "ref-$key.txt").exists(),
        )
    }

    @Test
    fun `without the marker the same call does restore, so the gate is the cause`() {
        val repository = ArtworkPreviewRepository(application)
        val key = seedLegacy()

        repository.migrateLegacyForTest(key)

        // Proves the previous test fails for the right reason rather than because migration
        // is broken in this environment.
        assertTrue(
            "migration itself must work when no marker is present",
            File(permanent, "ref-$key.txt").exists(),
        )
    }

    @Test
    fun `invalidate removes a removable legacy ref and leaves no marker`() {
        val repository = ArtworkPreviewRepository(application)
        val key = seedLegacy()
        runBlockingInvalidate(repository)

        assertFalse("the legacy ref must be deleted", File(legacy, "ref-$key.txt").exists())
        // A healthy cache must not accumulate tombstones in the permanent store.
        assertFalse(
            "no marker is needed once the legacy ref is gone",
            File(permanent, "forget-$key.txt").exists(),
        )
    }

    private fun runBlockingInvalidate(repository: ArtworkPreviewRepository) {
        kotlinx.coroutines.runBlocking { repository.invalidate(email, listOf(target())) }
    }

    /** Mirrors the private key derivation so the test can target the same files. */
    private fun sourceKeyOf(): String {
        val t = target()
        val value = ("v2|" + email + "|" + t.id + "|" + t.encodedPath +
            "|" + t.remoteSize + "|" + t.remoteEtag.orEmpty())
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
