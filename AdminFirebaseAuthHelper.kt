package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.data.MarketplaceFirestoreRepository
import com.example.model.normalizeCongoPhoneE164
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

object AdminFirebaseAuthHelper {
    private const val TAG = "AdminAuth"
    private const val PREFS_NAME = "lubumarket_admin_auth"
    private const val KEY_PENDING_EMAIL = "pending_admin_email_link"
    private const val EMAIL_LINK_CONTINUE_URL = "https://gen-lang-client-0555201904.firebaseapp.com/admin-auth"

    fun isAuthorizedOwnerEmail(email: String): Boolean {
        return email.trim().equals(MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL, ignoreCase = true)
    }

    fun savePendingEmailForLink(context: Context, email: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_EMAIL, email.trim())
            .apply()
    }

    fun getPendingEmailForLink(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_EMAIL, null)
    }

    fun clearPendingEmailForLink(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_EMAIL)
            .apply()
    }

    fun sendPhoneVerificationCode(
        activity: Activity,
        rawPhone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        auth: FirebaseAuth = Firebase.auth,
        onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val e164Phone = normalizeCongoPhoneE164(rawPhone)
        if (!e164Phone.startsWith("+") || e164Phone.length < 10) {
            onFailed("Numéro de téléphone invalide. Exemple : +243 997 123 456.")
            return
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                onAutoVerified(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "Phone verification failed: ${e.message}", e)
                onFailed(translateFirebaseAuthError(e))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId, token)
            }
        }

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164Phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (resendToken != null) {
            builder.setForceResendingToken(resendToken)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    suspend fun verifyPhoneCredentialOrCode(
        verificationId: String? = null,
        smsCode: String? = null,
        directCredential: PhoneAuthCredential? = null,
        linkToExistingUser: Boolean = false,
        auth: FirebaseAuth = Firebase.auth
    ): Result<FirebaseUser> {
        return try {
            val credential = directCredential ?: run {
                val vid = verificationId?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(IllegalStateException("Session SMS expirée. Veuillez renvoyer le code."))
                val cleanCode = smsCode?.trim().orEmpty()
                if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
                    return Result.failure(IllegalArgumentException("Veuillez saisir le code exact à 6 chiffres reçu par SMS."))
                }
                PhoneAuthProvider.getCredential(vid, cleanCode)
            }

            val currentUser = auth.currentUser
            val resultUser = if (linkToExistingUser && currentUser != null) {
                try {
                    currentUser.linkWithCredential(credential).await().user ?: currentUser
                } catch (_: FirebaseAuthUserCollisionException) {
                    // Phone is already linked or exists on a Firebase user; sign in with phone credential
                    auth.signInWithCredential(credential).await().user
                        ?: throw IllegalStateException("Impossible de valider ce numéro.")
                }
            } else {
                auth.signInWithCredential(credential).await().user
                    ?: throw IllegalStateException("Authentification SMS échouée.")
            }
            Result.success(resultUser)
        } catch (e: Exception) {
            Log.w(TAG, "OTP verification failed: ${e.message}", e)
            Result.failure(IllegalStateException(translateFirebaseAuthError(e)))
        }
    }

    suspend fun sendAdminEmailSignInLink(
        context: Context,
        email: String,
        auth: FirebaseAuth = Firebase.auth
    ): Result<Unit> {
        val cleanEmail = email.trim()
        if (!isAuthorizedOwnerEmail(cleanEmail)) {
            return Result.failure(
                SecurityException("Accès refusé : seul l'e-mail du propriétaire principal (${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL}) est autorisé.")
            )
        }
        return try {
            val settings = ActionCodeSettings.newBuilder()
                .setUrl(EMAIL_LINK_CONTINUE_URL)
                .setHandleCodeInApp(true)
                .setAndroidPackageName(context.packageName, true, null)
                .build()
            auth.sendSignInLinkToEmail(cleanEmail, settings).await()
            savePendingEmailForLink(context, cleanEmail)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "sendSignInLinkToEmail failed: ${e.message}", e)
            Result.failure(IllegalStateException(translateFirebaseAuthError(e)))
        }
    }

    suspend fun completeEmailLinkSignIn(
        context: Context,
        email: String,
        emailLink: String,
        auth: FirebaseAuth = Firebase.auth
    ): Result<FirebaseUser> {
        val cleanEmail = email.trim()
        if (!isAuthorizedOwnerEmail(cleanEmail)) {
            return Result.failure(
                SecurityException("Accès refusé : adresse e-mail non autorisée.")
            )
        }
        if (!auth.isSignInWithEmailLink(emailLink)) {
            return Result.failure(IllegalArgumentException("Lien de vérification invalide ou expiré."))
        }
        return try {
            val result = auth.signInWithEmailLink(cleanEmail, emailLink).await()
            clearPendingEmailForLink(context)
            val user = result.user ?: throw IllegalStateException("Session administrateur introuvable.")
            Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "signInWithEmailLink failed: ${e.message}", e)
            Result.failure(IllegalStateException(translateFirebaseAuthError(e)))
        }
    }

    fun translateFirebaseAuthError(e: Exception): String {
        val code = (e as? FirebaseAuthException)?.errorCode
        val msg = e.localizedMessage ?: ""
        return when {
            code == "ERROR_OPERATION_NOT_ALLOWED" || msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ->
                "L'envoi SMS / Lien e-mail doit être activé dans la Console Firebase (Authentication > Méthodes de connexion). Vous pouvez aussi utiliser la vérification Google ci-dessous."
            code == "ERROR_INVALID_PHONE_NUMBER" ->
                "Numéro de téléphone invalide. Utilisez le format +243..."
            code == "ERROR_INVALID_VERIFICATION_CODE" ->
                "Code à 6 chiffres incorrect. Vérifiez le SMS reçu ou renvoyez un code."
            code == "ERROR_SESSION_EXPIRED" ->
                "Le code à usage unique a expiré. Cliquez sur « Renvoyer le code »."
            e is FirebaseAuthInvalidCredentialsException ->
                "Identifiant ou code de vérification invalide."
            e is FirebaseTooManyRequestsException || code == "ERROR_TOO_MANY_REQUESTS" ->
                "Trop de tentatives. Patientez quelques instants avant de renvoyer un code."
            msg.contains("reCAPTCHA", ignoreCase = true) || msg.contains("Play Integrity", ignoreCase = true) ->
                "Vérification de sécurité appareil requise dans la Console Firebase (SHA-1/SHA-256) pour l'envoi SMS réel."
            else -> e.localizedMessage ?: "Erreur d'authentification Firebase."
        }
    }
}
