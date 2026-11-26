package io.github.audiz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.audiz.SearchViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpNextDialog(viewModel: SearchViewModel, onOpenPlaylist: () -> Unit, onDismiss: () -> Unit) {
    val state = viewModel.upNextState
    BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 540.dp
            val contentPadding = if (compact) 10.dp else 12.dp
            val narrowRows = maxWidth - contentPadding * 2 < 360.dp
            Card(Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = maxHeight * 0.9f)) {
                Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Далее · ${state.pending.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                            Icon(Icons.Default.Close, "Закрыть")
                        }
                    }
                    Text(
                        "Сейчас: ${viewModel.playerTrackTitle.ifBlank { "Ничего не играет" }}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall
                    )
                    Text("Треки очереди играют по порядку", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.pending.isEmpty()) {
                        Text("Очередь пуста. Выберите «В очередь» в меню трека.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        val listState = rememberLazyListState()
                        val visibleTrackIndices by listState.rememberVisiblePlaylistTrackIndices()
                        LazyColumn(Modifier.weight(1f, fill = false), state = listState) {
                            itemsIndexed(state.pending, key = { _, entry -> entry.key }) { index, entry ->
                                val loadThumbnail by remember(index, listState) {
                                    derivedStateOf { index in visibleTrackIndices }
                                }
                                PlaylistTrackRow(
                                    track = entry.track,
                                    artists = entry.track.artists.joinToString { it.name },
                                    loadThumbnail = loadThumbnail,
                                    isFirst = index == 0,
                                    isLast = index == state.pending.lastIndex,
                                    isActive = false,
                                    isPlaying = false,
                                    chartPosition = null,
                                    narrow = narrowRows,
                                    onActivate = null,
                                    onAddToPlaylist = null,
                                    onQueue = null,
                                    onRemove = null,
                                    extraMenuItems = { dismiss ->
                                        DropdownMenuItem(
                                            text = { Text("Переместить раньше", maxLines = 1) },
                                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                            enabled = index > 0,
                                            onClick = {
                                                dismiss()
                                                viewModel.moveUpNext(entry.key, -1)
                                            },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Переместить позже", maxLines = 1) },
                                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                            enabled = index < state.pending.lastIndex,
                                            onClick = {
                                                dismiss()
                                                viewModel.moveUpNext(entry.key, 1)
                                            },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Убрать из очереди", maxLines = 1) },
                                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                            onClick = {
                                                dismiss()
                                                viewModel.removeFromUpNext(entry.key)
                                            },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Text("Затем: ${viewModel.upNextContinuationTitle.ifBlank { "исходный плейлист" }}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = viewModel::clearUpNext, enabled = state.pending.isNotEmpty(),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) { Text("Очистить очередь") }
                        if (viewModel.hasPlaybackQueue) {
                            TextButton(onClick = onOpenPlaylist, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                                Text("Исходный плейлист")
                            }
                        }
                    }
                }
            }
        }
    }
}
