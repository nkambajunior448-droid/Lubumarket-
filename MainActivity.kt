package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MarketplaceFirestoreRepository
import com.example.model.*
import com.example.ui.components.LubumarketHeader
import com.example.ui.screens.*
import com.example.ui.theme.LubumBlue
import com.example.ui.theme.LubumRed
import com.example.ui.theme.LubumarketTheme
import android.content.Intent
import com.example.auth.AdminFirebaseAuthHelper
import com.example.viewmodel.MarketplaceViewModel
import com.example.viewmodel.SessionUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

enum class NavigationTab {
    HOME,
    CATALOG,
    CART,
    ORDERS,
    PROFILE
}

class MainActivity : ComponentActivity() {
    private var incomingDeepLink by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDeepLink = intent.data?.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingDeepLink = intent?.data?.toString()
        enableEdgeToEdge()
        setContent {
            LubumarketTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val credentialManager = remember { CredentialManager.create(context) }

                val viewModel: MarketplaceViewModel = viewModel(
                    factory = MarketplaceViewModel.Factory(
                        MarketplaceFirestoreRepository(context.applicationContext)
                    )
                )
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(incomingDeepLink) {
                    val link = incomingDeepLink
                    if (!link.isNullOrBlank() && Firebase.auth.isSignInWithEmailLink(link)) {
                        val pendingEmail = AdminFirebaseAuthHelper.getPendingEmailForLink(context)
                            ?: MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL
                        val res = AdminFirebaseAuthHelper.completeEmailLinkSignIn(
                            context = context,
                            email = pendingEmail,
                            emailLink = link
                        )
                        incomingDeepLink = null
                        if (res.isSuccess) {
                            viewModel.verifyPrincipalAdminSignIn(
                                onDenied = {},
                                onVerified = {}
                            )
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (Firebase.auth.currentUser == null && !uiState.isAdminAuthFlow) {
                        attemptAutoSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = { viewModel.onSignedInFromUi() },
                            onUnauthenticated = {},
                            scope = scope
                        )
                    }
                }

                when (val session = uiState.sessionState) {
                    SessionUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = LubumBlue)
                                Text("Chargement de votre espace LUBUMARKET...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    SessionUiState.SignedOut -> {
                        PublicAuthEntryFlow(
                            adminAuthError = uiState.adminAuthError,
                            onClearAdminError = { viewModel.clearAdminAuthError() },
                            onSetAdminAuthFlow = { active, isSignUp ->
                                viewModel.setAdminAuthFlow(active, isSignUp)
                            },
                            onVerifyAdminSignIn = { onDenied, onVerified ->
                                viewModel.verifyPrincipalAdminSignIn(onDenied, onVerified)
                            },
                            onCompleteSuperAdminRegistration = { fullName, email, phone, authMethod, targetUid, onErr, onOk ->
                                viewModel.completeSuperAdminRegistration(
                                    fullName = fullName,
                                    email = email,
                                    phone = phone,
                                    authMethod = authMethod,
                                    targetAdminUid = targetUid,
                                    onError = onErr,
                                    onSuccess = onOk
                                )
                            },
                            onSetPendingRegistration = { viewModel.setPendingRegistration(it) },
                            onAuthSuccess = { viewModel.onSignedInFromUi() }
                        )
                    }

                    is SessionUiState.PendingOrSuspended -> {
                        PendingApprovalScreen(
                            user = session.user,
                            onSignOutClick = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        )
                    }

                    is SessionUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = LubumRed, modifier = Modifier.size(48.dp))
                                Text(session.message, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Button(
                                    onClick = {
                                        signOut(credentialManager = credentialManager, scope = scope)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                                ) {
                                    Text("Retour à la connexion")
                                }
                            }
                        }
                    }

