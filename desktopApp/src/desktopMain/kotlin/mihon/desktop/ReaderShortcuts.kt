package mihon.desktop

import androidx.compose.ui.input.key.Key

/** Extra reader keys; arrows and Page Up/Down remain available for every configuration. */
internal enum class ReaderShortcutKey(val key: Key) {
    J(Key.J),
    K(Key.K),
    N(Key.N),
    P(Key.P),
    A(Key.A),
    D(Key.D),
    ;

    fun nextExcept(other: ReaderShortcutKey): ReaderShortcutKey {
        val choices = entries
        return (1..choices.size).asSequence()
            .map { choices[(ordinal + it) % choices.size] }
            .first { it != other }
    }

    companion object {
        fun restore(value: String?, default: ReaderShortcutKey): ReaderShortcutKey =
            entries.firstOrNull { it.name == value } ?: default
    }
}

internal data class ReaderShortcuts(
    val next: ReaderShortcutKey = ReaderShortcutKey.J,
    val previous: ReaderShortcutKey = ReaderShortcutKey.K,
) {
    init {
        require(next != previous) { "Next and previous must use different keys" }
    }

    fun action(key: Key): Action? = when (key) {
        next.key -> Action.NEXT
        previous.key -> Action.PREVIOUS
        else -> null
    }

    enum class Action { NEXT, PREVIOUS }
}
