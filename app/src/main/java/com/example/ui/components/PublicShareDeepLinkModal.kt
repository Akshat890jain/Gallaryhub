package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.UnifiedMediaItem
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.ui.viewmodel.PublicShareViewState
import com.example.util.ShareLinkCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-screen direct viewer launched whenever a GalleryHub share link is tapped
 * (HTTPS `/s/...`, `/api/share/...`, `/share/...`, or `galleryhub://s/...`).
 * Works seamlessly whether the user is signed in or signed out.
 */
@Composable
fun PublicShareDeepLinkModal(
    incomingLinkSource: String,
    shareState: PublicShareViewState,
    serverBaseUrl: String,
    isSavingOffline: Boolean,
    isSavedOffline: Boolean,
    onSubmitPassword: (tokenOrUrl: String, password: String) -> Unit,
    onRetry: (tokenOrUrl: String) -> Unit,
    onSaveOffline: (UnifiedMediaItem) -> Unit,
    onExportSharedMedia: (UnifiedMediaItem?, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var passwordInput by remember(incomingLinkSource) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val parsedLink = remember(incomingLinkSource) { ShareLinkCodec.parseShareInput(incomingLinkSource) }
    val directBrowserUrl = remember(incomingLinkSource, shareState, serverBaseUrl) {
        val activeToken = when (shareState) {
            is PublicShareViewState.Unlocked -> shareState.link.token
            else -> parsedLink?.rawToken
        }
        val base = parsedLink?.hostBaseUrl ?: serverBaseUrl
        if (!activeToken.isNullOrBlank()) {
            ShareLinkCodec.buildDirectServerUrl(base, activeToken)
        } else {
            incomingLinkSource
        }
    }
    val maskedLabel = remember(incomingLinkSource, shareState, serverBaseUrl) {
        val activeToken = when (shareState) {
            is PublicShareViewState.Unlocked -> shareState.link.token
            else -> parsedLink?.rawToken
        }
        val base = parsedLink?.hostBaseUrl ?: serverBaseUrl
        if (!activeToken.isNullOrBlank()) {
            ShareLinkCodec.buildMaskedDisplayPreview(base, activeToken)
        } else {
            incomingLinkSource.take(36)
        }
    }

    fun launchInBrowser() {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(directBrowserUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D10))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("public_share_deep_link_modal")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    GalleryHubLogoMark(
                        size = 34.dp,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (shareState) {
                                is PublicShareViewState.Unlocked -> shareState.link.fileName
                                else -> "Shared via GalleryHub"
                            },
                            color = Color.White,
                            fontFamily = PlayfairDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = maskedLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("deep_link_viewer_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close shared file viewer",
                            tint = Color.White
                        )
                    }
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (shareState) {
                    PublicShareViewState.Idle,
                    PublicShareViewState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Opening shared link…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Resolving masked link securely from GalleryHub cloud",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is PublicShareViewState.PasswordRequired -> {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Password Protected",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Enter the link password to unlock this file",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!shareState.errorMessage.isNullOrBlank()) {
                                    Text(
                                        text = shareState.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it },
                                    label = { Text("Share Password") },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("deep_link_password_input")
                                )

                                Button(
                                    onClick = { onSubmitPassword(incomingLinkSource, passwordInput) },
                                    enabled = passwordInput.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("deep_link_unlock_button")
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Unlock & View", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { launchInBrowser() },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open in Browser")
                                }
                            }
                        }
                    }

                    is PublicShareViewState.Unlocked -> {
                        val media = shareState.mediaItem
                        if (media != null) {
                            DeepLinkMediaStage(
                                item = media,
                                serverBaseUrl = parsedLink?.hostBaseUrl ?: serverBaseUrl
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = shareState.link.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "This shared page is web-rendered. Tap Open in Browser below to view it directly.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.72f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    is PublicShareViewState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Unable to Open Shared Link",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = shareState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { onRetry(incomingLinkSource) },
                                        shape = RoundedCornerShape(13.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("deep_link_retry_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Retry")
                                    }
                                    OutlinedButton(
                                        onClick = { launchInBrowser() },
                                        shape = RoundedCornerShape(13.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("deep_link_error_browser_button")
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Browser")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar when Unlocked
            if (shareState is PublicShareViewState.Unlocked) {
                val unlockedMedia = shareState.mediaItem
                Surface(
                    color = Color(0xFF13181E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (unlockedMedia != null) {
                            FilledTonalButton(
                                onClick = { if (!isSavingOffline && !isSavedOffline) onSaveOffline(unlockedMedia) },
                                enabled = !isSavingOffline,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("deep_link_save_offline_button")
                            ) {
                                if (isSavingOffline) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (isSavedOffline) Icons.Default.CloudDone else Icons.Default.DownloadForOffline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isSavedOffline) "Saved" else "Save Offline",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { onExportSharedMedia(unlockedMedia, directBrowserUrl) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("deep_link_export_button")
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Export",
                                maxLines = 1,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { launchInBrowser() },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("deep_link_open_browser_button")
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Browser",
                                maxLines = 1,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepLinkMediaStage(
    item: UnifiedMediaItem,
    serverBaseUrl: String
) {
    when (item.mediaType) {
        MediaType.IMAGE -> {
            var scale by remember(item.id) { mutableFloatStateOf(1f) }
            var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
            val imageModel = remember(item, serverBaseUrl) { item.resolveMediaUrl(serverBaseUrl) }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(item.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = nextScale
                            offset = if (nextScale <= 1.01f) Offset.Zero else offset + pan
                        }
                    }
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = item.originalName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
        }

        MediaType.VIDEO -> {
            val resolvedVideoUrl = remember(item, serverBaseUrl) { item.resolveMediaUrl(serverBaseUrl) }
            val lifecycleOwner = LocalLifecycleOwner.current
            var isPrepared by remember(resolvedVideoUrl) { mutableStateOf(false) }
            var videoError by remember(resolvedVideoUrl) { mutableStateOf<String?>(null) }
            var retryKey by remember(resolvedVideoUrl) { mutableIntStateOf(0) }
            var savedPosMs by remember(resolvedVideoUrl) { mutableIntStateOf(0) }
            var videoViewRef by remember(resolvedVideoUrl, retryKey) { mutableStateOf<VideoView?>(null) }
            var controllerRef by remember(resolvedVideoUrl, retryKey) { mutableStateOf<MediaController?>(null) }

            DisposableEffect(lifecycleOwner, videoViewRef) {
                val observer = LifecycleEventObserver { _, event ->
                    val vv = videoViewRef ?: return@LifecycleEventObserver
                    when (event) {
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                            runCatching {
                                controllerRef?.hide()
                                if (vv.isPlaying) {
                                    savedPosMs = vv.currentPosition
                                    vv.pause()
                                }
                            }
                        }

                        Lifecycle.Event.ON_RESUME -> {
                            if (isPrepared && videoError == null && !vv.isPlaying) {
                                runCatching {
                                    if (savedPosMs > 0) vv.seekTo(savedPosMs)
                                    vv.start()
                                }
                            }
                        }

                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    runCatching {
                        controllerRef?.hide()
                        videoViewRef?.pause()
                        videoViewRef?.stopPlayback()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (videoError != null || resolvedVideoUrl.isBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Video playback error",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = videoError ?: "Could not play this shared video.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = {
                                videoError = null
                                isPrepared = false
                                retryKey++
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                } else {
                    androidx.compose.runtime.key(resolvedVideoUrl, retryKey) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    controllerRef = controller
                                    setMediaController(controller)
                                    setVideoURI(Uri.parse(resolvedVideoUrl))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        isPrepared = true
                                        if (savedPosMs > 0) runCatching { seekTo(savedPosMs) }
                                        start()
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        isPrepared = false
                                        videoError = "Video stream unavailable. Try Retry or Open in Browser."
                                        true
                                    }
                                    videoViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!isPrepared) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        MediaType.DOCUMENT -> {
            val localFile = remember(item) {
                item.localUri?.removePrefix("file://")?.let { File(it) }
                    ?.takeIf { it.exists() && it.length() > 0L }
                    ?: File(item.path).takeIf { it.exists() && it.length() > 0L }
            }
            val ext = item.extension

            if (localFile != null && ext == "pdf") {
                DeepLinkPdfPreview(pdfFile = localFile)
            } else if (localFile != null && ext in setOf("txt", "md", "json", "csv", "xml", "kt", "js", "ts", "py", "html", "css", "log")) {
                var textBody by remember(localFile.absolutePath) { mutableStateOf("Loading text preview…") }
                LaunchedEffect(localFile.absolutePath) {
                    textBody = withContext(Dispatchers.IO) {
                        runCatching { localFile.readText(Charsets.UTF_8).take(120_000) }
                            .getOrDefault("Unable to read text content.")
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF141920),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                ) {
                    Text(
                        text = textBody,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE6EDF3),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(item.iconEmoji, fontSize = 48.sp)
                        Text(
                            text = item.originalName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${ext.uppercase().ifBlank { "FILE" }} · ${item.formattedSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Tap Save Offline to keep this file in your vault, or Export to open it in another app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepLinkPdfPreview(pdfFile: File) {
    var pages by remember(pdfFile.absolutePath) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var failed by remember(pdfFile.absolutePath) { mutableStateOf(false) }

    LaunchedEffect(pdfFile.absolutePath) {
        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                val result = mutableListOf<Bitmap>()
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val maxPages = renderer.pageCount.coerceAtMost(20)
                        for (i in 0 until maxPages) {
                            renderer.openPage(i).use { page ->
                                val scale = (1080f / page.width.coerceAtLeast(1)).coerceIn(1f, 2f)
                                val bmp = Bitmap.createBitmap(
                                    (page.width * scale).toInt().coerceAtLeast(1),
                                    (page.height * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                result.add(bmp)
                            }
                        }
                    }
                }
                result
            }.getOrNull()
        }
        if (rendered.isNullOrEmpty()) {
            failed = true
        } else {
            pages = rendered
        }
    }

    DisposableEffect(pdfFile.absolutePath) {
        onDispose {
            pages.forEach { runCatching { it.recycle() } }
        }
    }

    if (failed) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            Text("PDF ready — tap Export to open externally", color = Color.White)
        }
    } else if (pages.isEmpty()) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(pages) { index, bmp ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "PDF page ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
