package com.example.data.remote

import com.example.data.model.ActivityPageResponse
import com.example.data.model.Folder
import com.example.data.model.MediaItem
import com.example.data.model.ShareLink
import com.example.data.model.User
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit

// ============================================================================
// Request & Response Payloads for ApiService
// ============================================================================

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    val name: String,
    val email: String,
    val currentPassword: String? = null,
    val newPassword: String? = null
)

@JsonClass(generateAdapter = true)
data class ZipImagesRequest(val imageIds: List<String>)

@JsonClass(generateAdapter = true)
data class ZipVideosRequest(val videoIds: List<String>)

@JsonClass(generateAdapter = true)
data class ZipDocumentsRequest(val documentIds: List<String>)

@JsonClass(generateAdapter = true)
data class FolderUpsertRequest(
    val name: String,
    val color: String
)

@JsonClass(generateAdapter = true)
data class FolderMoveFilesRequest(
    val fileIds: List<String>,
    val fileType: String
)

@JsonClass(generateAdapter = true)
data class FolderShareRequest(
    val email: String,
    val permission: String
)

@JsonClass(generateAdapter = true)
data class CreateShareLinkRequest(
    val fileId: String,
    val fileType: String,
    val password: String? = null,
    val expiresInDays: Int? = null
)

@JsonClass(generateAdapter = true)
data class VerifySharePasswordRequest(
    val password: String
)

@JsonClass(generateAdapter = true)
data class ChatbotPromptRequest(
    val message: String
)

@JsonClass(generateAdapter = true)
data class ChatbotPromptResponse(
    val success: Boolean? = null,
    val response: String? = null,
    val suggestions: List<String>? = null,
    val isAI: Boolean? = null
)

// ============================================================================
// ApiService Interface for https://galleryhub-n66c.onrender.com
// ============================================================================

interface ApiService {

    // --- Authentication & User Management (/api/users) ---
    @POST("api/users/register")
    suspend fun register(@Body body: RegisterRequest): Response<User>

    @POST("api/users/login")
    suspend fun login(@Body body: LoginRequest): Response<User>

    @GET("api/users/profile")
    suspend fun getProfile(): Response<User>

