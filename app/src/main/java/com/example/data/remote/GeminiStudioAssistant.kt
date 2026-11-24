package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/**
 * Live streaming Studio Assistant powered by Firebase AI Logic (Gemini 3.8 Flash).
 * Replaces static hardcoded chatbot templates with real, library-aware answers.
 */
class GeminiStudioAssistant {

    companion object {
        private const val TAG = "GeminiStudioAssistant"
        private const val MODEL_NAME = "gemini-3.8-flash"
        private const val STREAM_TIMEOUT_MS = 60_000L
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    """
                    You are GalleryHub Studio Assistant, a helpful, concise companion embedded inside the GalleryHub Android app.
                    Answer questions accurately using the user's live library summary provided with each prompt.
                    Key GalleryHub capabilities you can guide users to:
                    - Gallery Filter & Tools buttons next to the Search bar (Categories, Smart Collections, #Tags, Sort, View Modes including Timeline, On This Day Memories, Private Biometric Vault, Duplicate & Large File Cleaner, Multi-Page PDF Document Scanner, QR Share Link Scanner, and On-Device ML Kit OCR text search).
                    - Photo Editor with Crop, Rotate, Filters, Adjustments, and Markup (Pen, Highlighter, Arrow, Text Callout, Blur Redaction).
                    - Video Player with playback speed (0.5x-2x), double-tap +/-10s skip, brightness/volume swipe gestures, Picture-in-Picture, Frame Grab, and Clip Trimmer.
                    - Sharing with EXIF Metadata Scrubber (strips GPS & camera info by default), Password/Expiry Share Links, QR Codes, and receiving shares from other apps.
                    - Offline Vault auto-pinning + Companion Cloud Sync (Google Sign-In for Firestore metadata sync alongside the Render media server).
                    Keep answers crisp, friendly, and directly actionable (2-5 short sentences or brief bullet points).
                    """.trimIndent()
                )
            }
        )
    }

    data class LibraryContext(
        val totalFiles: Int,
        val photoCount: Int,
        val videoCount: Int,
        val docCount: Int,
        val favoriteCount: Int,
        val offlineCount: Int,
        val hiddenVaultCount: Int,
        val folderNames: List<String>,
        val usedStorageFormatted: String,
        val quotaStorageFormatted: String,
        val recentFilenames: List<String>,
        val topTags: List<String>,
        val ocrIndexedCount: Int
    )

    fun streamReply(
        userPrompt: String,
        libraryContext: LibraryContext,
        recentHistory: List<Pair<Boolean, String>> = emptyList()
    ): Flow<String> = flow {
        val contextBlock = buildString {
            appendLine("[Live GalleryHub Library Context]")
            appendLine("- Active files: ${libraryContext.totalFiles} (${libraryContext.photoCount} photos, ${libraryContext.videoCount} videos, ${libraryContext.docCount} documents)")
            appendLine("- Favorites: ${libraryContext.favoriteCount}, Offline saved: ${libraryContext.offlineCount}, Private Vault hidden: ${libraryContext.hiddenVaultCount}, OCR indexed: ${libraryContext.ocrIndexedCount}")
            appendLine("- Storage: ${libraryContext.usedStorageFormatted} used of ${libraryContext.quotaStorageFormatted}")
            if (libraryContext.folderNames.isNotEmpty()) {
                appendLine("- Folders: ${libraryContext.folderNames.take(10).joinToString(", ")}")
            }
            if (libraryContext.recentFilenames.isNotEmpty()) {
                appendLine("- Recent files: ${libraryContext.recentFilenames.take(8).joinToString(", ")}")
            }
            if (libraryContext.topTags.isNotEmpty()) {
                appendLine("- Common #tags: ${libraryContext.topTags.take(10).joinToString(", ") { "#$it" }}")
            }
            if (recentHistory.isNotEmpty()) {
                appendLine("- Recent chat turns:")
                recentHistory.takeLast(4).forEach { (isUser, msg) ->
                    appendLine("  ${if (isUser) "User" else "Assistant"}: ${msg.take(240)}")
                }
            }
            appendLine()
            append("User question: $userPrompt")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(STREAM_TIMEOUT_MS) {
                model.generateContentStream(contextBlock).collect { chunk ->
                    val part = chunk.text.orEmpty()
                    if (part.isNotEmpty()) {
                        accumulated.append(part)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response. Please try again.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out", e)
            throw IllegalStateException("AI response timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic streaming failed", e)
            throw e
        }
    }
}
