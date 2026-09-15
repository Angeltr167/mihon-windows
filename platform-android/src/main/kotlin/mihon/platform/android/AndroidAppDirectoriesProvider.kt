package mihon.platform.android

import android.content.Context
import android.os.Environment
import mihon.platform.api.AppDirectories
import mihon.platform.api.AppDirectoriesProvider
import java.io.File

class AndroidAppDirectoriesProvider(
    private val context: Context,
) : AppDirectoriesProvider {

    override fun resolve(): AppDirectories {
        val data = context.filesDir
        val config = File(data, "config")
        val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(data, "downloads")
        val localLibrary = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(data, "local")
        val database = context.getDatabasePath("tachiyomi.db").parentFile
            ?: File(data, "databases")

        val directories = AppDirectories(
            config = config.absolutePath,
            data = data.absolutePath,
            cache = context.cacheDir.absolutePath,
            database = database.absolutePath,
            downloads = downloads.absolutePath,
            localLibrary = localLibrary.absolutePath,
            extensions = File(data, "extensions").absolutePath,
            temp = File(context.cacheDir, "temp").absolutePath,
        )

        listOf(
            directories.config,
            directories.data,
            directories.cache,
            directories.database,
            directories.downloads,
            directories.localLibrary,
            directories.extensions,
            directories.temp,
        ).forEach { File(it).mkdirs() }

        return directories
    }
}
