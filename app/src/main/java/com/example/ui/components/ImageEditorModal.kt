package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.UnifiedMediaItem
import com.example.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.roundToInt

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class EditorToolTab(val label: String) {
    PRESETS("Presets"),
    ADJUST("Sliders"),
    TRANSFORM("Transform"),
    CROP("Crop"),
    MARKUP("Markup")
}

enum class MarkupTool(val label: String) {
    PEN("Pen"),
    HIGHLIGHTER("Highlight"),
    ARROW("Arrow"),
    TEXT("Text"),
    REDACT_BLUR("Redact")
}

sealed class MarkupAnnotation {
    data class FreehandPath(
        val points: List<Pair<Float, Float>>, // normalized 0..1
        val colorArgb: Int,
        val strokeWidthFraction: Float,
        val isHighlighter: Boolean
    ) : MarkupAnnotation()

    data class ArrowShape(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val colorArgb: Int,
        val strokeWidthFraction: Float
    ) : MarkupAnnotation()

    data class TextCallout(
        val normX: Float,
        val normY: Float,
        val text: String,
        val colorArgb: Int
    ) : MarkupAnnotation()

    data class RedactBlurRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) : MarkupAnnotation()
}

enum class FilterPreset(
    val label: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val sepia: Float,
    val grayscale: Float,
    val blur: Float
) {
    NONE("None", 100f, 100f, 100f, 0f, 0f, 0f),
    VIVID("Vivid", 110f, 120f, 140f, 0f, 0f, 0f),
    WARM("Warm", 104f, 105f, 110f, 20f, 0f, 0f),
    COOL("Cool", 100f, 105f, 80f, 0f, 0f, 0f),
    BW("B&W", 100f, 115f, 0f, 0f, 100f, 0f),
    VINTAGE("Vintage", 98f, 90f, 85f, 40f, 0f, 0f),
    DRAMATIC("Dramatic", 90f, 150f, 110f, 0f, 0f, 0f),
    SOFT("Soft", 105f, 95f, 95f, 5f, 0f, 2f)
}

