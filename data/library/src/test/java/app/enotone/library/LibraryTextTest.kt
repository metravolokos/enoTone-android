package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTextTest {
    @Test fun searchFoldsCaseDiacriticsAndSpacesButNotShortI() {
        assertEquals("ежик", LibraryText.searchKey("ЁЖИК"))
        assertEquals("йод", LibraryText.searchKey("ЙОД"))
        assertEquals("йод", LibraryText.searchKey("и\u0306од"))
        assertEquals("иод", LibraryText.searchKey("Иод"))
        assertEquals("cafe", LibraryText.searchKey("Cafe\u0301"))
        assertEquals("е й", LibraryText.searchKey("  Ё \t  Й   "))
    }

    @Test fun termsKeepTheSameSubstringAndSemanticsAsLegacySearch() {
        val text = "Ёжик в Café"
        val terms = LibraryText.searchTerms("ЕЖ cafe")
        assertEquals(listOf("еж", "cafe"), terms)
        assertTrue(LibraryText.matchesAllTerms(LibraryText.searchKey(text), terms))
        assertEquals(LibraryText.containsAllTerms(text, "ЕЖ cafe"),
            LibraryText.matchesAllTerms(LibraryText.searchKey(text), terms))
        assertFalse(LibraryText.matchesAllTerms(LibraryText.searchKey(text),
            LibraryText.searchTerms("еж nowhere")))
        assertTrue(LibraryText.matchesAllTerms(LibraryText.searchKey(text),
            LibraryText.searchTerms("")))
    }

    @Test fun theArticleIsIgnoredOnlyForPerformers() {
        assertEquals("Beatles", LibraryText.sortKey(" The Beatles ", true))
        assertEquals("The Beatles", LibraryText.sortKey("The Beatles", false))
        assertEquals(0, LibraryText.compare("The Beatles", "Beatles", true))
    }

    @Test fun russianCollatorSecondarySortOrderIsStableOnJvm() {
        val values = listOf("Яблоко", "The Beatles", "Еж", "Ёж", "Ива", "Йод",
            "Aha", "9", "Бобр", "", "Zebra")
        assertEquals(listOf("", "9", "Aha", "The Beatles", "Zebra", "Бобр",
            "Еж", "Ёж", "Ива", "Йод", "Яблоко"),
            values.sortedWith { a, b -> LibraryText.compare(a, b, true) })
        assertEquals(0, LibraryText.compare("ЁЖ", "ёж"))
        assertTrue(LibraryText.compare("е", "ё") < 0)
    }

    @Test fun smallCollectionsUseSameNormalizer() {
        assertTrue(LibraryText.containsAllTerms("Мой Ёжик", "ежик МОЙ"))
        assertTrue(LibraryText.containsAllTerms("Café del Mar", "cafe"))
        assertFalse(LibraryText.containsAllTerms("Иван", "йва"))
    }
}
