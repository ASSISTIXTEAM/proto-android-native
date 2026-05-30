package org.assistix.proto.nativeapp.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.ProtoApplication
import org.assistix.proto.nativeapp.data.MediaViewerItem
import org.assistix.proto.nativeapp.data.MsgItem
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoCacheManager
import org.assistix.proto.nativeapp.data.ProtoForwardState
import org.assistix.proto.nativeapp.data.ProtoMediaGallery
import org.assistix.proto.nativeapp.data.ProtoMediaViewerState
import org.assistix.proto.nativeapp.data.guessMimeFromName
import org.assistix.proto.nativeapp.data.mediaKindFromMime
import org.assistix.proto.nativeapp.data.normalizeUploadId

private val ViewerBgTop = Color(0xFF0A0A0C)
private val ViewerBgBottom = Color(0xFF050506)
private val TopChromeHeight = 56.dp
private val BottomChromeHeight = 132.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProtoMediaViewerOverlay(
    token: String?,
    api: ProtoApi,
    onDismiss: () -> Unit = { ProtoMediaViewerState.close() },
) {
    if (!ProtoMediaViewerState.active || ProtoMediaViewerState.items.isEmpty()) return
    val items = ProtoMediaViewerState.items
    val pagerState = rememberPagerState(initialPage = ProtoMediaViewerState.startIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var dismissDragY by remember { mutableFloatStateOf(0f) }
    var pageZoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = items.getOrNull(pagerState.currentPage)
    val dismissAlpha by animateFloatAsState(
        targetValue = (1f - (dismissDragY / 900f)).coerceIn(0.35f, 1f),
        label = "viewerDismissAlpha",
    )

    fun dismissViewer() {
        dismissDragY = 0f
        onDismiss()
    }

    LaunchedEffect(pagerState.currentPage) {
        pageZoomed = false
        dismissDragY = 0f
    }

    BackHandler { dismissViewer() }

    Dialog(
        onDismissRequest = { dismissViewer() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(ViewerBgTop, ViewerBgBottom, ViewerBgTop)),
                )
                .graphicsLayer {
                    translationY = dismissDragY
                    alpha = dismissAlpha
                }
                .pointerInput(pageZoomed) {
                    if (pageZoomed) return@pointerInput
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dismissDragY > 180f) {
                                dismissViewer()
                            } else {
                                dismissDragY = 0f
                            }
                        },
                        onDragCancel = { dismissDragY = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0f || dismissDragY > 0f) {
                                dismissDragY = (dismissDragY + dragAmount).coerceAtLeast(0f)
                            }
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = TopChromeHeight, bottom = BottomChromeHeight),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !pageZoomed && dismissDragY <= 0f,
                ) { page ->
                    val item = items[page]
                    val isCurrent = pagerState.currentPage == page
                    if (item.isVideo) {
                        MediaViewerVideoPage(
                            item = item,
                            token = token,
                            api = api,
                            active = isCurrent,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onPlayNext = {
                                if (page < items.lastIndex) {
                                    scope.launch { pagerState.animateScrollToPage(page + 1) }
                                }
                            },
                        )
                    } else {
                        MediaViewerImagePage(
                            item = item,
                            token = token,
                            api = api,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onZoomChanged = { zoomed -> if (isCurrent) pageZoomed = zoomed },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                MediaViewerTopBar(
                    currentIndex = pagerState.currentPage,
                    total = items.size,
                    onClose = { dismissViewer() },
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
            ) {
                MediaViewerBottomChrome(
                    item = current,
                    token = token,
                    api = api,
                    sourceMessage = ProtoMediaViewerState.sourceMessage,
                    onForward = {
                        val msg = ProtoMediaViewerState.sourceMessage
                        if (msg != null) {
                            ProtoForwardState.start(msg, ProtoMediaViewerState.fromLabel)
                            dismissViewer()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaViewerTopBar(
    currentIndex: Int,
    total: Int,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = TopChromeHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Icon(Icons.Default.Close, contentDescription = UiStrings.back, tint = Color.White)
        }
        if (total > 1) {
            Text(
                UiStrings.mediaViewerCounterFmt(currentIndex + 1, total),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .clip(ProtoShapes.pill)
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        } else {
            Box(Modifier.size(44.dp))
        }
        Box(Modifier.size(44.dp))
    }
}

@Composable
private fun MediaViewerBottomChrome(
    item: MediaViewerItem?,
    token: String?,
    api: ProtoApi,
    sourceMessage: MsgItem?,
    onForward: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item?.caption?.takeIf { it.isNotBlank() }?.let { cap ->
            Text(
                cap,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Row(
            Modifier
                .clip(ProtoShapes.pill)
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sourceMessage != null) {
                MediaViewerActionChip(
                    icon = { Icon(Icons.AutoMirrored.Filled.Forward, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    label = UiStrings.forward,
                    enabled = !busy,
                    onClick = onForward,
                )
            }
            MediaViewerActionChip(
                icon = { Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                label = UiStrings.mediaViewerSave,
                enabled = !busy && item != null && token != null,
                onClick = {
                    val t = token ?: return@MediaViewerActionChip
                    val cur = item ?: return@MediaViewerActionChip
                    scope.launch {
                        busy = true
                        val file = resolveViewerMediaFile(ctx, api, t, cur) ?: run {
                            busy = false
                            Toast.makeText(ctx, UiStrings.downloadFailed, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val mime = cur.mime ?: guessMimeFromName(cur.name)
                        val name = cur.name?.takeIf { it.isNotBlank() } ?: if (cur.isVideo) "proto_video.mp4" else "proto_image.jpg"
                        val ok = withContext(Dispatchers.IO) { ProtoMediaGallery.saveToGallery(ctx, file, name, mime) }
                        busy = false
                        Toast.makeText(
                            ctx,
                            if (ok) UiStrings.mediaViewerSavedGallery else UiStrings.downloadFailed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
            MediaViewerActionChip(
                icon = { Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                label = UiStrings.share,
                enabled = !busy && item != null && token != null,
                onClick = {
                    val t = token ?: return@MediaViewerActionChip
                    val cur = item ?: return@MediaViewerActionChip
                    scope.launch {
                        busy = true
                        val file = resolveViewerMediaFile(ctx, api, t, cur) ?: run {
                            busy = false
                            Toast.makeText(ctx, UiStrings.shareFailed, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val mime = cur.mime ?: guessMimeFromName(cur.name)
                        withContext(Dispatchers.Main) {
                            if (!ProtoMediaGallery.shareFile(ctx, file, mime)) {
                                Toast.makeText(ctx, UiStrings.shareFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                        busy = false
                    }
                },
            )
        }
    }
}

@Composable
private fun MediaViewerActionChip(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .pointerInput(enabled, onClick) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        icon()
        Text(
            label,
            color = Color.White.copy(alpha = if (enabled) 0.95f else 0.4f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MediaViewerImagePage(
    item: MediaViewerItem,
    token: String?,
    api: ProtoApi,
    onToggleChrome: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    var scale by remember(item) { mutableFloatStateOf(1f) }
    var offset by remember(item) { mutableStateOf(Offset.Zero) }
    var loading by remember(item) { mutableStateOf(true) }
    var localFile by remember(item) { mutableStateOf<File?>(null) }
    val isGif = remember(item) { isGifItem(item) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
    }

    LaunchedEffect(item, token) {
        loading = true
        localFile = null
        val t = token
        val id = normalizeUploadId(item.uploadId)
        if (t != null && id != null) {
            localFile = withContext(Dispatchers.IO) { downloadMediaCached(ctx, api, t, id, item) }
        }
        loading = false
    }

    val model =
        remember(item, localFile, token, isGif) {
            val builder =
                when {
                    localFile != null -> ImageRequest.Builder(ctx).data(localFile)
                    item.imageUrl != null -> ImageRequest.Builder(ctx).data(item.imageUrl)
                    item.uploadId != null ->
                        ImageRequest.Builder(ctx)
                            .data(api.mediaUrl(item.uploadId!!))
                            .apply { token?.let { t -> api.authHeaders(t).forEach { (k, v) -> addHeader(k, v) } } }
                    else -> null
                }
            builder?.apply {
                crossfade(!isGif)
                if (isGif) {
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        decoderFactory(ImageDecoderDecoder.Factory())
                    } else {
                        decoderFactory(GifDecoder.Factory())
                    }
                }
            }?.build()
        }

    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        when {
            loading && model == null -> CircularProgressIndicator(color = ProtoOrange)
            model != null -> {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onSuccess = { loading = false },
                    onError = { loading = false },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxH)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .pointerInput(item, maxW, maxH) {
                                val maxPxX = maxW.toPx()
                                val maxPxY = maxH.toPx()
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale
                                    if (newScale > 1f) {
                                        val maxX = (maxPxX * (newScale - 1f)) / 2f
                                        val maxY = (maxPxY * (newScale - 1f)) / 2f
                                        offset =
                                            Offset(
                                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                                            )
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            }
                            .pointerInput(item) {
                                detectTapGestures(
                                    onTap = { if (scale <= 1.05f) onToggleChrome() },
                                    onDoubleTap = { tap ->
                                        if (scale > 1.1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                            offset = Offset.Zero
                                        }
                                    },
                                )
                            },
                )
            }
        }
    }
}

@Composable
private fun MediaViewerVideoPage(
    item: MediaViewerItem,
    token: String?,
    api: ProtoApi,
    active: Boolean,
    onToggleChrome: () -> Unit,
    onPlayNext: () -> Unit,
) {
    val ctx = LocalContext.current
    var localFile by remember(item) { mutableStateOf<File?>(null) }
    var loading by remember(item) { mutableStateOf(true) }
    var playing by remember(item) { mutableStateOf(false) }
    var positionMs by remember(item) { mutableLongStateOf(0L) }
    var durationMs by remember(item) { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(item, token) {
        loading = true
        playing = false
        localFile = null
        val t = token
        val id = normalizeUploadId(item.uploadId)
        if (t != null && id != null) {
            localFile = withContext(Dispatchers.IO) { downloadMediaCached(ctx, api, t, id, item) }
        }
        loading = false
    }

    val exoPlayer =
        remember(item) {
            ExoPlayer.Builder(ctx).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
            }
        }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(localFile, active) {
        if (!active || localFile == null) {
            exoPlayer.pause()
            playing = false
            return@LaunchedEffect
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(localFile!!)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        playing = true
    }

    LaunchedEffect(active) {
        if (!active) {
            exoPlayer.pause()
            playing = false
        }
    }

    DisposableEffect(exoPlayer, active) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && active) {
                        playing = false
                        positionMs = 0L
                        exoPlayer.seekTo(0)
                        onPlayNext()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(playing, active, scrubbing) {
        while (isActive && active && !scrubbing) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0) durationMs = d
            delay(180)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(color = ProtoOrange)
            localFile == null -> Text(UiStrings.downloadFailed, color = Color.White.copy(0.7f))
            else -> {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { view ->
                        view.player = exoPlayer
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight),
                )
                if (!playing) {
                    IconButton(
                        onClick = {
                            exoPlayer.play()
                            playing = true
                        },
                        modifier =
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = UiStrings.play, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
            }
        }

        if (!loading && localFile != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (playing) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    Text(formatViewerMs(positionMs), color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        onValueChange = { frac ->
                            scrubbing = true
                            val target = (durationMs * frac).toLong()
                            positionMs = target
                            exoPlayer.seekTo(target)
                        },
                        onValueChangeFinished = { scrubbing = false },
                        modifier = Modifier.weight(1f),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = ProtoOrange,
                                activeTrackColor = ProtoOrange,
                                inactiveTrackColor = Color.White.copy(0.25f),
                            ),
                    )
                    Text(formatViewerMs(durationMs), color = Color.White.copy(0.65f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun isGifItem(item: MediaViewerItem): Boolean {
    val mime = item.mime?.lowercase().orEmpty()
    val name = item.name?.lowercase().orEmpty()
    return mime.contains("gif") || name.endsWith(".gif")
}

private suspend fun resolveViewerMediaFile(
    ctx: android.content.Context,
    api: ProtoApi,
    token: String,
    item: MediaViewerItem,
): File? {
    val id = normalizeUploadId(item.uploadId)
    if (id != null) {
        return downloadMediaCached(ctx, api, token, id, item)
    }
    val url = item.imageUrl?.takeIf { it.isNotBlank() } ?: return null
    return withContext(Dispatchers.IO) {
        val cache = appCache(ctx)
        val ext = if (url.lowercase().endsWith(".gif")) "gif" else if (url.lowercase().endsWith(".png")) "png" else "jpg"
        val f = File(cache.rootDir, "viewer_url_${url.hashCode()}.$ext")
        if (f.exists() && f.length() > 0L) return@withContext f
        runCatching {
            java.net.URL(url).openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            f.takeIf { it.length() > 0L }
        }.getOrNull()
    }
}

private fun appCache(ctx: android.content.Context): ProtoCacheManager {
    val app = ctx.applicationContext as? ProtoApplication
    return app?.cache ?: ProtoCacheManager(ctx)
}

private suspend fun downloadMediaCached(
    ctx: android.content.Context,
    api: ProtoApi,
    token: String,
    uploadId: String,
    item: MediaViewerItem,
): File? {
    val app = ctx.applicationContext as? ProtoApplication
    val cache = app?.cache ?: ProtoCacheManager(ctx)
    val kind = item.kind.ifBlank { mediaKindFromMime(item.mime, item.name) ?: "image" }
    val file =
        when (kind) {
            "video" -> cache.videoFile(uploadId)
            else -> cache.photoFile(uploadId)
        }
    if (file.exists() && file.length() > 0L) return file
    val ok = api.downloadMedia(token, uploadId, file)
    return file.takeIf { ok && it.length() > 0L }
}

private fun formatViewerMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