@Composable
fun ImageEditorModal(
    item: UnifiedMediaItem,
    serverBaseUrl: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveEditedBitmap: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeTab by remember { mutableStateOf(EditorToolTab.PRESETS) }
    var selectedPreset by remember { mutableStateOf(FilterPreset.NONE) }

    // 5 Parametric Sliders (matching GalleryHub spec)
    var brightness by remember { mutableFloatStateOf(100f) } // 0..200%
    var contrast by remember { mutableFloatStateOf(100f) }   // 0..200%
    var saturation by remember { mutableFloatStateOf(100f) } // 0..200%
    var sepia by remember { mutableFloatStateOf(0f) }        // 0..100%
    var grayscale by remember { mutableFloatStateOf(0f) }    // 0..100%
    var blurRadius by remember { mutableFloatStateOf(0f) }   // 0..10px

    // Transform State
    var rotationDegrees by remember { mutableIntStateOf(0) } // 0, 90, 180, 270
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }

    // Normalized Crop Box (0f..1f)
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.95f) }
    var isCropApplied by remember { mutableStateOf(false) }

    // Markup & Annotation State (Pen, Highlighter, Arrow, Text Callout, Blur/Pixelate Redaction)
    var selectedMarkupTool by remember { mutableStateOf(MarkupTool.PEN) }
    var selectedMarkupColorArgb by remember { mutableIntStateOf(Color.parseColor("#EF4444")) }
    var markupStrokeWidth by remember { mutableFloatStateOf(0.010f) }
    val annotations = remember { mutableStateListOf<MarkupAnnotation>() }
    var activeDragPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var activeShapeStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var activeShapeEnd by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var pendingTextAnchor by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var pendingTextDraft by remember { mutableStateOf("") }

    LaunchedEffect(item.id, item.offlineFilePath, item.localUri, serverBaseUrl) {
        val loaded = withContext(Dispatchers.IO) {
            loadEditorSourceBitmap(
                context = context,
                item = item,
                serverBaseUrl = serverBaseUrl,
                maxDimensionPx = 2048
            ) ?: createFallbackStudioBitmap(item.originalName)
        }
        sourceBitmap = loaded
    }

    val annotationsSnapshot = annotations.toList()
    val previewBitmap = remember(
        sourceBitmap,
        brightness,
        contrast,
        saturation,
        sepia,
        grayscale,
        blurRadius,
        rotationDegrees,
        flipH,
        flipV,
        isCropApplied,
        cropLeft,
        cropTop,
        cropRight,
        cropBottom,
        annotationsSnapshot
    ) {
        sourceBitmap?.let { src ->
            renderStudioPipelineBitmap(
                source = src,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                sepia = sepia,
                grayscale = grayscale,
                blurPx = blurRadius,
                rotationDegrees = rotationDegrees,
                flipH = flipH,
                flipV = flipV,
                cropLeft = if (isCropApplied) cropLeft else 0f,
                cropTop = if (isCropApplied) cropTop else 0f,
                cropRight = if (isCropApplied) cropRight else 1f,
                cropBottom = if (isCropApplied) cropBottom else 1f,
                annotations = annotationsSnapshot
            )
        }
    }

    fun applyPreset(preset: FilterPreset) {
        selectedPreset = preset
        brightness = preset.brightness
        contrast = preset.contrast
        saturation = preset.saturation
        sepia = preset.sepia
        grayscale = preset.grayscale
        blurRadius = preset.blur
    }

    fun resetAll() {
        applyPreset(FilterPreset.NONE)
        rotationDegrees = 0
        flipH = false
        flipV = false
        cropLeft = 0.05f
        cropTop = 0.05f
        cropRight = 0.95f
        cropBottom = 0.95f
        isCropApplied = false
        annotations.clear()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color(0xFF090D16)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Studio Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("editor_close_button")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Image Editor",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }
                        Column {
                            Text(
                                text = "Image Editing Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Text(
                                text = item.originalName,
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = ::resetAll,
                            modifier = Modifier.testTag("editor_reset_button")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reset Adjustments",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reset")
                        }
                        Button(
                            onClick = {
                                val src = sourceBitmap ?: return@Button
                                val finalBmp = renderStudioPipelineBitmap(
                                    source = src,
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                    sepia = sepia,
                                    grayscale = grayscale,
                                    blurPx = blurRadius,
                                    rotationDegrees = rotationDegrees,
                                    flipH = flipH,
                                    flipV = flipV,
                                    cropLeft = if (activeTab == EditorToolTab.CROP || isCropApplied) cropLeft else 0f,
                                    cropTop = if (activeTab == EditorToolTab.CROP || isCropApplied) cropTop else 0f,
                                    cropRight = if (activeTab == EditorToolTab.CROP || isCropApplied) cropRight else 1f,
                                    cropBottom = if (activeTab == EditorToolTab.CROP || isCropApplied) cropBottom else 1f,
                                    annotations = annotations.toList()
                                )
                                onSaveEditedBitmap(finalBmp)
                            },
                            enabled = !isSaving && previewBitmap != null,
                            modifier = Modifier.testTag("editor_save_copy_button")
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save as Copy",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSaving) "Saving…" else "Save as Copy")
                        }
                    }
                }

                // Live Canvas Preview Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFF111827)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Live Edited Image Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Interactive Crop Overlay when CROP tab is active
                            if (activeTab == EditorToolTab.CROP) {
                                ComposeCanvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val w = size.width.coerceAtLeast(1).toFloat()
                                                val h = size.height.coerceAtLeast(1).toFloat()
                                                val normX = change.position.x / w
                                                val normY = change.position.y / h
                                                val dx = dragAmount.x / w
                                                val dy = dragAmount.y / h

                                                val midX = (cropLeft + cropRight) / 2f
                                                val midY = (cropTop + cropBottom) / 2f
                                                if (normX < midX) {
                                                    cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.15f)
                                                } else {
                                                    cropRight = (cropRight + dx).coerceIn(cropLeft + 0.15f, 1f)
                                                }
                                                if (normY < midY) {
                                                    cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.15f)
                                                } else {
                                                    cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.15f, 1f)
                                                }
                                            }
                                        }
                                ) {
                                    val leftPx = size.width * cropLeft
                                    val topPx = size.height * cropTop
                                    val rightPx = size.width * cropRight
                                    val bottomPx = size.height * cropBottom
                                    drawRect(
                                        color = androidx.compose.ui.graphics.Color(0xFF38BDF8),
                                        topLeft = Offset(leftPx, topPx),
                                        size = Size(rightPx - leftPx, bottomPx - topPx),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }

                            // Interactive Markup & Redaction Canvas when MARKUP tab is active
                            if (activeTab == EditorToolTab.MARKUP) {
                                ComposeCanvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(selectedMarkupTool, selectedMarkupColorArgb, markupStrokeWidth) {
                                            if (selectedMarkupTool == MarkupTool.TEXT) {
                                                detectTapGestures { offset ->
                                                    val w = size.width.coerceAtLeast(1).toFloat()
                                                    val h = size.height.coerceAtLeast(1).toFloat()
                                                    pendingTextAnchor = (offset.x / w).coerceIn(0.05f, 0.9f) to (offset.y / h).coerceIn(0.08f, 0.92f)
                                                    pendingTextDraft = ""
                                                }
                                            } else {
                                                detectDragGestures(
                                                    onDragStart = { startOffset ->
                                                        val w = size.width.coerceAtLeast(1).toFloat()
                                                        val h = size.height.coerceAtLeast(1).toFloat()
                                                        val pt = (startOffset.x / w).coerceIn(0f, 1f) to (startOffset.y / h).coerceIn(0f, 1f)
                                                        if (selectedMarkupTool == MarkupTool.PEN || selectedMarkupTool == MarkupTool.HIGHLIGHTER) {
                                                            activeDragPoints = listOf(pt)
                                                        } else {
                                                            activeShapeStart = pt
                                                            activeShapeEnd = pt
                                                        }
                                                    },
                                                    onDrag = { change, _ ->
                                                        change.consume()
                                                        val w = size.width.coerceAtLeast(1).toFloat()
                                                        val h = size.height.coerceAtLeast(1).toFloat()
                                                        val pt = (change.position.x / w).coerceIn(0f, 1f) to (change.position.y / h).coerceIn(0f, 1f)
                                                        if (selectedMarkupTool == MarkupTool.PEN || selectedMarkupTool == MarkupTool.HIGHLIGHTER) {
                                                            activeDragPoints = activeDragPoints + pt
                                                        } else {
                                                            activeShapeEnd = pt
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        when (selectedMarkupTool) {
                                                            MarkupTool.PEN, MarkupTool.HIGHLIGHTER -> {
                                                                if (activeDragPoints.size >= 2) {
                                                                    annotations.add(
                                                                        MarkupAnnotation.FreehandPath(
                                                                            points = activeDragPoints,
                                                                            colorArgb = selectedMarkupColorArgb,
                                                                            strokeWidthFraction = if (selectedMarkupTool == MarkupTool.HIGHLIGHTER) markupStrokeWidth * 2.4f else markupStrokeWidth,
                                                                            isHighlighter = selectedMarkupTool == MarkupTool.HIGHLIGHTER
                                                                        )
                                                                    )
                                                                }
                                                                activeDragPoints = emptyList()
                                                            }
                                                            MarkupTool.ARROW -> {
                                                                val s = activeShapeStart
                                                                val e = activeShapeEnd
                                                                if (s != null && e != null) {
                                                                    annotations.add(
                                                                        MarkupAnnotation.ArrowShape(
                                                                            startX = s.first,
                                                                            startY = s.second,
                                                                            endX = e.first,
                                                                            endY = e.second,
                                                                            colorArgb = selectedMarkupColorArgb,
                                                                            strokeWidthFraction = markupStrokeWidth
                                                                        )
                                                                    )
                                                                }
                                                                activeShapeStart = null
                                                                activeShapeEnd = null
                                                            }
                                                            MarkupTool.REDACT_BLUR -> {
                                                                val s = activeShapeStart
                                                                val e = activeShapeEnd
                                                                if (s != null && e != null) {
                                                                    annotations.add(
                                                                        MarkupAnnotation.RedactBlurRect(
                                                                            left = min(s.first, e.first),
                                                                            top = min(s.second, e.second),
                                                                            right = max(s.first, e.first),
                                                                            bottom = max(s.second, e.second)
                                                                        )
                                                                    )
                                                                }
                                                                activeShapeStart = null
                                                                activeShapeEnd = null
                                                            }
                                                            MarkupTool.TEXT -> Unit
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        activeDragPoints = emptyList()
                                                        activeShapeStart = null
                                                        activeShapeEnd = null
                                                    }
                                                )
                                            }
                                        }
                                ) {
                                    // Live stroke preview while dragging
                                    if (activeDragPoints.size >= 2) {
                                        val path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(activeDragPoints.first().first * size.width, activeDragPoints.first().second * size.height)
                                            activeDragPoints.drop(1).forEach { (nx, ny) ->
                                                lineTo(nx * size.width, ny * size.height)
                                            }
                                        }
                                        val previewColor = androidx.compose.ui.graphics.Color(selectedMarkupColorArgb)
                                            .copy(alpha = if (selectedMarkupTool == MarkupTool.HIGHLIGHTER) 0.42f else 1f)
                                        drawPath(
                                            path = path,
                                            color = previewColor,
                                            style = Stroke(
                                                width = (if (selectedMarkupTool == MarkupTool.HIGHLIGHTER) markupStrokeWidth * 2.4f else markupStrokeWidth) * size.width,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                    val s = activeShapeStart
                                    val e = activeShapeEnd
                                    if (s != null && e != null) {
                                        if (selectedMarkupTool == MarkupTool.ARROW) {
                                            drawLine(
                                                color = androidx.compose.ui.graphics.Color(selectedMarkupColorArgb),
                                                start = Offset(s.first * size.width, s.second * size.height),
                                                end = Offset(e.first * size.width, e.second * size.height),
                                                strokeWidth = markupStrokeWidth * size.width,
                                                cap = StrokeCap.Round
                                            )
                                        } else if (selectedMarkupTool == MarkupTool.REDACT_BLUR) {
                                            val l = min(s.first, e.first) * size.width
                                            val t = min(s.second, e.second) * size.height
                                            val r = max(s.first, e.first) * size.width
                                            val b = max(s.second, e.second) * size.height
                                            drawRect(
                                                color = androidx.compose.ui.graphics.Color(0x88111827),
                                                topLeft = Offset(l, t),
                                                size = Size((r - l).coerceAtLeast(1f), (b - t).coerceAtLeast(1f))
                                            )
                                            drawRect(
                                                color = androidx.compose.ui.graphics.Color(0xFFF59E0B),
                                                topLeft = Offset(l, t),
                                                size = Size((r - l).coerceAtLeast(1f), (b - t).coerceAtLeast(1f)),
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Preparing Canvas Studio…",
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }

                // Bottom Studio Control Deck
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.ui.graphics.Color(0xFF131B2E),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Tool Mode Selector Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EditorToolTab.entries.forEach { tab ->
                                FilterChip(
                                    selected = activeTab == tab,
                                    onClick = { activeTab = tab },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("editor_tab_${tab.name.lowercase()}")
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        when (activeTab) {
                            EditorToolTab.PRESETS -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilterPreset.entries.forEach { preset ->
                                        val isSelected = selectedPreset == preset
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                    else androidx.compose.ui.graphics.Color(0xFF1E293B)
                                                )
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else androidx.compose.ui.graphics.Color.Transparent,
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .clickable { applyPreset(preset) }
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                .testTag("preset_${preset.name.lowercase()}")
                                        ) {
                                            Text(
                                                text = preset.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = androidx.compose.ui.graphics.Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = "${preset.brightness.roundToInt()}% / ${preset.contrast.roundToInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }

                            EditorToolTab.ADJUST -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StudioSliderRow(
                                        label = "Brightness",
                                        value = brightness,
                                        range = 20f..200f,
                                        unit = "%",
                                        onValueChange = { brightness = it }
                                    )
                                    StudioSliderRow(
                                        label = "Contrast",
                                        value = contrast,
                                        range = 20f..200f,
                                        unit = "%",
                                        onValueChange = { contrast = it }
                                    )
                                    StudioSliderRow(
                                        label = "Saturation",
                                        value = saturation,
                                        range = 0f..200f,
                                        unit = "%",
                                        onValueChange = { saturation = it }
                                    )
                                    StudioSliderRow(
                                        label = "Sepia",
                                        value = sepia,
                                        range = 0f..100f,
                                        unit = "%",
                                        onValueChange = { sepia = it }
                                    )
                                    StudioSliderRow(
                                        label = "Soft Blur",
                                        value = blurRadius,
                                        range = 0f..10f,
                                        unit = "px",
                                        onValueChange = { blurRadius = it }
                                    )
                                }
                            }

                            EditorToolTab.TRANSFORM -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TransformActionButton(
                                        icon = Icons.Default.Rotate90DegreesCcw,
                                        label = "Rotate -90°",
                                        onClick = { rotationDegrees = (rotationDegrees + 270) % 360 }
                                    )
                                    TransformActionButton(
                                        icon = Icons.Default.Rotate90DegreesCw,
                                        label = "Rotate +90°",
                                        onClick = { rotationDegrees = (rotationDegrees + 90) % 360 }
                                    )
                                    TransformActionButton(
                                        icon = Icons.Default.Flip,
                                        label = if (flipH) "Flip H (On)" else "Flip Horiz",
                                        onClick = { flipH = !flipH }
                                    )
                                    TransformActionButton(
                                        icon = Icons.Default.Tune,
                                        label = if (flipV) "Flip V (On)" else "Flip Vert",
                                        onClick = { flipV = !flipV }
                                    )
                                }
                            }

                            EditorToolTab.CROP -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Drag the blue crop corners on the canvas or pick a standard aspect ratio:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f
                                                isCropApplied = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Full") }
                                        OutlinedButton(
                                            onClick = {
                                                cropLeft = 0.1f; cropTop = 0.1f; cropRight = 0.9f; cropBottom = 0.9f
                                                isCropApplied = true
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("1:1 Square") }
                                        OutlinedButton(
                                            onClick = {
                                                cropLeft = 0.05f; cropTop = 0.15f; cropRight = 0.95f; cropBottom = 0.85f
                                                isCropApplied = true
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("16:9 Wide") }
                                        Button(
                                            onClick = { isCropApplied = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Apply")
                                        }
                                    }
                                }
                            }

                            EditorToolTab.MARKUP -> {
                                val swatches = remember {
                                    listOf(
                                        Color.parseColor("#EF4444"),
                                        Color.parseColor("#F59E0B"),
                                        Color.parseColor("#22C55E"),
                                        Color.parseColor("#38BDF8"),
                                        Color.parseColor("#FFFFFF"),
                                        Color.parseColor("#111827")
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MarkupTool.entries.forEach { tool ->
                                            val icon = when (tool) {
                                                MarkupTool.PEN -> Icons.Default.Brush
                                                MarkupTool.HIGHLIGHTER -> Icons.Default.Brush
                                                MarkupTool.ARROW -> Icons.Default.NorthEast
                                                MarkupTool.TEXT -> Icons.Default.TextFields
                                                MarkupTool.REDACT_BLUR -> Icons.Default.BlurOn
                                            }
                                            FilterChip(
                                                selected = selectedMarkupTool == tool,
                                                onClick = { selectedMarkupTool = tool },
                                                leadingIcon = {
                                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                label = { Text(tool.label) },
                                                modifier = Modifier.testTag("markup_tool_${tool.name.lowercase()}")
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            swatches.forEach { argb ->
                                                val selected = selectedMarkupColorArgb == argb
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(androidx.compose.ui.graphics.Color(argb))
                                                        .border(
                                                            width = if (selected) 3.dp else 1.dp,
                                                            color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                                                            shape = CircleShape
                                                        )
                                                        .clickable { selectedMarkupColorArgb = argb }
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { if (annotations.isNotEmpty()) annotations.removeAt(annotations.lastIndex) },
                                                enabled = annotations.isNotEmpty(),
                                                modifier = Modifier.testTag("markup_undo_button")
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Undo,
                                                    contentDescription = "Undo Markup",
                                                    tint = androidx.compose.ui.graphics.Color.White
                                                )
                                            }
                                            IconButton(
                                                onClick = { annotations.clear() },
                                                enabled = annotations.isNotEmpty(),
                                                modifier = Modifier.testTag("markup_clear_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteSweep,
                                                    contentDescription = "Clear All Markup",
                                                    tint = androidx.compose.ui.graphics.Color(0xFFF87171)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = when (selectedMarkupTool) {
                                            MarkupTool.TEXT -> "Tap anywhere on the image to place a text callout."
                                            MarkupTool.REDACT_BLUR -> "Drag a box over personal info (phone, address, ID) to pixelate & redact it."
                                            MarkupTool.ARROW -> "Drag from start to target to draw a callout arrow."
                                            MarkupTool.HIGHLIGHTER -> "Draw translucent highlight strokes over documents or passes."
                                            MarkupTool.PEN -> "Draw freehand notes directly on the photo."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Text Callout Dialog
    val anchor = pendingTextAnchor
    if (anchor != null) {
        AlertDialog(
            onDismissRequest = { pendingTextAnchor = null },
            title = { Text("Add Text Callout") },
            text = {
                OutlinedTextField(
                    value = pendingTextDraft,
                    onValueChange = { pendingTextDraft = it },
                    label = { Text("Annotation text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = pendingTextDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            annotations.add(
                                MarkupAnnotation.TextCallout(
                                    normX = anchor.first,
                                    normY = anchor.second,
                                    text = trimmed,
                                    colorArgb = selectedMarkupColorArgb
                                )
                            )
                        }
                        pendingTextAnchor = null
                    }
                ) { Text("Place Text") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextAnchor = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.width(84.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${value.roundToInt()}$unit",
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFF94A3B8),
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun TransformActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}

/**
 * Hardware-accelerated Canvas 2D rendering pipeline matching Section 5.2 of the GalleryHub specification:
 * 1. Source crop rectangle resolution (sx, sy, sWidth, sHeight)
 * 2. 90°/270° Canvas dimension swapping
 * 3. Center coordinate translation + rotation + H/V mirror scaling
 * 4. ColorMatrix composite filter (brightness, contrast, saturation, grayscale, sepia) + soft blur
 */
fun renderStudioPipelineBitmap(
    source: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    sepia: Float,
    grayscale: Float,
    blurPx: Float,
    rotationDegrees: Int,
    flipH: Boolean,
    flipV: Boolean,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    annotations: List<MarkupAnnotation> = emptyList()
): Bitmap {
    val sx = (cropLeft.coerceIn(0f, 0.9f) * source.width).toInt()
    val sy = (cropTop.coerceIn(0f, 0.9f) * source.height).toInt()
    val sWidth = ((cropRight.coerceIn(cropLeft + 0.05f, 1f) - cropLeft) * source.width).toInt().coerceAtLeast(16)
    val sHeight = ((cropBottom.coerceIn(cropTop + 0.05f, 1f) - cropTop) * source.height).toInt().coerceAtLeast(16)

    val swapDimensions = (rotationDegrees % 180) != 0
    val outWidth = if (swapDimensions) sHeight else sWidth
    val outHeight = if (swapDimensions) sWidth else sHeight

    val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val matrix = Matrix().apply {
        postTranslate(-sWidth / 2f, -sHeight / 2f)
        postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
        postRotate(rotationDegrees.toFloat())
        postTranslate(outWidth / 2f, outHeight / 2f)
    }

    val combinedMatrix = ColorMatrix()

    // 1. Saturation & Grayscale
    val effectiveSat = (saturation / 100f) * (1f - (grayscale / 100f))
    val satMatrix = ColorMatrix().apply { setSaturation(effectiveSat.coerceAtLeast(0f)) }
    combinedMatrix.postConcat(satMatrix)

    // 2. Contrast & Brightness
    val c = contrast / 100f
    val bOffset = (brightness - 100f) * 2.55f
    val translate = (1f - c) * 128f + bOffset
    val cbMatrix = ColorMatrix(
        floatArrayOf(
            c, 0f, 0f, 0f, translate,
            0f, c, 0f, 0f, translate,
            0f, 0f, c, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    combinedMatrix.postConcat(cbMatrix)

    // 3. Sepia Blend
    if (sepia > 0f) {
        val t = (sepia / 100f).coerceIn(0f, 1f)
        val inv = 1f - t
        val sepiaMatrix = ColorMatrix(
            floatArrayOf(
                inv + 0.393f * t, 0.769f * t, 0.189f * t, 0f, 0f,
                0.349f * t, inv + 0.686f * t, 0.168f * t, 0f, 0f,
                0.272f * t, 0.534f * t, inv + 0.131f * t, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        combinedMatrix.postConcat(sepiaMatrix)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(combinedMatrix)
    }

    val safeSx = sx.coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val safeSy = sy.coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val safeCropW = sWidth.coerceIn(1, (source.width - safeSx).coerceAtLeast(1))
    val safeCropH = sHeight.coerceIn(1, (source.height - safeSy).coerceAtLeast(1))

    val croppedSource = Bitmap.createBitmap(
        source,
        safeSx,
        safeSy,
        safeCropW,
        safeCropH
    )

    var downscaled: Bitmap? = null
    val drawSource = if (blurPx >= 1f) {
        val factor = (1f + blurPx * 0.6f).toInt().coerceIn(2, 8)
        val smallW = (croppedSource.width / factor).coerceAtLeast(8)
        val smallH = (croppedSource.height / factor).coerceAtLeast(8)
        val down = Bitmap.createScaledBitmap(croppedSource, smallW, smallH, true)
        downscaled = down
        Bitmap.createScaledBitmap(down, croppedSource.width, croppedSource.height, true)
    } else {
        croppedSource
    }

    canvas.drawBitmap(drawSource, matrix, paint)

    // Render Markup & Redaction Annotations on top of output canvas
    if (annotations.isNotEmpty()) {
        drawMarkupAnnotationsOnCanvas(output, canvas, annotations)
    }

    // Clean up temporary intermediate bitmaps so slider adjustments don't leak heap memory
    if (drawSource !== croppedSource) {
        runCatching { drawSource.recycle() }
    }
    if (downscaled != null && downscaled !== croppedSource) {
        runCatching { downscaled.recycle() }
    }
    if (croppedSource !== source) {
        runCatching { croppedSource.recycle() }
    }
    return output
}

private fun drawMarkupAnnotationsOnCanvas(
    targetBitmap: Bitmap,
    canvas: Canvas,
    annotations: List<MarkupAnnotation>
) {
    val w = targetBitmap.width.toFloat()
    val h = targetBitmap.height.toFloat()
    for (ann in annotations) {
        when (ann) {
            is MarkupAnnotation.FreehandPath -> {
                if (ann.points.size < 2) continue
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = ann.colorArgb
                    alpha = if (ann.isHighlighter) 108 else 255
                    strokeWidth = (ann.strokeWidthFraction * w).coerceAtLeast(3f)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val path = android.graphics.Path().apply {
                    moveTo(ann.points.first().first * w, ann.points.first().second * h)
                    ann.points.drop(1).forEach { (nx, ny) -> lineTo(nx * w, ny * h) }
                }
                canvas.drawPath(path, p)
            }
            is MarkupAnnotation.ArrowShape -> {
                val sx = ann.startX * w
                val sy = ann.startY * h
                val ex = ann.endX * w
                val ey = ann.endY * h
                val strokeW = (ann.strokeWidthFraction * w).coerceAtLeast(4f)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = ann.colorArgb
                    strokeWidth = strokeW
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(sx, sy, ex, ey, p)
                val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val headLen = (strokeW * 3.8f).coerceAtLeast(22f)
                val x1 = (ex - headLen * cos(angle - Math.PI / 6.0)).toFloat()
                val y1 = (ey - headLen * sin(angle - Math.PI / 6.0)).toFloat()
                val x2 = (ex - headLen * cos(angle + Math.PI / 6.0)).toFloat()
                val y2 = (ey - headLen * sin(angle + Math.PI / 6.0)).toFloat()
                canvas.drawLine(ex, ey, x1, y1, p)
                canvas.drawLine(ex, ey, x2, y2, p)
            }
            is MarkupAnnotation.TextCallout -> {
                val textSizePx = (w * 0.048f).coerceIn(22f, 96f)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ann.colorArgb
                    textSize = textSizePx
                    isFakeBoldText = true
                }
                val tx = ann.normX * w
                val ty = ann.normY * h
                val textWidth = textPaint.measureText(ann.text)
                val pad = textSizePx * 0.28f
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(175, 15, 23, 42)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(
                    tx - pad,
                    ty - textSizePx - pad * 0.5f,
                    tx + textWidth + pad,
                    ty + pad,
                    pad,
                    pad,
                    bgPaint
                )
                canvas.drawText(ann.text, tx, ty, textPaint)
            }
            is MarkupAnnotation.RedactBlurRect -> {
                val l = (ann.left * targetBitmap.width).toInt().coerceIn(0, targetBitmap.width - 2)
                val t = (ann.top * targetBitmap.height).toInt().coerceIn(0, targetBitmap.height - 2)
                val r = (ann.right * targetBitmap.width).toInt().coerceIn(l + 2, targetBitmap.width)
                val b = (ann.bottom * targetBitmap.height).toInt().coerceIn(t + 2, targetBitmap.height)
                val rw = (r - l).coerceAtLeast(2)
                val rh = (b - t).coerceAtLeast(2)
                runCatching {
                    val region = Bitmap.createBitmap(targetBitmap, l, t, rw, rh)
                    val pixelBlock = (max(rw, rh) / 10).coerceIn(8, 28)
                    val smallW = (rw / pixelBlock).coerceAtLeast(3)
                    val smallH = (rh / pixelBlock).coerceAtLeast(3)
                    val tiny = Bitmap.createScaledBitmap(region, smallW, smallH, true)
                    val pixelated = Bitmap.createScaledBitmap(tiny, rw, rh, false)
                    canvas.drawBitmap(pixelated, l.toFloat(), t.toFloat(), null)
                    pixelated.recycle()
                    tiny.recycle()
                    region.recycle()
                }
            }
        }
    }
}

private fun loadEditorSourceBitmap(
    context: Context,
    item: UnifiedMediaItem,
    serverBaseUrl: String,
    maxDimensionPx: Int = 2048
): Bitmap? {
    // 1. Offline Vault copy (fastest & works offline)
    item.offlineFilePath?.takeIf { it.isNotBlank() }?.let { path ->
        val f = File(path)
        if (f.exists() && f.length() > 0L) {
            decodeSampledBitmapFromBytes(runCatching { f.readBytes() }.getOrNull(), maxDimensionPx)
                ?.let { return it }
        }
    }

    // 2. Local URI (content:// or file://)
    item.localUri?.takeIf { it.isNotBlank() }?.let { rawUri ->
        runCatching {
            val uri = Uri.parse(rawUri)
            val bytes = if (uri.scheme == "file") {
                uri.path?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }?.readBytes()
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            decodeSampledBitmapFromBytes(bytes, maxDimensionPx)
        }.getOrNull()?.let { return it }
    }

    // 3. Direct filesystem path
    if (item.path.isNotBlank() && !item.path.startsWith("http", ignoreCase = true)) {
        val localFile = File(item.path)
        if (localFile.exists() && localFile.length() > 0L) {
            decodeSampledBitmapFromBytes(runCatching { localFile.readBytes() }.getOrNull(), maxDimensionPx)
                ?.let { return it }
        }
    }

    // 4. Authenticated OkHttp download across all resolved remote candidate URLs
    val candidates = item.resolveRemoteCandidateUrls(serverBaseUrl)
    for (candidateUrl in candidates) {
        val bmp = runCatching {
            val req = Request.Builder().url(candidateUrl).get().build()
            RetrofitClient.okHttpClient.newCall(req).execute().use { res ->
                val contentType = res.header("Content-Type").orEmpty().lowercase()
                if (res.isSuccessful && !contentType.contains("text/html")) {
                    val bytes = res.body?.bytes()
                    decodeSampledBitmapFromBytes(bytes, maxDimensionPx)
                } else null
            }
        }.getOrNull()
        if (bmp != null) return bmp
    }

    return null
}

private fun decodeSampledBitmapFromBytes(bytes: ByteArray?, maxDimensionPx: Int): Bitmap? {
    if (bytes == null || bytes.isEmpty()) return null

    // Pass 1: Read dimensions without allocating full bitmap in memory
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while ((bounds.outWidth / sampleSize) > maxDimensionPx || (bounds.outHeight / sampleSize) > maxDimensionPx) {
        sampleSize *= 2
    }

    // Pass 2: Decode safely downsampled bitmap
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null

    // Apply EXIF orientation if present so camera portraits aren't rotated sideways
    val exifOrientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return decoded
    }

    return runCatching {
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) {
            decoded.recycle()
        }
        oriented
    }.getOrDefault(decoded)
}

private fun createFallbackStudioBitmap(title: String): Bitmap {
    val w = 720
    val h = 540
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            Color.parseColor("#1E3A8A"),
            Color.parseColor("#9333EA"),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(Rect(0, 0, w, h), p)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        isFakeBoldText = true
    }
    canvas.drawText(title.take(26), 60f, h / 2f, tp)
    return bmp
}
