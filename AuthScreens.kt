package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.auth_email.EmailAuthPortalScreen
import com.example.data.MarketplaceFirestoreRepository
import com.example.data.MarketplaceSeedData
import com.example.model.*
import com.example.ui.components.LubumarketHeader
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
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
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Configuration Google Sign-In manquante (default_web_client_id introuvable)")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Type d'identifiant inattendu")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Échec de la connexion Google")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

enum class AuthScreenMode {
    PUBLIC_HOME,
    SIGN_IN,
    SIGN_UP,
    ADMIN_SIGN_IN,
    ADMIN_SIGN_UP,
    EMAIL_PASSWORD_AUTH
}

@Composable
fun PublicAuthEntryFlow(
    adminAuthError: String? = null,
    onClearAdminError: () -> Unit = {},
    onSetAdminAuthFlow: (active: Boolean, isSignUp: Boolean) -> Unit = { _, _ -> },
    onVerifyAdminSignIn: (onDenied: (String) -> Unit, onVerified: () -> Unit) -> Unit = { _, onOk -> onOk() },
    onCompleteSuperAdminRegistration: (
        fullName: String,
        email: String,
        phone: String?,
        authMethod: AdminAuthMethod,
        targetAdminUid: String?,
        onError: (String) -> Unit,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, onOk -> onOk() },
    onSetPendingRegistration: (PendingRegistrationData) -> Unit,
    onAuthSuccess: () -> Unit
) {
    var mode by remember { mutableStateOf(AuthScreenMode.PUBLIC_HOME) }
    var useUsd by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ProductCategory.TOUS) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mode) {
        when (mode) {
            AuthScreenMode.ADMIN_SIGN_IN -> onSetAdminAuthFlow(true, false)
            AuthScreenMode.ADMIN_SIGN_UP -> onSetAdminAuthFlow(true, true)
            else -> onSetAdminAuthFlow(false, false)
        }
    }

    BackHandler(enabled = mode != AuthScreenMode.PUBLIC_HOME) {
        onClearAdminError()
        onSetAdminAuthFlow(false, false)
        mode = AuthScreenMode.PUBLIC_HOME
    }

    val filteredPreview = remember(searchQuery, selectedCategory) {
        MarketplaceSeedData.starterProducts.filter { p ->
            (selectedCategory == ProductCategory.TOUS || p.category == selectedCategory) &&
                (searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) ||
                    p.sellerName.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LubumarketHeader(
                currentRole = UserRole.CLIENT,
                useUsd = useUsd,
                onToggleCurrency = { useUsd = !useUsd },
                cartItemCount = 0,
                onCartClick = { mode = AuthScreenMode.SIGN_IN },
                isSignedIn = false,
                onSignOutClick = {}
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (mode) {
                AuthScreenMode.PUBLIC_HOME -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            color = LubumBlueLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Bienvenue sur LUBUMARKET — Connectez-vous pour commander, vendre ou livrer",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LubumBlueDark
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { mode = AuthScreenMode.SIGN_UP },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("home_signup_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("S'INSCRIRE", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { mode = AuthScreenMode.SIGN_IN },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("home_signin_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.5.dp, LubumRed),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.White,
                                            contentColor = LubumRed
                                        )
                                    ) {
                                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("SE CONNECTER", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            onClearAdminError()
                                            mode = AuthScreenMode.ADMIN_SIGN_UP
                                        },
                                        modifier = Modifier.testTag("home_admin_signup_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = LubumRed,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Devenir Administrateur",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LubumRed
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            onClearAdminError()
                                            mode = AuthScreenMode.ADMIN_SIGN_IN
                                        },
                                        modifier = Modifier.testTag("home_admin_signin_button")
                                    ) {
                                        Icon(
                                            Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = LubumBlueDark,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Connexion Administrateur",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LubumBlueDark
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedButton(
                                    onClick = { mode = AuthScreenMode.EMAIL_PASSWORD_AUTH },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("home_email_password_auth_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, LubumBlue),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White,
                                        contentColor = LubumBlueDark
                                    )
                                ) {
                                    Icon(Icons.Default.AlternateEmail, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Compte Email & Mot de passe (SQLite / JWT)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        HomeScreen(
                            products = filteredPreview,
                            promotions = MarketplaceSeedData.starterPromotions,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            selectedCategory = selectedCategory,
                            onCategorySelect = { selectedCategory = it },
                            useUsd = useUsd,
                            cdfRate = 2800.0,
                            onProductClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Inscrivez-vous ou connectez-vous pour voir la fiche et commander.")
                                }
                                mode = AuthScreenMode.SIGN_IN
                            },
                            onAddToCart = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Connectez-vous pour ajouter au panier.")
                                }
                                mode = AuthScreenMode.SIGN_IN
                            },
                            onBuyNow = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Connectez-vous pour commander ce produit.")
                                }
                                mode = AuthScreenMode.SIGN_IN
                            },
                            onDeliveryBannerClick = { mode = AuthScreenMode.SIGN_UP }
                        )
                    }
                }

                AuthScreenMode.SIGN_IN -> {
                    SignInScreen(
                        onBackToHome = { mode = AuthScreenMode.PUBLIC_HOME },
                        onGoToSignUp = { mode = AuthScreenMode.SIGN_UP },
                        onGoToEmailPasswordAuth = { mode = AuthScreenMode.EMAIL_PASSWORD_AUTH },
                        onGoToAdminSignIn = {
                            onClearAdminError()
                            mode = AuthScreenMode.ADMIN_SIGN_IN
                        },
                        onGoToAdminSignUp = {
                            onClearAdminError()
                            mode = AuthScreenMode.ADMIN_SIGN_UP
                        },
                        onAuthSuccess = onAuthSuccess,
                        onAuthError = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }

                AuthScreenMode.SIGN_UP -> {
                    SignUpScreen(
                        onBackToHome = { mode = AuthScreenMode.PUBLIC_HOME },
                        onGoToSignIn = { mode = AuthScreenMode.SIGN_IN },
                        onSetPendingRegistration = onSetPendingRegistration,
                        onAuthSuccess = onAuthSuccess,
                        onAuthError = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }

                AuthScreenMode.ADMIN_SIGN_IN -> {
                    AdminSignInScreen(
                        adminAuthError = adminAuthError,
                        onBackToHome = {
                            onClearAdminError()
                            onSetAdminAuthFlow(false, false)
                            mode = AuthScreenMode.PUBLIC_HOME
                        },
                        onGoToAdminSignUp = {
                            onClearAdminError()
                            mode = AuthScreenMode.ADMIN_SIGN_UP
                        },
                        onVerifyAdminSignIn = onVerifyAdminSignIn,
                        onAuthSuccess = onAuthSuccess
                    )
                }

                AuthScreenMode.ADMIN_SIGN_UP -> {
                    AdminSignUpScreen(
                        adminAuthError = adminAuthError,
                        onBackToHome = {
                            onClearAdminError()
                            onSetAdminAuthFlow(false, false)
                            mode = AuthScreenMode.PUBLIC_HOME
                        },
                        onGoToAdminSignIn = {
                            onClearAdminError()
                            mode = AuthScreenMode.ADMIN_SIGN_IN
                        },
                        onCompleteSuperAdminRegistration = onCompleteSuperAdminRegistration,
                        onAuthSuccess = onAuthSuccess
                    )
                }

                AuthScreenMode.EMAIL_PASSWORD_AUTH -> {
                    EmailAuthPortalScreen(
                        onBackToHome = { mode = AuthScreenMode.PUBLIC_HOME }
                    )
                }
            }
        }
    }
}

