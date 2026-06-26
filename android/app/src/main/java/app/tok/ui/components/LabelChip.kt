package app.tok.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tok.ui.theme.NeonGreen

/** Hex (#RRGGBB) -> Compose Color; valt terug op neon-groen bij een fout. */
fun colorFromHex(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    NeonGreen
}

@Composable
fun LabelChip(
    name: String,
    colorHex: String,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = colorFromHex(colorHex)
    Surface(
        shape = RoundedCornerShape(50),
        color = c.copy(alpha = if (selected) 0.18f else 0.05f),
        border = BorderStroke(1.dp, c.copy(alpha = if (selected) 0.9f else 0.35f)),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
    ) {
        Text(
            text = name,
            color = if (selected) c else c.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
