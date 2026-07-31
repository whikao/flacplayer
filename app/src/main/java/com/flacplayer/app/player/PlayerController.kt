package com.flacplayer.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flacplayer.app.data.MusicRepository
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
    private val repository = MusicRepository(context)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /** 当前播放会话（播放历史），null 表示未在播放 */
    private var sessionId: Long? = null
    private var wasPlaying = false

    /** 当前交给播放器的完整列表 */
    var queue: List<TrackEntity> = emptyList()
        private set

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    /** 本次启动以来实际处于播放状态的累计秒数 */
    private val _playElapsed = MutableStateFlow(0L)
    val playElapsed: StateFlow<Long> = _playElapsed

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
                startElapsedTicker()
                startSessionHeartbeat()
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

    private fun startElapsedTicker() {
        scope.launch {
            while (true) {
                if (controller?.isPlaying == true) {
                    _playElapsed.value += 1
                }
                delay(1000)
            }
        }
    }

    private fun startSessionHeartbeat() {
        // 每 30 秒心跳一次：更新未闭合会话的 endMs，
        // 即使突然断电，数据库里也保留最近一次心跳的结束时间
        scope.launch {
            while (true) {
                delay(30_000)
                if (controller?.isPlaying == true) {
                    sessionId?.let { id -> repository.updateSessionEnd(id) }
                }
            }
        }
    }

    fun resetPlayElapsed() {
        _playElapsed.value = 0L
    }

    private fun syncState(player: Player) {
        _currentIndex.value = player.currentMediaItemIndex.takeIf { it >= 0 } ?: -1
        _isPlaying.value = player.isPlaying
        if (player.isPlaying != wasPlaying) {
            wasPlaying = player.isPlaying
            if (player.isPlaying) onSessionStart() else onSessionEnd()
        }
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

    /** 播放开始：开启一条新的历史会话 */
    private fun onSessionStart() {
        val title = currentTrack()?.title ?: ""
        scope.launch { sessionId = repository.startSession(title) }
    }

    /** 播放停止/释放：闭合当前会话 */
    private fun onSessionEnd() {
        val id = sessionId ?: return
        sessionId = null
        scope.launch { repository.updateSessionEnd(id) }
    }

    fun release() {
        wasPlaying = false
        onSessionEnd()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }
}
