package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import java.io.File

/**
 * Themed GalleryHub Home Screen Dashboard Widget showing:
 * - Favorite photo thumbnail (or themed GalleryHub studio emblem)
 * - Live Cloud Storage Quota bar & summary
 * - Quick Upload, Scan PDF, and Scan QR action buttons styled with the active accent color
 */
class GalleryHubDashboardWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val PREFS_NAME = "galleryhub_widget_snapshot"
        private const val KEY_ACCENT_HEX = "accent_hex"
        private const val KEY_IS_DARK = "is_dark"
        private const val KEY_USED_BYTES = "used_bytes"
        private const val KEY_QUOTA_BYTES = "quota_bytes"
        private const val KEY_QUOTA_LABEL = "quota_label"
        private const val KEY_SUBTITLE = "subtitle"
        private const val KEY_FAV_THUMB_PATH = "fav_thumb_path"

        const val EXTRA_WIDGET_ACTION = "com.example.extra.WIDGET_ACTION"
        const val ACTION_WIDGET_UPLOAD = "widget_open_upload"
        const val ACTION_WIDGET_SCAN_PDF = "widget_open_scan_pdf"
        const val ACTION_WIDGET_SCAN_QR = "widget_open_scan_qr"

        fun updateSnapshotAndRefresh(
            context: Context,
            accentHex: String,
            isDark: Boolean,
            usedBytes: Long,
            quotaBytes: Long,
            quotaLabel: String,
            subtitle: String,
            favoriteThumbPath: String?
        ) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCENT_HEX, accentHex)
                .putBoolean(KEY_IS_DARK, isDark)
                .putLong(KEY_USED_BYTES, usedBytes)
                .putLong(KEY_QUOTA_BYTES, quotaBytes)
                .putString(KEY_QUOTA_LABEL, quotaLabel)
                .putString(KEY_SUBTITLE, subtitle)
                .putString(KEY_FAV_THUMB_PATH, favoriteThumbPath ?: "")
                .apply()

            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, GalleryHubDashboardWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    updateSingleWidget(context, manager, id)
                }
            }
        }

        private fun updateSingleWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val accentHex = prefs.getString(KEY_ACCENT_HEX, "#D96B43") ?: "#D96B43"
            val isDark = prefs.getBoolean(KEY_IS_DARK, true)
            val usedBytes = prefs.getLong(KEY_USED_BYTES, 0L)
            val quotaBytes = prefs.getLong(KEY_QUOTA_BYTES, 500L * 1024L * 1024L).coerceAtLeast(1L)
            val quotaLabel = prefs.getString(KEY_QUOTA_LABEL, "0 B / 500 MB used") ?: "0 B / 500 MB used"
            val subtitle = prefs.getString(KEY_SUBTITLE, "Cloud & Offline Vault Ready") ?: "Cloud & Offline Vault Ready"
            val favPath = prefs.getString(KEY_FAV_THUMB_PATH, "").orEmpty()

            val accentColor = runCatching { Color.parseColor(accentHex) }.getOrDefault(Color.parseColor("#D96B43"))
            val surfaceColor = if (isDark) Color.parseColor("#161A20") else Color.parseColor("#FBF9F6")
            val primaryText = if (isDark) Color.parseColor("#F5F7FA") else Color.parseColor("#181C22")
            val secondaryText = if (isDark) Color.parseColor("#A8B3C2") else Color.parseColor("#5C6573")

            val progressPct = ((usedBytes * 100L) / quotaBytes).toInt().coerceIn(0, 100)
            val views = RemoteViews(context.packageName, R.layout.widget_galleryhub_dashboard)

            views.setTextViewText(R.id.widget_title, "GalleryHub")
            views.setTextViewText(R.id.widget_subtitle, subtitle)
            views.setTextViewText(R.id.widget_quota_text, quotaLabel)
            views.setTextColor(R.id.widget_title, primaryText)
            views.setTextColor(R.id.widget_subtitle, secondaryText)
            views.setTextColor(R.id.widget_quota_text, secondaryText)
            views.setProgressBar(R.id.widget_quota_progress, 100, progressPct, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(surfaceColor))
                views.setColorStateList(R.id.widget_btn_upload, "setBackgroundTintList", ColorStateList.valueOf(accentColor))
                views.setColorStateList(R.id.widget_btn_scan_pdf, "setBackgroundTintList", ColorStateList.valueOf(accentColor))
                views.setColorStateList(R.id.widget_btn_scan_qr, "setBackgroundTintList", ColorStateList.valueOf(accentColor))
                views.setColorStateList(R.id.widget_quota_progress, "setProgressTintList", ColorStateList.valueOf(accentColor))
            }

            // Load local favorite photo thumbnail if available
            val thumbLoaded = if (favPath.isNotBlank()) {
                val f = File(favPath)
                if (f.exists() && f.length() > 0L) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath, opts) }.getOrNull()
                    if (bmp != null) {
                        views.setImageViewBitmap(R.id.widget_favorite_thumb, bmp)
                        true
                    } else false
                } else false
            } else false

            if (!thumbLoaded) {
                views.setImageViewResource(R.id.widget_favorite_thumb, R.drawable.ic_app_terracotta_dark)
            }

            // Wire quick-action PendingIntents
            views.setOnClickPendingIntent(R.id.widget_root, buildActionIntent(context, null, 100))
            views.setOnClickPendingIntent(R.id.widget_btn_upload, buildActionIntent(context, ACTION_WIDGET_UPLOAD, 101))
            views.setOnClickPendingIntent(R.id.widget_btn_scan_pdf, buildActionIntent(context, ACTION_WIDGET_SCAN_PDF, 102))
            views.setOnClickPendingIntent(R.id.widget_btn_scan_qr, buildActionIntent(context, ACTION_WIDGET_SCAN_QR, 103))

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun buildActionIntent(context: Context, action: String?, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (action != null) {
                    putExtra(EXTRA_WIDGET_ACTION, action)
                }
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
