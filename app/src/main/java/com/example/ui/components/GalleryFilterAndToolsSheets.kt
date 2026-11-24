package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryFilter
import com.example.data.model.FolderItem
import com.example.data.model.GalleryViewMode
import com.example.data.model.SmartCollectionFilter
import com.example.data.model.SortField
import com.example.data.model.SortOrder
import com.example.data.model.UnifiedMediaItem
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.ui.theme.parseHexColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryFilterBottomSheet(
    folders: List<FolderItem>,
    sharedFolders: List<FolderItem>,
    activeFolder: FolderItem?,
    activeCategory: CategoryFilter,
    smartCollectionFilter: SmartCollectionFilter,
    availableTags: List<String>,
    searchQuery: String,
    sortField: SortField,
    sortOrder: SortOrder,
    viewMode: GalleryViewMode,
    isSelectionMode: Boolean,
    onThisDayMemories: List<UnifiedMediaItem>,
    serverBaseUrl: String,
    onCategoryChange: (CategoryFilter) -> Unit,
    onSmartCollectionChange: (SmartCollectionFilter) -> Unit,
    onSelectFolder: (FolderItem?) -> Unit,
    onSelectTag: (String) -> Unit,
    onSortFieldChange: (SortField) -> Unit,
    onToggleSortOrder: () -> Unit,
    onViewModeChange: (GalleryViewMode) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onPreviewMemory: (UnifiedMediaItem) -> Unit,
    onResetAllFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allFolders = folders + sharedFolders

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("gallery_filter_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Filter & View",
                            fontFamily = PlayfairDisplayFontFamily,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Refine archive, sort order & layout",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onResetAllFilters,
                        modifier = Modifier.testTag("filter_sheet_reset_button")
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("filter_sheet_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Filter Sheet")
                    }
                }
            }

            // 1. On This Day · Memories (moved cleanly off home screen)
            if (onThisDayMemories.isNotEmpty()) {
                OnThisDayMemoriesBanner(
                    memories = onThisDayMemories,
                    serverBaseUrl = serverBaseUrl,
                    onPreviewMemory = { mem ->
                        onDismiss()
                        onPreviewMemory(mem)
                    }
                )
            }

            // 2. View Mode & Multi-Select
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSectionHeader(title = "Layout & Selection")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GalleryViewMode.entries.forEach { mode ->
                        val selected = viewMode == mode
                        val icon = when (mode) {
                            GalleryViewMode.GRID -> Icons.Default.GridView
                            GalleryViewMode.TIMELINE -> Icons.Default.CalendarMonth
                            GalleryViewMode.MASONRY -> Icons.Default.Dashboard
                            GalleryViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onViewModeChange(mode) }
                                .testTag("filter_sheet_view_${mode.name.lowercase()}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = mode.label,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        onToggleSelectionMode()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("filter_sheet_multi_select_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isSelectionMode) "Exit Multi-Select Mode" else "Enter Multi-Select Mode",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // 3. Categories
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSectionHeader(title = "Categories")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CategoryFilter.entries.forEach { cat ->
                        val selected = activeCategory == cat
                        val label = if (cat == CategoryFilter.DOCUMENTS) "Docs" else cat.label
                        FilterChip(
                            selected = selected,
                            onClick = { onCategoryChange(cat) },
                            label = { Text(label) },
                            leadingIcon = when (cat) {
                                CategoryFilter.FAVORITES -> {
                                    {
                                        Icon(
                                            Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFFE05263),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                CategoryFilter.OFFLINE -> {
                                    {
                                        Icon(
                                            Icons.Default.OfflinePin,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF2E9E6B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                else -> null
                            },
                            modifier = Modifier.testTag("category_chip_${cat.key}")
                        )
                    }
                }
            }

            // 4. Smart Auto-Collections
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSectionHeader(title = "Smart Auto-Collections")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SmartCollectionFilter.entries.forEach { smart ->
                        FilterChip(
                            selected = smartCollectionFilter == smart,
                            onClick = { onSmartCollectionChange(smart) },
                            label = { Text(smart.label) },
                            modifier = Modifier.testTag("smart_collection_${smart.key}")
                        )
                    }
                }
            }

            // 5. Sort Order
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilterSectionHeader(title = "Sort By")
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onToggleSortOrder() }
                            .testTag("sort_order_toggle_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (sortOrder == SortOrder.DESC) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = "Toggle Sort Direction",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (sortOrder == SortOrder.DESC) "Descending" else "Ascending",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortField.entries.forEach { field ->
                        FilterChip(
                            selected = sortField == field,
                            onClick = { onSortFieldChange(field) },
                            label = { Text(field.label, maxLines = 1) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sort_field_${field.name.lowercase()}")
                        )
                    }
                }
            }

            // 6. Quick Folders
            if (allFolders.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterSectionHeader(title = "Folders")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val rootSelected = activeFolder == null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (rootSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (rootSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectFolder(null) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Icon(
                                    Icons.Default.Dashboard,
                                    contentDescription = null,
                                    tint = if (rootSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "All Archive",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (rootSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        allFolders.forEach { folder ->
                            val isSelected = activeFolder?.id == folder.id
                            val tint = parseHexColor(folder.color)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) tint.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (isSelected) tint else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectFolder(if (isSelected) null else folder) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                ) {
                                    Icon(
                                        imageVector = if (folder.isShared) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${folder.name} · ${folder.fileCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Popular #Tags
            if (availableTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterSectionHeader(title = "Popular #Tags")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gallery_tag_chips_row")
                    ) {
                        val activeTag = searchQuery.trim().removePrefix("#").lowercase()
                        availableTags.forEach { tag ->
                            val selected = activeTag == tag
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTag(tag) },
                                label = { Text("#$tag") },
                                modifier = Modifier.testTag("tag_filter_chip_$tag")
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryToolsBottomSheet(
    hiddenVaultCount: Int,
    isOcrIndexing: Boolean,
    ocrIndexedCount: Int,
    onOpenPrivateVault: () -> Unit,
    onOpenStorageCleaner: () -> Unit,
    onOpenDocumentScanner: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onIndexOcrLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("gallery_tools_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Studio Tools",
                            fontFamily = PlayfairDisplayFontFamily,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Privacy vault, storage cleaner & scanners",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("tools_sheet_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Tools Sheet")
                }
            }

            // Row 1: Vault & Cleaner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioToolTile(
                    icon = Icons.Default.Lock,
                    tint = Color(0xFF4F8FF7),
                    title = "Private Vault",
                    subtitle = if (hiddenVaultCount > 0) "$hiddenVaultCount hidden item(s)" else "Biometric & PIN lock",
                    testTag = "quick_tool_vault",
                    onClick = {
                        onDismiss()
                        onOpenPrivateVault()
                    },
                    modifier = Modifier.weight(1f)
                )
                StudioToolTile(
                    icon = Icons.Default.CleaningServices,
                    tint = Color(0xFF0EA5E9),
                    title = "Storage Cleaner",
                    subtitle = "Duplicates & large files",
                    testTag = "quick_tool_cleaner",
                    onClick = {
                        onDismiss()
                        onOpenStorageCleaner()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Scan PDF & Scan QR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioToolTile(
                    icon = Icons.Default.DocumentScanner,
                    tint = Color(0xFF2E9E6B),
                    title = "Scan to PDF",
                    subtitle = "Multi-page doc capture",
                    testTag = "quick_tool_scan_pdf",
                    onClick = {
                        onDismiss()
                        onOpenDocumentScanner()
                    },
                    modifier = Modifier.weight(1f)
                )
                StudioToolTile(
                    icon = Icons.Default.QrCodeScanner,
                    tint = Color(0xFFA855F7),
                    title = "Scan QR Link",
                    subtitle = "Open shared files fast",
                    testTag = "quick_tool_scan_qr",
                    onClick = {
                        onDismiss()
                        onOpenQrScanner()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: On-Device ML Kit OCR Indexer (full width)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isOcrIndexing) {
                        onIndexOcrLibrary()
                        onDismiss()
                    }
                    .testTag("quick_tool_ocr_index")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE08A2F).copy(alpha = 0.16f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isOcrIndexing) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFE08A2F),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.TextSnippet,
                                    contentDescription = null,
                                    tint = Color(0xFFE08A2F),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOcrIndexing) "Indexing OCR Text…" else "Index Photo & PDF Text (OCR)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (ocrIndexedCount > 0) {
                                "$ocrIndexedCount item(s) indexed for instant search inside images & PDFs"
                            } else {
                                "Extract searchable text on-device using ML Kit"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun StudioToolTile(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.10f),
        modifier = modifier
            .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tint.copy(alpha = 0.18f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
