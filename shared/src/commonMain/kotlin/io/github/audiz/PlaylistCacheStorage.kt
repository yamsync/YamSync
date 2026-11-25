package io.github.audiz

import io.github.audiz.models.EditorialShowcaseData
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.models.LocalPlaylist
import io.github.audiz.models.PersonalPlaylistItemData
import io.github.audiz.models.PlaylistInfo
import io.github.audiz.models.WavePersonalCatalog
import io.github.audiz.util.AppLogger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 🗂 Кроссплатформенное хранилище кешей и локальных плейлистов.
 *
 * Раскладка на диске:
 * - `{basePath}/playlists_cache/{cacheKey}.json` — треки плейлистов (живут [PLAYLIST_CACHE_MAX_AGE_MS]);
 * - `{basePath}/playlists_cache/_*.json` — служебные кеши (персональные, пользовательские плейлисты, витрина, каталог волны);
 * - `{basePath}/playlists/local_playlists.json` — локальные оффлайн-плейлисты.
 *
 * Платформенный код предоставляет только примитивы I/O (см. [writeTextFileAtomic] и др. в MusicStorage.kt).
 */

const val PLAYLIST_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000L // 7 дней

private const val TAG = "PlaylistCache"
private const val CACHE_DIR = "playlists_cache"
private const val LOCAL_PLAYLISTS_DIR = "playlists"
private const val LOCAL_PLAYLISTS_FILE = "local_playlists.json"
private const val PERSONAL_PLAYLISTS_FILE = "_personal_playlists.json"
private const val USER_PLAYLISTS_FILE = "_user_playlists.json"
private const val EDITORIAL_SHOWCASE_FILE = "_editorial_showcase.json"
private const val WAVE_CATALOG_FILE = "_wave_catalog.json"

internal val playlistStorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    isLenient = true
}

private fun cacheDir(basePath: String) = "$basePath/$CACHE_DIR"

private fun cacheFile(basePath: String, fileName: String) = "${cacheDir(basePath)}/$fileName"

private fun playlistCacheFile(basePath: String, cacheKey: String) =
    cacheFile(basePath, "${sanitizePlaylistFileName(cacheKey)}.json")

private fun localPlaylistsFile(basePath: String) = "$basePath/$LOCAL_PLAYLISTS_DIR/$LOCAL_PLAYLISTS_FILE"

/** Очистка имени плейлиста для использования в имени файла */
internal fun sanitizePlaylistFileName(title: String): String =
    sanitizeKeepSpaces(title).trim().ifBlank { "Playlist" }

private fun <T> writeJson(path: String, serializer: KSerializer<T>, value: T, what: String): Boolean = try {
    writeTextFileAtomic(path, playlistStorageJson.encodeToString(serializer, value)).also { ok ->
        if (!ok) AppLogger.w(TAG, "Не удалось записать $what: $path")
    }
} catch (e: Exception) {
    AppLogger.e(TAG, "Ошибка сохранения $what: ${e.message}", e)
    false
}

private fun <T> readJson(path: String, serializer: KSerializer<T>, what: String): T? = try {
    readTextFile(path)?.takeIf { it.isNotBlank() }?.let { playlistStorageJson.decodeFromString(serializer, it) }
} catch (e: Exception) {
    AppLogger.e(TAG, "Ошибка чтения $what: ${e.message}", e)
    null
}

private fun isExpired(path: String, maxAgeMs: Long, now: Long = currentTimeMillis()): Boolean {
    val modified = fileLastModifiedMs(path) ?: return false
    return now - modified > maxAgeMs
}

// ───────────────────────────── Треки плейлистов ─────────────────────────────

/** Сохранить список треков плейлиста в локальный кеш на диске: {basePath}/playlists_cache/{cacheKey}.json */
fun savePlaylistTracksCache(basePath: String, cacheKey: String, tracks: List<FullTrackInfo>) {
    if (basePath.isBlank() || tracks.isEmpty() || cacheKey.isBlank()) return
    if (writeJson(playlistCacheFile(basePath, cacheKey), ListSerializer(FullTrackInfo.serializer()), tracks, "кеша плейлиста '$cacheKey'")) {
        AppLogger.d(TAG, "Кеш плейлиста '$cacheKey' сохранен (${tracks.size} треков)")
    }
    cleanExpiredPlaylistCaches(basePath)
}

