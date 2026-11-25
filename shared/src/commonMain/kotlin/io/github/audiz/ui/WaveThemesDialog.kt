@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.audiz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.audiz.SearchViewModel
import io.github.audiz.models.ThematicWavePreset
import io.github.audiz.models.WaveThemeCategory
import io.github.audiz.models.WaveThemeOption
import io.github.audiz.wave.WaveCatalogOrganizer
import io.github.audiz.wave.WaveGenreFamilyGroup
import io.github.audiz.wave.WaveManager

@Composable
internal fun WaveThemesDialog(viewModel: SearchViewModel, onDismiss: () -> Unit) {
    val recent = viewModel.recentThematicWaves.take(3)
    val recentSeeds = recent.map { it.seeds }.toSet()
    val personalStations = viewModel.personalWaveStations.filterNot { it.seeds in recentSeeds }
    val artistWaves = viewModel.suggestedWaveArtists
    val currentTrackCandidate = viewModel.currentWaveTrackCandidate
    val categories = viewModel.waveCategories.toList()
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var showAllGenres by remember { mutableStateOf(false) }
    var selectedGenreFamilyId by remember { mutableStateOf<String?>(null) }

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

    val trimmedQuery = searchQuery.trim()
    val isSearching = trimmedQuery.isNotEmpty()
    LaunchedEffect(trimmedQuery) {
        if (trimmedQuery.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val coreGenreSeeds = remember { WaveManager.ALL_STYLE_PRESETS.flatMap { it.seeds } }
    val recentKey = viewModel.recentThematicWaves.map { it.seeds }
    val personalKey = viewModel.personalWaveStations.toList()
    val genreAffinity = remember(
        recentKey,
        personalKey,
        viewModel.loadedTracks.size,
        viewModel.likedTrackIds.size,
        viewModel.wavePastTracks.size,
        currentTrackCandidate?.id
    ) {
        viewModel.collectGenreAffinityWeights()
    }

    val prioritizedGenres = remember(categories, genreAffinity) {
        val rawGenres = categories.firstOrNull { it.id == "genre" }?.items.orEmpty()
        WaveCatalogOrganizer.prioritizeGenres(rawGenres, genreAffinity, coreGenreSeeds)
    }
    val genreFamilies = remember(prioritizedGenres) {
        WaveCatalogOrganizer.groupGenresByFamily(prioritizedGenres)
    }
    LaunchedEffect(genreFamilies) {
        if (selectedGenreFamilyId != null && genreFamilies.none { it.id == selectedGenreFamilyId }) {
            selectedGenreFamilyId = null
        }
    }

    val visibleRecent = remember(recent, trimmedQuery) {
        if (!isSearching) recent else recent.filter { WaveCatalogOrganizer.matchesQuery(it, trimmedQuery) }
    }
    val visiblePersonalStations = remember(personalStations, trimmedQuery) {
        if (!isSearching) personalStations else filterAndRankOptions(personalStations, trimmedQuery)
    }
    val visibleArtistWaves = remember(artistWaves, trimmedQuery) {
        if (!isSearching) artistWaves else filterAndRankOptions(artistWaves, trimmedQuery)
    }
    val showPersonalRow = !isSearching || WaveCatalogOrganizer.matchScore(
        title = "Персональная",
        subtitle = "Под ваш вкус",
        seeds = listOf("personal", "user:onyourwave"),
        query = trimmedQuery
    ) > 0
    val visibleCurrentTrack = currentTrackCandidate?.takeIf {
        !isSearching || WaveCatalogOrganizer.matchesQuery(it, trimmedQuery)
    }

    val visibleNonGenreByCatId = remember(categories, trimmedQuery) {
        categories.filter { it.id != "genre" }.associate { cat ->
            val items = if (!isSearching) cat.items else filterAndRankOptions(cat.items, trimmedQuery)
            cat.id to items
        }
    }
    val matchingGenres = remember(prioritizedGenres, trimmedQuery) {
        if (!isSearching) prioritizedGenres else filterAndRankOptions(prioritizedGenres, trimmedQuery)
    }

    val hasAnySearchResult = !isSearching ||
        showPersonalRow ||
        visibleCurrentTrack != null ||
        visibleRecent.isNotEmpty() ||
        visiblePersonalStations.isNotEmpty() ||
        visibleArtistWaves.isNotEmpty() ||
        visibleNonGenreByCatId.values.any { it.isNotEmpty() } ||
        matchingGenres.isNotEmpty()

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

                    WaveThemesSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" }
                    )
                    Spacer(Modifier.height(8.dp))

                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(end = 6.dp),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!hasAnySearchResult) {
                                item(key = "empty_search") {
                                    WaveEmptySearchState(
                                        query = trimmedQuery,
                                        onReset = { searchQuery = "" }
                                    )
                                }
                            } else {
                                if (showPersonalRow) {
                                    item(key = "personal") {
                                        WaveThemeItem(
                                            title = "Персональная",
                                            subtitle = "Под ваш вкус",
                                            theme = null,
                                            viewModel = viewModel,
                                            onDismiss = onDismiss
                                        )
                                    }
                                }

                                if (visibleCurrentTrack != null) {
                                    item(key = "current_track_wave") {
                                        WaveThemeItem(
                                            title = visibleCurrentTrack.title,
                                            subtitle = visibleCurrentTrack.subtitle,
                                            theme = ThematicWavePreset(
                                                visibleCurrentTrack.title,
                                                visibleCurrentTrack.seeds,
                                                visibleCurrentTrack.subtitle
                                            ),
                                            viewModel = viewModel,
                                            onDismiss = onDismiss
                                        )
                                    }
                                }

                                if (visibleRecent.isNotEmpty()) {
                                    item(key = "recent_header") { WaveThemesHeading("Недавно слушали") }
                                    items(visibleRecent, key = { "recent_${it.seeds.joinToString()}" }) { theme ->
                                        WaveThemeItem(title = theme.title, theme = theme, viewModel = viewModel, onDismiss = onDismiss)
                                    }
                                }

                                if (visiblePersonalStations.isNotEmpty()) {
                                    item(key = "dashboard_header") { WaveThemesHeading("Подобрано для вас") }
                                    item(key = "dashboard_chips") {
                                        WaveOptionFlowRow(
                                            options = visiblePersonalStations,
                                            icon = Icons.Filled.AutoAwesome,
                                            isWaveLoading = viewModel.isWaveLoading,
                                            isSelected = { viewModel.isWaveMode && viewModel.currentWaveSeeds == it.seeds },
                                            onOptionClick = { station ->
                                                viewModel.startThematicWave(station.title, station.seeds)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }

                                if (visibleArtistWaves.isNotEmpty()) {
                                    item(key = "artists_header") { WaveThemesHeading("По вашим артистам") }
                                    item(key = "artists_chips") {
                                        WaveOptionFlowRow(
                                            options = visibleArtistWaves,
                                            icon = Icons.Filled.Person,
                                            isWaveLoading = viewModel.isWaveLoading,
                                            isSelected = { viewModel.isWaveMode && viewModel.currentWaveSeeds == it.seeds },
                                            onOptionClick = { artistWave ->
                                                viewModel.startThematicWave(artistWave.title, artistWave.seeds)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }

                                categories.forEach { category ->
                                    if (category.id == "genre") {
                                        waveGenreSection(
                                            category = category,
                                            prioritizedGenres = prioritizedGenres,
                                            matchingGenres = matchingGenres,
                                            genreFamilies = genreFamilies,
                                            selectedGenre = selectedByCategory["genre"],
                                            isSearching = isSearching,
                                            showAllGenres = showAllGenres,
                                            selectedFamilyId = selectedGenreFamilyId,
                                            isWaveLoading = viewModel.isWaveLoading,
                                            onToggleOption = { option ->
                                                toggleCategoryOption(selectedByCategory, category.id, option)
                                            },
                                            onSetShowAll = { showAllGenres = it },
                                            onSelectFamily = { selectedGenreFamilyId = it }
                                        )
                                    } else {
                                        val items = visibleNonGenreByCatId[category.id].orEmpty()
                                        if (items.isNotEmpty()) {
                                            item(key = "cat_header_${category.id}") {
                                                WaveThemesHeading(category.title)
                                            }
                                            item(key = "cat_chips_${category.id}") {
                                                val activeInCategory = selectedByCategory[category.id]
                                                WaveOptionFlowRow(
                                                    options = items,
                                                    isWaveLoading = viewModel.isWaveLoading,
                                                    isSelected = { activeInCategory?.id == it.id },
                                                    onOptionClick = { option ->
                                                        toggleCategoryOption(selectedByCategory, category.id, option)
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

private fun filterAndRankOptions(options: List<WaveThemeOption>, query: String): List<WaveThemeOption> {
    if (query.isBlank()) return options
    return options.withIndex()
        .mapNotNull { indexed ->
            val score = WaveCatalogOrganizer.matchScore(indexed.value, query)
            if (score > 0) Triple(score, indexed.index, indexed.value) else null
        }
        .sortedWith(compareByDescending<Triple<Int, Int, WaveThemeOption>> { it.first }.thenBy { it.second })
        .map { it.third }
}

private fun toggleCategoryOption(
    selectedByCategory: MutableMap<String, WaveThemeOption>,
    categoryId: String,
    option: WaveThemeOption
) {
    if (selectedByCategory[categoryId]?.id == option.id) {
        selectedByCategory.remove(categoryId)
    } else {
        selectedByCategory[categoryId] = option
    }
}

private fun LazyListScope.waveGenreSection(
    category: WaveThemeCategory,
    prioritizedGenres: List<WaveThemeOption>,
    matchingGenres: List<WaveThemeOption>,
    genreFamilies: List<WaveGenreFamilyGroup>,
    selectedGenre: WaveThemeOption?,
    isSearching: Boolean,
    showAllGenres: Boolean,
    selectedFamilyId: String?,
    isWaveLoading: Boolean,
    onToggleOption: (WaveThemeOption) -> Unit,
    onSetShowAll: (Boolean) -> Unit,
    onSelectFamily: (String?) -> Unit
) {
    if (isSearching) {
        if (matchingGenres.isEmpty()) return
        item(key = "cat_header_genre_search") {
            WaveThemesHeading("${category.title} (${matchingGenres.size})")
        }
        item(key = "cat_chips_genre_search") {
            WaveOptionFlowRow(
                options = matchingGenres,
                isWaveLoading = isWaveLoading,
                isSelected = { selectedGenre?.id == it.id },
                onOptionClick = onToggleOption
            )
        }
        return
    }

    if (prioritizedGenres.isEmpty()) return

    if (!showAllGenres) {
        val collapsed = WaveCatalogOrganizer.buildCollapsedGenres(
            prioritizedItems = prioritizedGenres,
            selectedOption = selectedGenre,
            limit = WaveCatalogOrganizer.DEFAULT_COLLAPSED_GENRE_LIMIT
        )
        val hiddenCount = (prioritizedGenres.size - collapsed.size).coerceAtLeast(0)
        item(key = "cat_header_genre") {
            WaveThemesHeading(category.title)
        }
        item(key = "cat_chips_genre_collapsed") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                collapsed.forEach { option ->
                    WaveQuickActionChip(
                        title = option.title,
                        selected = selectedGenre?.id == option.id,
                        enabled = !isWaveLoading,
                        onClick = { onToggleOption(option) }
                    )
                }
                if (hiddenCount > 0) {
                    WaveQuickActionChip(
                        title = "Все жанры (+$hiddenCount)",
                        selected = false,
                        enabled = true,
                        icon = Icons.Filled.KeyboardArrowDown,
                        emphasize = true,
                        onClick = { onSetShowAll(true) }
                    )
                }
            }
        }
    } else {
        item(key = "cat_header_genre_expanded") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${category.title} (${prioritizedGenres.size})",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    onClick = {
                        onSetShowAll(false)
                        onSelectFamily(null)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            "Свернуть",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (genreFamilies.size > 1) {
            item(key = "genre_family_tabs") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WaveQuickActionChip(
                        title = "Все",
                        selected = selectedFamilyId == null,
                        enabled = true,
                        onClick = { onSelectFamily(null) }
                    )
                    genreFamilies.forEach { family ->
                        WaveQuickActionChip(
                            title = family.title,
                            selected = selectedFamilyId == family.id,
                            enabled = true,
                            onClick = {
                                onSelectFamily(if (selectedFamilyId == family.id) null else family.id)
                            }
                        )
                    }
                }
            }
        }

        val visibleFamilies = if (selectedFamilyId == null) {
            genreFamilies
        } else {
            genreFamilies.filter { it.id == selectedFamilyId }
        }
        visibleFamilies.forEach { family ->
            if (selectedFamilyId == null && genreFamilies.size > 1) {
                item(key = "genre_family_hdr_${family.id}") {
                    Text(
                        text = "${family.title} (${family.items.size})",
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item(key = "genre_family_items_${family.id}") {
                WaveOptionFlowRow(
                    options = family.items,
                    isWaveLoading = isWaveLoading,
                    isSelected = { selectedGenre?.id == it.id },
                    onOptionClick = onToggleOption
                )
            }
        }
    }
}

@Composable
private fun WaveThemesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val borderColor = if (isFocused || query.isNotEmpty()) colors.primary.copy(alpha = 0.7f) else colors.outlineVariant.copy(alpha = 0.55f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = "Поиск жанра или темы",
            tint = if (isFocused || query.isNotEmpty()) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    "Поиск жанра, темы или артиста…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && query.isNotEmpty()) {
                            onClear()
                            true
                        } else {
                            false
                        }
                    }
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Очистить поиск", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun WaveEmptySearchState(query: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(28.dp)
        )
        Text(
            "Ничего не найдено по запросу «$query»",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "Попробуйте жанр на русском или английском (например: рок, lofi, драм)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(
            onClick = onReset,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text("Сбросить поиск")
        }
    }
}

@Composable
private fun WaveOptionFlowRow(
    options: List<WaveThemeOption>,
    isWaveLoading: Boolean,
    isSelected: (WaveThemeOption) -> Boolean,
    icon: ImageVector? = null,
    onOptionClick: (WaveThemeOption) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            WaveQuickActionChip(
                title = option.title,
                selected = isSelected(option),
                enabled = !isWaveLoading,
                icon = icon,
                onClick = { onOptionClick(option) }
            )
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
    emphasize: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val bgColor = when {
        selected -> lerp(colors.surfaceContainerLow, colors.primary, 0.16f)
        emphasize -> lerp(colors.surfaceContainerLow, colors.primary, 0.08f)
        else -> colors.surfaceContainerLow
    }
    val borderColor = when {
        selected -> colors.primary.copy(alpha = 0.65f)
        emphasize -> colors.primary.copy(alpha = 0.4f)
        else -> colors.outlineVariant.copy(alpha = 0.45f)
    }
    val contentColor = when {
        selected || emphasize -> colors.primary
        else -> colors.onSurface
    }
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
                fontWeight = if (selected || emphasize) FontWeight.SemiBold else FontWeight.Normal,
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
