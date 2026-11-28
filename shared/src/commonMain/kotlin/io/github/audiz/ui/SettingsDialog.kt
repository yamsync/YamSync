package io.github.audiz.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import io.github.audiz.addons.AiFilterMode
import io.github.audiz.player.RecentPlaybackHistory
import androidx.compose.ui.unit.dp

data class SettingsState(
    val isRecordToDiskActive: Boolean = false,
    val selectedQuality: String = "2",
    val appTheme: String = "Dark",
    val accentColor: String = "purple",
    val musicStoragePath: String = "",
    val currentAccessToken: String = "",
    val authStatusMessage: String? = null,
    val isFetchingYnison: Boolean = false,
    val ynisonWaveSessionId: String? = null,
    val ynisonHost: String? = null,
    val ynisonError: String? = null,
    val storageStatusMessage: String? = null,
    val uiMode: String = "mobile",
    val crossfadeSeconds: Int = 3,
    val isUiWatchdogActive: Boolean = false,
    val hasPlusSubscription: Boolean? = null,
    val aiFilterMode: AiFilterMode = AiFilterMode.LABELS,
    val aiBlacklistVersion: Int? = null,
    val waveLoopProtectionEnabled: Boolean = false,
    val waveLoopHistoryLimit: Int = RecentPlaybackHistory.MAX_TRACKS,
)

data class SettingsActions(
    val onClose: () -> Unit = {},
    val onToggleRecordToDisk: (Boolean) -> Unit = {},
    val onSaveQuality: (String) -> Unit = {},
    val onSaveTheme: (String) -> Unit = {},
    val onSaveAccentColor: (String) -> Unit = {},
    val onSaveMusicPath: (String) -> Unit = {},
    val onSaveToken: (token: String, onComplete: (Boolean) -> Unit) -> Unit = { _, _ -> },
    val onClearAuthStatus: () -> Unit = {},
    val onFetchYnisonSession: () -> Unit = {},
    val onClearPlaylistsCache: () -> Unit = {},
    val onClearAllDownloadedMusic: () -> Unit = {},
    val onOpenEqualizer: ((initialTab: Int) -> Unit)? = null,
    val onOpenLogs: (() -> Unit)? = null,
    val onSaveUiMode: (String) -> Unit = {},
    val onSaveCrossfade: (Int) -> Unit = {},
    val onToggleUiWatchdog: (Boolean) -> Unit = {},
    val onRefreshPlusSubscription: () -> Unit = {},
    val onProjectOpened: () -> Unit = {},
    val onSaveAiFilterMode: (AiFilterMode) -> Unit = {},
    val onToggleWaveLoopProtection: (Boolean) -> Unit = {},
    val onSaveWaveLoopHistoryLimit: (Int) -> Unit = {},
)

