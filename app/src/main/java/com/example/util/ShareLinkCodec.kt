package com.example.util

import android.net.Uri
import java.net.URLDecoder

enum class ShareLinkFormat(val label: String, val badge: String) {
    SHORT_MASKED("Short Masked", "/s/…"),
    APP_DIRECT("App Link", "galleryhub://"),
    DIRECT_SERVER("Direct Web", "/api/share/…")
}

data class ParsedShareLink(
    val rawToken: String,
    val maskedCode: String,
    val sourceUrl: String,
    val hostBaseUrl: String?,
    val embeddedPassword: String? = null
)

/**
 * Reversible token masking and compact link encoder/decoder for GalleryHub share links.
 *
 * Compresses 64-character hex tokens (32 bytes) with keyed XOR masking and URL-safe Base64
 * into a 44-character masked short code (`m<43 chars>`), hiding the raw backend token and
 * `/api/share/` API path while remaining 100% lossless and interoperable with existing links.
 */
object ShareLinkCodec {

    private const val MASK_KEY_TEXT = "GalleryHub-Mask-v1-2026"
    private val MASK_KEY_BYTES = MASK_KEY_TEXT.toByteArray(Charsets.UTF_8)
    private const val HEX_MASK_PREFIX = "m"
    private const val TEXT_MASK_PREFIX = "t"
    private const val BASE64_URL_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val BASE64_DECODE_TABLE = IntArray(128) { -1 }.apply {
        BASE64_URL_ALPHABET.forEachIndexed { index, ch ->
            this[ch.code] = index
        }
        // Tolerate standard Base64 chars if pasted from another tool
        this['+'.code] = 62
        this['/'.code] = 63
    }

    /**
     * Encodes a raw share token into a shorter, masked URL-safe code.
     * A 64-char hex token (32 bytes) shrinks to 44 chars (`m` + 43 Base64URL chars).
     */
    fun maskToken(rawToken: String): String {
        val clean = rawToken.trim()
        if (clean.isEmpty()) return ""

        // Avoid double-masking if caller already passed a valid masked code
        if (isMaskedCode(clean)) return clean

        val hexBytes = hexToBytesOrNull(clean)
        return if (hexBytes != null && hexBytes.size == 32) {
            HEX_MASK_PREFIX + encodeBase64UrlNoPad(xorMask(hexBytes))
        } else {
            TEXT_MASK_PREFIX + encodeBase64UrlNoPad(xorMask(clean.toByteArray(Charsets.UTF_8)))
        }
    }

    /**
     * Reverses [maskToken] back to the exact backend share token.
     * If [codeOrToken] is already a raw backend token, returns it untouched.
     */
    fun unmaskToken(codeOrToken: String): String {
        val clean = codeOrToken.trim()
        if (clean.isEmpty()) return ""

        if (clean.startsWith(HEX_MASK_PREFIX) && clean.length == 44) {
            val decoded = decodeBase64Url(clean.substring(1))
            if (decoded != null && decoded.size == 32) {
                return bytesToHexLower(xorMask(decoded))
            }
        }
        if (clean.startsWith(TEXT_MASK_PREFIX) && clean.length > 2) {
            val decoded = decodeBase64Url(clean.substring(1))
            if (decoded != null && decoded.isNotEmpty()) {
                val text = String(xorMask(decoded), Charsets.UTF_8)
                if (text.all { it.code in 33..126 }) {
                    return text
                }
            }
        }
        return clean
    }

    fun isMaskedCode(value: String): Boolean {
        val clean = value.trim()
        if (clean.startsWith(HEX_MASK_PREFIX) && clean.length == 44) {
            return decodeBase64Url(clean.substring(1))?.size == 32
        }
        if (clean.startsWith(TEXT_MASK_PREFIX) && clean.length > 2) {
            val decoded = decodeBase64Url(clean.substring(1)) ?: return false
            val text = String(xorMask(decoded), Charsets.UTF_8)
            return text.isNotBlank() && text.all { it.code in 33..126 }
        }
        return false
    }

    fun buildShortMaskedUrl(baseUrl: String, rawToken: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val masked = maskToken(rawToken)
        return "$cleanBase/s/$masked"
    }

    fun buildAppSchemeUrl(rawToken: String): String {
        val masked = maskToken(rawToken)
        return "galleryhub://s/$masked"
    }

