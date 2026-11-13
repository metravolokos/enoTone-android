package app.enotone.android.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EnoLetterAvatarTest {
    @Test fun latinCyrillicAndEmptyName() {
        assertEquals("A", avatarInitial("alice"))
        assertEquals("Ж", avatarInitial("жираф"))
        assertEquals("?", avatarInitial(""))
        assertEquals("?", avatarInitial("  "))
        assertEquals("1", avatarInitial("123"))
    }

    @Test fun composedCharacterAndEmojiRemainComplete() {
        assertEquals("É", avatarInitial("e\u0301cho", Locale.ROOT))
        assertEquals("😀", avatarInitial("😀 dance"))
        assertTrue(avatarInitial("🦝 Enot").isNotEmpty())
    }

    @Test fun paletteSelectionIsDeterministic() {
        assertEquals(avatarPaletteIndex("Альбом"), avatarPaletteIndex("Альбом"))
        assertEquals(avatarPaletteIndex("ABC"), avatarPaletteIndex("ABC"))
        for (name in listOf("", "AA", "Ж", "🦝", "v2")) {
            assertTrue(avatarPaletteIndex(name) in 0..5)
        }
    }
}
