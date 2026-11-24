package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Companion Google Sign-In helper via Jetpack Credential Manager for authenticating
 * Firestore cloud metadata sync. Preserves the existing Render/MongoDB login untouched.
 * Strictly uses one credential option per GetCredentialRequest for API 36 safety.
 */
object FirebaseAuthManager {

    private const val TAG = "Auth"

    fun currentUser(): FirebaseUser? = runCatching { Firebase.auth.currentUser }.getOrNull()

    /**
     * Silent startup check using GetGoogleIdOption (one option per request).
     */
    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onUnauthenticated: () -> Unit = {}
    ) {
        val existing = currentUser()
        if (existing != null) {
            onAuthSuccess(existing)
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }
        if (clientId.isBlank()) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val result = Firebase.auth
                        .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                        .await()
                    val user = result.user
                    if (user != null) onAuthSuccess(user) else onUnauthenticated()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    /**
     * Interactive "Sign in with Google" button flow using GetSignInWithGoogleOption (one option per request).
     */
    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            onAuthError("Sign-in requires an active screen context.")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val result = Firebase.auth
                        .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                        .await()
                    val user = result.user
                    if (user != null) {
                        onAuthSuccess(user)
                    } else {
                        onAuthError("Could not complete Google Sign-In.")
                    }
                } else {
                    onAuthError("Unexpected credential type returned.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    /**
     * Safe sign-out wrapping credential clearing & Firebase sign-out in NonCancellable.
     * Caller should cancel active Firestore listeners before invoking onSignOutComplete.
     */
    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onBeforeSignOut: () -> Unit = {},
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                onBeforeSignOut()
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    runCatching { Firebase.auth.signOut() }
                    onSignOutComplete()
                }
            }
        }
    }
}
