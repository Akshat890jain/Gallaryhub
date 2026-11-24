package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityItem
import com.example.data.model.ChatMessage
import com.example.data.model.FolderItem
import com.example.data.model.MediaType
import com.example.data.model.ServerHealthState
import com.example.data.model.ShareLinkItem
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.formatBytes
import com.example.data.repository.UploadCandidate
import com.example.ui.theme.JetBrainsMonoFontFamily

// --- 1. Multi-Format Upload Modal (Images, Videos, Documents up to 50MB) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadMediaBottomSheet(
    candidates: List<UploadCandidate>,
    activeFolder: FolderItem?,
    isUploading: Boolean,
    onPickPhotos: () -> Unit,
    onPickVideos: () -> Unit = onPickPhotos,
    onPickDocuments: () -> Unit,
    onRemoveCandidate: (Uri) -> Unit,
    onExecuteUpload: () -> Unit,
    onDismiss: () -> Unit,
    onOpenDocumentScanner: () -> Unit = {},
    wifiOnlyUploads: Boolean = false,
    onToggleWifiOnlyUploads: (Boolean) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Upload to Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (activeFolder != null) {
                            "Choose Photo, Video, or File for 📁 ${activeFolder.name}"
                        } else {
                            "Choose Photo, Video, or File (up to 50 MB each)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Upload Sheet")
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3 Explicit Upload Options: Photo, Video, File
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickPhotos,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_photos_videos_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Photo", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onPickVideos,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_videos_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Video", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onPickDocuments,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_documents_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("File", maxLines = 1)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Scan Document to PDF + Wi-Fi-Only Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenDocumentScanner()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("upload_sheet_scan_pdf_button")
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan to PDF", maxLines = 1)
                }
                FilterChip(
                    selected = wifiOnlyUploads,
                    onClick = { onToggleWifiOnlyUploads(!wifiOnlyUploads) },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text("Wi-Fi Only", maxLines = 1) },
                    modifier = Modifier.testTag("upload_sheet_wifi_only_chip")
                )
            }

            Spacer(Modifier.height(14.dp))

            if (candidates.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onPickPhotos() }
                        .padding(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Select Photo, Video, or File above",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Supports JPG, PNG, WebP, MP4, MOV, PDF, DOCX, XLSX & ZIP up to 50 MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates, key = { it.uri.toString() }) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val emoji = when (item.mediaType) {
                                    MediaType.IMAGE -> "🖼️"
                                    MediaType.VIDEO -> "🎥"
                                    MediaType.DOCUMENT -> "📄"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.mediaType.label} · ${formatBytes(item.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onRemoveCandidate(item.uri) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove staged file")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onExecuteUpload,
                enabled = candidates.isNotEmpty() && !isUploading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_upload_batch_button")
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Uploading…")
                } else {
                    Text("Upload ${candidates.size} File(s)")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// --- 2. Public File Share Link Modal (Masked Short Links + Scannable QR Code) ---
@Composable
fun ShareFileLinkDialog(
    item: UnifiedMediaItem,
    createdLink: ShareLinkItem?,
    serverBaseUrl: String,
    onGenerateLink: (password: String?, expiresInDays: Int?) -> Unit,
    onShareViaAndroidSheet: (String) -> Unit,
    onShareQrCode: ((fileName: String, shareUrl: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    exifScrubDefaultOn: Boolean = true,
    onShareFileDirectly: ((scrubExif: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var selectedExpiryDays by remember { mutableStateOf<Int?>(7) }
    var selectedFormat by remember { mutableStateOf(com.example.util.ShareLinkFormat.SHORT_MASKED) }
    var revealFullUrl by remember { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }
    var scrubExifOnShare by remember(item.id, exifScrubDefaultOn) { mutableStateOf(exifScrubDefaultOn) }
    val expiryOptions = listOf(
        null to "Never",
        1 to "1 Day",
        7 to "7 Days",
        30 to "30 Days"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Share File Link")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${item.originalName} (${item.formattedSize})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (createdLink == null) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Protect with Password (Optional)") },
                        placeholder = { Text("Leave blank for anyone with link") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_link_password_input")
                    )

                    Text(
                        text = "Link Expiration:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        expiryOptions.forEach { (days, label) ->
                            FilterChip(
                                selected = selectedExpiryDays == days,
                                onClick = { selectedExpiryDays = days },
                                label = { Text(label) }
                            )
                        }
                    }

                    // EXIF Metadata Scrubber per-share toggle (Default ON for photos)
                    if (item.mediaType == MediaType.IMAGE) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                            Icons.Default.PrivacyTip,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Scrub EXIF Metadata",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Strips GPS coordinates, camera model & timestamps",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = scrubExifOnShare,
                                        onCheckedChange = { scrubExifOnShare = it },
                                        modifier = Modifier.testTag("share_exif_scrub_toggle")
                                    )
                                }
                                if (onShareFileDirectly != null) {
                                    OutlinedButton(
                                        onClick = { onShareFileDirectly(scrubExifOnShare) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("share_scrubbed_photo_direct_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (scrubExifOnShare) "Share Scrubbed Photo Directly" else "Share Original Photo Directly"
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val activeUrl = remember(createdLink, serverBaseUrl, selectedFormat) {
                        com.example.util.ShareLinkCodec.buildUrlForFormat(
                            baseUrl = serverBaseUrl,
                            rawToken = createdLink.token,
                            format = selectedFormat
                        )
                    }
                    val maskedPreview = remember(createdLink, serverBaseUrl, selectedFormat) {
                        createdLink.buildMaskedPreview(serverBaseUrl, selectedFormat)
                    }

                    // Link Format Switcher (Short Masked / App Link / Direct Web)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.util.ShareLinkFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = selectedFormat == fmt,
                                onClick = {
                                    selectedFormat = fmt
                                    copiedFeedback = false
                                },
                                label = { Text(fmt.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("share_format_${fmt.name.lowercase()}")
                            )
                        }
                    }

                    // Embedded Scannable QR Code Card
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.util.QrCodePreviewCard(
                            payload = activeUrl,
                            caption = when (selectedFormat) {
                                com.example.util.ShareLinkFormat.SHORT_MASKED -> "Scan to open Short Masked Link in GalleryHub"
                                com.example.util.ShareLinkFormat.APP_DIRECT -> "Scan to launch directly in GalleryHub app"
                                com.example.util.ShareLinkFormat.DIRECT_SERVER -> "Scan to open Direct Web / App Link"
                            },
                            qrSize = 168.dp
                        )
                    }

                    // Masked Link Display Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (copiedFeedback) "✓ Copied to Clipboard" else "Masked Share Link Ready",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = { revealFullUrl = !revealFullUrl },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("toggle_reveal_share_url")
                                ) {
                                    Text(
                                        text = if (revealFullUrl) "Mask" else "Reveal",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Text(
                                text = if (revealFullUrl) activeUrl else maskedPreview,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = if (revealFullUrl) 3 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("GalleryHub Link", activeUrl))
                                        copiedFeedback = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("copy_share_link_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                Button(
                                    onClick = { onShareViaAndroidSheet(activeUrl) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("send_share_link_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Send")
                                }
                            }
                            if (onShareQrCode != null) {
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = { onShareQrCode(item.originalName, activeUrl) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("share_qr_image_button")
                                ) {
                                    Text("Share Scannable QR Image", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (createdLink == null) {
                Button(
                    onClick = { onGenerateLink(password.takeIf { it.isNotBlank() }, selectedExpiryDays) },
                    modifier = Modifier.testTag("generate_share_link_button")
                ) {
                    Text("Create Masked Link")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (createdLink == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

// --- 3. Collaborative Folder Sharing Modal ---
@Composable
fun ShareFolderCollaboratorsDialog(
    folder: FolderItem,
    onInviteCollaborator: (email: String, permission: String) -> Unit,
    onRevokeCollaborator: (userId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("view") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Share \"${folder.name}\"")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Invite by Email") },
                    placeholder = { Text("friend@example.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_share_email_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Access:", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = permission == "view",
                        onClick = { permission = "view" },
                        label = { Text("Can View") }
                    )
                    FilterChip(
                        selected = permission == "edit",
                        onClick = { permission = "edit" },
                        label = { Text("Can Edit") }
                    )
                }

                Button(
                    onClick = {
                        if (email.isNotBlank()) {
                            onInviteCollaborator(email, permission)
                            email = ""
                        }
                    },
                    enabled = email.contains("@"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_collaborator_button")
                ) {
                    Text("Send Invite")
                }

                HorizontalDivider()

                Text(
                    text = "People with Access",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Folder Owner",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = folder.ownerName.ifBlank { "You" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (folder.ownerEmail.isNotBlank()) {
                                Text(
                                    text = folder.ownerEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Owner",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                folder.sharedWith.forEach { collab ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = collab.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${collab.email} · ${if (collab.permission == "edit") "Can Edit" else "Can View"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onRevokeCollaborator(collab.userId) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove Access",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

// --- 4. Recent Activity Sheet ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityAuditFeedSheet(
    activities: List<ActivityItem>,
    onClearAll: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your recent uploads, edits, and shared files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activities.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAll,
                            modifier = Modifier.testTag("clear_activity_history_button")
                        ) {
                            Text("Clear", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Activity Feed")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (activities.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No recent activity",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "New uploads, folder updates, and shared links will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activities, key = { it.id }) { act ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(act.actionEmoji, style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = act.actionDescription,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = act.relativeTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// --- 5. AI Assistant Chatbot Sheet ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantChatbotSheet(
    messages: List<ChatMessage>,
    isSending: Boolean,
    onSendPrompt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "GalleryHub Assistant",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Ask anything about organizing, editing, or sharing your files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Assistant")
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            color = if (msg.isUser) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                bottomEnd = if (msg.isUser) 4.dp else 16.dp
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (msg.isUser) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (!msg.isUser && msg.suggestions.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                msg.suggestions.forEach { chip ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onSendPrompt(chip) },
                                        label = { Text(chip, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask a question…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chatbot_input_field")
                )
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSendPrompt(input)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("chatbot_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// --- 6. Cloud Sync Status Dialog (No Database or Backend URLs exposed to users) ---
@Composable
fun ServerEndpointConfigDialog(
    serverHealth: ServerHealthState,
    onTestAndSaveUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Cloud Sync Status")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Your photos, videos, and documents are automatically synced to your personal cloud archive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (serverHealth.isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4E9F76),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Cloud Sync Active",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "500 MB Personal Storage Quota",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

// --- 7. 6-Step Welcome Tour Banner ---
@Composable
fun GuidedOnboardingTourBanner(
    stepIndex: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onDismiss: () -> Unit
) {
    val steps = listOf(
        "Welcome to GalleryHub (1/6)" to "Keep all your photos, videos, and documents safe and organized in one place.",
        "Quick Upload (2/6)" to "Tap the '+ Upload' button anytime to add photos, videos, PDFs, or documents from your device.",
        "Switch Views & Filter (3/6)" to "Browse by Photos, Videos, or Documents, and switch between Grid, List, and Masonry views.",
        "Color-Coded Folders (4/6)" to "Group files into custom-colored folders and invite friends or teammates to view or edit.",
        "Recent Activity (5/6)" to "Tap the clock icon at the top right to see your recent uploads, edits, and shared links.",
        "Personalize Your App (6/6)" to "Visit Settings to pick your favorite accent color, switch between Light & Dark mode, and check your storage."
    )
    val (title, description) = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text("Skip Tour")
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stepIndex > 0) {
                    TextButton(onClick = onPrev) {
                        Text("Back")
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.testTag("onboarding_next_button")
                ) {
                    Text(if (stepIndex >= 5) "Finish" else "Next")
                }
            }
        }
    }
}
