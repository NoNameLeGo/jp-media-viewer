package com.jp.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
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
    subfolderFilterUri: Uri?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onToggleSubfolderFilter: () -> Unit,
    onMediaLoadError: () -> Unit
) {
    if (mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("没有找到媒体文件", color = Color.White)
        }
        return
    }

    val item = mediaItems[currentIndex]
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val contentOffsetY = remember { Animatable(0f) }
    var showControls by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }
    var isSwipeAnimating by remember { mutableStateOf(false) }
    val currentOnToggleFavorite by rememberUpdatedState(onToggleFavorite)
    val imageScale = remember(item.uri) { Animatable(1f) }
    var imageOffset by remember(item.uri) { mutableStateOf(Offset.Zero) }
    val animatedImageOffset by animateOffsetAsState(
        targetValue = imageOffset,
        animationSpec = spring(stiffness = 450f, dampingRatio = 0.82f),
        label = "imageOffset"
    )
    var imageLoadSize by remember(item.uri) { mutableStateOf(IntSize.Zero) }
    var settledZoomScale by remember(item.uri) { mutableStateOf(1f) }
    val isImageZoomed = item.isImage && imageScale.value > 1.01f
    var videoPlayerRef by remember { mutableStateOf<Player?>(null) }
    var isVideoPlaying by remember { mutableStateOf(false) }
    var isVideoMuted by remember { mutableStateOf(false) }

    DisposableEffect(videoPlayerRef) {
        val p = videoPlayerRef
        if (p != null) {
            val stateListener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isVideoPlaying = p.isPlaying
                }
            }
            p.addListener(stateListener)
            onDispose { p.removeListener(stateListener) }
        } else {
            onDispose { }
        }
    }
    val targetZoomScale = when {
        imageScale.value >= 2.40f -> 4f
        imageScale.value >= 1.60f -> 2f
        imageScale.value >= 1.15f -> 1.5f
        else -> 1f
    }
    val imageTargetSize = remember(item.uri, settledZoomScale, imageLoadSize) {
        if (!item.isImage || imageLoadSize.width <= 0 || imageLoadSize.height <= 0) {
            null
        } else {
            IntSize(
                width = (imageLoadSize.width * settledZoomScale).roundToInt(),
                height = (imageLoadSize.height * settledZoomScale).roundToInt()
            )
        }
    }
    val folderName = deepestFolderName(item.folderUri)
    val isSubfolderFiltered = subfolderFilterUri != null
    val swipeScale = if (contentOffsetY.value < 0f) {
        (1f + contentOffsetY.value / 1000f).coerceAtLeast(0.9f)
    } else {
        (1f - contentOffsetY.value / 1200f).coerceAtLeast(0.9f)
    }
    val swipeAlpha = (1f - abs(contentOffsetY.value) / 1600f).coerceAtLeast(0.7f)

    LaunchedEffect(item.uri, imageScale.value, imageLoadSize) {
        if (!item.isImage || imageLoadSize.width <= 0 || imageLoadSize.height <= 0) return@LaunchedEffect
        if (imageScale.value <= 1.01f) {
            settledZoomScale = 1f
            return@LaunchedEffect
        }
        val stableScale = targetZoomScale
        kotlinx.coroutines.delay(80)
        if (imageScale.value >= stableScale - 0.05f && imageLoadSize.width > 0 && imageLoadSize.height > 0) {
            settledZoomScale = stableScale
        }
    }
    LaunchedEffect(item.uri) {
        showDetails = false
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        fun clampImageOffset(offset: Offset, scale: Float): Offset {
            if (scale <= 1f) return Offset.Zero
            val maxX = screenWidthPx * (scale - 1f) / 2f
            val maxY = screenHeightPx * (scale - 1f) / 2f
            return Offset(
                x = offset.x.coerceIn(-maxX, maxX),
                y = offset.y.coerceIn(-maxY, maxY)
            )
        }
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
                    playVideo = false,
                    onLoadError = {},
                    onImageSizeChanged = {},
                    isMuted = isVideoMuted
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
                loadSize = imageTargetSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (item.isImage) {
                            scaleX = imageScale.value
                            scaleY = imageScale.value
                            translationX = animatedImageOffset.x
                            translationY = animatedImageOffset.y
                        }
                    },
                playVideo = true,
                onLoadError = onMediaLoadError,
                onImageSizeChanged = { imageLoadSize = it },
                onPlayerReady = { videoPlayerRef = it },
                isMuted = isVideoMuted
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(item.uri, item.isImage) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { tapOffset ->
                            if (item.isImage) {
                                scope.launch {
                                    val nextScale = if (imageScale.value > 1.01f) 1f else 2f
                                    val nextOffset = if (nextScale == 1f) {
                                        Offset.Zero
                                    } else {
                                        clampImageOffset(
                                            Offset(
                                                x = (screenWidthPx / 2f - tapOffset.x) * (nextScale - 1f),
                                                y = (screenHeightPx / 2f - tapOffset.y) * (nextScale - 1f)
                                            ),
                                            nextScale
                                        )
                                    }
                                    settledZoomScale = nextScale
                                    imageOffset = nextOffset
                                    imageScale.animateTo(
                                        targetValue = nextScale,
                                        animationSpec = spring(stiffness = 450f, dampingRatio = 0.82f)
                                    )
                                }
                            }
                        },
                        onLongPress = { showDetails = true }
                    )
                }
                .pointerInput(item.uri, item.isImage) {
                    if (!item.isImage) return@pointerInput

                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            val shouldTransform = pressedChanges.size > 1 || imageScale.value > 1f
                            if (shouldTransform) {
                                val zoomChange = if (pressedChanges.size > 1) event.calculateZoom() else 1f
                                val panChange = if (pressedChanges.size > 1) {
                                    event.calculatePan()
                                } else {
                                    pressedChanges.firstOrNull()?.positionChange() ?: Offset.Zero
                                }
                                val newScale = (imageScale.value * zoomChange).coerceIn(1f, 4f)
                                imageOffset = clampImageOffset(imageOffset + panChange, newScale)
                                scope.launch {
                                    imageScale.snapTo(newScale)
                                }
                                if (newScale <= 1.01f) {
                                    settledZoomScale = 1f
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        if (imageScale.value <= 1.01f) {
                            imageOffset = Offset.Zero
                            scope.launch {
                                imageScale.snapTo(1f)
                            }
    }
}

                }
                .pointerInput(isImageZoomed) {
                    var totalDy = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            totalDy = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!isSwipeAnimating && !isImageZoomed) {
                                totalDy += dragAmount
                                change.consume()
                                scope.launch {
                                    contentOffsetY.snapTo(contentOffsetY.value + dragAmount)
                                }
                            }
                        },
                        onDragEnd = {
                            if (!isSwipeAnimating && !isImageZoomed) {
                                scope.launch {
                                    when {
                                        totalDy < -60f -> {
                                            isSwipeAnimating = true
                                            contentOffsetY.animateTo(
                                                targetValue = -screenHeightPx,
                                                animationSpec = tween(200, easing = FastOutLinearInEasing)
                                            )
                                            onNext()
                                            contentOffsetY.snapTo(0f)
                                            isSwipeAnimating = false
                                        }
                                        totalDy > 60f -> {
                                            isSwipeAnimating = true
                                            contentOffsetY.animateTo(
                                                targetValue = screenHeightPx,
                                                animationSpec = tween(200, easing = FastOutLinearInEasing)
                                            )
                                            onPrevious()
                                            contentOffsetY.snapTo(0f)
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
                            if (!isImageZoomed) {
                                scope.launch {
                                    contentOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = 2000f, dampingRatio = 0.85f)
                                    )
                                }
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = "${currentIndex + 1} / ${mediaItems.size}${if (isFavoriteBrowsing) " · 收藏" else if (isFavorite) " · 已收藏" else ""}${if (isSubfolderFiltered) " · 📁 $folderName" else ""}",
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
                    IconButton(onClick = onToggleSubfolderFilter) {
                        Icon(
                            Icons.Default.FilterList,
                            if (isSubfolderFiltered) "查看全部" else "只看此文件夹",
                            tint = if (isSubfolderFiltered) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
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
                    IconButton(onClick = currentOnToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "大小：${formatFileSize(item.size)} · 文件夹：$folderName${if (isSubfolderFiltered) " 📁 子文件夹模式" else ""}",
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(folderName))
                        Toast.makeText(context, "已复制文件夹名称", Toast.LENGTH_SHORT).show()
                    }
                )
                if (item.isVideo) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isVideoPlaying) "暂停" else "播放",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { isVideoMuted = !isVideoMuted },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = if (isVideoMuted) "取消静音" else "静音",
                                tint = if (isVideoMuted) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
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
                        Text("日期：${formatModifiedDate(item.modifiedAt)}")
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
    loadSize: IntSize? = null,
    modifier: Modifier = Modifier,
    playVideo: Boolean,
    onLoadError: () -> Unit,
    onImageSizeChanged: (IntSize) -> Unit = {},
    onPlayerReady: (Player) -> Unit = {},
    isMuted: Boolean = false
) {
    val context = LocalContext.current

    if (item.isVideo && playVideo) {
        VideoPlayer(uri = item.uri, modifier = modifier, onLoadError = onLoadError, onPlayerReady = onPlayerReady, isMuted = isMuted)
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .apply {
                    if (loadSize != null && loadSize.width > 0 && loadSize.height > 0) {
                        size(loadSize.width, loadSize.height)
                    }
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            loading = {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = modifier.onSizeChanged(onImageSizeChanged),
            onError = { onLoadError() }
        )
    }
}

