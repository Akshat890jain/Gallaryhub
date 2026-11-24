package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dynamic, high-contrast studio logo mark for GalleryHub.
 * Adapts seamlessly to BOTH the selected Studio Accent palette (primary + secondary gradient)
 * and the active Light ("Warm Paper") or Dark ("Obsidian Dark") theme mode, mirroring the
 * dynamic app drawer launcher icons.
 */
@Composable
fun GalleryHubLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    secondaryAccentColor: Color = MaterialTheme.colorScheme.secondary,
    isDarkTheme: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    testTag: String = "galleryhub_logo_mark"
) {
    val baseColor = if (isDarkTheme) Color(0xFF181512) else Color(0xFFFAF7F2)
    val backCardColor = if (isDarkTheme) Color(0xFFFAF6F0) else Color(0xFF25221E)
    val peakColor = if (isDarkTheme) Color(0xFF181512) else Color(0xFF231F1C)
    val borderColor = if (isDarkTheme) {
        accentColor.copy(alpha = 0.48f)
    } else {
        accentColor.copy(alpha = 0.58f)
    }
    // Use luminous ivory-gold sun when accent itself is Champagne Gold so it remains crisp
    val isGoldAccent = accentColor.red > 0.78f && accentColor.green > 0.55f && accentColor.blue < 0.35f
    val sunColor = if (isGoldAccent) Color(0xFFFFF3C4) else Color(0xFFF5C451)
    val shadowLipColor = secondaryAccentColor.copy(alpha = if (isDarkTheme) 0.62f else 0.42f)

    Surface(
        shape = RoundedCornerShape(size * 0.28f),
        color = baseColor,
        shadowElevation = if (isDarkTheme) 3.dp else 4.dp,
        modifier = modifier
            .size(size)
            .border(
                width = 1.1.dp,
                color = borderColor,
                shape = RoundedCornerShape(size * 0.28f)
            )
            .semantics { contentDescription = "GalleryHub Logo" }
            .testTag(testTag)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Tilted Back Photo Card (Warm Cream in Dark Mode, Warm Charcoal in Light Mode)
            rotate(degrees = -9f, pivot = Offset(w * 0.44f, h * 0.50f)) {
                drawRoundRect(
                    color = backCardColor,
                    topLeft = Offset(w * 0.16f, h * 0.22f),
                    size = Size(w * 0.54f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
                )
            }

            // 2. Front Photo Card Geometry (Clipped Rounded Square)
            val cardLeft = w * 0.28f
            val cardTop = h * 0.24f
            val cardW = w * 0.57f
            val cardH = h * 0.57f
            val cardCorner = w * 0.13f

            // Subtle secondary accent shadow lip beneath front card
            drawRoundRect(
                color = shadowLipColor,
                topLeft = Offset(cardLeft + w * 0.018f, cardTop + h * 0.026f),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(cardCorner, cardCorner)
            )

            val frontCardPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH),
                        cornerRadius = CornerRadius(cardCorner, cardCorner)
                    )
                )
            }

            clipPath(frontCardPath) {
                // Studio Sky Gradient Fill driven by Primary + Secondary Accent Tokens
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor,
                            secondaryAccentColor
                        ),
                        startY = cardTop,
                        endY = cardTop + cardH
                    ),
                    topLeft = Offset(cardLeft, cardTop),
                    size = Size(cardW, cardH)
                )

                // Warm Sun Circle
                drawCircle(
                    color = sunColor,
                    radius = cardW * 0.185f,
                    center = Offset(cardLeft + cardW * 0.36f, cardTop + cardH * 0.44f)
                )

                // Crisp Minimal Cloud Badge (Top-Right of front photo card)
                val cloudCenterX = cardLeft + cardW * 0.74f
                val cloudBaseY = cardTop + cardH * 0.27f
                val cloudR = cardW * 0.075f
                drawCircle(
                    color = Color.White,
                    radius = cloudR * 1.15f,
                    center = Offset(cloudCenterX, cloudBaseY - cloudR * 0.45f)
                )
                drawCircle(
                    color = Color.White,
                    radius = cloudR * 0.82f,
                    center = Offset(cloudCenterX - cloudR * 0.95f, cloudBaseY - cloudR * 0.10f)
                )
                drawCircle(
                    color = Color.White,
                    radius = cloudR * 0.78f,
                    center = Offset(cloudCenterX + cloudR * 0.95f, cloudBaseY - cloudR * 0.05f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(cloudCenterX - cloudR * 1.45f, cloudBaseY - cloudR * 0.35f),
                    size = Size(cloudR * 2.9f, cloudR * 1.05f),
                    cornerRadius = CornerRadius(cloudR * 0.55f, cloudR * 0.55f)
                )

                // Bold Twin Mountain Landscape Silhouette
                val mountains = Path().apply {
                    moveTo(cardLeft - w * 0.02f, cardTop + cardH + h * 0.02f)
                    lineTo(cardLeft - w * 0.02f, cardTop + cardH * 0.78f)
                    lineTo(cardLeft + cardW * 0.32f, cardTop + cardH * 0.48f)
                    lineTo(cardLeft + cardW * 0.58f, cardTop + cardH * 0.72f)
                    lineTo(cardLeft + cardW * 0.76f, cardTop + cardH * 0.58f)
                    lineTo(cardLeft + cardW + w * 0.02f, cardTop + cardH * 0.82f)
                    lineTo(cardLeft + cardW + w * 0.02f, cardTop + cardH + h * 0.02f)
                    close()
                }
                drawPath(path = mountains, color = peakColor)
            }
        }
    }
}

