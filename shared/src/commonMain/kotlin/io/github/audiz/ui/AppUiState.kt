package io.github.audiz.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.audiz.AppConfigKeys
import io.github.audiz.loadAppConfig
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.saveAppConfig

/**
 * Модальное окно, открытое поверх главного экрана. Одновременно открыто не больше одного:
 * все они модальные и открываются только по действию пользователя вне других окон.
 *
 * Окно YamSync сюда не входит: оно открывается ещё и по входящей ссылке (QR-код),
 * то есть поверх любого уже открытого окна, поэтому его флаг хранится отдельно.
 */
sealed interface AppDialog {
    /** Очередь «Далее». [openedFromPlayer] — открыто из полноэкранного мобильного плеера, при закрытии возвращаемся в него. */
    data class UpNext(val openedFromPlayer: Boolean) : AppDialog

    data class Equalizer(val initialTab: Int = 0) : AppDialog

    /** Медиатека (плейлисты, папки). Выбранная вкладка хранится в [AppNavigationState.mediaLibraryTab]. */
    data object MediaLibrary : AppDialog

    data object Logs : AppDialog

    data class AddToPlaylist(val track: FullTrackInfo) : AppDialog

    data class PlayerTrackRemoval(val track: FullTrackInfo) : AppDialog

    data class DeleteTrack(
        val trackId: String,
        val title: String,
        val artist: String
    ) : AppDialog

    /** Конструктор тем и фильтров «Моей волны». */
    data object WaveThemes : AppDialog
}

@Stable
class AppNavigationState internal constructor(initialMobilePlayerExpanded: Boolean) {
    var showCollections by mutableStateOf(false)

    /** Полноэкранный оверлей настроек. Не модальное окно, поэтому живёт отдельно от [AppDialog]. */
    var settingsExpanded by mutableStateOf(false)

    /** Полноэкранный мобильный плеер. Меняется через [updateMobilePlayerExpanded], чтобы сохранялось между запусками. */
    var isMobilePlayerExpanded by mutableStateOf(initialMobilePlayerExpanded)
        private set

    /** Текущая позиция верхнего края мобильного плеера при перетаскивании (px), `null` — не перетаскивается. */
    var mobilePlayerDragTop by mutableStateOf<Float?>(null)

    var isDraggingMobilePlayerOpen by mutableStateOf(false)

    /** Выбранная вкладка медиатеки; переживает закрытие и повторное открытие окна. */
    var mediaLibraryTab by mutableIntStateOf(0)

    fun updateMobilePlayerExpanded(expanded: Boolean) {
        isMobilePlayerExpanded = expanded
        saveAppConfig(AppConfigKeys.MOBILE_PLAYER_EXPANDED, expanded.toString())
    }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState = remember {
    AppNavigationState(
        initialMobilePlayerExpanded = loadAppConfig(AppConfigKeys.MOBILE_PLAYER_EXPANDED) == "true"
    )
}
