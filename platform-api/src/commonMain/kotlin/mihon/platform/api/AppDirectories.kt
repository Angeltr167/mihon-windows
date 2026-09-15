package mihon.platform.api

data class AppDirectories(
    val config: String,
    val data: String,
    val cache: String,
    val database: String,
    val downloads: String,
    val localLibrary: String,
    val extensions: String,
    val temp: String,
)

interface AppDirectoriesProvider {
    fun resolve(): AppDirectories
}
