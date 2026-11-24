package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FolderItem
import com.example.data.model.ShareLinkItem
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.formatRelativeTime
import com.example.ui.theme.FolderColorPresets
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.PublicShareViewState

// --- Feature 6 & 7: Folders Management & RBAC Collaborative Folders Screen ---
@Composable
fun FoldersTabScreen(
    myFolders: List<FolderItem>,
    sharedFolders: List<FolderItem>,
    onOpenFolder: (FolderItem) -> Unit,
    onCreateFolder: (name: String, colorHex: String) -> Unit,
    onUpdateFolder: (folderId: String, name: String, colorHex: String) -> Unit,
    onDeleteFolder: (FolderItem) -> Unit,
    onManageCollaborators: (FolderItem) -> Unit,
    hiddenVaultCount: Int = 0,
    onOpenPrivateVault: () -> Unit = {},
    onOpenStorageCleaner: () -> Unit = {},
    onOpenDocumentScanner: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<FolderItem?>(null) }
    var deletingFolder by remember { mutableStateOf<FolderItem?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Folders",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Organize your files and share folders with friends or teammates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("create_folder_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Folder", maxLines = 1, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Quick Tools Strip (Private Vault, Storage Cleaner, Scan PDF)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenPrivateVault)
                        .testTag("folders_quick_vault_button")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Private Vault", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (hiddenVaultCount > 0) "Vault ($hiddenVaultCount)" else "Vault",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenStorageCleaner)
                        .testTag("folders_quick_cleaner_button")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Duplicate Cleaner", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Cleaner", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenDocumentScanner)
                        .testTag("folders_quick_scan_pdf_button")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = "Scan PDF", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Scan PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        // Owned Folders Section
        item {
            Text(
                text = "My Folders (${myFolders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (myFolders.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📁", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("No folders created yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Create color-coded folders to organize images, videos, and documents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(myFolders, key = { it.id }) { folder ->
                val folderColor = parseHexColor(folder.color)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .clickable { onOpenFolder(folder) }
                        .testTag("folder_card_${folder.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(folderColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = folderColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${folder.fileCount} files · ${folder.imageCount} 📷 · ${folder.videoCount} 🎥 · ${folder.docCount} 📄",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (folder.sharedWith.isNotEmpty()) {
                                    Text(
                                        text = "Shared with ${folder.sharedWith.size} collaborator(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onManageCollaborators(folder) },
                                modifier = Modifier.testTag("share_folder_${folder.id}")
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "Share Folder RBAC",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { editingFolder = folder }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename or Recolor Folder")
                            }
                            IconButton(onClick = { deletingFolder = folder }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Folder",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // Shared With Me Section (/api/folders/shared)
        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Shared With Me (${sharedFolders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (sharedFolders.isEmpty()) {
            item {
                Text(
                    text = "Folders shared with your account by teammates will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(sharedFolders, key = { it.id }) { folder ->
                val folderColor = parseHexColor(folder.color)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .clickable { onOpenFolder(folder) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(folderColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderShared,
                                    contentDescription = null,
                                    tint = folderColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Owner: ${folder.ownerName} (${folder.ownerEmail})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${folder.fileCount} files · ${folder.imageCount} 📷 · ${folder.videoCount} 🎥 · ${folder.docCount} 📄",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (folder.myPermission == "edit") "Editor" else "Viewer",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog || editingFolder != null) {
        val currentEditing = editingFolder
        var folderName by remember(currentEditing) { mutableStateOf(currentEditing?.name.orEmpty()) }
        var selectedHex by remember(currentEditing) { mutableStateOf(currentEditing?.color ?: "#4285F4") }

        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                editingFolder = null
            },
            title = { Text(if (currentEditing == null) "Create New Folder" else "Edit Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("Folder Name") },
                        placeholder = { Text("e.g., Travel 2026") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("folder_name_input")
                    )

                    Text("Folder Accent Color:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FolderColorPresets.forEach { (hex, color) ->
                            val isSelected = selectedHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedHex = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentEditing == null) {
                            onCreateFolder(folderName, selectedHex)
                        } else {
                            onUpdateFolder(currentEditing.id, folderName, selectedHex)
                        }
                        showCreateDialog = false
                        editingFolder = null
                    },
                    enabled = folderName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_save_folder_button")
                ) {
                    Text(if (currentEditing == null) "Create" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        editingFolder = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete Folder \"${folder.name}\"?") },
            text = {
                Text("Deleting a folder safely unfiles its ${folder.fileCount} asset(s) back to the Root Gallery without deleting any media files.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFolder(folder)
                        deletingFolder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- Feature 8: Public File Sharing Dashboard & Masked Short Link Explorer ---
@Composable
fun ShareLinksTabScreen(
    myShareLinks: List<ShareLinkItem>,
    publicShareState: PublicShareViewState,
    serverBaseUrl: String,
    onInspectPublicToken: (tokenOrUrl: String, password: String?) -> Unit,
    onOpenFullScreenShareLink: (String) -> Unit,
    onResetPublicInspector: () -> Unit,
    onRevokeLink: (ShareLinkItem) -> Unit,
    onShareViaAndroidSheet: (String) -> Unit,
    onShareQrCode: (fileName: String, shareUrl: String) -> Unit,
    onPreviewUnlockedMedia: (UnifiedMediaItem) -> Unit,
    onOpenQrScanner: () -> Unit = {}
) {
    val context = LocalContext.current
    var tokenInput by remember { mutableStateOf("") }
    var passwordPromptInput by remember { mutableStateOf("") }
    var qrPreviewLink by remember { mutableStateOf<ShareLinkItem?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "Shared Links",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Manage short masked links, scan QR codes, or open any GalleryHub link in-app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onOpenQrScanner,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("links_scan_qr_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan QR", maxLines = 1, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Public Share Link Explorer Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Open a Shared Link",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = { Text("Paste /s/…, /api/share/…, or galleryhub://") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("public_token_input")
                        )
                        Button(
                            onClick = { onOpenFullScreenShareLink(tokenInput) },
                            enabled = tokenInput.isNotBlank(),
                            modifier = Modifier.testTag("open_public_token_button")
                        ) {
                            Text("Open")
                        }
                    }

                    // 4-State Machine for inline SharePageView fallback
                    when (publicShareState) {
                        PublicShareViewState.Idle -> {}
                        PublicShareViewState.Loading -> {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Opening shared link…")
                            }
                        }

                        is PublicShareViewState.PasswordRequired -> {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Password Protected Link",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (!publicShareState.errorMessage.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = publicShareState.errorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = passwordPromptInput,
                                        onValueChange = { passwordPromptInput = it },
                                        label = { Text("Enter Share Link Password") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                onInspectPublicToken(
                                                    publicShareState.token,
                                                    passwordPromptInput
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Unlock File")
                                        }
                                        TextButton(onClick = onResetPublicInspector) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }

                        is PublicShareViewState.Unlocked -> {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "✓ Unlocked Shared Asset: ${publicShareState.link.fileName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total Views: ${publicShareState.link.viewCount} · Type: ${publicShareState.link.fileType.label}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        publicShareState.mediaItem?.let { item ->
                                            Button(onClick = { onPreviewUnlockedMedia(item) }) {
                                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("View in Lightbox")
                                            }
                                        }
                                        OutlinedButton(onClick = onResetPublicInspector) {
                                            Text("Close")
                                        }
                                    }
                                }
                            }
                        }

                        is PublicShareViewState.Error -> {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = publicShareState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onResetPublicInspector) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Share Links Roster (GET /api/share/my-links)
        item {
            Text(
                text = "My Active Share Links (${myShareLinks.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (myShareLinks.isEmpty()) {
            item {
                Text(
                    text = "No shared links yet. Open any photo, video, or document in your gallery and tap 'Share Link' to create a short masked link with QR code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(myShareLinks, key = { it.id }) { link ->
                val shortMaskedUrl = remember(link.token, serverBaseUrl) {
                    link.buildShortMaskedUrl(serverBaseUrl)
                }
                val maskedPreview = remember(link.token, serverBaseUrl) {
                    link.buildMaskedPreview(serverBaseUrl)
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = link.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append("${link.viewCount} views · Created ${formatRelativeTime(link.createdAt)}")
                                        if (link.hasPassword) append(" · 🔒 Protected")
                                        if (link.expiresAt != null) append(" · ⏳ Expires ${formatRelativeTime(link.expiresAt)}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { qrPreviewLink = link },
                                modifier = Modifier.testTag("show_qr_link_${link.id}")
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = "Show QR Code and Link Formats",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onRevokeLink(link) },
                                modifier = Modifier.testTag("revoke_link_${link.id}")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Revoke Share Link",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = maskedPreview,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "MASKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onOpenFullScreenShareLink(shortMaskedUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open")
                            }

                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Masked Share Link", shortMaskedUrl))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }

                            Button(
                                onClick = { onShareViaAndroidSheet(shortMaskedUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    }

    qrPreviewLink?.let { link ->
        var selectedFmt by remember(link.id) { mutableStateOf(com.example.util.ShareLinkFormat.SHORT_MASKED) }
        val activeUrl = remember(link.token, serverBaseUrl, selectedFmt) {
            com.example.util.ShareLinkCodec.buildUrlForFormat(serverBaseUrl, link.token, selectedFmt)
        }
        AlertDialog(
            onDismissRequest = { qrPreviewLink = null },
            title = { Text(link.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.util.ShareLinkFormat.entries.forEach { fmt ->
                            androidx.compose.material3.FilterChip(
                                selected = selectedFmt == fmt,
                                onClick = { selectedFmt = fmt },
                                label = { Text(fmt.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    com.example.util.QrCodePreviewCard(
                        payload = activeUrl,
                        caption = "Scan to open directly in GalleryHub",
                        qrSize = 180.dp
                    )
                    Text(
                        text = link.buildMaskedPreview(serverBaseUrl, selectedFmt),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("GalleryHub Link", activeUrl))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy URL")
                        }
                        Button(
                            onClick = { onShareQrCode(link.fileName, activeUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Share QR")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { qrPreviewLink = null }) {
                    Text("Close")
                }
            }
        )
    }
}
