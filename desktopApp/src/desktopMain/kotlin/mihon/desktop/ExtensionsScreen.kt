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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import mihon.desktop.design.RoninLayout
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
    val uiLanguage = LocalRoninLanguage.current
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
            title = roninCopy("Extensions", "Extensiones", uiLanguage),
            pageHeading = true,
            subtitle = roninCopy(
                "Install sources, manage updates, and configure repositories.",
                "Instala fuentes, administra actualizaciones y configura repositorios.",
                uiLanguage,
            ),
            trailing = {
                RoninBadge(
                    label = if (engineRunning) {
                        roninCopy(
                            "Engine ready",
                            "Motor listo",
                            uiLanguage,
                        )
                    } else {
                        roninCopy("Engine offline", "Motor desconectado", uiLanguage)
                    },
                    accent = engineRunning,
                )
            },
        )
        RoninTabStrip(
            labels = listOf(
                roninCopy("Installed", "Instaladas", uiLanguage),
                roninCopy("Discover", "Explorar", uiLanguage),
                roninCopy("Updates", "Actualizaciones", uiLanguage),
                roninCopy("Repository", "Repositorio", uiLanguage),
            ),
            selectedIndex = selectedSection.coerceIn(0, 3),
            onSelect = onSelectSection,
            modifier = Modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium),
        )

        if (error != null) {
            RoninErrorState(
                title = roninCopy("Extension operation failed", "Falló la operación de extensión", uiLanguage),
                detail = error,
                modifier = Modifier.padding(bottom = RoninSpacing.medium),
            )
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val section = selectedSection.coerceIn(0, 3)
            val showOperationsPanel = maxWidth >= RoninLayout.rightPanelBreakpoint
            val sectionContent: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier) {
                    when (section) {
                        0 -> InstalledExtensions(
                            suwayomi = installedSuwayomi,
                            desktop = installedDesktopExtensions,
                            busyPackage = busyPackage,
                            busyLabel = busyLabel,
                            onDiscover = { onSelectSection(1) },
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

            if (showOperationsPanel && section != 3) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                ) {
                    sectionContent(Modifier.weight(1f).fillMaxHeight())
                    ExtensionOperationsPanel(
                        status = status,
                        engineRunning = engineRunning,
                        installedCount = installedSuwayomi.size + installedDesktopExtensions.size,
                        updateCount = suwayomiUpdates.size + desktopUpdates.size,
                        repositoryCount = availableDesktopExtensions.size,
                        busyLabel = busyLabel,
                        onOpenRepository = { onSelectSection(3) },
                        modifier = Modifier.width(RoninLayout.rightPanelWidth).fillMaxHeight(),
                    )
                }
            } else {
                sectionContent(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ExtensionOperationsPanel(
    status: String,
    engineRunning: Boolean,
    installedCount: Int,
    updateCount: Int,
    repositoryCount: Int,
    busyLabel: String?,
    onOpenRepository: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiLanguage = LocalRoninLanguage.current
    RoninPanel(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro)) {
                Text(
                    roninCopy("Extension engine", "Motor de extensiones", uiLanguage),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    roninCopy(
                        "Live state from Ronin’s configured extension engine.",
                        "Estado actual del motor de extensiones de Ronin.",
                        uiLanguage,
                    ),
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            RoninBadge(
                label = if (engineRunning) {
                    roninCopy(
                        "Engine ready",
                        "Motor listo",
                        uiLanguage,
                    )
                } else {
                    roninCopy("Engine offline", "Motor desconectado", uiLanguage)
                },
                accent = engineRunning,
            )
            Text(
                roninUiText(status),
                color = if (engineRunning) RoninColors.accentSage else RoninColors.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                DirectoryLabel(roninCopy("Installed", "Instaladas", uiLanguage), installedCount.toString())
                DirectoryLabel(roninCopy("Updates", "Actualizaciones", uiLanguage), updateCount.toString())
            }
            if (busyLabel != null) {
                RoninBadge(busyLabel, accent = true)
            }
            androidx.compose.material3.HorizontalDivider(color = RoninColors.borderSubtle)
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                Text(
                    roninCopy("Repository management", "Administrar repositorios", uiLanguage),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    roninCopy(
                        "$repositoryCount repository entries are currently loaded.",
                        "$repositoryCount entradas de repositorio cargadas.",
                        uiLanguage,
                    ),
                    color = RoninColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    roninCopy(
                        "Manage package sources and local signing fingerprints.",
                        "Administra repositorios de paquetes y huellas de firma locales.",
                        uiLanguage,
                    ),
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            RoninSecondaryButton(
                label = roninCopy("Open repositories", "Abrir repositorios", uiLanguage),
                onClick = onOpenRepository,
                modifier = Modifier.fillMaxWidth(),
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
    onDiscover: () -> Unit,
    onAction: (SuwayomiExtension, ExtensionAction) -> Unit,
) {
    val uiLanguage = LocalRoninLanguage.current
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    val total = suwayomi.size + desktop.size

    if (total == 0) {
        RoninPanel(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(RoninSpacing.xLarge)) {
                Column(
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                ) {
                    Text(
                        roninCopy("Installed extensions", "Extensiones instaladas", uiLanguage),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        roninCopy("No extensions installed", "No hay extensiones instaladas", uiLanguage),
                        color = RoninColors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        roninCopy(
                            "Discover community sources or load a signed Desktop package repository when you need one.",
                            "Explora las fuentes de la comunidad o carga un repositorio de paquetes firmados para escritorio.",
                            uiLanguage,
                        ),
                        color = RoninColors.textSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    RoninButton(roninCopy("Explore catalog", "Explorar catálogo", uiLanguage), onDiscover)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        item("installed-summary") {
            DirectoryLabel(
                roninCopy("Installed", "Instaladas", uiLanguage),
                roninCopy("$total extensions", "$total extensiones", uiLanguage),
            )
        }
        items(suwayomi, key = { "suwayomi:${it.pkgName}" }) { entry ->
            ExtensionRow(
                title = entry.name,
                subtitle = "${entry.versionName} · ${entry.pkgName}",
                badges = buildList {
                    entry.languageContext()?.let(::add)
                    add("Keiyoushi")
                    if (entry.hasUpdate) {
                        add(
                            roninCopy("Update available", "Actualización disponible", uiLanguage),
                        )
                    } else {
                        add(roninCopy("Installed", "Instaladas", uiLanguage))
                    }
                },
                accentLastBadge = entry.hasUpdate,
                busy = busyPackage == entry.pkgName,
                busyLabel = busyLabel,
                actions = {
                    if (entry.hasUpdate) {
                        RoninInlineAction(
                            label = roninCopy("Update", "Actualizar", uiLanguage),
                            onClick = { onAction(entry, ExtensionAction.UPDATE) },
                            enabled = busyPackage == null,
                        )
                    } else if (pendingRemoval == entry.pkgName) {
                        RoninInlineAction(
                            label = roninCopy("Cancel", "Cancelar", uiLanguage),
                            onClick = { pendingRemoval = null },
                            enabled = busyPackage == null,
                        )
                        RoninInlineAction(
                            label = roninCopy("Confirm remove", "Confirmar eliminación", uiLanguage),
                            onClick = {
                                pendingRemoval = null
                                onAction(entry, ExtensionAction.REMOVE)
                            },
                            enabled = busyPackage == null,
                        )
                    } else {
                        RoninInlineAction(
                            label = roninCopy("Remove", "Eliminar", uiLanguage),
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
                subtitle = roninCopy(
                    "Version ${entry.versionCode} · ${entry.fingerprint.take(12)}…",
                    "Versión ${entry.versionCode} · ${entry.fingerprint.take(12)}…",
                    uiLanguage,
                ),
                badges = listOf(
                    roninCopy("Desktop .mihonext", "Escritorio .mihonext", uiLanguage),
                    roninCopy("Locally trusted", "Confianza local", uiLanguage),
                ),
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
    val uiLanguage = LocalRoninLanguage.current
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
                        Text(
                            roninCopy("Keiyoushi catalog", "Catálogo de Keiyoushi", uiLanguage),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            roninUiText(status),
                            color = if (engineRunning) RoninColors.accentSage else RoninColors.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (narrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                        copy(Modifier.fillMaxWidth())
                        RoninButton(
                            roninCopy("Refresh catalog", "Actualizar catálogo", uiLanguage),
                            onRefresh,
                            enabled =
                            engineRunning && !loading,
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        copy(Modifier.weight(1f))
                        RoninButton(
                            roninCopy("Refresh catalog", "Actualizar catálogo", uiLanguage),
                            onRefresh,
                            enabled =
                            engineRunning && !loading,
                        )
                    }
                }
            }
        }

        RoninSearchField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = roninCopy("Search extensions or language", "Buscar extensiones o idioma", uiLanguage),
            modifier = Modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium),
            enabled = !loading,
        )

        if (loading && extensions.isEmpty()) {
            RoninLoadingState(roninCopy("Loading extension catalog…", "Cargando catálogo de extensiones…", uiLanguage))
            return
        }
        if (!engineRunning && extensions.isEmpty()) {
            RoninErrorState(
                roninCopy("Extension engine unavailable", "Motor de extensiones no disponible", uiLanguage),
                roninUiText(status),
            )
            return
        }
        if (extensions.isEmpty()) {
            RoninEmptyState(
                roninCopy("Catalog is not loaded", "El catálogo no está cargado", uiLanguage),
                roninCopy(
                    "Refresh the catalog to discover compatible sources.",
                    "Actualiza el catálogo para descubrir fuentes compatibles.",
                    uiLanguage,
                ),
            )
            return
        }
        if (visible.isEmpty()) {
            RoninEmptyState(
                roninCopy("No matching extensions", "No se encontraron extensiones", uiLanguage),
                roninCopy(
                    "Try another name, package, or language.",
                    "Prueba otro nombre, paquete o idioma.",
                    uiLanguage,
                ),
            )
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
                                entry.installed && entry.hasUpdate -> roninCopy(
                                    "Update available",
                                    "Actualización disponible",
                                    uiLanguage,
                                )
                                entry.installed -> roninCopy("Installed", "Instaladas", uiLanguage)
                                else -> roninCopy("Available", "Disponible", uiLanguage)
                            },
                        )
                    },
                    accentLastBadge = entry.installed || entry.hasUpdate,
                    busy = busyPackage == entry.pkgName,
                    busyLabel = busyLabel,
                    actions = {
                        RoninInlineAction(
                            label = when {
                                !entry.installed -> roninCopy("Install", "Instalar", uiLanguage)
                                entry.hasUpdate -> roninCopy("Update", "Actualizar", uiLanguage)
                                else -> roninCopy("Installed", "Instaladas", uiLanguage)
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
    val uiLanguage = LocalRoninLanguage.current
    if (suwayomi.isEmpty() && desktop.isEmpty()) {
        RoninEmptyState(
            title = roninCopy("No updates available", "No hay actualizaciones disponibles", uiLanguage),
            detail = if (repositoryLoaded) {
                roninCopy(
                    "Installed extensions match the currently loaded catalogs.",
                    "Las extensiones instaladas están al día según los catálogos cargados.",
                    uiLanguage,
                )
            } else {
                roninCopy(
                    "Keiyoushi is current. Load a Desktop repository index to check signed .mihonext packages.",
                    "Keiyoushi está al día. Carga un índice de escritorio para comprobar los paquetes .mihonext firmados.",
                    uiLanguage,
                )
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        if (suwayomi.isNotEmpty()) {
            item("suwayomi-updates-label") {
                DirectoryLabel(
                    "Keiyoushi",
                    roninCopy("${suwayomi.size} updates", "${suwayomi.size} actualizaciones", uiLanguage),
                )
            }
            items(suwayomi, key = { "suwayomi-update:${it.pkgName}" }) { entry ->
                ExtensionRow(
                    title = entry.name,
                    subtitle = "${entry.versionName} · ${entry.pkgName}",
                    badges = buildList {
                        entry.languageContext()?.let(::add)
                        add(roninCopy("Update available", "Actualización disponible", uiLanguage))
                    },
                    accentLastBadge = true,
                    busy = busyPackage == entry.pkgName,
                    busyLabel = busyLabel,
                    actions = {
                        RoninInlineAction(
                            roninCopy("Update", "Actualizar", uiLanguage),
                            { onSuwayomiAction(entry, ExtensionAction.UPDATE) },
                            enabled = busyPackage == null,
                        )
                    },
                )
            }
        }
        if (desktop.isNotEmpty()) {
            item("desktop-updates-label") {
                DirectoryLabel(
                    roninCopy("Desktop packages", "Paquetes de escritorio", uiLanguage),
                    roninCopy("${desktop.size} updates", "${desktop.size} actualizaciones", uiLanguage),
                )
            }
            items(desktop, key = { "desktop-update:${it.id}" }) { entry ->
                val installed = installedDesktopById[entry.id]
                ExtensionRow(
                    title = entry.name,
                    subtitle = roninCopy(
                        "Version ${installed?.versionCode ?: 0} → ${entry.versionCode} · ${entry.id}",
                        "Versión ${installed?.versionCode ?: 0} → ${entry.versionCode} · ${entry.id}",
                        uiLanguage,
                    ),
                    badges = listOf(
                        roninCopy("Signed .mihonext", ".mihonext firmado", uiLanguage),
                        roninCopy("Update available", "Actualización disponible", uiLanguage),
                    ),
                    accentLastBadge = true,
                    busy = busyPackage == entry.id,
                    busyLabel = busyLabel,
                    actions = {
                        RoninInlineAction(
                            roninCopy("Update", "Actualizar", uiLanguage),
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
    val uiLanguage = LocalRoninLanguage.current
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
                    Text(
                        roninCopy("Desktop repository index", "Índice de repositorio de escritorio", uiLanguage),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        roninCopy(
                            "Ronin accepts HTTPS indexes for signed .mihonext packages. The URL is session state, not a saved repository list.",
                            "Ronin acepta índices HTTPS de paquetes .mihonext firmados. El enlace se utiliza en esta sesión.",
                            uiLanguage,
                        ),
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
                        label = if (loading) {
                            roninCopy(
                                "Loading…",
                                "Cargando…",
                                uiLanguage,
                            )
                        } else {
                            roninCopy("Load index", "Cargar índice", uiLanguage)
                        },
                        onClick = onDiscover,
                        enabled = repositoryUrl.isNotBlank() && !loading && busyPackage == null,
                    )
                }
            }
        }

        if (available.isNotEmpty()) {
            item("repository-results-label") {
                DirectoryLabel(
                    roninCopy("Repository packages", "Paquetes del repositorio", uiLanguage),
                    roninCopy("${available.size} entries", "${available.size} entradas", uiLanguage),
                )
            }
            items(available, key = { "repository:${it.id}" }) { entry ->
                val installed = installedDesktopById[entry.id]
                val action = when {
                    installed == null -> roninCopy("Install", "Instalar", uiLanguage)
                    entry.versionCode > installed.versionCode -> roninCopy("Update", "Actualizar", uiLanguage)
                    else -> roninCopy("Installed", "Instaladas", uiLanguage)
                }
                ExtensionRow(
                    title = entry.name,
                    subtitle = roninCopy(
                        "Version ${entry.versionCode} · ${entry.id}",
                        "Versión ${entry.versionCode} · ${entry.id}",
                        uiLanguage,
                    ),
                    badges = buildList {
                        add(roninCopy("Signed index", "Índice firmado", uiLanguage))
                        add(
                            when {
                                installed == null -> roninCopy("Available", "Disponible", uiLanguage)
                                entry.versionCode > installed.versionCode -> roninCopy(
                                    "Update available",
                                    "Actualización disponible",
                                    uiLanguage,
                                )
                                else -> roninCopy("Installed", "Instaladas", uiLanguage)
                            },
                        )
                    },
                    accentLastBadge = installed != null,
                    busy = busyPackage == entry.id,
                    busyLabel = busyLabel,
                    actions = {
                        RoninInlineAction(
                            label = roninCopy("Fingerprint", "Huella de firma", uiLanguage),
                            onClick = { onFingerprintChange(entry.fingerprint) },
                            enabled = busyPackage == null,
                        )
                        RoninInlineAction(
                            label = action,
                            onClick = { onInstallEntry(entry) },
                            enabled = action != roninCopy("Installed", "Instaladas", uiLanguage) && busyPackage == null,
                        )
                    },
                )
            }
        } else if (!loading) {
            item("repository-empty") {
                RoninEmptyState(
                    roninCopy("No Desktop repository loaded", "No hay repositorio de escritorio cargado", uiLanguage),
                    roninCopy(
                        "Load a valid HTTPS index to discover or update signed Desktop extensions.",
                        "Carga un índice HTTPS válido para descubrir o actualizar extensiones de escritorio firmadas.",
                        uiLanguage,
                    ),
                )
            }
        }

        item("local-trust") {
            RoninPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(RoninSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Text(
                        roninCopy("Local package trust", "Confianza en paquetes locales", uiLanguage),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        roninCopy(
                            "Trusting a fingerprint records a local decision only; it does not verify a publisher identity.",
                            "Confiar en una huella registra tu decisión local; no verifica la identidad del autor.",
                            uiLanguage,
                        ),
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RoninSearchField(
                        value = fingerprint,
                        onValueChange = onFingerprintChange,
                        placeholder = roninCopy(
                            "64-character SHA-256 signing fingerprint",
                            "Huella de firma SHA-256 de 64 caracteres",
                            uiLanguage,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = busyPackage == null,
                    )
                    RoninSecondaryButton(
                        roninCopy("Trust fingerprint", "Confiar en la huella", uiLanguage),
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
                            label = roninCopy("Choose package…", "Elegir paquete…", uiLanguage),
                            onClick = onChoosePackage,
                            enabled = busyPackage == null,
                        )
                        RoninButton(
                            label = roninCopy("Install local package", "Instalar paquete local", uiLanguage),
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
    val uiLanguage = LocalRoninLanguage.current
    val interactionSource = remember(title, subtitle) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier.fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource),
        shape = RoundedCornerShape(RoninRadius.card),
        color = if (hovered) RoninColors.hoverSurface else RoninColors.elevatedSurface,
        border = BorderStroke(
            RoninBorders.hairline,
            if (hovered) RoninColors.accentCoral else RoninColors.borderSubtle,
        ),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = RoninSpacing.large, vertical = RoninSpacing.medium),
        ) {
            val narrow = maxWidth < 560.dp
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
                            RoninBadge(
                                busyLabel?.let {
                                    roninUiText(it)
                                } ?: roninCopy("Working…", "Procesando…", uiLanguage),
                                accent = true,
                            )
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
