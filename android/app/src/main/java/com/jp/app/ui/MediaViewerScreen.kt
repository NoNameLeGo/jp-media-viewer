package com.jp.app.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jp.app.data.MediaItem
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaItems: List<MediaItem>,
    currentIndex: Int,
    isFavoriteBrowsing: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    if (mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("没有找到媒体文件", color = Color.White)
        }
        return
    }

    val item = mediaItems[currentIndex]
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val contentOffsetY = remember { Animatable(0f) }
    var showControls by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }
    var isSwipeAnimating by remember { mutableStateOf(false) }
    val folderName = item.folderUri.lastPathSegment ?: item.folderUri.toString()
    val swipeScale = if (contentOffsetY.value < 0f) {
        (1f + contentOffsetY.value / 1000f).coerceAtLeast(0.9f)
    } else {
        (1f - contentOffsetY.value / 1200f).coerceAtLeast(0.9f)
    }
    val swipeAlpha = (1f - abs(contentOffsetY.value) / 1600f).coerceAtLeast(0.7f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val previewDirection = when {
            contentOffsetY.value < 0f -> 1
            contentOffsetY.value > 0f -> -1
            else -> 0
        }
        val previewProgress = (abs(contentOffsetY.value) / (screenHeightPx * 0.45f)).coerceIn(0f, 1f)
        val previewIndex = when (previewDirection) {
            1 -> (currentIndex + 1) % mediaItems.size
            -1 -> (currentIndex - 1 + mediaItems.size) % mediaItems.size
            else -> currentIndex
        }

        if (previewDirection != 0 && mediaItems.size > 1) {
            val previewStartOffset = screenHeightPx * 0.12f * previewDirection
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (previewStartOffset * (1f - previewProgress)).roundToInt()) }
                    .scale(0.94f + 0.06f * previewProgress)
                    .graphicsLayer { alpha = 0.35f + 0.65f * previewProgress }
            ) {
                MediaSurface(
                    item = mediaItems[previewIndex],
                    modifier = Modifier.fillMaxSize(),
                    playVideo = false
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, contentOffsetY.value.roundToInt()) }
                .scale(swipeScale)
                .graphicsLayer { alpha = swipeAlpha }
        ) {
            MediaSurface(
                item = item,
                modifier = Modifier.fillMaxSize(),
                playVideo = true
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { onToggleFavorite() },
                        onLongPress = { showDetails = true }
                    )
                }
                .pointerInput(Unit) {
                    var totalDy = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            totalDy = 0f
                            showControls = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!isSwipeAnimating) {
                                totalDy += dragAmount
                                change.consume()
                                scope.launch {
                                    contentOffsetY.snapTo(contentOffsetY.value + dragAmount)
                                }
                            }
                        },
                        onDragEnd = {
                            if (!isSwipeAnimating) {
                                scope.launch {
                                    when {
                                        totalDy < -60f -> {
                                            isSwipeAnimating = true
                                            contentOffsetY.animateTo(
                                                targetValue = -screenHeightPx,
                                                animationSpec = tween(200, easing = FastOutLinearInEasing)
                                            )
                                            onNext()
                                            contentOffsetY.snapTo(screenHeightPx * 0.25f)
                                            contentOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                                            )
                                            isSwipeAnimating = false
                                        }
                                        totalDy > 60f -> {
                                            isSwipeAnimating = true
                                            contentOffsetY.animateTo(
                                                targetValue = screenHeightPx,
                                                animationSpec = tween(200, easing = FastOutLinearInEasing)
                                            )
                                            onPrevious()
                                            contentOffsetY.snapTo(-screenHeightPx * 0.25f)
                                            contentOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                                            )
                                            isSwipeAnimating = false
                                        }
                                        else -> {
                                            contentOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(stiffness = 2000f, dampingRatio = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                contentOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = 2000f, dampingRatio = 0.85f)
                                )
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.fillMaxSize()
        ) {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "${currentIndex + 1} / ${mediaItems.size}${if (isFavoriteBrowsing) " · 收藏" else if (isFavorite) " · 已收藏" else ""}",
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, "设置", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = if (item.isVideo) "视频" else "图片",
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "大小：${formatFileSize(item.size)} · 文件夹：$folderName",
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (showDetails) {
            AlertDialog(
                onDismissRequest = { showDetails = false },
                title = { Text("文件详情") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("文件名：${item.name}")
                        Text("大小：${formatFileSize(item.size)}")
                        Text("文件夹：$folderName")
                        Text("状态：${if (isFavorite) "已收藏" else "未收藏"}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDetails = false }) {
                        Text("关闭")
                    }
                }
            )
        }
    }
}

@Composable
private fun MediaSurface(
    item: MediaItem,
    modifier: Modifier = Modifier,
    playVideo: Boolean
) {
    val context = LocalContext.current

    if (item.isVideo && playVideo) {
        VideoPlayer(uri = item.uri, modifier = modifier)
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "未知"

    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }

    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(size, units[unitIndex])
    }
}

@Composable
fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
    )
}
