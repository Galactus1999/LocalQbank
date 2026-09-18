package com.localqbank.library.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Small Compose-first AI surface designed for incremental View/Compose coexistence. */
@Composable
fun BenAiComposeSurface(
    title: String,
    subtitle: String,
    profile: String,
    localEvidence: Int,
    neuralState: String,
    dark: Boolean,
    primary: Color,
    surface: Color,
    onSurface: Color,
    onOpenContext: () -> Unit = {},
    onOpenEvidence: () -> Unit = {}
) {
    val scheme = if (dark) {
        darkColorScheme(primary = primary, surface = surface, surfaceContainerHigh = surface, onSurface = onSurface)
    } else {
        lightColorScheme(primary = primary, surface = surface, surfaceContainerHigh = surface, onSurface = onSurface)
    }
    MaterialTheme(colorScheme = scheme) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onOpenContext, label = { Text(profile) })
                    AssistChip(onClick = onOpenEvidence, label = { Text("Evidence $localEvidence") })
                    AssistChip(onClick = {}, label = { Text(neuralState) })
                }
            }
        }
    }
}

fun composeAccentColor(argb: Int): Color = Color(argb)
