package com.localqbank.library

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google-account authentication for Gemini access.
 *
 * The flow deliberately separates:
 *  1) Credential Manager account selection/authentication,
 *  2) Google ID-token extraction, and
 *  3) Firebase credential exchange.
 *
 * A Google account that requires re-authentication must use the explicit
 * Sign in with Google option; the bottom-sheet flow intentionally excludes
 * such accounts. We therefore use the explicit flow first for this user-
 * initiated G button, with the bottom-sheet flow as a recovery path.
 */
class GoogleAccountManager(private val activity: Activity) {
    private val context: Context get() = activity.applicationContext
    private val credentials by lazy { CredentialManager.create(activity) }

    @Volatile var lastFailure: Diagnostic? = null
        private set

    data class Diagnostic(
        val stage: String,
        val type: String,
        val message: String,
        val playServicesStatus: Int,
        val playServicesVersion: Long?
    )

    fun auth(): FirebaseAuth? = runCatching {
        if (FirebaseAppAvailability.ensureInitialized(context) != null) FirebaseAuth.getInstance() else null
    }.getOrNull()

    fun currentEmail(): String? = auth()?.currentUser?.email

    suspend fun signIn(): Result<String> = withContext(Dispatchers.Main) {
        clearFailure()
        val firebaseAuth = auth() ?: return@withContext failure(
            stage = "FIREBASE_INIT",
            error = IllegalStateException("Firebase is not configured. Add google-services.json and enable Google sign-in.")
        )
        val webClientId = webClientId()
        if (webClientId.isBlank()) return@withContext failure(
            stage = "OAUTH_CLIENT",
            error = IllegalStateException("Missing default_web_client_id. Configure Firebase Google sign-in.")
        )

        // This G button is explicitly user initiated. Google documents the
        // explicit Sign in with Google option as the correct path when an
        // account needs re-authentication.
        val explicit = requestExplicitGoogleButton(webClientId, firebaseAuth)
        if (explicit.isSuccess) return@withContext explicit

        val firstError = explicit.exceptionOrNull()
        if (firstError is GetCredentialCancellationException) {
            // Credential Manager documents cancellation as both user dismissal and
            // technical authorization/configuration failure. Its message is not a
            // stable contract, so never infer "reauthentication required" from text.
            // Preserve the original exception as the authoritative diagnostic.
            return@withContext failure("GOOGLE_EXPLICIT_CANCELLATION", firstError)
        }
        if (firstError is GetCredentialInterruptedException) {
            // Interrupted is retryable, but never loop automatically.
            return@withContext failure("CREDENTIAL_INTERRUPTED", firstError)
        }

        // If the explicit button flow could not find/complete a credential,
        // recover with the standard Credential Manager account sheet.
        val fallback = requestBottomSheet(webClientId, firebaseAuth, authorizedOnly = true, autoSelect = true)
        if (fallback.isSuccess) return@withContext fallback
        if (fallback.exceptionOrNull() is NoCredentialException) {
            return@withContext requestBottomSheet(webClientId, firebaseAuth, authorizedOnly = false, autoSelect = false)
        }
        fallback
    }

