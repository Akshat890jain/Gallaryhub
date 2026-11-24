package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.data.remote.RetrofitClient
import com.example.util.VideoMediaTools
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Upgraded Jetpack Media3 ExoPlayer video stage featuring:
 * - Strict active-page gating (zero off-screen playback when neighboring pages are prefetched)
 * - Speed selector (0.5x–2.0x), Mute toggle, Double-tap ±10s seek HUD
 * - Left/right vertical edge swipe gestures for Brightness & Volume
 * - Picture-in-Picture (PiP) launcher, Frame Grab capture, and lossless Quick Video Trimmer
 */
@OptIn(UnstableApi::class)
@Composable
fun ProVideoPlayerStage(
    videoUri: String,
    displayName: String,
    isActivePage: Boolean,
    onEnterPipMode: () -> Unit = {},
    onFrameGrabbed: (File) -> Unit = {},
    onTrimmedClipSaved: (File) -> Unit = {},
    onRecoverMissingFile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val latestIsActive by rememberUpdatedState(isActivePage)

    var hasActivated by remember(videoUri) { mutableStateOf(false) }
    var isBuffering by remember(videoUri) { mutableStateOf(true) }
    var errorMessage by remember(videoUri) { mutableStateOf<String?>(null) }
    var retryKey by remember(videoUri) { mutableIntStateOf(0) }
    var playbackSpeed by remember(videoUri) { mutableFloatStateOf(1.0f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var isMuted by remember(videoUri) { mutableStateOf(false) }
    var durationMs by remember(videoUri) { mutableLongStateOf(0L) }
    var seekHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var isGrabbingFrame by remember { mutableStateOf(false) }
    var showTrimmerDialog by remember { mutableStateOf(false) }
    var isTrimming by remember { mutableStateOf(false) }
    var trimRange by remember(videoUri) { mutableStateOf(0f..1f) }

    // Never allocate or stream off-screen neighbor videos until the user lands on this page
    LaunchedEffect(isActivePage) {
        if (isActivePage && !hasActivated) {
            hasActivated = true
        }
    }

    // Auto-hide seek & gesture HUD pills
    LaunchedEffect(seekHudText) {
        if (seekHudText != null) {
            delay(850L)
            seekHudText = null
        }
    }
    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(950L)
            gestureHudText = null
        }
    }

    val exoPlayer = remember(videoUri, hasActivated, retryKey) {
        if (!hasActivated || videoUri.isBlank()) {
            null
        } else {
            val okHttpFactory = OkHttpDataSource.Factory(RetrofitClient.okHttpClient)
            val dataSourceFactory = DefaultDataSource.Factory(context, okHttpFactory)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(videoUri))
                    playWhenReady = false
                    prepare()
                }
        }
    }

    // Listen for player state, duration, and errors
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    if (latestIsActive && !exoPlayer.isPlaying) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                errorMessage = "Could not load \"$displayName\". Check your connection or re-link it from your device."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            runCatching { exoPlayer.release() }
        }
    }

    // Sync active-page state, speed, and mute strictly to ExoPlayer
    LaunchedEffect(exoPlayer, isActivePage, playbackSpeed, isMuted) {
        val player = exoPlayer ?: return@LaunchedEffect
        player.playbackParameters = PlaybackParameters(playbackSpeed)
        player.volume = if (isMuted) 0f else 1f
        if (isActivePage && errorMessage == null) {
            player.playWhenReady = true
            player.play()
        } else {
            player.playWhenReady = false
            player.pause()
        }
    }

    // Pause on app background (unless entering Picture-in-Picture)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            val player = exoPlayer ?: return@LifecycleEventObserver
            val activity = context as? Activity
            val inPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                activity?.isInPictureInPictureMode == true
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (!inPip) {
                        player.playWhenReady = false
                        player.pause()
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (latestIsActive && errorMessage == null) {
                        player.playWhenReady = true
                        player.play()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A0D12),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .height(380.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    RoundedCornerShape(20.dp)
                )
                .clip(RoundedCornerShape(20.dp))
                .testTag("video_player_stage")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    videoUri.isBlank() || errorMessage != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = "Video playback error",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "Unable to Play Video",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = errorMessage ?: "Video source is unavailable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        errorMessage = null
                                        isBuffering = true
                                        retryKey++
                                    },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("video_viewer_retry_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry")
                                }
                                if (onRecoverMissingFile != null) {
                                    Button(
                                        onClick = onRecoverMissingFile,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.testTag("video_viewer_relink_button")
                                    ) {
                                        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Re-link")
                                    }
                                }
                            }
                        }
                    }

                    !hasActivated || exoPlayer == null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Video ready",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = "Video ready",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }

                    else -> {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    player = exoPlayer
                                }
                            },
                            update = { pv ->
                                if (pv.player !== exoPlayer) {
                                    pv.player = exoPlayer
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Gesture overlay for double-tap ±10s seek & vertical Brightness/Volume swipe
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left half: Double-tap -10s + Vertical Drag = Brightness
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .pointerInput(exoPlayer) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                                exoPlayer.seekTo(target)
                                                seekHudText = "-10s"
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { _, dragAmount ->
                                            val window = (context as? Activity)?.window ?: return@detectVerticalDragGestures
                                            val lp = window.attributes
                                            val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                            val next = (current - dragAmount / 520f).coerceIn(0.08f, 1.0f)
                                            lp.screenBrightness = next
                                            window.attributes = lp
                                            gestureHudText = "Brightness ${(next * 100).toInt()}%"
                                        }
                                    }
                            )

                            // Right half: Double-tap +10s + Vertical Drag = Volume
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .pointerInput(exoPlayer) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                val maxDur = exoPlayer.duration.coerceAtLeast(0L)
                                                val target = (exoPlayer.currentPosition + 10_000L).let {
                                                    if (maxDur > 0L) it.coerceAtMost(maxDur) else it
                                                }
                                                exoPlayer.seekTo(target)
                                                seekHudText = "+10s"
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { _, dragAmount ->
                                            val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                                ?: return@detectVerticalDragGestures
                                            val maxVol = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                            val curVol = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val step = if (dragAmount < -18f) 1 else if (dragAmount > 18f) -1 else 0
                                            if (step != 0) {
                                                val nextVol = (curVol + step).coerceIn(0, maxVol)
                                                audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                                gestureHudText = "Volume ${(nextVol * 100) / maxVol}%"
                                            }
                                        }
                                    }
                            )
                        }

                        if (isBuffering) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }

                        // Seek / Gesture HUD Pill
                        val hudLabel = seekHudText ?: gestureHudText
                        androidx.compose.animation.AnimatedVisibility(
                            visible = hudLabel != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.72f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            hudLabel?.startsWith("-") == true -> Icons.Default.FastRewind
                                            hudLabel?.startsWith("+") == true -> Icons.Default.FastForward
                                            hudLabel?.startsWith("Brightness") == true -> Icons.Default.Brightness6
                                            else -> Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = hudLabel.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Pro Video Studio Controls Row (Speed, Mute, PiP, Frame Grab, Trim)
        if (hasActivated && errorMessage == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { speedMenuExpanded = true },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("video_speed_button")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${playbackSpeed}x", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { speedMenuExpanded = false }
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            DropdownMenuItem(
                                text = { Text("${spd}x") },
                                onClick = {
                                    playbackSpeed = spd
                                    speedMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier.testTag("video_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }

                FilledTonalIconButton(
                    onClick = onEnterPipMode,
                    modifier = Modifier.testTag("video_pip_button")
                ) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture-in-Picture")
                }

                FilledTonalIconButton(
                    enabled = !isGrabbingFrame,
                    onClick = {
                        val posMs = exoPlayer?.currentPosition ?: 0L
                        isGrabbingFrame = true
                        scope.launch {
                            val res = VideoMediaTools.extractVideoFrameToFile(
                                context = context,
                                videoUrl = videoUri,
                                positionMs = posMs,
                                baseName = displayName
                            )
                            isGrabbingFrame = false
                            res.onSuccess { file -> onFrameGrabbed(file) }
                                .onFailure { err ->
                                    gestureHudText = err.localizedMessage ?: "Frame grab failed"
                                }
                        }
                    },
                    modifier = Modifier.testTag("video_frame_grab_button")
                ) {
                    if (isGrabbingFrame) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Save Still Frame")
                    }
                }

                FilledTonalIconButton(
                    onClick = {
                        exoPlayer?.pause()
                        trimRange = 0f..1f
                        showTrimmerDialog = true
                    },
                    modifier = Modifier.testTag("video_trim_button")
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Trim Video Clip")
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }

    // Quick Lossless Video Trimmer Dialog
    if (showTrimmerDialog) {
        val totalDuration = durationMs.coerceAtLeast(1000L)
        val startMs = (trimRange.start * totalDuration).toLong()
        val endMs = (trimRange.endInclusive * totalDuration).toLong()
        fun fmtSec(ms: Long): String {
            val s = (ms / 1000L).coerceAtLeast(0L)
            return "%d:%02d".format(s / 60L, s % 60L)
        }
        AlertDialog(
            onDismissRequest = { if (!isTrimming) showTrimmerDialog = false },
            icon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
            title = { Text("Quick Trim Clip", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select the range to export as a trimmed MP4 clip (saved to your gallery & Offline Vault).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RangeSlider(
                        value = trimRange,
                        onValueChange = { next ->
                            if (next.endInclusive - next.start >= 0.05f) {
                                trimRange = next
                            }
                        },
                        valueRange = 0f..1f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Start: ${fmtSec(startMs)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("End: ${fmtSec(endMs)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isTrimming,
                    onClick = {
                        isTrimming = true
                        scope.launch {
                            val res = VideoMediaTools.trimVideoLossless(
                                context = context,
                                videoUrl = videoUri,
                                startMs = startMs,
                                endMs = endMs,
                                baseName = displayName
                            )
                            isTrimming = false
                            showTrimmerDialog = false
                            res.onSuccess { trimmedFile -> onTrimmedClipSaved(trimmedFile) }
                                .onFailure { err ->
                                    gestureHudText = err.localizedMessage ?: "Trim failed"
                                }
                        }
                    },
                    modifier = Modifier.testTag("video_trim_confirm_button")
                ) {
                    if (isTrimming) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Trimming…")
                    } else {
                        Text("Save Trimmed Clip")
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isTrimming, onClick = { showTrimmerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
