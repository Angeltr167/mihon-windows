package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import mihon.core.extension.desktop.DesktopRepositoryEntry
import mihon.core.extension.desktop.InstalledExtension
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing

internal enum class ExtensionAction {
    INSTALL,
    UPDATE,
    REMOVE,
}

@Composable
internal fun ExtensionsScreen(
    selectedSection: Int,
    onSelectSection: (Int) -> Unit,
    suwayomiExtensions: List<SuwayomiExtension>,
    installedDesktopExtensions: List<InstalledExtension>,
    availableDesktopExtensions: List<DesktopRepositoryEntry>,
    search: String,
    onSearchChange: (String) -> Unit,
    status: String,
    engineRunning: Boolean,
    loading: Boolean,
    error: String?,
    busyPackage: String?,
    busyLabel: String?,
    repositoryUrl: String,
    onRepositoryUrlChange: (String) -> Unit,
    packagePath: String,
    onPackagePathChange: (String) -> Unit,
    fingerprint: String,
    onFingerprintChange: (String) -> Unit,
    onRefreshCatalog: () -> Unit,
    onSuwayomiAction: (SuwayomiExtension, ExtensionAction) -> Unit,
    onDiscoverRepository: () -> Unit,
    onInstallRepositoryEntry: (DesktopRepositoryEntry) -> Unit,
    onTrustFingerprint: () -> Unit,
    onChoosePackage: () -> Unit,
    onInstallPackage: () -> Unit,
) {
    val installedSuwayomi = remember(suwayomiExtensions) {
        suwayomiExtensions.filter { it.installed && !it.obsolete }.sortedBy { it.name.lowercase() }
    }
    val installedDesktopById = remember(installedDesktopExtensions) {
        installedDesktopExtensions.associateBy(InstalledExtension::id)
    }
    val desktopUpdates = remember(availableDesktopExtensions, installedDesktopExtensions) {
        availableDesktopExtensions.filter { entry ->
            installedDesktopById[entry.id]?.let { entry.versionCode > it.versionCode } == true
        }.sortedBy { it.name.lowercase() }
    }
    val suwayomiUpdates = remember(suwayomiExtensions) {
        suwayomiExtensions.filter { it.installed && it.hasUpdate && !it.obsolete }
            .sortedBy { it.name.lowercase() }
    }

    Column(Modifier.fillMaxSize()) {
        RoninSectionHeader(
            title = "Extensions",
            subtitle = "Manage source packages through Ronin's existing Desktop and local extension engines.",
            trailing = {
                RoninBadge(
                    label = if (engineRunning) "Engine ready" else "Engine offline",
                    accent = engineRunning,
                )
            },
        )
        RoninTabStrip(
            labels = listOf("Installed", "Discover", "Updates", "Repository"),
            selectedIndex = selectedSection.coerceIn(0, 3),
            onSelect = onSelectSection,
            modifier = Modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium),
        )

        if (error != null) {
            RoninErrorState(
                title = "Extension operation failed",
                detail = error,
                modifier = Modifier.padding(bottom = RoninSpacing.medium),
            )
        }

        when (selectedSection.coerceIn(0, 3)) {
            0 -> InstalledExtensions(
                suwayomi = installedSuwayomi,
                desktop = installedDesktopExtensions,
                busyPackage = busyPackage,
                busyLabel = busyLabel,
                onAction = onSuwayomiAction,
            )
            1 -> DiscoverExtensions(
                extensions = suwayomiExtensions,
                search = search,
                onSearchChange = onSearchChange,
                status = status,
                engineRunning = engineRunning,
                loading = loading,
                busyPackage = busyPackage,
                busyLabel = busyLabel,
                onRefresh = onRefreshCatalog,
                onAction = onSuwayomiAction,
            )
            2 -> ExtensionUpdates(
                suwayomi = suwayomiUpdates,
                desktop = desktopUpdates,
                installedDesktopById = installedDesktopById,
                busyPackage = busyPackage,
                busyLabel = busyLabel,
                repositoryLoaded = availableDesktopExtensions.isNotEmpty(),
                onSuwayomiAction = onSuwayomiAction,
                onInstallDesktop = onInstallRepositoryEntry,
            )
            else -> RepositoryManagement(
                repositoryUrl = repositoryUrl,
                onRepositoryUrlChange = onRepositoryUrlChange,
                available = availableDesktopExtensions,
                installedDesktopById = installedDesktopById,
                loading = loading,
                busyPackage = busyPackage,
                busyLabel = busyLabel,
                packagePath = packagePath,
                onPackagePathChange = onPackagePathChange,
                fingerprint = fingerprint,
                onFingerprintChange = onFingerprintChange,
                onDiscover = onDiscoverRepository,
                onInstallEntry = onInstallRepositoryEntry,
                onTrustFingerprint = onTrustFingerprint,
                onChoosePackage = onChoosePackage,
                onInstallPackage = onInstallPackage,
            )
        }
    }
}

