# Keiyoushi extensions in Mihon Windows

Mihon Windows keeps its own Compose interface, library/database, reader and download
manager. A bundled, separately running Suwayomi-Server JVM process supplies the
Keiyoushi extension catalog, installed sources, manga metadata and image URLs over
its local GraphQL/REST API. The existing `.mihonext` format still works independently.
Android Mihon is unchanged.

## Use

1. Install a newly built Windows MSI or EXE. Older packages do not contain this
   engine. Launch Mihon; the **Extensions** screen should say **Local extension
   engine ready**. If startup fails, inspect `suwayomi/launcher.log` under Mihon's
   per-user data directory.
2. Select **Load Keiyoushi catalog**. This adds the Keiyoushi repository and fetches
   its current index. Internet access is required for this step and for installation.
3. Search for an extension and select **Install**. Its sources then appear under
   **Sources**. Browse or search a source, open a manga and chapter, and read using
   Mihon's own reader. Mihon's existing library and downloads own saved content.
4. Return to **Extensions** to update or remove the extension. Installed extensions
   remain in Suwayomi's per-user profile between app launches.

Only install extensions from publishers you trust: extension code executes on your
computer inside the local engine. Source sites and extension repositories are not
operated by Mihon. A source may be unavailable, change its API, require a browser
challenge, or contain content warnings. The first release does not expose
source-specific filter controls in Mihon's search UI; text search and browse work.
The engine binds to a random `127.0.0.1` port, does not open its own web UI or system
tray, and stops when Mihon exits. Its profile is separate from Mihon's database.
On first launch Suwayomi may download its browser-challenge runtime into that profile;
this is separate from the Mihon installer. For an offline development smoke test, set
`-Dmihon.suwayomi.kcefEnabled=false` (sources requiring browser challenges may then
fail).

## Build and provenance

`desktopApp:prepareSuwayomiEngine` downloads Suwayomi-Server `v2.3.2243` and its
MPL-2.0 license from that exact release/tag into ignored build resources. Both files
are SHA-256 checked before MSI/EXE packaging. The JAR is not committed. Release JAR:

`821141b32e170d4a02d3cbdfed577ed8f07bd22383ff5f4132ebb5ae40e98dd5`

License file:

`3f3d9e0024b1921b067d6f7f88deb4a60cbe7a78e76c64e3f1d7fc3b779b9d04`

The packaged JAR contains its own dependency license/notice files; the installer
also carries `suwayomi-LICENSE.txt`. Upstream source and license:
https://github.com/Suwayomi/Suwayomi-Server/tree/v2.3.2243 . The Keiyoushi index
is fetched from https://github.com/keiyoushi/extensions when the user requests it.
Public release still requires the repository-wide third-party notice audit and
signing/clean-install validation described in `PHASE_12_RELEASE_STATUS.md`.

For a development run outside the installer, set JVM property
`-Dmihon.suwayomi.jar=<absolute-path-to-the-pinned-jar>` or run the packaging resource
task first. The engine uses the active JVM's `javaw.exe` on Windows.
