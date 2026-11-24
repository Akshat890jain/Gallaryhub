package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SmartCollectionFilter
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.UploadQueueItem
import com.example.data.model.UploadQueueStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryQuickToolsBar(
    hiddenVaultCount: Int,
    isOcrIndexing: Boolean,
    ocrIndexedCount: Int,
    onOpenPrivateVault: () -> Unit,
    onOpenStorageCleaner: () -> Unit,
    onOpenDocumentScanner: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onIndexOcrLibrary: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("gallery_quick_tools_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickToolPill(
            icon = Icons.Default.Lock,
            label = if (hiddenVaultCount > 0) "Vault ($hiddenVaultCount)" else "Vault",
            accent = MaterialTheme.colorScheme.primary,
            onClick = onOpenPrivateVault,
            testTag = "quick_tool_vault"
        )
        QuickToolPill(
            icon = Icons.Default.CleaningServices,
            label = "Cleaner",
            accent = Color(0xFF0EA5E9),
            onClick = onOpenStorageCleaner,
            testTag = "quick_tool_cleaner"
        )
        QuickToolPill(
            icon = Icons.Default.DocumentScanner,
            label = "Scan PDF",
            accent = Color(0xFF10B981),
            onClick = onOpenDocumentScanner,
            testTag = "quick_tool_scan_pdf"
        )
        QuickToolPill(
            icon = Icons.Default.QrCodeScanner,
            label = "Scan QR",
            accent = Color(0xFF8B5CF6),
            onClick = onOpenQrScanner,
            testTag = "quick_tool_scan_qr"
        )
        QuickToolPill(
            icon = Icons.Default.TextSnippet,
            label = if (isOcrIndexing) "Indexing…" else if (ocrIndexedCount > 0) "OCR ($ocrIndexedCount)" else "Index Text",
            accent = Color(0xFFF59E0B),
            isLoading = isOcrIndexing,
            onClick = onIndexOcrLibrary,
            testTag = "quick_tool_ocr"
        )
    }
}

