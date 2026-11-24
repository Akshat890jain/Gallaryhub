package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class QrCodeMatrix(
    val size: Int,
    val version: Int,
    val modules: BooleanArray
) {
    fun get(row: Int, col: Int): Boolean = modules[row * size + col]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrCodeMatrix) return false
        return size == other.size && version == other.version && modules.contentEquals(other.modules)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + version
        result = 31 * result + modules.contentHashCode()
        return result
    }
}

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code encoder (Byte mode, Error Correction Level M, Versions 1..10).
 * Supports payloads up to 213 UTF-8 bytes with standard GF(256) Reed-Solomon ECC and optimal
 * 8-mask penalty selection so every mobile camera and QR scanner reads codes reliably.
 */
object QrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val dataCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val group1Blocks: Int,
        val group1DataPerBlock: Int,
        val group2Blocks: Int,
        val group2DataPerBlock: Int,
        val alignmentCenters: IntArray
    )

    // ISO/IEC 18004 Table 9 (Error Correction Level M, Versions 1..10)
    private val VERSION_SPECS_M = listOf(
        VersionSpec(1, 26, 16, 10, 1, 16, 0, 0, intArrayOf()),
        VersionSpec(2, 44, 28, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 70, 44, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 100, 64, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 134, 86, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 172, 108, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 196, 124, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 242, 154, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
        VersionSpec(9, 292, 182, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
        VersionSpec(10, 346, 216, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50))
    )

    // GF(256) exp/log tables with primitive polynomial 0x11D
    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    fun encode(content: String): QrCodeMatrix? {
        val payload = content.toByteArray(Charsets.UTF_8)
        if (payload.isEmpty()) return null

        val spec = VERSION_SPECS_M.firstOrNull { candidate ->
            val charCountBits = if (candidate.version <= 9) 8 else 16
            val totalBitsNeeded = 4 + charCountBits + payload.size * 8
            totalBitsNeeded <= candidate.dataCodewords * 8
        } ?: return null

        val dataCodewords = buildDataCodewords(payload, spec)
        val interleaved = interleaveBlocksAndEc(dataCodewords, spec)
        val size = spec.version * 4 + 17

        // Build base function-pattern matrix & reservation mask
        val baseModules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        placeFinderPattern(baseModules, isFunction, size, 0, 0)
        placeFinderPattern(baseModules, isFunction, size, 0, size - 7)
        placeFinderPattern(baseModules, isFunction, size, size - 7, 0)
        placeTimingPatterns(baseModules, isFunction, size)
        placeAlignmentPatterns(baseModules, isFunction, size, spec.alignmentCenters)
        reserveFormatAndDarkModule(baseModules, isFunction, size)
        if (spec.version >= 7) {
            placeVersionInfo(baseModules, isFunction, size, spec.version)
        }

        // Place interleaved data + EC bits in standard zig-zag order
        placeCodewordBits(baseModules, isFunction, size, interleaved)

        // Evaluate all 8 mask patterns and select the lowest penalty score
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestModules: BooleanArray? = null

        for (maskPattern in 0 until 8) {
            val candidate = baseModules.copyOf()
            applyMask(candidate, isFunction, size, maskPattern)
            writeFormatInfo(candidate, size, maskPattern)
            val penalty = computePenaltyScore(candidate, size)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = maskPattern
                bestModules = candidate
            }
        }

        val finalModules = bestModules ?: run {
            val fallback = baseModules.copyOf()
            applyMask(fallback, isFunction, size, bestMask)
            writeFormatInfo(fallback, size, bestMask)
            fallback
        }

        return QrCodeMatrix(size = size, version = spec.version, modules = finalModules)
    }

    fun renderToBitmap(matrix: QrCodeMatrix, pixelSize: Int = 640, quietZoneModules: Int = 4): Bitmap {
        val totalModules = matrix.size + quietZoneModules * 2
        val modulePx = (pixelSize / totalModules).coerceAtLeast(2)
        val finalSize = modulePx * totalModules
        val bmp = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint().apply {
            color = AndroidColor.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        for (r in 0 until matrix.size) {
            for (c in 0 until matrix.size) {
                if (matrix.get(r, c)) {
                    val left = ((c + quietZoneModules) * modulePx).toFloat()
                    val top = ((r + quietZoneModules) * modulePx).toFloat()
                    canvas.drawRect(left, top, left + modulePx, top + modulePx, paint)
                }
            }
        }
        return bmp
    }

    private fun buildDataCodewords(payload: ByteArray, spec: VersionSpec): ByteArray {
        val bits = ArrayList<Boolean>(spec.dataCodewords * 8)
        fun appendBits(value: Int, count: Int) {
            for (i in (count - 1) downTo 0) {
                bits.add(((value ushr i) and 1) != 0)
            }
        }

        // Byte mode indicator: 0100
        appendBits(0b0100, 4)
        // Character count indicator
        appendBits(payload.size, if (spec.version <= 9) 8 else 16)
        // Payload bytes
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val capacityBits = spec.dataCodewords * 8
        // Terminator (up to 4 zero bits)
        val terminatorLen = minOf(4, capacityBits - bits.size)
        repeat(terminatorLen) { bits.add(false) }

        // Pad to byte boundary
        while (bits.size % 8 != 0) {
            bits.add(false)
        }

        val bytes = ByteArray(spec.dataCodewords)
        var byteIdx = 0
        var bitIdx = 0
        while (bitIdx < bits.size && byteIdx < spec.dataCodewords) {
            var v = 0
            for (k in 0 until 8) {
                v = (v shl 1) or (if (bits[bitIdx + k]) 1 else 0)
            }
            bytes[byteIdx++] = v.toByte()
            bitIdx += 8
        }

        // Pad remaining bytes with alternating 0xEC, 0x11
        var toggle = true
        while (byteIdx < spec.dataCodewords) {
            bytes[byteIdx++] = (if (toggle) 0xEC else 0x11).toByte()
            toggle = !toggle
        }
        return bytes
    }

    private fun interleaveBlocksAndEc(data: ByteArray, spec: VersionSpec): ByteArray {
        val dataBlocks = mutableListOf<ByteArray>()
        val ecBlocks = mutableListOf<ByteArray>()
        var offset = 0

        repeat(spec.group1Blocks) {
            val block = data.copyOfRange(offset, offset + spec.group1DataPerBlock)
            offset += spec.group1DataPerBlock
            dataBlocks.add(block)
            ecBlocks.add(computeReedSolomon(block, spec.ecCodewordsPerBlock))
        }
        repeat(spec.group2Blocks) {
            val block = data.copyOfRange(offset, offset + spec.group2DataPerBlock)
            offset += spec.group2DataPerBlock
            dataBlocks.add(block)
            ecBlocks.add(computeReedSolomon(block, spec.ecCodewordsPerBlock))
        }

        val out = ByteArray(spec.totalCodewords)
        var outIdx = 0
        val maxDataLen = maxOf(spec.group1DataPerBlock, spec.group2DataPerBlock)
        for (i in 0 until maxDataLen) {
            for (block in dataBlocks) {
                if (i < block.size) out[outIdx++] = block[i]
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (ec in ecBlocks) {
                out[outIdx++] = ec[i]
            }
        }
        return out
    }

    private fun computeReedSolomon(data: ByteArray, ecCount: Int): ByteArray {
        // Generator polynomial: prod_{i=0..ecCount-1} (x - alpha^i)
        var gen = IntArray(1) { 1 }
        for (i in 0 until ecCount) {
            val next = IntArray(gen.size + 1)
            val root = GF_EXP[i]
            for (j in gen.indices) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gfMul(gen[j], root)
            }
            gen = next
        }

        val remainder = IntArray(ecCount)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1)
            remainder[ecCount - 1] = 0
            if (factor != 0) {
                for (j in 0 until ecCount) {
                    remainder[j] = remainder[j] xor gfMul(gen[j + 1], factor)
                }
            }
        }
        return ByteArray(ecCount) { remainder[it].toByte() }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun placeFinderPattern(
        modules: BooleanArray,
        isFunction: BooleanArray,
        size: Int,
        topRow: Int,
        leftCol: Int
    ) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val r = topRow + dr
                val c = leftCol + dc
                if (r !in 0 until size || c !in 0 until size) continue
                val isDark = if (dr in 0..6 && dc in 0..6) {
                    val dist = maxOf(kotlin.math.abs(dr - 3), kotlin.math.abs(dc - 3))
                    dist != 2
                } else {
                    false // White separator
                }
                modules[r * size + c] = isDark
                isFunction[r * size + c] = true
            }
        }
    }

    private fun placeTimingPatterns(modules: BooleanArray, isFunction: BooleanArray, size: Int) {
        for (i in 8 until size - 8) {
            val dark = (i % 2 == 0)
            if (!isFunction[6 * size + i]) {
                modules[6 * size + i] = dark
                isFunction[6 * size + i] = true
            }
            if (!isFunction[i * size + 6]) {
                modules[i * size + 6] = dark
                isFunction[i * size + 6] = true
            }
        }
    }

    private fun placeAlignmentPatterns(
        modules: BooleanArray,
        isFunction: BooleanArray,
        size: Int,
        centers: IntArray
    ) {
        if (centers.isEmpty()) return
        for (rCenter in centers) {
            for (cCenter in centers) {
                // Skip corners overlapping the 3 finder patterns
                val overlapsTopLeft = rCenter <= 8 && cCenter <= 8
                val overlapsTopRight = rCenter <= 8 && cCenter >= size - 9
                val overlapsBottomLeft = rCenter >= size - 9 && cCenter <= 8
                if (overlapsTopLeft || overlapsTopRight || overlapsBottomLeft) continue

                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val r = rCenter + dr
                        val c = cCenter + dc
                        val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                        modules[r * size + c] = (dist != 1)
                        isFunction[r * size + c] = true
                    }
                }
            }
        }
    }

    private fun reserveFormatAndDarkModule(
        modules: BooleanArray,
        isFunction: BooleanArray,
        size: Int
    ) {
        // Reserve top-left format info strip
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8 * size + i] = true
                isFunction[i * size + 8] = true
            }
        }
        // Reserve top-right and bottom-left format info strips
        for (i in 0 until 8) {
            isFunction[8 * size + (size - 1 - i)] = true
        }
        for (i in 0 until 7) {
            isFunction[(size - 1 - i) * size + 8] = true
        }
        // Fixed dark module at (size - 8, 8)
        modules[(size - 8) * size + 8] = true
        isFunction[(size - 8) * size + 8] = true
    }

    private fun placeVersionInfo(
        modules: BooleanArray,
        isFunction: BooleanArray,
        size: Int,
        version: Int
    ) {
        var rem = version
        for (i in 0 until 12) {
            rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val dark = ((bits ushr i) and 1) != 0
            val a = size - 11 + (i % 3)
            val b = i / 3
            modules[a * size + b] = dark
            isFunction[a * size + b] = true
            modules[b * size + a] = dark
            isFunction[b * size + a] = true
        }
    }

    private fun placeCodewordBits(
        modules: BooleanArray,
        isFunction: BooleanArray,
        size: Int,
        codewords: ByteArray
    ) {
        val totalBits = codewords.size * 8
        var bitIdx = 0
        var upward = true
        var col = size - 1

        while (col >= 1) {
            if (col == 6) col-- // Skip vertical timing column
            for (step in 0 until size) {
                val row = if (upward) size - 1 - step else step
                for (dx in 0..1) {
                    val c = col - dx
                    val idx = row * size + c
                    if (!isFunction[idx]) {
                        val isDark = if (bitIdx < totalBits) {
                            val b = codewords[bitIdx ushr 3].toInt() and 0xFF
                            ((b ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[idx] = isDark
                        bitIdx++
                    }
                }
            }
            upward = !upward
            col -= 2
        }
    }

    private fun applyMask(
        modules: BooleanArray,
        isFunction: BooleanArray,
        size: Int,
        maskPattern: Int
    ) {
        for (r in 0 until size) {
            for (c in 0 until size) {
                val idx = r * size + c
                if (isFunction[idx]) continue
                val invert = when (maskPattern) {
                    0 -> (r + c) % 2 == 0
                    1 -> r % 2 == 0
                    2 -> c % 3 == 0
                    3 -> (r + c) % 3 == 0
                    4 -> ((r / 2) + (c / 3)) % 2 == 0
                    5 -> ((r * c) % 2) + ((r * c) % 3) == 0
                    6 -> (((r * c) % 2) + ((r * c) % 3)) % 2 == 0
                    7 -> (((r + c) % 2) + ((r * c) % 3)) % 2 == 0
                    else -> false
                }
                if (invert) modules[idx] = !modules[idx]
            }
        }
    }

    private fun writeFormatInfo(modules: BooleanArray, size: Int, maskPattern: Int) {
        // Error Correction Level M indicator = 00
        val data = (0b00 shl 3) or (maskPattern and 0b111)
        var rem = data
        for (i in 0 until 10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        // First copy around top-left finder
        for (i in 0..5) modules[i * size + 8] = ((bits ushr i) and 1) != 0
        modules[7 * size + 8] = ((bits ushr 6) and 1) != 0
        modules[8 * size + 8] = ((bits ushr 7) and 1) != 0
        modules[8 * size + 7] = ((bits ushr 8) and 1) != 0
        for (i in 9..14) modules[8 * size + (14 - i)] = ((bits ushr i) and 1) != 0

        // Second copy along top-right & bottom-left
        for (i in 0..7) modules[8 * size + (size - 1 - i)] = ((bits ushr i) and 1) != 0
        for (i in 8..14) modules[(size - 15 + i) * size + 8] = ((bits ushr i) and 1) != 0
    }

    private fun computePenaltyScore(modules: BooleanArray, size: Int): Int {
        var score = 0

        // Rule 1: 5+ consecutive same-color modules in rows & columns
        for (r in 0 until size) {
            var runLen = 1
            var prev = modules[r * size]
            for (c in 1 until size) {
                val cur = modules[r * size + c]
                if (cur == prev) {
                    runLen++
                } else {
                    if (runLen >= 5) score += 3 + (runLen - 5)
                    runLen = 1
                    prev = cur
                }
            }
            if (runLen >= 5) score += 3 + (runLen - 5)
        }
        for (c in 0 until size) {
            var runLen = 1
            var prev = modules[c]
            for (r in 1 until size) {
                val cur = modules[r * size + c]
                if (cur == prev) {
                    runLen++
                } else {
                    if (runLen >= 5) score += 3 + (runLen - 5)
                    runLen = 1
                    prev = cur
                }
            }
            if (runLen >= 5) score += 3 + (runLen - 5)
        }

        // Rule 2: 2x2 blocks of same color
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val v = modules[r * size + c]
                if (v == modules[r * size + c + 1] &&
                    v == modules[(r + 1) * size + c] &&
                    v == modules[(r + 1) * size + c + 1]
                ) {
                    score += 3
                }
            }
        }

        // Rule 3: Finder-like 1:1:3:1:1 pattern with 4-module white space
        val p1 = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        val p2 = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)
        for (r in 0 until size) {
            for (c in 0..size - 11) {
                var m1 = true
                var m2 = true
                for (k in 0 until 11) {
                    val v = modules[r * size + c + k]
                    if (v != p1[k]) m1 = false
                    if (v != p2[k]) m2 = false
                }
                if (m1 || m2) score += 40
            }
        }
        for (c in 0 until size) {
            for (r in 0..size - 11) {
                var m1 = true
                var m2 = true
                for (k in 0 until 11) {
                    val v = modules[(r + k) * size + c]
                    if (v != p1[k]) m1 = false
                    if (v != p2[k]) m2 = false
                }
                if (m1 || m2) score += 40
            }
        }

        // Rule 4: Dark/light ratio balance
        var darkCount = 0
        for (m in modules) if (m) darkCount++
        val total = size * size
        val percentDiff = kotlin.math.abs((darkCount * 20 - total * 10) / total)
        score += percentDiff * 10

        return score
    }
}

@Composable
fun QrCodePreviewCard(
    payload: String,
    caption: String,
    qrSize: Dp = 180.dp,
    modifier: Modifier = Modifier
) {
    val matrix = remember(payload) { QrCodeGenerator.encode(payload) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(qrSize + 24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(12.dp)
                .testTag("share_link_qr_code")
                .semantics { contentDescription = "Scannable QR code for $caption" }
        ) {
            if (matrix != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val quietZone = 2
                    val total = matrix.size + quietZone * 2
                    val cell = minOf(size.width, size.height) / total.toFloat()
                    val offsetX = (size.width - cell * total) / 2f
                    val offsetY = (size.height - cell * total) / 2f

                    for (r in 0 until matrix.size) {
                        for (c in 0 until matrix.size) {
                            if (matrix.get(r, c)) {
                                drawRect(
                                    color = Color(0xFF0E1116),
                                    topLeft = Offset(
                                        x = offsetX + (c + quietZone) * cell,
                                        y = offsetY + (r + quietZone) * cell
                                    ),
                                    size = Size(cell + 0.45f, cell + 0.45f)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "QR unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