    @PUT("api/users/profile")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): Response<User>

    @Multipart
    @PUT("api/users/profile/picture")
    suspend fun uploadProfilePicture(@Part profilePicture: MultipartBody.Part): Response<User>

    // --- Image Management (/api/images) ---
    @GET("api/images")
    suspend fun getImages(): Response<List<MediaItem>>

    @Multipart
    @POST("api/images/upload")
    suspend fun uploadSingleImage(@Part image: MultipartBody.Part): Response<ResponseBody>

    @Multipart
    @POST("api/images/upload-multiple")
    suspend fun uploadMultipleImages(@Part images: List<MultipartBody.Part>): Response<ResponseBody>

    @DELETE("api/images/{id}")
    suspend fun softDeleteImage(@Path("id") id: String): Response<ResponseBody>

    @GET("api/images/trash")
    suspend fun getTrashedImages(): Response<List<MediaItem>>

    @PUT("api/images/{id}/restore")
    suspend fun restoreImage(@Path("id") id: String): Response<ResponseBody>

    @DELETE("api/images/{id}/permanent")
    suspend fun permanentDeleteImage(@Path("id") id: String): Response<ResponseBody>

    @Streaming
    @POST("api/images/download-zip")
    suspend fun downloadImagesZip(@Body body: ZipImagesRequest): Response<ResponseBody>

    // --- Video Management (/api/videos) ---
    @GET("api/videos")
    suspend fun getVideos(): Response<List<MediaItem>>

    @Multipart
    @POST("api/videos/upload-multiple")
    suspend fun uploadMultipleVideos(@Part videos: List<MultipartBody.Part>): Response<ResponseBody>

    @DELETE("api/videos/{id}")
    suspend fun softDeleteVideo(@Path("id") id: String): Response<ResponseBody>

    @GET("api/videos/trash")
    suspend fun getTrashedVideos(): Response<List<MediaItem>>

    @PUT("api/videos/{id}/restore")
    suspend fun restoreVideo(@Path("id") id: String): Response<ResponseBody>

    @DELETE("api/videos/{id}/permanent")
    suspend fun permanentDeleteVideo(@Path("id") id: String): Response<ResponseBody>

    @Streaming
    @POST("api/videos/download-zip")
    suspend fun downloadVideosZip(@Body body: ZipVideosRequest): Response<ResponseBody>

    // --- Document Management (/api/documents) ---
    @GET("api/documents")
    suspend fun getDocuments(): Response<List<MediaItem>>

    @Multipart
    @POST("api/documents/upload-multiple")
    suspend fun uploadMultipleDocuments(@Part documents: List<MultipartBody.Part>): Response<ResponseBody>

    @DELETE("api/documents/{id}")
    suspend fun softDeleteDocument(@Path("id") id: String): Response<ResponseBody>

    @GET("api/documents/trash")
    suspend fun getTrashedDocuments(): Response<List<MediaItem>>

    @PUT("api/documents/{id}/restore")
    suspend fun restoreDocument(@Path("id") id: String): Response<ResponseBody>

    @DELETE("api/documents/{id}/permanent")
    suspend fun permanentDeleteDocument(@Path("id") id: String): Response<ResponseBody>

    @Streaming
    @POST("api/documents/download-zip")
    suspend fun downloadDocumentsZip(@Body body: ZipDocumentsRequest): Response<ResponseBody>

    // --- Folder Organization & RBAC Sharing (/api/folders) ---
    @GET("api/folders")
    suspend fun getFolders(): Response<List<Folder>>

    @POST("api/folders")
    suspend fun createFolder(@Body body: FolderUpsertRequest): Response<Folder>

    @PUT("api/folders/remove-files")
    suspend fun removeFilesFromFolder(@Body body: FolderMoveFilesRequest): Response<ResponseBody>

    @PUT("api/folders/{id}")
    suspend fun updateFolder(@Path("id") id: String, @Body body: FolderUpsertRequest): Response<Folder>

    @DELETE("api/folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String): Response<ResponseBody>

    @PUT("api/folders/{id}/move")
    suspend fun moveFilesToFolder(@Path("id") id: String, @Body body: FolderMoveFilesRequest): Response<ResponseBody>

    @POST("api/folders/{id}/share")
    suspend fun shareFolder(@Path("id") id: String, @Body body: FolderShareRequest): Response<Folder>

    @DELETE("api/folders/{id}/share/{userId}")
    suspend fun unshareFolder(@Path("id") id: String, @Path("userId") userId: String): Response<Folder>

    @GET("api/folders/shared")
    suspend fun getSharedFolders(): Response<List<Folder>>

    // --- Public Share Links (/api/share) ---
    @POST("api/share")
    suspend fun createShareLink(@Body body: CreateShareLinkRequest): Response<ShareLink>

    @GET("api/share/my-links")
    suspend fun getMyShareLinks(): Response<List<ShareLink>>

    @DELETE("api/share/{id}")
    suspend fun deleteShareLink(@Path("id") id: String): Response<ResponseBody>

    @POST("api/share/{token}/verify")
    suspend fun verifySharePassword(
        @Path("token") token: String,
        @Body body: VerifySharePasswordRequest
    ): Response<ResponseBody>

    @Streaming
    @GET("api/share/{token}")
    suspend fun getSharedFileStream(
        @Path("token") token: String,
        @Query("p") password: String? = null
    ): Response<ResponseBody>

    // --- Activity Feed, AI Chatbot & Health (/api/activity, /api/chatbot, /api/ping) ---
    @GET("api/activity")
    suspend fun getActivities(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 25
    ): Response<ActivityPageResponse>

    @POST("api/chatbot")
    suspend fun sendChatbotMessage(@Body body: ChatbotPromptRequest): Response<ChatbotPromptResponse>

    @GET("api/test")
    suspend fun testBackend(): Response<ResponseBody>

    @GET("api/ping")
    suspend fun pingBackend(): Response<ResponseBody>
}

