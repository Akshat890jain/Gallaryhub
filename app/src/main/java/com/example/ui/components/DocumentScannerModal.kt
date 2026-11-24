package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.util.DocumentPdfBuilder
import com.example.util.ScanFilterPreset
import com.example.util.ScannedPageDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun DocumentScannerModal(
    targetFolderName: String?,
    onPdfCreatedAndUpload: (File, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var pages by remember { mutableStateOf<List<ScannedPageDraft>>(emptyList()) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isReviewMode by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var isBuildingPdf by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pdfTitle by remember {
        mutableStateOf(
            "Scan_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}"
        )
    }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

    // Zero-permission Photo Picker fallback to add pages without camera
    val importPagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val scanDir = File(context.cacheDir, "scanner_pages").apply { mkdirs() }
                val imported = uris.mapNotNull { uri ->
                    runCatching {
                        val dest = File(scanDir, "page_${UUID.randomUUID()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(dest).use { out -> input.copyTo(out) }
                        }
                        if (dest.exists() && dest.length() > 0L) {
                            ScannedPageDraft(id = UUID.randomUUID().toString(), imageFile = dest)
                        } else null
                    }.getOrNull()
                }
                withContext(Dispatchers.Main) {
                    if (imported.isNotEmpty()) {
                        pages = pages + imported
                        selectedPageIndex = (pages.size - 1).coerceAtLeast(0)
                        isReviewMode = true
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler {
        if (isReviewMode && pages.isNotEmpty()) {
            isReviewMode = false
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(56f)
            .background(Color(0xFF0A0D10))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("document_scanner_modal")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isReviewMode) "Review & Export PDF" else "Document Scanner",
                        fontFamily = PlayfairDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Text(
                        text = buildString {
                            append("${pages.size} page(s)")
                            if (!targetFolderName.isNullOrBlank()) {
                                append(" · Uploads to $targetFolderName")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("scanner_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Scanner", tint = Color.White)
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (!isReviewMode) {
                // Live CameraX Capture Mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF14181F))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
                        DisposableEffect(Unit) {
                            onDispose {
                                runCatching { cameraProviderRef?.unbindAll() }
                            }
                        }
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    runCatching {
                                        val provider = cameraProviderFuture.get()
                                        cameraProviderRef = provider
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()
                                        imageCaptureRef = capture
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture
                                        )
                                    }.onFailure { err ->
                                        errorMessage = "Camera unavailable: ${err.localizedMessage}. You can import pages from Photos."
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Document alignment guide overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(26.dp)
                                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Camera permission lets you scan paper documents directly. Or import existing photos to build a multi-page PDF.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Allow Camera")
                                }
                                OutlinedButton(
                                    onClick = {
                                        importPagesLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Text("Import Photos")
                                }
                            }
                        }
                    }
                }

                // Bottom Capture Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            importPagesLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("scanner_import_photos_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }

                    // Shutter Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(68.dp)
                            .border(3.dp, Color.White, CircleShape)
                            .clip(CircleShape)
                            .clickable(enabled = hasCameraPermission && !isCapturing) {
                                val capture = imageCaptureRef ?: return@clickable
                                isCapturing = true
                                errorMessage = null
                                val scanDir = File(context.cacheDir, "scanner_pages").apply { mkdirs() }
                                val outFile = File(scanDir, "scan_${System.currentTimeMillis()}.jpg")
                                val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                capture.takePicture(
                                    opts,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            isCapturing = false
                                            pages = pages + ScannedPageDraft(
                                                id = UUID.randomUUID().toString(),
                                                imageFile = outFile
                                            )
                                            selectedPageIndex = (pages.size - 1).coerceAtLeast(0)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturing = false
                                            errorMessage = "Capture failed: ${exception.localizedMessage}"
                                        }
                                    }
                                )
                            }
                            .testTag("scanner_capture_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCapturing) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Capture Page", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                    }

                    Button(
                        onClick = { isReviewMode = true },
                        enabled = pages.isNotEmpty(),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("scanner_review_button")
                    ) {
                        Text("Next (${pages.size})")
                    }
                }
            } else {
                // Multi-Page Review, Filter Enhancement & PDF Export Mode
                val currentPage = pages.getOrNull(selectedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
                var previewBmp by remember { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(currentPage) {
                    previewBmp?.let { runCatching { it.recycle() } }
                    previewBmp = currentPage?.let { draft ->
                        withContext(Dispatchers.IO) {
                            DocumentPdfBuilder.loadFilteredPreviewBitmap(draft, maxDim = 1100)
                        }
                    }
                }
                DisposableEffect(Unit) {
                    onDispose {
                        previewBmp?.let { runCatching { it.recycle() } }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF14181F)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBmp
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Scanned page preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Page Controls & Enhancement Filters
                if (currentPage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScanFilterPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = currentPage.filter == preset,
                                onClick = {
                                    pages = pages.mapIndexed { idx, p ->
                                        if (idx == selectedPageIndex) p.copy(filter = preset) else p
                                    }
                                },
                                label = { Text(preset.label) }
                            )
                        }
                        IconButton(
                            onClick = {
                                pages = pages.mapIndexed { idx, p ->
                                    if (idx == selectedPageIndex) p.copy(rotationDegrees = (p.rotationDegrees + 90) % 360) else p
                                }
                            }
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate Page", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                val nextList = pages.filterIndexed { idx, _ -> idx != selectedPageIndex }
                                pages = nextList
                                selectedPageIndex = selectedPageIndex.coerceAtMost((nextList.size - 1).coerceAtLeast(0))
                                if (nextList.isEmpty()) isReviewMode = false
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Page", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Multi-Page Selector Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { idx, _ ->
                        FilterChip(
                            selected = idx == selectedPageIndex,
                            onClick = { selectedPageIndex = idx },
                            label = { Text("Page ${idx + 1}") }
                        )
                    }
                    OutlinedButton(
                        onClick = { isReviewMode = false },
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Page")
                    }
                }

                // Filename + Save Multi-Page PDF Button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = pdfTitle,
                        onValueChange = { pdfTitle = it },
                        label = { Text("PDF File Name") },
                        suffix = { Text(".pdf") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (pages.isEmpty() || isBuildingPdf) return@Button
                            isBuildingPdf = true
                            errorMessage = null
                            scope.launch {
                                val result = DocumentPdfBuilder.buildMultiPagePdf(context, pages, pdfTitle)
                                isBuildingPdf = false
                                result.fold(
                                    onSuccess = { pdfFile ->
                                        onPdfCreatedAndUpload(pdfFile, pdfFile.name)
                                    },
                                    onFailure = { err ->
                                        errorMessage = err.localizedMessage ?: "Could not create PDF"
                                    }
                                )
                            }
                        },
                        enabled = pages.isNotEmpty() && !isBuildingPdf,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("scanner_create_pdf_button")
                    ) {
                        if (isBuildingPdf) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Building PDF…")
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Merge ${pages.size} Page(s) & Upload PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
