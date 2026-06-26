package app.tok.ui.record

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tok.data.local.LabelEntity
import app.tok.data.prefs.TokPrefs
import app.tok.data.repo.TokRepository
import app.tok.di.ServiceLocator
import app.tok.speech.ModelManager
import app.tok.speech.RecordingService
import app.tok.speech.SpeechController
import app.tok.ui.components.LabelChip
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordViewModel(
    private val repo: TokRepository,
    private val speech: SpeechController,
    private val models: ModelManager,
    private val prefs: TokPrefs,
) : ViewModel() {

    val listening = speech.listening
    val partial = speech.partial
    val segments = speech.segments
    val tier = speech.tier
    val smallState = models.small
    val largeState = models.large
    val speechError = speech.error
    val labels = repo.labels

    val largeEnabled = prefs.largeModelEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var lastBulletId by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { models.ensure(ModelManager.Tier.SMALL) }
        viewModelScope.launch {
            prefs.largeModelEnabled.collect { enabled ->
                if (enabled && models.loaded(ModelManager.Tier.LARGE) == null) {
                    models.ensure(ModelManager.Tier.LARGE)
                }
            }
        }
    }

    fun modelReady() = models.loaded(ModelManager.Tier.SMALL) != null || models.loaded(ModelManager.Tier.LARGE) != null

    fun startRecording() {
        if (!modelReady()) {
            viewModelScope.launch { models.ensure(ModelManager.Tier.SMALL) }
            return
        }
        RecordingService.start(ServiceLocator.appContext)
        speech.start(preferLarge = largeEnabled.value)
    }

    fun newBullet() {
        val text = speech.cutCurrentText()
        if (text.isNotBlank()) viewModelScope.launch { lastBulletId = repo.addBullet(text) }
    }

    fun stopRecording() {
        speech.stop()
        val text = speech.cutCurrentText()
        RecordingService.stop(ServiceLocator.appContext)
        if (text.isNotBlank()) viewModelScope.launch { lastBulletId = repo.addBullet(text) }
    }

    fun clearLastBullet() {
        lastBulletId = null
    }

    fun setLabelsOnLast(labelIds: List<String>) {
        val id = lastBulletId ?: return
        viewModelScope.launch { repo.setBulletLabels(id, labelIds) }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordScreen() {
    val vm: RecordViewModel = viewModel {
        RecordViewModel(
            ServiceLocator.repository,
            ServiceLocator.speechController,
            ServiceLocator.modelManager,
            ServiceLocator.prefs,
        )
    }
    val listening by vm.listening.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val segments by vm.segments.collectAsStateWithLifecycle()
    val tier by vm.tier.collectAsStateWithLifecycle()
    val smallState by vm.smallState.collectAsStateWithLifecycle()
    val largeState by vm.largeState.collectAsStateWithLifecycle()
    val largeEnabled by vm.largeEnabled.collectAsStateWithLifecycle()
    val speechError by vm.speechError.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle(initialValue = emptyList())

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        ModelStatusRow(smallState, largeState, largeEnabled, listening, tier)

        Card(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (segments.isEmpty() && partial.isBlank()) {
                    Text(
                        if (listening) "Aan het luisteren…" else "Druk op de knop en begin te praten.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                append(segments.joinToString(" "))
                            }
                            if (partial.isNotBlank()) {
                                if (segments.isNotEmpty()) append(" ")
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(partial)
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        speechError?.let {
            Text(
                "Spraakfout: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (listening) {
                OutlinedButton(onClick = vm::newBullet) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nieuwe bullet")
                }
                Spacer(Modifier.width(20.dp))
            }
            LargeFloatingActionButton(
                onClick = {
                    when {
                        listening -> vm.stopRecording()
                        micPermission.status.isGranted -> vm.startRecording()
                        else -> micPermission.launchPermissionRequest()
                    }
                },
                containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
            ) {
                Icon(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (listening) "Stoppen" else "Opnemen",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        if (!micPermission.status.isGranted) {
            Text(
                "Microfoontoegang is nodig om op te nemen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        vm.lastBulletId?.let {
            Spacer(Modifier.height(12.dp))
            QuickLabelPanel(
                labels = labels,
                onApply = { selected ->
                    vm.setLabelsOnLast(selected)
                    vm.clearLastBullet()
                },
                onSkip = vm::clearLastBullet,
            )
        }
    }
}

@Composable
private fun ModelStatusRow(
    small: ModelManager.State,
    large: ModelManager.State,
    largeEnabled: Boolean,
    listening: Boolean,
    tier: ModelManager.Tier,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when {
                listening -> if (tier == ModelManager.Tier.LARGE) "Luistert (groot model)" else "Luistert (klein model)"
                else -> "Klaar om op te nemen"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        StateChip("Klein", small)
        if (largeEnabled) {
            Spacer(Modifier.width(6.dp))
            StateChip("Groot", large)
        }
    }
}

@Composable
private fun StateChip(label: String, state: ModelManager.State) {
    val text = when (state) {
        is ModelManager.State.Absent -> "$label –"
        is ModelManager.State.Downloading -> "$label ${state.percent}%"
        is ModelManager.State.Unpacking -> "$label uitpakken"
        is ModelManager.State.Loading -> "$label laden"
        is ModelManager.State.Ready -> "$label ✓"
        is ModelManager.State.Failed -> "$label ✗"
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickLabelPanel(
    labels: List<LabelEntity>,
    onApply: (List<String>) -> Unit,
    onSkip: () -> Unit,
) {
    val selected = remember { mutableStateListOf<String>() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Label toevoegen aan de zojuist gemaakte bullet", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            if (labels.isEmpty()) {
                Text(
                    "Nog geen labels — maak ze aan op het tabblad Labels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { label ->
                        val isSel = label.id in selected
                        LabelChip(
                            name = label.name,
                            colorHex = label.color,
                            selected = isSel,
                            onClick = { if (isSel) selected.remove(label.id) else selected.add(label.id) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text("Overslaan") }
                TextButton(onClick = { onApply(selected.toList()) }) { Text("Klaar") }
            }
        }
    }
}
