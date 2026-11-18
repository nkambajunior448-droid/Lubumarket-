package com.example.auth_email

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

interface EmailAuthApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthApiResponse>

    @POST("api/auth/verify-email")
    suspend fun verifyEmail(@Body request: VerifyEmailRequest): Response<AuthApiResponse>

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(@Body request: EmailOnlyRequest): Response<AuthApiResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthApiResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<AuthApiResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<AuthApiResponse>

    @GET("api/protected/account-overview")
    suspend fun getProtectedAccountOverview(): Response<AuthApiResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: EmailOnlyRequest): Response<AuthApiResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<AuthApiResponse>
}

interface CookiePersistence {
    fun saveCookies(host: String, encodedCookies: Set<String>)
    fun loadCookies(host: String): Set<String>
    fun clearAll()
}

class SharedPreferencesCookiePersistence(context: Context) : CookiePersistence {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveCookies(host: String, encodedCookies: Set<String>) {
        prefs.edit().putStringSet(KEY_PREFIX + host, encodedCookies).apply()
    }

    override fun loadCookies(host: String): Set<String> {
        return prefs.getStringSet(KEY_PREFIX + host, emptySet())?.toSet() ?: emptySet()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "lubumarket_email_http_cookies"
        const val KEY_PREFIX = "cookies_"
    }
}

/**
 * Gère les cookies httpOnly (dont le JWT `lubumarket_session`) sans exposer le jeton dans le code applicatif.
 */
class SessionCookieJar(
    private val persistence: CookiePersistence? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : CookieJar {
    private val lock = Any()
    private val memoryStore = mutableMapOf<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val host = url.host
            val hostCookies = loadOrRestoreHostMap(host)
            val now = clockMs()

            for (cookie in cookies) {
                val key = "${cookie.name}|${cookie.path}"
                if (cookie.expiresAt <= now || cookie.value.isEmpty()) {
                    hostCookies.remove(key)
                } else {
                    hostCookies[key] = cookie
                }
            }
            persistHostMap(url, hostCookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val host = url.host
            val hostCookies = loadOrRestoreHostMap(host)
            val now = clockMs()

            val expiredKeys = hostCookies.filterValues { it.expiresAt <= now }.keys.toList()
            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { hostCookies.remove(it) }
                persistHostMap(url, hostCookies)
            }

            return hostCookies.values.filter { it.matches(url) }
        }
    }

    fun hasCookie(baseUrl: String, cookieName: String = SESSION_COOKIE_NAME): Boolean {
        val parsedUrl = baseUrl.toHttpUrlOrNull() ?: return false
        return loadForRequest(parsedUrl).any { it.name == cookieName }
    }

    fun clear() {
        synchronized(lock) {
            memoryStore.clear()
            persistence?.clearAll()
        }
    }

    private fun loadOrRestoreHostMap(host: String): MutableMap<String, Cookie> {
        memoryStore[host]?.let { return it }
        val restored = mutableMapOf<String, Cookie>()
        val dummyUrl = "http://$host/".toHttpUrlOrNull()
        if (persistence != null && dummyUrl != null) {
            for (raw in persistence.loadCookies(host)) {
                val parsed = Cookie.parse(dummyUrl, raw)
                if (parsed != null && parsed.expiresAt > clockMs()) {
                    restored["${parsed.name}|${parsed.path}"] = parsed
                }
            }
        }
        memoryStore[host] = restored
        return restored
    }

    private fun persistHostMap(url: HttpUrl, hostCookies: Map<String, Cookie>) {
        if (persistence == null) return
        val encoded = hostCookies.values.map { it.toString() }.toSet()
        persistence.saveCookies(url.host, encoded)
    }

    companion object {
        const val SESSION_COOKIE_NAME = "lubumarket_session"
    }
}

sealed class EmailAuthResult<out T> {
    data class Success<T>(val data: T) : EmailAuthResult<T>()
    data class VerificationRequired(val message: String, val email: String) : EmailAuthResult<Nothing>()
    data class Error(val message: String, val httpCode: Int? = null) : EmailAuthResult<Nothing>()
}

