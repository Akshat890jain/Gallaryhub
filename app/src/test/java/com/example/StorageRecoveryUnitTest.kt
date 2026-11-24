package com.example

import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.UnifiedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRecoveryUnitTest {

    @Test
    fun mediaItem_resolveBestRemotePath_prefersExternalHttpsUrlFields() {
        val cloudinaryItem = MediaItem(
            id = "673a1b2c",
            filename = "onboarding.png",
            originalName = "Onboarding pass 1.png",
            path = "uploads/onboarding.png",
            secureUrl = "https://res.cloudinary.com/demo/image/upload/v1/onboarding.png"
        )
        assertEquals(
            "https://res.cloudinary.com/demo/image/upload/v1/onboarding.png",
            cloudinaryItem.resolveBestRemotePath()
        )

        val fileUrlItem = MediaItem(
            id = "673a1b2d",
            filename = "invoice.pdf",
            originalName = "Invoice.pdf",
            path = "",
            fileUrl = "https://bucket.s3.amazonaws.com/docs/invoice.pdf"
        )
        assertEquals(
            "https://bucket.s3.amazonaws.com/docs/invoice.pdf",
            fileUrlItem.resolveBestRemotePath()
        )

        val localDiskItem = MediaItem(
            id = "673a1b2e",
            filename = "pass.png",
            originalName = "Onboarding pass 1.png",
            path = "uploads/pass.png"
        )
        assertEquals("uploads/pass.png", localDiskItem.resolveBestRemotePath())
    }

    @Test
    fun unifiedMediaItem_preservesExternalHttpsUrlWithoutRewriting() {
        val item = UnifiedMediaItem(
            id = "673a1b2c",
            filename = "onboarding.png",
            originalName = "Onboarding pass 1.png",
            path = "https://res.cloudinary.com/demo/image/upload/v1/onboarding.png",
            size = 153700L,
            uploadDate = "2026-11-15T10:00:00.000Z",
            mediaType = MediaType.IMAGE
        )
        val candidates = item.resolveRemoteCandidateUrls("https://galleryhub-n66c.onrender.com")
        assertEquals(
            listOf("https://res.cloudinary.com/demo/image/upload/v1/onboarding.png"),
            candidates
        )
    }

    @Test
    fun unifiedMediaItem_includesApiDownloadFallbacksForRemoteIdsOnly() {
        val remoteDoc = UnifiedMediaItem(
            id = "673a9999",
            filename = "onboarding.pdf",
            originalName = "Onboarding.pdf",
            path = "documents/onboarding.pdf",
            size = 55100L,
            uploadDate = "2026-11-15T10:00:00.000Z",
            mediaType = MediaType.DOCUMENT
        )
        val candidates = remoteDoc.resolveRemoteCandidateUrls("https://galleryhub-n66c.onrender.com")
        assertTrue(candidates.contains("https://galleryhub-n66c.onrender.com/documents/onboarding.pdf"))
        assertTrue(candidates.contains("https://galleryhub-n66c.onrender.com/api/documents/673a9999/download"))
        assertTrue(candidates.contains("https://galleryhub-n66c.onrender.com/api/documents/673a9999/file"))
    }

    @Test
    fun retrofitClient_buildAuthHeadersForUrl_scopesBearerTokenToGalleryHubHostOnly() {
        val originalToken = com.example.data.remote.RetrofitClient.jwtToken
        try {
            com.example.data.remote.RetrofitClient.jwtToken = "test_jwt_token_123"

            val galleryHubHeaders = com.example.data.remote.RetrofitClient.buildAuthHeadersForUrl(
                "https://galleryhub-n66c.onrender.com/videos/clip.mp4"
            )
            assertEquals("Bearer test_jwt_token_123", galleryHubHeaders["Authorization"])

            val externalCdnHeaders = com.example.data.remote.RetrofitClient.buildAuthHeadersForUrl(
                "https://res.cloudinary.com/demo/video/upload/clip.mp4"
            )
            assertTrue(externalCdnHeaders.isEmpty())

            val localFileHeaders = com.example.data.remote.RetrofitClient.buildAuthHeadersForUrl(
                "file:///data/user/0/com.example/files/clip.mp4"
            )
            assertTrue(localFileHeaders.isEmpty())
        } finally {
            com.example.data.remote.RetrofitClient.jwtToken = originalToken
        }
    }
}