@Composable
private fun QuickToolPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        modifier = Modifier
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = accent, modifier = Modifier.size(14.dp))
            } else {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SmartAutoCollectionsStrip(
    selectedFilter: SmartCollectionFilter,
    onSelectFilter: (SmartCollectionFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_collections_row")
    ) {
        items(SmartCollectionFilter.entries, key = { it.key }) { filter ->
            val selected = selectedFilter == filter
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelectFilter(filter) }
                    .testTag("smart_collection_${filter.key}")
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun UploadQueueProgressBanner(
    queue: List<UploadQueueItem>,
    wifiOnlyUploads: Boolean,
    onRetryFailed: () -> Unit,
    onClearCompleted: () -> Unit
) {
    if (queue.isEmpty()) return
    val hasFailedOrWaiting = queue.any {
        it.status == UploadQueueStatus.FAILED || it.status == UploadQueueStatus.WAITING_WIFI
    }
    val hasCompleted = queue.any { it.status == UploadQueueStatus.COMPLETED }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .testTag("upload_queue_progress_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Upload Queue (${queue.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (wifiOnlyUploads) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
                                Text("Wi-Fi only", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasFailedOrWaiting) {
                        TextButton(
                            onClick = onRetryFailed,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("upload_queue_retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (hasCompleted) {
                        IconButton(
                            onClick = onClearCompleted,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("upload_queue_clear_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear completed", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            queue.take(3).forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when (entry.status) {
                                UploadQueueStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(14.dp))
                                UploadQueueStatus.FAILED -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                UploadQueueStatus.WAITING_WIFI -> Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                else -> Unit
                            }
                            Text(
                                text = if (entry.status == UploadQueueStatus.UPLOADING) "${entry.progressPercent}%" else entry.status.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when (entry.status) {
                                    UploadQueueStatus.FAILED -> MaterialTheme.colorScheme.error
                                    UploadQueueStatus.COMPLETED -> Color(0xFF22C55E)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    if (entry.status == UploadQueueStatus.UPLOADING) {
                        LinearProgressIndicator(
                            progress = { (entry.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnThisDayMemoriesBanner(
    memories: List<UnifiedMediaItem>,
    serverBaseUrl: String,
    onPreviewMemory: (UnifiedMediaItem) -> Unit
) {
    if (memories.isEmpty()) return
    var expanded by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .testTag("on_this_day_memories_banner")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "On This Day · Memories (${memories.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse Memories" else "Expand Memories",
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(memories, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPreviewMemory(item) }
                                .testTag("memory_thumb_${item.id}")
                        ) {
                            AsyncImage(
                                model = item.resolveThumbnailUrl(serverBaseUrl) ?: item.resolveMediaUrl(serverBaseUrl),
                                contentDescription = item.originalName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = item.relativeDateLabel,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chronological Month/Year Grouped Timeline View with sticky month headers
 * and a right-edge fast-scrub date rail.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineGroupedGalleryView(
    mediaItems: List<UnifiedMediaItem>,
    serverBaseUrl: String,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    ocrMatchedIds: Set<String>,
    onMediaClick: (UnifiedMediaItem) -> Unit,
    onMediaLongClick: (UnifiedMediaItem) -> Unit
) {
    val monthFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val shortRailFormatter = remember { SimpleDateFormat("MMM yy", Locale.US) }

    val groupedSections = remember(mediaItems) {
        val grouped = linkedMapOf<String, Pair<String, MutableList<UnifiedMediaItem>>>()
        mediaItems.sortedByDescending { it.timestampMillis }.forEach { item ->
            val date = Date(if (item.timestampMillis > 0L) item.timestampMillis else System.currentTimeMillis())
            val fullLabel = monthFormatter.format(date)
            val shortLabel = shortRailFormatter.format(date)
            val entry = grouped.getOrPut(fullLabel) { shortLabel to mutableListOf() }
            entry.second.add(item)
        }
        grouped.entries.map { (title, pair) -> Triple(title, pair.first, pair.second.toList()) }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Precompute LazyColumn item index for each month header for instant rail scrubbing
    val headerIndices = remember(groupedSections) {
        val map = mutableMapOf<String, Int>()
        var runningIdx = 0
        groupedSections.forEach { (monthTitle, _, itemsInMonth) ->
            map[monthTitle] = runningIdx
            runningIdx += 1 + ((itemsInMonth.size + 2) / 3) // 1 header + ceil(items / 3) rows
        }
        map
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 48.dp, top = 8.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("timeline_grouped_gallery")
        ) {
            groupedSections.forEach { (monthTitle, _, itemsInMonth) ->
                stickyHeader(key = "header_$monthTitle") {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${itemsInMonth.size} item(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val chunkedRows = itemsInMonth.chunked(3)
                items(chunkedRows, key = { row -> row.joinToString("_") { it.id } }) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { item ->
                            val selected = item.id in selectedIds
                            val isOcrMatch = item.id in ocrMatchedIds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onMediaClick(item) }
                                    .testTag("timeline_tile_${item.id}")
                            ) {
                                val thumb = item.resolveThumbnailUrl(serverBaseUrl)
                                if (thumb != null) {
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = item.originalName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(item.iconEmoji, fontSize = 24.sp)
                                        Text(
                                            text = item.originalName,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isOcrMatch) {
                                    Surface(
                                        color = Color(0xFFF59E0B).copy(alpha = 0.92f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = "Text",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (isSelectionMode && selected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Right-edge fast-scrub date rail
        if (groupedSections.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 16.dp, bottom = 120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .testTag("timeline_fast_scrub_rail"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedSections.take(12).forEach { (fullTitle, shortLabel, _) ->
                    Text(
                        text = shortLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val targetIdx = headerIndices[fullTitle] ?: 0
                                scope.launch { listState.animateScrollToItem(targetIdx) }
                            }
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