class EmailAuthRepository(
    private val api: EmailAuthApiService,
    private val cookieJar: SessionCookieJar,
    private val moshi: Moshi = Moshi.Builder().build(),
    private val baseUrl: String = BuildConfig.AUTH_SERVER_URL
) {
    private val responseAdapter by lazy { moshi.adapter(AuthApiResponse::class.java) }

    suspend fun register(
        email: String,
        password: String,
        confirmPassword: String
    ): EmailAuthResult<AuthApiResponse> {
        val cleanedEmail = EmailAuthValidator.normalizeEmail(email)
        return safeCall {
            val response = api.register(RegisterRequest(cleanedEmail, password, confirmPassword))
            handleResponse(response, "Impossible de créer le compte.")
        }
    }

    suspend fun verifyEmail(tokenInput: String): EmailAuthResult<AuthApiResponse> {
        val token = EmailAuthValidator.extractTokenFromInput(tokenInput)
        return safeCall {
            val response = api.verifyEmail(VerifyEmailRequest(token))
            handleResponse(response, "Lien de vérification invalide ou expiré.")
        }
    }

    suspend fun resendVerification(email: String): EmailAuthResult<AuthApiResponse> {
        val cleanedEmail = EmailAuthValidator.normalizeEmail(email)
        return safeCall {
            val response = api.resendVerification(EmailOnlyRequest(cleanedEmail))
            handleResponse(response, "Impossible de renvoyer l'email de confirmation.")
        }
    }

    suspend fun login(email: String, password: String): EmailAuthResult<AuthApiResponse> {
        val cleanedEmail = EmailAuthValidator.normalizeEmail(email)
        return safeCall {
            val response = api.login(LoginRequest(cleanedEmail, password))
            if (response.code() == 403) {
                val parsed = parseErrorBody(response.errorBody()?.string())
                EmailAuthResult.VerificationRequired(
                    message = parsed?.message
                        ?: "Veuillez confirmer votre adresse email avant de vous connecter.",
                    email = cleanedEmail
                )
            } else {
                handleResponse(response, "Email ou mot de passe incorrect")
            }
        }
    }

    suspend fun getCurrentUser(): EmailAuthResult<AuthApiResponse> {
        return safeCall {
            val response = api.getCurrentUser()
            handleResponse(response, "Session expirée ou non authentifiée.")
        }
    }

    suspend fun getProtectedOverview(): EmailAuthResult<AuthApiResponse> {
        return safeCall {
            val response = api.getProtectedAccountOverview()
            handleResponse(response, "Accès refusé à la ressource protégée.")
        }
    }

    suspend fun logout(): EmailAuthResult<AuthApiResponse> {
        return safeCall {
            val response = api.logout()
            cookieJar.clear()
            handleResponse(response, "Déconnexion effectuée.")
        }
    }

    suspend fun forgotPassword(email: String): EmailAuthResult<AuthApiResponse> {
        val cleanedEmail = EmailAuthValidator.normalizeEmail(email)
        return safeCall {
            val response = api.forgotPassword(EmailOnlyRequest(cleanedEmail))
            handleResponse(response, "Erreur lors de l'envoi du lien de réinitialisation.")
        }
    }

    suspend fun resetPassword(
        tokenInput: String,
        newPassword: String,
        confirmPassword: String
    ): EmailAuthResult<AuthApiResponse> {
        val token = EmailAuthValidator.extractTokenFromInput(tokenInput)
        return safeCall {
            val response = api.resetPassword(
                ResetPasswordRequest(
                    token = token,
                    newPassword = newPassword,
                    confirmPassword = confirmPassword
                )
            )
            handleResponse(response, "Impossible de réinitialiser le mot de passe.")
        }
    }

    fun hasStoredSessionCookie(): Boolean = cookieJar.hasCookie(baseUrl)

    private fun handleResponse(
        response: Response<AuthApiResponse>,
        fallbackError: String
    ): EmailAuthResult<AuthApiResponse> {
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            EmailAuthResult.Success(body)
        } else {
            val parsed = parseErrorBody(response.errorBody()?.string())
            EmailAuthResult.Error(
                message = parsed?.message ?: fallbackError,
                httpCode = response.code()
            )
        }
    }

    private fun parseErrorBody(raw: String?): AuthApiResponse? {
        if (raw.isNullOrBlank()) return null
        return try {
            responseAdapter.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> EmailAuthResult<T>): EmailAuthResult<T> {
        return try {
            block()
        } catch (_: IOException) {
            EmailAuthResult.Error(
                "Serveur d'authentification inaccessible ($baseUrl). Démarrez le backend Node.js."
            )
        } catch (e: Exception) {
            EmailAuthResult.Error(e.localizedMessage ?: "Erreur réseau inattendue.")
        }
    }

    companion object {
        fun createDefault(
            context: Context,
            baseUrl: String = BuildConfig.AUTH_SERVER_URL
        ): EmailAuthRepository {
            val moshi = Moshi.Builder().build()
            val cookieJar = SessionCookieJar(SharedPreferencesCookiePersistence(context))
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            val okHttp = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBase)
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return EmailAuthRepository(
                api = retrofit.create(EmailAuthApiService::class.java),
                cookieJar = cookieJar,
                moshi = moshi,
                baseUrl = normalizedBase
            )
        }
    }
}
