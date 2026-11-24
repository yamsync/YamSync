package io.github.audiz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.player.PlayerTrackRemoval
import io.github.audiz.player.TrackRemovalSelection
import kotlinx.coroutines.CancellationException

@Composable
internal fun PlayerTrackRemovalDialog(
    track: FullTrackInfo,
    loadOptions: suspend () -> PlayerTrackRemoval,
    onDismiss: () -> Unit,
    onConfirm: (PlayerTrackRemoval, TrackRemovalSelection) -> Unit
) {
    val result by produceState<Result<PlayerTrackRemoval>?>(null, track) {
        value = try {
            Result.success(loadOptions())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val target = result?.getOrNull()
    val hasPlaylists = target != null && target.playlistCount > 0
    val hasFile = target?.filePath != null
    val canChangeRecommendations = target?.recommendationTrackId != null
    var selection by remember(target) { mutableStateOf(target?.defaultSelection() ?: TrackRemovalSelection()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что сделать с треком?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = track.title.ifBlank { "Без названия" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                when {
                    result == null -> CircularProgressIndicator()
                    target == null -> Text("Не удалось определить доступные действия.")
                    else -> {
                        RemovalOption(
                            label = if (hasPlaylists) "Удалить из плейлистов (${target.playlistCount})" else "Удалить из плейлистов",
                            checked = selection.removeFromPlaylists,
                            enabled = hasPlaylists,
                            onCheckedChange = { selection = selection.copy(removeFromPlaylists = it) }
                        )
                        RemovalOption("Удалить с диска", selection.deleteFile, hasFile) {
                            selection = selection.copy(deleteFile = it)
                        }
                        RemovalOption(
                            label = if (target.isHiddenFromRecommendations) "Вернуть в рекомендации" else "Скрыть из рекомендаций",
                            checked = selection.changeRecommendations,
                            enabled = canChangeRecommendations,
                            onCheckedChange = { selection = selection.copy(changeRecommendations = it) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = target != null && selection.hasActions,
                onClick = {
                    if (target != null) onConfirm(target, selection)
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun RemovalOption(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