/**
 * Small, non-intrusive status indicator pill for the Top App Bar of MediaGalleryScreen.
 * Clearly communicates whether the app is connected to the cloud or operating in offline mode.
 */
@Composable
fun CloudConnectionStatusIndicator(
    isCloudConnected: Boolean,
    isChecking: Boolean = false,
    isGuestMode: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    val emeraldCloud = Color(0xFF2E9E6B)
    val amberOffline = Color(0xFFD9822B)
    val syncingAccent = MaterialTheme.colorScheme.primary

    val targetDotColor = when {
        isChecking && !isGuestMode -> syncingAccent
        isCloudConnected -> emeraldCloud
        else -> amberOffline
    }

    val indicatorColor by animateColorAsState(
        targetValue = targetDotColor,
        animationSpec = tween(durationMillis = 260),
        label = "status_indicator_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cloud_status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_dot_pulse"
    )

    val statusLabel = when {
        isChecking && !isGuestMode -> "Syncing"
        isCloudConnected -> "Cloud"
        else -> "Offline"
    }

    val accessibilityLabel = when {
        isChecking && !isGuestMode -> "Checking cloud connection"
        isCloudConnected -> "Connected to Cloud"
        else -> "Operating in Offline Mode"
    }

    val statusIcon = when {
        isChecking && !isGuestMode -> Icons.Default.CloudSync
        isCloudConnected -> Icons.Default.CloudDone
        else -> Icons.Default.CloudOff
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = indicatorColor.copy(alpha = 0.11f),
        modifier = modifier
            .border(
                width = 1.dp,
                color = indicatorColor.copy(alpha = 0.34f),
                shape = RoundedCornerShape(50)
            )
            .clip(RoundedCornerShape(50))
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = accessibilityLabel }
            .testTag("cloud_connection_status_indicator")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        indicatorColor.copy(
                            alpha = if (isCloudConnected || isChecking) pulseAlpha else 0.9f
                        )
                    )
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = indicatorColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.15.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = indicatorColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Dedicated manual 'Sync Now' button designed to sit directly beside the
 * CloudConnectionStatusIndicator in the top app bar to force-refresh the local
 * cache with the remote backend.
 */
@Composable
fun SyncNowStatusButton(
    isSyncing: Boolean,
    pendingSyncCount: Int = 0,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val pendingAmber = Color(0xFFD9822B)
    val hasPending = pendingSyncCount > 0 && !isSyncing

    val infiniteTransition = rememberInfiniteTransition(label = "sync_now_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_icon_rotation"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isSyncing -> primary.copy(alpha = 0.18f)
            hasPending -> pendingAmber.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(200),
        label = "sync_btn_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isSyncing -> primary.copy(alpha = 0.55f)
            hasPending -> pendingAmber.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)
        },
        animationSpec = tween(200),
        label = "sync_btn_border"
    )

    val contentTint by animateColorAsState(
        targetValue = when {
            isSyncing -> primary
            hasPending -> pendingAmber
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(200),
        label = "sync_btn_tint"
    )

    val buttonLabel = when {
        isSyncing -> "Syncing…"
        hasPending -> "Sync ($pendingSyncCount)"
        else -> "Sync Now"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !isSyncing) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onSyncNow()
            }
            .semantics {
                contentDescription = when {
                    isSyncing -> "Syncing archive with cloud"
                    hasPending -> "Sync $pendingSyncCount queued offline changes"
                    else -> "Sync Now"
                }
            }
            .testTag("topbar_sync_now_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        rotationZ = if (isSyncing) spinAngle else 0f
                    }
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.15.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = contentTint,
                maxLines = 1
            )
        }
    }
}

