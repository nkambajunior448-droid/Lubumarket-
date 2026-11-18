package com.example.auth_email

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*

@Composable
fun EmailAuthPortalScreen(
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: EmailAuthViewModel = viewModel(factory = EmailAuthViewModel.Factory(context))
    val state by vm.uiState.collectAsStateWithLifecycle()

    BackHandler {
        when (state.activeTab) {
            EmailAuthTab.FORGOT_PASSWORD,
            EmailAuthTab.RESET_PASSWORD,
            EmailAuthTab.PENDING_VERIFICATION -> vm.switchTab(EmailAuthTab.LOGIN)
            else -> onBackToHome()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .testTag("email_auth_portal"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBackToHome,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("email_auth_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = LubumBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retour", color = LubumBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // En-tête identitaire Lubumarket Bleu & Rouge (100% autonome Express/SQLite/JWT)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(LubumBlueDark, LubumBlue, LubumRedDark))
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "COMPTE EMAIL LUBUMARKET",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Authentification sécurisée • bcrypt + JWT (cookie httpOnly) + SQLite",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        if (state.activeTab == EmailAuthTab.LOGIN || state.activeTab == EmailAuthTab.REGISTER) {
            item {
                SegmentedAuthTabs(
                    activeTab = state.activeTab,
                    onSelectTab = { vm.switchTab(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                )
            }
        }

        state.errorMessage?.let { errorMsg ->
            item {
                FeedbackBanner(
                    message = errorMsg,
                    isError = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .testTag("email_auth_error_banner")
                )
            }
        }

        state.infoMessage?.let { infoMsg ->
            item {
                FeedbackBanner(
                    message = infoMsg,
                    isError = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .testTag("email_auth_info_banner")
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            ) {
                when (state.activeTab) {
                    EmailAuthTab.LOGIN -> EmailLoginCard(
                        isLoading = state.isLoading,
                        onLogin = { email, password -> vm.login(email, password) },
                        onGoToRegister = { vm.switchTab(EmailAuthTab.REGISTER) },
                        onGoToForgotPassword = { vm.switchTab(EmailAuthTab.FORGOT_PASSWORD) },
                        onGoToVerifyEmail = { vm.switchTab(EmailAuthTab.PENDING_VERIFICATION) }
                    )

                    EmailAuthTab.REGISTER -> EmailRegisterCard(
                        isLoading = state.isLoading,
                        onRegister = { email, pw, cpw -> vm.register(email, pw, cpw) },
                        onGoToLogin = { vm.switchTab(EmailAuthTab.LOGIN) }
                    )

                    EmailAuthTab.PENDING_VERIFICATION -> PendingVerificationCard(
                        email = state.pendingVerificationEmail,
                        isLoading = state.isLoading,
                        onVerifyToken = { token -> vm.verifyEmailToken(token) },
                        onResend = { email -> vm.resendVerification(email) },
                        onBackToLogin = { vm.switchTab(EmailAuthTab.LOGIN) }
                    )

                    EmailAuthTab.FORGOT_PASSWORD -> ForgotPasswordCard(
                        isLoading = state.isLoading,
                        onSubmitEmail = { email -> vm.forgotPassword(email) },
                        onAlreadyHaveToken = { vm.switchTab(EmailAuthTab.RESET_PASSWORD) },
                        onBackToLogin = { vm.switchTab(EmailAuthTab.LOGIN) }
                    )

                    EmailAuthTab.RESET_PASSWORD -> ResetPasswordCard(
                        isLoading = state.isLoading,
                        onResetPassword = { token, newPw, confirmPw ->
                            vm.resetPassword(token, newPw, confirmPw)
                        },
                        onBackToLogin = { vm.switchTab(EmailAuthTab.LOGIN) }
                    )

                    EmailAuthTab.ACCOUNT -> EmailAccountSessionCard(
                        user = state.currentUser,
                        protectedRouteStatus = state.protectedRouteStatus,
                        hasHttpOnlyCookie = state.hasActiveHttpOnlyCookie,
                        isLoading = state.isLoading,
                        onCheckProtectedRoute = { vm.checkProtectedEndpoint() },
                        onLogout = { vm.logout() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedAuthTabs(
    activeTab: EmailAuthTab,
    onSelectTab: (EmailAuthTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = LightSurfaceVariant,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isLogin = activeTab == EmailAuthTab.LOGIN
            Button(
                onClick = { onSelectTab(EmailAuthTab.LOGIN) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("tab_email_login"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLogin) LubumBlue else Color.Transparent,
                    contentColor = if (isLogin) Color.White else TextPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Connexion", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            val isReg = activeTab == EmailAuthTab.REGISTER
            Button(
                onClick = { onSelectTab(EmailAuthTab.REGISTER) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("tab_email_register"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReg) LubumRed else Color.Transparent,
                    contentColor = if (isReg) Color.White else TextPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Inscription", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FeedbackBanner(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isError) LubumRedLight else LubumBlueLight
    val border = if (isError) LubumRed else LubumBlue
    val fg = if (isError) LubumRedDark else LubumBlueDark
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.MarkEmailRead,
                contentDescription = null,
                tint = fg
            )
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
        }
    }
}

@Composable
private fun EmailLoginCard(
    isLoading: Boolean,
    onLogin: (String, String) -> Unit,
    onGoToRegister: () -> Unit,
    onGoToForgotPassword: () -> Unit,
    onGoToVerifyEmail: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Se connecter",
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = LubumBlueDark
            )
            Text(
                text = "Entrez votre email vérifié et votre mot de passe pour ouvrir une session sécurisée.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Adresse email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = LubumBlue) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_email_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = LubumBlue) },
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Masquer le mot de passe" else "Afficher le mot de passe"
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onGoToForgotPassword,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("forgot_password_link")
                ) {
                    Text(
                        text = "Mot de passe oublié ?",
                        color = LubumRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Button(
                onClick = { onLogin(email, password) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("email_login_submit_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("SE CONNECTER", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }

            HorizontalDivider(color = BorderColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onGoToVerifyEmail,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Valider un lien email", fontSize = 12.sp, color = TextSecondary)
                }
                TextButton(
                    onClick = onGoToRegister,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Créer un compte", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LubumBlue)
                }
            }
        }
    }
}

@Composable
private fun EmailRegisterCard(
    isLoading: Boolean,
    onRegister: (String, String, String) -> Unit,
    onGoToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val passwordValidLength = EmailAuthValidator.hasMinLength(password)
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Créer un compte",
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = LubumRedDark
            )
            Text(
                text = "Un lien de confirmation sera envoyé à votre adresse email pour activer votre accès.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Adresse email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = LubumBlue) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_email_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe (8 caractères min.)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = LubumBlue) },
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Masquer" else "Afficher"
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_password_input")
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirmer le mot de passe") },
                leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null, tint = LubumBlue) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_confirm_password_input")
            )

            // Règles visuelles de sécurité en direct
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PasswordRuleRow(
                    met = passwordValidLength,
                    label = "Au moins 8 caractères"
                )
                PasswordRuleRow(
                    met = passwordsMatch,
                    label = "Confirmation identique au mot de passe"
                )
            }

            Button(
                onClick = { onRegister(email, password, confirmPassword) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("email_register_submit_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LubumRed)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("CRÉER MON COMPTE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Déjà inscrit ?", fontSize = 12.sp, color = TextSecondary)
                TextButton(
                    onClick = onGoToLogin,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Se connecter", fontWeight = FontWeight.Bold, color = LubumBlue, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PasswordRuleRow(met: Boolean, label: String) {
    val color = if (met) LubumBlue else TextSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = color,
            fontWeight = if (met) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PendingVerificationCard(
    email: String,
    isLoading: Boolean,
    onVerifyToken: (String) -> Unit,
    onResend: (String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var emailField by remember(email) { mutableStateOf(email) }
    var tokenInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.MarkEmailUnread, contentDescription = null, tint = LubumBlue, modifier = Modifier.size(28.dp))
                Text(
                    text = "Vérification de l'email requise",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = LubumBlueDark
                )
            }
            Text(
                text = "Cliquez sur le lien de confirmation reçu dans votre boîte mail. La connexion reste bloquée tant que votre email n'est pas confirmé.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            OutlinedTextField(
                value = emailField,
                onValueChange = { emailField = it },
                label = { Text("Adresse email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { onResend(emailField) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("resend_verification_btn"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, LubumBlue)
            ) {
                Text("Renvoyer le lien de confirmation", color = LubumBlue, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = BorderColor)

            Text(
                text = "Ou collez le lien / jeton reçu par email :",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Jeton ou URL de vérification") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_token_input")
            )
            Button(
                onClick = { onVerifyToken(tokenInput) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("verify_token_submit_btn"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
            ) {
                Text("Confirmer mon email", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onBackToLogin,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .minimumInteractiveComponentSize()
            ) {
                Text("Retour à la connexion", color = LubumRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ForgotPasswordCard(
    isLoading: Boolean,
    onSubmitEmail: (String) -> Unit,
    onAlreadyHaveToken: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Mot de passe oublié",
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = LubumRedDark
            )
            Text(
                text = "Saisissez votre adresse email pour recevoir un lien de réinitialisation valable 15 minutes.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Adresse email du compte") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = LubumRed) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_email_input")
            )

            Button(
                onClick = { onSubmitEmail(email) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("forgot_submit_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LubumRed)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Envoyer le lien de réinitialisation", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onAlreadyHaveToken,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("J'ai déjà un code/lien", fontSize = 12.sp, color = LubumBlue, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = onBackToLogin,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Connexion", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ResetPasswordCard(
    isLoading: Boolean,
    onResetPassword: (String, String, String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Nouveau mot de passe",
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = LubumBlueDark
            )
            Text(
                text = "Collez le lien ou le jeton reçu par email (valide 15 minutes) puis définissez un nouveau mot de passe.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Jeton ou URL de réinitialisation") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_token_input")
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Nouveau mot de passe (8 car. min.)") },
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Masquer" else "Afficher"
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_new_password_input")
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirmer le nouveau mot de passe") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_confirm_password_input")
            )

            Button(
                onClick = { onResetPassword(tokenInput, newPassword, confirmPassword) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("reset_submit_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Réinitialiser le mot de passe", fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = onBackToLogin,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .minimumInteractiveComponentSize()
            ) {
                Text("Retour à la connexion", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmailAccountSessionCard(
    user: EmailAuthUserDto?,
    protectedRouteStatus: String?,
    hasHttpOnlyCookie: Boolean,
    isLoading: Boolean,
    onCheckProtectedRoute: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("email_account_session_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LubumBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = LubumBlue)
                }
                Column {
                    Text(
                        text = "Session Connectée",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = LubumBlueDark
                    )
                    Text(
                        text = user?.email ?: "Utilisateur vérifié",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = LightSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("ID SQLite : #${user?.id ?: "-"}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Statut email : ${if (user?.isVerified == true) "Vérifié" else "En attente"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LubumBlue
                    )
                    if (!user?.createdAt.isNullOrBlank()) {
                        Text("Créé le : ${user?.createdAt}", fontSize = 11.sp, color = TextSecondary)
                    }
                    Text(
                        text = "Cookie httpOnly JWT : ${if (hasHttpOnlyCookie) "Présent et actif" else "Vérifié côté serveur"}",
                        fontSize = 11.sp,
                        color = LubumBlueDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!protectedRouteStatus.isNullOrBlank()) {
                        Text(
                            text = protectedRouteStatus,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onCheckProtectedRoute,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("check_protected_route_btn"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, LubumBlue)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = LubumBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tester la route privée (/api/protected)", color = LubumBlue, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onLogout,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("email_logout_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LubumRed)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DÉCONNEXION", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
