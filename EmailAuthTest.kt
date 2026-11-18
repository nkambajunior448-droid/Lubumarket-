package com.example.auth_email

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class EmailAuthTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun validatorRejectsInvalidEmailsShortPasswordsAndMismatches() {
        assertFalse(EmailAuthValidator.isValidEmail("pas-un-email"))
        assertFalse(EmailAuthValidator.isValidEmail("user@domain"))
        assertTrue(EmailAuthValidator.isValidEmail("Client.Lubum@exemple.cd"))

        assertNotNull(EmailAuthValidator.validateRegistration("invalide", "MotDePasse1", "MotDePasse1"))
        assertEquals(
            "Le mot de passe doit contenir au moins 8 caractères.",
            EmailAuthValidator.validateRegistration("user@lubumarket.cd", "1234567", "1234567")
        )
        assertEquals(
            "La confirmation du mot de passe ne correspond pas.",
            EmailAuthValidator.validateRegistration("user@lubumarket.cd", "Secret123", "AutreSecret123")
        )
        assertNull(
            EmailAuthValidator.validateRegistration("user@lubumarket.cd", "Secret123", "Secret123")
        )
    }

    @Test
    fun validatorExtractsTokenFromVerificationOrResetUrl() {
        val raw = "abcdef123456"
        val url = "http://10.0.2.2:4000/api/auth/reset-password?token=abcdef123456&src=mail"
        assertEquals(raw, EmailAuthValidator.extractTokenFromInput(raw))
        assertEquals(raw, EmailAuthValidator.extractTokenFromInput(url))
    }

    @Test
    fun cookieJarStoresAndEvictsHttpOnlySessionCookie() {
        var nowMs = 1_000_000L
        val jar = SessionCookieJar(clockMs = { nowMs })
        val url = "http://10.0.2.2:4000/api/auth/login".toHttpUrl()

        val jwtCookie = Cookie.Builder()
            .name(SessionCookieJar.SESSION_COOKIE_NAME)
            .value("jwt.token.sample")
            .domain("10.0.2.2")
            .path("/")
            .httpOnly()
            .expiresAt(nowMs + 60_000L)
            .build()

        jar.saveFromResponse(url, listOf(jwtCookie))
        assertTrue(jar.hasCookie("http://10.0.2.2:4000/"))
        assertEquals(1, jar.loadForRequest(url).size)

        // Simuler expiration / déconnexion (clearCookie Max-Age=0)
        val clearCookie = Cookie.Builder()
            .name(SessionCookieJar.SESSION_COOKIE_NAME)
            .value("")
            .domain("10.0.2.2")
            .path("/")
            .expiresAt(nowMs - 1_000L)
            .build()
        jar.saveFromResponse(url, listOf(clearCookie))

        assertFalse(jar.hasCookie("http://10.0.2.2:4000/"))
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun viewModelHandlesRegisterVerifyGenericLoginErrorAndLogout() = runTest {
        val fakeApi = FakeEmailAuthApiService()
        val cookieJar = SessionCookieJar()
        val repo = EmailAuthRepository(
            api = fakeApi,
            cookieJar = cookieJar,
            moshi = Moshi.Builder().build(),
            baseUrl = "http://10.0.2.2:4000/"
        )
        val vm = EmailAuthViewModel(repo)

        // 1. Inscription valide -> bascule vers vérification email
        vm.register("client@lubumarket.cd", "Lubum2025!", "Lubum2025!")
        advanceUntilIdle()
        assertEquals(EmailAuthTab.PENDING_VERIFICATION, vm.uiState.value.activeTab)
        assertEquals("client@lubumarket.cd", vm.uiState.value.pendingVerificationEmail)

        // 2. Tentative de connexion avant vérification -> bloquée (403)
        vm.login("client@lubumarket.cd", "Lubum2025!")
        advanceUntilIdle()
        assertEquals(EmailAuthTab.PENDING_VERIFICATION, vm.uiState.value.activeTab)
        assertNull(vm.uiState.value.currentUser)

        // 3. Vérification email avec jeton -> retour écran connexion
        vm.verifyEmailToken("valid-verify-token")
        advanceUntilIdle()
        assertEquals(EmailAuthTab.LOGIN, vm.uiState.value.activeTab)

        // 4. Mauvais mot de passe -> message générique exact
        vm.login("client@lubumarket.cd", "MauvaisPasse!")
        advanceUntilIdle()
        assertEquals("Email ou mot de passe incorrect", vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.currentUser)

        // 5. Connexion réussie -> session connectée
        vm.login("client@lubumarket.cd", "Lubum2025!")
        advanceUntilIdle()
        assertEquals(EmailAuthTab.ACCOUNT, vm.uiState.value.activeTab)
        assertEquals("client@lubumarket.cd", vm.uiState.value.currentUser?.email)

        // 6. Déconnexion -> retour LOGIN & session nettoyée
        vm.logout()
        advanceUntilIdle()
        assertEquals(EmailAuthTab.LOGIN, vm.uiState.value.activeTab)
        assertNull(vm.uiState.value.currentUser)
    }

    private class FakeEmailAuthApiService : EmailAuthApiService {
        private var registeredEmail: String? = null
        private var storedPassword: String? = null
        private var verified = false

        override suspend fun register(request: RegisterRequest): Response<AuthApiResponse> {
            registeredEmail = request.email
            storedPassword = request.password
            verified = false
            return Response.success(
                201,
                AuthApiResponse(
                    success = true,
                    requiresEmailVerification = true,
                    message = "Lien de confirmation envoyé.",
                    user = EmailAuthUserDto(1L, request.email, isVerified = false, createdAt = "2026-01-01T00:00:00Z")
                )
            )
        }

        override suspend fun verifyEmail(request: VerifyEmailRequest): Response<AuthApiResponse> {
            verified = true
            return Response.success(
                AuthApiResponse(success = true, message = "Email confirmé !")
            )
        }

        override suspend fun resendVerification(request: EmailOnlyRequest): Response<AuthApiResponse> {
            return Response.success(AuthApiResponse(success = true, message = "Lien renvoyé."))
        }

        override suspend fun login(request: LoginRequest): Response<AuthApiResponse> {
            if (request.email != registeredEmail || request.password != storedPassword) {
                return jsonError(401, """{"success":false,"message":"Email ou mot de passe incorrect"}""")
            }
            if (!verified) {
                return jsonError(403, """{"success":false,"requiresEmailVerification":true,"message":"Veuillez confirmer votre adresse email avant de vous connecter."}""")
            }
            return Response.success(
                AuthApiResponse(
                    success = true,
                    message = "Connexion réussie.",
                    user = EmailAuthUserDto(1L, request.email, isVerified = true, createdAt = "2026-01-01T00:00:00Z")
                )
            )
        }

        override suspend fun logout(): Response<AuthApiResponse> {
            return Response.success(AuthApiResponse(success = true, message = "Déconnecté."))
        }

        override suspend fun getCurrentUser(): Response<AuthApiResponse> {
            return Response.success(
                AuthApiResponse(
                    success = true,
                    user = EmailAuthUserDto(1L, registeredEmail ?: "client@lubumarket.cd", verified, "2026-01-01T00:00:00Z")
                )
            )
        }

        override suspend fun getProtectedAccountOverview(): Response<AuthApiResponse> {
            return Response.success(AuthApiResponse(success = true, message = "OK"))
        }

        override suspend fun forgotPassword(request: EmailOnlyRequest): Response<AuthApiResponse> {
            return Response.success(AuthApiResponse(success = true, message = "Lien envoyé."))
        }

        override suspend fun resetPassword(request: ResetPasswordRequest): Response<AuthApiResponse> {
            storedPassword = request.newPassword
            return Response.success(AuthApiResponse(success = true, message = "Réinitialisé."))
        }

        private fun jsonError(code: Int, rawJson: String): Response<AuthApiResponse> {
            return Response.error(code, rawJson.toResponseBody("application/json".toMediaType()))
        }
    }
}
