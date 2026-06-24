package app.tok.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TokColors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.Black,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    tertiary = NeonPink,
    onTertiary = Color.Black,
    background = TokBg,
    onBackground = TokOnSurface,
    surface = TokSurface,
    onSurface = TokOnSurface,
    surfaceVariant = TokSurfaceVariant,
    onSurfaceVariant = TokOnSurfaceDim,
    outline = TokOutline,
    error = NeonPink,
    onError = Color.Black,
)

@Composable
fun TokTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TokColors,
        typography = Typography(),
        content = content,
    )
}
