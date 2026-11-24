@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.audiz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import io.github.audiz.isPlatformPickerSupported
import io.github.audiz.launchDirectoryPicker
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.audiz.YandexCookieService
import kotlin.math.roundToInt

internal enum class SettingsBlock {
    RecordToDisk,
    Equalizer,
    Crossfade,
    DownloadQuality,
    Theme,
    InterfaceMode,
    DownloadFolder,
    YandexAccount,
    WaveConnection,
    Storage,
    Support,
    Logs,
    AiFilter;

    @Composable
    fun Content(state: SettingsState, actions: SettingsActions) {
        when (this) {
            RecordToDisk -> RecordToDiskSettings(state, actions)
            Equalizer -> EqualizerSettings(actions)
            Crossfade -> CrossfadeSettings(state, actions)
            DownloadQuality -> DownloadQualitySettings(state, actions)
            Theme -> ThemeSettings(state, actions)
            InterfaceMode -> InterfaceModeSettings(state, actions)
            DownloadFolder -> DownloadFolderSettings(state, actions)
            YandexAccount -> YandexAccountSettings(state, actions)
            WaveConnection -> WaveConnectionSettings(state, actions)
            Storage -> StorageSettings(state, actions)
            Support -> SupportSettings(actions)
            Logs -> LogsSettings(state, actions)
            AiFilter -> AiFilterSettings(state, actions)
        }
    }
}

@Composable
private fun RecordToDiskSettings(state: SettingsState, actions: SettingsActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = "Сохранение треков на диск",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Автоматически сохранять воспроизводимые треки в постоянное хранилище",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = state.isRecordToDiskActive,
            onCheckedChange = { actions.onToggleRecordToDisk(it) },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        )
    }
}

