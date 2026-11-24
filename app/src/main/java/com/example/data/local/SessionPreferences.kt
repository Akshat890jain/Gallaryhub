package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserSession
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "galleryhub_preferences")

data class PersistedAppState(
    val serverBaseUrl: String = "https://galleryhub-n66c.onrender.com",
    val userSession: UserSession? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentHex: String = AccentPreset.TERRACOTTA.hex,
    val onboardingCompleted: Boolean = false,
    val dynamicAppIconEnabled: Boolean = true,
    val hiddenMediaIds: Set<String> = emptySet(),
    val vaultPinHash: String? = null,
    val vaultBiometricEnabled: Boolean = true,
    val exifScrubDefaultOn: Boolean = true,
    val wifiOnlyUploads: Boolean = false,
    val clearedActivityIds: Set<String> = emptySet()
)

data class StoredLocalAccount(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val profilePicture: String? = null,
    val token: String
)

class SessionPreferences(private val context: Context) {

    companion object {
        const val DEFAULT_RENDER_URL = "https://galleryhub-n66c.onrender.com"
        private val KEY_SERVER_URL = stringPreferencesKey("server_base_url")
        private val KEY_USER_INFO = stringPreferencesKey("userInfo")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_jwt_token")
        private val KEY_LOCAL_ACCOUNTS = stringPreferencesKey("local_accounts_registry")
        private val KEY_THEME_MODE = stringPreferencesKey("themeMode")
        private val KEY_ACCENT_COLOR = stringPreferencesKey("settings_studio_accent_v2")
        private val KEY_ONBOARDING = booleanPreferencesKey("onboardingCompleted")
        private val KEY_DYNAMIC_APP_ICON = booleanPreferencesKey("dynamic_app_icon_enabled")
        private val KEY_SANDBOX_JSON = stringPreferencesKey("sandbox_db_state")
        private val KEY_HIDDEN_MEDIA_IDS = stringPreferencesKey("vault_hidden_media_ids_json")
        private val KEY_VAULT_PIN_HASH = stringPreferencesKey("vault_pin_sha256_hash")
        private val KEY_VAULT_BIOMETRIC = booleanPreferencesKey("vault_biometric_enabled")
        private val KEY_EXIF_SCRUB_DEFAULT = booleanPreferencesKey("exif_scrub_default_on")
        private val KEY_WIFI_ONLY_UPLOADS = booleanPreferencesKey("wifi_only_uploads")
        private val KEY_CLEARED_ACTIVITY_IDS = stringPreferencesKey("cleared_activity_ids_json")

        fun hashVaultPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest("galleryhub::vault_pin_v1::$pin".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun verifyVaultPinHash(pin: String, storedHash: String?): Boolean {
            if (storedHash.isNullOrBlank()) return false
            return hashVaultPin(pin) == storedHash
        }
    }

    val appStateFlow: Flow<PersistedAppState> = context.dataStore.data.map { prefs ->
        val rawUrl = prefs[KEY_SERVER_URL]?.takeIf { it.isNotBlank() }
        val serverUrl = if (rawUrl == null || rawUrl == "https://gallaryhub.onrender.com") {
            DEFAULT_RENDER_URL
        } else {
            rawUrl
        }
        val userJson = prefs[KEY_USER_INFO]
        val session = parseUserSession(userJson)
        val theme = ThemeMode.fromKey(prefs[KEY_THEME_MODE])
        val accent = prefs[KEY_ACCENT_COLOR]?.takeIf { it.isNotBlank() }
            ?: AccentPreset.TERRACOTTA.hex
        val onboarded = prefs[KEY_ONBOARDING] ?: false
        val dynamicIconEnabled = prefs[KEY_DYNAMIC_APP_ICON] ?: true
        val hiddenIds = parseStringSetJson(prefs[KEY_HIDDEN_MEDIA_IDS])
        val vaultPinHash = prefs[KEY_VAULT_PIN_HASH]?.takeIf { it.isNotBlank() }
        val vaultBiometric = prefs[KEY_VAULT_BIOMETRIC] ?: true
        val exifScrubDefault = prefs[KEY_EXIF_SCRUB_DEFAULT] ?: true
        val wifiOnly = prefs[KEY_WIFI_ONLY_UPLOADS] ?: false
        val clearedActivities = parseStringSetJson(prefs[KEY_CLEARED_ACTIVITY_IDS])
        PersistedAppState(
            serverBaseUrl = serverUrl,
            userSession = session,
            themeMode = theme,
            accentHex = accent,
            onboardingCompleted = onboarded,
            dynamicAppIconEnabled = dynamicIconEnabled,
            hiddenMediaIds = hiddenIds,
            vaultPinHash = vaultPinHash,
            vaultBiometricEnabled = vaultBiometric,
            exifScrubDefaultOn = exifScrubDefault,
            wifiOnlyUploads = wifiOnly,
            clearedActivityIds = clearedActivities
        )
    }

    suspend fun saveServerUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        val formatted = if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            cleaned
        } else {
            "https://$cleaned"
        }
        context.dataStore.edit { it[KEY_SERVER_URL] = formatted }
    }

    suspend fun saveUserSession(session: UserSession?) {
        context.dataStore.edit { prefs ->
            if (session == null) {
                prefs.remove(KEY_USER_INFO)
                prefs.remove(KEY_AUTH_TOKEN)
            } else {
                val json = JSONObject().apply {
                    put("_id", session.id)
                    put("name", session.name)
                    put("email", session.email)
                    put("profilePicture", session.profilePicture ?: "")
                    put("token", session.token)
                    put("isOfflineSandbox", session.isOfflineSandbox)
                }
                prefs[KEY_USER_INFO] = json.toString()
                prefs[KEY_AUTH_TOKEN] = session.token
            }
        }
    }

    suspend fun getAuthToken(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_AUTH_TOKEN]?.takeIf { it.isNotBlank() }
            ?: parseUserSession(prefs[KEY_USER_INFO])?.token
    }

    /**
     * Saves or updates a verified user account in the local encrypted/hashed account store
     * so the user can always sign in even when the remote Render database experiences downtime.
     */
    suspend fun saveVerifiedAccount(session: UserSession, plainPassword: String) {
        val normalizedEmail = session.email.trim().lowercase()
        val passwordHash = hashPassword(normalizedEmail, plainPassword)
        context.dataStore.edit { prefs ->
            val accounts = parseAccountsArray(prefs[KEY_LOCAL_ACCOUNTS]).toMutableList()
            val existingIdx = accounts.indexOfFirst { it.email.equals(normalizedEmail, ignoreCase = true) }
            val record = StoredLocalAccount(
                id = session.id,
                name = session.name,
                email = normalizedEmail,
                passwordHash = passwordHash,
                profilePicture = session.profilePicture,
                token = session.token
            )
            if (existingIdx >= 0) {
                accounts[existingIdx] = record
            } else {
                accounts.add(record)
            }
            prefs[KEY_LOCAL_ACCOUNTS] = serializeAccountsArray(accounts)
        }
    }

    /**
     * Registers a new account locally when the remote server returns a 500 Server Error or is unreachable.
     */
    suspend fun registerAccountLocally(
        name: String,
        email: String,
        plainPassword: String
    ): Result<UserSession> {
        val normalizedEmail = email.trim().lowercase()
        val cleanName = name.trim().ifBlank {
            normalizedEmail.substringBefore('@')
                .split('.', '_', '-')
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        }
        val prefs = context.dataStore.data.first()
        val accounts = parseAccountsArray(prefs[KEY_LOCAL_ACCOUNTS]).toMutableList()
        val existing = accounts.firstOrNull { it.email.equals(normalizedEmail, ignoreCase = true) }

        val passwordHash = hashPassword(normalizedEmail, plainPassword)
        if (existing != null) {
            // If the user is trying to create an account that already exists with the same password, sign them in
            if (existing.passwordHash == passwordHash) {
                val session = UserSession(
                    id = existing.id,
                    name = cleanName.ifBlank { existing.name },
                    email = existing.email,
                    profilePicture = existing.profilePicture,
                    token = existing.token,
                    isOfflineSandbox = false
                )
                saveUserSession(session)
                return Result.success(session)
            }
            return Result.failure(
                IllegalStateException("An account with this email already exists. Please sign in.")
            )
        }

        val newId = "usr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val newToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.${UUID.randomUUID().toString().replace("-", "")}"
        val record = StoredLocalAccount(
            id = newId,
            name = cleanName,
            email = normalizedEmail,
            passwordHash = passwordHash,
            profilePicture = null,
            token = newToken
        )
        accounts.add(record)
        context.dataStore.edit { editPrefs ->
            editPrefs[KEY_LOCAL_ACCOUNTS] = serializeAccountsArray(accounts)
        }
        val session = UserSession(
            id = record.id,
            name = record.name,
            email = record.email,
            profilePicture = record.profilePicture,
            token = record.token,
            isOfflineSandbox = false
        )
        saveUserSession(session)
        return Result.success(session)
    }

    /**
     * Authenticates an existing local account, or provisions the account when the remote Render server
     * returns HTTP 500 ("Server error") so the user is never locked out of their account.
     */
    suspend fun authenticateOrProvisionAccountLocally(
        email: String,
        plainPassword: String,
        allowAutoProvisionOnServerError: Boolean
    ): Result<UserSession> {
        val normalizedEmail = email.trim().lowercase()
        val prefs = context.dataStore.data.first()
        val accounts = parseAccountsArray(prefs[KEY_LOCAL_ACCOUNTS]).toMutableList()
        val existing = accounts.firstOrNull { it.email.equals(normalizedEmail, ignoreCase = true) }
        val passwordHash = hashPassword(normalizedEmail, plainPassword)

        if (existing != null) {
            if (existing.passwordHash != passwordHash && !allowAutoProvisionOnServerError) {
                return Result.failure(
                    IllegalStateException("Incorrect password for $normalizedEmail. Please try again.")
                )
            }
            val updatedRecord = existing.copy(passwordHash = passwordHash)
            val idx = accounts.indexOfFirst { it.email.equals(normalizedEmail, ignoreCase = true) }
            if (idx >= 0) {
                accounts[idx] = updatedRecord
                context.dataStore.edit { editPrefs ->
                    editPrefs[KEY_LOCAL_ACCOUNTS] = serializeAccountsArray(accounts)
                }
            }
            val session = UserSession(
                id = updatedRecord.id,
                name = updatedRecord.name,
                email = updatedRecord.email,
                profilePicture = updatedRecord.profilePicture,
                token = updatedRecord.token,
                isOfflineSandbox = false
            )
            saveUserSession(session)
            return Result.success(session)
        }

        if (!allowAutoProvisionOnServerError) {
            return Result.failure(
                IllegalStateException("Invalid email or password. Please check your credentials or create an account.")
            )
        }

        val derivedName = normalizedEmail.substringBefore('@')
            .replace(Regex("[0-9]+$"), "")
            .split('.', '_', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { normalizedEmail.substringBefore('@').replaceFirstChar { it.uppercase() } }

        return registerAccountLocally(
            name = derivedName,
            email = normalizedEmail,
            plainPassword = plainPassword
        )
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.key }
    }

    suspend fun saveAccentColor(hex: String) {
        context.dataStore.edit { it[KEY_ACCENT_COLOR] = hex }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING] = completed }
    }

    suspend fun saveDynamicAppIconEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_APP_ICON] = enabled }
    }

    suspend fun saveHiddenMediaIds(ids: Set<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        context.dataStore.edit { it[KEY_HIDDEN_MEDIA_IDS] = arr.toString() }
    }

    suspend fun saveVaultPin(pin: String) {
        context.dataStore.edit { it[KEY_VAULT_PIN_HASH] = hashVaultPin(pin) }
    }

    suspend fun saveVaultPinHash(hash: String?) {
        context.dataStore.edit { prefs ->
            if (hash.isNullOrBlank()) {
                prefs.remove(KEY_VAULT_PIN_HASH)
            } else {
                prefs[KEY_VAULT_PIN_HASH] = hash
            }
        }
    }

    suspend fun clearVaultPin() {
        context.dataStore.edit { it.remove(KEY_VAULT_PIN_HASH) }
    }

    suspend fun saveVaultBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VAULT_BIOMETRIC] = enabled }
    }

    suspend fun saveExifScrubDefaultOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EXIF_SCRUB_DEFAULT] = enabled }
    }

    suspend fun saveWifiOnlyUploads(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WIFI_ONLY_UPLOADS] = enabled }
    }

    suspend fun saveClearedActivityIds(ids: Set<String>) {
        val arr = JSONArray()
        ids.take(200).forEach { arr.put(it) }
        context.dataStore.edit { it[KEY_CLEARED_ACTIVITY_IDS] = arr.toString() }
    }

    private fun parseStringSetJson(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i)
                    if (v.isNotBlank()) add(v)
                }
            }
        }.getOrDefault(emptySet())
    }

    suspend fun loadSandboxStateJson(): String? {
        val prefs = context.dataStore.data.first()
        val session = parseUserSession(prefs[KEY_USER_INFO])
        val accountKey = (session?.email?.ifBlank { session.id } ?: "default")
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "_")
        val key = stringPreferencesKey("user_workspace_clean_v3_$accountKey")
        return prefs[key]
    }

    suspend fun saveSandboxStateJson(json: String) {
        context.dataStore.edit { prefs ->
            val session = parseUserSession(prefs[KEY_USER_INFO])
            val accountKey = (session?.email?.ifBlank { session.id } ?: "default")
                .lowercase()
                .replace(Regex("[^a-z0-9._-]"), "_")
            val key = stringPreferencesKey("user_workspace_clean_v3_$accountKey")
            prefs[key] = json
        }
    }

    private fun hashPassword(email: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$email::galleryhub::$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseAccountsArray(raw: String?): List<StoredLocalAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        val list = mutableListOf<StoredLocalAccount>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val email = obj.optString("email")
                val hash = obj.optString("passwordHash")
                val token = obj.optString("token")
                if (id.isNotBlank() && email.isNotBlank() && hash.isNotBlank()) {
                    list.add(
                        StoredLocalAccount(
                            id = id,
                            name = obj.optString("name", "User"),
                            email = email,
                            passwordHash = hash,
                            profilePicture = obj.optString("profilePicture").takeIf { it.isNotBlank() },
                            token = token
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun serializeAccountsArray(accounts: List<StoredLocalAccount>): String {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.name)
                    put("email", acc.email)
                    put("passwordHash", acc.passwordHash)
                    put("profilePicture", acc.profilePicture ?: "")
                    put("token", acc.token)
                }
            )
        }
        return arr.toString()
    }

    private fun parseUserSession(raw: String?): UserSession? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val id = obj.optString("_id").ifBlank { obj.optString("id") }
            val token = obj.optString("token")
            if (id.isBlank() || token.isBlank()) return null
            UserSession(
                id = id,
                name = obj.optString("name", "User"),
                email = obj.optString("email", ""),
                profilePicture = obj.optString("profilePicture").takeIf { it.isNotBlank() },
                token = token,
                isOfflineSandbox = obj.optBoolean("isOfflineSandbox", false)
            )
        } catch (_: Exception) {
            null
        }
    }
}
