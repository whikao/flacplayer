package com.flacplayer.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun allTracks(): Flow<List<TrackEntity>>

    @Query("SELECT uri FROM tracks")
    suspend fun allUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(track: TrackEntity): Long

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<TrackEntity>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY id ASC")
    fun allPlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT tracks.* FROM tracks
        INNER JOIN playlist_entries ON tracks.id = playlist_entries.trackId
        WHERE playlist_entries.playlistId = :playlistId
        ORDER BY playlist_entries.position ASC
        """
    )
    fun tracksInPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeEntry(playlistId: Long, trackId: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int
}