@Composable
private fun EqualizerSettings(actions: SettingsActions) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Эквалайзер и срезы частот",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "10 полос, фильтры Low-Cut (HPF) и High-Cut (LPF)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { actions.onOpenEqualizer?.invoke(0) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Эквалайзер",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            OutlinedButton(
                onClick = { actions.onOpenEqualizer?.invoke(1) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Срезы",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun CrossfadeSettings(state: SettingsState, actions: SettingsActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Плавный переход между треками",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (state.crossfadeSeconds == 0) "Выкл (стык)" else "${state.crossfadeSeconds} сек",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            softWrap = false,
            maxLines = 1
        )
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = if (state.crossfadeSeconds == 0) {
            "Умный стык: отрезает тишину в конце трека и переключает мгновенно."
        } else {
            "Радио-сведение: плавное наложение следующего трека за ${state.crossfadeSeconds} сек до конца."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = state.crossfadeSeconds.toFloat(),
        onValueChange = { actions.onSaveCrossfade(it.roundToInt()) },
        valueRange = 0f..12f,
        steps = 11,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DownloadQualitySettings(state: SettingsState, actions: SettingsActions) {
    Text(
        text = "Качество скачивания",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.selectedQuality == "1",
                onClick = { actions.onSaveQuality("1") }
            )
            Text("Low", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.selectedQuality == "2",
                onClick = { actions.onSaveQuality("2") }
            )
            Text("High", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ThemeSettings(state: SettingsState, actions: SettingsActions) {
    Text(
        text = "Тема оформления",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.appTheme == "Light",
                onClick = { actions.onSaveTheme("Light") }
            )
            Text("Светлая", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.appTheme == "Dark",
                onClick = { actions.onSaveTheme("Dark") }
            )
            Text("Тёмная", style = MaterialTheme.typography.bodyMedium)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 🎨 Цветовой акцент (Основной цвет интерфейса)
    Text(
        text = "Цветовой акцент",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = "Основной цвет кнопок, слайдеров, индикаторов и активных элементов",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    val selectedAccent = AppAccentColor.fromKey(state.accentColor)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 420.dp) 6 else 4
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppAccentColor.entries.chunked(columns).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { accent ->
                        val isSelected = accent == selectedAccent
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { actions.onSaveAccentColor(accent.key) }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) accent.previewColor else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .background(accent.previewColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.Black.copy(alpha = 0.75f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = accent.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun InterfaceModeSettings(state: SettingsState, actions: SettingsActions) {
    Text(
        text = "Вид интерфейса",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { actions.onSaveUiMode("mobile") }
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            RadioButton(
                selected = state.uiMode != "desktop",
                onClick = { actions.onSaveUiMode("mobile") }
            )
            Text("Мобильный", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { actions.onSaveUiMode("desktop") }
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            RadioButton(
                selected = state.uiMode == "desktop",
                onClick = { actions.onSaveUiMode("desktop") }
            )
            Text("ПК", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DownloadFolderSettings(state: SettingsState, actions: SettingsActions) {
    Text(
        text = "Папка загрузки",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(4.dp))

    var pathInput by remember(state.musicStoragePath) {
        mutableStateOf(state.musicStoragePath)
    }
    var pathSaved by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = pathInput,
        onValueChange = { pathInput = it; pathSaved = false },
        label = { Text("Путь") },
        singleLine = true,
        trailingIcon = if (isPlatformPickerSupported) {
            {
                IconButton(
                    onClick = {
                        launchDirectoryPicker { picked ->
                            if (!picked.isNullOrBlank()) {
                                pathInput = picked
                                actions.onSaveMusicPath(picked)
                                pathSaved = true
                            }
                        }
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "Выбрать папку")
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isPlatformPickerSupported) {
            OutlinedButton(
                onClick = {
                    launchDirectoryPicker { picked ->
                        if (!picked.isNullOrBlank()) {
                            pathInput = picked
                            actions.onSaveMusicPath(picked)
                            pathSaved = true
                        }
                    }
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Обзор...", maxLines = 1, softWrap = false)
            }
        }
        Button(
            onClick = {
                actions.onSaveMusicPath(pathInput)
                pathSaved = true
            },
            enabled = pathInput.isNotBlank() && pathInput != state.musicStoragePath,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Сохранить путь", maxLines = 1, softWrap = false)
        }
        if (pathSaved) {
            Text(
                text = "✅ Сохранено!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun YandexAccountSettings(state: SettingsState, actions: SettingsActions) {
    val uriHandler = LocalUriHandler.current

    Text(
        text = "Авторизация в Яндекс Музыке (OAuth)",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "1. Нажмите кнопку, чтобы открыть страницу авторизации в Яндекс в браузере.\n2. После подтверждения доступа скопируйте ссылку из адресной строки (или access_token) и вставьте ниже:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))

    var tokenInput by remember(state.currentAccessToken) {
        mutableStateOf(state.currentAccessToken)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { uriHandler.openUri(YandexCookieService.OAUTH_AUTH_URL) },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text("🌐 1. Получить токен в браузере")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = tokenInput,
        onValueChange = {
            tokenInput = it
            actions.onClearAuthStatus()
        },
        label = { Text("OAuth access_token или ссылка из адресной строки") },
        placeholder = { Text("https://music.yandex.ru/#access_token=AQAAAA... или AQAAAA...") },
        singleLine = false,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(6.dp))

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = {
                actions.onSaveToken(tokenInput) { success ->
                    if (success) {
                        tokenInput = state.currentAccessToken
                    }
                }
            },
            enabled = tokenInput.isNotBlank(),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text("💾 2. Сохранить токен", maxLines = 1, softWrap = false)
        }

        if (state.authStatusMessage != null) {
            Text(
                text = state.authStatusMessage,
                color = if (state.authStatusMessage.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    // 👑 Статус подписки Яндекс Плюс
    if (state.currentAccessToken.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        val badgeColor = when (state.hasPlusSubscription) {
            true -> Color(0xFF4CAF50)
            false -> Color(0xFFFFA000)
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val badgeIcon = when (state.hasPlusSubscription) {
            true -> "👑"
            false -> "⚠️"
            null -> "⏳"
        }
        val badgeTitle = when (state.hasPlusSubscription) {
            true -> "Подписка Яндекс Плюс активна"
            false -> "Подписка Яндекс Плюс не активна"
            null -> "Проверка подписки Яндекс Плюс..."
        }
        val badgeSubtitle = when (state.hasPlusSubscription) {
            true -> "Доступны персональные рекомендации и все стили Волны"
            false -> "Базовый режим: доступны чарты и стандартная Моя Волна"
            null -> "Запрашиваем статус аккаунта в Яндекс Музыке..."
        }

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = badgeColor.copy(alpha = 0.12f)
            ),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = badgeIcon,
                    style = MaterialTheme.typography.titleMedium
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = badgeTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                    Text(
                        text = badgeSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { actions.onRefreshPlusSubscription() },
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Обновить статус подписки",
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveConnectionSettings(state: SettingsState, actions: SettingsActions) {
    Text(
        text = "🌊 Ynison WebSocket (Моя Волна)",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Служба реального времени для синхронизации очереди и управления устройствами.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalButton(
            onClick = { actions.onFetchYnisonSession() },
            enabled = !state.isFetchingYnison && state.currentAccessToken.isNotBlank(),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text(if (state.isFetchingYnison) "⏳ Подключение..." else "🔄 Запросить Ynison Session")
        }

        if (state.ynisonWaveSessionId != null) {
            SelectionContainer(modifier = Modifier.align(Alignment.CenterVertically)) {
                Text(
                    text = "ID: ${state.ynisonWaveSessionId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // ⚠️ Ошибка подключения к Ynison хосту
    if (!state.ynisonError.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("⚠️", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "Сбой соединения с сервером Ynison",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = state.ynisonError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🛡️ Активирован резервный режим Rotor (Моя Волна продолжает играть без сбоев)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else if (state.ynisonWaveSessionId != null && !state.ynisonHost.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Подключено к хосту: ${state.ynisonHost}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StorageSettings(state: SettingsState, actions: SettingsActions) {
    Text(
        text = "Управление хранилищем и кешем",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(6.dp))

    var showDeleteAllConfirmation by remember { mutableStateOf(false) }

    FlowRow(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = { actions.onClearPlaylistsCache() },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Очистить кеш плейлистов")
        }

        OutlinedButton(
            onClick = { showDeleteAllConfirmation = true },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Удалить все скачанные треки")
        }
    }

    if (state.storageStatusMessage != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = state.storageStatusMessage,
            color = if (state.storageStatusMessage.startsWith("✅")) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = { Text("Удаление всей музыки") },
            text = {
                Text("Вы действительно хотите удалить все скачанные аудиофайлы из папки хранения музыки? Это действие нельзя отменить.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirmation = false
                        actions.onClearAllDownloadedMusic()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Удалить всё")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllConfirmation = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun SupportSettings(actions: SettingsActions) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Поддержать проект",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Звезда на GitHub помогает развитию YamSync",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        var copiedLink by remember { mutableStateOf(false) }
        var projectOpenFailed by remember { mutableStateOf(false) }
        if (projectOpenFailed) {
            Text("Не удалось открыть браузер. Попробуйте ещё раз.", style = MaterialTheme.typography.bodySmall)
        }

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            uriHandler.openUri(io.github.audiz.support.PROJECT_GITHUB_URL)
                            projectOpenFailed = false
                            actions.onProjectOpened()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            projectOpenFailed = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(40.dp).pointerHoverIcon(PointerIcon.Hand),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.StarBorder,
                        contentDescription = null,
                        tint = Color(0xFFFF922B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Поставить звезду",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }

                OutlinedIconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(io.github.audiz.support.PROJECT_GITHUB_URL))
                        copiedLink = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = if (copiedLink) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Скопировать ссылку",
                        tint = if (copiedLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsSettings(state: SettingsState, actions: SettingsActions) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Отладка и логи",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Просмотр и копирование журнала работы приложения",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { actions.onOpenLogs?.invoke() },
            modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Открыть журнал логов", maxLines = 1)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Детектор лагов UI (Watchdog)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Записывать в лог стек вызовов потоков при зависаниях интерфейса (>100мс)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.isUiWatchdogActive,
                onCheckedChange = actions.onToggleUiWatchdog,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