// ============================================================================
// RetrofitClient Singleton Object Targeting https://galleryhub-n66c.onrender.com
// ============================================================================

object RetrofitClient {

    const val DEFAULT_BASE_URL = "https://galleryhub-n66c.onrender.com/"

    @Volatile
    var baseUrl: String = "https://galleryhub-n66c.onrender.com"
        set(value) {
            val cleaned = value.trim().trimEnd('/')
            field = if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                cleaned
            } else {
                "https://$cleaned"
            }
        }

    @Volatile
    var jwtToken: String? = null

    private val defaultApiHost: String? by lazy {
        DEFAULT_BASE_URL.toHttpUrlOrNull()?.host?.lowercase()
    }

    private val authAndHostInterceptor = Interceptor { chain ->
        var request = chain.request()
        val targetBase = baseUrl.toHttpUrlOrNull()
        val requestHost = request.url.host.lowercase()
        val targetHost = targetBase?.host?.lowercase()

        // Only rewrite requests targeting GalleryHub's API host — never hijack external CDN/storage hosts (Cloudinary, S3, GCS, etc.)
        val isDefaultHost = defaultApiHost != null && requestHost == defaultApiHost
        val isConfiguredHost = targetHost != null && requestHost == targetHost
        val isGalleryHubApiRequest = isDefaultHost || isConfiguredHost

        if (isGalleryHubApiRequest && targetBase != null) {
            if (isDefaultHost && !isConfiguredHost) {
                val newPath = buildString {
                    val basePath = targetBase.encodedPath.trimEnd('/')
                    if (basePath.isNotEmpty() && !request.url.encodedPath.startsWith(basePath)) {
                        append(basePath)
                    }
                    append(request.url.encodedPath)
                }
                val newUrl = request.url.newBuilder()
                    .scheme(targetBase.scheme)
                    .host(targetBase.host)
                    .port(targetBase.port)
                    .encodedPath(newPath)
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }

            val token = jwtToken
            if (!token.isNullOrBlank() && request.header("Authorization") == null) {
                request = request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            }
        }
        chain.proceed(request)
    }

    private val connectionPool = okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES)

    @Volatile
    private var httpCache: okhttp3.Cache? = null

    fun initializeHttpCache(cacheDir: java.io.File) {
        if (httpCache == null) {
            val dir = java.io.File(cacheDir, "okhttp_http_cache").apply { mkdirs() }
            httpCache = okhttp3.Cache(dir, 60L * 1024L * 1024L) // 60 MB HTTP cache
        }
    }

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .apply { httpCache?.let { cache(it) } }
            .addInterceptor(authAndHostInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Lightweight, fast-fail OkHttpClient dedicated to connectivity pings so offline checks
     * never stall the UI waiting for a 45-second socket timeout.
     */
    val fastPingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .addInterceptor(authAndHostInterceptor)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }

    val fastPingService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(fastPingClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }

    fun buildAuthHeadersForUrl(url: String): Map<String, String> {
        val token = jwtToken?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val parsed = url.toHttpUrlOrNull() ?: return emptyMap()
        val targetBase = baseUrl.toHttpUrlOrNull()
        val defaultHost = DEFAULT_BASE_URL.toHttpUrlOrNull()?.host
        val isGalleryHubHost = (defaultHost != null && parsed.host.equals(defaultHost, ignoreCase = true)) ||
            (targetBase != null && parsed.host.equals(targetBase.host, ignoreCase = true))
        return if (isGalleryHubHost) mapOf("Authorization" to "Bearer $token") else emptyMap()
    }

    fun parseErrorMessage(errorBody: ResponseBody?, defaultMsg: String): String {
        return try {
            val raw = errorBody?.string().orEmpty()
            if (raw.isBlank()) return defaultMsg
            val json = JSONObject(raw)
            json.optString("message").ifBlank { defaultMsg }
        } catch (_: Exception) {
            defaultMsg
        }
    }
}