                    is SessionUiState.Authenticated -> {
                        val user = session.user
                        val isAdmin = session.isAdmin
                        var activeTab by remember(user.userId, isAdmin) {
                            mutableStateOf(if (isAdmin) NavigationTab.PROFILE else NavigationTab.HOME)
                        }
                        val snackbarHostState = remember { SnackbarHostState() }

                        LaunchedEffect(uiState.statusMessage) {
                            uiState.statusMessage?.let { msg ->
                                snackbarHostState.showSnackbar(msg)
                                viewModel.clearStatusMessage()
                            }
                        }

                        BackHandler(
                            enabled = uiState.selectedProduct != null ||
                                uiState.selectedOrder != null ||
                                uiState.showCheckoutSheet ||
                                activeTab != NavigationTab.HOME
                        ) {
                            when {
                                uiState.showCheckoutSheet -> viewModel.setShowCheckoutSheet(false)
                                uiState.selectedProduct != null -> viewModel.selectProduct(null)
                                uiState.selectedOrder != null -> viewModel.selectOrder(null)
                                activeTab != NavigationTab.HOME -> activeTab = NavigationTab.HOME
                            }
                        }

                        val effectiveRole = if (isAdmin) UserRole.ADMIN else user.role
                        val cdfRate = uiState.cdfRate
                        val activePromotions = uiState.promotions.filter { it.isActive }

                        val onSignOutAction: () -> Unit = {
                            signOut(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                LubumarketHeader(
                                    currentRole = effectiveRole,
                                    useUsd = uiState.useUsd,
                                    onToggleCurrency = { viewModel.toggleCurrency() },
                                    cartItemCount = uiState.cart.sumOf { it.quantity },
                                    onCartClick = { activeTab = NavigationTab.CART },
                                    isSignedIn = true,
                                    onSignOutClick = onSignOutAction
                                )
                            },
                            bottomBar = {
                                NavigationBar(
                                    modifier = Modifier
                                        .navigationBarsPadding()
                                        .testTag("bottom_nav_bar"),
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    NavigationBarItem(
                                        selected = activeTab == NavigationTab.HOME,
                                        onClick = { activeTab = NavigationTab.HOME },
                                        icon = {
                                            Icon(
                                                imageVector = if (activeTab == NavigationTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                                contentDescription = "Accueil"
                                            )
                                        },
                                        label = { Text("Accueil", fontSize = 11.sp) }
                                    )

                                    NavigationBarItem(
                                        selected = activeTab == NavigationTab.CATALOG,
                                        onClick = { activeTab = NavigationTab.CATALOG },
                                        icon = {
                                            Icon(
                                                imageVector = if (activeTab == NavigationTab.CATALOG) Icons.Filled.GridView else Icons.Outlined.GridView,
                                                contentDescription = "Catalogue"
                                            )
                                        },
                                        label = { Text("Catalogue", fontSize = 11.sp) }
                                    )

                                    NavigationBarItem(
                                        selected = activeTab == NavigationTab.CART,
                                        onClick = { activeTab = NavigationTab.CART },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    val count = uiState.cart.sumOf { it.quantity }
                                                    if (count > 0) {
                                                        Badge(containerColor = LubumRed) { Text("$count") }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (activeTab == NavigationTab.CART) Icons.Filled.ShoppingCart else Icons.Outlined.ShoppingCart,
                                                    contentDescription = "Panier"
                                                )
                                            }
                                        },
                                        label = { Text("Panier", fontSize = 11.sp) }
                                    )

                                    NavigationBarItem(
                                        selected = activeTab == NavigationTab.ORDERS,
                                        onClick = { activeTab = NavigationTab.ORDERS },
                                        icon = {
                                            Icon(
                                                imageVector = if (activeTab == NavigationTab.ORDERS) Icons.Filled.LocalShipping else Icons.Outlined.LocalShipping,
                                                contentDescription = "Suivi"
                                            )
                                        },
                                        label = { Text("Livraisons", fontSize = 11.sp) }
                                    )

                                    val profileLabel = when (effectiveRole) {
                                        UserRole.CLIENT -> "Mon Espace"
                                        UserRole.VENDEUR -> "Vendeur"
                                        UserRole.LIVREUR -> "Livreur"
                                        UserRole.ADMIN -> "Admin"
                                    }
                                    NavigationBarItem(
                                        selected = activeTab == NavigationTab.PROFILE,
                                        onClick = { activeTab = NavigationTab.PROFILE },
                                        icon = {
                                            Icon(
                                                imageVector = when (effectiveRole) {
                                                    UserRole.CLIENT -> Icons.Default.Person
                                                    UserRole.VENDEUR -> Icons.Default.Storefront
                                                    UserRole.LIVREUR -> Icons.Default.TwoWheeler
                                                    UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                                },
                                                contentDescription = profileLabel
                                            )
                                        },
                                        label = { Text(profileLabel, fontSize = 11.sp) }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when (activeTab) {
                                    NavigationTab.HOME, NavigationTab.CATALOG -> {
                                        HomeScreen(
                                            products = uiState.filteredProducts,
                                            promotions = activePromotions,
                                            searchQuery = uiState.searchQuery,
                                            onSearchChange = { viewModel.updateSearchQuery(it) },
                                            selectedCategory = uiState.selectedCategory,
                                            onCategorySelect = { viewModel.selectCategory(it) },
                                            useUsd = uiState.useUsd,
                                            cdfRate = cdfRate,
                                            onProductClick = { viewModel.selectProduct(it) },
                                            onAddToCart = { viewModel.addToCart(it) },
                                            onBuyNow = { viewModel.buyNowProduct(it) },
                                            onDeliveryBannerClick = { activeTab = NavigationTab.ORDERS }
                                        )
                                    }
                                    NavigationTab.CART -> {
                                        CartScreen(
                                            cart = uiState.cart,
                                            useUsd = uiState.useUsd,
                                            cdfRate = cdfRate,
                                            onQuantityChange = { prodId, qty -> viewModel.updateCartQuantity(prodId, qty) },
                                            onRemoveItem = { prodId -> viewModel.removeFromCart(prodId) },
                                            onProceedToCheckout = { viewModel.setShowCheckoutSheet(true) },
                                            onExploreClick = { activeTab = NavigationTab.HOME }
                                        )
                                    }
                                    NavigationTab.ORDERS -> {
                                        DeliveryTrackingScreen(
                                            orders = uiState.orders,
                                            selectedOrder = uiState.selectedOrder,
                                            disputes = uiState.orderDisputes,
                                            useUsd = uiState.useUsd,
                                            cdfRate = cdfRate,
                                            onSelectOrder = { viewModel.selectOrder(it) },
                                            onCreateDispute = { ordId, reason -> viewModel.createOrderDispute(ordId, reason) },
                                            onExploreClick = { activeTab = NavigationTab.HOME }
                                        )
                                    }
                                    NavigationTab.PROFILE -> {
                                        when (effectiveRole) {
                                            UserRole.ADMIN -> {
                                                AdminDashboardScreen(
                                                    orders = uiState.orders,
                                                    products = uiState.products,
                                                    allUsers = uiState.allUsers,
                                                    categories = uiState.categories,
                                                    promotions = uiState.promotions,
                                                    vendorWithdrawals = uiState.vendorWithdrawals,
                                                    ownerWalletConfig = uiState.ownerWalletConfig,
                                                    ownerWithdrawals = uiState.ownerWithdrawals,
                                                    orderDisputes = uiState.orderDisputes,
                                                    adminAuditLogs = uiState.adminAuditLogs,
                                                    appSettings = uiState.appSettings,
                                                    totalPlatformCommissionsUsd = uiState.totalPlatformCommissionsUsd,
                                                    ownerAvailableBalanceUsd = uiState.ownerAvailableBalanceUsd,
                                                    ownerTotalWithdrawnUsd = uiState.ownerTotalWithdrawnUsd,
                                                    useUsd = uiState.useUsd,
                                                    onUpdateUserStatus = { uid, st -> viewModel.adminUpdateUserStatus(uid, st) },
                                                    onAddProductClick = { viewModel.setShowAddProductDialog(true, null) },
                                                    onEditProductClick = { viewModel.setShowAddProductDialog(true, it) },
                                                    onModerateProduct = { pid, st, reason -> viewModel.moderateProduct(pid, st, reason) },
                                                    onDeleteProduct = { viewModel.deleteProduct(it) },
                                                    onAddCategory = { name, icon -> viewModel.saveCategory(name, icon) },
                                                    onToggleCategory = { viewModel.toggleCategoryActive(it) },
                                                    onAddPromotion = { t, s, b -> viewModel.savePromotion(t, s, b) },
                                                    onDeletePromotion = { viewModel.deletePromotion(it) },
                                                    onUpdateOrderStatus = { ord, st -> viewModel.updateOrderStatus(ord, st) },
                                                    onAssignDriver = { ordId, drv -> viewModel.adminAssignDriver(ordId, drv) },
                                                    onConfirmOrderPayment = { ordId, ref -> viewModel.confirmOrderPayment(ordId, ref) },
                                                    onSettleDriverPayout = { ordId -> viewModel.settleDriverOrderPayout(ordId) },
                                                    onResolveDispute = { did, st, notes -> viewModel.resolveOrderDispute(did, st, notes) },
                                                    onSaveOwnerWalletConfig = { m, acc, holder -> viewModel.saveOwnerWalletConfig(m, acc, holder) },
                                                    onRequestOwnerWithdrawal = { amt, m, acc -> viewModel.requestOwnerWithdrawal(amt, m, acc) },
                                                    onUpdateVendorWithdrawal = { id, st, reason, verified ->
                                                        viewModel.updateVendorWithdrawalStatus(id, st, reason, verified)
                                                    },
                                                    onUpdateOwnerWithdrawal = { id, st, reason, verified ->
                                                        viewModel.updateOwnerWithdrawalStatus(id, st, reason, verified)
                                                    },
                                                    onSaveAppSettings = { viewModel.saveAppSettings(it) },
                                                    onSignOutClick = onSignOutAction
                                                )
                                            }
                                            UserRole.VENDEUR -> {
                                                VendorDashboardScreen(
                                                    user = user,
                                                    products = uiState.products,
                                                    orders = uiState.orders,
                                                    withdrawals = uiState.vendorWithdrawals,
                                                    useUsd = uiState.useUsd,
                                                    cdfRate = cdfRate,
                                                    commissionPercent = uiState.appSettings.commissionPercent,
                                                    onAddProductClick = { viewModel.setShowAddProductDialog(true, null) },
                                                    onEditProductClick = { viewModel.setShowAddProductDialog(true, it) },
                                                    onDeleteProductClick = { viewModel.deleteProduct(it) },
                                                    onUpdateOrderStatus = { ord, st -> viewModel.updateOrderStatus(ord, st) },
                                                    onRequestWithdrawalClick = { viewModel.setShowWithdrawalDialog(true) },
                                                    onSignOutClick = onSignOutAction
                                                )
                                            }
                                            UserRole.LIVREUR -> {
                                                DriverDashboardScreen(
                                                    user = user,
                                                    orders = uiState.orders,
                                                    availableJobs = uiState.availableDeliveryJobs,
                                                    useUsd = uiState.useUsd,
                                                    cdfRate = cdfRate,
                                                    onAcceptOrder = { viewModel.driverClaimOrder(it) },
                                                    onUpdateStatus = { ord, st -> viewModel.updateOrderStatus(ord, st) },
                                                    onSignOutClick = onSignOutAction
                                                )
                                            }
                                            UserRole.CLIENT -> {
                                                ProfileScreen(
                                                    user = user,
                                                    useUsd = uiState.useUsd,
                                                    cdfRate = cdfRate,
                                                    savedAddresses = uiState.savedAddresses,
                                                    onToggleCurrency = { viewModel.toggleCurrency() },
                                                    onAddNewAddress = { viewModel.setShowAddAddressDialog(true) },
                                                    onSignOutClick = onSignOutAction
                                                )
                                            }
                                        }
                                    }
                                }

                                uiState.selectedProduct?.let { product ->
                                    ProductDetailDialog(
                                        product = product,
                                        useUsd = uiState.useUsd,
                                        cdfRate = cdfRate,
                                        onDismiss = { viewModel.selectProduct(null) },
                                        onAddToCart = { prod, qty -> viewModel.addToCart(prod, qty) },
                                        onBuyNow = { prod, qty -> viewModel.buyNowProduct(prod, qty) }
                                    )
                                }

                                if (uiState.showCheckoutSheet) {
                                    CheckoutDialog(
                                        cart = uiState.cart,
                                        savedAddresses = uiState.savedAddresses,
                                        useUsd = uiState.useUsd,
                                        appSettings = uiState.appSettings,
                                        defaultCustomerName = user.fullName,
                                        defaultCustomerPhone = user.phone,
                                        onDismiss = { viewModel.setShowCheckoutSheet(false) },
                                        onConfirmOrder = { address, paymentMethod, notes ->
                                            viewModel.placeOrder(address, paymentMethod, notes) {
                                                activeTab = NavigationTab.ORDERS
                                            }
                                        }
                                    )
                                }

                                if (uiState.showAddAddressDialog) {
                                    AddAddressDialog(
                                        appSettings = uiState.appSettings,
                                        onDismiss = { viewModel.setShowAddAddressDialog(false) },
                                        onSave = { fullName, phone, type, commune, quartier, avenue, landmark ->
                                            viewModel.addDeliveryAddress(fullName, phone, type, commune, quartier, avenue, landmark)
                                        }
                                    )
                                }

                                if (uiState.showAddProductDialog) {
                                    AddProductDialog(
                                        existingProduct = uiState.editingProduct,
                                        isAdmin = uiState.isAdmin,
                                        onDismiss = { viewModel.setShowAddProductDialog(false, null) },
                                        onSave = { id, name, desc, price, cat, stock, commune, popular, bestOffer, discount, imageUrls ->
                                            viewModel.saveOrUpdateProduct(
                                                existingId = id,
                                                name = name,
                                                description = desc,
                                                priceUsd = price,
                                                category = cat,
                                                stock = stock,
                                                commune = commune,
                                                isPopular = popular,
                                                isBestOffer = bestOffer,
                                                discountPercent = discount,
                                                imageUrls = imageUrls
                                            )
                                        }
                                    )
                                }

                                if (uiState.showWithdrawalDialog) {
                                    val vendorGrossSales = uiState.orders
                                        .filter { it.status == OrderStatus.LIVREE && it.sellerIds.contains(user.userId) }
                                        .sumOf { it.subtotalUsd }
                                    val alreadyReservedOrPaid = uiState.vendorWithdrawals
                                        .filter { it.vendorId == user.userId && it.status != WithdrawalStatus.REFUSE }
                                        .sumOf { it.amountGrossUsd }
                                    val availableBalance = (vendorGrossSales - alreadyReservedOrPaid).coerceAtLeast(0.0)

                                    WithdrawalDialog(
                                        availableBalanceUsd = availableBalance,
                                        commissionPercent = uiState.appSettings.commissionPercent,
                                        cdfRate = cdfRate,
                                        onDismiss = { viewModel.setShowWithdrawalDialog(false) },
                                        onConfirm = { amountUsd, method, details ->
                                            viewModel.requestVendorWithdrawal(amountUsd, method, details)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
