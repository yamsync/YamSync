package io.github.audiz

/**
 * 🔥 КРОСС-ПЛАТФОРМЕННЫЕ КОНТРАКТЫ ДЛЯ ХРАНЕНИЯ МУЗЫКИ
 * Организованная структура: {basePath}/{Artist}/{Artist — Title.ext}
 */

/** Путь по умолчанию для сохранения музыки */
expect fun getDefaultMusicDir(): String

/** Сохранить трек в структурированную папку */
expect fun saveTrackFile(basePath: String, artist: String, fileName: String, bytes: ByteArray)

/** Файл трека, открытый для последовательной записи кусками (без загрузки целиком в память). */
interface TrackFileWriter {
    fun write(buffer: ByteArray, length: Int)
    /** Закрыть файл после успешной записи. */
    fun close()
    /** Закрыть и удалить недописанный файл. */
    fun abort()
}

/** Открыть файл трека для потоковой записи: {basePath}/{Artist}/{fileName} */
expect fun openTrackFileWriter(basePath: String, artist: String, fileName: String): TrackFileWriter

/** Сохранить файл напрямую в указанную папку: {targetDir}/{fileName} */
expect fun saveTrackToFolder(targetDir: String, fileName: String, bytes: ByteArray): String?

/** Скопировать существующий файл в указанную папку: {targetDir}/{destFileName} */
expect fun copyFileToFolder(sourceFilePath: String, targetDir: String, destFileName: String? = null): String?

/** Сохранить файл по прямому целевому пути: {destFilePath} */
expect fun saveFileToDirectPath(destFilePath: String, bytes: ByteArray): String?

/** Скопировать существующий файл по прямому целевому пути: {destFilePath} */
expect fun copyFileToDirectPath(sourceFilePath: String, destFilePath: String): String?

/** Проверить, существует ли трек на диске */
expect fun trackFileExists(basePath: String, artist: String, fileName: String): Boolean

/** Сохранить/загрузить настройку пути хранения музыки */
expect fun saveMusicStoragePath(path: String)
expect fun loadMusicStoragePath(): String?

/** Проверить существование файла по прямому пути */
expect fun localFileExists(filePath: String): Boolean

/** Разрешить локальный путь к файлу с fallback поиском / сопоставлением путей */
expect fun resolveLocalPath(path: String): String

/** Проверить, является ли путь существующей директорией */
expect fun isDirectory(path: String): Boolean

/** Прочитать содержимое текстового файла (например, плейлиста M3U) */
expect fun readTextFile(path: String): String?

/** Сканировать локальные аудиофайлы и сформировать список треков без сетевых запросов */
expect fun scanDownloadedTracks(basePath: String): List<io.github.audiz.models.FullTrackInfo>

/** Быстро получить список всех существующих путей к скачанным аудиофайлам (без чтения метаданных и длительности) */
expect fun scanDownloadedTrackPaths(basePath: String): List<String>

/**
 * Примитивы файлового ввода-вывода для общего кода (кеши, локальные плейлисты).
 * Сериализация и раскладка файлов живут в [PlaylistCacheStorage.kt], здесь — только платформенная запись/чтение.
 */

/** Записать текст в файл атомарно (через временный файл), создав родительские папки. */
internal expect fun writeTextFileAtomic(path: String, text: String): Boolean

/** Время последнего изменения файла в миллисекундах с эпохи или null, если файла нет. */
internal expect fun fileLastModifiedMs(path: String): Long?

/** Имена обычных файлов (без подпапок) в директории; пустой список, если директории нет. */
internal expect fun listFileNames(dirPath: String): List<String>

/** Удалить обычный файл. */
internal expect fun deleteRegularFile(path: String): Boolean

/** Резервная папка для поиска локальных плейлистов, если основная недоступна (например, сменился контейнер приложения). */
internal expect fun localPlaylistsFallbackDir(): String?

/** Экспортировать локальный плейлист в формат M3U8: {basePath}/playlists/{title}.m3u8 */
expect fun exportPlaylistToM3u8(basePath: String, playlist: io.github.audiz.models.LocalPlaylist, tracks: List<io.github.audiz.models.FullTrackInfo>): String

/** Преобразовать список локальных путей к файлам в список объектов FullTrackInfo с проверкой наличия на диске */
expect fun getTracksFromLocalPaths(paths: List<String>): List<io.github.audiz.models.FullTrackInfo>

/** Удалить аудиофайл трека с диска (и пустую папку артиста, если файлов больше нет) */
expect fun deleteTrackFile(basePath: String, artist: String, trackTitle: String, localFilePath: String? = null): Boolean

/** Удалить все скачанные треки из папок HQ, LQ и корневой директории */
expect fun clearAllDownloadedMusic(basePath: String): Int

/** Дописать строку лога в файл отладки */
expect fun appendLogToFile(line: String)

/** Получить абсолютный путь к файлу логов */
expect fun getLogFilePath(): String

private val ILLEGAL_CHARS_REGEX = Regex("[\\\\/:*?\"<>|]")

/** Очистка недопустимых символов в именах файлов с сохранением пробелов */
fun sanitizeKeepSpaces(input: String): String {
    return input.replace(ILLEGAL_CHARS_REGEX, "_")
}

/** Очистка недопустимых символов в именах папок */
fun sanitizeDirName(name: String): String {
    return name.replace(ILLEGAL_CHARS_REGEX, "_").trim().ifEmpty { "Unknown" }
}

/** Получить листинг содержимого директории (подпапки и аудиофайлы) для встроенного проводника */
expect fun listFolderContents(folderPath: String, rootPath: String? = null): io.github.audiz.models.FolderListing

/** Загрузить байты обложки локального аудиофайла (встроенный тег APIC/PICTURE/covr или файл обложки в папке) */
expect fun loadLocalCoverBytes(pathOrUri: String): ByteArray?

/** Получить локальный URL/URI к обложке трека (для MPRIS/системных уведомлений) */
expect fun getLocalCoverArtUrl(pathOrUri: String): String?
