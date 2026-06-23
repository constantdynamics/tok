package app.tok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import app.tok.ui.theme.TokTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Placeholder — wordt in de UI-fase vervangen door de navigatie
                    // (koppelen / opname / overzicht / labels / instellingen).
                    Text("tok")
                }
            }
        }
    }
}