    private suspend fun requestExplicitGoogleButton(webClientId: String, firebaseAuth: FirebaseAuth): Result<String> {
        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId)
                .setNonce(secureNonce())
                .build()
            requestCredential(option, firebaseAuth, "GOOGLE_EXPLICIT")
        } catch (t: Throwable) {
            failure("GOOGLE_EXPLICIT", t)
        }
    }

    private suspend fun requestBottomSheet(
        webClientId: String,
        firebaseAuth: FirebaseAuth,
        authorizedOnly: Boolean,
        autoSelect: Boolean
    ): Result<String> {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(authorizedOnly)
                .setAutoSelectEnabled(autoSelect)
                .setNonce(secureNonce())
                .build()
            requestCredential(option, firebaseAuth, "GOOGLE_BOTTOM_SHEET")
        } catch (t: Throwable) {
            failure("GOOGLE_BOTTOM_SHEET", t)
        }
    }

    private suspend fun requestCredential(
        option: androidx.credentials.CredentialOption,
        firebaseAuth: FirebaseAuth,
        stage: String
    ): Result<String> {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val mutableContext = MutableContextWrapper(activity)
        val result = credentials.getCredential(request = request, context = mutableContext)
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return failure(stage + "_CREDENTIAL", IllegalStateException("Google returned an unsupported credential type: ${credential.type}"))
        }
        val google = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            return failure(stage + "_TOKEN_PARSE", e)
        }
        return try {
            val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
            val signedIn = firebaseAuth.signInWithCredential(firebaseCredential).awaitTask()
            clearFailure()
            Result.success(signedIn.user?.email ?: "Google account")
        } catch (t: Throwable) {
            failure("FIREBASE_CREDENTIAL_EXCHANGE", t)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.Main) {
        runCatching { auth()?.signOut() }
        runCatching { credentials.clearCredentialState(ClearCredentialStateRequest()) }
        clearFailure()
    }

    fun firebaseReady(): Boolean = auth() != null && webClientId().isNotBlank()

    fun webClientId(): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return id.takeIf { it != 0 }?.let(context::getString)?.trim().orEmpty()
    }

    fun playServicesStatus(): Int = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)

    fun playServicesVersion(): Long? = runCatching {
        context.packageManager.getPackageInfo("com.google.android.gms", 0).longVersionCode
    }.getOrNull()

    fun friendlyError(t: Throwable): String {
        val diagnostic = lastFailure
        val message = t.message.orEmpty()
        val type = t::class.java.simpleName
        val stage = diagnostic?.stage ?: "UNKNOWN"
        return when {
            t is GetCredentialCancellationException ->
                "Google did not complete the authorization request. This can mean the Google UI was cancelled or that Google/Play services rejected the request. The app will not guess the cause from Google's message."
            t is GetCredentialInterruptedException ->
                "Google sign-in was interrupted. Try the sign-in button again."
            t is NoCredentialException ->
                "No usable Google credential was returned. The account may not be available to Credential Manager, may require reauthentication, or may not be authorized for this app."
            message.contains("DEVELOPER_ERROR", true) || Regex("(?:status|error)[^0-9]{0,12}10\\b", RegexOption.IGNORE_CASE).containsMatchIn(message) ->
                "Google sign-in configuration mismatch. Verify the installed APK signing SHA-1, package com.localqbank.library, and Firebase OAuth clients."
            message.contains("12500", true) || message.contains("SIGN_IN_FAILED", true) ->
                "Google sign-in failed. Verify Google Sign-In is enabled in Firebase Authentication and the OAuth clients are present."
            message.contains("AppCheck", true) || message.contains("app check", true) ->
                "Firebase App Check rejected this app instance. Check the App Check provider, registered SHA-256, and enforcement settings."
            else -> message.ifBlank { "Google sign-in could not be completed." }
        } + "\n\nDiagnostic: stage=$stage; type=$type; message=${message.ifBlank { "<empty>" }}"
    }

    fun diagnosticSummary(): String {
        val d = lastFailure ?: return "No Google sign-in failure recorded."
        return "stage=${d.stage}; type=${d.type}; message=${d.message.ifBlank { "<empty>" }}; playServicesStatus=${d.playServicesStatus}; playServicesVersion=${d.playServicesVersion ?: "unknown"}"
    }

    private fun clearFailure() { lastFailure = null }

    private fun failure(stage: String, error: Throwable): Result<String> {
        lastFailure = Diagnostic(
            stage = stage,
            type = error::class.java.name,
            message = error.message.orEmpty(),
            playServicesStatus = playServicesStatus(),
            playServicesVersion = playServicesVersion()
        )
        return Result.failure(error)
    }

    private fun secureNonce(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
