package app.tok.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tok.data.prefs.TokPrefs
import app.tok.data.remote.DeviceDto
import app.tok.data.repo.TokRepository
import app.tok.di.ServiceLocator
import app.tok.speech.ModelManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: TokRepository,
    private val models: ModelManager,
    private val prefs: TokPrefs,
) : ViewModel() {

    val smallState = models.small
    val largeState = models.large
    val largeEnabled = prefs.largeModelEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val deviceName = prefs.deviceName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val syncing = repo.syncing
    val syncError = repo.syncError

    var generatedCode by mutableStateOf<String?>(null)
        private set
    var devices by mutableStateOf<List<DeviceDto>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun setDeviceName(name: String) = viewModelScope.launch { prefs.setDeviceName(name) }
    fun downloadSmall() = viewModelScope.launch { models.ensure(ModelManager.Tier.SMALL) }
    fun setLargeEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setLargeModelEnabled(enabled)
        if (enabled) models.ensure(ModelManager.Tier.LARGE)
    }

    fun importModel(tier: ModelManager.Tier, uri: android.net.Uri) =
        viewModelScope.launch { models.importFromZip(tier, uri) }

    fun generateCode() = viewModelScope.launch {
        repo.createPairingCode()
            .onSuccess { generatedCode = it; message = null }
            .onFailure { message = it.message }
    }

    fun loadDevices() = viewModelScope.launch {
        repo.listDevices().onSuccess { devices = it }.onFailure { message = it.message }
    }

    fun revoke(id: String) = viewModelScope.launch {
        repo.revokeDevice(id).onSuccess { loadDevices() }.onFailure { message = it.message }
    }

    fun sync() = viewModelScope.launch { repo.syncNow() }
    fun unpair() = viewModelScope.launch { repo.unpair() }
}

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel {
        SettingsViewModel(ServiceLocator.repository, ServiceLocator.modelManager, ServiceLocator.prefs)
    }
    val smallState by vm.smallState.collectAsStateWithLifecycle()
    val largeState by vm.largeState.collectAsStateWithLifecycle()
    val largeEnabled by vm.largeEnabled.collectAsStateWithLifecycle()
    val deviceName by vm.deviceName.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncError by vm.syncError.collectAsStateWithLifecycle()

    var pendingTier by remember { mutableStateOf<ModelManager.Tier?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tier = pendingTier
        if (uri != null && tier != null) vm.importModel(tier, uri)
        pendingTier = null
    }

    LaunchedEffect(Unit) { vm.loadDevices() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Spraakmodellen ---
        Section("Spraakmodellen") {
            Text("Klein model: ${stateText(smallState)}", style = MaterialTheme.typography.bodyMedium)
            Row {
                OutlinedButton(onClick = { vm.downloadSmall() }) { Text("Downloaden") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { pendingTier = ModelManager.Tier.SMALL; importLauncher.launch(arrayOf("application/zip", "*/*")) }) {
                    Text("Importeren")
                }
            }
            Divider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Groot model gebruiken", modifier = Modifier.weight(1f))
                Switch(checked = largeEnabled, onCheckedChange = { vm.setLargeEnabled(it) })
            }
            Text(
                "Nauwkeuriger, maar ~1,4 GB en zwaarder. Opname start altijd direct met het kleine model en schakelt bij een pauze over zodra het grote model geladen is.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (largeEnabled) {
                Text("Groot model: ${stateText(largeState)}", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { pendingTier = ModelManager.Tier.LARGE; importLauncher.launch(arrayOf("application/zip", "*/*")) }) {
                    Text("Importeren")
                }
            }
        }

        // --- Apparaten koppelen ---
        Section("Apparaten") {
            Text(
                "Genereer een code om een nieuw apparaat (of de webpagina) te koppelen. Geldig ~10 minuten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { vm.generateCode() }) { Text("Nieuwe koppelcode") }
            vm.generatedCode?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            Divider(Modifier.padding(vertical = 4.dp))
            Text("Gekoppelde apparaten", style = MaterialTheme.typography.titleSmall)
            if (vm.devices.isEmpty()) {
                Text("Nog niet geladen of geen apparaten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                vm.devices.forEach { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (d.deviceName ?: "Onbekend apparaat") + if (d.isOwner) " (eigenaar)" else "",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!d.isOwner) {
                            TextButton(onClick = { vm.revoke(d.id) }) { Text("Intrekken") }
                        }
                    }
                }
            }
            TextButton(onClick = { vm.loadDevices() }) { Text("Vernieuwen") }
        }

        // --- Sync ---
        Section("Synchronisatie") {
            Text(if (syncing) "Bezig met synchroniseren…" else "Up-to-date", style = MaterialTheme.typography.bodyMedium)
            syncError?.let { Text("Laatste fout: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { vm.sync() }) { Text("Nu synchroniseren") }
        }

        // --- Apparaatnaam ---
        Section("Dit apparaat") {
            var name by remember(deviceName) { mutableStateOf(deviceName) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Apparaatnaam") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.setDeviceName(name) }) { Text("Opslaan") }
        }

        // --- Ontkoppelen ---
        OutlinedButton(
            onClick = { vm.unpair() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Dit apparaat ontkoppelen")
        }

        vm.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private fun stateText(s: ModelManager.State): String = when (s) {
    is ModelManager.State.Absent -> "niet gedownload"
    is ModelManager.State.Downloading -> "downloaden ${s.percent}%"
    is ModelManager.State.Unpacking -> "uitpakken…"
    is ModelManager.State.Loading -> "laden…"
    is ModelManager.State.Ready -> "klaar ✓"
    is ModelManager.State.Failed -> "fout: ${s.message}"
}
