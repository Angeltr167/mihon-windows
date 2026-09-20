package mihon.core.extension.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small JVM build-tooling primitive for producing a signed `.mihonext` artifact from a shared JVM extension JAR. */
object DesktopExtensionPackager {
    fun create(
        output: Path,
        manifest: DesktopExtensionManifest,
        extensionJar: Path,
        publicKey: ByteArray,
        privateKey: PrivateKey,
    ) {
        val jar = Files.readAllBytes(extensionJar)
        require(manifest.jarSha256 == sha256(jar)) { "manifest jarSha256 does not match extension.jar" }
        val manifestBytes = Json.encodeToString(manifest).toByteArray(UTF_8)
        val signature = Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(manifestBytes + manifest.jarSha256.toByteArray(UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        val signing = DesktopExtensionSignature(
            publicKey = Base64.getEncoder().encodeToString(publicKey),
            signature = signature,
        )
        Files.createDirectories(output.parent)
        ZipOutputStream(Files.newOutputStream(output)).use { zip ->
            zip.writeEntry("manifest.json", manifestBytes)
            zip.writeEntry("extension.jar", jar)
            zip.writeEntry("signature.json", Json.encodeToString(signing).toByteArray(UTF_8))
        }
    }

    fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }

    private fun ZipOutputStream.writeEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }
}
