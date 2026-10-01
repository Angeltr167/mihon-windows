package mihon.desktop

import mihon.backup.shared.BackupDesktopPreference
import mihon.core.reader.FitMode
import mihon.core.reader.ReadingMode
import mihon.platform.api.KeyValueStore

/** Only portable, non-sensitive desktop preferences are included in local backups. */
internal class DesktopPreferences(private val store: KeyValueStore) {
    var language: String
        get() = store.getString(LANGUAGE, "system").takeIf { it in LANGUAGES } ?: "system"
        set(value) {
            require(value in LANGUAGES)
            store.putString(LANGUAGE, value)
        }

    fun effectiveLanguageTag(systemLanguageTag: String): String =
        language.takeUnless { it == "system" } ?: systemLanguageTag

    fun export(): List<BackupDesktopPreference> = BACKUP_KEYS.mapNotNull { key ->
        store.getString(key, DEFAULTS[key])?.takeIf { valid(key, it) }?.let { BackupDesktopPreference(key, it) }
    }

    fun restore(preferences: List<BackupDesktopPreference>) {
        preferences.filter { valid(it.key, it.value) }.forEach { store.putString(it.key, it.value) }
    }

    companion object {
        const val LANGUAGE = "desktop.language"
        const val START_SCREEN = "desktop.startScreen"
        const val MAXIMIZED = "desktop.window.startMaximized"
        const val REMEMBER_SIZE = "desktop.window.rememberSize"
        const val NOTIFICATIONS = "desktop.download.notifications"
        const val BACKUP_DIRECTORY = "desktop.backup.directory"
        val LANGUAGES = listOf("system", "es", "en")
        val START_SCREENS = listOf(Screen.LIBRARY, Screen.HISTORY, Screen.UPDATES)
        private val DEFAULTS = mapOf(
            LANGUAGE to "system", START_SCREEN to "LIBRARY", MAXIMIZED to "true", REMEMBER_SIZE to "true",
            NOTIFICATIONS to "true", "desktop.window.width" to "1100", "desktop.window.height" to "750",
            "desktop.reader.mode" to "SINGLE_LTR", "desktop.reader.fit" to "HEIGHT",
            "desktop.reader.imageFilter" to "NORMAL", "desktop.reader.brightness" to "0",
            DesktopLibraryUpdateScheduler.INTERVAL_KEY to "0",
        )
        private val BACKUP_KEYS = setOf(
            LANGUAGE, START_SCREEN, MAXIMIZED, REMEMBER_SIZE, NOTIFICATIONS,
            "desktop.window.width", "desktop.window.height",
            "desktop.reader.mode", "desktop.reader.fit", "desktop.reader.imageFilter", "desktop.reader.brightness",
            DesktopLibraryUpdateScheduler.INTERVAL_KEY,
        )

        private fun valid(key: String, value: String): Boolean = when (key) {
            LANGUAGE -> value in LANGUAGES
            START_SCREEN -> START_SCREENS.any { it.name == value }
            MAXIMIZED, REMEMBER_SIZE, NOTIFICATIONS -> value.toBooleanStrictOrNull() != null
            "desktop.window.width" -> value.toLongOrNull()?.let { it in 800..3840 } == true
            "desktop.window.height" -> value.toLongOrNull()?.let { it in 560..2160 } == true
            "desktop.reader.mode" -> ReadingMode.entries.any { it.name == value }
            "desktop.reader.fit" -> FitMode.entries.any { it.name == value }
            "desktop.reader.imageFilter" -> value in setOf("NORMAL", "GRAYSCALE", "INVERT")
            "desktop.reader.brightness" -> value.toIntOrNull()?.let { it in -100..100 } == true
            DesktopLibraryUpdateScheduler.INTERVAL_KEY -> value.toLongOrNull() in
                DesktopLibraryUpdateScheduler.INTERVALS
            else -> false
        }
    }
}
