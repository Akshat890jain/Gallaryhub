package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.data.model.MediaType
import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.formatBytes
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.util.DuplicateMatchKind
import com.example.util.StorageAuditReport

@Composable
fun StorageCleanerModal(
    report: StorageAuditReport,
    serverBaseUrl: String,
    onTrashItems: (List<UnifiedMediaItem>) -> Unit,
    onUnpinOfflineItems: (List<UnifiedMediaItem>) -> Unit,
    onPreviewItem: (UnifiedMediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Duplicates, 1 = Largest Files
    var selectedIds by remember(report) {
        // Pre-select duplicate copies (keeping the best copy in each cluster) for one-tap cleanup
        mutableStateOf(
            report.duplicateClusters
                .flatMap { cluster -> cluster.items.drop(1).map { it.id } }
                .toSet()
        )
    }

    val allAuditedItems = remember(report) {
        (report.duplicateClusters.flatMap { it.items } + report.largestItems)
            .associateBy { it.id }
    }
    val selectedItems = remember(selectedIds, allAuditedItems) {
        selectedIds.mapNotNull { allAuditedItems[it] }
    }
    val selectedBytes = remember(selectedItems) {
        selectedItems.sumOf { it.size.coerceAtLeast(0L) }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(54f)
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("storage_cleaner_modal")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Duplicate & Large Cleaner",
                            fontFamily = PlayfairDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "Library: ${formatBytes(report.totalLibraryBytes)} · Offline Vault: ${formatBytes(report.totalOfflineVaultBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cleaner_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Cleaner")
                }
            }

            // Reclaimable Summary Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        RoundedCornerShape(18.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (report.totalDuplicateCopiesCount > 0) {
                                "${report.totalDuplicateCopiesCount} duplicate file(s) found"
                            } else {
                                "No duplicates detected"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (report.totalReclaimableDuplicateBytes > 0L) {
                                "Reclaim up to ${formatBytes(report.totalReclaimableDuplicateBytes)} in one tap"
                            } else {
                                "Inspect Largest Files to free cloud quota or Offline Vault storage"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (report.duplicateClusters.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                selectedIds = report.duplicateClusters
                                    .flatMap { it.items.drop(1).map { m -> m.id } }
                                    .toSet()
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Smart Pick", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("Duplicates (${report.duplicateClusters.size})") },
                    modifier = Modifier.testTag("cleaner_tab_duplicates")
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("Largest Files (${report.largestItems.size})") },
                    modifier = Modifier.testTag("cleaner_tab_largest")
                )
            }

            // Content List
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab == 0) {
                    if (report.duplicateClusters.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E9E6B),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Your library is duplicate-free!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Switch to Largest Files to review heavy videos and PDFs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(report.duplicateClusters, key = { it.id }) { cluster ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cluster.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (cluster.matchKind == DuplicateMatchKind.EXACT) {
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                            } else {
                                                Color(0xFFF59E0B).copy(alpha = 0.16f)
                                            }
                                        ) {
                                            Text(
                                                text = "${cluster.matchKind.label} · Save ${formatBytes(cluster.reclaimableBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (cluster.matchKind == DuplicateMatchKind.EXACT) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    Color(0xFFF59E0B)
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    cluster.items.forEachIndexed { idx, item ->
                                        CleanerMediaRow(
                                            item = item,
                                            serverBaseUrl = serverBaseUrl,
                                            isSelected = item.id in selectedIds,
                                            badgeText = if (idx == 0) "Keep Best" else "Copy",
                                            onToggle = {
                                                selectedIds = if (item.id in selectedIds) {
                                                    selectedIds - item.id
                                                } else {
                                                    selectedIds + item.id
                                                }
                                            },
                                            onPreview = { onPreviewItem(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(report.largestItems, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                CleanerMediaRow(
                                    item = item,
                                    serverBaseUrl = serverBaseUrl,
                                    isSelected = item.id in selectedIds,
                                    badgeText = item.formattedSize,
                                    onToggle = {
                                        selectedIds = if (item.id in selectedIds) {
                                            selectedIds - item.id
                                        } else {
                                            selectedIds + item.id
                                        }
                                    },
                                    onPreview = { onPreviewItem(item) }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            if (selectedItems.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pinnedSelected = selectedItems.filter { it.offlineState != OfflineAvailabilityState.NONE }
                        if (pinnedSelected.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    onUnpinOfflineItems(pinnedSelected)
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("cleaner_unpin_offline_button")
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Unpin (${pinnedSelected.size})", maxLines = 1)
                            }
                        }
                        Button(
                            onClick = {
                                onTrashItems(selectedItems)
                                selectedIds = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("cleaner_trash_selected_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Trash ${selectedItems.size} (${formatBytes(selectedBytes)})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanerMediaRow(
    item: UnifiedMediaItem,
    serverBaseUrl: String,
    isSelected: Boolean,
    badgeText: String,
    onToggle: () -> Unit,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onPreview() },
            contentAlignment = Alignment.Center
        ) {
            val thumb = remember(item.id, item.offlineFilePath, serverBaseUrl) {
                item.resolveThumbnailUrl(serverBaseUrl)
            }
            if (thumb != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumb)
                        .size(Size(160, 160))
                        .build(),
                    contentDescription = item.originalName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.PlayCircle else Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.originalName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${item.formattedSize} · ${item.relativeDateLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.offlineState == OfflineAvailabilityState.PINNED) {
                    Icon(
                        Icons.Default.OfflinePin,
                        contentDescription = "Offline Vault",
                        tint = Color(0xFF2E9E6B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
