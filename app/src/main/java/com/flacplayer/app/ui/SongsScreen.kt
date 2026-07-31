package com.flacplayer.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flacplayer.app.data.TrackEntity
import kotlinx.coroutines.launch
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

    // 多选状态：选中的 trackId 集合（rememberSaveable 用 List 存储以便跨配置恢复）
    var selectedIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    var batchAddDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            if (selectionMode) {
                // 多选工具栏
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { selectedIds = emptyList() }) {
                        Icon(Icons.Filled.Close, contentDescription = "退出多选")
                    }
                    Text(
                        "已选 ${selectedIds.size} 首",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        selectedIds = if (selectedIds.size == tracks.size) {
                            emptyList()
                        } else {
                            tracks.map { it.id }
                        }
                    }) {
                        Text(if (selectedIds.size == tracks.size) "取消全选" else "全选")
                    }
                    FilledTonalButton(onClick = { batchAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("添加到歌单")
                    }
                }
            } else {
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
                            selectionMode = selectionMode,
                            selected = track.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (track.id in selectedIds) {
                                        selectedIds - track.id
                                    } else {
                                        selectedIds + track.id
                                    }
                                } else {
                                    viewModel.playAll(tracks, index)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) selectedIds = listOf(track.id)
                            },
                            onAddToPlaylist = { addToPlaylistTarget = track },
                            onRemove = { viewModel.removeTrack(track) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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

    if (batchAddDialog) {
        AlertDialog(
            onDismissRequest = { batchAddDialog = false },
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
                                    val ids = selectedIds
                                    batchAddDialog = false
                                    selectedIds = emptyList()
                                    viewModel.addTracksToPlaylist(p.id, ids) { added ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar("已添加 $added 首")
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { batchAddDialog = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: TrackEntity,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (selected) "已选中" else "未选中",
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                }
                AsyncImage(
                    model = track.coverPath?.let { File(it) },
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        },
        headlineContent = { Text(track.title, maxLines = 1) },
        supportingContent = {
            Text("${track.artist} · ${track.album}", maxLines = 1)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall)
                if (!selectionMode && onAddToPlaylist != null) {
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(Icons.Filled.Add, contentDescription = "加入歌单")
                    }
                }
                if (!selectionMode && onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = "移除")
                    }
                }
            }
        }
    )
}
