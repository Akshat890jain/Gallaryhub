package com.example

import com.example.data.local.SessionPreferences
import com.example.data.model.MediaType
import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.SmartCollectionFilter
import com.example.data.model.UnifiedMediaItem
import com.example.util.DuplicateMatchKind
import com.example.util.StorageCleanerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProSuiteUnitTest {

    private fun sampleItem(
        id: String,
        name: String,
        type: MediaType = MediaType.IMAGE,
        size: Long = 100_000L,
        folderId: String? = null,
        isFavorite: Boolean = false,
        isDeleted: Boolean = false,
        offlineState: OfflineAvailabilityState = OfflineAvailabilityState.NONE,
        tags: List<String> = emptyList()
    ): UnifiedMediaItem {
        return UnifiedMediaItem(
            id = id,
            filename = name,
            originalName = name,
            path = "/uploads/$name",
            size = size,
            mediaType = type,
            uploadDate = "2026-11-15T10:00:00.000Z",
            folderId = folderId,
            isFavorite = isFavorite,
            isDeleted = isDeleted,
            offlineState = offlineState,
            tags = tags
        )
    }

    @Test
    fun storageCleaner_detectsExactAndNearDuplicatesAndLargestFiles() {
        val items = listOf(
            sampleItem("1", "Vacation.jpg", size = 500_000L, isFavorite = true),
            sampleItem("2", "Vacation.jpg", size = 500_000L),
            sampleItem("3", "Receipt_Store.png", size = 200_000L),
            sampleItem("4", "Receipt_Store (1).png", size = 205_000L),
            sampleItem("5", "Movie_4K.mp4", type = MediaType.VIDEO, size = 12_000_000L, offlineState = OfflineAvailabilityState.PINNED),
            sampleItem("6", "Deleted_Copy.jpg", size = 500_000L, isDeleted = true)
        )

        val report = StorageCleanerEngine.analyze(items)

        assertEquals(2, report.duplicateClusters.size)
        assertEquals(2, report.totalDuplicateCopiesCount)
        assertTrue(report.duplicateClusters.any { it.matchKind == DuplicateMatchKind.EXACT })
        assertTrue(report.duplicateClusters.any { it.matchKind == DuplicateMatchKind.NEAR })

        val exactCluster = report.duplicateClusters.first { it.matchKind == DuplicateMatchKind.EXACT }
        // Favorite should be kept first
        assertEquals("1", exactCluster.items.first().id)
        assertEquals(500_000L, exactCluster.reclaimableBytes)

        // Largest file is the 12MB video, deleted items ignored
        assertEquals("5", report.largestItems.first().id)
        assertFalse(report.largestItems.any { it.id == "6" })
        assertEquals(12_000_000L, report.totalOfflineVaultBytes)
    }

    @Test
    fun smartCollections_matchExpectedMediaCategories() {
        val screenshot = sampleItem("s1", "Screenshot_20261115_0848.png", folderId = "f1")
        val invoice = sampleItem("r1", "Invoice_2781209.pdf", type = MediaType.DOCUMENT)
        val passImg = sampleItem("r2", "Onboarding pass 1.png")
        val video = sampleItem("v1", "Clip_Trip.mp4", type = MediaType.VIDEO)
        val audio = sampleItem("a1", "Voice_Note.m4a", type = MediaType.DOCUMENT)
        val edited = sampleItem("edt_99", "Portrait_edited.jpg", folderId = "f1")

        assertTrue(SmartCollectionFilter.SCREENSHOTS.matches(screenshot))
        assertFalse(SmartCollectionFilter.SCREENSHOTS.matches(invoice))

        assertTrue(SmartCollectionFilter.RECEIPTS_PDFS.matches(invoice))
        assertTrue(SmartCollectionFilter.RECEIPTS_PDFS.matches(passImg))
        assertFalse(SmartCollectionFilter.RECEIPTS_PDFS.matches(video))

        assertTrue(SmartCollectionFilter.VIDEOS.matches(video))
        assertTrue(SmartCollectionFilter.AUDIO.matches(audio))
        assertTrue(SmartCollectionFilter.RECENTLY_EDITED.matches(edited))
        assertTrue(SmartCollectionFilter.UNCATEGORIZED.matches(invoice))
        assertFalse(SmartCollectionFilter.UNCATEGORIZED.matches(screenshot))
    }

    @Test
    fun vaultPinHashing_verifiesValidPinAndRejectsWrongOrMissingHash() {
        val hash = SessionPreferences.hashVaultPin("4829")
        assertNotEquals("4829", hash)
        assertTrue(hash.length == 64)
        assertTrue(SessionPreferences.verifyVaultPinHash("4829", hash))
        assertFalse(SessionPreferences.verifyVaultPinHash("0000", hash))
        assertFalse(SessionPreferences.verifyVaultPinHash("4829", null))
        assertFalse(SessionPreferences.verifyVaultPinHash("4829", ""))
    }
}
