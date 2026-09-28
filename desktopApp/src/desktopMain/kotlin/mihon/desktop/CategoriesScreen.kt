package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import eu.kanade.tachiyomi.source.Source
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing
import tachiyomi.data.GetCategories
import tachiyomi.data.Mangas

@Composable
internal fun CategoriesScreen(
    categories: List<GetCategories>,
    library: List<Mangas>,
    membership: Map<Long, Set<Long>>,
    sources: List<Source>,
    search: String,
    onSearchChange: (String) -> Unit,
    newCategoryName: String,
    onNewCategoryNameChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onCreateCategory: () -> Unit,
    onRenameCategory: (Long, String) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onMoveCategory: (Long, Int) -> Unit,
    onOpenCategory: (Long) -> Unit,
) {
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingName by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    val sourceById = remember(sources) { sources.associateBy(Source::id) }
    val mangaByCategory = remember(categories, library, membership) {
        val result = categories.associate { it.id to mutableListOf<Mangas>() }.toMutableMap()
        library.forEach { manga ->
            val categoryIds = membership[manga._id].orEmpty()
            if (categoryIds.isEmpty()) {
                result[0L]?.add(manga)
            } else {
                categoryIds.forEach { categoryId -> result[categoryId]?.add(manga) }
            }
        }
        result.mapValues { (_, manga) -> manga.toList() }
    }
    val visibleCategories = remember(categories, search) {
        categories.filter { category ->
            val displayName = category.name.ifBlank { "Uncategorized" }
            search.isBlank() || displayName.contains(search, ignoreCase = true)
        }
    }
    val userCategories = remember(categories) { categories.filter { it.id > 0 } }
    val customCount = userCategories.size
    val uncategorizedCount = mangaByCategory[0L].orEmpty().size

    Column(Modifier.fillMaxSize()) {
        RoninSectionHeader(
            title = "Categories",
            subtitle = "$customCount custom categories · $uncategorizedCount uncategorized manga",
        )

        RoninPanel(Modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(RoninSpacing.medium)) {
                val narrow = maxWidth < 760.dp
                val searchField: @Composable (Modifier) -> Unit = { modifier ->
                    RoninSearchField(
                        value = search,
                        onValueChange = onSearchChange,
                        placeholder = "Find category",
                        modifier = modifier,
                        enabled = !loading,
                    )
                }
                val createField: @Composable (Modifier) -> Unit = { modifier ->
                    Row(
                        modifier = modifier,
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoninSearchField(
                            value = newCategoryName,
                            onValueChange = onNewCategoryNameChange,
                            placeholder = "New category",
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                        )
                        RoninButton(
                            label = "Create",
                            onClick = onCreateCategory,
                            enabled = newCategoryName.isNotBlank() && !loading,
                        )
                    }
                }

                if (narrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                        searchField(Modifier.fillMaxWidth())
                        createField(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        searchField(Modifier.weight(1f))
                        createField(Modifier.weight(1f))
                    }
                }
            }
        }

        if (error != null) {
            RoninErrorState(
                title = "Could not load categories",
                detail = error,
                modifier = Modifier.padding(bottom = RoninSpacing.medium),
            )
        }

        if (loading && categories.isEmpty()) {
            RoninLoadingState("Loading categories…")
            return
        }

        if (visibleCategories.isEmpty()) {
            RoninEmptyState(
                title = if (search.isBlank()) "No categories yet" else "No categories found",
                detail = if (search.isBlank()) {
                    "Create a category to organize your library."
                } else {
                    "Try a different category name."
                },
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
        ) {
            items(visibleCategories, key = GetCategories::id) { category ->
                val manga = mangaByCategory[category.id].orEmpty()
                val userIndex = userCategories.indexOfFirst { it.id == category.id }
                CategoryRow(
                    category = category,
                    manga = manga,
                    sourceById = sourceById,
                    editing = editingId == category.id,
                    editingName = editingName,
                    onEditingNameChange = { editingName = it },
                    pendingDelete = pendingDeleteId == category.id,
                    canMoveUp = category.id > 0 && userIndex > 0,
                    canMoveDown = category.id > 0 && userIndex in 0 until userCategories.lastIndex,
                    onOpen = { onOpenCategory(category.id) },
                    onBeginEdit = {
                        pendingDeleteId = null
                        editingId = category.id
                        editingName = category.name
                    },
                    onCancelEdit = {
                        editingId = null
                        editingName = ""
                    },
                    onSaveEdit = {
                        val name = editingName.trim()
                        if (name.isNotEmpty()) {
                            onRenameCategory(category.id, name)
                            editingId = null
                            editingName = ""
                        }
                    },
                    onMoveUp = { onMoveCategory(category.id, -1) },
                    onMoveDown = { onMoveCategory(category.id, 1) },
                    onBeginDelete = {
                        editingId = null
                        pendingDeleteId = category.id
                    },
                    onCancelDelete = { pendingDeleteId = null },
                    onConfirmDelete = {
                        pendingDeleteId = null
                        onDeleteCategory(category.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: GetCategories,
    manga: List<Mangas>,
    sourceById: Map<Long, Source>,
    editing: Boolean,
    editingName: String,
    onEditingNameChange: (String) -> Unit,
    pendingDelete: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onBeginEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onBeginDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val interactionSource = remember(category.id) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val systemCategory = category.id == 0L
    val displayName = category.name.ifBlank { "Uncategorized" }

    Surface(
        modifier = Modifier.fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource),
        shape = RoundedCornerShape(RoninRadius.card),
        color = if (hovered) RoninColors.hoverSurface else RoninColors.elevatedSurface,
        border = BorderStroke(
            RoninBorders.hairline,
            if (hovered) RoninColors.accentSageMuted else RoninColors.border,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(RoninSpacing.medium)) {
            val narrow = maxWidth < 820.dp
            if (editing && !systemCategory) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Text("Rename $displayName", style = MaterialTheme.typography.titleMedium)
                    RoninSearchField(
                        value = editingName,
                        onValueChange = onEditingNameChange,
                        placeholder = "Category name",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    ) {
                        RoninButton("Save", onSaveEdit, enabled = editingName.isNotBlank())
                        RoninSecondaryButton("Cancel", onCancelEdit)
                    }
                }
                return@BoxWithConstraints
            }

            val identity: @Composable () -> Unit = {
                Column(
                    modifier = if (narrow) Modifier.fillMaxWidth() else Modifier.width(210.dp),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                ) {
                    Text(displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${manga.size} manga",
                        color = RoninColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (systemCategory) {
                        RoninBadge("System")
                    } else {
                        RoninBadge("Category", accent = hovered)
                    }
                }
            }
            val previews: @Composable () -> Unit = {
                if (manga.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(76.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("No manga assigned", color = RoninColors.textMuted)
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                    ) {
                        manga.take(5).forEach { item ->
                            DesktopCover(
                                item.thumbnail_url,
                                sourceById[item.source],
                                Modifier.width(52.dp).height(74.dp),
                            )
                        }
                        if (manga.size > 5) {
                            Box(
                                Modifier.width(52.dp).height(74.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "+${manga.size - 5}",
                                    color = RoninColors.textMuted,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
            val actions: @Composable () -> Unit = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                ) {
                    RoninInlineAction("Open", onOpen)
                    if (!systemCategory) {
                        if (pendingDelete) {
                            RoninInlineAction("Cancel", onCancelDelete)
                            RoninInlineAction("Confirm delete", onConfirmDelete)
                        } else {
                            RoninInlineAction("Rename", onBeginEdit)
                            RoninInlineAction("Up", onMoveUp, enabled = canMoveUp)
                            RoninInlineAction("Down", onMoveDown, enabled = canMoveDown)
                            RoninInlineAction("Delete", onBeginDelete)
                        }
                    }
                }
            }

            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium)) {
                    identity()
                    previews()
                    actions()
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    identity()
                    Box(Modifier.weight(1f)) { previews() }
                    actions()
                }
            }
        }
    }
}
