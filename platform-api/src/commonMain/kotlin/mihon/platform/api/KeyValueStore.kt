package mihon.platform.api

interface KeyValueStore {
    fun getString(key: String, defaultValue: String? = null): String?

    fun putString(key: String, value: String?)

    fun getLong(key: String, defaultValue: Long = 0L): Long

    fun putLong(key: String, value: Long)

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun contains(key: String): Boolean

    fun remove(key: String)
}
