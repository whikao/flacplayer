package com.flacplayer.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flacplayer.app.data.MusicRepository
import com.flacplayer.app.data.PlaylistEntity
import com.flacplayer.app.data.TrackEntity
import com.flacplayer.app.importer.SafImporter
import com.flacplayer.app.lyrics.LrcLine
import com.flacplayer.app.lyrics.LyricsLoader
import com.flacplayer.app.player.PlayerController
import com.flacplayer.app.player.SleepTimerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    private val importer = SafImporter(app)
    private val lyricsLoader = LyricsLoader(app)

    val player = PlayerController(app)
    val sleepTimer = SleepTimerManager { player.pause() }

    val tracks: StateFlow<List<TrackEntity>> = repository.tracks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _playlistTracks = MutableStateFlow<List<TrackEntity>>(emptyList())
    val playlistTracks: StateFlow<List<TrackEntity>> = _playlistTracks.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    init {
        player.connect()
        viewModelScope.launch {
            player.currentIndex.collect {
                reloadLyrics()
            }
        }
    }

    fun importTree(uri: Uri) {
        SafImporter.persistPermission(getApplication(), uri)
        viewModelScope.launch {
            _importing.value = true
            try {
                val result = importer.importTree(uri)
                repository.addTracks(result.tracks)
            } finally {
                _importing.value = false
            }
        }
    }

    fun importFiles(uris: List<Uri>) {
        uris.forEach { SafImporter.persistPermission(getApplication(), it) }
        viewModelScope.launch {
            _importing.value = true
            try {
                val result = importer.importFiles(uris)
                repository.addTracks(result.tracks)
            } finally {
                _importing.value = false
            }
        }
    }

    fun removeTrack(track: TrackEntity) {
        viewModelScope.launch { repository.removeTrack(track.id) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name.trim().ifBlank { "新歌单" }) }
    }

    fun renamePlaylist(playlist: PlaylistEntity, name: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlist.id, name.trim().ifBlank { playlist.name })
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { repository.deletePlaylist(playlist.id) }
    }

    fun addToPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch { repository.addToPlaylist(playlistId, track.id) }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val added = repository.addTracksToPlaylist(playlistId, trackIds)
            onDone(added)
        }
    }

    fun removeFromPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch { repository.removeFromPlaylist(playlistId, track.id) }
    }

    fun openPlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.tracksInPlaylist(playlistId).collect { _playlistTracks.value = it }
        }
    }

    fun playAll(tracks: List<TrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        player.playQueue(tracks, startIndex)
    }

    private fun reloadLyrics() {
        val track = player.currentTrack()
        viewModelScope.launch {
            _lyrics.value = track?.let { lyricsLoader.load(it) } ?: emptyList()
        }
    }

    override fun onCleared() {
        sleepTimer.cancel()
        player.release()
        super.onCleared()
    }
}
