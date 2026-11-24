package com.example

import com.example.util.QrCodeGenerator
import com.example.util.ShareLinkCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkAndQrUnitTest {

    private val sampleToken = "bd6227dac47b379000b5b9161ff4ebb92e61a5d9130dd3d9e507f3e0930f5841"
    private val baseUrl = "https://galleryhub-n66c.onrender.com"

    @Test
    fun maskAndUnmaskHexTokenIsShorterAndLossless() {
        val masked = ShareLinkCodec.maskToken(sampleToken)
        assertTrue("Masked code should start with 'm'", masked.startsWith("m"))
        assertEquals("32-byte masked code should be 44 chars", 44, masked.length)
        assertFalse("Masked code should hide raw hex token", masked.contains("bd6227"))
        assertEquals(sampleToken, ShareLinkCodec.unmaskToken(masked))
    }

    @Test
    fun parseAllSupportedShareLinkFormats() {
        val shortUrl = ShareLinkCodec.buildShortMaskedUrl(baseUrl, sampleToken)
        val appUrl = ShareLinkCodec.buildAppSchemeUrl(sampleToken)
        val directUrl = "$baseUrl/api/share/$sampleToken"
        val legacyShareUrl = "$baseUrl/share/$sampleToken?password=secret123"

        assertTrue("Short URL should be shorter than direct URL", shortUrl.length < directUrl.length)

        val parsedShort = ShareLinkCodec.parseShareInput(shortUrl)
        assertNotNull(parsedShort)
        assertEquals(sampleToken, parsedShort!!.rawToken)
        assertEquals(baseUrl, parsedShort.hostBaseUrl)

        val parsedApp = ShareLinkCodec.parseShareInput(appUrl)
        assertNotNull(parsedApp)
        assertEquals(sampleToken, parsedApp!!.rawToken)

        val parsedDirect = ShareLinkCodec.parseShareInput(directUrl)
        assertNotNull(parsedDirect)
        assertEquals(sampleToken, parsedDirect!!.rawToken)

        val parsedLegacy = ShareLinkCodec.parseShareInput(legacyShareUrl)
        assertNotNull(parsedLegacy)
        assertEquals(sampleToken, parsedLegacy!!.rawToken)
        assertEquals("secret123", parsedLegacy.embeddedPassword)
    }

    @Test
    fun qrCodeGeneratorProducesValidMatrixWithFinderPatterns() {
        val shortUrl = ShareLinkCodec.buildShortMaskedUrl(baseUrl, sampleToken)
        val matrix = QrCodeGenerator.encode(shortUrl)
        assertNotNull("QR matrix should be generated", matrix)
        val qr = matrix!!
        assertTrue("Matrix size should match version formula", qr.size == qr.version * 4 + 17)
        // Verify top-left, top-right, and bottom-left finder centers (3,3), (3, size-4), (size-4, 3) are dark
        assertTrue(qr.get(3, 3))
        assertTrue(qr.get(3, qr.size - 4))
        assertTrue(qr.get(qr.size - 4, 3))
        // Verify finder separator white ring around top-left (0, 7) and (7, 0)
        assertFalse(qr.get(0, 7))
        assertFalse(qr.get(7, 0))
    }
}
