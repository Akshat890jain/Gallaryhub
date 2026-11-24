package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

enum class AppLinkDomainStatus(val label: String, val detail: String) {
    VERIFIED(
        "Verified",
        "Android automatically opens GalleryHub share links directly inside the app."
    ),
    USER_SELECTED(
        "Enabled by User",
        "Approved in Android 'Open by default' settings to launch directly in GalleryHub."
    ),
    NEEDS_SETUP(
        "Needs Approval",
        "Tap 'Open App Link Settings' below to allow this domain, or host assetlinks.json on your server."
    ),
    LEGACY_SUPPORTED(
        "Ready",
        "Device prompts or opens GalleryHub automatically for supported share links."
    )
}

data class AppLinksDiagnostics(
    val packageName: String,
    val host: String,
    val sha256Fingerprints: List<String>,
    val domainStatus: AppLinkDomainStatus,
    val assetLinksJson: String,
    val shortRedirectSnippet: String
)

/**
 * Runtime diagnostics and one-tap system setup utilities for Android App Links
 * (`/.well-known/assetlinks.json` + Android 12+ Open by Default settings).
 */
object AppLinksHelper {

    fun collectDiagnostics(context: Context, serverBaseUrl: String): AppLinksDiagnostics {
        val packageName = context.packageName
        val host = extractHost(serverBaseUrl)
        val fingerprints = readSigningSha256Fingerprints(context)
        val status = checkDomainStatus(context, host)
        val assetLinksJson = buildAssetLinksJson(packageName, fingerprints)
        return AppLinksDiagnostics(
            packageName = packageName,
            host = host,
            sha256Fingerprints = fingerprints,
            domainStatus = status,
            assetLinksJson = assetLinksJson,
            shortRedirectSnippet = ShareLinkCodec.buildBackendShortRedirectSnippet()
        )
    }

    fun openAppLinksSystemSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(
                    Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        for (intent in intents) {
            val started = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return
        }
    }

    fun buildAssetLinksJson(packageName: String, sha256Fingerprints: List<String>): String {
        val fpEntries = sha256Fingerprints
            .ifEmpty { listOf("REPLACE_WITH_YOUR_RELEASE_SHA256_FINGERPRINT") }
            .joinToString(",\n          ") { "\"$it\"" }
        return """
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "$packageName",
      "sha256_cert_fingerprints": [
          $fpEntries
      ]
    }
  }
]
        """.trimIndent()
    }

    private fun extractHost(baseUrl: String): String {
        return runCatching { Uri.parse(baseUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "galleryhub-n66c.onrender.com"
    }

    @Suppress("DEPRECATION")
    private fun readSigningSha256Fingerprints(context: Context): List<String> {
        return runCatching {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = pkgInfo.signingInfo
                when {
                    signingInfo == null -> emptyList()
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners.toList()
                    else -> signingInfo.signingCertificateHistory.toList()
                }
            } else {
                val pkgInfo = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                pkgInfo.signatures?.toList().orEmpty()
            }
            signatures.map { sig ->
                val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                digest.joinToString(":") { "%02X".format(it) }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun checkDomainStatus(context: Context, host: String): AppLinkDomainStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return AppLinkDomainStatus.LEGACY_SUPPORTED
        }
        return runCatching {
            val manager = context.getSystemService(DomainVerificationManager::class.java)
                ?: return AppLinkDomainStatus.NEEDS_SETUP
            val userState = manager.getDomainVerificationUserState(context.packageName)
                ?: return AppLinkDomainStatus.NEEDS_SETUP
            val state = userState.hostToStateMap[host]
            when (state) {
                DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> AppLinkDomainStatus.VERIFIED
                DomainVerificationUserState.DOMAIN_STATE_SELECTED -> AppLinkDomainStatus.USER_SELECTED
                else -> AppLinkDomainStatus.NEEDS_SETUP
            }
        }.getOrDefault(AppLinkDomainStatus.NEEDS_SETUP)
    }
}
