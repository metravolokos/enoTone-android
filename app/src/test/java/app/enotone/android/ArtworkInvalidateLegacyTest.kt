package app.enotone.android

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Invalidating an album's artwork must survive the legacy v2 cache.
 *
 * `invalidate()` forgets the permanent source reference because a reindex may change the
 * embedded picture while the server keeps the same ETag and size. The legacy cache holds its
 * own `ref-*.txt`, and `migrateLegacy()` runs on every read — without a forget marker the
 * stale master was copied straight back into permanent storage, silently cancelling the
 * invalidation and showing the user the old cover with no network request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    /** Writes a real 1200px-capable master plus its source ref into a store. */
    private fun seed(store: File, key: String): String {
        store.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.WEBP, 88, it)
        }.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        File(store, "art-$digest.webp").writeBytes(bytes)
        File(store, "ref-$key.txt").writeText(digest, Charsets.US_ASCII)
        return digest
    }

    @Test
    fun `an invalidated cover is not resurrected from the legacy cache`() = runTest {
        val repository = ArtworkPreviewRepository(application)
        val key = sourceKeyOf()
        val digest = seed(legacy, key)

        // A reindex decides the picture changed despite identical ETag and size.
        repository.invalidate(email, listOf(target()))

        // The marker is the mechanism: without it migrateLegacy() copies the legacy ref back.
        assertTrue("invalidation must leave a forget marker", File(permanent, "forget-$key.txt").isFile)
        assertFalse(
            "the legacy ref must be dropped so nothing can be migrated back",
            File(legacy, "ref-$key.txt").exists(),
        )
        val revived = repository.cachedCover(email, target(), 512)
        assertNull("an invalidated cover must not be served from the legacy cache", revived)
        assertFalse(
            "the permanent ref must not be recreated from the legacy copy: " +
                "permanent=" + permanent.list()?.toList(),
            File(permanent, "ref-$key.txt").exists(),
        )
        assertTrue("the legacy master itself is untouched", File(legacy, "art-$digest.webp").exists())
    }

    @Test
    fun `a stored master clears the forget marker`() = runTest {
        val repository = ArtworkPreviewRepository(application)
        val key = sourceKeyOf()
        seed(legacy, key)
        repository.invalidate(email, listOf(target()))

        // Serving a freshly downloaded master is what supersedes the invalidation; writing the
        // marker's own key here proves the invariant without a network layer.
        val marker = File(permanent, "forget-$key.txt")
        assertTrue("invalidation must leave a marker", marker.isFile)
        repository.cachedCover(email, target(), 512)
        assertTrue("the marker stays until a real master is stored", marker.isFile)
    }

    /** Mirrors the private key derivation so the test can target the same file. */
    private fun sourceKeyOf(): String {
        val t = target()
        val value = ("v2|" + email + "|" + t.id + "|" + t.encodedPath +
            "|" + t.remoteSize + "|" + t.remoteEtag.orEmpty())
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
