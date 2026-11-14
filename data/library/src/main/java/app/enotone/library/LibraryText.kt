package app.enotone.library

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Shared on-device normalization and sorting. No network or logging. */
object LibraryText {
    private val russian = Locale.forLanguageTag("ru")
    private val spaces = Regex("\\s+")
    private val marks = Regex("\\p{M}+")
    private val ftsWords = Regex("[\\p{L}\\p{N}]+")
    private val collators: ThreadLocal<Collator> = ThreadLocal.withInitial {
        Collator.getInstance(russian).apply { strength = Collator.SECONDARY }
    }

    /** Preserve й (и + breve); intentionally fold ё -> е and other diacritics. */
    fun searchKey(text: String): String {
        val decomposed = Normalizer.normalize(
            Normalizer.normalize(text, Normalizer.Form.NFC), Normalizer.Form.NFD,
        )
        val protectedShortI = buildString(decomposed.length) {
            var pos = 0
            while (pos < decomposed.length) {
                val letter = decomposed[pos]
                val isShortI = letter == 'и' || letter == 'И'
                val followedByBreve = pos + 1 < decomposed.length &&
                    decomposed[pos + 1] == '\u0306'
                if (isShortI && followedByBreve) {
                    append(if (letter == 'И') 'Й' else 'й')
                    pos += 2
                } else {
                    append(letter)
                    pos++
                }
            }
        }
        return marks.replace(protectedShortI, "").lowercase(russian)
            .trim().replace(spaces, " ")
    }

    fun sortKey(text: String, dropArticle: Boolean): String {
        val trimmed = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
        return if (dropArticle && trimmed.startsWith("The ", ignoreCase = true))
            trimmed.drop("The ".length).trimStart() else trimmed
    }

    fun compare(left: String, right: String, dropArticle: Boolean = false): Int =
        collators.get().compare(sortKey(left, dropArticle), sortKey(right, dropArticle))

    /** DAO binds the MATCH expression; adjacent quoted prefixes give portable implicit AND. */
    fun ftsMatchExpression(query: String): String? {
        val terms = ftsWords.findAll(searchKey(query)).map { it.value }.toList()
        return terms.takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { "\"$it*\"" }
    }

    /** The much smaller album/artist group collection stays in memory. */
    fun containsAllTerms(text: String, query: String): Boolean {
        val terms = searchKey(query).split(' ').filter(String::isNotEmpty)
        val haystack = searchKey(text)
        return terms.all(haystack::contains)
    }
}
