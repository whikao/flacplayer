package com.flacplayer.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class MusicRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val trackDao = db.trackDao()
    private val playlistDao = db.playlistDao()

    val tracks: Flow<List<TrackEntity>> = trackDao.allTracks()
    val playlists: Flow<List<PlaylistEntity>> = playlistDao.allPlaylists()

    fun tracksInPlaylist(playlistId: Long): Flow<List<TrackEntity>> =
        playlistDao.tracksInPlaylist(playlistId)

    suspend fun addTracks(newTracks: List<TrackEntity>) {
        val existing = trackDao.allUris().toSet()
        newTracks.filter { it.uri !in existing }.forEach { trackDao.insert(it) }
    }

    suspend fun removeTrack(trackId: Long) = trackDao.deleteById(trackId)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(PlaylistEntity(name = name))

    suspend fun renamePlaylist(id: Long, name: String) = playlistDao.rename(id, name)

    suspend fun deletePlaylist(id: Long) = playlistDao.deleteById(id)

    suspend fun addToPlaylist(playlistId: Long, trackId: Long) {
        val pos = playlistDao.nextPosition(playlistId)
        playlistDao.insertEntry(PlaylistEntry(playlistId, trackId, pos))
    }

    /** 批量添加，跳过已在歌单中的曲目，返回实际添加的数量 */
    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>): Int {
        val existing = playlistDao.trackIdsInPlaylist(playlistId).toSet()
        val newIds = trackIds.distinct().filter { it !in existing }
        var pos = playlistDao.nextPosition(playlistId)
        newIds.forEach { playlistDao.insertEntry(PlaylistEntry(playlistId, it, pos++)) }
        return newIds.size
    }

    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long) =
        playlistDao.removeEntry(playlistId, trackId)
}