    fun buildDirectServerUrl(baseUrl: String, rawToken: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val raw = unmaskToken(rawToken)
        return "$cleanBase/api/share/$raw"
    }

    fun buildUrlForFormat(baseUrl: String, rawToken: String, format: ShareLinkFormat): String {
        return when (format) {
            ShareLinkFormat.SHORT_MASKED -> buildShortMaskedUrl(baseUrl, rawToken)
            ShareLinkFormat.APP_DIRECT -> buildAppSchemeUrl(rawToken)
            ShareLinkFormat.DIRECT_SERVER -> buildDirectServerUrl(baseUrl, rawToken)
        }
    }

    /**
     * Compact human-friendly masked preview shown on cards & dialogs so raw tokens are never cluttered.
     * Example: `galleryhub-n66c.onrender.com/s/mK9xP•••7qRz`
     */
    fun buildMaskedDisplayPreview(
        baseUrl: String,
        rawToken: String,
        format: ShareLinkFormat = ShareLinkFormat.SHORT_MASKED
    ): String {
        val masked = maskToken(rawToken)
        val compactCode = if (masked.length > 12) {
            "${masked.take(6)}•••${masked.takeLast(4)}"
        } else {
            masked
        }
        val host = extractHost(baseUrl).ifBlank { "galleryhub" }
        val shortHost = if (host.length > 24) "${host.take(14)}…${host.takeLast(7)}" else host
        return when (format) {
            ShareLinkFormat.SHORT_MASKED -> "$shortHost/s/$compactCode"
            ShareLinkFormat.APP_DIRECT -> "galleryhub://s/$compactCode"
            ShareLinkFormat.DIRECT_SERVER -> {
                val raw = unmaskToken(rawToken)
                val compactRaw = if (raw.length > 12) "${raw.take(6)}•••${raw.takeLast(4)}" else raw
                "$shortHost/api/share/$compactRaw"
            }
        }
    }

    /**
     * Parses any supported share link or pasted token:
     * - `https://galleryhub-n66c.onrender.com/s/<maskedCode>`
     * - `https://galleryhub-n66c.onrender.com/api/share/<rawToken>`
     * - `https://galleryhub-n66c.onrender.com/share/<rawToken>`
     * - `galleryhub://s/<maskedCode>`
     * - `galleryhub://share/<rawToken>`
     * - Pasted raw token or masked code directly
     */
    fun parseShareInput(rawInput: String): ParsedShareLink? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Extract optional query parameter ?password= or ?p=
        val withoutFragment = trimmed.substringBefore("#")
        val queryPart = withoutFragment.substringAfter("?", "")
        val embeddedPassword = extractQueryParam(queryPart, "password")
            ?: extractQueryParam(queryPart, "p")
        val pathOnly = withoutFragment.substringBefore("?").trimEnd('/')

        val lower = pathOnly.lowercase()
        val hostBaseUrl = extractSchemeAndHost(pathOnly)

        val candidateSegment: String = when {
            lower.startsWith("galleryhub://s/") -> pathOnly.substring("galleryhub://s/".length)
            lower.startsWith("galleryhub://share/") -> pathOnly.substring("galleryhub://share/".length)
            lower.contains("/api/share/") -> pathOnly.substringAfterLast("/api/share/")
            lower.contains("/share/") -> pathOnly.substringAfterLast("/share/")
            lower.contains("/s/") -> pathOnly.substringAfterLast("/s/")
            !pathOnly.contains("://") && !pathOnly.contains("/") -> pathOnly
            else -> return null
        }.substringBefore("/").trim()

        if (candidateSegment.isBlank()) return null

        val rawToken = unmaskToken(candidateSegment)
        if (rawToken.isBlank()) return null

