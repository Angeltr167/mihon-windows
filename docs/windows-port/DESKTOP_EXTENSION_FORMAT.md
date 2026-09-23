# Desktop extension format

Desktop extensions use a versioned ZIP container with the `.mihonext` extension. Android APK/Dex extensions are not accepted by the Desktop loader.

```text
manifest.json       required; format version, identity, version, source classes, JAR digest
extension.jar       required; JVM extension implementation
signature.json      required; Ed25519 public key and signature
icon.*              optional; presentation asset
```

`signature.json` signs the exact UTF-8 `manifest.json` bytes followed by the manifest's SHA-256 digest of `extension.jar`. The first installation requires explicitly trusting the SHA-256 fingerprint of the signing public key. A package cannot downgrade an installed version or replace an installed signing key silently.

Use `DesktopExtensionPackager.create` from JVM extension build tooling after producing `extension.jar`; source implementations must use the existing `Source` or `SourceFactory` API from `:source-api`. `DesktopExtensionPackagerCli` is a `JavaExec`-compatible entry point accepting, in order, output `.mihonext`, manifest JSON, extension JAR, Ed25519 public key in X.509 DER, and private key in PKCS#8 DER. The manifest's `jarSha256` must match the JAR. Keep the private key outside the repository and pass its local path to the packaging task; never embed it in the build or artifact.

The reference JVM extension is compiled and packaged in `DesktopExtensionManagerTest`; the test installs it in an isolated Desktop profile, loads its `SourceFactory`, and executes its `Source.getPopularManga` method. An extension project can share its source implementation between Android and Desktop when its dependencies are portable, while packaging the Android variant as an APK and the Desktop JVM variant with `DesktopExtensionPackager.create`. Android APKs are never loaded as Desktop packages.

Desktop repositories publish an HTTPS JSON index, separate from Android APK stores:

```json
{
  "formatVersion": 1,
  "extensions": [{
    "id": "example.extension",
    "name": "Example",
    "versionCode": 1,
    "packageUrl": "example.mihonext",
    "sha256": "<lowercase SHA-256 of the .mihonext file>",
    "fingerprint": "<lowercase SHA-256 of the Ed25519 public key>"
  }]
}
```

`DesktopExtensionRepository` discovers and compares versions, downloads over HTTPS, checks the package digest and listed identity/signing key, then delegates installation to `DesktopExtensionManager`. The signing fingerprint still requires explicit trust; an index alone cannot grant it. The Desktop UI must surface these decisions before calling `trust` or `install`. This classloader isolates dependency names but is not a security sandbox: users should only trust extension authors they intend to run as local code.
