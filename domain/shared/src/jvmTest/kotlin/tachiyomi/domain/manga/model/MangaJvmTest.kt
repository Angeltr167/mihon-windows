package tachiyomi.domain.manga.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MangaJvmTest {

    @Test
    fun `shared manga defaults are usable on plain JVM`() {
        val manga = Manga.create()

        assertEquals(-1L, manga.id)
        assertEquals(UpdateStrategy.ALWAYS_UPDATE, manga.updateStrategy)
        assertFalse(manga.favorite)
    }
}
