package app.tok.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tok.di.ServiceLocator
import app.tok.ui.labels.LabelsScreen
import app.tok.ui.overview.OverviewScreen
import app.tok.ui.pairing.PairingScreen
import app.tok.ui.record.RecordScreen
import app.tok.ui.settings.SettingsScreen

@Composable
fun TokRoot() {
    val isPaired by ServiceLocator.repository.isPaired.collectAsStateWithLifecycle(initialValue = null)
    when (isPaired) {
        null -> Box(Modifier.fillMaxSize())
        false -> PairingScreen()
        else -> MainScaffold()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    RECORD("Opnemen", Icons.Outlined.Mic),
    OVERVIEW("Overzicht", Icons.AutoMirrored.Outlined.List),
    LABELS("Labels", Icons.Outlined.Label),
    SETTINGS("Instellingen", Icons.Outlined.Settings),
}

@Composable
private fun MainScaffold() {
    var tab by rememberSaveable { mutableStateOf(Tab.RECORD) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                Tab.RECORD -> RecordScreen()
                Tab.OVERVIEW -> OverviewScreen()
                Tab.LABELS -> LabelsScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
