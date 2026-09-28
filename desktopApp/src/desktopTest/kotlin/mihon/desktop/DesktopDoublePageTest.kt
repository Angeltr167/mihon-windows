package mihon.desktop

import mihon.core.reader.ReadingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopDoublePageTest {

    @Test
    fun `chapter transitions override stored progress while direct opens restore it`() {
        assertEquals(4, readerStartPage(ReaderChapterOpenIntent.RESTORE, storedPage = 4, pageCount = 10))
        assertEquals(0, readerStartPage(ReaderChapterOpenIntent.FIRST, storedPage = 4, pageCount = 10))
        assertEquals(9, readerStartPage(ReaderChapterOpenIntent.LAST, storedPage = 4, pageCount = 10))
        assertEquals(9, readerStartPage(ReaderChapterOpenIntent.RESTORE, storedPage = 99, pageCount = 10))
    }

    @Test
    fun `reader controls stay visible while any chrome interaction is active`() {
        assertTrue(
            shouldAutoHideReaderControls(
                showAppearance = false,
                showShortcuts = false,
                showMore = false,
                showModeMenu = false,
                showFitMenu = false,
                sliderDragging = false,
                controlsHovered = false,
            ),
        )
        assertTrue(
            !shouldAutoHideReaderControls(
                showAppearance = false,
                showShortcuts = false,
                showMore = false,
                showModeMenu = true,
                showFitMenu = false,
                sliderDragging = false,
                controlsHovered = false,
            ),
        )
        assertTrue(
            !shouldAutoHideReaderControls(
                showAppearance = false,
                showShortcuts = false,
                showMore = false,
                showModeMenu = false,
                showFitMenu = false,
                sliderDragging = true,
                controlsHovered = false,
            ),
        )
        assertTrue(
            !shouldAutoHideReaderControls(
                showAppearance = false,
                showShortcuts = false,
                showMore = false,
                showModeMenu = false,
                showFitMenu = false,
                sliderDragging = false,
                controlsHovered = true,
            ),
        )
    }

    @Test
    fun `reader boundary controls stop only at the global chapter edges`() {
        assertEquals(
            ReaderBoundaryState(canPrevious = false, canNext = true),
            readerBoundaryState(
                mode = ReadingMode.SINGLE_LTR,
                pageIndex = 0,
                pageCount = 4,
                chapterIndex = 0,
                chapterCount = 2,
            ),
        )
        assertEquals(
            ReaderBoundaryState(canPrevious = true, canNext = true),
            readerBoundaryState(
                mode = ReadingMode.DOUBLE_RTL,
                pageIndex = 3,
                pageCount = 5,
                chapterIndex = 0,
                chapterCount = 2,
            ),
        )
        assertEquals(
            ReaderBoundaryState(canPrevious = true, canNext = false),
            readerBoundaryState(
                mode = ReadingMode.SINGLE_RTL,
                pageIndex = 2,
                pageCount = 3,
                chapterIndex = 1,
                chapterCount = 2,
            ),
        )
        assertEquals(
            ReaderBoundaryState(canPrevious = false, canNext = false),
            readerBoundaryState(
                mode = ReadingMode.WEBTOON,
                pageIndex = 0,
                pageCount = 0,
                chapterIndex = 0,
                chapterCount = 1,
            ),
        )
    }

    @Test
    fun `touchpad deltas must accumulate and a high resolution event turns one page`() {
        var accumulated = 0f
        var result = accumulateReaderWheelDelta(accumulated, 0.3f)
        accumulated = result.remainder
        assertNull(result.direction)

        result = accumulateReaderWheelDelta(accumulated, 0.3f)
        accumulated = result.remainder
        assertNull(result.direction)

        result = accumulateReaderWheelDelta(accumulated, 0.4f)
        assertEquals(1, result.direction)
        assertEquals(0f, result.remainder)

        result = accumulateReaderWheelDelta(0f, -4f)
        assertEquals(-1, result.direction)
        assertEquals(0f, result.remainder)
    }

    @Test
    fun `continuous reader progress remains on the requested page`() {
        for (mode in listOf(ReadingMode.SINGLE_LTR, ReadingMode.VERTICAL, ReadingMode.WEBTOON)) {
            assertEquals(3, normalizeReaderPageIndex(mode, 3, 7))
            assertEquals(3, viewedReaderPageIndex(mode, 3, 7))
        }
    }

    @Test
    fun `cover is alone and every page appears once for even and odd chapter lengths`() {
        for (pageCount in 1..9) {
            val visited = mutableListOf<Int>()
            var start = 0
            while (true) {
                val spread = doublePageSpread(start, pageCount)
                visited += spread
                val next = nextReaderPageIndex(ReadingMode.DOUBLE_RTL, start, pageCount) ?: break
                start = next
            }

            assertEquals((0 until pageCount).toList(), visited, "page count $pageCount")
            assertEquals(listOf(0), doublePageSpread(0, pageCount), "the cover stands alone")
        }
    }

    @Test
    fun `double page navigation and restored progress stay on the containing spread`() {
        assertEquals(1, nextReaderPageIndex(ReadingMode.DOUBLE_LTR, 0, 5))
        assertEquals(3, nextReaderPageIndex(ReadingMode.DOUBLE_LTR, 1, 5))
        assertEquals(1, previousReaderPageIndex(ReadingMode.DOUBLE_RTL, 3, 5))
        assertEquals(0, previousReaderPageIndex(ReadingMode.DOUBLE_RTL, 1, 5))
        assertNull(previousReaderPageIndex(ReadingMode.DOUBLE_RTL, 0, 5))
        assertNull(nextReaderPageIndex(ReadingMode.DOUBLE_RTL, 3, 5))

        val savedPage = viewedReaderPageIndex(ReadingMode.DOUBLE_RTL, 1, 5)
        assertEquals(2, savedPage)
        val restoredSpread = normalizeReaderPageIndex(ReadingMode.DOUBLE_RTL, savedPage, 5)
        assertEquals(listOf(1, 2), doublePageSpread(restoredSpread, 5))
    }

    @Test
    fun `spread fits both differently shaped pages without clipping or a middle gap`() {
        for ((ratios, availableWidth, availableHeight) in listOf(
            Triple(listOf(0.7f, 0.7f), 1000f, 600f),
            Triple(listOf(0.5f, 1.3f), 600f, 900f),
            Triple(listOf(1.6f), 800f, 500f),
        )) {
            val layout = fitReaderSpread(ratios, availableWidth, availableHeight)
            assertEquals(ratios.size, layout.widths.size)
            assertTrue(layout.height <= availableHeight)
            assertTrue(layout.widths.sum() <= availableWidth + 0.001f)
            layout.widths.forEachIndexed { index, width ->
                assertEquals(ratios[index], width / layout.height, 0.0001f)
            }
        }
    }

    @Test
    fun `switching from a single page to a spread ignores the previous decoded page`() {
        val staleRatios = listOf(0.75f)
        val activeRatios = safeReaderRatios(staleRatios, pageCount = 2)
        assertEquals(listOf(0.7f, 0.7f), activeRatios)
        assertEquals(2, fitReaderSpread(activeRatios, 900f, 700f).widths.size)
        assertEquals(staleRatios, safeReaderRatios(staleRatios, pageCount = 1))
    }
}
