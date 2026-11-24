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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.audiz.addons.AiBlacklist
import io.github.audiz.addons.AiFilterMode
import io.github.audiz.addons.AiMatch
import io.github.audiz.models.FullArtistInfo

internal val LocalAiBlacklist = compositionLocalOf<AiBlacklist?> { null }

@Composable
internal fun AiArtistBadge(artistId: String?, modifier: Modifier = Modifier) {
    val id = artistId?.toLongOrNull() ?: return
    if (LocalAiBlacklist.current?.containsArtist(id) == true) {
        AiBadge(AiMatch.ARTIST, modifier)
    }
}

@Composable
internal fun AiTrackBadge(trackId: String?, artists: List<FullArtistInfo>, modifier: Modifier = Modifier) {
    val match = LocalAiBlacklist.current?.matchTrack(trackId, artists) ?: return
    AiBadge(match, modifier)
}

@Composable
private fun AiBadge(match: AiMatch, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(5.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = when (match) {
                AiMatch.TRACK -> "ИИ-трек"
                AiMatch.ARTIST -> "ИИ-артист"
            },
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun AiFilterSettings(state: SettingsState, actions: SettingsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Фильтр ИИ-музыки", style = MaterialTheme.typography.titleMedium)
        Text(
            "Метки работают офлайн по встроенной базе. В «Моей волне» можно пропускать треки, которые входят в список ИИ-треков.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.selectableGroup()) {
            AiFilterMode.entries.forEach { mode ->
                val selected = state.aiFilterMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected, role = Role.RadioButton) { actions.onSaveAiFilterMode(mode) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(mode.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        state.aiBlacklistVersion?.let { version ->
            Text(
                "Встроенная база · v$version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
