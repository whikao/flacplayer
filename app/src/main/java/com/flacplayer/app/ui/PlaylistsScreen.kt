package com.flacplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.flacplayer.app.data.PlaylistEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: MainViewModel,
    onOpenPlaylist: (PlaylistEntity) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("歌单", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            if (playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有歌单\n点右下角 + 新建",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenPlaylist(playlist) },
                            headlineContent = { Text(playlist.name) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { renameTarget = playlist }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "重命名")
                                    }
                                    IconButton(onClick = { deleteTarget = playlist }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新建歌单")
        }
    }

    if (showCreate) {
        NameDialog(
            title = "新建歌单",
            initial = "",
            onDismiss = { showCreate = false },
            onConfirm = {
                viewModel.createPlaylist(it)
                showCreate = false
            }
        )
    }
    renameTarget?.let { p ->
        NameDialog(
            title = "重命名歌单",
            initial = p.name,
            onDismiss = { renameTarget = null },
            onConfirm = {
                viewModel.renamePlaylist(p, it)
                renameTarget = null
            }
        )
    }
    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除歌单") },
            text = { Text("确定删除「${p.name}」吗？歌单内歌曲不会从曲库删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(p)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: MainViewModel,
    playlistId: Long,
    onBack: () -> Unit
) {
    val tracks by viewModel.playlistTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playlistName = playlists.firstOrNull { it.id == playlistId }?.name ?: "歌单"

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(playlistName, style = MaterialTheme.typography.headlineSmall)
        }
        if (tracks.isNotEmpty()) {
            TextButton(onClick = { viewModel.playAll(tracks, 0) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("播放全部 (${tracks.size})")
            }
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "歌单为空\n去「歌曲」页点歌曲右侧 + 加入",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(tracks.size, key = { tracks[it].id }) { index ->
                    val track = tracks[index]
                    TrackRow(
                        track = track,
                        onClick = { viewModel.playAll(tracks, index) },
                        onRemove = { viewModel.removeFromPlaylist(playlistId, track) }
                    )
                }
            }
        }
    }
}
