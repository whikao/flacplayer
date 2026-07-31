package com.flacplayer.app.lyrics

import android.content.Context
import android.net.Uri
import com.flacplayer.app.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {
    private val TAG = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(raw: String): List<LrcLine> {
        val result = mutableListOf<LrcLine>()
        for (line in raw.lines()) {
            val tags = TAG.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.replace(TAG, "").trim()
            for (tag in tags) {
                val min = tag.groupValues[1].toLongOrNull() ?: continue
                val sec = tag.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = tag.groupValues.getOrNull(3).orEmpty()
                val frac = when (fracRaw.length) {
                    0 -> 0L
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10
                    else -> fracRaw.substring(0, 3).toLongOrNull() ?: 0L
                }
                result.add(LrcLine(min * 60_000 + sec * 1000 + frac, text))
            }
        }
        return result.sortedBy { it.timeMs }
    }
}

class LyricsLoader(private val context: Context) {

    /** 优先同目录同名 .lrc，其次内嵌歌词 */
    suspend fun load(track: TrackEntity): List<LrcLine> = withContext(Dispatchers.IO) {
        track.lrcUri?.let { lrc ->
            try {
                context.contentResolver.openInputStream(Uri.parse(lrc))?.use { input ->
                    val text = input.readBytes().toString(Charsets.UTF_8)
                    val parsed = LrcParser.parse(text)
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
            } catch (_: Exception) {
            }
        }
        track.embeddedLyrics?.let { embedded ->
            val parsed = LrcParser.parse(embedded)
            if (parsed.isNotEmpty()) return@withContext parsed
        }
        emptyList()
    }
}
