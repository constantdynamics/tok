package app.tok.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tok.data.repo.TokRepository
import app.tok.di.ServiceLocator
import app.tok.ui.theme.NeonGreen
import kotlinx.coroutines.launch

class PairingViewModel(private val repo: TokRepository) : ViewModel() {
    var code by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun onCodeChange(value: String) {
        code = value.filter { it.isDigit() }.take(6)
        error = null
    }

    fun pair() {
        if (code.length < 6 || busy) return
        busy = true
        error = null
        viewModelScope.launch {
            val result = repo.pair(code)
            busy = false
            result.onFailure { error = friendly(it) }
        }
    }

    private fun friendly(t: Throwable): String {
        val m = t.message.orEmpty()
        return when {
            m.contains("invalid_or_expired", true) -> "Ongeldige of verlopen code."
            m.contains("PGRST", true) || m.contains("HTTP 40", true) -> "Ongeldige of verlopen code."
            else -> m.ifBlank { "Koppelen mislukt." }
        }
    }
}

@Composable
fun PairingScreen() {
    val vm: PairingViewModel = viewModel { PairingViewModel(ServiceLocator.repository) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("tok", color = NeonGreen, fontSize = 64.sp, fontWeight = FontWeight.Black)
        Text(
            "Voer de koppelcode uit de webpagina of een ander apparaat in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = vm.code,
            onValueChange = vm::onCodeChange,
            singleLine = true,
            isError = vm.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                textAlign = TextAlign.Center,
                letterSpacing = 8.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (vm.error != null) {
            Text(
                vm.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = vm::pair,
            enabled = vm.code.length == 6 && !vm.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Koppelen")
            }
        }
    }
}
