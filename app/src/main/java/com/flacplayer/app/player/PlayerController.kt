package com.flacplayer.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flacplayer.app.data.TrackEntity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /** 当前交给播放器的完整列表 */
    var queue: List<TrackEntity> = emptyList()
        private set

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    /** 0=顺序播放(列表循环) 1=单曲循环 2=随机 */
    private val _playMode = MutableStateFlow(0)
    val playMode: StateFlow<Int> = _playMode

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
        }
    }

    fun connect(onReady: (() -> Unit)? = null) {
        if (controller != null) {
            onReady?.invoke()
            return
        }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener({
                val c = future.get()
                controller = c
                c.addListener(listener)
                syncState(c)
                startPositionPolling()
                onReady?.invoke()
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun startPositionPolling() {
        scope.launch {
            while (true) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        _positionMs.value = c.currentPosition
                    } else {
                        _positionMs.value = c.currentPosition
                    }
                }
                delay(500)
            }
        }
    }

    private fun syncState(player: Player) {
        _currentIndex.value = player.currentMediaItemIndex.takeIf { it >= 0 } ?: -1
        _isPlaying.value = player.isPlaying
        _durationMs.value = player.duration.coerceAtLeast(0L)
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _playMode.value = when {
            player.shuffleModeEnabled -> 2
            player.repeatMode == Player.REPEAT_MODE_ONE -> 1
            else -> 0
        }
    }

    fun currentTrack(): TrackEntity? =
        _currentIndex.value.takeIf { it in queue.indices }?.let { queue[it] }

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int) {
        queue = tracks
        val c = controller ?: run { connect { playQueue(tracks, startIndex) }; return }
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, startIndex.coerceIn(0, tracks.lastIndex.coerceAtLeast(0)), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun cyclePlayMode() {
        val c = controller ?: return
        when (_playMode.value) {
            0 -> { // 顺序 -> 单曲循环
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_ONE
            }
            1 -> { // 单曲 -> 随机
                c.shuffleModeEnabled = true
                c.repeatMode = Player.REPEAT_MODE_ALL
            }
            else -> { // 随机 -> 顺序
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
        syncState(c)
    }

    fun pause() = controller?.pause()

    fun release() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }
}
