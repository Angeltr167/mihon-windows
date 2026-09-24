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
