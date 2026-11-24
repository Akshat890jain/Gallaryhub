package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class MediaOverrideDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mediaId: String = "",
    val favorite: Boolean = false,
    val customTags: List<String> = emptyList(),
    val renamedTitle: String? = null,
    val hiddenInVault: Boolean = false,
    val ocrText: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudioStateDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vaultPinHash: String? = null,
    val vaultBiometricEnabled: Boolean = true,
    val clearedActivityIds: List<String> = emptyList(),
    val themeMode: String = "system",
    val accentHex: String = "#C85A32",
    val exifScrubDefaultOn: Boolean = true,
    val wifiOnlyUploads: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Companion Firestore repository that persists cross-device metadata not stored on the Render server:
 * Favorites, Custom #Tags, Renamed Titles, Private Vault hidden items & salted PIN hash,
 * OCR text index, Cleared Activity IDs, and Studio preferences.
 *
 * Always targets the named Firestore database from R.string.firestore_database_id.
 */
class CompanionCloudRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Cloud Sync sign-in required before accessing Firestore.")
    }

    fun sanitizeDocId(rawId: String): String {
        val cleaned = rawId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        return cleaned.ifBlank { "item_default" }
    }

    fun observeMediaOverrides(uid: String): Flow<List<MediaOverrideDoc>> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return@flow
        val path = "users/$uid/mediaOverrides"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("mediaOverrides")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MediaOverrideDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeStudioState(uid: String): Flow<StudioStateDoc?> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return@flow
        val path = "users/$uid/studioState/profile"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("studioState")
                .document("profile")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            StudioStateDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Creates or updates a media override document matching firestore.rules 1:1.
     */
    suspend fun upsertMediaOverride(
        rawMediaId: String,
        favorite: Boolean,
        customTags: List<String>,
        renamedTitle: String?,
        hiddenInVault: Boolean,
        ocrText: String?
    ): Result<Unit> {
        val safeMediaId = sanitizeDocId(rawMediaId)
        val currentUid = auth.currentUser?.uid
        val path = "users/${currentUid ?: "unauthenticated"}/mediaOverrides/$safeMediaId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users")
                .document(uid)
                .collection("mediaOverrides")
                .document(safeMediaId)

            val boundedTags = customTags
                .map { it.trim().removePrefix("#").take(60) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(30)
            val boundedTitle = renamedTitle?.trim()?.take(200)?.takeIf { it.isNotBlank() }
            val boundedOcr = ocrText?.trim()?.take(12000)?.takeIf { it.isNotBlank() }

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mutableMapOf<String, Any?>(
                    "favorite" to favorite,
                    "customTags" to boundedTags,
                    "hiddenInVault" to hiddenInVault,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (boundedTitle != null) updatePayload["renamedTitle"] = boundedTitle
                if (boundedOcr != null) updatePayload["ocrText"] = boundedOcr
                docRef.update(updatePayload.filterValues { it != null }).await()
            } else {
                val createPayload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "mediaId" to safeMediaId,
                    "favorite" to favorite,
                    "customTags" to boundedTags,
                    "hiddenInVault" to hiddenInVault,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (boundedTitle != null) createPayload["renamedTitle"] = boundedTitle
                if (boundedOcr != null) createPayload["ocrText"] = boundedOcr
                docRef.set(createPayload.filterValues { it != null }).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Creates or updates the per-user StudioState profile document matching firestore.rules 1:1.
     */
    suspend fun upsertStudioState(
        vaultPinHash: String?,
        vaultBiometricEnabled: Boolean,
        clearedActivityIds: List<String>,
        themeMode: String,
        accentHex: String,
        exifScrubDefaultOn: Boolean,
        wifiOnlyUploads: Boolean
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid
        val path = "users/${currentUid ?: "unauthenticated"}/studioState/profile"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users")
                .document(uid)
                .collection("studioState")
                .document("profile")

            val validTheme = themeMode.lowercase().takeIf { it in setOf("system", "light", "dark") } ?: "system"
            val validAccent = accentHex.trim().take(12).takeIf { it.length >= 4 } ?: "#C85A32"
            val validPinHash = vaultPinHash?.trim()?.take(128)?.takeIf { it.length >= 16 }
            val boundedCleared = clearedActivityIds
                .map { it.trim().take(128) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(200)

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mutableMapOf<String, Any?>(
                    "vaultPinHash" to validPinHash,
                    "vaultBiometricEnabled" to vaultBiometricEnabled,
                    "clearedActivityIds" to boundedCleared,
                    "themeMode" to validTheme,
                    "accentHex" to validAccent,
                    "exifScrubDefaultOn" to exifScrubDefaultOn,
                    "wifiOnlyUploads" to wifiOnlyUploads,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                // Allow explicit null when user resets Vault PIN, since rules permit null
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "vaultBiometricEnabled" to vaultBiometricEnabled,
                    "clearedActivityIds" to boundedCleared,
                    "themeMode" to validTheme,
                    "accentHex" to validAccent,
                    "exifScrubDefaultOn" to exifScrubDefaultOn,
                    "wifiOnlyUploads" to wifiOnlyUploads,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (validPinHash != null) createPayload["vaultPinHash"] = validPinHash
                docRef.set(createPayload.filterValues { it != null }).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
