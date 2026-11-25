package io.github.audiz

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import io.github.audiz.util.AppLogger

actual fun shareTrackFile(
    filePath: String,
    title: String,
    artist: String,
    trackId: String,
    albumId: Long?
) {
    val file = File(filePath)
    val caption = buildTrackShareCaption(title, artist, trackId, includeLocalLink = true)

    try {
        if (file.exists()) {
            val transferable = FileTransferable(listOf(file), caption)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
            AppLogger.d("PlatformShare", "📤 [YamSync] Файл '${file.name}' и ссылка скопированы в буфер обмена для вставки в чат (Ctrl+V)")
        } else {
            val stringSelection = java.awt.datatransfer.StringSelection(caption)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(stringSelection, null)
            AppLogger.d("PlatformShare", "📤 [YamSync] Ссылка на трек скопирована в буфер обмена: $caption")
        }
    } catch (e: Exception) {
        AppLogger.e("PlatformShare", "Ошибка копирования в буфер обмена: ${e.message}", e)
    }
}

private class FileTransferable(
    private val files: List<File>,
    private val text: String
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(
        DataFlavor.javaFileListFlavor,
        DataFlavor.stringFlavor
    )

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return flavor == DataFlavor.javaFileListFlavor || flavor == DataFlavor.stringFlavor
    }

    override fun getTransferData(flavor: DataFlavor): Any {
        return when (flavor) {
            DataFlavor.javaFileListFlavor -> files
            DataFlavor.stringFlavor -> text
            else -> throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
        }
    }
}
