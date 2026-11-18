package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.auth.AdminFirebaseAuthHelper
import com.example.data.MarketplaceFirestoreRepository
import com.example.model.AdminAuthMethod
import com.example.model.normalizeCongoPhoneE164
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AdminAuthChannel(val label: String) {
    GMAIL("Par Gmail"),
    PHONE("Par Téléphone (SMS)")
}

@Composable
fun AdminSignUpScreen(
    adminAuthError: String?,
    onBackToHome: () -> Unit,
    onGoToAdminSignIn: () -> Unit,
    onCompleteSuperAdminRegistration: (
        fullName: String,
        email: String,
        phone: String?,
        authMethod: AdminAuthMethod,
        targetAdminUid: String?,
        onError: (String) -> Unit,
        onSuccess: () -> Unit
    ) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var selectedChannel by remember { mutableStateOf(AdminAuthChannel.GMAIL) }
    var fullName by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf(MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL) }
    var phoneInput by remember { mutableStateOf("+243") }
    var otpCode by remember { mutableStateOf("") }

    var isGmailVerified by remember {
        val cur = Firebase.auth.currentUser
        mutableStateOf(
            cur != null &&
                cur.email?.equals(MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL, ignoreCase = true) == true &&
                cur.isEmailVerified
        )
    }
    var verifiedPhoneUid by remember { mutableStateOf<String?>(null) }
    var isPhoneOtpVerified by remember { mutableStateOf(false) }

    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var smsSent by remember { mutableStateOf(false) }
    var emailLinkSent by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var infoBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds -= 1
        }
    }

    val displayedError = localError ?: adminAuthError

    fun triggerSendSmsCode(isResend: Boolean) {
        if (activity == null) {
            localError = "Activité Android introuvable pour la vérification SMS."
            return
        }
        val cleanPhone = normalizeCongoPhoneE164(phoneInput)
        if (cleanPhone.length < 10) {
            localError = "Veuillez saisir un numéro congolais valide (+243...)."
            return
        }
        localError = null
        infoBanner = null
        isLoading = true
        AdminFirebaseAuthHelper.sendPhoneVerificationCode(
            activity = activity,
            rawPhone = cleanPhone,
            resendToken = if (isResend) resendToken else null,
            onCodeSent = { vid, token ->
                isLoading = false
                verificationId = vid
                resendToken = token
                smsSent = true
                countdownSeconds = 60
                infoBanner = "Code SMS à 6 chiffres envoyé au $cleanPhone."
            },
            onAutoVerified = { credential ->
                scope.launch {
                    val res = AdminFirebaseAuthHelper.verifyPhoneCredentialOrCode(
                        directCredential = credential,
                        linkToExistingUser = isGmailVerified
                    )
                    isLoading = false
                    res.fold(
                        onSuccess = { fbUser ->
                            isPhoneOtpVerified = true
                            if (!isGmailVerified) verifiedPhoneUid = fbUser.uid
                            infoBanner = "Numéro $cleanPhone vérifié automatiquement par Firebase !"
                        },
                        onFailure = { err ->
                            localError = err.localizedMessage
                        }
                    )
                }
            },
            onFailed = { errMsg ->
                isLoading = false
                localError = errMsg
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag("admin_signup_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackToHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accueil")
                }
                TextButton(onClick = onGoToAdminSignIn) {
                    Text("Déjà configuré ? Connexion", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LubumRed)
                }
            }
        }

        // Executive Blue & Red Hero Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(LubumBlueDark, LubumBlue, LubumRed)))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CRÉER MON COMPTE ADMINISTRATEUR",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Super Administrateur LUBUMARKET • Validation réelle Gmail & SMS",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                    }
                }
            }
        }

        // Channel Selector
        item {
            AdminChannelSelector(
                selected = selectedChannel,
                onSelect = {
                    selectedChannel = it
                    localError = null
                    infoBanner = null
                }
            )
        }

        if (!displayedError.isNullOrBlank()) {
            item {
                AdminErrorCard(displayedError)
            }
        }

        if (!infoBanner.isNullOrBlank()) {
            item {
                AdminInfoCard(infoBanner!!)
            }
        }

        // Owner Identity Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Identité du Propriétaire Principal",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = LubumBlueDark
                    )

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Nom complet du Super Administrateur *") },
                        placeholder = { Text("ex: Junior Nkamba") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = LubumBlue) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_signup_name_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Adresse Gmail autorisée du propriétaire *") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = LubumBlue) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_signup_email_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Numéro de téléphone (+243...) *") },
                        placeholder = { Text("+243 997 123 456") },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = LubumRed) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_signup_phone_input"),
                        singleLine = true
                    )
                }
            }
        }

        // Verification Card based on chosen method
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (selectedChannel == AdminAuthChannel.GMAIL)
                            "2. Vérification Gmail & Double Facteur SMS"
                        else
                            "2. Code SMS à 6 chiffres & Autorisation Propriétaire",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = LubumBlueDark
                    )

                    if (selectedChannel == AdminAuthChannel.PHONE) {
                        // Phone SMS first, then owner Gmail confirmation
                        if (!isPhoneOtpVerified) {
                            Button(
                                onClick = { triggerSendSmsCode(isResend = false) },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("admin_signup_send_sms_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (smsSent) "Code SMS envoyé" else "Envoyer le code à 6 chiffres par SMS",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (smsSent) {
                                OtpSixDigitSection(
                                    otpCode = otpCode,
                                    onOtpChange = { otpCode = it },
                                    countdownSeconds = countdownSeconds,
                                    isLoading = isLoading,
                                    inputTestTag = "admin_signup_otp_input",
                                    resendTestTag = "admin_signup_resend_btn",
                                    confirmButtonText = "Confirmer le code SMS à 6 chiffres",
                                    confirmButtonColor = LubumRed,
                                    onResendClick = { triggerSendSmsCode(isResend = true) },
                                    onConfirmClick = {
                                        localError = null
                                        isLoading = true
                                        scope.launch {
                                            val res = AdminFirebaseAuthHelper.verifyPhoneCredentialOrCode(
                                                verificationId = verificationId,
                                                smsCode = otpCode,
                                                linkToExistingUser = false
                                            )
                                            isLoading = false
                                            res.fold(
                                                onSuccess = { fbUser ->
                                                    verifiedPhoneUid = fbUser.uid
                                                    isPhoneOtpVerified = true
                                                    infoBanner = "Téléphone vérifié par code SMS ! Confirmez maintenant l'autorisation du propriétaire Gmail."
                                                },
                                                onFailure = { err -> localError = err.localizedMessage }
                                            )
                                        }
                                    }
                                )
                            }
                        } else {
                            VerifiedStatusRow(
                                label = "Téléphone vérifié : ${normalizeCongoPhoneE164(phoneInput)}"
                            )
                        }

                        HorizontalDivider()

                        if (!isGmailVerified) {
                            Text(
                                text = "Sécurité : confirmez l'adresse propriétaire (${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL}) pour autoriser ce numéro comme Super Administrateur.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Button(
                                onClick = {
                                    if (!AdminFirebaseAuthHelper.isAuthorizedOwnerEmail(emailInput)) {
                                        localError = "Seul l'e-mail propriétaire ${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL} peut autoriser le premier Super Administrateur."
                                        return@Button
                                    }
                                    localError = null
                                    isLoading = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            val cur = Firebase.auth.currentUser
                                            if (cur != null && cur.email?.equals(MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL, ignoreCase = true) == true && cur.isEmailVerified) {
                                                isGmailVerified = true
                                                isLoading = false
                                                infoBanner = "Identité propriétaire Gmail confirmée !"
                                            } else {
                                                Firebase.auth.signOut()
                                                isLoading = false
                                                localError = "Compte Google non autorisé : utilisez ${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL}."
                                            }
                                        },
                                        onAuthError = { msg ->
                                            isLoading = false
                                            localError = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = { isLoading = false }
                                    )
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("admin_signup_google_verify_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Autoriser avec Gmail Propriétaire", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            VerifiedStatusRow(
                                label = "Propriétaire Gmail vérifié : ${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL}"
                            )
                        }
                    } else {
                        // GMAIL TAB: Verify owner Gmail via Google Sign-In or Email Link + optional SMS 6-digit 2FA
                        if (!isGmailVerified) {
                            Button(
                                onClick = {
                                    if (!AdminFirebaseAuthHelper.isAuthorizedOwnerEmail(emailInput)) {
                                        localError = "Seul l'e-mail propriétaire ${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL} peut créer le compte Super Administrateur."
                                        return@Button
                                    }
                                    localError = null
                                    isLoading = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            val cur = Firebase.auth.currentUser
                                            if (cur != null && cur.email?.equals(MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL, ignoreCase = true) == true && cur.isEmailVerified) {
                                                isGmailVerified = true
                                                isLoading = false
                                                infoBanner = "Gmail propriétaire vérifié ! Recevez maintenant le code SMS à 6 chiffres pour sécuriser votre numéro."
                                            } else {
                                                Firebase.auth.signOut()
                                                isLoading = false
                                                localError = "Ce compte Google n'est pas le propriétaire principal autorisé."
                                            }
                                        },
                                        onAuthError = { msg ->
                                            isLoading = false
                                            localError = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = { isLoading = false }
                                    )
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("admin_signup_google_verify_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Vérifier mon Gmail avec Google", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    localError = null
                                    isLoading = true
                                    scope.launch {
                                        val res = AdminFirebaseAuthHelper.sendAdminEmailSignInLink(context, emailInput)
                                        isLoading = false
                                        res.fold(
                                            onSuccess = {
                                                emailLinkSent = true
                                                countdownSeconds = 60
                                                infoBanner = "Lien de vérification unique envoyé à ${emailInput.trim()}. Ouvrez votre boîte Gmail pour confirmer."
                                            },
                                            onFailure = { err -> localError = err.localizedMessage }
                                        )
                                    }
                                },
                                enabled = !isLoading && (!emailLinkSent || countdownSeconds == 0),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, LubumBlue)
                            ) {
                                Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = LubumBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        emailLinkSent && countdownSeconds > 0 -> "Renvoyer le lien Gmail (${countdownSeconds}s)"
                                        emailLinkSent -> "Renvoyer le lien de vérification par Gmail"
                                        else -> "Recevoir un lien de vérification par Gmail"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LubumBlue
                                )
                            }
                        } else {
                            VerifiedStatusRow(
                                label = "Gmail vérifié : ${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL}"
                            )

                            HorizontalDivider()

                            if (!isPhoneOtpVerified) {
                                Text(
                                    text = "Étape suivante (SMS à 6 chiffres) : liez votre téléphone pour activer la connexion par code SMS.",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )

                                OutlinedButton(
                                    onClick = { triggerSendSmsCode(isResend = false) },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("admin_signup_send_sms_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, LubumRed)
                                ) {
                                    Icon(Icons.Default.Sms, contentDescription = null, tint = LubumRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Envoyer le code SMS à 6 chiffres", color = LubumRed, fontWeight = FontWeight.Bold)
                                }

                                if (smsSent) {
                                    OtpSixDigitSection(
                                        otpCode = otpCode,
                                        onOtpChange = { otpCode = it },
                                        countdownSeconds = countdownSeconds,
                                        isLoading = isLoading,
                                        inputTestTag = "admin_signup_otp_input",
                                        resendTestTag = "admin_signup_resend_btn",
                                        confirmButtonText = "Valider le code SMS et activer",
                                        confirmButtonColor = LubumRed,
                                        onResendClick = { triggerSendSmsCode(isResend = true) },
                                        onConfirmClick = {
                                            localError = null
                                            isLoading = true
                                            scope.launch {
                                                val res = AdminFirebaseAuthHelper.verifyPhoneCredentialOrCode(
                                                    verificationId = verificationId,
                                                    smsCode = otpCode,
                                                    linkToExistingUser = true
                                                )
                                                isLoading = false
                                                res.fold(
                                                    onSuccess = {
                                                        isPhoneOtpVerified = true
                                                        infoBanner = "Code SMS validé ! Téléphone lié au Super Administrateur."
                                                    },
                                                    onFailure = { err -> localError = err.localizedMessage }
                                                )
                                            }
                                        }
                                    )
                                }
                            } else {
                                VerifiedStatusRow(
                                    label = "Téléphone lié et vérifié : ${normalizeCongoPhoneE164(phoneInput)}"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Finalize Admin Account Button
                    val canFinalize = if (selectedChannel == AdminAuthChannel.PHONE) {
                        isGmailVerified && isPhoneOtpVerified && fullName.trim().length >= 2
                    } else {
                        isGmailVerified && fullName.trim().length >= 2
                    }

                    Button(
                        onClick = {
                            if (fullName.trim().length < 2) {
                                localError = "Veuillez saisir votre nom complet."
                                return@Button
                            }
                            localError = null
                            isLoading = true
                            val method = when {
                                isGmailVerified && isPhoneOtpVerified -> AdminAuthMethod.GMAIL_AND_SMS
                                selectedChannel == AdminAuthChannel.PHONE -> AdminAuthMethod.PHONE_SMS
                                else -> AdminAuthMethod.GMAIL
                            }
                            val cleanPhone = normalizeCongoPhoneE164(phoneInput).takeIf { isPhoneOtpVerified || it.length >= 10 }
                            onCompleteSuperAdminRegistration(
                                fullName.trim(),
                                emailInput.trim(),
                                cleanPhone,
                                method,
                                verifiedPhoneUid,
                                { errMsg ->
                                    isLoading = false
                                    localError = errMsg
                                },
                                {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                            )
                        },
                        enabled = canFinalize && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("admin_signup_submit_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Shield, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPhoneOtpVerified)
                                    "Créer mon compte Super Administrateur"
                                else
                                    "Finaliser avec mon Gmail vérifié",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSignInScreen(
    adminAuthError: String?,
    onBackToHome: () -> Unit,
    onGoToAdminSignUp: () -> Unit = {},
    onVerifyAdminSignIn: (onDenied: (String) -> Unit, onVerified: () -> Unit) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var selectedChannel by remember { mutableStateOf(AdminAuthChannel.PHONE) }
    var emailInput by remember { mutableStateOf(MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL) }
    var phoneInput by remember { mutableStateOf("+243") }
    var otpCode by remember { mutableStateOf("") }

    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var smsSent by remember { mutableStateOf(false) }
    var emailLinkSent by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var infoBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds -= 1
        }
    }

    val displayedError = localError ?: adminAuthError

    fun sendLoginSmsOtp(isResend: Boolean) {
        if (activity == null) {
            localError = "Activité Android introuvable pour l'envoi du SMS."
            return
        }
        val cleanPhone = normalizeCongoPhoneE164(phoneInput)
        if (cleanPhone.length < 10) {
            localError = "Numéro de téléphone invalide. Exemple : +243 997 123 456."
            return
        }
        localError = null
        infoBanner = null
        isLoading = true
        AdminFirebaseAuthHelper.sendPhoneVerificationCode(
            activity = activity,
            rawPhone = cleanPhone,
            resendToken = if (isResend) resendToken else null,
            onCodeSent = { vid, token ->
                isLoading = false
                verificationId = vid
                resendToken = token
                smsSent = true
                countdownSeconds = 60
                infoBanner = "Code de vérification à usage unique (6 chiffres) envoyé par SMS au $cleanPhone."
            },
            onAutoVerified = { credential ->
                scope.launch {
                    val res = AdminFirebaseAuthHelper.verifyPhoneCredentialOrCode(
                        directCredential = credential,
                        linkToExistingUser = false
                    )
                    res.fold(
                        onSuccess = {
                            onVerifyAdminSignIn(
                                { deniedMsg ->
                                    isLoading = false
                                    localError = deniedMsg
                                },
                                {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                            )
                        },
                        onFailure = { err ->
                            isLoading = false
                            localError = err.localizedMessage
                        }
                    )
                }
            },
            onFailed = { errMsg ->
                isLoading = false
                localError = errMsg
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("admin_signin_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackToHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accueil")
                }
                TextButton(
                    onClick = onGoToAdminSignUp,
                    modifier = Modifier.testTag("admin_signin_goto_signup_btn")
                ) {
                    Text("Créer compte Admin", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LubumBlue)
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(LubumBlueContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = LubumBlue,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "CONNEXION ADMINISTRATEUR",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = LubumBlueDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Accès protégé par vérification réelle (SMS 6 chiffres ou Gmail propriétaire vérifié).",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            AdminChannelSelector(
                selected = selectedChannel,
                onSelect = {
                    selectedChannel = it
                    localError = null
                    infoBanner = null
                }
            )
        }

        if (!displayedError.isNullOrBlank()) {
            item { AdminErrorCard(displayedError) }
        }

        if (!infoBanner.isNullOrBlank()) {
            item { AdminInfoCard(infoBanner!!) }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedChannel == AdminAuthChannel.PHONE) {
                        Text(
                            text = "Connexion Administrateur par Code SMS à usage unique",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LubumBlueDark,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Numéro de téléphone Super Admin (+243...)") },
                            placeholder = { Text("+243 997 123 456") },
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = LubumRed) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_signin_phone_input"),
                            singleLine = true
                        )

                        Button(
                            onClick = { sendLoginSmsOtp(isResend = false) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("admin_signin_send_sms_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                        ) {
                            Icon(Icons.Default.Sms, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (smsSent) "Code SMS envoyé" else "Envoyer le code à 6 chiffres (SMS)",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (smsSent) {
                            OtpSixDigitSection(
                                otpCode = otpCode,
                                onOtpChange = { otpCode = it },
                                countdownSeconds = countdownSeconds,
                                isLoading = isLoading,
                                inputTestTag = "admin_signin_otp_input",
                                resendTestTag = "admin_signin_resend_btn",
                                confirmButtonText = "Valider le code et ouvrir l'Administration",
                                confirmButtonColor = LubumRed,
                                onResendClick = { sendLoginSmsOtp(isResend = true) },
                                onConfirmClick = {
                                    localError = null
                                    isLoading = true
                                    scope.launch {
                                        val res = AdminFirebaseAuthHelper.verifyPhoneCredentialOrCode(
                                            verificationId = verificationId,
                                            smsCode = otpCode,
                                            linkToExistingUser = false
                                        )
                                        res.fold(
                                            onSuccess = {
                                                onVerifyAdminSignIn(
                                                    { deniedMsg ->
                                                        isLoading = false
                                                        localError = deniedMsg
                                                    },
                                                    {
                                                        isLoading = false
                                                        onAuthSuccess()
                                                    }
                                                )
                                            },
                                            onFailure = { err ->
                                                isLoading = false
                                                localError = err.localizedMessage
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    } else {
                        Text(
                            text = "Connexion Administrateur par Gmail vérifié (${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LubumBlueDark,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Adresse Gmail du Super Administrateur") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = LubumBlue) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_signin_email_input"),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (!AdminFirebaseAuthHelper.isAuthorizedOwnerEmail(emailInput)) {
                                    localError = "Accès refusé : seul le compte ${MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL} est autorisé."
                                    return@Button
                                }
                                localError = null
                                isLoading = true
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        onVerifyAdminSignIn(
                                            { deniedMsg ->
                                                isLoading = false
                                                localError = deniedMsg
                                            },
                                            {
                                                isLoading = false
                                                onAuthSuccess()
                                            }
                                        )
                                    },
                                    onAuthError = { msg ->
                                        isLoading = false
                                        localError = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = { isLoading = false }
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("admin_google_signin_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connexion vérifiée avec Google", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                localError = null
                                isLoading = true
                                scope.launch {
                                    val res = AdminFirebaseAuthHelper.sendAdminEmailSignInLink(context, emailInput)
                                    isLoading = false
                                    res.fold(
                                        onSuccess = {
                                            emailLinkSent = true
                                            countdownSeconds = 60
                                            infoBanner = "Lien de connexion à usage unique envoyé à ${emailInput.trim()}. Cliquez sur le lien reçu dans Gmail."
                                        },
                                        onFailure = { err -> localError = err.localizedMessage }
                                    )
                                }
                            },
                            enabled = !isLoading && (!emailLinkSent || countdownSeconds == 0),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_signin_send_emaillink_btn"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.2.dp, LubumRed)
                        ) {
                            Icon(Icons.Default.MarkEmailUnread, contentDescription = null, tint = LubumRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    emailLinkSent && countdownSeconds > 0 -> "Renvoyer le lien Gmail (${countdownSeconds}s)"
                                    emailLinkSent -> "Renvoyer le lien à usage unique par Gmail"
                                    else -> "Envoyer un lien à usage unique par Gmail"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LubumRed
                            )
                        }
                    }

                    Text(
                        text = "Sécurité : aucun vendeur, client ou livreur ne peut obtenir les droits administrateur.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminChannelSelector(
    selected: AdminAuthChannel,
    onSelect: (AdminAuthChannel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AdminAuthChannel.values().forEach { channel ->
            val isSel = selected == channel
            Button(
                onClick = { onSelect(channel) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("admin_channel_${channel.name}"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSel) LubumBlue else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSel) Color.White else LubumBlueDark
                ),
                border = if (isSel) null else BorderStroke(1.dp, LubumBlue.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = if (channel == AdminAuthChannel.GMAIL) Icons.Default.Email else Icons.Default.Sms,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(channel.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OtpSixDigitSection(
    otpCode: String,
    onOtpChange: (String) -> Unit,
    countdownSeconds: Int,
    isLoading: Boolean,
    inputTestTag: String,
    resendTestTag: String,
    confirmButtonText: String,
    confirmButtonColor: Color,
    onResendClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LubumBlueLight)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Saisissez le code à 6 chiffres reçu par SMS :",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = LubumBlueDark
        )

        // Visual 6-box code preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (index in 0 until 6) {
                val charText = otpCode.getOrNull(index)?.toString() ?: ""
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(
                            width = 1.5.dp,
                            color = if (charText.isNotEmpty()) LubumBlue else LubumBlue.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = charText.ifEmpty { "•" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = if (charText.isNotEmpty()) LubumBlueDark else TextSecondary
                    )
                }
            }
        }

        OutlinedTextField(
            value = otpCode,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(6)
                onOtpChange(digits)
            },
            label = { Text("Code à 6 chiffres *") },
            placeholder = { Text("000000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(inputTestTag),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onResendClick,
                enabled = countdownSeconds == 0 && !isLoading,
                modifier = Modifier.testTag(resendTestTag)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = LubumRed)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (countdownSeconds > 0) "Renvoyer le code (${countdownSeconds}s)" else "Renvoyer le code",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (countdownSeconds > 0) TextSecondary else LubumRed
                )
            }
        }

        Button(
            onClick = onConfirmClick,
            enabled = otpCode.length == 6 && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = confirmButtonColor)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(confirmButtonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun VerifiedStatusRow(label: String) {
    Surface(
        color = LubumBlueContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Verified, contentDescription = null, tint = LubumBlue, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LubumBlueDark)
        }
    }
}

@Composable
private fun AdminErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LubumRedLight),
        border = BorderStroke(1.dp, LubumRed)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.GppBad, contentDescription = null, tint = LubumRed)
            Spacer(modifier = Modifier.width(10.dp))
            Text(message, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LubumRedDark)
        }
    }
}

@Composable
private fun AdminInfoCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LubumBlueContainer),
        border = BorderStroke(1.dp, LubumBlue)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = LubumBlue)
            Spacer(modifier = Modifier.width(10.dp))
            Text(message, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LubumBlueDark)
        }
    }
}
