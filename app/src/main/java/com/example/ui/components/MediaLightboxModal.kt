package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.example.data.model.FolderItem
import com.example.data.model.MediaType
import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.UnifiedMediaItem
import com.example.data.remote.RetrofitClient
import com.example.ui.theme.PlayfairDisplayFontFamily
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLightboxModal(
    item: UnifiedMediaItem,
    mediaItems: List<UnifiedMediaItem> = listOf(item),
    downloadingOfflineIds: Set<String> = emptySet(),
    serverBaseUrl: String,
    folders: List<FolderItem>,
    isSlideshowPlaying: Boolean,
    onDismiss: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectPageItem: (UnifiedMediaItem) -> Unit = {},
    onToggleSlideshow: () -> Unit,
    onPauseSlideshow: (String?) -> Unit = {},
    onToggleFavorite: (UnifiedMediaItem) -> Unit = {},
    onToggleOfflinePin: (UnifiedMediaItem) -> Unit = {},
    onRecoverMissingFile: ((UnifiedMediaItem) -> Unit)? = null,
    onRenameItem: (UnifiedMediaItem, String) -> Unit = { _, _ -> },
    onUpdateTags: (UnifiedMediaItem, String) -> Unit = { _, _ -> },
    onOpenImageEditor: (UnifiedMediaItem) -> Unit,
    onMoveToFolder: (UnifiedMediaItem, String?) -> Unit,
    onOpenShareModal: (UnifiedMediaItem) -> Unit,
    onNativeShare: (UnifiedMediaItem) -> Unit,
    onSoftDelete: (UnifiedMediaItem) -> Unit,
    onScanOcrText: (UnifiedMediaItem) -> Unit = {},
    onToggleHideInVault: (UnifiedMediaItem) -> Unit = {},
    isItemHiddenInVault: Boolean = false,
    onEnterPipMode: () -> Unit = {},
    onVideoFrameGrabbed: (File) -> Unit = {},
    onTrimmedVideoSaved: (File) -> Unit = {}
) {
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var isCurrentImageZoomed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pagerItems = remember(mediaItems, item.id) {
        if (mediaItems.any { it.id == item.id }) mediaItems else listOf(item)
    }
    val initialIndex = remember(pagerItems) {
        pagerItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { pagerItems.size }
    )
    val currentItem = pagerItems.getOrNull(pagerState.currentPage) ?: item
    val isDownloadingCurrentOffline = currentItem.id in downloadingOfflineIds

    // Sync external selection (e.g. Slideshow or Prev/Next) into HorizontalPager
    LaunchedEffect(item.id, pagerItems.size) {
        val targetIdx = pagerItems.indexOfFirst { it.id == item.id }
        if (targetIdx >= 0 && targetIdx != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetIdx)
        }
    }

    // Reset zoom lock & notify ViewModel + pre-warm neighbor images in Coil on swipe
    LaunchedEffect(pagerState, pagerItems) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledIdx ->
                isCurrentImageZoomed = false
                val settledItem = pagerItems.getOrNull(settledIdx) ?: return@collect
                onSelectPageItem(settledItem)

                // Pre-warm previous and next images in Coil for zero-jank swiping
                listOf(settledIdx - 1, settledIdx + 1).forEach { neighborIdx ->
                    val neighbor = pagerItems.getOrNull(neighborIdx)
                    if (neighbor != null && neighbor.mediaType == MediaType.IMAGE) {
                        val url = neighbor.resolveMediaUrl(serverBaseUrl)
                        if (url.isNotBlank()) {
                            context.imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .size(Size(1920, 1920))
                                    .build()
                            )
                        }
                    }
                }
            }
    }

    fun triggerTactileClick(isStrong: Boolean = false) {
        if (isStrong) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    // Intercept system back button to dismiss the studio lightbox cleanly
    BackHandler(enabled = true) {
        triggerTactileClick()
        onDismiss()
    }

    // Automatic 3.5-second Slideshow Timer — pauses automatically when landing on a video
    LaunchedEffect(isSlideshowPlaying, currentItem.id, pagerState.settledPage) {
        if (!isSlideshowPlaying) return@LaunchedEffect
        val activeItem = pagerItems.getOrNull(pagerState.settledPage) ?: currentItem
        if (activeItem.mediaType == MediaType.VIDEO) {
            onPauseSlideshow("Slideshow paused for video")
            return@LaunchedEffect
        }
        if (pagerItems.size > 1) {
            delay(3500L)
            val nextPage = (pagerState.currentPage + 1) % pagerItems.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val offlineGreen = Color(0xFF2E9E6B)
    val favoriteRed = Color(0xFFE05263)
    val glassSurfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    val glassBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.22f),
            primaryColor.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        )
    )

    // Full-screen Edge-to-Edge Studio Overlay (Inherits real Activity WindowInsets so bottom buttons are never clipped)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { /* Consume background taps */ } }
            .testTag("media_lightbox_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Glassmorphic Studio Bar — Spacious two-row layout so title is never cramped
            Surface(
                color = glassSurfaceColor,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .border(
                        width = 1.dp,
                        brush = glassBorderBrush,
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Close + Tappable Title/Metadata + Favorite, Offline Pin & Info/Rename
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    triggerTactileClick()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                                    .testTag("lightbox_close_button")
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Preview",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        triggerTactileClick()
                                        showDetailsSheet = true
                                    }
                            ) {
                                Text(
                                    text = currentItem.originalName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentItem.mediaType.label} · ${currentItem.formattedSize} · ${currentItem.relativeDateLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Favorite Heart Toggle
                            IconButton(
                                onClick = {
                                    triggerTactileClick()
                                    onToggleFavorite(currentItem)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("lightbox_favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (currentItem.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (currentItem.isFavorite) "Remove from Favorites" else "Add to Favorites",
                                    tint = if (currentItem.isFavorite) favoriteRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Offline Vault Pin Toggle
                            IconButton(
                                onClick = {
                                    triggerTactileClick()
                                    onToggleOfflinePin(currentItem)
                                },
                                enabled = !isDownloadingCurrentOffline,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("lightbox_offline_pin_button")
                            ) {
                                if (isDownloadingCurrentOffline) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = offlineGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = when (currentItem.offlineState) {
                                            OfflineAvailabilityState.PINNED -> Icons.Default.OfflinePin
                                            OfflineAvailabilityState.AUTO_CACHED -> Icons.Default.CloudDone
                                            OfflineAvailabilityState.NONE -> Icons.Default.CloudDownload
                                        },
                                        contentDescription = if (currentItem.offlineState == OfflineAvailabilityState.PINNED) {
                                            "Pinned Offline (Tap to Remove)"
                                        } else {
                                            "Keep Available Offline"
                                        },
                                        tint = if (currentItem.offlineState != OfflineAvailabilityState.NONE) {
                                            offlineGreen
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // File Details & Inline Rename Sheet Trigger
                            IconButton(
                                onClick = {
                                    triggerTactileClick()
                                    showDetailsSheet = true
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("lightbox_details_button")
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "File Details and Rename",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Row 2: Pager Position & Prev/Next + Slideshow + Edit + Move to Folder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                    RoundedCornerShape(50)
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            triggerTactileClick()
                                            if (pagerItems.size > 1) {
                                                val prev = (pagerState.currentPage - 1 + pagerItems.size) % pagerItems.size
                                                coroutineScope.launch { pagerState.animateScrollToPage(prev) }
                                            } else {
                                                onPrev()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .testTag("lightbox_prev_button")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Previous Media",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${pagerItems.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            triggerTactileClick()
                                            if (pagerItems.size > 1) {
                                                val next = (pagerState.currentPage + 1) % pagerItems.size
                                                coroutineScope.launch { pagerState.animateScrollToPage(next) }
                                            } else {
                                                onNext()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .testTag("lightbox_next_button")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Next Media",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (currentItem.offlineState != OfflineAvailabilityState.NONE) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = offlineGreen.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (currentItem.offlineState == OfflineAvailabilityState.PINNED) "Offline Ready" else "Auto-Cached",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = offlineGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    triggerTactileClick()
                                    onToggleSlideshow()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("lightbox_slideshow_button")
                            ) {
                                Icon(
                                    imageVector = if (isSlideshowPlaying) Icons.Default.PauseCircle else Icons.Default.Slideshow,
                                    contentDescription = "Toggle Slideshow",
                                    tint = if (isSlideshowPlaying) primaryColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            if (currentItem.mediaType == MediaType.IMAGE) {
                                IconButton(
                                    onClick = {
                                        triggerTactileClick(isStrong = true)
                                        onOpenImageEditor(currentItem)
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("lightbox_edit_image_button")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Open Image Studio",
                                        tint = primaryColor,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            if (currentItem.mediaType == MediaType.IMAGE || currentItem.extension == "pdf") {
                                IconButton(
                                    onClick = {
                                        triggerTactileClick()
                                        onScanOcrText(currentItem)
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("lightbox_ocr_scan_button")
                                ) {
                                    Icon(
                                        Icons.Default.DocumentScanner,
                                        contentDescription = "Extract Text (On-Device OCR)",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    triggerTactileClick(isStrong = true)
                                    onToggleHideInVault(currentItem)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("lightbox_hide_vault_button")
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = if (isItemHiddenInVault) "Unhide from Private Vault" else "Hide in Private Vault",
                                    tint = if (isItemHiddenInVault) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = {
                                        triggerTactileClick()
                                        folderMenuExpanded = true
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("lightbox_move_folder_button")
                                ) {
                                    Icon(
                                        Icons.Default.DriveFileMove,
                                        contentDescription = "Move to Folder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = folderMenuExpanded,
                                    onDismissRequest = { folderMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Unfile (Root Gallery)") },
                                        onClick = {
                                            folderMenuExpanded = false
                                            onMoveToFolder(currentItem, null)
                                        }
                                    )
                                    folders.forEach { f ->
                                        DropdownMenuItem(
                                            text = { Text("Move to ${f.name}") },
                                            onClick = {
                                                folderMenuExpanded = false
                                                onMoveToFolder(currentItem, f.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Smooth Swipeable HorizontalPager Media Stage
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentImageZoomed,
                key = { idx -> pagerItems.getOrNull(idx)?.id ?: idx },
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("lightbox_media_pager")
            ) { pageIndex ->
                val pageItem = pagerItems.getOrNull(pageIndex) ?: return@HorizontalPager
                val isActivePage = pageIndex == pagerState.settledPage && !pagerState.isScrollInProgress
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val resolvedUrl = remember(pageItem.id, pageItem.offlineFilePath, pageItem.localUri, pageItem.path, serverBaseUrl) {
                        pageItem.resolveMediaUrl(serverBaseUrl)
                    }
                    val ext = pageItem.extension

                    when {
                        pageItem.mediaType == MediaType.IMAGE -> {
                            ZoomableImageViewer(
                                model = resolvedUrl,
                                contentDescription = pageItem.originalName,
                                isActivePage = isActivePage,
                                onZoomStateChanged = { zoomed ->
                                    if (pageIndex == pagerState.currentPage) {
                                        isCurrentImageZoomed = zoomed
                                    }
                                },
                                onRecoverMissingFile = onRecoverMissingFile?.let { recover ->
                                    { recover(pageItem) }
                                }
                            )
                        }

                        pageItem.mediaType == MediaType.VIDEO -> {
                            ProVideoPlayerStage(
                                videoUri = resolvedUrl,
                                displayName = pageItem.originalName,
                                isActivePage = isActivePage,
                                onEnterPipMode = onEnterPipMode,
                                onFrameGrabbed = onVideoFrameGrabbed,
                                onTrimmedClipSaved = onTrimmedVideoSaved,
                                onRecoverMissingFile = onRecoverMissingFile?.let { recover ->
                                    { recover(pageItem) }
                                }
                            )
                        }

                        ext in setOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> {
                            AudioWaveformStage(
                                audioUri = resolvedUrl,
                                displayName = pageItem.originalName,
                                isActivePage = isActivePage
                            )
                        }

                        else -> {
                            DocumentPreviewer(
                                item = pageItem,
                                serverBaseUrl = serverBaseUrl,
                                isActivePage = isActivePage,
                                onNativeShare = { onNativeShare(pageItem) }
                            )
                        }
                    }
                }
            }

            // Floating Glassmorphic Bottom Action Dock (Inset-safe + extra bottom margin so gesture bar never overlaps)
            Surface(
                color = glassSurfaceColor,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 14.dp)
                    .border(
                        width = 1.dp,
                        brush = glassBorderBrush,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    val displayTags = currentItem.effectiveTags
                    if (displayTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            displayTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = primaryColor.copy(alpha = 0.14f),
                                    modifier = Modifier.border(
                                        1.dp,
                                        primaryColor.copy(alpha = 0.35f),
                                        RoundedCornerShape(50)
                                    )
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                triggerTactileClick()
                                onOpenShareModal(currentItem)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("lightbox_share_link_button")
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Share Link",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                triggerTactileClick()
                                onNativeShare(currentItem)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("lightbox_native_share_button")
                        ) {
                            Icon(
                                Icons.Default.IosShare,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Export",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    triggerTactileClick(isStrong = true)
                                    onSoftDelete(currentItem)
                                    onDismiss()
                                }
                                .testTag("lightbox_delete_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Move to Trash",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // File Details, Metadata & Inline Rename Bottom Sheet
    if (showDetailsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var renameDraft by remember(currentItem.id, currentItem.originalName) {
            mutableStateOf(currentItem.originalName)
        }
        var tagsDraft by remember(currentItem.id, currentItem.effectiveTags) {
            mutableStateOf(currentItem.effectiveTags.joinToString(", "))
        }
        val folderName = remember(currentItem.folderId, folders) {
            folders.firstOrNull { it.id == currentItem.folderId }?.name ?: "Root Gallery"
        }

        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("lightbox_details_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "File Details & Rename",
                        fontFamily = PlayfairDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = primaryColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = currentItem.extension.uppercase().ifBlank { currentItem.mediaType.label.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Inline Rename Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("details_rename_input")
                    )
                    Button(
                        onClick = {
                            if (renameDraft.isNotBlank() && renameDraft.trim() != currentItem.originalName) {
                                onRenameItem(currentItem, renameDraft)
                            }
                        },
                        enabled = renameDraft.isNotBlank() && renameDraft.trim() != currentItem.originalName,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("details_rename_save_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                // Inline Tags Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tagsDraft,
                        onValueChange = { tagsDraft = it },
                        label = { Text("Tags (comma-separated)") },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("details_tags_input")
                    )
                    FilledTonalButton(
                        onClick = { onUpdateTags(currentItem, tagsDraft) },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("details_tags_save_button")
                    ) {
                        Text("Tags", fontWeight = FontWeight.Bold)
                    }
                }

                // Offline Vault Status Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = offlineGreen.copy(alpha = 0.09f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, offlineGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = when (currentItem.offlineState) {
                                    OfflineAvailabilityState.PINNED -> "Pinned on Device"
                                    OfflineAvailabilityState.AUTO_CACHED -> "Auto-Cached Offline"
                                    OfflineAvailabilityState.NONE -> "Cloud Only"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = offlineGreen
                            )
                            Text(
                                text = when (currentItem.offlineState) {
                                    OfflineAvailabilityState.PINNED -> "Saved permanently for instant zero-lag offline viewing."
                                    OfflineAvailabilityState.AUTO_CACHED -> "Cached temporarily. Pin to keep permanently."
                                    OfflineAvailabilityState.NONE -> "Pin this file to open it anytime without internet."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { onToggleOfflinePin(currentItem) },
                            enabled = !isDownloadingCurrentOffline,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("details_offline_toggle_button")
                        ) {
                            Text(
                                text = when {
                                    isDownloadingCurrentOffline -> "Saving…"
                                    currentItem.offlineState == OfflineAvailabilityState.PINNED -> "Unpin"
                                    else -> "Pin Offline"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Metadata Summary Grid
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetadataKeyValueRow("Media Type", currentItem.mediaType.label)
                        MetadataKeyValueRow("File Size", currentItem.formattedSize)
                        MetadataKeyValueRow("Folder", folderName)
                        MetadataKeyValueRow("Added", currentItem.relativeDateLabel)
                        currentItem.formattedDuration?.let { dur ->
                            MetadataKeyValueRow("Duration", dur)
                        }
                        MetadataKeyValueRow("Favorite", if (currentItem.isFavorite) "Yes" else "No")
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun MetadataKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImageViewer(
    model: Any?,
    contentDescription: String,
    isActivePage: Boolean = true,
    onZoomStateChanged: (Boolean) -> Unit = {},
    onRecoverMissingFile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var isLoading by remember(model) { mutableStateOf(true) }
    var loadFailed by remember(model) { mutableStateOf(false) }
    var retryNonce by remember(model) { mutableIntStateOf(0) }

    // Reset zoom and pan cleanly when swiping away from this page
    LaunchedEffect(isActivePage) {
        if (!isActivePage && (scale > 1.01f || offset != Offset.Zero)) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    LaunchedEffect(scale, isActivePage) {
        if (isActivePage) {
            onZoomStateChanged(scale > 1.02f)
        }
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4.5f)
        offset = if (scale > 1f) {
            offset + offsetChange
        } else {
            Offset.Zero
        }
    }

    val imageRequest = remember(model, retryNonce) {
        ImageRequest.Builder(context)
            .data(model)
            .setParameter("retry_nonce", retryNonce, memoryCacheKey = null)
            .size(Size(1920, 1920))
            .crossfade(160)
            .build()
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        modifier = Modifier
            .fillMaxSize()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(model) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.4f
                            }
                        }
                    )
                }
                .then(
                    if (scale > 1.02f) {
                        Modifier.transformable(state = transformState)
                    } else {
                        Modifier.transformable(state = transformState, canPan = { false })
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                onLoading = {
                    isLoading = true
                    loadFailed = false
                },
                onSuccess = {
                    isLoading = false
                    loadFailed = false
                },
                onError = {
                    isLoading = false
                    loadFailed = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            if (loadFailed && !isLoading) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp)
                        )
                        .testTag("image_viewer_error_card")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Preview unavailable",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Image Preview Unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Could not load \"$contentDescription\". Check your connection or re-link the file from your device if it was cleared from the server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    loadFailed = false
                                    isLoading = true
                                    retryNonce++
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("image_viewer_retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                            if (onRecoverMissingFile != null) {
                                Button(
                                    onClick = onRecoverMissingFile,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("image_viewer_relink_button")
                                ) {
                                    Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Re-link")
                                }
                            }
                        }
                    }
                }
            }

            // Subtle Zoom Badge when zoomed in
            AnimatedVisibility(
                visible = scale > 1.05f && !loadFailed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .clickable {
                            scale = 1f
                            offset = Offset.Zero
                        }
                ) {
                    Text(
                        text = "${(scale * 100).toInt()}% · Tap to Reset",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerStage(
    videoUri: String,
    displayName: String,
    isActivePage: Boolean,
    onRecoverMissingFile: (() -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestIsActive by rememberUpdatedState(isActivePage)

    var hasActivated by remember(videoUri) { mutableStateOf(false) }
    var isPrepared by remember(videoUri) { mutableStateOf(false) }
    var errorMessage by remember(videoUri) { mutableStateOf<String?>(null) }
    var retryKey by remember(videoUri) { mutableIntStateOf(0) }
    var savedPositionMs by remember(videoUri) { mutableIntStateOf(0) }
    var videoViewRef by remember(videoUri, retryKey) { mutableStateOf<VideoView?>(null) }
    var mediaControllerRef by remember(videoUri, retryKey) { mutableStateOf<MediaController?>(null) }

    // Never prepare or stream off-screen neighbor videos until the user actually lands on this page
    LaunchedEffect(isActivePage) {
        if (isActivePage && !hasActivated) {
            hasActivated = true
        }
    }

    // Pause immediately on swipe-away; auto-play only while this page is active and settled
    LaunchedEffect(isActivePage, isPrepared, videoViewRef) {
        val vv = videoViewRef ?: return@LaunchedEffect
        if (isActivePage) {
            if (isPrepared && errorMessage == null && !vv.isPlaying) {
                if (savedPositionMs > 0) {
                    runCatching { vv.seekTo(savedPositionMs) }
                }
                runCatching { vv.start() }
            }
        } else {
            runCatching {
                mediaControllerRef?.hide()
                if (vv.isPlaying) {
                    savedPositionMs = vv.currentPosition
                    vv.pause()
                }
            }
        }
    }

    // Pause on app background / screen lock; clean up on dispose
    DisposableEffect(lifecycleOwner, videoViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            val vv = videoViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    runCatching {
                        mediaControllerRef?.hide()
                        if (vv.isPlaying) {
                            savedPositionMs = vv.currentPosition
                            vv.pause()
                        }
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (latestIsActive && isPrepared && errorMessage == null && !vv.isPlaying) {
                        runCatching {
                            if (savedPositionMs > 0) vv.seekTo(savedPositionMs)
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
                mediaControllerRef?.hide()
                videoViewRef?.pause()
                videoViewRef?.stopPlayback()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0C1016),
            modifier = Modifier
                .fillMaxWidth()
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        errorMessage = null
                                        isPrepared = false
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

                    !hasActivated -> {
                        // Idle standby placeholder while off-screen in HorizontalPager
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
                        androidx.compose.runtime.key(videoUri, retryKey) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val controller = MediaController(ctx)
                                        controller.setAnchorView(this)
                                        mediaControllerRef = controller
                                        setMediaController(controller)

                                        val parsedUri = Uri.parse(videoUri)
                                        val headers = RetrofitClient.buildAuthHeadersForUrl(videoUri)
                                        if (headers.isNotEmpty() && (parsedUri.scheme == "http" || parsedUri.scheme == "https")) {
                                            setVideoURI(parsedUri, headers)
                                        } else {
                                            setVideoURI(parsedUri)
                                        }

                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            isPrepared = true
                                            if (savedPositionMs > 0) {
                                                runCatching { seekTo(savedPositionMs) }
                                            }
                                            if (latestIsActive) {
                                                start()
                                            } else {
                                                pause()
                                            }
                                        }

                                        setOnErrorListener { _, _, _ ->
                                            isPrepared = false
                                            errorMessage = "Could not load \"$displayName\". Check your connection or re-link it from your device."
                                            true
                                        }

                                        videoViewRef = this
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (!isPrepared) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AudioWaveformStage(
    audioUri: String,
    displayName: String,
    isActivePage: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestIsActive by rememberUpdatedState(isActivePage)

    var hasActivated by remember(audioUri) { mutableStateOf(false) }
    var isPrepared by remember(audioUri) { mutableStateOf(false) }
    var isPlaying by remember(audioUri) { mutableStateOf(false) }
    var userPaused by remember(audioUri) { mutableStateOf(false) }
    var durationMs by remember(audioUri) { mutableIntStateOf(0) }
    var positionMs by remember(audioUri) { mutableIntStateOf(0) }
    var errorMessage by remember(audioUri) { mutableStateOf<String?>(null) }
    var retryKey by remember(audioUri) { mutableIntStateOf(0) }
    var phase by remember { mutableFloatStateOf(0f) }
    var mediaPlayerRef by remember(audioUri, retryKey) { mutableStateOf<MediaPlayer?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isActivePage) {
        if (isActivePage && !hasActivated) {
            hasActivated = true
        }
    }

    // Create & prepare real MediaPlayer only after page becomes active
    DisposableEffect(audioUri, hasActivated, retryKey) {
        if (!hasActivated || audioUri.isBlank()) {
            return@DisposableEffect onDispose { }
        }
        val mp = MediaPlayer()
        mediaPlayerRef = mp
        isPrepared = false
        errorMessage = null

        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            val parsedUri = Uri.parse(audioUri)
            val headers = RetrofitClient.buildAuthHeadersForUrl(audioUri)
            if (headers.isNotEmpty() && (parsedUri.scheme == "http" || parsedUri.scheme == "https")) {
                mp.setDataSource(context, parsedUri, headers)
            } else {
                mp.setDataSource(context, parsedUri)
            }
            mp.isLooping = false
            mp.setOnPreparedListener { player ->
                isPrepared = true
                durationMs = player.duration.coerceAtLeast(0)
                if (latestIsActive && !userPaused) {
                    runCatching {
                        player.start()
                        isPlaying = true
                    }
                }
            }
            mp.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs
            }
            mp.setOnErrorListener { _, _, _ ->
                isPrepared = false
                isPlaying = false
                errorMessage = "Could not play \"$displayName\"."
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Audio format unavailable."
            runCatching { mp.release() }
            mediaPlayerRef = null
        }

        onDispose {
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
            if (mediaPlayerRef === mp) {
                mediaPlayerRef = null
            }
            isPlaying = false
            isPrepared = false
        }
    }

    // Sync playback strictly to active pager page
    LaunchedEffect(isActivePage, isPrepared, mediaPlayerRef) {
        val mp = mediaPlayerRef ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        if (!isActivePage) {
            runCatching {
                if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                }
            }
        } else if (!userPaused && !mp.isPlaying) {
            runCatching {
                mp.start()
                isPlaying = true
            }
        }
    }

    // Pause audio on background / screen off
    DisposableEffect(lifecycleOwner, mediaPlayerRef) {
        val observer = LifecycleEventObserver { _, event ->
            val mp = mediaPlayerRef ?: return@LifecycleEventObserver
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                runCatching {
                    if (mp.isPlaying) {
                        mp.pause()
                        isPlaying = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Animate waveform & poll playback position only while actively playing
    LaunchedEffect(isPlaying, isActivePage, mediaPlayerRef) {
        while (isActive && isPlaying && isActivePage) {
            phase += 0.16f
            mediaPlayerRef?.let { mp ->
                runCatching {
                    if (mp.isPlaying) {
                        positionMs = mp.currentPosition
                    }
                }
            }
            delay(60L)
        }
    }

    fun formatDurationMs(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "%d:%02d".format(mins, secs)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Studio Audio Player",
                style = MaterialTheme.typography.labelLarge,
                color = primaryColor
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                val width = size.width
                val midY = size.height / 2f
                val path = Path()
                path.moveTo(0f, midY)
                val steps = 80
                val ampScale = if (isPlaying && isActivePage) 0.35f else 0.06f
                for (i in 0..steps) {
                    val x = (i.toFloat() / steps) * width
                    val amp = sin(i * 0.35f + phase) * (size.height * ampScale) *
                        sin((i.toFloat() / steps) * Math.PI.toFloat())
                    path.lineTo(x, midY + amp)
                }
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 6f)
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        retryKey++
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry Audio")
                }
            } else {
                if (durationMs > 0) {
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                        onValueChange = { newVal ->
                            val target = newVal.toInt()
                            positionMs = target
                            runCatching { mediaPlayerRef?.seekTo(target) }
                        },
                        valueRange = 0f..durationMs.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDurationMs(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        val mp = mediaPlayerRef
                        if (mp != null && isPrepared) {
                            runCatching {
                                if (mp.isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                    userPaused = true
                                } else {
                                    if (positionMs >= durationMs && durationMs > 0) {
                                        mp.seekTo(0)
                                        positionMs = 0
                                    }
                                    mp.start()
                                    isPlaying = true
                                    userPaused = false
                                }
                            }
                        }
                    },
                    enabled = isPrepared,
                    modifier = Modifier.testTag("audio_player_toggle_button")
                ) {
                    if (!isPrepared) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Loading Audio…")
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPlaying) "Pause Playback" else "Play Audio")
                    }
                }
            }
        }
    }
}

// ============================================================================
// WEBVIEW-POWERED `DocumentPreviewer` COMPONENT FOR PDFs & OFFICE FILES
// ============================================================================

private enum class WebViewThemeTone(val label: String, val cssClass: String) {
    STUDIO_DARK("Studio Dark", "theme-dark"),
    WARM_PAPER("Warm Paper", "theme-paper"),
    SEPIA("Sepia", "theme-sepia")
}

private sealed class WebDocumentPayload {
    data object Loading : WebDocumentPayload()
    data class Ready(
        val htmlContent: String,
        val summaryBadge: String,
        val remoteGoogleViewerUrl: String?
    ) : WebDocumentPayload()

    data class Error(val message: String) : WebDocumentPayload()
}

/**
 * Dedicated `DocumentPreviewer` component that uses an Android `WebView` to render
 * PDFs, Word (`.docx`, `.doc`, `.rtf`), Excel/CSV (`.xlsx`, `.csv`, `.tsv`),
 * PowerPoint (`.pptx`), archives (`.zip`), and source/text files directly within the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocumentPreviewer(
    item: UnifiedMediaItem,
    serverBaseUrl: String,
    isActivePage: Boolean = true,
    onNativeShare: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    var hasActivated by remember(item.id) { mutableStateOf(isActivePage) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var themeTone by remember { mutableStateOf(WebViewThemeTone.WARM_PAPER) }
    var zoomPercent by remember { mutableIntStateOf(100) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebViewPageLoading by remember { mutableStateOf(false) }

    var payloadState by remember(item.id, reloadKey) {
        mutableStateOf<WebDocumentPayload>(WebDocumentPayload.Loading)
    }

    fun triggerHaptic() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    LaunchedEffect(isActivePage) {
        if (isActivePage && !hasActivated) {
            hasActivated = true
        }
    }

    // Defer heavy document download & PDF/Office rendering until this page is actually active
    LaunchedEffect(item.id, reloadKey, hasActivated) {
        if (!hasActivated) return@LaunchedEffect
        payloadState = WebDocumentPayload.Loading
        payloadState = withContext(Dispatchers.IO) {
            buildWebViewDocumentPayload(
                context = context,
                item = item,
                serverBaseUrl = serverBaseUrl
            )
        }
    }

    // Pause/resume WebView with Activity lifecycle and destroy cleanly on dispose to prevent memory leaks
    DisposableEffect(lifecycleOwner, webViewRef) {
        val wv = webViewRef
        val observer = LifecycleEventObserver { _, event ->
            if (wv == null) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> runCatching { wv.onPause() }
                Lifecycle.Event.ON_RESUME -> runCatching { wv.onResume() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (wv != null) {
                runCatching {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                }
                if (webViewRef === wv) {
                    webViewRef = null
                }
            }
        }
    }

    // Dynamically update WebView zoom & reading theme via JavaScript without reloading the whole document
    LaunchedEffect(zoomPercent, themeTone, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.evaluateJavascript(
            "if(window.applyStudioSettings){window.applyStudioSettings('${themeTone.cssClass}', $zoomPercent);}",
            null
        )
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .testTag("document_previewer_webview_card")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // DocumentPreviewer Header Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (item.extension.equals("pdf", ignoreCase = true)) {
                                        Icons.Default.PictureAsPdf
                                    } else {
                                        Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val badgeText = when (val st = payloadState) {
                                is WebDocumentPayload.Ready -> st.summaryBadge
                                is WebDocumentPayload.Loading -> "Preparing WebView Engine…"
                                is WebDocumentPayload.Error -> "Preview Unavailable"
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "In-App Studio WebView · ${themeTone.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Zoom, Reading Tone & Engine Mode Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                triggerHaptic()
                                zoomPercent = (zoomPercent - 20).coerceAtLeast(80)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("doc_previewer_zoom_out")
                        ) {
                            Icon(
                                Icons.Default.ZoomOut,
                                contentDescription = "Zoom Out",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    triggerHaptic()
                                    zoomPercent = when (zoomPercent) {
                                        100 -> 125
                                        125 -> 150
                                        else -> 100
                                    }
                                }
                                .testTag("doc_previewer_zoom_badge")
                        ) {
                            Text(
                                text = "$zoomPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                triggerHaptic()
                                zoomPercent = (zoomPercent + 20).coerceAtMost(200)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("doc_previewer_zoom_in")
                        ) {
                            Icon(
                                Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                triggerHaptic()
                                themeTone = when (themeTone) {
                                    WebViewThemeTone.WARM_PAPER -> WebViewThemeTone.SEPIA
                                    WebViewThemeTone.SEPIA -> WebViewThemeTone.STUDIO_DARK
                                    WebViewThemeTone.STUDIO_DARK -> WebViewThemeTone.WARM_PAPER
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("doc_previewer_tone_button")
                        ) {
                            Icon(
                                Icons.Default.Contrast,
                                contentDescription = "Switch Reading Tone",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isWebViewPageLoading) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            }

            // Main WebView Stage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = payloadState) {
                    is WebDocumentPayload.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Rendering ${item.extension.uppercase().ifBlank { "Document" }} in WebView…",
                                fontFamily = PlayfairDisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.originalName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    is WebDocumentPayload.Ready -> {
                        val targetModeKey = remember(item.id, reloadKey) {
                            "${item.id}_${reloadKey}"
                        }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = false
                                        allowFileAccess = true
                                        allowContentAccess = true
                                        blockNetworkLoads = true
                                        blockNetworkImage = true
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        cacheMode = WebSettings.LOAD_NO_CACHE
                                    }
                                    webChromeClient = WebChromeClient()
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?
                                        ) {
                                            isWebViewPageLoading = true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isWebViewPageLoading = false
                                            view?.evaluateJavascript(
                                                "if(window.applyStudioSettings){window.applyStudioSettings('${themeTone.cssClass}', $zoomPercent);}",
                                                null
                                            )
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val scheme = request?.url?.scheme?.lowercase().orEmpty()
                                            if (scheme == "http" || scheme == "https") {
                                                return WebResourceResponse(
                                                    "text/plain",
                                                    "UTF-8",
                                                    ByteArrayInputStream(ByteArray(0))
                                                )
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            return true
                                        }
                                    }
                                    webViewRef = this
                                }
                            },
                            update = { webView ->
                                if (webView.tag != targetModeKey) {
                                    webView.tag = targetModeKey
                                    webView.loadDataWithBaseURL(
                                        "about:blank",
                                        state.htmlContent,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("document_previewer_webview")
                        )
                    }

                    is WebDocumentPayload.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = item.originalName,
                                fontFamily = PlayfairDisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { reloadKey++ },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("doc_reader_retry_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry")
                                }
                                Button(
                                    onClick = onNativeShare,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("doc_reader_external_button")
                                ) {
                                    Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open External")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// WEBVIEW HTML5 DOCUMENT GENERATION ENGINE (PDF, DOCX, XLSX, PPTX, CSV, ZIP, CODE)
// ============================================================================

private fun buildWebViewDocumentPayload(
    context: Context,
    item: UnifiedMediaItem,
    serverBaseUrl: String
): WebDocumentPayload {
    val ext = item.extension.lowercase()
    val cachedFile = resolveOrDownloadDocumentToCache(
        context = context,
        item = item,
        serverBaseUrl = serverBaseUrl
    )

    if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
        val offlineSummaryHtml = wrapStudioWebViewHtml(
            title = item.originalName,
            subtitle = "${ext.uppercase().ifBlank { "DOCUMENT" }} · ${item.formattedSize} · ${item.relativeDateLabel}",
            bodyHtml = """
                <div class="doc-card">
                    <div class="slide-badge">${escapeHtml(ext.uppercase().ifBlank { "DOCUMENT" })} ARCHIVE RECORD</div>
                    <h2>${escapeHtml(item.originalName)}</h2>
                    <p>This cloud record is indexed in your studio archive (<strong>${escapeHtml(item.formattedSize)}</strong>), but its original binary file is not cached on this device yet.</p>
                    <table>
                        <tbody>
                            <tr><th>File Name</th><td>${escapeHtml(item.originalName)}</td></tr>
                            <tr><th>Format</th><td>${escapeHtml(ext.uppercase().ifBlank { "Document" })}</td></tr>
                            <tr><th>Size</th><td>${escapeHtml(item.formattedSize)}</td></tr>
                            <tr><th>Uploaded</th><td>${escapeHtml(item.relativeDateLabel)}</td></tr>
                            <tr><th>Tags</th><td>${escapeHtml(item.effectiveTags.joinToString(", ") { "#$it" })}</td></tr>
                        </tbody>
                    </table>
                </div>
            """.trimIndent()
        )
        return WebDocumentPayload.Ready(
            htmlContent = offlineSummaryHtml,
            summaryBadge = "${ext.uppercase().ifBlank { "DOC" }} Inspector · ${item.formattedSize}",
            remoteGoogleViewerUrl = null
        )
    }

    return try {
        when {
            ext == "pdf" || item.docType.orEmpty().contains("pdf", ignoreCase = true) -> {
                buildPdfWebViewHtml(cachedFile, item, null)
            }

            ext == "docx" -> {
                buildDocxWebViewHtml(cachedFile, item, null)
            }

            ext in setOf("doc", "rtf", "odt") -> {
                buildLegacyWordWebViewHtml(cachedFile, item, ext.uppercase(), null)
            }

            ext == "xlsx" -> {
                buildXlsxWebViewHtml(cachedFile, item, null)
            }

            ext in setOf("csv", "tsv") -> {
                buildCsvWebViewHtml(cachedFile, item, isTab = ext == "tsv", null)
            }

            ext == "pptx" -> {
                buildPptxWebViewHtml(cachedFile, item, null)
            }

            ext == "zip" -> {
                buildZipWebViewHtml(cachedFile, item)
            }

            else -> {
                buildTextCodeWebViewHtml(cachedFile, item, ext.uppercase().ifBlank { "TEXT" })
            }
        }
    } catch (e: Exception) {
        WebDocumentPayload.Error(
            "Could not render \"${item.originalName}\" (${e.localizedMessage ?: "format error"})."
        )
    }
}

private fun buildPdfWebViewHtml(
    file: File,
    item: UnifiedMediaItem,
    googleViewerUrl: String?
): WebDocumentPayload {
    val pagesHtml = StringBuilder()
    var totalPages = 0

    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            totalPages = renderer.pageCount
            val maxPagesToRender = totalPages.coerceAtMost(25)
            for (pageIdx in 0 until maxPagesToRender) {
                renderer.openPage(pageIdx).use { page ->
                    val targetWidth = 1080
                    val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                    val targetHeight = (page.height * scale).toInt().coerceIn(360, 2200)
                    val bitmap = Bitmap.createBitmap(
                        targetWidth,
                        targetHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 92, baos)
                    bitmap.recycle()
                    val base64Png = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                    pagesHtml.append(
                        """
                        <div class="pdf-page-card" id="page-${pageIdx + 1}">
                            <div class="pdf-page-header">
                                <span>PAGE ${pageIdx + 1} OF $totalPages</span>
                                <span>${escapeHtml(item.originalName)}</span>
                            </div>
                            <img class="pdf-page-img" src="data:image/png;base64,$base64Png" alt="Page ${pageIdx + 1}" />
                        </div>
                        """.trimIndent()
                    )
                }
            }
        }
    }

    if (totalPages <= 0) {
        return WebDocumentPayload.Error("This PDF contains 0 renderable pages.")
    }

    val jumpPills = if (totalPages > 1) {
        val links = (1..totalPages.coerceAtMost(25)).joinToString("") { p ->
            """<a class="page-pill" href="#page-$p">Page $p</a>"""
        }
        """<div class="pill-strip">$links</div>"""
    } else {
        ""
    }

    val html = wrapStudioWebViewHtml(
        title = item.originalName,
        subtitle = "PDF Document · $totalPages ${if (totalPages == 1) "Page" else "Pages"} · ${item.formattedSize}",
        bodyHtml = jumpPills + pagesHtml.toString()
    )

    return WebDocumentPayload.Ready(
        htmlContent = html,
        summaryBadge = "PDF Reader · $totalPages ${if (totalPages == 1) "page" else "pages"}",
        remoteGoogleViewerUrl = googleViewerUrl
    )
}

private fun buildDocxWebViewHtml(
    file: File,
    item: UnifiedMediaItem,
    googleViewerUrl: String?
): WebDocumentPayload {
    val bodyBuilder = StringBuilder()
    var paragraphCount = 0
    var wordCount = 0

    ZipInputStream(file.inputStream().buffered()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(zis, "UTF-8")
                var eventType = parser.eventType
                val currentParagraph = StringBuilder()
                var currentIsHeading = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tagName = parser.name?.substringAfterLast(':').orEmpty()
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (tagName == "p") {
                                currentParagraph.clear()
                                currentIsHeading = false
                            } else if (tagName == "pStyle") {
                                val styleVal = parser.getAttributeValue(null, "w:val")
                                    ?: parser.getAttributeValue(null, "val").orEmpty()
                                if (styleVal.contains("Heading", ignoreCase = true) ||
                                    styleVal.contains("Title", ignoreCase = true)
                                ) {
                                    currentIsHeading = true
                                }
                            } else if (tagName == "tab") {
                                currentParagraph.append("    ")
                            } else if (tagName == "br") {
                                currentParagraph.append("\n")
                            }
                        }

                        XmlPullParser.TEXT -> {
                            currentParagraph.append(parser.text.orEmpty())
                        }

                        XmlPullParser.END_TAG -> {
                            if (tagName == "p") {
                                val cleaned = currentParagraph.toString().trim()
                                if (cleaned.isNotEmpty()) {
                                    paragraphCount++
                                    wordCount += cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
                                    if (currentIsHeading) {
                                        bodyBuilder.append("<h2>${escapeHtml(cleaned)}</h2>")
                                    } else {
                                        bodyBuilder.append("<p>${escapeHtml(cleaned)}</p>")
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                break
            }
            entry = zis.nextEntry
        }
    }

    if (paragraphCount == 0) {
        bodyBuilder.append("<p>This Word document contains no extractable text paragraphs.</p>")
    }

    val html = wrapStudioWebViewHtml(
        title = item.originalName,
        subtitle = "Word DOCX · $wordCount words · $paragraphCount paragraphs",
        bodyHtml = """<div class="doc-card">$bodyBuilder</div>"""
    )

    return WebDocumentPayload.Ready(
        htmlContent = html,
        summaryBadge = "Word DOCX · $wordCount words",
        remoteGoogleViewerUrl = googleViewerUrl
    )
}

private fun buildLegacyWordWebViewHtml(
    file: File,
    item: UnifiedMediaItem,
    label: String,
    googleViewerUrl: String?
): WebDocumentPayload {
    val rawString = String(file.readBytes(), Charset.forName("ISO-8859-1"))
    val chunks = Regex("[\\x20-\\x7E]{5,}")
        .findAll(rawString)
        .map { it.value.trim() }
        .filter { line ->
            line.length >= 5 &&
                !line.startsWith("{\\rtf") &&
                !line.startsWith("\\fonttbl") &&
                !line.contains("Normal.dotm") &&
                !line.contains("Microsoft Office Word")
        }
        .take(400)
        .toList()

    val paragraphsHtml = if (chunks.isNotEmpty()) {
        chunks.joinToString("") { "<p>${escapeHtml(it)}</p>" }
    } else {
        "<p>${escapeHtml(file.readText(Charset.forName("UTF-8")).take(50_000))}</p>"
    }

    val html = wrapStudioWebViewHtml(
        title = item.originalName,
        subtitle = "Office $label Document · ${item.formattedSize}",
        bodyHtml = """<div class="doc-card">$paragraphsHtml</div>"""
    )

    return WebDocumentPayload.Ready(
        htmlContent = html,
        summaryBadge = "Office $label · ${chunks.size} blocks",
        remoteGoogleViewerUrl = googleViewerUrl
    )
}

private fun buildXlsxWebViewHtml(
    file: File,
    item: UnifiedMediaItem,
    googleViewerUrl: String?
): WebDocumentPayload {
    val sharedStrings = mutableListOf<String>()
    val sheetRows = mutableListOf<List<String>>()

    ZipInputStream(file.inputStream().buffered()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/sharedStrings.xml") {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(zis, "UTF-8")
                var event = parser.eventType
                val currentText = StringBuilder()
                while (event != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.substringAfterLast(':').orEmpty()
                    if (event == XmlPullParser.START_TAG && tag == "si") {
                        currentText.clear()
                    } else if (event == XmlPullParser.TEXT) {
                        currentText.append(parser.text.orEmpty())
                    } else if (event == XmlPullParser.END_TAG && tag == "si") {
                        sharedStrings.add(currentText.toString())
                    }
                    event = parser.next()
                }
                break
            }
            entry = zis.nextEntry
        }
    }

    ZipInputStream(file.inputStream().buffered()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(zis, "UTF-8")
                var event = parser.eventType
                var currentRow = mutableListOf<String>()
                var cellType = ""
                val cellVal = StringBuilder()

                while (event != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.substringAfterLast(':').orEmpty()
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            if (tag == "row") currentRow = mutableListOf()
                            else if (tag == "c") {
                                cellType = parser.getAttributeValue(null, "t").orEmpty()
                                cellVal.clear()
                            }
                        }

                        XmlPullParser.TEXT -> cellVal.append(parser.text.orEmpty())
                        XmlPullParser.END_TAG -> {
                            if (tag == "c") {
                                val raw = cellVal.toString().trim()
                                val resolved = if (cellType == "s") {
                                    sharedStrings.getOrNull(raw.toIntOrNull() ?: -1) ?: raw
                                } else {
                                    raw
                                }
                                currentRow.add(resolved)
                            } else if (tag == "row" && currentRow.any { it.isNotBlank() }) {
                                sheetRows.add(currentRow)
                            }
                        }
                    }
                    event = parser.next()
                }
                break
            }
            entry = zis.nextEntry
        }
    }

    val colCount = sheetRows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 4
    val headers = (0 until colCount).map { idx -> ('A' + (idx % 26)).toString() }
    val tableHtml = buildSpreadsheetTableHtml(headers, sheetRows.take(250))

    return WebDocumentPayload.Ready(
        htmlContent = wrapStudioWebViewHtml(
            title = item.originalName,
            subtitle = "Excel XLSX · ${sheetRows.size} rows × $colCount cols",
            bodyHtml = tableHtml
        ),
        summaryBadge = "Excel XLSX · ${sheetRows.size} rows",
        remoteGoogleViewerUrl = googleViewerUrl
    )
}

private fun buildCsvWebViewHtml(
    file: File,
    item: UnifiedMediaItem,
    isTab: Boolean,
    googleViewerUrl: String?
): WebDocumentPayload {
    val delimiter = if (isTab) '\t' else ','
    val lines = file.readLines(Charset.forName("UTF-8"))
        .filter { it.isNotBlank() }
        .take(250)
    val parsed = lines.map { line ->
        line.split(delimiter).map { it.trim().removeSurrounding("\"") }
    }
    val headers = parsed.firstOrNull() ?: listOf("Column A")
    val rows = if (parsed.size > 1) parsed.drop(1) else emptyList()
    val label = if (isTab) "TSV" else "CSV"

    return WebDocumentPayload.Ready(
        htmlContent = wrapStudioWebViewHtml(
            title = item.originalName,
            subtitle = "$label Spreadsheet · ${rows.size} rows",
            bodyHtml = buildSpreadsheetTableHtml(headers, rows)
        ),
        summaryBadge = "$label Table · ${rows.size} rows",
        remoteGoogleViewerUrl = googleViewerUrl
    )
}

private fun buildSpreadsheetTableHtml(
    headers: List<String>,
    rows: List<List<String>>
): String {
    val sb = StringBuilder()
    sb.append("""<div class="table-wrap"><table><thead><tr><th>#</th>""")
    headers.forEach { h -> sb.append("<th>${escapeHtml(h)}</th>") }
    sb.append("</tr></thead><tbody>")
    rows.forEachIndexed { rIdx, rCells ->
        sb.append("<tr><td class=\"row-num\">${rIdx + 1}</td>")
        for (cIdx in headers.indices) {
            sb.append("<td>${escapeHtml(rCells.getOrNull(cIdx).orEmpty())}</td>")
        }
        sb.append("</tr>")
    }
    sb.append("</tbody></table></div>")
    return sb.toString()
}

private fun buildPptxWebViewHtml(
    file: File,
    item: UnifiedMediaItem,
    googleViewerUrl: String?
): WebDocumentPayload {
    val slides = mutableListOf<Pair<Int, List<String>>>()
    ZipInputStream(file.inputStream().buffered()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                val slideNum = entry.name
                    .substringAfter("slide")
                    .substringBefore(".xml")
                    .toIntOrNull() ?: (slides.size + 1)
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(zis, "UTF-8")
                var event = parser.eventType
                val runs = mutableListOf<String>()
                val current = StringBuilder()
                while (event != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.substringAfterLast(':').orEmpty()
                    if (event == XmlPullParser.START_TAG && tag == "p") current.clear()
                    else if (event == XmlPullParser.TEXT) current.append(parser.text.orEmpty())
                    else if (event == XmlPullParser.END_TAG && tag == "p") {
                        val cleaned = current.toString().trim()
                        if (cleaned.isNotEmpty()) runs.add(cleaned)
                    }
                    event = parser.next()
                }
                slides.add(slideNum to runs)
            }
            entry = zis.nextEntry
        }
    }

    val sortedSlides = slides.sortedBy { it.first }
    val cardsHtml = sortedSlides.joinToString("") { (num, runs) ->
        val title = escapeHtml(runs.firstOrNull() ?: "Slide $num")
        val bullets = runs.drop(1).joinToString("") { "<li>${escapeHtml(it)}</li>" }
        """
        <div class="doc-card slide-card">
            <div class="slide-badge">SLIDE $num</div>
            <h2>$title</h2>
            ${if (bullets.isNotEmpty()) "<ul>$bullets</ul>" else ""}
        </div>
        """.trimIndent()
    }

    return WebDocumentPayload.Ready(
        htmlContent = wrapStudioWebViewHtml(
            title = item.originalName,
            subtitle = "PowerPoint PPTX · ${sortedSlides.size} slides",
            bodyHtml = cardsHtml.ifBlank { "<div class=\"doc-card\"><p>No slide text extracted.</p></div>" }
        ),
        summaryBadge = "PowerPoint · ${sortedSlides.size} slides",
        remoteGoogleViewerUrl = googleViewerUrl
    )
}

private fun buildZipWebViewHtml(
    file: File,
    item: UnifiedMediaItem
): WebDocumentPayload {
    val rows = StringBuilder()
    var count = 0
    ZipInputStream(file.inputStream().buffered()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null && count < 300) {
            count++
            val sizeKb = (entry.size.coerceAtLeast(0L) / 1024).coerceAtLeast(1)
            rows.append(
                "<tr><td class=\"row-num\">$count</td><td>${escapeHtml(entry.name)}</td><td>${if (entry.isDirectory) "Folder" else "$sizeKb KB"}</td></tr>"
            )
            entry = zis.nextEntry
        }
    }

    val body = """
        <div class="table-wrap">
            <table>
                <thead><tr><th>#</th><th>Archive Entry Path</th><th>Size</th></tr></thead>
                <tbody>$rows</tbody>
            </table>
        </div>
    """.trimIndent()

    return WebDocumentPayload.Ready(
        htmlContent = wrapStudioWebViewHtml(
            title = item.originalName,
            subtitle = "ZIP Archive · $count entries",
            bodyHtml = body
        ),
        summaryBadge = "ZIP Archive · $count files",
        remoteGoogleViewerUrl = null
    )
}

private fun buildTextCodeWebViewHtml(
    file: File,
    item: UnifiedMediaItem,
    formatLabel: String
): WebDocumentPayload {
    val lines = file.readText(Charset.forName("UTF-8")).take(100_000).lines()
    val codeRows = lines.mapIndexed { idx, line ->
        """<div class="code-line"><span class="ln">${idx + 1}</span><span class="lc">${escapeHtml(line.ifEmpty { " " })}</span></div>"""
    }.joinToString("")

    return WebDocumentPayload.Ready(
        htmlContent = wrapStudioWebViewHtml(
            title = item.originalName,
            subtitle = "$formatLabel Source · ${lines.size} lines",
            bodyHtml = """<div class="doc-card code-card">$codeRows</div>"""
        ),
        summaryBadge = "$formatLabel · ${lines.size} lines",
        remoteGoogleViewerUrl = null
    )
}

private fun wrapStudioWebViewHtml(
    title: String,
    subtitle: String,
    bodyHtml: String
): String {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes" />
            <link rel="icon" href="data:," />
            <title>${escapeHtml(title)}</title>
            <style>
                :root {
                    --bg: #FAF7F2;
                    --card: #FFFFFF;
                    --text: #1A1917;
                    --muted: #6E6A63;
                    --accent: #D96B43;
                    --border: #E5DEC9;
                }
                body.theme-paper {
                    --bg: #FAF7F2;
                    --card: #FFFFFF;
                    --text: #1A1917;
                    --muted: #6E6A63;
                    --accent: #D96B43;
                    --border: #E5DEC9;
                }
                body.theme-sepia {
                    --bg: #F3EAD8;
                    --card: #FAF3E6;
                    --text: #2C241B;
                    --muted: #766552;
                    --accent: #B85630;
                    --border: #DECGB0;
                }
                body.theme-dark {
                    --bg: #111114;
                    --card: #1A1A1F;
                    --text: #F4EFE6;
                    --muted: #A09A90;
                    --accent: #E2825C;
                    --border: #2C2C35;
                }
                * { box-sizing: border-box; }
                body {
                    margin: 0;
                    padding: 14px;
                    background: var(--bg);
                    color: var(--text);
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    line-height: 1.65;
                    transition: background 0.2s ease, color 0.2s ease;
                }
                #studio-root {
                    transform-origin: top left;
                }
                .doc-banner {
                    background: var(--card);
                    border: 1px solid var(--border);
                    border-radius: 16px;
                    padding: 14px 16px;
                    margin-bottom: 14px;
                    box-shadow: 0 4px 14px rgba(0,0,0,0.05);
                }
                .doc-banner h1 {
                    margin: 0 0 4px 0;
                    font-size: 17px;
                    font-weight: 700;
                    word-break: break-word;
                }
                .doc-banner .sub {
                    font-size: 12px;
                    color: var(--muted);
                }
                .pill-strip {
                    display: flex;
                    gap: 8px;
                    overflow-x: auto;
                    padding-bottom: 10px;
                    margin-bottom: 10px;
                }
                .page-pill {
                    flex: 0 0 auto;
                    text-decoration: none;
                    background: var(--card);
                    color: var(--accent);
                    border: 1px solid var(--border);
                    border-radius: 999px;
                    padding: 5px 12px;
                    font-size: 12px;
                    font-weight: 600;
                }
                .pdf-page-card {
                    background: var(--card);
                    border: 1px solid var(--border);
                    border-radius: 16px;
                    overflow: hidden;
                    margin-bottom: 16px;
                    box-shadow: 0 6px 20px rgba(0,0,0,0.08);
                }
                .pdf-page-header {
                    display: flex;
                    justify-content: space-between;
                    padding: 8px 14px;
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--muted);
                    border-bottom: 1px solid var(--border);
                }
                .pdf-page-img {
                    width: 100%;
                    height: auto;
                    display: block;
                }
                body.theme-dark .pdf-page-img {
                    filter: invert(0.9) hue-rotate(180deg);
                }
                body.theme-sepia .pdf-page-img {
                    filter: sepia(0.28) contrast(0.96);
                }
                .doc-card {
                    background: var(--card);
                    border: 1px solid var(--border);
                    border-radius: 16px;
                    padding: 18px;
                    margin-bottom: 14px;
                    box-shadow: 0 4px 14px rgba(0,0,0,0.05);
                }
                .doc-card h2 {
                    margin: 14px 0 8px 0;
                    color: var(--accent);
                    font-size: 18px;
                }
                .doc-card p {
                    margin: 0 0 12px 0;
                    font-size: 15px;
                    word-wrap: break-word;
                }
                .slide-badge {
                    display: inline-block;
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--accent);
                    margin-bottom: 6px;
                }
                .table-wrap {
                    background: var(--card);
                    border: 1px solid var(--border);
                    border-radius: 16px;
                    overflow-x: auto;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    font-size: 13px;
                }
                th, td {
                    padding: 9px 12px;
                    border-bottom: 1px solid var(--border);
                    text-align: left;
                    white-space: nowrap;
                }
                th {
                    background: rgba(217, 107, 67, 0.12);
                    color: var(--accent);
                    font-weight: 700;
                }
                .row-num {
                    color: var(--muted);
                    font-weight: 600;
                    width: 40px;
                }
                .code-card {
                    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                    font-size: 12.5px;
                    overflow-x: auto;
                    padding: 12px;
                }
                .code-line {
                    display: flex;
                    white-space: pre;
                    line-height: 1.5;
                }
                .code-line .ln {
                    width: 38px;
                    flex-shrink: 0;
                    color: var(--muted);
                    user-select: none;
                }
            </style>
            <script>
                window.applyStudioSettings = function(themeClass, zoomPct) {
                    document.body.className = themeClass;
                    document.body.style.zoom = (zoomPct / 100.0);
                };
            </script>
        </head>
        <body class="theme-paper">
            <div id="studio-root">
                <div class="doc-banner">
                    <h1>${escapeHtml(title)}</h1>
                    <div class="sub">${escapeHtml(subtitle)}</div>
                </div>
                $bodyHtml
            </div>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeHtml(raw: String): String {
    return raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private fun resolveOrDownloadDocumentToCache(
    context: Context,
    item: UnifiedMediaItem,
    serverBaseUrl: String
): File? {
    // 0. Check Offline Vault copy first for instant zero-network document rendering
    item.offlineFilePath?.takeIf { it.isNotBlank() }?.let { path ->
        val vaultFile = File(path)
        if (vaultFile.exists() && vaultFile.canRead() && vaultFile.length() > 0L) {
            return vaultFile
        }
    }

    val ext = item.extension.ifBlank { "bin" }
    val safeHash = MessageDigest.getInstance("MD5")
        .digest((item.id + "_" + item.filename).toByteArray())
        .joinToString("") { "%02x".format(it) }
    val cacheTarget = File(context.cacheDir, "studio_doc_${safeHash}.$ext")

    if (cacheTarget.exists() && cacheTarget.length() > 0L) {
        return cacheTarget
    }

    // 1. Check filesDir/uploaded_media for local copies created during uploadBatch
    val uploadDir = File(context.filesDir, "uploaded_media")
    if (uploadDir.exists()) {
        val sanitizedOriginal = item.originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val matched = uploadDir.listFiles()?.firstOrNull { f ->
            f.isFile && f.length() > 0L && (
                f.name.equals(item.originalName, ignoreCase = true) ||
                    f.name.equals(item.filename, ignoreCase = true) ||
                    f.name.endsWith("-$sanitizedOriginal", ignoreCase = true) ||
                    (item.size > 0L && f.length() == item.size && f.extension.equals(ext, ignoreCase = true))
                )
        }
        if (matched != null) {
            matched.copyTo(cacheTarget, overwrite = true)
            return cacheTarget
        }
    }

    // 2. Check direct filesystem path
    if (item.path.isNotBlank() && !item.path.startsWith("http", ignoreCase = true)) {
        val directFile = File(item.path)
        if (directFile.exists() && directFile.canRead() && directFile.length() > 0L) {
            directFile.copyTo(cacheTarget, overwrite = true)
            return cacheTarget
        }
    }

    // 3. Check localUri (content:// or file://)
    val localUriStr = item.localUri
    if (!localUriStr.isNullOrBlank()) {
        try {
            val uri = Uri.parse(localUriStr)
            if (uri.scheme == "content" || uri.scheme == "file") {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copyStreamToFile(input, cacheTarget)
                }
                if (cacheTarget.exists() && cacheTarget.length() > 0L) {
                    return cacheTarget
                }
            }
        } catch (_: Exception) {
            // Fall through to remote URL download
        }
    }

    // 4. Download from resolved & URL-encoded remote candidate URLs (with JWT auth)
    val candidateUrls = item.resolveRemoteCandidateUrls(serverBaseUrl)
    val expectHtml = ext in setOf("html", "htm")

    for (remoteUrl in candidateUrls) {
        try {
            val req = okhttp3.Request.Builder().url(remoteUrl).get().build()
            com.example.data.remote.RetrofitClient.okHttpClient.newCall(req).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (response.isSuccessful && (expectHtml || !contentType.contains("text/html"))) {
                    response.body?.byteStream()?.use { input ->
                        copyStreamToFile(input, cacheTarget)
                    }
                    if (cacheTarget.exists() && cacheTarget.length() > 0L) {
                        return cacheTarget
                    }
                }
            }
        } catch (_: Exception) {
            // Try next candidate URL
        }
    }

    return null
}

private fun copyStreamToFile(input: InputStream, target: File) {
    FileOutputStream(target).use { output ->
        val buffer = ByteArray(16 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
    }
}
