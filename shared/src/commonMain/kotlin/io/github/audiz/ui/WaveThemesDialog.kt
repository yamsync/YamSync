@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.audiz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.audiz.SearchViewModel
import io.github.audiz.models.ThematicWavePreset
import io.github.audiz.models.WaveThemeOption

@Composable
internal fun WaveThemesDialog(viewModel: SearchViewModel, onDismiss: () -> Unit) {
    val recent = viewModel.recentThematicWaves.take(3)
    val recentSeeds = recent.map { it.seeds }.toSet()
    val personalStations = viewModel.personalWaveStations.filterNot { it.seeds in recentSeeds }
    val artistWaves = viewModel.suggestedWaveArtists
    val currentTrackCandidate = viewModel.currentWaveTrackCandidate
    val categories = viewModel.waveCategories
    val listState = rememberLazyListState()

    val selectedByCategory = remember { mutableStateMapOf<String, WaveThemeOption>() }
    LaunchedEffect(Unit) {
        if (viewModel.personalWaveStations.isEmpty() && viewModel.currentAccessToken.isNotBlank()) {
            viewModel.loadWaveCatalog()
        }
    }
    LaunchedEffect(viewModel.currentWaveSeeds, categories) {
        if (selectedByCategory.isEmpty() && viewModel.currentWaveSeeds.isNotEmpty()) {
            val activeSeedSet = viewModel.currentWaveSeeds.toSet()
            for (cat in categories) {
                val match = cat.items.firstOrNull { opt -> opt.seeds.all { it in activeSeedSet } }
                if (match != null) selectedByCategory[cat.id] = match
            }
        }
    }

    val selectedOptions = remember(categories, selectedByCategory.toMap()) {
        categories.mapNotNull { selectedByCategory[it.id] }
    }
    val combinedSeeds = remember(selectedOptions) {
        selectedOptions.flatMap { it.seeds }.distinct()
    }
    val combinedTitle = remember(selectedOptions) {
        selectedOptions.joinToString(" • ") { it.title }
    }

    BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 540.dp
            Card(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 700.dp).fillMaxHeight(0.92f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(if (compact) 10.dp else 14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Настройка волны",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (viewModel.isWaveCatalogLoading) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.loadWaveCatalog(forceRefresh = true) },
                                modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(Icons.Filled.Refresh, "Обновить подборки волны", modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)) {
                            Icon(Icons.Filled.Close, "Закрыть")
                        }
                    }
                    Text(
                        "Запустите готовый поток или соберите свой из фильтров",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))

                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(end = 6.dp),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item(key = "personal") {
                                WaveThemeItem(
                                    title = "Персональная",
                                    subtitle = "Под ваш вкус",
                                    theme = null,
                                    viewModel = viewModel,
                                    onDismiss = onDismiss
                                )
                            }

                            if (currentTrackCandidate != null) {
                                item(key = "current_track_wave") {
                                    WaveThemeItem(
                                        title = currentTrackCandidate.title,
                                        subtitle = currentTrackCandidate.subtitle,
                                        theme = ThematicWavePreset(
                                            currentTrackCandidate.title,
                                            currentTrackCandidate.seeds,
                                            currentTrackCandidate.subtitle
                                        ),
                                        viewModel = viewModel,
                                        onDismiss = onDismiss
                                    )
                                }
                            }

                            if (recent.isNotEmpty()) {
                                item(key = "recent_header") { WaveThemesHeading("Недавно слушали") }
                                items(recent, key = { "recent_${it.seeds.joinToString()}" }) { theme ->
                                    WaveThemeItem(title = theme.title, theme = theme, viewModel = viewModel, onDismiss = onDismiss)
                                }
                            }

                            if (personalStations.isNotEmpty()) {
                                item(key = "dashboard_header") { WaveThemesHeading("Подобрано для вас") }
                                item(key = "dashboard_chips") {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        personalStations.forEach { station ->
                                            val isSelected = viewModel.isWaveMode && viewModel.currentWaveSeeds == station.seeds
                                            WaveQuickActionChip(
                                                title = station.title,
                                                selected = isSelected,
                                                enabled = !viewModel.isWaveLoading,
                                                icon = Icons.Filled.AutoAwesome,
                                                onClick = {
                                                    viewModel.startThematicWave(station.title, station.seeds)
                                                    onDismiss()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (artistWaves.isNotEmpty()) {
                                item(key = "artists_header") { WaveThemesHeading("По вашим артистам") }
                                item(key = "artists_chips") {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        artistWaves.forEach { artistWave ->
                                            val isSelected = viewModel.isWaveMode && viewModel.currentWaveSeeds == artistWave.seeds
                                            WaveQuickActionChip(
                                                title = artistWave.title,
                                                selected = isSelected,
                                                enabled = !viewModel.isWaveLoading,
                                                icon = Icons.Filled.Person,
                                                onClick = {
                                                    viewModel.startThematicWave(artistWave.title, artistWave.seeds)
                                                    onDismiss()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            categories.forEach { category ->
                                if (category.items.isNotEmpty()) {
                                    item(key = "cat_header_${category.id}") {
                                        WaveThemesHeading(category.title)
                                    }
                                    item(key = "cat_chips_${category.id}") {
                                        val activeInCategory = selectedByCategory[category.id]
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            category.items.forEach { option ->
                                                val isChosen = activeInCategory?.id == option.id
                                                WaveQuickActionChip(
                                                    title = option.title,
                                                    selected = isChosen,
                                                    enabled = !viewModel.isWaveLoading,
                                                    onClick = {
                                                        if (isChosen) selectedByCategory.remove(category.id)
                                                        else selectedByCategory[category.id] = option
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        PlatformScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }

                    AnimatedVisibility(visible = combinedSeeds.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Выбранная комбинация",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = combinedTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { selectedByCategory.clear() },
                                    modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Сбросить", modifier = Modifier.size(18.dp))
                                }
                                Button(
                                    onClick = {
                                        viewModel.startThematicWave(combinedTitle, combinedSeeds)
                                        onDismiss()
                                    },
                                    enabled = !viewModel.isWaveLoading,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Запустить", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveThemesHeading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun WaveQuickActionChip(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val bgColor = if (selected) lerp(colors.surfaceContainerLow, colors.primary, 0.16f) else colors.surfaceContainerLow
    val borderColor = if (selected) colors.primary.copy(alpha = 0.65f) else colors.outlineVariant.copy(alpha = 0.45f)
    val contentColor = if (selected) colors.primary else colors.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.heightIn(min = 36.dp).pointerHoverIcon(PointerIcon.Hand)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            }
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WaveThemeItem(
    title: String,
    subtitle: String? = null,
    theme: ThematicWavePreset?,
    viewModel: SearchViewModel,
    onDismiss: () -> Unit
) {
    val selected = viewModel.isWaveMode && viewModel.currentWaveSeeds == theme?.seeds.orEmpty()
    val accent = MaterialTheme.colorScheme.primary
    ListItem(
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !viewModel.isWaveLoading, role = Role.Button) {
                viewModel.selectWaveTheme(theme)
                onDismiss()
            }.pointerHoverIcon(PointerIcon.Hand),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) accent.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                Icons.Filled.Waves,
                null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                if (selected) Icons.Filled.Check else Icons.Filled.PlayArrow,
                null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}
