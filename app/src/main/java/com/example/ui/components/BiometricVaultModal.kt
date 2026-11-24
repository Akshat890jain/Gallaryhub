package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.data.model.MediaType
import com.example.data.model.UnifiedMediaItem
import com.example.ui.theme.PlayfairDisplayFontFamily

@Composable
fun BiometricVaultModal(
    hiddenItems: List<UnifiedMediaItem>,
    serverBaseUrl: String,
    isUnlocked: Boolean,
    hasPinConfigured: Boolean,
    biometricEnabled: Boolean,
    onUnlockSuccess: () -> Unit,
    onSetupPin: (String) -> Unit,
    onVerifyPin: (String) -> Boolean,
    onLockNow: () -> Unit,
    onUnhideItem: (UnifiedMediaItem) -> Unit,
    onPreviewItem: (UnifiedMediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = remember(context) {
        val mgr = BiometricManager.from(context)
        mgr.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var pinEntry by remember { mutableStateOf("") }
    var confirmFirstPin by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun launchBiometricPrompt() {
        val host = activity ?: return
        val executor = ContextCompat.getMainExecutor(host)
        val prompt = BiometricPrompt(
            host,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    pinError = null
                    onUnlockSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        pinError = errString.toString()
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Private Vault")
            .setSubtitle("Use Fingerprint or Face Unlock to access hidden media")
            .setNegativeButtonText("Use 4-Digit PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        runCatching { prompt.authenticate(info) }
    }

    // Automatically prompt biometrics when opening a configured locked vault
    LaunchedEffect(isUnlocked, hasPinConfigured, biometricEnabled, biometricAvailable) {
        if (!isUnlocked && hasPinConfigured && biometricEnabled && biometricAvailable) {
            launchBiometricPrompt()
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(55f)
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("biometric_vault_modal")
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Private Vault",
                            fontFamily = PlayfairDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isUnlocked) {
                                "${hiddenItems.size} hidden item(s) · Auto-locks in background"
                            } else {
                                "Biometric & PIN protected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUnlocked) {
                        OutlinedButton(
                            onClick = onLockNow,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("vault_lock_now_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lock")
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("vault_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Vault")
                    }
                }
            }

            if (!isUnlocked) {
                // Locked / Setup PIN View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val promptTitle = when {
                        !hasPinConfigured && confirmFirstPin == null -> "Create 4-Digit Vault PIN"
                        !hasPinConfigured && confirmFirstPin != null -> "Confirm Your Vault PIN"
                        else -> "Enter Vault PIN"
                    }
                    val promptSubtitle = when {
                        !hasPinConfigured && confirmFirstPin == null ->
                            "Sets your fallback PIN for Fingerprint / Face Unlock."
                        !hasPinConfigured && confirmFirstPin != null ->
                            "Re-enter the same 4 digits to confirm."
                        else -> "Hidden photos, videos, and documents stay invisible outside the vault."
                    }

                    Text(
                        text = promptTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = promptSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    // 4 PIN Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { idx ->
                            val filled = idx < pinEntry.length
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.5.dp,
                                        if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                            )
                        }
                    }

                    if (!pinError.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = pinError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(26.dp))

                    // Tactile 3x4 PIN Keypad
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("BIO", "0", "DEL")
                    )

                    fun handleDigit(digit: String) {
                        if (pinEntry.length >= 4) return
                        pinError = null
                        val next = pinEntry + digit
                        pinEntry = next
                        if (next.length == 4) {
                            if (!hasPinConfigured) {
                                val first = confirmFirstPin
                                if (first == null) {
                                    confirmFirstPin = next
                                    pinEntry = ""
                                } else if (first == next) {
                                    onSetupPin(next)
                                    confirmFirstPin = null
                                    pinEntry = ""
                                } else {
                                    pinError = "PINs did not match. Try again."
                                    confirmFirstPin = null
                                    pinEntry = ""
                                }
                            } else {
                                if (onVerifyPin(next)) {
                                    pinEntry = ""
                                } else {
                                    pinError = "Incorrect PIN. Please try again."
                                    pinEntry = ""
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                row.forEach { key ->
                                    when (key) {
                                        "BIO" -> {
                                            if (hasPinConfigured && biometricEnabled && biometricAvailable) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                    modifier = Modifier
                                                        .size(66.dp)
                                                        .clip(CircleShape)
                                                        .clickable { launchBiometricPrompt() }
                                                        .testTag("vault_biometric_button")
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.Fingerprint,
                                                            contentDescription = "Unlock with Biometrics",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Spacer(Modifier.size(66.dp))
                                            }
                                        }

                                        "DEL" -> {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                                modifier = Modifier
                                                    .size(66.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        if (pinEntry.isNotEmpty()) {
                                                            pinEntry = pinEntry.dropLast(1)
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Backspace,
                                                        contentDescription = "Delete digit",
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }

                                        else -> {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surface,
                                                tonalElevation = 2.dp,
                                                modifier = Modifier
                                                    .size(66.dp)
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                        CircleShape
                                                    )
                                                    .clip(CircleShape)
                                                    .clickable { handleDigit(key) }
                                                    .testTag("vault_pin_key_$key")
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = key,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (hiddenItems.isEmpty()) {
                // Unlocked Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Your Private Vault is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Open any file's ⋮ menu or long-press items in Gallery and choose \"Hide in Vault\" to move them here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                        Text("Back to Gallery")
                    }
                }
            } else {
                // Unlocked Hidden Media Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(hiddenItems, key = { it.id }) { item ->
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
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPreviewItem(item) }
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.15f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val thumb = remember(item.id, item.offlineFilePath, serverBaseUrl) {
                                        item.resolveThumbnailUrl(serverBaseUrl)
                                    }
                                    if (thumb != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(thumb)
                                                .size(Size(420, 420))
                                                .crossfade(120)
                                                .build(),
                                            contentDescription = item.originalName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (item.mediaType == MediaType.VIDEO) {
                                                Icons.Default.PlayCircle
                                            } else {
                                                Icons.Default.Description
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.originalName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.formattedSize} · ${item.mediaType.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { onUnhideItem(item) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Unhide", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
