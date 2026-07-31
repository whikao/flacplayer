package com.flacplayer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flacplayer.app.ui.HistoryScreen
import com.flacplayer.app.ui.MainViewModel
import com.flacplayer.app.ui.NowPlayingScreen
import com.flacplayer.app.ui.PlaylistDetailScreen
import com.flacplayer.app.ui.PlaylistsScreen
import com.flacplayer.app.ui.SongsScreen
import com.flacplayer.app.ui.theme.FlacPlayerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlacPlayerTheme {
                AppRoot(viewModel)
            }
        }
    }

    fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var permissionGranted by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result.values.any { it }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.importTree(it) } }

    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importFiles(uris) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (navController.currentBackStackEntry == null ||
                navController.currentBackStackEntry?.destination?.route != "playlist/{id}"
            ) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            navController.navigate("songs") {
                                popUpTo("songs") { inclusive = true }
                            }
                        },
                        icon = { Icon(Icons.Filled.MusicNote, contentDescription = "歌曲") },
                        label = { Text("歌曲") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            navController.navigate("playlists") {
                                popUpTo("songs")
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "歌单") },
                        label = { Text("歌单") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            navController.navigate("now") {
                                popUpTo("songs")
                            }
                        },
                        icon = { Icon(Icons.Filled.PlayCircle, contentDescription = "正在播放") },
                        label = { Text("正在播放") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = {
                            selectedTab = 3
                            navController.navigate("history") {
                                popUpTo("songs")
                            }
                        },
                        icon = { Icon(Icons.Filled.History, contentDescription = "历史") },
                        label = { Text("历史") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "songs",
            modifier = Modifier.padding(padding)
        ) {
            composable("songs") {
                SongsScreen(
                    viewModel = viewModel,
                    onPickFolder = { folderLauncher.launch(null) },
                    onPickFiles = { filesLauncher.launch(arrayOf("audio/*")) },
                    onRequestPermission = {
                        val perms = buildList {
                            if (Build.VERSION.SDK_INT >= 33) {
                                add(Manifest.permission.READ_MEDIA_AUDIO)
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }.toTypedArray()
                        permissionLauncher.launch(perms)
                    }
                )
            }
            composable("playlists") {
                PlaylistsScreen(
                    viewModel = viewModel,
                    onOpenPlaylist = { playlist ->
                        viewModel.openPlaylist(playlist.id)
                        navController.navigate("playlist/${playlist.id}")
                    }
                )
            }
            composable(
                route = "playlist/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                PlaylistDetailScreen(
                    viewModel = viewModel,
                    playlistId = id,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("now") {
                NowPlayingScreen(viewModel = viewModel)
            }
            composable("history") {
                HistoryScreen(viewModel = viewModel)
            }
        }
    }
}
