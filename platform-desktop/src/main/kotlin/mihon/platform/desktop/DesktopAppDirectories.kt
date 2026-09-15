package mihon.platform.desktop

import mihon.platform.api.AppDirectories
import mihon.platform.api.AppDirectoriesProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DesktopAppDirectoriesProvider(
    private val environment: Map<String, String> = System.getenv(),
    private val userHome: String = System.getProperty("user.home"),
    private val createDirectories: Boolean = true,
) : AppDirectoriesProvider {

    override fun resolve(): AppDirectories {
        val home = Paths.get(userHome)
        val roamingBase = environment["APPDATA"]?.let(Paths::get)
            ?: home.resolve("AppData").resolve("Roaming")
        val localBase = environment["LOCALAPPDATA"]?.let(Paths::get)
            ?: home.resolve("AppData").resolve("Local")

        val config = roamingBase.resolve(APP_DIR_NAME)
        val data = localBase.resolve(APP_DIR_NAME)
        val directories = AppDirectories(
            config = config.toString(),
            data = data.toString(),
            cache = data.resolve("cache").toString(),
            database = data.resolve("database").toString(),
            downloads = home.resolve("Downloads").resolve(APP_DIR_NAME).toString(),
            localLibrary = home.resolve("Documents").resolve(APP_DIR_NAME).toString(),
            extensions = data.resolve("extensions").toString(),
            temp = data.resolve("temp").toString(),
        )

        if (createDirectories) {
            managedDirectories(directories).forEach(Files::createDirectories)
        }

        return directories
    }

    private fun managedDirectories(directories: AppDirectories): List<Path> = listOf(
        Paths.get(directories.config),
        Paths.get(directories.data),
        Paths.get(directories.cache),
        Paths.get(directories.database),
        Paths.get(directories.downloads),
        Paths.get(directories.localLibrary),
        Paths.get(directories.extensions),
        Paths.get(directories.temp),
    )

    private companion object {
        const val APP_DIR_NAME = "Mihon"
    }
}
