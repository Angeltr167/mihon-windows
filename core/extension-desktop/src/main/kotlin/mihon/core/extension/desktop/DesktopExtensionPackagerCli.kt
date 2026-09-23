package mihon.core.extension.desktop

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec

/** CLI entry point for extension builds that already produce a JVM JAR and manifest. */
object DesktopExtensionPackagerCli {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 5) {
            "Usage: <output.mihonext> <manifest.json> <extension.jar> <public-key.der> <private-key.pkcs8>"
        }
        val manifest = Json.decodeFromString<DesktopExtensionManifest>(Files.readString(Path.of(args[1])))
        val privateKey = KeyFactory.getInstance("Ed25519")
            .generatePrivate(PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(args[4]))))
        DesktopExtensionPackager.create(
            output = Path.of(args[0]),
            manifest = manifest,
            extensionJar = Path.of(args[2]),
            publicKey = Files.readAllBytes(Path.of(args[3])),
            privateKey = privateKey,
        )
    }
}
