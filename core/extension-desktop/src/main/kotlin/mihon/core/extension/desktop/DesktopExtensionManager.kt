package mihon.core.extension.desktop

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.core.extension.policy.ExtensionPolicy
import mihon.core.extension.policy.ReplacementIssue
import mihon.platform.api.AppDirectories
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipFile

/** JVM-native extension installation and discovery for the versioned `.mihonext` container. */
class DesktopExtensionManager(
    directories: AppDirectories,
    private val supportedLibVersions: Set<Double> = ExtensionPolicy.supportedLibVersions,
    private val enabledContentWarnings: Set<ContentWarning> = ContentWarning.entries.toSet(),
) {
    private val root = Path.of(directories.extensions)
    private val packages = root.resolve("packages")
    private val trustFile = root.resolve("trusted-fingerprints.json")
    private val installedFile = root.resolve("installed.json")
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }
    private val loaders = mutableMapOf<String, ExtensionClassLoader>()

    fun trust(fingerprint: String) {
        require(fingerprint.matches(Regex("[0-9a-f]{64}"))) { "Invalid extension fingerprint" }
        val trusted = readList(trustFile).toMutableSet()
        trusted += fingerprint
        write(trustFile, json.encodeToString(trusted.sorted()))
    }

    fun installedExtensions(): List<InstalledExtension> = readInstalled()

    fun install(packageFile: Path, expected: ExpectedDesktopExtension? = null): DesktopExtensionInstallResult {
        val archive =
            readArchive(packageFile)
                ?: return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.MALFORMED)
        val signed =
            archive.signature
                ?: return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.UNSIGNED)
        if (!verifySignature(
                archive,
                signed,
            )
        ) {
            return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.UNSIGNED)
        }
        if (archive.manifest.libVersion !in supportedLibVersions) {
            return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.UNSUPPORTED_LIB_VERSION)
        }
        if (expected != null && (
                expected.id != archive.manifest.id ||
                    expected.versionCode != archive.manifest.versionCode ||
                    expected.fingerprint != signed.fingerprint
                )
        ) {
            return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.INDEX_MISMATCH)
        }
        if (signed.fingerprint !in
            readList(trustFile)
        ) {
            return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.UNTRUSTED)
        }

        val installed = readInstalled().associateBy { it.id }.toMutableMap()
        installed[archive.manifest.id]?.let { current ->
            when (
                ExtensionPolicy.replacementIssue(
                    installedVersionCode = current.versionCode,
                    installedFingerprints = setOf(current.fingerprint),
                    candidateVersionCode = archive.manifest.versionCode,
                    candidateFingerprints = setOf(signed.fingerprint),
                )
            ) {
                ReplacementIssue.DOWNGRADE ->
                    return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.DOWNGRADE)
                ReplacementIssue.UNSIGNED ->
                    return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.UNSIGNED)
                ReplacementIssue.SIGNATURE_CHANGED ->
                    return DesktopExtensionInstallResult.Rejected(
                        DesktopExtensionInstallResult.Reason.SIGNATURE_CHANGED,
                    )
                null -> Unit
            }
        }

        Files.createDirectories(packages)
        copyAtomically(packageFile, packages.resolve("${archive.manifest.id}.mihonext"))
        installed[archive.manifest.id] =
            InstalledExtension(archive.manifest.id, archive.manifest.versionCode, signed.fingerprint)
        write(installedFile, json.encodeToString(installed.values.sortedBy { it.id }))
        return DesktopExtensionInstallResult.Installed(archive.manifest)
    }

    fun loadInstalled(): List<DesktopExtensionLoadResult> {
        if (!Files.isDirectory(packages)) return emptyList()
        return Files.list(packages).use { files ->
            files.filter { it.fileName.toString().endsWith(".mihonext") }
                .sorted()
                .map(::load)
                .toList()
        }
    }

    fun load(packageFile: Path): DesktopExtensionLoadResult {
        val archive = readArchive(packageFile) ?: return DesktopExtensionLoadResult.NotLoaded(
            packageFile.fileName.toString(),
            DesktopExtensionLoadResult.Reason.MALFORMED,
        )
        val signed = archive.signature ?: return archive.notLoaded(DesktopExtensionLoadResult.Reason.UNSIGNED)
        if (!verifySignature(archive, signed)) return archive.notLoaded(DesktopExtensionLoadResult.Reason.UNSIGNED)
        if (signed.fingerprint !in
            readList(trustFile)
        ) {
            return archive.notLoaded(DesktopExtensionLoadResult.Reason.UNTRUSTED)
        }
        val installed = readInstalled().singleOrNull { it.id == archive.manifest.id }
        if (installed == null || installed.versionCode != archive.manifest.versionCode ||
            installed.fingerprint != signed.fingerprint ||
            packageFile.toAbsolutePath().normalize() !=
            packages.resolve("${archive.manifest.id}.mihonext").toAbsolutePath().normalize()
        ) {
            return archive.notLoaded(DesktopExtensionLoadResult.Reason.NOT_INSTALLED)
        }
        if (archive.manifest.libVersion !in supportedLibVersions) {
            return archive.notLoaded(DesktopExtensionLoadResult.Reason.UNSUPPORTED_LIB_VERSION)
        }
        if (!ExtensionPolicy.allowsContentWarning(
                archive.manifest.contentWarning.name,
                enabledContentWarnings.map(ContentWarning::name).toSet(),
            )
        ) {
            return archive.notLoaded(DesktopExtensionLoadResult.Reason.FILTERED)
        }

        return runCatching {
            val jarFile = root.resolve("runtime").resolve("${archive.manifest.id}-${archive.manifest.jarSha256}.jar")
            if (!Files.isRegularFile(jarFile) || sha256(Files.readAllBytes(jarFile)) != archive.manifest.jarSha256) {
                Files.createDirectories(jarFile.parent)
                Files.write(jarFile, archive.jar)
            }
            val loader = ExtensionClassLoader(jarFile.toUri().toURL(), javaClass.classLoader)
            val sources = try {
                archive.manifest.sourceClasses.flatMap { className ->
                    when (val instance = loader.loadClass(className).getDeclaredConstructor().newInstance()) {
                        is Source -> listOf(instance)
                        is SourceFactory -> instance.createSources()
                        else -> error("$className is not a Source or SourceFactory")
                    }
                }
            } catch (failure: Throwable) {
                loader.close()
                throw failure
            }
            loaders.put(archive.manifest.id, loader)?.close()
            DesktopExtensionLoadResult.Loaded(archive.manifest, sources)
        }.getOrElse { archive.notLoaded(DesktopExtensionLoadResult.Reason.FAILED, it.message) }
    }

    private fun readArchive(path: Path): Archive? = runCatching {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_PACKAGE_SIZE) return null
        ZipFile(path.toFile()).use { zip ->
            val entries = zip.entries().asSequence().toList()
            if (entries.any { it.isDirectory || (it.name !in REQUIRED_ENTRIES && !it.name.matches(ICON_NAME)) } ||
                entries.map { it.name }.toSet().size != entries.size
            ) {
                return null
            }
            val manifestBytes = zip.readRequired("manifest.json", MAX_METADATA_SIZE) ?: return null
            val jar = zip.readRequired("extension.jar", MAX_JAR_SIZE) ?: return null
            val manifest = json.decodeFromString<DesktopExtensionManifest>(manifestBytes.toString(UTF_8))
            if (!manifest.isValid() || sha256(jar) != manifest.jarSha256) return null
            val signature = zip.readRequired("signature.json", MAX_METADATA_SIZE)?.let { bytes ->
                json.decodeFromString<DesktopExtensionSignature>(bytes.toString(UTF_8))
            }
            Archive(manifest, manifestBytes, jar, signature)
        }
    }.getOrNull()

    private fun DesktopExtensionManifest.isValid() = formatVersion == FORMAT_VERSION &&
        id.matches(Regex("[A-Za-z0-9._-]+")) && name.isNotBlank() && versionName.isNotBlank() &&
        versionCode >= 0 && sourceClasses.isNotEmpty() &&
        sourceClasses.all { it.matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]*")) } &&
        jarSha256.matches(Regex("[0-9a-f]{64}"))

    private fun verifySignature(archive: Archive, signature: DesktopExtensionSignature): Boolean = runCatching {
        if (signature.algorithm != "Ed25519") return false
        val keyBytes = Base64.getDecoder().decode(signature.publicKey)
        val key: PublicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(archive.signaturePayload)
            verify(Base64.getDecoder().decode(signature.signature))
        }
    }.getOrDefault(false)

    private fun readInstalled(): List<InstalledExtension> = runCatching {
        if (Files.isRegularFile(
                installedFile,
            )
        ) {
            json.decodeFromString<List<InstalledExtension>>(Files.readString(installedFile))
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())
    private fun readList(path: Path): List<String> = runCatching {
        if (Files.isRegularFile(path)) json.decodeFromString<List<String>>(Files.readString(path)) else emptyList()
    }.getOrDefault(emptyList())
    private fun write(path: Path, value: String) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
        Files.writeString(temporary, value)
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun copyAtomically(source: Path, target: Path) {
        val temporary = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private data class Archive(
        val manifest: DesktopExtensionManifest,
        val manifestBytes: ByteArray,
        val jar: ByteArray,
        val signature: DesktopExtensionSignature?,
    ) {
        val signaturePayload get() = manifestBytes + manifest.jarSha256.toByteArray(UTF_8)
        fun notLoaded(reason: DesktopExtensionLoadResult.Reason, message: String? = null) =
            DesktopExtensionLoadResult.NotLoaded(manifest.id, reason, message)
    }

    private class ExtensionClassLoader(url: URL, parent: ClassLoader) : URLClassLoader(arrayOf(url), parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            val parentFirst = PROTECTED_PREFIXES.any(name::startsWith)
            val loaded = if (parentFirst) {
                parent.loadClass(name)
            } else {
                runCatching { findClass(name) }.getOrElse { parent.loadClass(name) }
            }
            if (resolve) resolveClass(loaded)
            loaded
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val MAX_PACKAGE_SIZE = 64L * 1024 * 1024
        const val MAX_JAR_SIZE = 48 * 1024 * 1024
        const val MAX_METADATA_SIZE = 64 * 1024
        val REQUIRED_ENTRIES = setOf("manifest.json", "extension.jar", "signature.json")
        val ICON_NAME = Regex("icon\\.[A-Za-z0-9]{1,8}")
        val PROTECTED_PREFIXES =
            listOf(
                "java.", "javax.", "jdk.", "sun.", "kotlin.", "kotlinx.", "mihon.",
                "tachiyomi.", "eu.kanade.tachiyomi.", "okhttp3.", "okio.", "rx.",
            )
        fun sha256(
            value: ByteArray,
        ) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    }
}

private fun ZipFile.readRequired(name: String, limit: Int): ByteArray? = getEntry(name)?.let { entry ->
    if (entry.size > limit) return null
    getInputStream(entry).use { stream ->
        val bytes = stream.readNBytes(limit + 1)
        bytes.takeIf { it.size <= limit }
    }
}
private val DesktopExtensionSignature.fingerprint get() = DesktopExtensionPackager.sha256(
    Base64.getDecoder().decode(publicKey),
)
