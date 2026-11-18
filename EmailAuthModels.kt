package com.example.auth_email

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EmailAuthUserDto(
    val id: Long,
    val email: String,
    val isVerified: Boolean = false,
    val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val password: String,
    val confirmPassword: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class EmailOnlyRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class VerifyEmailRequest(
    val token: String
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
    val confirmPassword: String
)

@JsonClass(generateAdapter = true)
data class AuthApiResponse(
    val success: Boolean = false,
    val message: String? = null,
    val requiresEmailVerification: Boolean? = null,
    val user: EmailAuthUserDto? = null
)

object EmailAuthValidator {
    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    const val MIN_PASSWORD_LENGTH = 8

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun isValidEmail(raw: String): Boolean {
        val cleaned = normalizeEmail(raw)
        return cleaned.isNotEmpty() && EMAIL_PATTERN.matches(cleaned)
    }

    fun hasMinLength(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    fun validateRegistration(
        email: String,
        password: String,
        confirmPassword: String
    ): String? {
        if (!isValidEmail(email)) {
            return "Veuillez saisir une adresse email valide."
        }
        if (!hasMinLength(password)) {
            return "Le mot de passe doit contenir au moins 8 caractères."
        }
        if (password != confirmPassword) {
            return "La confirmation du mot de passe ne correspond pas."
        }
        return null
    }

    fun validateLogin(email: String, password: String): String? {
        if (!isValidEmail(email) || password.isEmpty()) {
            return "Email ou mot de passe incorrect"
        }
        return null
    }

    fun validateResetPassword(
        token: String,
        newPassword: String,
        confirmPassword: String
    ): String? {
        if (extractTokenFromInput(token).isBlank()) {
            return "Veuillez saisir le jeton ou le lien de réinitialisation reçu par email."
        }
        if (!hasMinLength(newPassword)) {
            return "Le nouveau mot de passe doit contenir au moins 8 caractères."
        }
        if (newPassword != confirmPassword) {
            return "La confirmation du mot de passe ne correspond pas."
        }
        return null
    }

    fun extractTokenFromInput(rawInput: String): String {
        val trimmed = rawInput.trim()
        val marker = "token="
        val idx = trimmed.indexOf(marker)
        return if (idx >= 0) {
            trimmed.substring(idx + marker.length).substringBefore('&').trim()
        } else {
            trimmed
        }
    }
}