@Composable
fun SettingsDialog(
    visible: Boolean,
    state: SettingsState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedSection by remember { mutableStateOf<SettingsSection?>(null) }
    val currentSection = selectedSection
    var selectedAddon by remember { mutableStateOf<SettingsAddon?>(null) }
    val currentAddon = selectedAddon.takeIf { currentSection == SettingsSection.Addons }

    fun clearFocus() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun goBack() {
        clearFocus()
        when {
            currentAddon != null -> selectedAddon = null
            currentSection != null -> selectedSection = null
            else -> actions.onClose()
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            clearFocus()
        } else {
            selectedSection = null
            selectedAddon = null
        }
    }
    BackHandler(enabled = visible, onBack = ::goBack)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        val settingsScrollState = key(currentSection, currentAddon) { rememberScrollState() }

        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                val horizontalPadding = if (maxWidth < 450.dp) 12.dp else 16.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (currentSection != null) {
                            TopBarIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (currentAddon != null) "К дополнениям" else "К разделам настроек",
                                size = 40.dp,
                                onClick = ::goBack,
                            )
                        }
                        Text(
                            text = currentAddon?.title ?: currentSection?.title ?: "Настройки",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TopBarIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Закрыть настройки",
                            size = 40.dp,
                            outlined = true,
                            onClick = {
                                clearFocus()
                                actions.onClose()
                            },
                        )
                    }
                    HorizontalDivider()

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .verticalScroll(settingsScrollState)
                                .padding(start = horizontalPadding, end = horizontalPadding + 8.dp)
                                .padding(vertical = if (currentSection == null) 0.dp else 12.dp)
                        ) {
                            if (currentSection == null) {
                                SettingsSectionMenu(state, onOpen = { selectedSection = it })
                            } else if (currentSection == SettingsSection.Addons && currentAddon == null) {
                                SettingsAddon.entries.forEach { addon ->
                                    SettingsMenuRow(addon.title, addon.icon, addon.summary(state)) {
                                        selectedAddon = addon
                                    }
                                }
                            } else {
                                val blocks = currentAddon?.let { listOf(it.block) } ?: currentSection.blocks
                                blocks.forEachIndexed { index, block ->
                                    if (index > 0) {
                                        Spacer(Modifier.height(12.dp))
                                        HorizontalDivider()
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    block.Content(state, actions)
                                }
                            }
                        }

                        PlatformScrollbar(
                            state = settingsScrollState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private enum class SettingsAddon(val title: String, val icon: ImageVector, val block: SettingsBlock) {
    AiFilter("Фильтр ИИ-музыки", Icons.Filled.GraphicEq, SettingsBlock.AiFilter);

    fun summary(state: SettingsState): String = when (this) {
        AiFilter -> state.aiFilterMode.title
    }
}

private enum class SettingsSection(
    val title: String,
    val icon: ImageVector,
    val blocks: List<SettingsBlock>
) {
    Sound("Звук", Icons.Filled.GraphicEq, listOf(SettingsBlock.Crossfade, SettingsBlock.Equalizer)),
    Downloads(
        "Загрузки", Icons.Filled.Download,
        listOf(SettingsBlock.DownloadQuality, SettingsBlock.RecordToDisk, SettingsBlock.DownloadFolder, SettingsBlock.Storage)
    ),
    Appearance("Оформление", Icons.Filled.Palette, listOf(SettingsBlock.Theme, SettingsBlock.InterfaceMode)),
    Account("Аккаунт Яндекса", Icons.Filled.PersonOutline, listOf(SettingsBlock.YandexAccount)),
    MyWave("Моя волна", Icons.Filled.Waves, listOf(SettingsBlock.WaveLoopProtection)),
    Addons("Дополнения", Icons.Filled.Extension, emptyList()),
    Diagnostics("Диагностика", Icons.Filled.MonitorHeart, listOf(SettingsBlock.Logs, SettingsBlock.WaveConnection)),
    Support("Поддержать проект", Icons.Filled.FavoriteBorder, listOf(SettingsBlock.Support));

    fun summary(state: SettingsState): String? = when (this) {
        Sound -> if (state.crossfadeSeconds == 0) "Переход выключен" else "Переход: ${state.crossfadeSeconds} сек"
        Downloads -> if (state.selectedQuality == "1") "Низкое качество" else "Высокое качество"
        Appearance -> {
            val theme = when (state.appTheme) {
                "Light" -> "Светлая"
                "System" -> "Системная"
                else -> "Тёмная"
            }
            "$theme · ${AppAccentColor.fromKey(state.accentColor).title}"
        }
        MyWave -> if (state.waveLoopProtectionEnabled) "Защита от зацикливания включена" else null
        Addons -> "Фильтр ИИ-музыки · ${state.aiFilterMode.title}"
        Account, Diagnostics, Support -> null
    }
}

@Composable
private fun SettingsSectionMenu(state: SettingsState, onOpen: (SettingsSection) -> Unit) {
    SettingsSection.entries.forEach { section ->
        SettingsMenuRow(section.title, section.icon, section.summary(state)) { onOpen(section) }
    }
}

@Composable
private fun SettingsMenuRow(title: String, icon: ImageVector, summary: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            summary?.let {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
