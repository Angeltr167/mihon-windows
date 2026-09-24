package mihon.desktop

import androidx.compose.ui.input.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReaderShortcutsTest {
    @Test
    fun `custom keys dispatch only their configured action`() {
        val shortcuts = ReaderShortcuts(ReaderShortcutKey.N, ReaderShortcutKey.P)
        assertEquals(ReaderShortcuts.Action.NEXT, shortcuts.action(Key.N))
        assertEquals(ReaderShortcuts.Action.PREVIOUS, shortcuts.action(Key.P))
        assertNull(shortcuts.action(Key.J))
    }

    @Test
    fun `cycling and restoration cannot duplicate keys`() {
        val next = ReaderShortcutKey.J.nextExcept(ReaderShortcutKey.K)
        assertNotEquals(ReaderShortcutKey.K, next)
        assertEquals(ReaderShortcutKey.J, ReaderShortcutKey.restore("invalid", ReaderShortcutKey.J))
        assertEquals(ReaderShortcutKey.D, ReaderShortcutKey.restore("D", ReaderShortcutKey.J))
        assertThrows(IllegalArgumentException::class.java) {
            ReaderShortcuts(ReaderShortcutKey.J, ReaderShortcutKey.J)
        }
    }
}