/** Загрузить список треков плейлиста из локального кеша; устаревший кеш удаляется */
fun loadPlaylistTracksCache(basePath: String, cacheKey: String): List<FullTrackInfo> {
    if (basePath.isBlank() || cacheKey.isBlank()) return emptyList()
    val path = playlistCacheFile(basePath, cacheKey)
    if (isExpired(path, PLAYLIST_CACHE_MAX_AGE_MS)) {
        deleteRegularFile(path)
        AppLogger.d(TAG, "Кеш плейлиста '$cacheKey' устарел и удален")
        return emptyList()
    }
    return readJson(path, ListSerializer(FullTrackInfo.serializer()), "кеша плейлиста '$cacheKey'").orEmpty()
}

/** Удалить устаревшие файлы кеша плейлистов старше maxAgeMs (по умолчанию 7 дней) */
fun cleanExpiredPlaylistCaches(basePath: String, maxAgeMs: Long = PLAYLIST_CACHE_MAX_AGE_MS): Int {
    if (basePath.isBlank()) return 0
    val dir = cacheDir(basePath)
    val now = currentTimeMillis()
    val removed = listFileNames(dir).count { name ->
        val path = "$dir/$name"
        name.endsWith(".json") && isExpired(path, maxAgeMs, now) && deleteRegularFile(path)
    }
    if (removed > 0) AppLogger.d(TAG, "Удалено $removed устаревших файлов кеша плейлистов")
    return removed
}

/** Очистить весь кеш плейлистов: удаляет все файлы из {basePath}/playlists_cache/ */
fun clearPlaylistsCache(basePath: String): Int {
    if (basePath.isBlank()) return 0
    val dir = cacheDir(basePath)
    val removed = listFileNames(dir).count { deleteRegularFile("$dir/$it") }
    AppLogger.d(TAG, "Очищен кеш плейлистов ($removed файлов удалено)")
    return removed
}

// ───────────────────────────── Служебные кеши ─────────────────────────────

/** Сохранить список персональных плейлистов в кеш для бокового меню */
fun savePersonalPlaylistsCache(basePath: String, items: List<PersonalPlaylistItemData>) {
    if (basePath.isBlank() || items.isEmpty()) return
    writeJson(cacheFile(basePath, PERSONAL_PLAYLISTS_FILE), ListSerializer(PersonalPlaylistItemData.serializer()), items, "кеша персональных плейлистов")
}

/** Загрузить список персональных плейлистов из кеша */
fun loadPersonalPlaylistsCache(basePath: String): List<PersonalPlaylistItemData> {
    if (basePath.isBlank()) return emptyList()
    return readJson(cacheFile(basePath, PERSONAL_PLAYLISTS_FILE), ListSerializer(PersonalPlaylistItemData.serializer()), "кеша персональных плейлистов").orEmpty()
}

/** Сохранить список пользовательских плейлистов из Яндекса в кеш */
fun saveUserPlaylistsCache(basePath: String, items: List<PlaylistInfo>) {
    if (basePath.isBlank() || items.isEmpty()) return
    writeJson(cacheFile(basePath, USER_PLAYLISTS_FILE), ListSerializer(PlaylistInfo.serializer()), items, "кеша пользовательских плейлистов")
}

/** Загрузить список пользовательских плейлистов из Яндекса из кеша */
fun loadUserPlaylistsCache(basePath: String): List<PlaylistInfo> {
    if (basePath.isBlank()) return emptyList()
    return readJson(cacheFile(basePath, USER_PLAYLISTS_FILE), ListSerializer(PlaylistInfo.serializer()), "кеша пользовательских плейлистов").orEmpty()
}

/** Сохранить редакционную витрину (чарты, новые плейлисты и релизы) в локальный кеш */
fun saveEditorialShowcaseCache(basePath: String, showcase: EditorialShowcaseData) {
    if (basePath.isBlank() || showcase.isEmpty) return
    writeJson(cacheFile(basePath, EDITORIAL_SHOWCASE_FILE), EditorialShowcaseData.serializer(), showcase, "кеша витрины")
}

