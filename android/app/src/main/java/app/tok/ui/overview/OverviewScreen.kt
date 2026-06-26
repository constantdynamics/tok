package app.tok.ui.overview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tok.data.local.BulletWithLabels
import app.tok.data.local.LabelEntity
import app.tok.data.repo.TokRepository
import app.tok.di.ServiceLocator
import app.tok.ui.components.LabelChip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode(val label: String) { ORDER("Volgorde"), NEWEST("Nieuwste"), OLDEST("Oudste") }
enum class ArchiveFilter(val label: String) { ACTIVE("Actief"), ARCHIVED("Archief"), ALL("Alles") }

class OverviewViewModel(private val repo: TokRepository) : ViewModel() {

    private val _filterLabels = MutableStateFlow<Set<String>>(emptySet())
    val filterLabels: StateFlow<Set<String>> = _filterLabels.asStateFlow()
    private val _archiveFilter = MutableStateFlow(ArchiveFilter.ACTIVE)
    val archiveFilter: StateFlow<ArchiveFilter> = _archiveFilter.asStateFlow()
    private val _sort = MutableStateFlow(SortMode.ORDER)
    val sort: StateFlow<SortMode> = _sort.asStateFlow()

    val selection = MutableStateFlow<Set<String>>(emptySet())

    val labels = repo.labels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allBullets = repo.bullets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visible: StateFlow<List<BulletWithLabels>> =
        combine(repo.bullets, _filterLabels, _archiveFilter, _sort) { bullets, fLabels, arch, sort ->
            bullets
                .filter {
                    when (arch) {
                        ArchiveFilter.ACTIVE -> !it.bullet.isArchived
                        ArchiveFilter.ARCHIVED -> it.bullet.isArchived
                        ArchiveFilter.ALL -> true
                    }
                }
                .filter { fLabels.isEmpty() || it.labels.any { l -> l.id in fLabels } }
                .sortedWith(
                    when (sort) {
                        SortMode.ORDER -> compareBy({ it.bullet.sortOrder }, { -it.bullet.createdAt })
                        SortMode.NEWEST -> compareByDescending { it.bullet.createdAt }
                        SortMode.OLDEST -> compareBy { it.bullet.createdAt }
                    },
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(s: SortMode) { _sort.value = s }
    fun setArchiveFilter(f: ArchiveFilter) { _archiveFilter.value = f }
    fun toggleFilterLabel(id: String) {
        _filterLabels.value = _filterLabels.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    fun toggleSelect(id: String) {
        selection.value = selection.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }
    fun clearSelection() { selection.value = emptySet() }

    fun updateText(id: String, text: String) = viewModelScope.launch { repo.updateBulletText(id, text) }
    fun refresh() = viewModelScope.launch { repo.syncNow() }

    fun archiveSelected(archived: Boolean) {
        val sel = selection.value
        viewModelScope.launch { repo.setArchived(sel, archived) }
        clearSelection()
    }

    fun deleteSelected() {
        val sel = selection.value
        viewModelScope.launch { repo.deleteBullets(sel) }
        clearSelection()
    }

    fun addLabelsToSelected(labelIds: List<String>) = applyLabels(labelIds, add = true)
    fun removeLabelsFromSelected(labelIds: List<String>) = applyLabels(labelIds, add = false)

    private fun applyLabels(labelIds: List<String>, add: Boolean) {
        val sel = selection.value
        val map = allBullets.value.associateBy { it.bullet.id }
        viewModelScope.launch {
            for (id in sel) {
                val cur = map[id]?.labels?.map { it.id } ?: continue
                val next = if (add) (cur + labelIds).distinct() else cur - labelIds.toSet()
                if (next != cur) repo.setBulletLabels(id, next)
            }
        }
        clearSelection()
    }

    fun moveUp(id: String) {
        val list = visible.value
        val idx = list.indexOfFirst { it.bullet.id == id }
        if (idx <= 0) return
        val above = list[idx - 1].bullet
        val aboveAbove = list.getOrNull(idx - 2)?.bullet
        val newOrder = if (aboveAbove == null) above.sortOrder - 1.0 else (aboveAbove.sortOrder + above.sortOrder) / 2.0
        viewModelScope.launch { repo.reorderBullet(id, newOrder) }
    }

    fun moveDown(id: String) {
        val list = visible.value
        val idx = list.indexOfFirst { it.bullet.id == id }
        if (idx < 0 || idx >= list.size - 1) return
        val below = list[idx + 1].bullet
        val belowBelow = list.getOrNull(idx + 2)?.bullet
        val newOrder = if (belowBelow == null) below.sortOrder + 1.0 else (below.sortOrder + belowBelow.sortOrder) / 2.0
        viewModelScope.launch { repo.reorderBullet(id, newOrder) }
    }
}

@Composable
fun OverviewScreen() {
    val vm: OverviewViewModel = viewModel { OverviewViewModel(ServiceLocator.repository) }
    val bullets by vm.visible.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val archiveFilter by vm.archiveFilter.collectAsStateWithLifecycle()
    val filterLabels by vm.filterLabels.collectAsStateWithLifecycle()

    var showLabelDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (selection.isEmpty()) {
            FilterSortBar(
                sort = sort,
                archiveFilter = archiveFilter,
                labels = labels,
                filterLabels = filterLabels,
                onSort = vm::setSort,
                onArchiveFilter = vm::setArchiveFilter,
                onToggleLabel = vm::toggleFilterLabel,
                onRefresh = { vm.refresh() },
            )
        } else {
            SelectionBar(
                count = selection.size,
                archiveFilter = archiveFilter,
                onLabel = { showLabelDialog = true },
                onArchive = { vm.archiveSelected(archiveFilter != ArchiveFilter.ARCHIVED) },
                onDelete = { showDeleteDialog = true },
                onClear = vm::clearSelection,
            )
        }

        if (bullets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nog niets hier. Neem iets op of pas je filter aan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bullets, key = { it.bullet.id }) { item ->
                    BulletRow(
                        item = item,
                        selectionMode = selection.isNotEmpty(),
                        selected = item.bullet.id in selection,
                        showReorder = sort == SortMode.ORDER && selection.isEmpty(),
                        editing = editingId == item.bullet.id,
                        onToggleSelect = { vm.toggleSelect(item.bullet.id) },
                        onStartEdit = { editingId = item.bullet.id },
                        onCommitEdit = { text ->
                            vm.updateText(item.bullet.id, text)
                            editingId = null
                        },
                        onCancelEdit = { editingId = null },
                        onMoveUp = { vm.moveUp(item.bullet.id) },
                        onMoveDown = { vm.moveDown(item.bullet.id) },
                    )
                }
            }
        }
    }

