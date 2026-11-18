package com.example.auth_email

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EmailAuthTab {
    LOGIN,
    REGISTER,
    PENDING_VERIFICATION,
    FORGOT_PASSWORD,
    RESET_PASSWORD,
    ACCOUNT
}

data class EmailAuthUiState(
    val activeTab: EmailAuthTab = EmailAuthTab.LOGIN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingVerificationEmail: String = "",
    val currentUser: EmailAuthUserDto? = null,
    val protectedRouteStatus: String? = null,
    val hasActiveHttpOnlyCookie: Boolean = false
)

class EmailAuthViewModel(
    private val repository: EmailAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EmailAuthUiState(hasActiveHttpOnlyCookie = repository.hasStoredSessionCookie())
    )
    val uiState: StateFlow<EmailAuthUiState> = _uiState.asStateFlow()

    init {
        if (repository.hasStoredSessionCookie()) {
            restoreSession()
        }
    }

    fun switchTab(tab: EmailAuthTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        val validationError = EmailAuthValidator.validateRegistration(email, password, confirmPassword)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError, infoMessage = null) }
            return
        }

        val normalizedEmail = EmailAuthValidator.normalizeEmail(email)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val result = repository.register(normalizedEmail, password, confirmPassword)) {
                is EmailAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeTab = EmailAuthTab.PENDING_VERIFICATION,
                            pendingVerificationEmail = normalizedEmail,
                            infoMessage = result.data.message
                                ?: "Compte créé ! Veuillez confirmer votre adresse email avant de vous connecter."
                        )
                    }
                }
                is EmailAuthResult.VerificationRequired -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeTab = EmailAuthTab.PENDING_VERIFICATION,
                            pendingVerificationEmail = result.email,
                            infoMessage = result.message
                        )
                    }
                }
                is EmailAuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun verifyEmailToken(tokenInput: String) {
        val token = EmailAuthValidator.extractTokenFromInput(tokenInput)
        if (token.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Veuillez coller le jeton ou le lien de confirmation reçu par email.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val result = repository.verifyEmail(token)) {
                is EmailAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeTab = EmailAuthTab.LOGIN,
                            infoMessage = result.data.message
                                ?: "Adresse email confirmée ! Vous pouvez maintenant vous connecter."
                        )
                    }
                }
                is EmailAuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is EmailAuthResult.VerificationRequired -> Unit
            }
        }
    }

    fun resendVerification(email: String) {
        val target = EmailAuthValidator.normalizeEmail(
            email.ifBlank { _uiState.value.pendingVerificationEmail }
        )
        if (!EmailAuthValidator.isValidEmail(target)) {
            _uiState.update { it.copy(errorMessage = "Veuillez saisir une adresse email valide.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val result = repository.resendVerification(target)) {
                is EmailAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingVerificationEmail = target,
                            infoMessage = result.data.message
                                ?: "Un nouveau lien de confirmation a été envoyé."
                        )
                    }
                }
                is EmailAuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is EmailAuthResult.VerificationRequired -> Unit
            }
        }
    }

    fun login(email: String, password: String) {
        val validationError = EmailAuthValidator.validateLogin(email, password)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError, infoMessage = null) }
            return
        }

        val normalizedEmail = EmailAuthValidator.normalizeEmail(email)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val result = repository.login(normalizedEmail, password)) {
                is EmailAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeTab = EmailAuthTab.ACCOUNT,
                            currentUser = result.data.user,
                            hasActiveHttpOnlyCookie = repository.hasStoredSessionCookie(),
                            protectedRouteStatus = "Session JWT active via cookie httpOnly",
                            infoMessage = result.data.message ?: "Connexion réussie."
                        )
                    }
                }
                is EmailAuthResult.VerificationRequired -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeTab = EmailAuthTab.PENDING_VERIFICATION,
                            pendingVerificationEmail = result.email,
                            errorMessage = result.message
                        )
                    }
                }
                is EmailAuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun restoreSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getCurrentUser()) {
                is EmailAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeTab = EmailAuthTab.ACCOUNT,
                            currentUser = result.data.user,
                            hasActiveHttpOnlyCookie = repository.hasStoredSessionCookie(),
                            protectedRouteStatus = "Cookie httpOnly validé par GET /api/auth/me"
                        )
                    }
                }
                is EmailAuthResult.Error, is EmailAuthResult.VerificationRequired -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = null,
                            hasActiveHttpOnlyCookie = repository.hasStoredSessionCookie()
                        )
                    }
                }
            }
        }
    }

    fun checkProtectedEndpoint() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val result = repository.getProtectedOverview()) {
                is EmailAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = result.data.user ?: it.currentUser,
                            protectedRouteStatus = result.data.message ?: "Route protégée accessible.",
                            infoMessage = "Vérification réussie via cookie httpOnly JWT."
                        )
                    }
                }
                is EmailAuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            activeTab = if (result.httpCode == 401) EmailAuthTab.LOGIN else it.activeTab,
                            currentUser = if (result.httpCode == 401) null else it.currentUser
                        )
                    }
                }
                is EmailAuthResult.VerificationRequired -> Unit
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = repository.logout()
            val message = when (result) {
                is EmailAuthResult.Success -> result.data.message ?: "Déconnexion réussie."
                is EmailAuthResult.Error -> result.message
                is EmailAuthResult.VerificationRequired -> "Déconnexion réussie."
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    activeTab = EmailAuthTab.LOGIN,
                    currentUser = null,
                    protectedRouteStatus = null,
                    hasActiveHttpOnlyCookie = false,
                    infoMessage = message
                )
            }
        }
    }

    fun forgotPassword(email: String) {
        val cleaned = EmailAuthValidator.normalizeEmail(email)
        if (!EmailAuthValidator.isValidEmail(cleaned)) {
            _uiState.update { it.copy(errorMessage = "Veuillez saisir une adresse email valide.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val result = repository.forgotPassword(cleaned)) {
                is EmailAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeTab = EmailAuthTab.RESET_PASSWORD,
                            infoMessage = result.data.message
                                ?: "Si cette adresse existe, un lien de réinitialisation (15 min) a été envoyé."
                        )
                    }
                }
                is EmailAuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is EmailAuthResult.VerificationRequired -> Unit
            }
        }
    }

    fun resetPassword(tokenInput: String, newPassword: String, confirmPassword: String) {
        val validationError = EmailAuthValidator.validateResetPassword(tokenInput, newPassword, confirmPassword)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError, infoMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val result = repository.resetPassword(tokenInput, newPassword, confirmPassword)) {
                is EmailAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeTab = EmailAuthTab.LOGIN,
                            infoMessage = result.data.message
                                ?: "Mot de passe réinitialisé avec succès ! Connectez-vous."
                        )
                    }
                }
                is EmailAuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is EmailAuthResult.VerificationRequired -> Unit
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EmailAuthViewModel(EmailAuthRepository.createDefault(context)) as T
        }
    }
}
