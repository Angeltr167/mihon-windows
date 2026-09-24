package mihon.core.reader

/** Reading direction and layout are separate so page order never depends on the widget. */
enum class ReadingMode {
    SINGLE_LTR,
    SINGLE_RTL,
    DOUBLE_LTR,
    DOUBLE_RTL,
    VERTICAL,
    WEBTOON,
}

enum class FitMode { WIDTH, HEIGHT, ORIGINAL }

sealed interface ReaderPageLoadState {
    data object Loading : ReaderPageLoadState
    data class Ready(val pageCount: Int) : ReaderPageLoadState
    data class Failed(val reason: String, val attempts: Int) : ReaderPageLoadState
}

/** The second page of a spread is considered viewed when the spread is displayed. */
fun viewedPageIndex(pageIndex: Int, pageCount: Int, mode: ReadingMode): Int {
    require(pageIndex in 0 until pageCount)
    return when (mode) {
        ReadingMode.DOUBLE_LTR, ReadingMode.DOUBLE_RTL -> (pageIndex + 1).coerceAtMost(pageCount - 1)
        else -> pageIndex
    }
}

/** Continuous viewers compose nearby pages themselves; paged viewers warm only the next page. */
fun nextPageToPreload(pageIndex: Int, pageCount: Int, mode: ReadingMode): Int? {
    val step = when (mode) {
        ReadingMode.DOUBLE_LTR, ReadingMode.DOUBLE_RTL -> 2
        ReadingMode.SINGLE_LTR, ReadingMode.SINGLE_RTL -> 1
        ReadingMode.VERTICAL, ReadingMode.WEBTOON -> return null
    }
    return (pageIndex + step).takeIf { it in 0 until pageCount }
}

data class ReaderPosition(val chapterIndex: Int, val pageIndex: Int)

/** Pure chapter/page boundaries shared by desktop controls and renderers. */
class ReaderNavigation(
    private val pageCounts: List<Int>,
    initial: ReaderPosition,
) {
    init {
        require(pageCounts.isNotEmpty())
        require(pageCounts.all { it > 0 })
        require(initial.chapterIndex in pageCounts.indices)
        require(initial.pageIndex in 0 until pageCounts[initial.chapterIndex])
    }

    var position: ReaderPosition = initial
        private set

    fun goTo(position: ReaderPosition) {
        require(position.chapterIndex in pageCounts.indices)
        require(position.pageIndex in 0 until pageCounts[position.chapterIndex])
        this.position = position
    }

    fun next(): ReaderPosition? = move(1)

    fun previous(): ReaderPosition? = move(-1)

    private fun move(delta: Int): ReaderPosition? {
        val nextPage = position.pageIndex + delta
        val next = when {
            nextPage in 0 until pageCounts[position.chapterIndex] -> position.copy(pageIndex = nextPage)
            delta > 0 && position.chapterIndex + 1 in pageCounts.indices ->
                ReaderPosition(position.chapterIndex + 1, 0)
            delta < 0 && position.chapterIndex - 1 in pageCounts.indices ->
                ReaderPosition(position.chapterIndex - 1, pageCounts[position.chapterIndex - 1] - 1)
            else -> return null
        }
        position = next
        return next
    }
}
