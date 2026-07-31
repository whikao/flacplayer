package com.flacplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flacplayer.app.data.TrackEntity
import java.io.File

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
fun SongsScreen(
    viewModel: MainViewModel,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val tracks by viewModel.tracks.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    var addToPlaylistTarget by remember { mutableStateOf<TrackEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("我的歌曲", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPickFolder) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("导入文件夹")
            }
            FilledTonalButton(onClick = onPickFiles) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("导入文件")
            }
            FilledTonalButton(onClick = onRequestPermission) {
                Text("权限")
            }
        }

        if (tracks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { viewModel.playAll(tracks, 0) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("播放全部 (${tracks.size})")
            }
        }

        if (importing) {
            Row(
                Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("正在导入…", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(8.dp))
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有歌曲\n点上方「导入文件夹」或「导入文件」\n只读取你手动选择的内容，不做全盘扫描",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { viewModel.playAll(tracks, index) },
                        onAddToPlaylist = { addToPlaylistTarget = track },
                        onRemove = { viewModel.removeTrack(track) }
                    )
                }
            }
        }
    }

    addToPlaylistTarget?.let { track ->
        AlertDialog(
            onDismissRequest = { addToPlaylistTarget = null },
            title = { Text("添加到歌单") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("暂无歌单，请先在「歌单」页新建")
                    }
                    playlists.forEach { p ->
                        Text(
                            p.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addToPlaylist(p.id, track)
                                    addToPlaylistTarget = null
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addToPlaylistTarget = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackRow(
    track: TrackEntity,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            AsyncImage(
                model = track.coverPath?.let { File(it) },
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        },
        headlineContent = { Text(track.title, maxLines = 1) },
        supportingContent = {
            Text("${track.artist} · ${track.album}", maxLines = 1)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall)
                if (onAddToPlaylist != null) {
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(Icons.Filled.Add, contentDescription = "加入歌单")
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = "移除")
                    }
                }
            }
        }
    )
}
