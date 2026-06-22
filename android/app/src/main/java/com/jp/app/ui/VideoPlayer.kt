package com.jp.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    onLoadError: () -> Unit,
    onPlayerReady: (Player) -> Unit = {},
    isMuted: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnLoadError by androidx.compose.runtime.derivedStateOf { onLoadError }
    val currentOnPlayerReady by androidx.compose.runtime.derivedStateOf { onPlayerReady }

    // Single ExoPlayer instance reused across URI changes
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    androidx.compose.runtime.LaunchedEffect(player) {
        currentOnPlayerReady(player)
    }

    // Apply mute state when it changes
    LaunchedEffect(player, isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    // Swap media item when URI changes (no player recreation)
    androidx.compose.runtime.LaunchedEffect(uri) {
        player.stop()
        player.setMediaItem(ExoMediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
    }

    // Lifecycle: pause on background, release on dispose
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (player.playbackState != Player.STATE_IDLE) player.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                currentOnLoadError()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
    )
}
