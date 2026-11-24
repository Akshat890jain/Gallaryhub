package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import android.view.HapticFeedbackConstants
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.data.model.CategoryFilter
import com.example.data.model.FolderItem
import com.example.data.model.GalleryViewMode
import com.example.data.model.MediaType
import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.SmartCollectionFilter
import com.example.data.model.SortField
import com.example.data.model.SortOrder
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.UploadQueueItem
import com.example.ui.components.GalleryFilterBottomSheet
import com.example.ui.components.GalleryToolsBottomSheet
import com.example.ui.components.TimelineGroupedGalleryView
import com.example.ui.components.UploadQueueProgressBanner
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.parseHexColor

@Composable
fun GalleryTabScreen(
    mediaItems: List<UnifiedMediaItem>,
    folders: List<FolderItem>,
    sharedFolders: List<FolderItem>,
    activeFolder: FolderItem?,
    activeCategory: CategoryFilter,
    searchQuery: String,
    sortField: SortField,
    sortOrder: SortOrder,
    viewMode: GalleryViewMode,
    isLoading: Boolean,
    isSyncing: Boolean = false,
    isUploading: Boolean = false,
    isGuestMode: Boolean = false,
    isCloudConnected: Boolean = !isGuestMode,
    isCheckingConnection: Boolean = false,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    downloadingOfflineIds: Set<String> = emptySet(),
    missingServerMediaIds: Set<String> = emptySet(),
    pendingSyncCount: Int = 0,
    isZipExporting: Boolean,
    serverBaseUrl: String,
    onSearchQueryChange: (String) -> Unit,
    onCategoryChange: (CategoryFilter) -> Unit,
    onSortFieldChange: (SortField) -> Unit,
    onToggleSortOrder: () -> Unit,
    onViewModeChange: (GalleryViewMode) -> Unit,
    onSelectFolder: (FolderItem?) -> Unit,
    onOpenUploadModal: () -> Unit,
    onRefreshMedia: () -> Unit = {},
    onSyncNow: () -> Unit = onRefreshMedia,
    onPingConnection: () -> Unit = {},
    onDirectPhotoUpload: (List<Uri>) -> Unit = {},
    onUpdateItemTags: (UnifiedMediaItem, String) -> Unit = { _, _ -> },
    onToggleFavorite: (UnifiedMediaItem) -> Unit = {},
    onToggleOfflinePin: (UnifiedMediaItem) -> Unit = {},
    onRecoverMissingFile: (UnifiedMediaItem) -> Unit = {},
    onRenameItem: (UnifiedMediaItem, String) -> Unit = { _, _ -> },
    onMediaClick: (UnifiedMediaItem) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleItemSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkPinOffline: () -> Unit = {},
    onBulkDownloadZip: () -> Unit,
    onBulkShare: () -> Unit = onBulkDownloadZip,
    onBulkDelete: () -> Unit,
    onBulkMoveToFolder: (String?) -> Unit,
    onSingleShareLink: (UnifiedMediaItem) -> Unit,
    onSingleMoveToFolder: (UnifiedMediaItem, String?) -> Unit,
    onSingleSoftDelete: (UnifiedMediaItem) -> Unit,
    smartCollectionFilter: SmartCollectionFilter = SmartCollectionFilter.ALL,
    hiddenVaultCount: Int = 0,
    isOcrIndexing: Boolean = false,
    ocrTextByMediaId: Map<String, String> = emptyMap(),
    onThisDayMemories: List<UnifiedMediaItem> = emptyList(),
    uploadQueue: List<UploadQueueItem> = emptyList(),
    wifiOnlyUploads: Boolean = false,
    onSmartCollectionChange: (SmartCollectionFilter) -> Unit = {},
    onOpenPrivateVault: () -> Unit = {},
    onOpenStorageCleaner: () -> Unit = {},
    onOpenDocumentScanner: () -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    onIndexOcrLibrary: () -> Unit = {},
    onRetryFailedUploads: () -> Unit = {},
    onClearCompletedUploads: () -> Unit = {},
    onSingleHideInVault: (UnifiedMediaItem) -> Unit = {},
    onBulkHideInVault: () -> Unit = {}
) {
    MediaGalleryScreen(
        mediaItems = mediaItems,
        folders = folders,
        sharedFolders = sharedFolders,
        activeFolder = activeFolder,
        activeCategory = activeCategory,
        searchQuery = searchQuery,
        sortField = sortField,
        sortOrder = sortOrder,
        viewMode = viewMode,
        isLoading = isLoading,
        isSyncing = isSyncing,
        isUploading = isUploading,
        isGuestMode = isGuestMode,
        isCloudConnected = isCloudConnected,
        isCheckingConnection = isCheckingConnection,
        showEmbeddedTopBar = false,
        isSelectionMode = isSelectionMode,
        selectedIds = selectedIds,
        downloadingOfflineIds = downloadingOfflineIds,
        missingServerMediaIds = missingServerMediaIds,
        pendingSyncCount = pendingSyncCount,
        isZipExporting = isZipExporting,
        serverBaseUrl = serverBaseUrl,
        onSearchQueryChange = onSearchQueryChange,
        onCategoryChange = onCategoryChange,
        onSortFieldChange = onSortFieldChange,
        onToggleSortOrder = onToggleSortOrder,
        onViewModeChange = onViewModeChange,
        onSelectFolder = onSelectFolder,
        onOpenUploadModal = onOpenUploadModal,
        onRefreshMedia = onRefreshMedia,
        onSyncNow = onSyncNow,
        onPingConnection = onPingConnection,
        onDirectPhotoUpload = onDirectPhotoUpload,
        onUpdateItemTags = onUpdateItemTags,
        onToggleFavorite = onToggleFavorite,
        onToggleOfflinePin = onToggleOfflinePin,
        onRecoverMissingFile = onRecoverMissingFile,
        onRenameItem = onRenameItem,
        onMediaClick = onMediaClick,
        onToggleSelectionMode = onToggleSelectionMode,
        onToggleItemSelected = onToggleItemSelected,
        onSelectAll = onSelectAll,
        onClearSelection = onClearSelection,
        onBulkPinOffline = onBulkPinOffline,
        onBulkDownloadZip = onBulkDownloadZip,
        onBulkShare = onBulkShare,
        onBulkDelete = onBulkDelete,
        onBulkMoveToFolder = onBulkMoveToFolder,
        onSingleShareLink = onSingleShareLink,
        onSingleMoveToFolder = onSingleMoveToFolder,
        onSingleSoftDelete = onSingleSoftDelete,
        smartCollectionFilter = smartCollectionFilter,
        hiddenVaultCount = hiddenVaultCount,
        isOcrIndexing = isOcrIndexing,
        ocrTextByMediaId = ocrTextByMediaId,
        onThisDayMemories = onThisDayMemories,
        uploadQueue = uploadQueue,
        wifiOnlyUploads = wifiOnlyUploads,
        onSmartCollectionChange = onSmartCollectionChange,
        onOpenPrivateVault = onOpenPrivateVault,
        onOpenStorageCleaner = onOpenStorageCleaner,
        onOpenDocumentScanner = onOpenDocumentScanner,
        onOpenQrScanner = onOpenQrScanner,
        onIndexOcrLibrary = onIndexOcrLibrary,
        onRetryFailedUploads = onRetryFailedUploads,
        onClearCompletedUploads = onClearCompletedUploads,
        onSingleHideInVault = onSingleHideInVault,
        onBulkHideInVault = onBulkHideInVault
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    mediaItems: List<UnifiedMediaItem>,
    folders: List<FolderItem>,
    sharedFolders: List<FolderItem>,
    activeFolder: FolderItem?,
    activeCategory: CategoryFilter,
    searchQuery: String,
    sortField: SortField,
    sortOrder: SortOrder,
    viewMode: GalleryViewMode,
    isLoading: Boolean,
    isSyncing: Boolean = false,
    isUploading: Boolean = false,
    isGuestMode: Boolean = false,
    isCloudConnected: Boolean = !isGuestMode,
    isCheckingConnection: Boolean = false,
    showEmbeddedTopBar: Boolean = true,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    downloadingOfflineIds: Set<String> = emptySet(),
    missingServerMediaIds: Set<String> = emptySet(),
    pendingSyncCount: Int = 0,
    isZipExporting: Boolean,
    serverBaseUrl: String,
    onSearchQueryChange: (String) -> Unit,
    onCategoryChange: (CategoryFilter) -> Unit,
    onSortFieldChange: (SortField) -> Unit,
    onToggleSortOrder: () -> Unit,
    onViewModeChange: (GalleryViewMode) -> Unit,
    onSelectFolder: (FolderItem?) -> Unit,
    onOpenUploadModal: () -> Unit,
    onRefreshMedia: () -> Unit = {},
    onSyncNow: () -> Unit = onRefreshMedia,
    onPingConnection: () -> Unit = {},
    onDirectPhotoUpload: (List<Uri>) -> Unit = {},
    onUpdateItemTags: (UnifiedMediaItem, String) -> Unit = { _, _ -> },
    onToggleFavorite: (UnifiedMediaItem) -> Unit = {},
    onToggleOfflinePin: (UnifiedMediaItem) -> Unit = {},
    onRecoverMissingFile: (UnifiedMediaItem) -> Unit = {},
    onRenameItem: (UnifiedMediaItem, String) -> Unit = { _, _ -> },
    onMediaClick: (UnifiedMediaItem) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleItemSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkPinOffline: () -> Unit = {},
    onBulkDownloadZip: () -> Unit,
    onBulkShare: () -> Unit = onBulkDownloadZip,
    onBulkDelete: () -> Unit,
    onBulkMoveToFolder: (String?) -> Unit,
    onSingleShareLink: (UnifiedMediaItem) -> Unit,
    onSingleMoveToFolder: (UnifiedMediaItem, String?) -> Unit,
    onSingleSoftDelete: (UnifiedMediaItem) -> Unit,
    smartCollectionFilter: SmartCollectionFilter = SmartCollectionFilter.ALL,
    hiddenVaultCount: Int = 0,
    isOcrIndexing: Boolean = false,
    ocrTextByMediaId: Map<String, String> = emptyMap(),
    onThisDayMemories: List<UnifiedMediaItem> = emptyList(),
    uploadQueue: List<UploadQueueItem> = emptyList(),
    wifiOnlyUploads: Boolean = false,
    onSmartCollectionChange: (SmartCollectionFilter) -> Unit = {},
    onOpenPrivateVault: () -> Unit = {},
    onOpenStorageCleaner: () -> Unit = {},
    onOpenDocumentScanner: () -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    onIndexOcrLibrary: () -> Unit = {},
    onRetryFailedUploads: () -> Unit = {},
    onClearCompletedUploads: () -> Unit = {},
    onSingleHideInVault: (UnifiedMediaItem) -> Unit = {},
    onBulkHideInVault: () -> Unit = {}
) {
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var isToolsSheetOpen by remember { mutableStateOf(false) }
    var bulkMoveMenuExpanded by remember { mutableStateOf(false) }
    var tagEditingItem by remember { mutableStateOf<UnifiedMediaItem?>(null) }
    var renamingItem by remember { mutableStateOf<UnifiedMediaItem?>(null) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    val activeFilterCount = remember(
        activeCategory,
        smartCollectionFilter,
        activeFolder,
        searchQuery,
        sortField,
        sortOrder,
        viewMode,
        isSelectionMode
    ) {
        var count = 0
        if (activeCategory != CategoryFilter.ALL) count++
        if (smartCollectionFilter != SmartCollectionFilter.ALL) count++
        if (activeFolder != null) count++
        if (searchQuery.trim().startsWith("#")) count++
        if (viewMode != GalleryViewMode.GRID) count++
        if (sortField != SortField.DATE || sortOrder != SortOrder.DESC) count++
        if (isSelectionMode) count++
        count
    }

    val resetAllFilters: () -> Unit = {
        onCategoryChange(CategoryFilter.ALL)
        onSmartCollectionChange(SmartCollectionFilter.ALL)
        onSelectFolder(null)
        if (searchQuery.trim().startsWith("#")) onSearchQueryChange("")
        onSortFieldChange(SortField.DATE)
        if (sortOrder != SortOrder.DESC) onToggleSortOrder()
        onViewModeChange(GalleryViewMode.GRID)
        if (isSelectionMode) onClearSelection()
    }

    val ocrMatchedIds = remember(searchQuery, ocrTextByMediaId) {
        val q = searchQuery.trim()
        if (q.isEmpty()) emptySet()
        else ocrTextByMediaId.filterValues { it.contains(q, ignoreCase = true) }.keys
    }

    val availableTags = remember(mediaItems) {
        mediaItems
            .flatMap { it.effectiveTags }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showEmbeddedTopBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("media_gallery_embedded_top_bar"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.components.GalleryHubLogoMark(
                            size = 34.dp,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "GalleryHub",
                            fontFamily = PlayfairDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.ui.components.CloudConnectionStatusIndicator(
                            isCloudConnected = isCloudConnected,
                            isChecking = isCheckingConnection || isSyncing,
                            isGuestMode = isGuestMode,
                            onClick = onPingConnection
                        )
                        com.example.ui.components.SyncNowStatusButton(
                            isSyncing = isSyncing,
                            pendingSyncCount = pendingSyncCount,
                            onSyncNow = onSyncNow
                        )
                    }
                }
            }

            // Minimal Data-First Top Utility Row: Search + Filter + Tools
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                text = "Search files, OCR text, or #tags…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.testTag("gallery_search_clear_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gallery_search_input")
                    )

                    // Filter & View Button (48dp touch target + active filter badge)
                    Box(contentAlignment = Alignment.Center) {
                        val hasFilters = activeFilterCount > 0
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (hasFilters) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(48.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (hasFilters) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    isFilterSheetOpen = true
                                }
                                .testTag("gallery_filter_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Filter and View Options",
                                    tint = if (hasFilters) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        if (activeFilterCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    .testTag("gallery_filter_active_badge")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$activeFilterCount",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            lineHeight = 9.sp
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiary
                                    )
                                }
                            }
                        }
                    }

                    // Studio Tools Button (48dp touch target)
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(48.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    isToolsSheetOpen = true
                                }
                                .testTag("gallery_tools_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isOcrIndexing) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Studio Tools",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                        if (hiddenVaultCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4F8FF7))
                            )
                        }
                    }
                }

                // Conditional Active-Filter Pill Strip (only shown when user has applied a filter/view change)
                if (activeFilterCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("gallery_active_filters_strip"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeFolder != null) {
                            ActiveFilterRemovablePill(
                                label = "Folder: ${activeFolder.name}",
                                accent = parseHexColor(activeFolder.color),
                                onRemove = { onSelectFolder(null) }
                            )
                        }
                        if (activeCategory != CategoryFilter.ALL) {
                            ActiveFilterRemovablePill(
                                label = activeCategory.label,
                                accent = MaterialTheme.colorScheme.primary,
                                onRemove = { onCategoryChange(CategoryFilter.ALL) }
                            )
                        }
                        if (smartCollectionFilter != SmartCollectionFilter.ALL) {
                            ActiveFilterRemovablePill(
                                label = smartCollectionFilter.label,
                                accent = MaterialTheme.colorScheme.primary,
                                onRemove = { onSmartCollectionChange(SmartCollectionFilter.ALL) }
                            )
                        }
                        if (searchQuery.trim().startsWith("#")) {
                            ActiveFilterRemovablePill(
                                label = searchQuery.trim(),
                                accent = MaterialTheme.colorScheme.primary,
                                onRemove = { onSearchQueryChange("") }
                            )
                        }
                        if (viewMode != GalleryViewMode.GRID) {
                            ActiveFilterRemovablePill(
                                label = "View: ${viewMode.label}",
                                accent = MaterialTheme.colorScheme.secondary,
                                onRemove = { onViewModeChange(GalleryViewMode.GRID) }
                            )
                        }
                        if (sortField != SortField.DATE || sortOrder != SortOrder.DESC) {
                            val dir = if (sortOrder == SortOrder.DESC) "↓" else "↑"
                            ActiveFilterRemovablePill(
                                label = "Sort: ${sortField.label} $dir",
                                accent = MaterialTheme.colorScheme.secondary,
                                onRemove = {
                                    onSortFieldChange(SortField.DATE)
                                    if (sortOrder != SortOrder.DESC) onToggleSortOrder()
                                }
                            )
                        }
                        if (isSelectionMode) {
                            ActiveFilterRemovablePill(
                                label = "Selecting (${selectedIds.size})",
                                accent = MaterialTheme.colorScheme.tertiary,
                                onRemove = onClearSelection
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { resetAllFilters() }
                                .testTag("active_filters_reset_all")
                        ) {
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Live Upload Progress & Background Queue Banner
            if (uploadQueue.isNotEmpty()) {
                UploadQueueProgressBanner(
                    queue = uploadQueue,
                    wifiOnlyUploads = wifiOnlyUploads,
                    onRetryFailed = onRetryFailedUploads,
                    onClearCompleted = onClearCompletedUploads
                )
            }

            // Pull-Down-to-Refresh Media Canvas (Replaces Top Bar Refresh Button)
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefreshMedia,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("gallery_pull_to_refresh")
            ) {
                when {
                    isLoading && mediaItems.isEmpty() -> {
                        SkeletonShimmerGrid(count = 8)
                    }

                    mediaItems.isEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                if (isGuestMode) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .size(72.dp)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                RoundedCornerShape(24.dp)
                                            )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }
                                } else {
                                    com.example.ui.components.GalleryHubLogoMark(
                                        size = 72.dp,
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        testTag = "empty_state_logo_mark"
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = when {
                                        searchQuery.isNotBlank() -> "No matches for \"$searchQuery\""
                                        activeCategory == CategoryFilter.FAVORITES -> "No Favorites Yet"
                                        activeCategory == CategoryFilter.OFFLINE -> "No Offline Files Yet"
                                        isGuestMode -> "Guest Preview Mode"
                                        else -> "Your Visual Archive is Empty"
                                    },
                                    fontFamily = PlayfairDisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = when {
                                        searchQuery.isNotBlank() -> "Try another title keyword or tap a #tag filter chip above."
                                        activeCategory == CategoryFilter.FAVORITES -> "Tap the heart icon on any photo, video, or document to pin it to your Favorites collection."
                                        activeCategory == CategoryFilter.OFFLINE -> "Pin files offline from any card menu or Lightbox, or view files once to auto-cache them."
                                        isGuestMode -> "Sign in or create a free account to upload photos, videos, and documents to your personal cloud."
                                        else -> "Pull down anytime to refresh, or tap Upload below to choose a photo, video, or file."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(20.dp))
                                if (searchQuery.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onSearchQueryChange("") },
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.testTag("empty_state_clear_search_button")
                                    ) {
                                        Text("Clear Search Filter")
                                    }
                                } else if (activeCategory == CategoryFilter.FAVORITES || activeCategory == CategoryFilter.OFFLINE) {
                                    OutlinedButton(
                                        onClick = { onCategoryChange(CategoryFilter.ALL) },
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.testTag("empty_state_show_all_button")
                                    ) {
                                        Text("Show All Media")
                                    }
                                } else {
                                    Button(
                                        onClick = onOpenUploadModal,
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("empty_state_upload_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isGuestMode) Icons.Default.Lock else Icons.Default.Add,
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isGuestMode) "Sign In to Upload" else "Upload",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    viewMode == GalleryViewMode.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 165.dp),
                            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 130.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("media_gallery_grid")
                        ) {
                            items(mediaItems, key = { it.id }, contentType = { it.mediaType }) { item ->
                                MediaGridCard(
                                    item = item,
                                    folders = folders,
                                    serverBaseUrl = serverBaseUrl,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = item.id in selectedIds,
                                    isDownloadingOffline = item.id in downloadingOfflineIds,
                                    isMissingOnServer = item.id in missingServerMediaIds,
                                    isOcrMatch = item.id in ocrMatchedIds,
                                    aspectRatio = 1.25f,
                                    onClick = {
                                        if (isSelectionMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onToggleItemSelected(item.id)
                                        } else {
                                            onMediaClick(item)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onToggleItemSelected(item.id)
                                    },
                                    onToggleFavorite = { onToggleFavorite(item) },
                                    onToggleOfflinePin = { onToggleOfflinePin(item) },
                                    onRecoverMissingFile = { onRecoverMissingFile(item) },
                                    onRename = { renamingItem = item },
                                    onTagClick = { clickedTag -> onSearchQueryChange("#$clickedTag") },
                                    onEditTags = { tagEditingItem = item },
                                    onShareLink = { onSingleShareLink(item) },
                                    onMoveToFolder = { folderId -> onSingleMoveToFolder(item, folderId) },
                                    onHideInVault = { onSingleHideInVault(item) },
                                    onDelete = { onSingleSoftDelete(item) }
                                )
                            }
                        }
                    }

                    viewMode == GalleryViewMode.TIMELINE -> {
                        TimelineGroupedGalleryView(
                            mediaItems = mediaItems,
                            serverBaseUrl = serverBaseUrl,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            ocrMatchedIds = ocrMatchedIds,
                            onMediaClick = { item ->
                                if (isSelectionMode) {
                                    onToggleItemSelected(item.id)
                                } else {
                                    onMediaClick(item)
                                }
                            },
                            onMediaLongClick = { item -> onToggleItemSelected(item.id) }
                        )
                    }

                    viewMode == GalleryViewMode.MASONRY -> {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(minSize = 165.dp),
                            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 130.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 14.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(mediaItems, key = { it.id }, contentType = { it.mediaType }) { item ->
                                MediaGridCard(
                                    item = item,
                                    folders = folders,
                                    serverBaseUrl = serverBaseUrl,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = item.id in selectedIds,
                                    isDownloadingOffline = item.id in downloadingOfflineIds,
                                    isMissingOnServer = item.id in missingServerMediaIds,
                                    isOcrMatch = item.id in ocrMatchedIds,
                                    aspectRatio = item.aspectRatio.coerceIn(0.75f, 1.6f),
                                    onClick = {
                                        if (isSelectionMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onToggleItemSelected(item.id)
                                        } else {
                                            onMediaClick(item)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onToggleItemSelected(item.id)
                                    },
                                    onToggleFavorite = { onToggleFavorite(item) },
                                    onToggleOfflinePin = { onToggleOfflinePin(item) },
                                    onRecoverMissingFile = { onRecoverMissingFile(item) },
                                    onRename = { renamingItem = item },
                                    onTagClick = { clickedTag -> onSearchQueryChange("#$clickedTag") },
                                    onEditTags = { tagEditingItem = item },
                                    onShareLink = { onSingleShareLink(item) },
                                    onMoveToFolder = { folderId -> onSingleMoveToFolder(item, folderId) },
                                    onHideInVault = { onSingleHideInVault(item) },
                                    onDelete = { onSingleSoftDelete(item) }
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 130.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(mediaItems, key = { it.id }, contentType = { it.mediaType }) { item ->
                                MediaListRowCard(
                                    item = item,
                                    serverBaseUrl = serverBaseUrl,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = item.id in selectedIds,
                                    isDownloadingOffline = item.id in downloadingOfflineIds,
                                    isMissingOnServer = item.id in missingServerMediaIds && item.offlineState != OfflineAvailabilityState.PINNED,
                                    onClick = {
                                        if (isSelectionMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onToggleItemSelected(item.id)
                                        } else {
                                            onMediaClick(item)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onToggleItemSelected(item.id)
                                    },
                                    onToggleFavorite = { onToggleFavorite(item) },
                                    onToggleOfflinePin = { onToggleOfflinePin(item) },
                                    onRecoverMissingFile = { onRecoverMissingFile(item) },
                                    onEditTags = { tagEditingItem = item },
                                    onShareLink = { onSingleShareLink(item) },
                                    onDelete = { onSingleSoftDelete(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Glassmorphic Floating Multi-Select Batch Action Dock
        if (isSelectionMode) {
            val allVisibleSelected = mediaItems.isNotEmpty() && selectedIds.containsAll(mediaItems.map { it.id })
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 12.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .testTag("batch_selection_action_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Row: Selected Count + Select All / Deselect All + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${selectedIds.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (selectedIds.size == 1) "1 item selected" else "${selectedIds.size} items selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(
                                onClick = onSelectAll,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("batch_select_all_button")
                            ) {
                                Icon(
                                    Icons.Default.SelectAll,
                                    contentDescription = "Select All",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (allVisibleSelected) "Deselect All" else "Select All",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = onClearSelection,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("batch_clear_selection_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Selection Mode")
                            }
                        }
                    }

                    // Bottom Action Row: Share, Offline, Move, ZIP, Delete (Compact vertical pills fit 100% cleanly on all screens)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val hasSel = selectedIds.isNotEmpty()
                        // 1. Share Multiple Files
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (hasSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = hasSel) { onBulkShare() }
                                .testTag("bulk_share_button")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share Selected",
                                    tint = if (hasSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Share",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        // 2. Save Offline Batch
                        val offlineGreen = Color(0xFF2E9E6B)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = offlineGreen.copy(alpha = if (hasSel) 0.14f else 0.06f),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .border(1.dp, offlineGreen.copy(alpha = if (hasSel) 0.45f else 0.2f), RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = hasSel) { onBulkPinOffline() }
                                .testTag("bulk_pin_offline_button")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.OfflinePin,
                                    contentDescription = "Save Offline",
                                    tint = if (hasSel) offlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasSel) offlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        // 3. Move to Folder
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = hasSel) { bulkMoveMenuExpanded = true }
                                    .testTag("bulk_move_folder_button")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        Icons.Default.DriveFileMove,
                                        contentDescription = "Move to Folder",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Move",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = bulkMoveMenuExpanded,
                                onDismissRequest = { bulkMoveMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Unfile (Root Gallery)") },
                                    onClick = {
                                        bulkMoveMenuExpanded = false
                                        onBulkMoveToFolder(null)
                                    }
                                )
                                folders.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text("Move to ${f.name}") },
                                        onClick = {
                                            bulkMoveMenuExpanded = false
                                            onBulkMoveToFolder(f.id)
                                        }
                                    )
                                }
                            }
                        }

                        // 4. Export ZIP
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(52.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = hasSel && !isZipExporting) { onBulkDownloadZip() }
                                .testTag("bulk_download_zip_button")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.FolderZip,
                                    contentDescription = "Export ZIP",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isZipExporting) "…" else "ZIP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }

                        // 4B. Hide Selected in Private Vault
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(52.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = hasSel) { onBulkHideInVault() }
                                .testTag("bulk_hide_vault_button")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Hide in Private Vault",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Hide",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }

                        // 5. Delete Selected
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = hasSel) { onBulkDelete() }
                                .testTag("bulk_delete_button")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Bulk Delete Selected",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Inline Rename File Dialog
    renamingItem?.let { item ->
        var newNameText by remember(item.id) { mutableStateOf(item.originalName) }
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            title = { Text("Rename File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a new name for this ${item.mediaType.label.lowercase()} file:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newNameText,
                        onValueChange = { newNameText = it },
                        label = { Text("File name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_file_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNameText.isNotBlank()) {
                            onRenameItem(item, newNameText)
                            renamingItem = null
                        }
                    },
                    enabled = newNameText.isNotBlank(),
                    modifier = Modifier.testTag("rename_file_confirm_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Tags Dialog
    tagEditingItem?.let { item ->
        var tagsText by remember(item.id) {
            mutableStateOf(item.effectiveTags.joinToString(", "))
        }
        AlertDialog(
            onDismissRequest = { tagEditingItem = null },
            title = { Text("Curate Item Tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Add comma-separated tags for \"${item.originalName}\" to filter it from the top search bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("Tags (e.g. portrait, travel, invoice)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_tags_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateItemTags(item, tagsText)
                        tagEditingItem = null
                    },
                    modifier = Modifier.testTag("save_tags_button")
                ) {
                    Text("Save Tags")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagEditingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Filter & View Bottom Sheet
    if (isFilterSheetOpen) {
        GalleryFilterBottomSheet(
            folders = folders,
            sharedFolders = sharedFolders,
            activeFolder = activeFolder,
            activeCategory = activeCategory,
            smartCollectionFilter = smartCollectionFilter,
            availableTags = availableTags,
            searchQuery = searchQuery,
            sortField = sortField,
            sortOrder = sortOrder,
            viewMode = viewMode,
            isSelectionMode = isSelectionMode,
            onThisDayMemories = onThisDayMemories,
            serverBaseUrl = serverBaseUrl,
            onCategoryChange = onCategoryChange,
            onSmartCollectionChange = onSmartCollectionChange,
            onSelectFolder = onSelectFolder,
            onSelectTag = { tag ->
                val cleanSearch = searchQuery.trim().removePrefix("#").lowercase()
                onSearchQueryChange(if (cleanSearch == tag) "" else "#$tag")
            },
            onSortFieldChange = onSortFieldChange,
            onToggleSortOrder = onToggleSortOrder,
            onViewModeChange = onViewModeChange,
            onToggleSelectionMode = onToggleSelectionMode,
            onPreviewMemory = onMediaClick,
            onResetAllFilters = resetAllFilters,
            onDismiss = { isFilterSheetOpen = false }
        )
    }

    // Studio Tools Bottom Sheet
    if (isToolsSheetOpen) {
        GalleryToolsBottomSheet(
            hiddenVaultCount = hiddenVaultCount,
            isOcrIndexing = isOcrIndexing,
            ocrIndexedCount = ocrTextByMediaId.size,
            onOpenPrivateVault = onOpenPrivateVault,
            onOpenStorageCleaner = onOpenStorageCleaner,
            onOpenDocumentScanner = onOpenDocumentScanner,
            onOpenQrScanner = onOpenQrScanner,
            onIndexOcrLibrary = onIndexOcrLibrary,
            onDismiss = { isToolsSheetOpen = false }
        )
    }
}

@Composable
private fun ActiveFilterRemovablePill(
    label: String,
    accent: Color,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        modifier = Modifier
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onRemove)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove $label filter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridCard(
    item: UnifiedMediaItem,
    folders: List<FolderItem>,
    serverBaseUrl: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isDownloadingOffline: Boolean = false,
    isMissingOnServer: Boolean = false,
    isOcrMatch: Boolean = false,
    aspectRatio: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleOfflinePin: () -> Unit,
    onRecoverMissingFile: () -> Unit = {},
    onRename: () -> Unit,
    onTagClick: (String) -> Unit,
    onEditTags: () -> Unit,
    onShareLink: () -> Unit,
    onMoveToFolder: (String?) -> Unit,
    onHideInVault: () -> Unit = {},
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    val cardScale by animateFloatAsState(
        targetValue = if (isSelected) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "media_card_selection_scale"
    )
    val cardBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 200),
        label = "media_card_border_width"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 220),
        label = "media_card_border_color"
    )
    val cardSurfaceColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 220),
        label = "media_card_surface_tint"
    )
    val thumbnailTintOverlay by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "media_card_thumb_tint"
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "media_card_badge_scale"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardSurfaceColor,
        tonalElevation = if (isSelected) 5.dp else 1.dp,
        shadowElevation = if (isSelected) 6.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .border(
                width = cardBorderWidth,
                color = cardBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_card_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val thumbUrl = remember(item.id, item.offlineFilePath, item.localUri, item.thumbnail, item.path, serverBaseUrl) {
                    item.resolveThumbnailUrl(serverBaseUrl)
                }
                var loadFailed by remember(thumbUrl) { mutableStateOf(false) }

                if (thumbUrl != null && !loadFailed) {
                    val thumbRequest = remember(thumbUrl) {
                        ImageRequest.Builder(context)
                            .data(thumbUrl)
                            .size(Size(480, 480))
                            .allowRgb565(true)
                            .crossfade(140)
                            .build()
                    }
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = item.originalName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { loadFailed = true }
                    )
                } else {
                    EditorialDocumentOrMediaFallback(item = item)
                }

                // Subtle animated selection color-tinting scrim over thumbnail
                if (thumbnailTintOverlay.alpha > 0.005f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(thumbnailTintOverlay)
                    )
                }

                val extBadge = item.extension.uppercase().ifBlank { item.mediaType.label.uppercase() }
                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = extBadge,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (item.mediaType == MediaType.VIDEO) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    item.formattedDuration?.let { dur ->
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = dur,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Top-Left: Selection Checkmark OR Offline Availability Badge
                if (isSelectionMode || isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = badgeScale
                                scaleY = badgeScale
                            }
                            .border(
                                width = 1.5.dp,
                                color = Color.White.copy(alpha = if (isSelected) 0.9f else 0.55f),
                                shape = CircleShape
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = if (isSelected) "Selected" else "Not selected",
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else if (isDownloadingOffline) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color(0xFF4ADE80),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else if (item.offlineState == OfflineAvailabilityState.PINNED) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1F7A4D).copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp)
                            .testTag("card_offline_pinned_badge_${item.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.OfflinePin,
                                contentDescription = "Pinned Offline",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else if (isMissingOnServer && !item.isAvailableOffline) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFD9822B).copy(alpha = 0.94f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clickable { onRecoverMissingFile() }
                            .testTag("card_missing_server_badge_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "Missing on server, tap to re-link",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Re-link",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (item.offlineState == OfflineAvailabilityState.AUTO_CACHED) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.56f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(22.dp)
                            .testTag("card_offline_cached_badge_${item.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = "Cached Offline",
                                tint = Color(0xFF86EFAC),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Bottom-Left: Tactile 1-Tap Favorite Heart Pill (hidden during selection mode)
                if (!isSelectionMode) {
                    val heartTint by animateColorAsState(
                        targetValue = if (item.isFavorite) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.9f),
                        animationSpec = tween(180),
                        label = "card_fav_heart_tint"
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = if (item.isFavorite) 0.62f else 0.42f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onToggleFavorite()
                            }
                            .testTag("card_favorite_button_${item.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
                                tint = heartTint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Bottom-Right OCR Text Match badge when matched via on-device ML Kit text index
                if (isOcrMatch) {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.94f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.TextSnippet, contentDescription = null, tint = Color.Black, modifier = Modifier.size(11.dp))
                            Text(
                                text = "Text match",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.originalName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.formattedSize} · ${item.relativeDateLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "File Actions",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (isMissingOnServer && !item.isAvailableOffline) {
                                DropdownMenuItem(
                                    text = { Text("Re-link from Device", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = Color(0xFFD9822B)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRecoverMissingFile()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (item.offlineState == OfflineAvailabilityState.PINNED) "Remove Offline Copy"
                                        else "Keep Available Offline"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (item.offlineState == OfflineAvailabilityState.PINNED) Icons.Default.OfflinePin else Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color(0xFF2E9E6B)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleOfflinePin()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (item.isFavorite) "Remove from Favorites" else "Add to Favorites")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = Color(0xFFE05263)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleFavorite()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename File") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Tags") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEditTags()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Create Public Share Link") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onShareLink()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hide in Private Vault") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    menuExpanded = false
                                    onHideInVault()
                                }
                            )
                            folders.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text("Move to ${f.name}") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onMoveToFolder(f.id)
                                    }
                                )
                            }
                            if (item.folderId != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Folder") },
                                    onClick = {
                                        menuExpanded = false
                                        onMoveToFolder(null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Move to Trash", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialDocumentOrMediaFallback(item: UnifiedMediaItem) {
    val ext = item.extension.uppercase().ifBlank { item.mediaType.label.uppercase() }
    val stampColor = when (ext) {
        "PDF" -> Color(0xFFD9534F)
        "DOC", "DOCX", "TXT", "MD" -> Color(0xFFD49B4B)
        "XLS", "XLSX", "CSV" -> Color(0xFF4E9F76)
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 4.dp,
            modifier = Modifier
                .width(86.dp)
                .height(104.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(topStart = 8.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = stampColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Surface(
                        color = stampColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = ext.take(4),
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = stampColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    )
                }

                Text(
                    text = item.formattedSize,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaListRowCard(
    item: UnifiedMediaItem,
    serverBaseUrl: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isDownloadingOffline: Boolean = false,
    isMissingOnServer: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onToggleOfflinePin: () -> Unit = {},
    onRecoverMissingFile: () -> Unit = {},
    onEditTags: () -> Unit,
    onShareLink: () -> Unit,
    onDelete: () -> Unit
) {
    val rowScale by animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "media_list_row_scale"
    )
    val rowSurfaceColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 220),
        label = "media_list_row_surface_tint"
    )
    val rowBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 220),
        label = "media_list_row_border_color"
    )
    val rowBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 200),
        label = "media_list_row_border_width"
    )
    val rowThumbTint by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "media_list_thumb_tint"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = rowSurfaceColor,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .border(
                width = rowBorderWidth,
                color = rowBorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_list_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val thumbUrl = remember(item.id, item.offlineFilePath, item.localUri, item.thumbnail, item.path, serverBaseUrl) {
                    item.resolveThumbnailUrl(serverBaseUrl)
                }
                if (thumbUrl != null) {
                    val req = remember(thumbUrl) {
                        ImageRequest.Builder(context)
                            .data(thumbUrl)
                            .size(Size(180, 180))
                            .allowRgb565(true)
                            .crossfade(120)
                            .build()
                    }
                    AsyncImage(
                        model = req,
                        contentDescription = item.originalName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = item.extension.uppercase().ifBlank { "DOC" },
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (rowThumbTint.alpha > 0.005f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(rowThumbTint)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.originalName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.offlineState == OfflineAvailabilityState.PINNED) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Default.OfflinePin,
                            contentDescription = "Pinned Offline",
                            tint = Color(0xFF2E9E6B),
                            modifier = Modifier.size(14.dp)
                        )
                    } else if (item.offlineState == OfflineAvailabilityState.AUTO_CACHED) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "Auto-Cached Offline",
                            tint = Color(0xFF2E9E6B).copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = "${item.mediaType.label} · ${item.formattedSize} · ${item.relativeDateLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelectionMode) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            } else {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (item.isFavorite) "Remove Favorite" else "Add Favorite",
                        tint = if (item.isFavorite) Color(0xFFE05263) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (isMissingOnServer) onRecoverMissingFile() else onToggleOfflinePin()
                    },
                    enabled = !isDownloadingOffline,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isDownloadingOffline) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else if (isMissingOnServer) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "File missing on server — tap to re-link",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(19.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (item.offlineState == OfflineAvailabilityState.PINNED) Icons.Default.OfflinePin else Icons.Default.CloudDownload,
                            contentDescription = if (item.offlineState == OfflineAvailabilityState.PINNED) "Remove Offline" else "Save Offline",
                            tint = if (item.offlineState == OfflineAvailabilityState.PINNED) Color(0xFF2E9E6B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onShareLink,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Link", modifier = Modifier.size(19.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonShimmerGrid(count: Int = 8) {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 300f)
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 165.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(count) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.25f)
                            .background(shimmerBrush)
                    )
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(shimmerBrush)
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(shimmerBrush)
                        )
                    }
                }
            }
        }
    }
}