/** Загрузить редакционную витрину из локального кеша */
fun loadEditorialShowcaseCache(basePath: String): EditorialShowcaseData? {
    if (basePath.isBlank()) return null
    return readJson(cacheFile(basePath, EDITORIAL_SHOWCASE_FILE), EditorialShowcaseData.serializer(), "кеша витрины")
}

/** Сохранить персональный каталог тем Моей волны в локальный кеш */
fun saveWaveCatalogCache(basePath: String, catalog: WavePersonalCatalog) {
    if (basePath.isBlank() || catalog.isEmpty) return
    writeJson(cacheFile(basePath, WAVE_CATALOG_FILE), WavePersonalCatalog.serializer(), catalog, "кеша каталога волны")
}

/** Загрузить персональный каталог тем Моей волны из локального кеша */
fun loadWaveCatalogCache(basePath: String): WavePersonalCatalog? {
    if (basePath.isBlank()) return null
    return readJson(cacheFile(basePath, WAVE_CATALOG_FILE), WavePersonalCatalog.serializer(), "кеша каталога волны")
}

// ───────────────────────────── Локальные плейлисты ─────────────────────────────

/** Сохранить список локальных оффлайн-плейлистов на диск: {basePath}/playlists/local_playlists.json */
fun saveLocalPlaylists(basePath: String, playlists: List<LocalPlaylist>) {
    if (basePath.isBlank()) return
    if (writeJson(localPlaylistsFile(basePath), ListSerializer(LocalPlaylist.serializer()), playlists, "локальных плейлистов")) {
        AppLogger.d(TAG, "Сохранено ${playlists.size} локальных плейлистов")
    }
}

/**
 * Загрузить список локальных оффлайн-плейлистов из {basePath}/playlists/local_playlists.json.
 * Если файла нет в основной папке, используется [localPlaylistsFallbackDir].
 * Устаревшие пути автоматически переразрешаются, дубликаты удаляются, результат сохраняется обратно.
 */
fun loadLocalPlaylists(basePath: String): List<LocalPlaylist> {
    val candidates = listOfNotNull(basePath.takeIf { it.isNotBlank() }, localPlaylistsFallbackDir()).distinct()
    val sourceBase = candidates.firstOrNull { fileLastModifiedMs(localPlaylistsFile(it)) != null } ?: return emptyList()
    val loaded = readJson(localPlaylistsFile(sourceBase), ListSerializer(LocalPlaylist.serializer()), "локальных плейлистов")
        ?: return emptyList()

    var anyMigrated = false
    val migrated = loaded.map { playlist ->
        val normalized = normalizePlaylistTrackPaths(playlist.trackPaths)
        if (normalized == playlist.trackPaths) {
            playlist
        } else {
            anyMigrated = true
            playlist.copy(trackPaths = normalized)
        }
    }
    if (anyMigrated) {
        saveLocalPlaylists(sourceBase, migrated)
        AppLogger.i(TAG, "Локальные плейлисты обновлены: пути переразрешены, дубликаты удалены")
    }
    AppLogger.d(TAG, "Загружено ${migrated.size} локальных плейлистов")
    return migrated
}

/**
 * Переразрешает устаревшие пути (только если новый путь реально существует)
 * и удаляет дубликаты по нормализованному имени файла.
 */
internal fun normalizePlaylistTrackPaths(paths: List<String>): List<String> {
    val seenNames = HashSet<String>()
    val seenBlankPaths = HashSet<String>()
    val result = ArrayList<String>(paths.size)
    for (original in paths) {
        val resolved = resolveLocalPath(original)
        val path = if (resolved != original && localFileExists(resolved)) resolved else original
        val normName = path.substringAfterLast('/').substringAfterLast('\\').trim().lowercase()
        val isNew = if (normName.isNotBlank()) seenNames.add(normName) else seenBlankPaths.add(path)
        if (isNew) result.add(path)
    }
    return result
}
