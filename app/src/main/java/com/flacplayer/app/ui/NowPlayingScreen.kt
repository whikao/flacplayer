package com.flacplayer.app.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun NowPlayingScreen(viewModel: MainViewModel) {
    val player = viewModel.player
    val isPlaying by player.isPlaying.collectAsState()
    val positionMs by player.positionMs.collectAsState()
    val durationMs by player.durationMs.collectAsState()
    val playMode by player.playMode.collectAsState()
    val currentIndex by player.currentIndex.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val sleepRemaining by viewModel.sleepTimer.remainingMs.collectAsState()

    val track = player.currentTrack()
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var showSleepDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        AsyncImage(
            model = track?.coverPath?.let { File(it) },
            contentDescription = "封面",
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(16.dp))
        Text(
            track?.title ?: "未在播放",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1
        )
        Text(
            "${track?.artist ?: "-"} · ${track?.album ?: "-"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Spacer(Modifier.height(12.dp))
        val effectiveDuration = durationMs.takeIf { it > 0 } ?: track?.durationMs ?: 0L
        Slider(
            value = if (dragging) dragPosition else positionMs.toFloat().coerceIn(0f, effectiveDuration.toFloat().coerceAtLeast(1f)),
            onValueChange = {
                dragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                player.seekTo(dragPosition.toLong())
                dragging = false
            },
            valueRange = 0f..effectiveDuration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(if (dragging) dragPosition.toLong() else positionMs),
                style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(effectiveDuration), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = { player.cyclePlayMode() }) {
                when (playMode) {
                    1 -> Icon(Icons.Filled.RepeatOne, contentDescription = "单曲循环",
                        tint = MaterialTheme.colorScheme.primary)
                    2 -> Icon(Icons.Filled.Shuffle, contentDescription = "随机播放",
                        tint = MaterialTheme.colorScheme.primary)
                    else -> Icon(Icons.Filled.Repeat, contentDescription = "顺序播放")
                }
            }
            IconButton(onClick = { player.previous() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = { player.togglePlayPause() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { player.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { showSleepDialog = true }) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = "睡眠定时",
                    tint = if (sleepRemaining > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (sleepRemaining > 0) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "睡眠定时剩余 ${formatDuration(sleepRemaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { viewModel.sleepTimer.cancel() }) {
                    Text("取消", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // 歌词区
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (lyrics.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无歌词\n（支持同目录同名 .lrc 文件或内嵌歌词）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LyricsList(lyrics = lyrics, positionMs = positionMs)
            }
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepDialog = false },
            onStart = { minutes ->
                viewModel.sleepTimer.start(minutes)
                showSleepDialog = false
            }
        )
    }
}

@Composable
fun LyricsList(lyrics: List<com.flacplayer.app.lyrics.LrcLine>, positionMs: Long) {
    val listState = rememberLazyListState()
    val currentLine = lyrics.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)

    LaunchedEffect(currentLine) {
        if (lyrics.isNotEmpty()) {
            listState.animateScrollToItem(currentLine.coerceIn(lyrics.indices))
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lyrics) { index, line ->
            val active = index == currentLine
            Text(
                line.text.ifBlank { "♪" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                style = if (active) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    var hoursText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("30") }
    var error by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("睡眠定时") },
        text = {
            Column {
                Text("输入小时和分钟，到时自动暂停播放，最长约 999 小时")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = {
                            hoursText = it.take(3)
                            error = false
                        },
                        label = { Text("小时") },
                        singleLine = true,
                        isError = error,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = {
                            minutesText = it.take(2)
                            error = false
                        },
                        label = { Text("分钟") },
                        singleLine = true,
                        isError = error,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hours = when {
                    hoursText.isBlank() -> 0
                    hoursText.toIntOrNull() == null -> {
                        error = true
                        errorMessage = "小时请输入数字"
                        return@TextButton
                    }
                    else -> hoursText.toInt()
                }
                val minutes = when {
                    minutesText.isBlank() -> 0
                    minutesText.toIntOrNull() == null -> {
                        error = true
                        errorMessage = "分钟请输入数字"
                        return@TextButton
                    }
                    else -> minutesText.toInt()
                }
                if (hours !in 0..999) {
                    error = true
                    errorMessage = "小时需在 0–999 之间"
                } else if (minutes !in 0..59) {
                    error = true
                    errorMessage = "分钟需在 0–59 之间"
                } else if (hours * 60 + minutes <= 0) {
                    error = true
                    errorMessage = "请输入大于 0 的时间"
                } else {
                    onStart(hours * 60 + minutes)
                }
            }) { Text("开始") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
