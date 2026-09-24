package mihon.core.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReaderNavigationTest {
    @Test
    fun `navigation crosses chapter boundaries and stops at ends`() {
        val navigation = ReaderNavigation(listOf(2, 3), ReaderPosition(0, 0))
        assertNull(navigation.previous())
        assertEquals(ReaderPosition(0, 1), navigation.next())
        assertEquals(ReaderPosition(1, 0), navigation.next())
        navigation.goTo(ReaderPosition(1, 2))
        assertNull(navigation.next())
        assertEquals(ReaderPosition(1, 1), navigation.previous())
    }
}
