package app.tok.ui.labels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tok.data.local.LabelEntity
import app.tok.data.repo.TokRepository
import app.tok.di.ServiceLocator
import app.tok.ui.components.NeonColorPicker
import app.tok.ui.components.colorFromHex
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LabelsViewModel(private val repo: TokRepository) : ViewModel() {
    val labels = repo.labels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(name: String, color: String) = viewModelScope.launch { repo.addLabel(name, color) }
    fun update(id: String, name: String, color: String) = viewModelScope.launch { repo.updateLabel(id, name, color) }
    fun delete(id: String) = viewModelScope.launch { repo.deleteLabel(id) }
}

@Composable
fun LabelsScreen() {
    val vm: LabelsViewModel = viewModel { LabelsViewModel(ServiceLocator.repository) }
    val labels by vm.labels.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<LabelEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (labels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nog geen labels. Tik op + om er een te maken.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(labels, key = { it.id }) { label ->
                    Card(Modifier.fillMaxWidth().clickable { editing = label }) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(colorFromHex(label.color)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.delete(label.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Verwijderen")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Nieuw label")
        }
    }

    if (creating) {
        LabelEditorDialog(
            initialName = "",
            initialColor = "#39FF14",
            onConfirm = { name, color -> vm.add(name, color); creating = false },
            onDismiss = { creating = false },
        )
    }
    editing?.let { label ->
        LabelEditorDialog(
            initialName = label.name,
            initialColor = label.color,
            onConfirm = { name, color -> vm.update(label.id, name, color); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun LabelEditorDialog(
    initialName: String,
    initialColor: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isBlank()) "Nieuw label" else "Label bewerken") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(16.dp))
                NeonColorPicker(selected = color, onSelected = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), color.trim()) }, enabled = name.isNotBlank()) {
                Text("Opslaan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