@Composable
fun SignInScreen(
    onBackToHome: () -> Unit,
    onGoToSignUp: () -> Unit,
    onGoToEmailPasswordAuth: () -> Unit = {},
    onGoToAdminSignIn: () -> Unit = {},
    onGoToAdminSignUp: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("signin_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBackToHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retour à l'accueil")
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LubumBlueContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = LubumBlue, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "SE CONNECTER À LUBUMARKET",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = LubumBlueDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Accédez automatiquement à votre Espace Client, Vendeur, Livreur ou Administration.",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
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
                    Text(
                        text = "Connexion sécurisée via Google (Firebase Authentication)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    onAuthError(msg)
                                },
                                scope = scope,
                                onAuthCancelled = { isLoading = false }
                            )
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_signin_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Se connecter avec Google", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            val recoveryIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://accounts.google.com/signin/recovery")
                            )
                            context.startActivity(recoveryIntent)
                        }
                    ) {
                        Text("Mot de passe oublié ? Récupérer mon compte Google", fontSize = 12.sp, color = LubumRed)
                    }

                    HorizontalDivider(color = BorderColor)

                    OutlinedButton(
                        onClick = onGoToEmailPasswordAuth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("signin_email_password_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, LubumBlue)
                    ) {
                        Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = LubumBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connexion par Email & Mot de passe",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = LubumBlueDark
                        )
                    }
                }
            }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Nouveau sur Lubumarket ?", fontSize = 13.sp, color = TextSecondary)
                    TextButton(onClick = onGoToSignUp) {
                        Text("S'INSCRIRE", fontWeight = FontWeight.Bold, color = LubumBlue)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onGoToAdminSignUp) {
                        Text("Devenir Administrateur", fontSize = 11.sp, color = LubumRed, fontWeight = FontWeight.SemiBold)
                    }
                    Text("•", fontSize = 11.sp, color = TextSecondary)
                    TextButton(onClick = onGoToAdminSignIn) {
                        Text("Connexion Admin", fontSize = 11.sp, color = LubumBlueDark, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun SignUpScreen(
    onBackToHome: () -> Unit,
    onGoToSignIn: () -> Unit,
    onSetPendingRegistration: (PendingRegistrationData) -> Unit,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var selectedRole by remember { mutableStateOf(UserRole.CLIENT) }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+243 ") }
    var emailHint by remember { mutableStateOf("") }
    var selectedCommune by remember { mutableStateOf(CommuneLubumbashi.LUBUMBASHI) }
    var quartier by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("Moto Express") }
    var deliveryZone by remember { mutableStateOf("Lubumbashi Centre, Kenya, Golf") }
    var communeMenuExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("signup_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                TextButton(onClick = onGoToSignIn) {
                    Text("Déjà inscrit ? Se connecter", fontSize = 12.sp, color = LubumBlue)
                }
            }
        }

        item {
            Text(
                text = "S'INSCRIRE SUR LUBUMARKET",
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = LubumBlueDark
            )
            Text(
                text = "Choisissez votre type de compte (l'accès Administrateur n'est jamais ouvert à l'inscription publique).",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // 3 Choices ONLY: CLIENT, VENDEUR, LIVREUR
        item {
            val publicRoles = listOf(
                Triple(UserRole.CLIENT, "CLIENT", "Pour acheter des produits en ligne et recevoir chez vous"),
                Triple(UserRole.VENDEUR, "VENDEUR", "Pour publier et vendre vos marchandises (validation requise)"),
                Triple(UserRole.LIVREUR, "LIVREUR", "Pour effectuer des livraisons à Lubumbashi (validation requise)")
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                publicRoles.forEach { (role, title, desc) ->
                    val isSel = selectedRole == role
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRole = role }
                            .testTag("signup_role_${role.name}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) LubumBlueContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSel) BorderStroke(1.5.dp, LubumBlue) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSel, onClick = { selectedRole = role })
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = when (role) {
                                    UserRole.CLIENT -> Icons.Default.ShoppingCart
                                    UserRole.VENDEUR -> Icons.Default.Storefront
                                    UserRole.LIVREUR -> Icons.Default.TwoWheeler
                                    UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                },
                                contentDescription = null,
                                tint = if (isSel) LubumBlue else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSel) LubumBlueDark else MaterialTheme.colorScheme.onSurface
                                )
                                Text(desc, fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Adapted Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Informations du compte ${selectedRole.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Nom complet *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Numéro de téléphone (+243...) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = emailHint,
                        onValueChange = { emailHint = it },
                        label = { Text("Adresse e-mail Google *") },
                        placeholder = { Text("ex: nom@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Commune à Lubumbashi :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedButton(
                            onClick = { communeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedCommune.nom, fontSize = 12.sp, color = LubumBlueDark)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = communeMenuExpanded,
                            onDismissRequest = { communeMenuExpanded = false }
                        ) {
                            CommuneLubumbashi.values().forEach { com ->
                                DropdownMenuItem(
                                    text = { Text(com.nom, fontSize = 12.sp) },
                                    onClick = {
                                        selectedCommune = com
                                        communeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = quartier,
                        onValueChange = { quartier = it },
                        label = { Text("Quartier *") },
                        placeholder = { Text("ex: Golf Malela, Gambela, Bel-Air...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (selectedRole == UserRole.VENDEUR) {
                        OutlinedTextField(
                            value = storeName,
                            onValueChange = { storeName = it },
                            label = { Text("Nom de votre boutique *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    if (selectedRole == UserRole.LIVREUR) {
                        OutlinedTextField(
                            value = vehicleType,
                            onValueChange = { vehicleType = it },
                            label = { Text("Moyen de transport *") },
                            placeholder = { Text("ex: Moto TVS 125, Tricycle, Voiture...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = deliveryZone,
                            onValueChange = { deliveryZone = it },
                            label = { Text("Zone de livraison souhaitée *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    if (selectedRole == UserRole.VENDEUR || selectedRole == UserRole.LIVREUR) {
                        Surface(
                            color = AccentAmberLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = AccentAmberDark, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Votre compte professionnel restera en attente jusqu'à l'approbation du propriétaire.",
                                    fontSize = 11.sp,
                                    color = AccentAmberDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (fullName.isBlank() || phone.trim().length < 6 || quartier.isBlank()) {
                                onAuthError("Veuillez remplir votre nom, téléphone et quartier.")
                                return@Button
                            }
                            if (selectedRole == UserRole.VENDEUR && storeName.isBlank()) {
                                onAuthError("Veuillez saisir le nom de votre boutique.")
                                return@Button
                            }
                            if (selectedRole == UserRole.LIVREUR && (vehicleType.isBlank() || deliveryZone.isBlank())) {
                                onAuthError("Veuillez préciser votre moyen de transport et votre zone.")
                                return@Button
                            }

                            onSetPendingRegistration(
                                PendingRegistrationData(
                                    role = selectedRole,
                                    fullName = fullName.trim(),
                                    phone = phone.trim(),
                                    emailHint = emailHint.trim(),
                                    commune = selectedCommune.nom,
                                    quartier = quartier.trim(),
                                    storeName = if (selectedRole == UserRole.VENDEUR) storeName.trim() else null,
                                    vehicleType = if (selectedRole == UserRole.LIVREUR) vehicleType.trim() else null,
                                    deliveryZone = if (selectedRole == UserRole.LIVREUR) deliveryZone.trim() else null
                                )
                            )

                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    onAuthError(msg)
                                },
                                scope = scope,
                                onAuthCancelled = { isLoading = false }
                            )
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("signup_submit_google_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Valider avec Google (Firebase Auth)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PendingApprovalScreen(
    user: UserProfile,
    onSignOutClick: () -> Unit
) {
    val isRestricted = user.accountStatus == AccountStatus.SUSPENDED ||
        user.accountStatus == AccountStatus.REJECTED ||
        user.accountStatus == AccountStatus.BLOCKED

    val titleText = when (user.accountStatus) {
        AccountStatus.SUSPENDED -> "Compte Temporairement Suspendu"
        AccountStatus.REJECTED -> "Demande de Compte Refusée"
        AccountStatus.BLOCKED -> "Compte Bloqué par l'Administration"
        else -> "En attente d'approbation"
    }

    val bodyText = when (user.accountStatus) {
        AccountStatus.SUSPENDED -> "Votre compte ${user.role.label} (${user.fullName}) a été temporairement suspendu par l'administration de LUBUMARKET."
        AccountStatus.REJECTED -> "Votre demande de compte ${user.role.label} n'a pas été approuvée par l'administration de LUBUMARKET."
        AccountStatus.BLOCKED -> "Votre compte a été bloqué pour des raisons de sécurité ou de conformité. Contactez le support LUBUMARKET."
        else -> "Merci ${user.fullName} ! Votre demande de compte ${user.role.label} a bien été enregistrée. Vous pourrez exercer vos activités dès que le propriétaire de LUBUMARKET aura approuvé votre dossier."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("pending_approval_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isRestricted) LubumRedLight else LubumBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRestricted) Icons.Default.Block else Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = if (isRestricted) LubumRed else LubumBlue,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = titleText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = if (isRestricted) LubumRedDark else LubumBlueDark
                )

                Text(
                    text = bodyText,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Rôle demandé : ${user.role.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Statut actuel : ${user.accountStatus.label}",
                            fontSize = 12.sp,
                            color = if (isRestricted) LubumRedDark else LubumBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Commune : ${user.commune} (${user.quartier})", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                OutlinedButton(
                    onClick = onSignOutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pending_signout_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LubumRed)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Se déconnecter", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
