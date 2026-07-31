package com.flacplayer.app.importer

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.flacplayer.app.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 纯 SAF 导入器：只遍历用户手动选择的文件夹 / 文件，绝不做全盘扫描。
 */
class SafImporter(private val context: Context) {

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "flac", "mp3", "aac", "m4a", "ogg", "opus", "wav", "wma", "ape", "dsd", "dff", "dsf"
        )
        private val COVER_NAMES = setOf("folder.jpg", "cover.jpg", "folder.png", "cover.png", "artist.jpg")

        fun persistPermission(context: Context, uri: Uri) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
        }
    }

    data class ScanResult(val tracks: List<TrackEntity>, val skipped: Int)

    /** 递归遍历用户选择的 DocumentTree */
    suspend fun importTree(treeUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ScanResult(emptyList(), 0)
        val collected = mutableListOf<Triple<Uri, String?, String?>>() // (audioUri, lrcUri, folderCoverPath)
        var skipped = 0

        fun walk(dir: DocumentFile) {
            val children = dir.listFiles()
            val audio = mutableListOf<DocumentFile>()
            val lrcByBase = mutableMapOf<String, Uri>()
            var folderCover: String? = null

            for (child in children) {
                val name = child.name ?: continue
                if (child.isDirectory) continue
                val lower = name.lowercase()
                when {
                    AUDIO_EXTENSIONS.contains(lower.substringAfterLast('.', "")) -> audio.add(child)
                    lower.endsWith(".lrc") ->
                        lrcByBase[lower.removeSuffix(".lrc")] = child.uri
                    lower in COVER_NAMES && folderCover == null ->
                        folderCover = saveBytesToCache(child.uri, "folder_" + child.uri.toString().hashCode())
                }
            }

            for (a in audio) {
                val base = (a.name ?: "").lowercase().substringBeforeLast('.')
                collected.add(Triple(a.uri, lrcByBase[base]?.toString(), folderCover))
            }

            for (child in children) {
                if (child.isDirectory) walk(child)
            }
        }

        try {
            walk(root)
        } catch (_: Exception) {
            skipped++
        }
        ScanResult(collected.map { (uri, lrc, folderCover) -> toEntity(uri, lrc, folderCover) }, skipped)
    }

    /** 用户手动多选文件（无法访问同目录，lrc 仅尝试内嵌歌词） */
    suspend fun importFiles(uris: List<Uri>): ScanResult = withContext(Dispatchers.IO) {
        ScanResult(uris.map { toEntity(it, null, null) }, 0)
    }

    private fun toEntity(uri: Uri, lrcUri: String?, folderCoverPath: String?): TrackEntity {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration: Long = 0L
        var coverPath: String? = null
        var embeddedLyrics: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            embeddedLyrics = try {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LYRICS)
            } catch (_: Exception) {
                null
            }
            retriever.embeddedPicture?.let { bytes ->
                coverPath = saveCoverBytes(bytes, uri.toString().hashCode().toString())
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (coverPath == null) coverPath = folderCoverPath

        val displayName = DocumentFile.fromSingleUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "未知曲目"

        return TrackEntity(
            uri = uri.toString(),
            title = title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
            artist = artist?.takeIf { it.isNotBlank() } ?: "未知歌手",
            album = album?.takeIf { it.isNotBlank() } ?: "未知专辑",
            durationMs = duration,
            coverPath = coverPath,
            lrcUri = lrcUri,
            embeddedLyrics = embeddedLyrics
        )
    }

    private fun saveBytesToCache(uri: Uri, prefix: String): String? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        saveCoverBytes(bytes, prefix)
    } catch (_: Exception) {
        null
    }

    private fun saveCoverBytes(bytes: ByteArray, key: String): String? = try {
        val dir = File(context.filesDir, "covers")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "cover_$key.jpg")
        if (!file.exists()) {
            FileOutputStream(file).use { it.write(bytes) }
        }
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}