@Composable
private fun InstalledExtensions(
    suwayomi: List<SuwayomiExtension>,
    desktop: List<InstalledExtension>,
    busyPackage: String?,
    busyLabel: String?,
    onAction: (SuwayomiExtension, ExtensionAction) -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    val total = suwayomi.size + desktop.size

    if (total == 0) {
        RoninEmptyState(
            title = "No extensions installed",
            detail = "Use Discover for community sources or Repository for signed Desktop packages.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        item("installed-summary") {
            DirectoryLabel("Installed", "$total extensions")
        }
        items(suwayomi, key = { "suwayomi:${it.pkgName}" }) { entry ->
            ExtensionRow(
                title = entry.name,
                subtitle = "${entry.versionName} · ${entry.pkgName}",
                badges = buildList {
                    entry.languageContext()?.let(::add)
                    add("Keiyoushi")
                    if (entry.hasUpdate) add("Update available") else add("Installed")
                },
                accentLastBadge = entry.hasUpdate,
                busy = busyPackage == entry.pkgName,
                busyLabel = busyLabel,
                actions = {
                    if (entry.hasUpdate) {
                        RoninInlineAction(
                            label = "Update",
                            onClick = { onAction(entry, ExtensionAction.UPDATE) },
                            enabled = busyPackage == null,
                        )
                    } else if (pendingRemoval == entry.pkgName) {
                        RoninInlineAction(
                            label = "Cancel",
                            onClick = { pendingRemoval = null },
                            enabled = busyPackage == null,
                        )
                        RoninInlineAction(
                            label = "Confirm remove",
                            onClick = {
                                pendingRemoval = null
                                onAction(entry, ExtensionAction.REMOVE)
                            },
                            enabled = busyPackage == null,
                        )
                    } else {
                        RoninInlineAction(
                            label = "Remove",
                            onClick = { pendingRemoval = entry.pkgName },
                            enabled = busyPackage == null,
                        )
                    }
                },
            )
        }
        items(desktop, key = { "desktop:${it.id}" }) { entry ->
            ExtensionRow(
                title = entry.id,
                subtitle = "Version ${entry.versionCode} · ${entry.fingerprint.take(12)}…",
                badges = listOf("Desktop .mihonext", "Locally trusted"),
                accentLastBadge = true,
                busy = busyPackage == entry.id,
                busyLabel = busyLabel,
            )
        }
    }
}

