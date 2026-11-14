package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompilationGroupingTest {
    private fun song(id: String, album: String?, artist: String?, albumArtist: String?) =
        OfflineTrackRow(id, id, artist, album, albumArtist, hasStoredTagData = true)

    @Test fun allExplicitAliasesBecomeOneFirstGroup() {
        val names = listOf("Various Artists", "VA", "Разные исполнители", "Сборник", " various artists ")
        val rows = names.mapIndexed { index, name ->
            song("va-$index", "Сборник $index", "Певец", name)
        } + song("ordinary", "Сольный", "Певец", "Певец")
        val groups = OfflineLibraryGrouping.albumArtists(rows)
        assertEquals(OfflineLibraryGrouping.COMPILATIONS_KEY, groups.first().key)
        assertEquals(names.indices.map { "va-$it" }.toSet(), groups.first().trackIds)
        assertEquals("Певец", groups.last().name)
        assertFalse(groups.last().trackIds.any { it.startsWith("va-") })
    }

    @Test fun missingAlbumArtistWithTwoPerformersIsCompilation() {
        val tracks = listOf(
            song("one", "Общий релиз", "Певец 1", null),
            song("two", "Общий релиз", "Певец 2", null),
            song("three", "Одиночный релиз", "Певец 3", null),
        )
        assertEquals(setOf("one", "two"), OfflineLibraryGrouping.compilationTrackIds(tracks))
        assertEquals(setOf("one", "two"), OfflineLibraryGrouping.albumArtists(tracks).single().trackIds)
    }

    @Test fun onePerformerOrMissingAlbumDoesNotBecomeCompilation() {
        val tracks = listOf(
            song("one", "Сольный", "Один", null),
            song("two", "Сольный", "Один", null),
            song("missing", null, "Другой", null),
            song("blank", "Пустой", null, null),
        )
        assertTrue(OfflineLibraryGrouping.compilationTrackIds(tracks).isEmpty())
        assertTrue(OfflineLibraryGrouping.albumArtists(tracks).isEmpty())
    }

    @Test fun explicitAlbumArtistTakesPrecedenceOverTrackPerformers() {
        val tracks = listOf(
            song("one", "Совместный", "Первый", "Автор"),
            song("two", "Совместный", "Второй", "Автор"),
        )
        assertTrue(OfflineLibraryGrouping.compilationTrackIds(tracks).isEmpty())
        assertEquals("Автор", OfflineLibraryGrouping.albumArtists(tracks).single().name)
    }
}
