package app.enotone.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PendingSignOutRecoveryTest {
    private val sourceKey = "a".repeat(64)
    private val otherSourceKey = "b".repeat(64)

    private enum class Interruption { AFTER_VAULT_ENTRY, AFTER_KEY_ALIAS, AFTER_MANIFEST, BEFORE_MARKER_FINISH }

    private data class State(
        var pending: Boolean = true,
        var cleanupConfirmed: Boolean = true,
        var sourceKey: String? = "a".repeat(64),
        var vaultEntry: Boolean = true,
        var keyAlias: Boolean = true,
        val manifests: MutableSet<String> = mutableSetOf("a".repeat(64), "b".repeat(64)),
        var interruption: Interruption? = null,
        var clearCalls: Int = 0,
        val manifestDeleteCalls: MutableList<String> = mutableListOf(),
    )

    private fun recovery(state: State) = PendingSignOutRecovery(
        pending = { state.pending },
        cleanupConfirmed = { state.cleanupConfirmed },
        sourceKey = { state.sourceKey },
        clearCredentials = {
            state.clearCalls++
            if (state.vaultEntry) {
                state.vaultEntry = false
                interrupt(state, Interruption.AFTER_VAULT_ENTRY)
            }
            if (state.keyAlias) {
                state.keyAlias = false
                interrupt(state, Interruption.AFTER_KEY_ALIAS)
            }
        },
        deleteArtworkManifest = { savedSourceKey ->
            state.manifestDeleteCalls += savedSourceKey
            state.manifests.remove(savedSourceKey)
            interrupt(state, Interruption.AFTER_MANIFEST)
        },
        finish = {
            interrupt(state, Interruption.BEFORE_MARKER_FINISH)
            state.pending = false
            state.cleanupConfirmed = false
            state.sourceKey = null
        },
    )

    private fun interrupt(state: State, point: Interruption) {
        if (state.interruption == point) {
            state.interruption = null
            error("simulated process interruption")
        }
    }

    @Test fun `legacy empty vault without proof or source key remains fail closed`() {
        listOf(
            State(cleanupConfirmed = false, vaultEntry = false, keyAlias = false),
            State(cleanupConfirmed = true, sourceKey = null, vaultEntry = false, keyAlias = false),
        ).forEach { state ->
            assertFalse(recovery(state).run())
            assertTrue(state.pending)
            assertTrue(state.manifests.contains(sourceKey))
            assertEquals(0, state.clearCalls)
            assertTrue(state.manifestDeleteCalls.isEmpty())
        }
    }

    @Test fun `confirmed cleanup handles every credential and manifest interruption idempotently`() {
        Interruption.entries.forEach { interruption ->
            val state = State(interruption = interruption)
            try {
                recovery(state).run()
                throw AssertionError("Expected interruption at $interruption")
            } catch (_: IllegalStateException) {
                // Resume below from the state persisted before the simulated process death.
            }

            assertTrue(state.pending)
            assertTrue(state.cleanupConfirmed)
            assertTrue(state.manifests.contains(otherSourceKey))
            when (interruption) {
                Interruption.AFTER_VAULT_ENTRY -> {
                    assertFalse(state.vaultEntry)
                    assertTrue(state.keyAlias)
                    assertTrue(state.manifests.contains(sourceKey))
                }
                Interruption.AFTER_KEY_ALIAS -> {
                    assertFalse(state.vaultEntry)
                    assertFalse(state.keyAlias)
                    assertTrue(state.manifests.contains(sourceKey))
                }
                Interruption.AFTER_MANIFEST,
                Interruption.BEFORE_MARKER_FINISH -> {
                    assertFalse(state.vaultEntry)
                    assertFalse(state.keyAlias)
                    assertFalse(state.manifests.contains(sourceKey))
                }
            }

            assertTrue(recovery(state).run())
            assertFalse(state.pending)
            assertFalse(state.cleanupConfirmed)
            assertFalse(state.vaultEntry)
            assertFalse(state.keyAlias)
            assertFalse(state.manifests.contains(sourceKey))
            assertTrue(state.manifests.contains(otherSourceKey))
            assertTrue(state.manifestDeleteCalls.all { it == sourceKey })
            val expectedDeleteAttempts = if (interruption == Interruption.AFTER_MANIFEST ||
                interruption == Interruption.BEFORE_MARKER_FINISH
            ) 2 else 1
            assertEquals(expectedDeleteAttempts, state.manifestDeleteCalls.size)

            assertTrue(recovery(state).run())
            assertEquals(2, state.clearCalls)
            assertTrue(state.manifestDeleteCalls.all { it == sourceKey })
        }
    }

    @Test fun `recovery removes only recorded source manifest and next sign out uses fresh artwork keys`() = runTest {
        val root = Files.createTempDirectory("signout-recovery-manifest").toFile()
        try {
            val currentManifest = AccountArtworkManifest.forSource(root, sourceKey)
            val otherManifest = AccountArtworkManifest.forSource(root, otherSourceKey)
            val oldKeys = AccountArtworkKeys(setOf("c".repeat(64)), setOf("d".repeat(64)))
            val otherKeys = AccountArtworkKeys(setOf("e".repeat(64)), setOf("f".repeat(64)))
            val newKeys = AccountArtworkKeys(setOf("1".repeat(64)), setOf("2".repeat(64)))
            currentManifest.save(oldKeys)
            otherManifest.save(otherKeys)

            val state = State(manifests = mutableSetOf(sourceKey, otherSourceKey))
            assertTrue(PendingSignOutRecovery(
                pending = { state.pending },
                cleanupConfirmed = { state.cleanupConfirmed },
                sourceKey = { state.sourceKey },
                clearCredentials = {},
                deleteArtworkManifest = { savedKey ->
                    AccountArtworkManifest.forSource(root, savedKey).clear()
                },
                finish = { state.pending = false },
            ).run())

            assertNull(currentManifest.read())
            assertEquals(otherKeys, otherManifest.read())

            // A later sign-out must capture the newly indexed references, not reuse old keys.
            currentManifest.ensure { newKeys }
            assertEquals(newKeys, currentManifest.read())
            val artwork = root.resolve("art").apply { mkdirs() }
            val oldRef = oldKeys.sourceRefs.single()
            val newRef = newKeys.sourceRefs.single()
            val otherRef = otherKeys.sourceRefs.single()
            artwork.resolve("ref-$oldRef.txt").writeText("a".repeat(64))
            artwork.resolve("ref-$newRef.txt").writeText("b".repeat(64))
            artwork.resolve("ref-$otherRef.txt").writeText("c".repeat(64))
            AccountArtworkFileCleanup(artwork).removeScoped(requireNotNull(currentManifest.read()))

            assertTrue(artwork.resolve("ref-$oldRef.txt").exists())
            assertFalse(artwork.resolve("ref-$newRef.txt").exists())
            assertTrue(artwork.resolve("ref-$otherRef.txt").exists())
            assertEquals(otherKeys, otherManifest.read())
        } finally {
            root.deleteRecursively()
        }
    }
}
