package io.github.audiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.audiz.addons.Slopless
import io.github.audiz.addons.SloplessEntity
import io.github.audiz.addons.SloplessMode
import kotlin.math.roundToInt

internal data class SloplessUi(val service: Slopless, val mode: SloplessMode, val artistThreshold: Int)

internal val LocalSlopless = compositionLocalOf<SloplessUi?> { null }

@Composable
internal fun SloplessBadge(entity: SloplessEntity, id: String?, modifier: Modifier = Modifier) {
    val slopless = LocalSlopless.current ?: return
    if (id == null || slopless.mode == SloplessMode.OFF) return

    LaunchedEffect(slopless.service, id, entity, slopless.mode) {
        slopless.service.request(entity, id)
    }
    val score = slopless.service.score(entity, id) ?: return
    val marked = when (entity) {
        SloplessEntity.TRACK -> score > 0.5
        SloplessEntity.ARTIST -> score >= slopless.artistThreshold / 100.0
    }
    if (marked) {
        Surface(
            modifier = modifier.semantics {
                contentDescription = "Возможная ИИ-музыка по данным Slopless"
            },
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Text("ИИ", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun SloplessSettings(state: SettingsState, actions: SettingsActions) {
    var threshold by remember(state.sloplessArtistThreshold) { mutableFloatStateOf(state.sloplessArtistThreshold.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Фильтр ИИ-музыки", style = MaterialTheme.typography.titleMedium)
        Text(
            "Slopless получает только ID треков и артистов из каталога. Если оценки нет, трек играет как обычно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.selectableGroup()) {
            SloplessMode.entries.forEach { mode ->
                val selected = state.sloplessMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected, role = Role.RadioButton) { actions.onSaveSloplessMode(mode) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(mode.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("Порог метки артиста · ${threshold.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = threshold,
            onValueChange = { threshold = it },
            onValueChangeFinished = { actions.onSaveSloplessArtistThreshold(threshold.roundToInt()) },
            valueRange = 1f..100f,
            enabled = state.sloplessMode != SloplessMode.OFF,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Порог задаёт долю ИИ-треков для метки артиста. В «Моей волне» пропуск зависит от оценки самого трека.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