    if (showLabelDialog) {
        BulkLabelDialog(
            labels = labels,
            onAdd = { vm.addLabelsToSelected(it); showLabelDialog = false },
            onRemove = { vm.removeLabelsFromSelected(it); showLabelDialog = false },
            onDismiss = { showLabelDialog = false },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Verwijderen?") },
            text = { Text("${selection.size} bullet(s) definitief verwijderen. Dit kan niet ongedaan worden gemaakt.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelected(); showDeleteDialog = false }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun FilterSortBar(
    sort: SortMode,
    archiveFilter: ArchiveFilter,
    labels: List<LabelEntity>,
    filterLabels: Set<String>,
    onSort: (SortMode) -> Unit,
    onArchiveFilter: (ArchiveFilter) -> Unit,
    onToggleLabel: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DropdownButton(icon = Icons.Filled.Sort, text = sort.label) { close ->
                SortMode.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { onSort(s); close() })
                }
            }
            Spacer(Modifier.width(8.dp))
            DropdownButton(icon = Icons.Filled.Inventory2, text = archiveFilter.label) { close ->
                ArchiveFilter.entries.forEach { a ->
                    DropdownMenuItem(text = { Text(a.label) }, onClick = { onArchiveFilter(a); close() })
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Synchroniseren") }
        }
        if (labels.isNotEmpty()) {
            FlowRowLabels(labels, filterLabels, onToggleLabel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowLabels(labels: List<LabelEntity>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        labels.forEach { l ->
            LabelChip(l.name, l.color, selected = l.id in selected, onClick = { onToggle(l.id) })
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    archiveFilter: ArchiveFilter,
    onLabel: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Sluiten") }
        Text("$count geselecteerd", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onLabel) { Icon(Icons.Filled.Label, contentDescription = "Labelen") }
        IconButton(onClick = onArchive) {
            Icon(
                Icons.Filled.Inventory2,
                contentDescription = if (archiveFilter == ArchiveFilter.ARCHIVED) "Dearchiveren" else "Archiveren",
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Verwijderen") }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BulletRow(
    item: BulletWithLabels,
    selectionMode: Boolean,
    selected: Boolean,
    showReorder: Boolean,
    editing: Boolean,
    onToggleSelect: () -> Unit,
    onStartEdit: () -> Unit,
    onCommitEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else if (!editing) onStartEdit() },
                    onLongClick = { if (!editing) onToggleSelect() },
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                if (editing) {
                    var text by remember { mutableStateOf(item.bullet.text) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onCancelEdit) { Text("Annuleren") }
                        TextButton(onClick = { onCommitEdit(text) }) { Text("Opslaan") }
                    }
                } else {
                    Text(
                        item.bullet.text.ifBlank { "(leeg)" },
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.bullet.isArchived) TextDecoration.LineThrough else null,
                        fontStyle = if (item.bullet.isArchived) FontStyle.Italic else FontStyle.Normal,
                        color = if (item.bullet.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.labels.isNotEmpty()) {
                        Spacer(Modifier.size(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.labels.forEach { LabelChip(it.name, it.color) }
                        }
                    }
                }
            }
            if (showReorder && !editing) {
                Column {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Omhoog")
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Omlaag")
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu { open = false }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkLabelDialog(
    labels: List<LabelEntity>,
    onAdd: (List<String>) -> Unit,
    onRemove: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Labels voor selectie") },
        text = {
            if (labels.isEmpty()) {
                Text("Nog geen labels. Maak ze aan op het tabblad Labels.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { l ->
                        val sel = l.id in chosen
                        LabelChip(l.name, l.color, selected = sel, onClick = {
                            if (sel) chosen.remove(l.id) else chosen.add(l.id)
                        })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(chosen.toList()) }, enabled = chosen.isNotEmpty()) { Text("Toevoegen") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onRemove(chosen.toList()) }, enabled = chosen.isNotEmpty()) { Text("Weghalen") }
                TextButton(onClick = onDismiss) { Text("Sluiten") }
            }
        },
    )
}
