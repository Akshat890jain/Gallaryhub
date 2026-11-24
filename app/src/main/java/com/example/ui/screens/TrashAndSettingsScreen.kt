package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.data.model.MediaStorageBreakdown
import com.example.data.model.OfflineVaultStats
import com.example.data.model.ServerHealthState
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.UserSession
import com.example.data.model.formatBytes
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.components.GalleryHubLogoMark
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.QuotaDangerRed
import com.example.ui.theme.QuotaHealthyGreen
import com.example.ui.theme.QuotaWarningAmber
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.GalleryUiState
import com.example.util.DynamicAppIconManager

// --- Feature 9: Trash, Two-Stage Soft-Delete & 30-Day Retention Countdown ---
@Composable
fun TrashTabScreen(
    trashItems: List<UnifiedMediaItem>,
    serverBaseUrl: String,
    onRestoreItem: (UnifiedMediaItem) -> Unit,
    onPermanentDeleteItem: (UnifiedMediaItem) -> Unit,
    onEmptyTrashAll: () -> Unit
) {
    var confirmingEraseItem by remember { mutableStateOf<UnifiedMediaItem?>(null) }
    var confirmingEmptyAll by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trash",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Items in Trash are automatically deleted after 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (trashItems.isNotEmpty()) {
                    Button(
                        onClick = { confirmingEmptyAll = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("empty_trash_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Empty Trash")
                    }
                }
            }
        }

        if (trashItems.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("♻️", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Trash is Clean",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Deleted files appear here for 30 days so you can restore them anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(trashItems, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumb = item.resolveThumbnailUrl(serverBaseUrl)
                                if (thumb != null) {
                                    val ctx = LocalContext.current
                                    val thumbReq = remember(thumb) {
                                        ImageRequest.Builder(ctx)
                                            .data(thumb)
                                            .size(Size(160, 160))
                                            .allowRgb565(true)
                                            .crossfade(120)
                                            .build()
                                    }
                                    AsyncImage(
                                        model = thumbReq,
                                        contentDescription = item.originalName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(item.iconEmoji, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = item.originalName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.mediaType.label} · ${item.formattedSize}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = Color(0xFFFBBC04).copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "⏳ ${item.daysLeftInTrash} days left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { onRestoreItem(item) },
                                modifier = Modifier.testTag("restore_trash_${item.id}")
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Restore")
                            }
                            OutlinedButton(
                                onClick = { confirmingEraseItem = item },
                                modifier = Modifier.testTag("permanent_delete_${item.id}")
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Permanent Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmingEraseItem?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmingEraseItem = null },
            title = { Text("Delete Permanently?") },
            text = {
                Text("\"${item.originalName}\" (${item.formattedSize}) will be permanently deleted and cannot be recovered.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentDeleteItem(item)
                        confirmingEraseItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingEraseItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmingEmptyAll) {
        AlertDialog(
            onDismissRequest = { confirmingEmptyAll = false },
            title = { Text("Empty Trash?") },
            text = {
                Text("All ${trashItems.size} item(s) in Trash will be permanently deleted and cannot be recovered.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrashAll()
                        confirmingEmptyAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingEmptyAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- Feature 13 & 14: User Profile, Security, 500MB Quota, Theme & Accent Engine, Danger Zone ---
@Composable
fun SettingsTabScreen(
    userSession: UserSession?,
    serverHealth: ServerHealthState,
    totalUsedBytes: Long,
    usagePercent: Float,
    storageBreakdown: MediaStorageBreakdown = MediaStorageBreakdown(),
    offlineVaultStats: OfflineVaultStats = OfflineVaultStats(),
    pendingSyncCount: Int = 0,
    isSyncing: Boolean = false,
    themeMode: ThemeMode,
    accentHex: String,
    dynamicAppIconEnabled: Boolean = true,
    hasPendingAppIconChange: Boolean = false,
    exifScrubDefaultOn: Boolean = true,
    wifiOnlyUploads: Boolean = false,
    vaultBiometricEnabled: Boolean = true,
    hasVaultPin: Boolean = false,
    hiddenVaultCount: Int = 0,
    isCloudSyncConnected: Boolean = false,
    cloudSyncUserEmail: String? = null,
    cloudSyncStatusMessage: String? = null,
    isCloudSyncingMetadata: Boolean = false,
    onGoogleCloudSignIn: () -> Unit = {},
    onGoogleCloudSignOut: () -> Unit = {},
    onPickAvatar: () -> Unit,
    onUpdateProfileAndPassword: (name: String, email: String, currentPass: String?, newPass: String?) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccentColor: (String) -> Unit,
    onToggleDynamicAppIcon: (Boolean) -> Unit = {},
    onToggleExifScrubDefault: (Boolean) -> Unit = {},
    onToggleWifiOnlyUploads: (Boolean) -> Unit = {},
    onToggleVaultBiometric: (Boolean) -> Unit = {},
    onResetVaultPin: () -> Unit = {},
    onOpenPrivateVault: () -> Unit = {},
    onOpenStorageCleaner: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onToggleAutoCache: (Boolean) -> Unit = {},
    onDownloadFavoritesOffline: () -> Unit = {},
    onClearAutoCache: () -> Unit = {},
    onClearAllOfflineFiles: () -> Unit = {},
    onOpenServerConfig: () -> Unit,
    onRelaunchOnboardingTour: () -> Unit,
    onDangerZoneDeleteAllFiles: () -> Unit,
    onLogout: () -> Unit
) {
    var name by remember(userSession?.name) { mutableStateOf(userSession?.name.orEmpty()) }
    var email by remember(userSession?.email) { mutableStateOf(userSession?.email.orEmpty()) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMismatchError by remember { mutableStateOf<String?>(null) }
    var showDangerZoneConfirm by remember { mutableStateOf(false) }
    var showClearAllOfflineConfirm by remember { mutableStateOf(false) }

    val quotaBarColor = when {
        usagePercent > 80f -> QuotaDangerRed
        usagePercent > 50f -> QuotaWarningAmber
        else -> QuotaHealthyGreen
    }
    val photosColor = MaterialTheme.colorScheme.primary
    val videosColor = Color(0xFF4F8FF7)
    val docsColor = Color(0xFF2E9E6B)
    val offlineAccent = Color(0xFF2E9E6B)
    val pendingAmber = Color(0xFFD9822B)

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 124.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Storage Quota Analytics + Media Type Breakdown Card (500 MB Ceiling)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .testTag("settings_cloud_storage_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = quotaBarColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Cloud Storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${String.format("%.1f", usagePercent)}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = quotaBarColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (usagePercent / 100f).coerceIn(0f, 1f) },
                        color = quotaBarColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${formatBytes(totalUsedBytes)} used of ${formatBytes(GalleryUiState.STORAGE_QUOTA_BYTES)} quota",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Segmented Media Type Breakdown Bar
                    val breakdownTotal = storageBreakdown.totalBytes.coerceAtLeast(1L)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Media Breakdown (${storageBreakdown.totalCount} files)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (storageBreakdown.imageBytes > 0L) {
                            Box(
                                modifier = Modifier
                                    .weight((storageBreakdown.imageBytes.toFloat() / breakdownTotal).coerceAtLeast(0.04f))
                                    .fillMaxSize()
                                    .background(photosColor)
                            )
                        }
                        if (storageBreakdown.videoBytes > 0L) {
                            Box(
                                modifier = Modifier
                                    .weight((storageBreakdown.videoBytes.toFloat() / breakdownTotal).coerceAtLeast(0.04f))
                                    .fillMaxSize()
                                    .background(videosColor)
                            )
                        }
                        if (storageBreakdown.documentBytes > 0L) {
                            Box(
                                modifier = Modifier
                                    .weight((storageBreakdown.documentBytes.toFloat() / breakdownTotal).coerceAtLeast(0.04f))
                                    .fillMaxSize()
                                    .background(docsColor)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaBreakdownPill(
                            label = "Photos",
                            count = storageBreakdown.imageCount,
                            bytes = storageBreakdown.imageBytes,
                            dotColor = photosColor,
                            modifier = Modifier.weight(1f)
                        )
                        MediaBreakdownPill(
                            label = "Videos",
                            count = storageBreakdown.videoCount,
                            bytes = storageBreakdown.videoBytes,
                            dotColor = videosColor,
                            modifier = Modifier.weight(1f)
                        )
                        MediaBreakdownPill(
                            label = "Docs",
                            count = storageBreakdown.documentCount,
                            bytes = storageBreakdown.documentBytes,
                            dotColor = docsColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 1B. Offline Vault & Cache Manager Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .testTag("settings_offline_vault_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.OfflinePin, contentDescription = null, tint = offlineAccent)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Offline Vault & Cache",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = offlineAccent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = formatBytes(offlineVaultStats.totalOfflineBytes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = offlineAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Pinned files stay permanently saved on this device. Viewed files are automatically cached up to 150 MB for instant offline loading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = offlineAccent.copy(alpha = 0.09f),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, offlineAccent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Pinned Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = offlineAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${offlineVaultStats.pinnedCount} file(s)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatBytes(offlineVaultStats.pinnedBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Auto-Cached",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${offlineVaultStats.autoCachedCount} file(s)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatBytes(offlineVaultStats.autoCachedBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (pendingSyncCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = pendingAmber.copy(alpha = 0.13f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, pendingAmber.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .testTag("settings_pending_sync_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$pendingSyncCount offline edit(s) queued",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = pendingAmber
                                    )
                                    Text(
                                        text = "Will auto-sync automatically when back online.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = onSyncNow,
                                    enabled = !isSyncing,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("settings_flush_queue_button")
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isSyncing) "Syncing" else "Sync", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Auto-Cache Viewed Files",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Silently cache opened media for offline viewing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = offlineVaultStats.autoCacheEnabled,
                            onCheckedChange = onToggleAutoCache,
                            modifier = Modifier.testTag("settings_auto_cache_switch")
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onDownloadFavoritesOffline,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("settings_pin_favorites_offline_button")
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download All Favorites for Offline", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearAutoCache,
                            enabled = offlineVaultStats.autoCachedCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("settings_clear_autocache_button")
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Clear Cache", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { showClearAllOfflineConfirm = true },
                            enabled = offlineVaultStats.totalOfflineCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("settings_clear_all_offline_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Clear Vault", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // 1B-Pro. Privacy, Private Vault, EXIF Scrubber, Sync & Home Screen Widget Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .testTag("settings_privacy_vault_sync_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Privacy, Vault & Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Private Vault & Storage Cleaner Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onOpenPrivateVault,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("settings_open_private_vault_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hiddenVaultCount > 0) "Vault ($hiddenVaultCount)" else "Private Vault",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenStorageCleaner,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("settings_open_cleaner_button")
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Cleaner",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Biometric Unlock toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Biometric Vault Unlock",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Fingerprint / Face unlock with 4-digit PIN fallback & auto-lock on background",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = vaultBiometricEnabled,
                            onCheckedChange = onToggleVaultBiometric,
                            modifier = Modifier.testTag("settings_vault_biometric_switch")
                        )
                    }

                    if (hasVaultPin) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = onResetVaultPin,
                            modifier = Modifier.testTag("settings_reset_vault_pin_button")
                        ) {
                            Text("Reset 4-Digit Vault PIN", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // EXIF Scrubber Default toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Scrub EXIF Metadata on Share",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Strip GPS coordinates, camera model & timestamps by default (per-share toggle available)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = exifScrubDefaultOn,
                            onCheckedChange = onToggleExifScrubDefault,
                            modifier = Modifier.testTag("settings_exif_scrub_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Wi-Fi-Only Uploads toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Wi-Fi-Only Uploads",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Queue uploads on cellular and auto-resume when Wi-Fi connects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = wifiOnlyUploads,
                            onCheckedChange = onToggleWifiOnlyUploads,
                            modifier = Modifier.testTag("settings_wifi_only_uploads_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Companion Cloud Metadata Sync (Google Sign-In + Firestore alongside Render Server)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCloudSyncConnected) {
                            Color(0xFF2E9E6B).copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isCloudSyncConnected) {
                                    Color(0xFF2E9E6B).copy(alpha = 0.40f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("settings_companion_cloud_sync_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = if (isCloudSyncConnected) Color(0xFF2E9E6B) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Cloud Metadata Sync (Google)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isCloudSyncConnected) {
                                                cloudSyncUserEmail ?: "Connected via Google"
                                            } else {
                                                "Optional companion sync alongside your Render server"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isCloudSyncConnected) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF2E9E6B).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = if (isCloudSyncingMetadata) "Syncing…" else "Live Synced",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E9E6B),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (isCloudSyncConnected) {
                                    cloudSyncStatusMessage
                                        ?: "Favorites, #Tags, Renames, Private Vault, OCR index, Cleared Activities & Theme sync automatically across devices. Your Render media server remains untouched."
                                } else {
                                    "Sign in with Google to sync Favorites, #Tags, Renames, Private Vault, OCR index, Cleared Activities & Settings across devices while keeping your Render server for files & folders."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isCloudSyncConnected) {
                                OutlinedButton(
                                    onClick = onGoogleCloudSignOut,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("settings_google_cloud_disconnect_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Disconnect Google Cloud Sync", fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Button(
                                    onClick = onGoogleCloudSignIn,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("settings_google_cloud_signin_button")
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Home Screen Widget Guidance
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Widgets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "GalleryHub Home Screen Widget",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Long-press your Android home screen → Widgets → GalleryHub Dashboard for a live favorite photo, quota bar, and 1-tap Upload / Scan PDF / Scan QR shortcuts synced to your theme.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1C. Android App Links & Short Share URLs Helper Card
        item {
            AppLinksSetupHelperCard(serverBaseUrl = serverHealth.baseUrl)
        }

        // 2. System-Aware Dynamic Theme Engine & 6 Accent Color Swatches
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Appearance & Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Theme Mode:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_mode_${mode.key}")
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Custom Accent Color Swatches:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            val isSelected = accentHex.equals(preset.hex, ignoreCase = true)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { onSelectAccentColor(preset.hex) }
                                    .testTag("accent_swatch_${preset.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(preset.color)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = preset.label.substringAfter(" "),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(14.dp))

                    val systemDark = isSystemInDarkTheme()
                    val previewVariant = DynamicAppIconManager.resolveVariant(
                        accentHex = accentHex,
                        themeMode = themeMode,
                        isSystemDark = systemDark,
                        dynamicSyncEnabled = dynamicAppIconEnabled
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("settings_dynamic_icon_preview_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GalleryHubLogoMark(
                                size = 50.dp,
                                accentColor = previewVariant.preset.color,
                                secondaryAccentColor = previewVariant.preset.secondaryColor,
                                isDarkTheme = previewVariant.isDark,
                                testTag = "settings_dynamic_icon_preview_logo"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dynamic App Drawer Icon",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (dynamicAppIconEnabled) {
                                        "Active: ${previewVariant.displayLabel} · Instant switch & Material You ready"
                                    } else {
                                        "Using default Terracotta icon · Android 13+ Material You ready"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (dynamicAppIconEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Switch(
                                checked = dynamicAppIconEnabled,
                                onCheckedChange = onToggleDynamicAppIcon,
                                modifier = Modifier.testTag("settings_dynamic_icon_switch")
                            )
                        }
                    }
                }
            }
        }

        // 3. Profile, Avatar Upload & Password Security
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { onPickAvatar() },
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarUrl = userSession?.resolveAvatarUrl(serverHealth.baseUrl)
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "User Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = (userSession?.name?.firstOrNull() ?: 'G').uppercase(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = userSession?.name ?: "GalleryHub User",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = userSession?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onPickAvatar,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Avatar", maxLines = 1)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Account Email") },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Change Password (Optional)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!passwordMismatchError.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = passwordMismatchError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (newPassword.isNotBlank()) {
                                if (newPassword.length < 6) {
                                    passwordMismatchError = "New password must be at least 6 characters."
                                    return@Button
                                }
                                if (newPassword != confirmPassword) {
                                    passwordMismatchError = "New password and confirmation do not match."
                                    return@Button
                                }
                            }
                            passwordMismatchError = null
                            onUpdateProfileAndPassword(
                                name,
                                email,
                                currentPassword.takeIf { it.isNotBlank() },
                                newPassword.takeIf { it.isNotBlank() }
                            )
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_profile_settings_button")
                    ) {
                        Text("Save Profile & Security Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Help & Account Controls
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Help & Account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onRelaunchOnboardingTour,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View Welcome Guide", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("settings_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out of GalleryHub", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 5. Danger Zone Operations (Delete All Files)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Delete All Media",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Permanently remove all photos, videos, and documents from your account and free up your cloud storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showDangerZoneConfirm = true },
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("danger_zone_delete_all_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete All Files", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showClearAllOfflineConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllOfflineConfirm = false },
            title = { Text("Clear All Offline Files?") },
            text = {
                Text("This removes all ${offlineVaultStats.totalOfflineCount} offline copy file(s) (${formatBytes(offlineVaultStats.totalOfflineBytes)}) stored on this device. Your cloud files stay completely safe.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllOfflineConfirm = false
                        onClearAllOfflineFiles()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Device Copies")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllOfflineConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDangerZoneConfirm) {
        AlertDialog(
            onDismissRequest = { showDangerZoneConfirm = false },
            title = { Text("Delete All Files Permanently?") },
            text = {
                Text("This Danger Zone action will permanently delete all images, videos, and documents in your account and reset your storage usage to 0 MB. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDangerZoneConfirm = false
                        onDangerZoneDeleteAllFiles()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Eradication")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDangerZoneConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MediaBreakdownPill(
    label: String,
    count: Int,
    bytes: Long,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = formatBytes(bytes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "$count item${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AppLinksSetupHelperCard(serverBaseUrl: String) {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    val diagnostics = remember(serverBaseUrl, refreshTick) {
        com.example.util.AppLinksHelper.collectDiagnostics(context, serverBaseUrl)
    }
    var showAssetLinksSnippet by remember { mutableStateOf(false) }
    var showRedirectSnippet by remember { mutableStateOf(false) }
    var copiedBanner by remember { mutableStateOf<String?>(null) }

    val statusColor = when (diagnostics.domainStatus) {
        com.example.util.AppLinkDomainStatus.VERIFIED,
        com.example.util.AppLinkDomainStatus.USER_SELECTED,
        com.example.util.AppLinkDomainStatus.LEGACY_SUPPORTED -> Color(0xFF2E9E6B)
        com.example.util.AppLinkDomainStatus.NEEDS_SETUP -> Color(0xFFD9822B)
    }

    fun copyToClipboard(label: String, content: String) {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, content))
        copiedBanner = "Copied $label to clipboard"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .testTag("settings_app_links_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "App Links & Short URLs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = diagnostics.domainStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = diagnostics.domainStatus.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Host: ${diagnostics.host}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Package: ${diagnostics.packageName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "SHA-256: ${diagnostics.sha256Fingerprints.firstOrNull() ?: "Unavailable"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!copiedBanner.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "✓ $copiedBanner",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E9E6B),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    com.example.util.AppLinksHelper.openAppLinksSystemSettings(context)
                    refreshTick++
                },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_app_links_settings_button")
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open App Link Settings", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        copyToClipboard("assetlinks.json", diagnostics.assetLinksJson)
                        showAssetLinksSnippet = !showAssetLinksSnippet
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_assetlinks_json_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("assetlinks.json", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        copyToClipboard("/s/:code route", diagnostics.shortRedirectSnippet)
                        showRedirectSnippet = !showRedirectSnippet
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_short_redirect_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("/s/ Redirect", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (showAssetLinksSnippet) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Host at https://${diagnostics.host}/.well-known/assetlinks.json",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diagnostics.assetLinksJson,
                        fontFamily = com.example.ui.theme.JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (showRedirectSnippet) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Optional Express route so browsers without GalleryHub redirect /s/:code:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diagnostics.shortRedirectSnippet,
                        fontFamily = com.example.ui.theme.JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}


