package com.example.util

import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.UnifiedMediaItem
import kotlin.math.abs

enum class DuplicateMatchKind(val label: String) {
    EXACT("Exact Duplicate"),
    NEAR("Near Duplicate")
}

data class DuplicateCluster(
    val id: String,
    val title: String,
    val matchKind: DuplicateMatchKind,
    val items: List<UnifiedMediaItem>,
    val reclaimableBytes: Long
)

data class StorageAuditReport(
    val duplicateClusters: List<DuplicateCluster> = emptyList(),
    val largestItems: List<UnifiedMediaItem> = emptyList(),
    val totalDuplicateCopiesCount: Int = 0,
    val totalReclaimableDuplicateBytes: Long = 0L,
    val totalOfflineVaultBytes: Long = 0L,
    val totalLibraryBytes: Long = 0L
)

/**
 * Pure Kotlin storage analyzer that detects exact duplicates, near-duplicates
 * (normalized base names with close sizes or copy suffixes like "photo (1).jpg"),
 * and largest files to help users reclaim Cloud quota and Offline Vault space.
 */
object StorageCleanerEngine {

    fun analyze(items: List<UnifiedMediaItem>): StorageAuditReport {
        val activeItems = items.filter { !it.isDeleted }
        if (activeItems.isEmpty()) return StorageAuditReport()

        val clusters = mutableListOf<DuplicateCluster>()
        val claimedIds = mutableSetOf<String>()

        // 1. Exact duplicate pass: identical normalized filename + identical non-zero byte size + mediaType
        val exactGroups = activeItems
            .filter { it.size > 0L }
            .groupBy { item ->
                "${item.mediaType.key}|${item.originalName.trim().lowercase()}|${item.size}"
            }
            .filterValues { it.size > 1 }

        for ((key, group) in exactGroups) {
            val sorted = group.sortedWith(
                compareByDescending<UnifiedMediaItem> { it.offlineState == OfflineAvailabilityState.PINNED }
                    .thenByDescending { it.isFavorite }
                    .thenBy { it.uploadDate }
            )
            sorted.forEach { claimedIds.add(it.id) }
            val reclaimable = sorted.drop(1).sumOf { it.size.coerceAtLeast(0L) }
            clusters.add(
                DuplicateCluster(
                    id = "exact_${key.hashCode()}",
                    title = sorted.first().originalName,
                    matchKind = DuplicateMatchKind.EXACT,
                    items = sorted,
                    reclaimableBytes = reclaimable
                )
            )
        }

        // 2. Near-duplicate pass: stripped copy suffixes ("(1)", "_copy", "-edited") with same type & close size (within 8%)
        val remaining = activeItems.filterNot { it.id in claimedIds }
        val nearBuckets = remaining.groupBy { item ->
            "${item.mediaType.key}|${normalizeStemForNearMatch(item.originalName)}|${item.extension}"
        }.filterValues { it.size > 1 }

        for ((bucketKey, candidates) in nearBuckets) {
            val sortedBySize = candidates.sortedByDescending { it.size }
            val anchor = sortedBySize.first()
            val closeMatches = sortedBySize.filter { candidate ->
                if (anchor.size <= 0L || candidate.size <= 0L) {
                    true
                } else {
                    val deltaRatio = abs(anchor.size - candidate.size).toDouble() / anchor.size.coerceAtLeast(1L)
                    deltaRatio <= 0.12
                }
            }
            if (closeMatches.size > 1) {
                val ordered = closeMatches.sortedWith(
                    compareByDescending<UnifiedMediaItem> { it.offlineState == OfflineAvailabilityState.PINNED }
                        .thenByDescending { it.isFavorite }
                        .thenByDescending { it.size }
                )
                ordered.forEach { claimedIds.add(it.id) }
                val reclaimable = ordered.drop(1).sumOf { it.size.coerceAtLeast(0L) }
                clusters.add(
                    DuplicateCluster(
                        id = "near_${bucketKey.hashCode()}",
                        title = ordered.first().originalName,
                        matchKind = DuplicateMatchKind.NEAR,
                        items = ordered,
                        reclaimableBytes = reclaimable
                    )
                )
            }
        }

        val sortedClusters = clusters.sortedByDescending { it.reclaimableBytes }
        val largestItems = activeItems.sortedByDescending { it.size }.take(20)
        val duplicateCopiesCount = sortedClusters.sumOf { (it.items.size - 1).coerceAtLeast(0) }
        val reclaimableTotal = sortedClusters.sumOf { it.reclaimableBytes }
        val offlineBytes = activeItems
            .filter { it.offlineState != OfflineAvailabilityState.NONE }
            .sumOf { it.size.coerceAtLeast(0L) }
        val totalBytes = activeItems.sumOf { it.size.coerceAtLeast(0L) }

        return StorageAuditReport(
            duplicateClusters = sortedClusters,
            largestItems = largestItems,
            totalDuplicateCopiesCount = duplicateCopiesCount,
            totalReclaimableDuplicateBytes = reclaimableTotal,
            totalOfflineVaultBytes = offlineBytes,
            totalLibraryBytes = totalBytes
        )
    }

    internal fun normalizeStemForNearMatch(rawName: String): String {
        val base = rawName.substringBeforeLast('.').trim().lowercase()
        return base
            .replace(Regex("""\s*\(\d+\)$"""), "")
            .replace(Regex("""[-_\s]+(copy|dup|duplicate|edited|edit|final|\d{1,2})$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