@Composable
private fun DiscoverExtensions(
    extensions: List<SuwayomiExtension>,
    search: String,
    onSearchChange: (String) -> Unit,
    status: String,
    engineRunning: Boolean,
    loading: Boolean,
    busyPackage: String?,
    busyLabel: String?,
    onRefresh: () -> Unit,
    onAction: (SuwayomiExtension, ExtensionAction) -> Unit,
) {
    val visible = remember(extensions, search) {
        extensions.asSequence()
            .filter { !it.obsolete || it.installed }
            .filter {
                search.isBlank() ||
                    it.name.contains(search, ignoreCase = true) ||
                    it.pkgName.contains(search, ignoreCase = true) ||
                    it.languageContext()?.contains(search, ignoreCase = true) == true
            }
            .sortedWith(compareBy<SuwayomiExtension> { it.installed }.thenBy { it.name.lowercase() })
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        RoninPanel(Modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(RoninSpacing.medium)) {
                val narrow = maxWidth < 720.dp
                val copy: @Composable (Modifier) -> Unit = { modifier ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro),
                        modifier = modifier,
                    ) {
                        Text("Keiyoushi catalog", style = MaterialTheme.typography.titleMedium)
                        Text(
                            status,
                            color = if (engineRunning) RoninColors.accentSage else RoninColors.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (narrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                        copy(Modifier.fillMaxWidth())
                        RoninButton("Refresh catalog", onRefresh, enabled = engineRunning && !loading)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        copy(Modifier.weight(1f))
                        RoninButton("Refresh catalog", onRefresh, enabled = engineRunning && !loading)
                    }
                }
            }
        }

        RoninSearchField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = "Search extensions or language",
            modifier = Modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium),
            enabled = !loading,
        )

        if (loading && extensions.isEmpty()) {
            RoninLoadingState("Loading extension catalog…")
            return
        }
        if (!engineRunning && extensions.isEmpty()) {
            RoninErrorState("Extension engine unavailable", status)
            return
        }
        if (extensions.isEmpty()) {
            RoninEmptyState("Catalog is not loaded", "Refresh the catalog to discover compatible sources.")
            return
        }
        if (visible.isEmpty()) {
            RoninEmptyState("No matching extensions", "Try another name, package, or language.")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
        ) {
            items(visible, key = { it.pkgName }) { entry ->
                ExtensionRow(
                    title = entry.name,
                    subtitle = "${entry.versionName} · ${entry.pkgName}",
                    badges = buildList {
                        entry.languageContext()?.let(::add)
                        if (entry.contentWarning.isNotBlank() && entry.contentWarning != "SAFE") {
                            add(entry.contentWarning)
                        }
                        add(
                            when {
                                entry.installed && entry.hasUpdate -> "Update available"
                                entry.installed -> "Installed"
                                else -> "Available"
                            },
                        )
                    },
                    accentLastBadge = entry.installed || entry.hasUpdate,
                    busy = busyPackage == entry.pkgName,
                    busyLabel = busyLabel,
                    actions = {
                        RoninInlineAction(
                            label = when {
                                !entry.installed -> "Install"
                                entry.hasUpdate -> "Update"
                                else -> "Installed"
                            },
                            onClick = {
                                when {
                                    !entry.installed -> onAction(entry, ExtensionAction.INSTALL)
                                    entry.hasUpdate -> onAction(entry, ExtensionAction.UPDATE)
                                }
                            },
                            enabled = busyPackage == null && (!entry.installed || entry.hasUpdate),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ExtensionUpdates(
    suwayomi: List<SuwayomiExtension>,
    desktop: List<DesktopRepositoryEntry>,
    installedDesktopById: Map<String, InstalledExtension>,
    busyPackage: String?,
    busyLabel: String?,
    repositoryLoaded: Boolean,
    onSuwayomiAction: (SuwayomiExtension, ExtensionAction) -> Unit,
    onInstallDesktop: (DesktopRepositoryEntry) -> Unit,
) {
    if (suwayomi.isEmpty() && desktop.isEmpty()) {
        RoninEmptyState(
            title = "No updates available",
            detail = if (repositoryLoaded) {
                "Installed extensions match the currently loaded catalogs."
            } else {
                "Keiyoushi is current. Load a Desktop repository index to check signed .mihonext packages."
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        if (suwayomi.isNotEmpty()) {
            item("suwayomi-updates-label") { DirectoryLabel("Keiyoushi", "${suwayomi.size} updates") }
            items(suwayomi, key = { "suwayomi-update:${it.pkgName}" }) { entry ->
                ExtensionRow(
                    title = entry.name,
                    subtitle = "${entry.versionName} · ${entry.pkgName}",
                    badges = buildList {
                        entry.languageContext()?.let(::add)
                        add("Update available")
                    },
                    accentLastBadge = true,
                    busy = busyPackage == entry.pkgName,
                    busyLabel = busyLabel,
                    actions = {
                        RoninInlineAction(
                            "Update",
                            { onSuwayomiAction(entry, ExtensionAction.UPDATE) },
                            enabled = busyPackage == null,
                        )
                    },
                )
            }
        }
        if (desktop.isNotEmpty()) {
            item("desktop-updates-label") { DirectoryLabel("Desktop packages", "${desktop.size} updates") }
            items(desktop, key = { "desktop-update:${it.id}" }) { entry ->
                val installed = installedDesktopById[entry.id]
                ExtensionRow(
                    title = entry.name,
                    subtitle = "Version ${installed?.versionCode ?: 0} → ${entry.versionCode} · ${entry.id}",
                    badges = listOf("Signed .mihonext", "Update available"),
                    accentLastBadge = true,
                    busy = busyPackage == entry.id,
                    busyLabel = busyLabel,
                    actions = {
                        RoninInlineAction(
                            "Update",
                            { onInstallDesktop(entry) },
                            enabled = busyPackage == null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RepositoryManagement(
    repositoryUrl: String,
    onRepositoryUrlChange: (String) -> Unit,
    available: List<DesktopRepositoryEntry>,
    installedDesktopById: Map<String, InstalledExtension>,
    loading: Boolean,
    busyPackage: String?,
    busyLabel: String?,
    packagePath: String,
    onPackagePathChange: (String) -> Unit,
    fingerprint: String,
    onFingerprintChange: (String) -> Unit,
    onDiscover: () -> Unit,
    onInstallEntry: (DesktopRepositoryEntry) -> Unit,
    onTrustFingerprint: () -> Unit,
    onChoosePackage: () -> Unit,
    onInstallPackage: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
    ) {
        item("repository-index") {
            RoninPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(RoninSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Text("Desktop repository index", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ronin accepts HTTPS indexes for signed .mihonext packages. The URL is session state, not a saved repository list.",
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RoninSearchField(
                        value = repositoryUrl,
                        onValueChange = onRepositoryUrlChange,
                        placeholder = "https://…/index.json",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading && busyPackage == null,
                    )
                    RoninButton(
                        label = if (loading) "Loading…" else "Load index",
                        onClick = onDiscover,
                        enabled = repositoryUrl.isNotBlank() && !loading && busyPackage == null,
                    )
                }
            }
        }

        if (available.isNotEmpty()) {
            item("repository-results-label") {
                DirectoryLabel("Repository packages", "${available.size} entries")
            }
            items(available, key = { "repository:${it.id}" }) { entry ->
                val installed = installedDesktopById[entry.id]
                val action = when {
                    installed == null -> "Install"
                    entry.versionCode > installed.versionCode -> "Update"
                    else -> "Installed"
                }
                ExtensionRow(
                    title = entry.name,
                    subtitle = "Version ${entry.versionCode} · ${entry.id}",
                    badges = buildList {
                        add("Signed index")
                        add(
                            when {
                                installed == null -> "Available"
                                entry.versionCode > installed.versionCode -> "Update available"
                                else -> "Installed"
                            },
                        )
                    },
                    accentLastBadge = installed != null,
                    busy = busyPackage == entry.id,
                    busyLabel = busyLabel,
                    actions = {
                        RoninInlineAction(
                            label = "Fingerprint",
                            onClick = { onFingerprintChange(entry.fingerprint) },
                            enabled = busyPackage == null,
                        )
                        RoninInlineAction(
                            label = action,
                            onClick = { onInstallEntry(entry) },
                            enabled = action != "Installed" && busyPackage == null,
                        )
                    },
                )
            }
        } else if (!loading) {
            item("repository-empty") {
                RoninEmptyState(
                    "No Desktop repository loaded",
                    "Load a valid HTTPS index to discover or update signed Desktop extensions.",
                )
            }
        }

        item("local-trust") {
            RoninPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(RoninSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Text("Local package trust", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Trusting a fingerprint records a local decision only; it does not verify a publisher identity.",
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RoninSearchField(
                        value = fingerprint,
                        onValueChange = onFingerprintChange,
                        placeholder = "64-character SHA-256 signing fingerprint",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = busyPackage == null,
                    )
                    RoninSecondaryButton(
                        "Trust fingerprint",
                        onTrustFingerprint,
                        enabled = fingerprint.isNotBlank() && busyPackage == null,
                    )
                    RoninSearchField(
                        value = packagePath,
                        onValueChange = onPackagePathChange,
                        placeholder = "C:\\path\\extension.mihonext",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = busyPackage == null,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    ) {
                        RoninSecondaryButton(
                            label = "Choose package…",
                            onClick = onChoosePackage,
                            enabled = busyPackage == null,
                        )
                        RoninButton(
                            label = "Install local package",
                            onClick = onInstallPackage,
                            enabled = packagePath.isNotBlank() && busyPackage == null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryLabel(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = RoninSpacing.xSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = RoninColors.textMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExtensionRow(
    title: String,
    subtitle: String,
    badges: List<String>,
    accentLastBadge: Boolean,
    busy: Boolean,
    busyLabel: String?,
    actions: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember(title, subtitle) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier.fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource),
        shape = RoundedCornerShape(RoninRadius.card),
        color = if (hovered) RoninColors.hoverSurface else RoninColors.elevatedSurface,
        border = BorderStroke(RoninBorders.hairline, if (hovered) RoninColors.accentCoral else RoninColors.border),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(RoninSpacing.medium)) {
            val narrow = maxWidth < 760.dp
            val identity: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, color = RoninColors.textMuted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    ) {
                        badges.forEachIndexed { index, badge ->
                            RoninBadge(badge, accent = accentLastBadge && index == badges.lastIndex)
                        }
                        if (busy) {
                            RoninBadge(busyLabel ?: "Working…", accent = true)
                        }
                    }
                }
            }

            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                    identity(Modifier.fillMaxWidth())
                    actions?.let {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                            verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        ) { it() }
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    identity(Modifier.weight(1f))
                    actions?.let {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                            verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        ) { it() }
                    }
                }
            }
        }
    }
}

private fun SuwayomiExtension.languageContext(): String? {
    val marker = ".extension."
    val tail = pkgName.substringAfter(marker, missingDelimiterValue = "")
    val language = tail.substringBefore('.')
    return language.takeIf { it.isNotBlank() && it.length <= 8 }?.uppercase()
}
