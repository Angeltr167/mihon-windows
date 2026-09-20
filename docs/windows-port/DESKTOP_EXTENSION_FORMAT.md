# Desktop extension format

Desktop extensions use a versioned ZIP container with the `.mihonext` extension. Android APK/Dex extensions are not accepted by the Desktop loader.

```text
manifest.json       required; format version, identity, version, source classes, JAR digest
extension.jar       required; JVM extension implementation
signature.json      required; Ed25519 public key and signature
icon.*              optional; presentation asset
```

`signature.json` signs the exact UTF-8 `manifest.json` bytes followed by the manifest's SHA-256 digest of `extension.jar`. The first installation requires explicitly trusting the SHA-256 fingerprint of the signing public key. A package cannot downgrade an installed version or replace an installed signing key silently.

Use `DesktopExtensionPackager.create` from JVM extension build tooling after producing `extension.jar`; source implementations must use the existing `Source` or `SourceFactory` API from `:source-api`.