        return ParsedShareLink(
            rawToken = rawToken,
            maskedCode = maskToken(rawToken),
            sourceUrl = trimmed,
            hostBaseUrl = hostBaseUrl,
            embeddedPassword = embeddedPassword
        )
    }

    fun isShareDeepLinkUri(uri: Uri?): Boolean {
        if (uri == null) return false
        return parseShareInput(uri.toString()) != null
    }

    /**
     * Generates a lightweight Node.js / Express handler so browsers WITHOUT the GalleryHub app
     * can also open shortened `/s/:code` links and get redirected straight to `/api/share/:token`.
     */
    fun buildBackendShortRedirectSnippet(): String = """
        // Add to your Express server (e.g. server.js) for web browser fallback on /s/:code links:
        const MASK_KEY = Buffer.from("$MASK_KEY_TEXT", "utf8");
        app.get("/s/:code", (req, res) => {
          const code = (req.params.code || "").trim();
          try {
            const prefix = code[0];
            const b64 = code.slice(1).replace(/-/g, "+").replace(/_/g, "/");
            const buf = Buffer.from(b64, "base64");
            const unmasked = Buffer.alloc(buf.length);
            for (let i = 0; i < buf.length; i++) unmasked[i] = buf[i] ^ MASK_KEY[i % MASK_KEY.length];
            const token = prefix === "m" ? unmasked.toString("hex") : unmasked.toString("utf8");
            return res.redirect(302, "/api/share/" + encodeURIComponent(token));
          } catch (_) {
            return res.status(404).send("Invalid GalleryHub short link");
          }
        });
    """.trimIndent()

    private fun xorMask(input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        val keyLen = MASK_KEY_BYTES.size
        for (i in input.indices) {
            out[i] = (input[i].toInt() xor MASK_KEY_BYTES[i % keyLen].toInt()).toByte()
        }
        return out
    }

    private fun hexToBytesOrNull(hex: String): ByteArray? {
        if (hex.length % 2 != 0 || hex.isEmpty()) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun bytesToHexLower(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(hexChars[v ushr 4])
            sb.append(hexChars[v and 0x0F])
        }
        return sb.toString()
    }

    private fun encodeBase64UrlNoPad(data: ByteArray): String {
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val hasB2 = i + 2 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

            sb.append(BASE64_URL_ALPHABET[b0 ushr 2])
            sb.append(BASE64_URL_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            if (hasB1) {
                sb.append(BASE64_URL_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
            }
            if (hasB2) {
                sb.append(BASE64_URL_ALPHABET[b2 and 0x3F])
            }
            i += 3
        }
        return sb.toString()
    }

    private fun decodeBase64Url(input: String): ByteArray? {
        val clean = input.trim().trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)
        val rem = clean.length % 4
        if (rem == 1) return null
        val outLen = (clean.length / 4) * 3 + when (rem) {
            2 -> 1
            3 -> 2
            else -> 0
        }
        val out = ByteArray(outLen)
        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val c0 = decodeBase64Char(clean[i]) ?: return null
            val c1 = if (i + 1 < clean.length) decodeBase64Char(clean[i + 1]) ?: return null else 0
            val c2 = if (i + 2 < clean.length) decodeBase64Char(clean[i + 2]) ?: return null else 0
            val c3 = if (i + 3 < clean.length) decodeBase64Char(clean[i + 3]) ?: return null else 0

            if (outIdx < outLen) out[outIdx++] = ((c0 shl 2) or (c1 ushr 4)).toByte()
            if (outIdx < outLen && i + 2 < clean.length) out[outIdx++] = (((c1 and 0x0F) shl 4) or (c2 ushr 2)).toByte()
            if (outIdx < outLen && i + 3 < clean.length) out[outIdx++] = (((c2 and 0x03) shl 6) or c3).toByte()
            i += 4
        }
        return out
    }

    private fun decodeBase64Char(ch: Char): Int? {
        val code = ch.code
        if (code !in 0..127) return null
        val value = BASE64_DECODE_TABLE[code]
        return if (value >= 0) value else null
    }

    private fun extractSchemeAndHost(url: String): String? {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        val schemeEnd = url.indexOf("://")
        val hostStart = schemeEnd + 3
        val pathStart = url.indexOf('/', hostStart)
        return if (pathStart > 0) url.substring(0, pathStart) else url
    }

    private fun extractHost(url: String): String {
        val schemeAndHost = extractSchemeAndHost(url) ?: return url.substringBefore("/")
        return schemeAndHost.substringAfter("://")
    }

    private fun extractQueryParam(query: String, key: String): String? {
        if (query.isBlank()) return null
        for (pair in query.split("&")) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val k = pair.substring(0, eqIdx).trim()
                if (k.equals(key, ignoreCase = true)) {
                    val rawVal = pair.substring(eqIdx + 1)
                    return runCatching { URLDecoder.decode(rawVal, "UTF-8") }.getOrDefault(rawVal)
                        .takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }
}
