package mihon.platform.desktop

import mihon.platform.api.KeyValueStore
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class PropertiesKeyValueStore(
    private val file: Path,
) : KeyValueStore {

    private val lock = ReentrantReadWriteLock()
    private val properties = Properties()

    init {
        file.parent?.let(Files::createDirectories)
        if (Files.exists(file)) {
            Files.newInputStream(file).use(properties::load)
        }
    }

    override fun getString(key: String, defaultValue: String?): String? = lock.read {
        properties.getProperty(key) ?: defaultValue
    }

    override fun putString(key: String, value: String?) = lock.write {
        if (value == null) {
            properties.remove(key)
        } else {
            properties.setProperty(key, value)
        }
        persist()
    }

    override fun getLong(key: String, defaultValue: Long): Long = lock.read {
        properties.getProperty(key)?.toLongOrNull() ?: defaultValue
    }

    override fun putLong(key: String, value: Long) = putString(key, value.toString())

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = lock.read {
        properties.getProperty(key)?.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    override fun contains(key: String): Boolean = lock.read {
        properties.containsKey(key)
    }

    override fun remove(key: String) = putString(key, null)

    private fun persist() {
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temp).use { output -> properties.store(output, null) }
        try {
            Files.move(
                temp,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
