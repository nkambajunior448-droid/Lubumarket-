package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MarketplaceFirestoreRepository
import com.example.data.MarketplaceSeedData
import com.example.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SessionUiState {
    object Loading : SessionUiState()
    object SignedOut : SessionUiState()
    data class PendingOrSuspended(val user: UserProfile) : SessionUiState()
    data class Authenticated(val user: UserProfile, val isAdmin: Boolean) : SessionUiState()
    data class Error(val message: String) : SessionUiState()
}

data class MarketplaceUiState(
    val sessionState: SessionUiState = SessionUiState.Loading,
    val useUsd: Boolean = true,
    val selectedCategory: ProductCategory = ProductCategory.TOUS,
    val searchQuery: String = "",
    val products: List<Product> = emptyList(),
    val categories: List<CategoryConfig> = emptyList(),
    val promotions: List<PromotionBanner> = emptyList(),
    val appSettings: AppConfigSettings = AppConfigSettings(),
    val cart: List<CartItem> = emptyList(),
    val savedAddresses: List<DeliveryAddress> = emptyList(),
    val orders: List<Order> = emptyList(),
    val availableDeliveryJobs: List<DeliveryJob> = emptyList(),
    val vendorWithdrawals: List<WithdrawalRequest> = emptyList(),
    val allUsers: List<UserProfile> = emptyList(),
    val ownerWalletConfig: OwnerWalletConfig? = null,
    val ownerWithdrawals: List<OwnerWithdrawalRequest> = emptyList(),
    val orderDisputes: List<OrderDispute> = emptyList(),
    val adminAuditLogs: List<AdminAuditLog> = emptyList(),
    val pendingRegistration: PendingRegistrationData? = null,
    val selectedProduct: Product? = null,
    val selectedOrder: Order? = null,
    val showCheckoutSheet: Boolean = false,
    val showAddAddressDialog: Boolean = false,
    val showAddProductDialog: Boolean = false,
    val editingProduct: Product? = null,
    val showWithdrawalDialog: Boolean = false,
    val adminAuthError: String? = null,
    val isAdminAuthFlow: Boolean = false,
    val isAdminSignUpInProgress: Boolean = false,
    val superAdminProfile: SuperAdminProfile? = null,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null
) {
    val activeUser: UserProfile?
        get() = when (sessionState) {
            is SessionUiState.Authenticated -> sessionState.user
            is SessionUiState.PendingOrSuspended -> sessionState.user
            else -> null
        }

    val isAdmin: Boolean
        get() = sessionState is SessionUiState.Authenticated && sessionState.isAdmin

    val publicApprovedProducts: List<Product>
        get() = products.filter { it.moderationStatus == ProductModerationStatus.APPROVED }

    val filteredProducts: List<Product>
        get() = publicApprovedProducts.filter { prod ->
            val matchesCat = selectedCategory == ProductCategory.TOUS || prod.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                prod.name.contains(searchQuery, ignoreCase = true) ||
                prod.description.contains(searchQuery, ignoreCase = true) ||
                prod.sellerName.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }

    val cdfRate: Double get() = appSettings.cdfExchangeRate

    // 5% commission credited ONCE ONLY per delivered AND real-payment-confirmed order
    val totalPlatformCommissionsUsd: Double
        get() = orders
            .filter { it.status == OrderStatus.LIVREE && it.paymentConfirmed && it.commissionCredited }
            .sumOf { it.commissionUsd }

    val ownerReservedOrPaidUsd: Double
        get() = ownerWithdrawals
            .filter { it.status != WithdrawalStatus.REFUSE }
            .sumOf { it.amountUsd }

    val ownerTotalWithdrawnUsd: Double
        get() = ownerWithdrawals
            .filter { it.status == WithdrawalStatus.PAYE }
            .sumOf { it.amountUsd }

    val ownerAvailableBalanceUsd: Double
        get() = (totalPlatformCommissionsUsd - ownerReservedOrPaidUsd).coerceAtLeast(0.0)
}

class MarketplaceViewModel(
    private val repository: MarketplaceFirestoreRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketplaceUiState())
    val uiState: StateFlow<MarketplaceUiState> = _uiState.asStateFlow()

    private var roleJobs = mutableListOf<Job>()
    private var profileObserverJob: Job? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        onAuthUserChanged(firebaseAuth.currentUser)
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        cancelRoleListeners()
        profileObserverJob?.cancel()
    }

    fun setPendingRegistration(data: PendingRegistrationData?) {
        _uiState.update { it.copy(pendingRegistration = data) }
    }

    fun clearAdminAuthError() {
        _uiState.update { it.copy(adminAuthError = null) }
    }

    fun setAdminAuthFlow(active: Boolean, isSignUp: Boolean = false) {
        _uiState.update {
            it.copy(
                isAdminAuthFlow = active,
                isAdminSignUpInProgress = active && isSignUp
            )
        }
    }

    fun onSignedInFromUi() {
        onAuthUserChanged(auth.currentUser)
    }

    fun verifyPrincipalAdminSignIn(onDenied: (String) -> Unit, onVerified: () -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onDenied("Authentification annulée ou session introuvable.")
            return
        }
        viewModelScope.launch {
            val isAdmin = repository.checkIsAdmin(currentUser)
            if (!isAdmin) {
                val identifier = currentUser.email?.takeIf { it.isNotBlank() }
                    ?: currentUser.phoneNumber?.takeIf { it.isNotBlank() }
                    ?: currentUser.uid
                val msg = "Accès refusé : le compte ($identifier) n'est pas autorisé comme Super Administrateur."
                auth.signOut()
                _uiState.update {
                    it.copy(
                        sessionState = SessionUiState.SignedOut,
                        adminAuthError = msg
                    )
                }
                onDenied(msg)
            } else {
                _uiState.update {
                    it.copy(
                        adminAuthError = null,
                        isAdminAuthFlow = false,
                        isAdminSignUpInProgress = false
                    )
                }
                repository.initializePrincipalAdmin(currentUser)
                onAuthUserChanged(currentUser)
                onVerified()
            }
        }
    }

    fun completeSuperAdminRegistration(
        fullName: String,
        email: String,
        phone: String?,
        authMethod: AdminAuthMethod,
        targetAdminUid: String? = null,
        onError: (String) -> Unit,
        onSuccess: () -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onError("Veuillez d'abord vérifier votre identité Gmail propriétaire.")
            return
        }
        viewModelScope.launch {
            val res = repository.registerSuperAdminProfile(
                fullName = fullName,
                email = email,
                phone = phone,
                authMethod = authMethod,
                targetAdminUid = targetAdminUid
            )
            res.fold(
                onSuccess = { superProfile ->
                    _uiState.update {
                        it.copy(
                            superAdminProfile = superProfile,
                            adminAuthError = null,
                            isAdminAuthFlow = false,
                            isAdminSignUpInProgress = false
                        )
                    }
                    onAuthUserChanged(currentUser)
                    onSuccess()
                },
                onFailure = { err ->
                    onError(err.localizedMessage ?: "Échec de l'enregistrement Super Administrateur.")
                }
            )
        }
    }

    private fun onAuthUserChanged(user: FirebaseUser?) {
        cancelRoleListeners()
        profileObserverJob?.cancel()

        if (user == null) {
            _uiState.update {
                MarketplaceUiState(
                    sessionState = SessionUiState.SignedOut,
                    products = MarketplaceSeedData.starterProducts,
                    categories = MarketplaceSeedData.starterCategories,
                    promotions = MarketplaceSeedData.starterPromotions,
                    adminAuthError = it.adminAuthError,
                    isAdminAuthFlow = it.isAdminAuthFlow,
                    isAdminSignUpInProgress = it.isAdminSignUpInProgress
                )
            }
            return
        }

        // While inside the multi-step Super Admin Sign-Up wizard (e.g. verifying Gmail then linking SMS OTP),
        // keep the user on the wizard until completeSuperAdminRegistration() finishes.
        if (_uiState.value.isAdminSignUpInProgress) {
            return
        }

        _uiState.update { it.copy(sessionState = SessionUiState.Loading) }

        viewModelScope.launch {
            val isAdmin = repository.checkIsAdmin(user)
            if (isAdmin) {
                repository.initializePrincipalAdmin(user)
                val storedAdmin = repository.getSuperAdminProfile(user.uid)
                val adminProfile = UserProfile(
                    id = user.uid,
                    userId = user.uid,
                    fullName = storedAdmin?.fullName?.ifBlank { user.displayName ?: "Propriétaire LUBUMARKET" }
                        ?: (user.displayName ?: "Propriétaire LUBUMARKET"),
                    phone = storedAdmin?.phone?.ifBlank { user.phoneNumber ?: "+243 990 000 001" }
                        ?: (user.phoneNumber ?: "+243 990 000 001"),
                    email = storedAdmin?.email?.ifBlank { user.email ?: MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL }
                        ?: (user.email ?: MarketplaceFirestoreRepository.OWNER_ADMIN_EMAIL),
                    roleName = UserRole.ADMIN.name,
                    statusName = AccountStatus.APPROVED.name,
                    commune = "Lubumbashi",
                    quartier = "Centre-Ville"
                )
                _uiState.update {
                    it.copy(
                        sessionState = SessionUiState.Authenticated(adminProfile, isAdmin = true),
                        superAdminProfile = storedAdmin,
                        pendingRegistration = null,
                        adminAuthError = null,
                        isAdminAuthFlow = false,
                        isAdminSignUpInProgress = false
                    )
                }
                startRoleListeners(adminProfile, isAdmin = true)
                return@launch
            }

            // CRITICAL SECURITY GUARD:
            // If an unauthorized user attempts to log in from the Admin screen, NEVER auto-create a Client profile.
            if (_uiState.value.isAdminAuthFlow) {
                _uiState.update { it.copy(sessionState = SessionUiState.SignedOut) }
                return@launch
            }

            val pending = _uiState.value.pendingRegistration
            val existing = repository.getUserProfileOnce(user.uid)
            if (existing == null) {
                val regResult = if (pending != null) {
                    repository.registerUserProfile(pending)
                } else {
                    repository.ensureDefaultClientProfile(user)
                }
                regResult.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            sessionState = SessionUiState.Error(
                                err.localizedMessage ?: "Erreur lors de la création du profil."
                            )
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(pendingRegistration = null) }
            } else {
                _uiState.update { it.copy(pendingRegistration = null) }
            }

            profileObserverJob = viewModelScope.launch {
                repository.observeUserProfile(user.uid)
                    .catch { err ->
                        _uiState.update {
                            it.copy(
                                sessionState = SessionUiState.Error(
                                    err.localizedMessage ?: "Erreur de chargement du profil."
                                )
                            )
                        }
                    }
                    .collect { profile ->
                        if (profile == null) return@collect
                        if (profile.accountStatus != AccountStatus.APPROVED) {
                            cancelRoleListeners()
                            _uiState.update {
                                it.copy(sessionState = SessionUiState.PendingOrSuspended(profile))
                            }
                        } else {
                            val currentAuthed = (_uiState.value.sessionState as? SessionUiState.Authenticated)?.user
                            val roleChanged = currentAuthed?.role != profile.role || currentAuthed.userId != profile.userId
                            _uiState.update {
                                it.copy(sessionState = SessionUiState.Authenticated(profile, isAdmin = false))
                            }
                            if (roleChanged || roleJobs.isEmpty()) {
                                cancelRoleListeners()
                                startRoleListeners(profile, isAdmin = false)
                            }
                        }
                    }
            }
        }
    }

    private fun cancelRoleListeners() {
        roleJobs.forEach { it.cancel() }
        roleJobs.clear()
    }

    private fun startRoleListeners(user: UserProfile, isAdmin: Boolean) {
        roleJobs += viewModelScope.launch {
            repository.observeProducts()
                .catch { /* logged by repo */ }
                .collect { list ->
                    _uiState.update { it.copy(products = list) }
                }
        }

        roleJobs += viewModelScope.launch {
            repository.observeCategories()
                .catch { }
                .collect { cats ->
                    _uiState.update { it.copy(categories = cats) }
                }
        }

        roleJobs += viewModelScope.launch {
            repository.observePromotions()
                .catch { }
                .collect { promos ->
                    _uiState.update { it.copy(promotions = promos) }
                }
        }

        roleJobs += viewModelScope.launch {
            repository.observeAppSettings()
                .catch { }
                .collect { settings ->
                    _uiState.update { it.copy(appSettings = settings) }
                }
        }

        roleJobs += viewModelScope.launch {
            repository.observeSavedAddresses(user.userId)
                .catch { }
                .collect { addrs ->
                    _uiState.update { it.copy(savedAddresses = addrs) }
                }
        }

        if (isAdmin) {
            roleJobs += viewModelScope.launch {
                repository.observeAllUsersForAdmin()
                    .catch { }
                    .collect { users ->
                        _uiState.update { it.copy(allUsers = users) }
                    }
            }
            roleJobs += viewModelScope.launch {
                repository.observeOrdersForAdmin()
                    .catch { }
                    .collect { list ->
                        _uiState.update { it.copy(orders = list) }
                    }
            }
            roleJobs += viewModelScope.launch {
                repository.observeVendorWithdrawals(user.userId, isAdmin = true)
                    .catch { }
                    .collect { list ->
                        _uiState.update { it.copy(vendorWithdrawals = list) }
                    }
            }
            roleJobs += viewModelScope.launch {
                repository.observeOwnerWalletConfig()
                    .catch { }
                    .collect { cfg ->
                        _uiState.update { it.copy(ownerWalletConfig = cfg) }
                    }
            }
            roleJobs += viewModelScope.launch {
                repository.observeOwnerWithdrawals()
                    .catch { }
                    .collect { list ->
                        _uiState.update { it.copy(ownerWithdrawals = list) }
                    }
            }
            roleJobs += viewModelScope.launch {
                repository.observeOrderDisputes(user.userId, isAdmin = true)
                    .catch { }
                    .collect { list ->
                        _uiState.update { it.copy(orderDisputes = list) }
                    }
            }
            roleJobs += viewModelScope.launch {
                repository.observeAdminAuditLogs()
                    .catch { }
                    .collect { list ->
                        _uiState.update { it.copy(adminAuditLogs = list) }
                    }
            }
        } else {
            when (user.role) {
                UserRole.CLIENT -> {
                    roleJobs += viewModelScope.launch {
                        repository.observeCustomerOrders(user.userId)
                            .catch { }
                            .collect { list ->
                                _uiState.update { state ->
                                    val refreshedSelected = state.selectedOrder?.let { sel ->
                                        list.firstOrNull { it.id == sel.id } ?: sel
                                    }
                                    state.copy(orders = list, selectedOrder = refreshedSelected)
                                }
                            }
                    }
                    roleJobs += viewModelScope.launch {
                        repository.observeOrderDisputes(user.userId, isAdmin = false)
                            .catch { }
                            .collect { list ->
                                _uiState.update { it.copy(orderDisputes = list) }
                            }
                    }
                }
                UserRole.VENDEUR -> {
                    roleJobs += viewModelScope.launch {
                        repository.observeVendorOrders(user.userId)
                            .catch { }
                            .collect { list ->
                                _uiState.update { it.copy(orders = list) }
                            }
                    }
                    roleJobs += viewModelScope.launch {
                        repository.observeVendorWithdrawals(user.userId, isAdmin = false)
                            .catch { }
                            .collect { list ->
                                _uiState.update { it.copy(vendorWithdrawals = list) }
                            }
                    }
                }
                UserRole.LIVREUR -> {
                    roleJobs += viewModelScope.launch {
                        repository.observeDriverAssignedOrders(user.userId)
                            .catch { }
                            .collect { list ->
                                _uiState.update { it.copy(orders = list) }
                            }
                    }
                    roleJobs += viewModelScope.launch {
                        repository.observeAvailableDeliveryJobs()
                            .catch { }
                            .collect { jobs ->
                                _uiState.update { it.copy(availableDeliveryJobs = jobs) }
                            }
                    }
                }
                UserRole.ADMIN -> {}
            }
        }
    }

    // --- UI Actions ---
    fun toggleCurrency() {
        _uiState.update { it.copy(useUsd = !it.useUsd) }
    }

    fun selectCategory(category: ProductCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectProduct(product: Product?) {
        _uiState.update { it.copy(selectedProduct = product) }
    }

    fun selectOrder(order: Order?) {
        _uiState.update { it.copy(selectedOrder = order) }
    }

    fun addToCart(product: Product, quantity: Int = 1) {
        val currentCart = _uiState.value.cart.toMutableList()
        val existingIndex = currentCart.indexOfFirst { it.product.id == product.id }
        if (existingIndex >= 0) {
            val item = currentCart[existingIndex]
            currentCart[existingIndex] = item.copy(quantity = item.quantity + quantity)
        } else {
            currentCart.add(CartItem(product, quantity))
        }
        _uiState.update {
            it.copy(
                cart = currentCart,
                statusMessage = "${product.name} ajouté au panier !"
            )
        }
    }

    fun buyNowProduct(product: Product, quantity: Int = 1) {
        val currentCart = _uiState.value.cart.toMutableList()
        val existingIndex = currentCart.indexOfFirst { it.product.id == product.id }
        if (existingIndex >= 0) {
            val item = currentCart[existingIndex]
            currentCart[existingIndex] = item.copy(quantity = item.quantity + quantity)
        } else {
            currentCart.add(CartItem(product, quantity))
        }
        _uiState.update {
            it.copy(
                cart = currentCart,
                selectedProduct = null,
                showCheckoutSheet = true
            )
        }
    }

    fun updateCartQuantity(productId: String, newQty: Int) {
        val currentCart = _uiState.value.cart.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == productId }
        if (idx >= 0) {
            if (newQty <= 0) currentCart.removeAt(idx)
            else currentCart[idx] = currentCart[idx].copy(quantity = newQty)
            _uiState.update { it.copy(cart = currentCart) }
        }
    }

    fun removeFromCart(productId: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.product.id == productId })
        }
    }

    fun setShowCheckoutSheet(show: Boolean) {
        _uiState.update { it.copy(showCheckoutSheet = show) }
    }

    fun setShowAddAddressDialog(show: Boolean) {
        _uiState.update { it.copy(showAddAddressDialog = show) }
    }

    fun setShowAddProductDialog(show: Boolean, productToEdit: Product? = null) {
        _uiState.update { it.copy(showAddProductDialog = show, editingProduct = productToEdit) }
    }

    fun setShowWithdrawalDialog(show: Boolean) {
        _uiState.update { it.copy(showWithdrawalDialog = show) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // --- Persistence Actions ---
    fun addDeliveryAddress(
        fullName: String,
        phone: String,
        type: DeliveryType,
        commune: CommuneLubumbashi,
        quartier: String,
        avenue: String,
        landmark: String
    ) {
        val uid = _uiState.value.activeUser?.userId ?: return
        viewModelScope.launch {
            val res = repository.addDeliveryAddress(
                uid,
                DeliveryAddress(
                    fullName = fullName,
                    phone = phone,
                    type = type,
                    commune = commune,
                    quartier = quartier,
                    avenue = avenue,
                    referenceLandmark = landmark
                )
            )
            _uiState.update {
                it.copy(
                    showAddAddressDialog = false,
                    statusMessage = if (res.isSuccess) "Adresse à Lubumbashi enregistrée !"
                    else "Erreur d'enregistrement : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun placeOrder(
        address: DeliveryAddress,
        paymentMethod: PaymentMethod,
        notes: String?,
        onOrderSuccess: (Order) -> Unit
    ) {
        val state = _uiState.value
        val items = state.cart
        if (items.isEmpty()) return
        val deliveryFee = state.appSettings.feeFor(address.commune)
        val commissionPercent = state.appSettings.commissionPercent

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = repository.placeOrder(
                address = address,
                cartItems = items,
                deliveryFeeUsd = deliveryFee,
                commissionPercent = commissionPercent,
                paymentMethod = paymentMethod,
                notes = notes
            )
            result.fold(
                onSuccess = { order ->
                    _uiState.update {
                        it.copy(
                            cart = emptyList(),
                            showCheckoutSheet = false,
                            selectedOrder = order,
                            isSubmitting = false,
                            statusMessage = "Commande ${order.id} enregistrée ! Livraison prévue en ${address.commune.estimatedTime}."
                        )
                    }
                    onOrderSuccess(order)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            statusMessage = "Échec de commande : ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun saveOrUpdateProduct(
        existingId: String?,
        name: String,
        description: String,
        priceUsd: Double,
        category: ProductCategory,
        stock: Int,
        commune: String,
        isPopular: Boolean,
        isBestOffer: Boolean,
        discountPercent: Int,
        imageUrls: List<String>
    ) {
        val user = _uiState.value.activeUser ?: return
        val isAdmin = _uiState.value.isAdmin
        viewModelScope.launch {
            val result = if (existingId.isNullOrBlank()) {
                repository.saveProduct(
                    Product(
                        name = name,
                        description = description,
                        priceUsd = priceUsd,
                        category = category,
                        categoryName = category.label,
                        sellerId = user.userId,
                        sellerName = user.storeName?.ifBlank { user.fullName } ?: user.fullName,
                        sellerCommune = commune,
                        stock = stock,
                        isPopular = isPopular,
                        isBestOffer = isBestOffer,
                        discountPercent = discountPercent,
                        isNew = true,
                        imageUrls = imageUrls,
                        moderationStatusName = if (isAdmin) ProductModerationStatus.APPROVED.name else ProductModerationStatus.PENDING.name
                    ),
                    isAdmin = isAdmin
                )
            } else {
                val existing = _uiState.value.products.firstOrNull { it.id == existingId }
                repository.updateProduct(
                    Product(
                        id = existingId,
                        name = name,
                        description = description,
                        priceUsd = priceUsd,
                        category = category,
                        categoryName = category.label,
                        sellerId = existing?.sellerId ?: user.userId,
                        sellerName = existing?.sellerName ?: (user.storeName?.ifBlank { user.fullName } ?: user.fullName),
                        sellerCommune = commune,
                        stock = stock,
                        isPopular = isPopular,
                        isBestOffer = isBestOffer,
                        discountPercent = discountPercent,
                        isNew = existing?.isNew ?: false,
                        imageUrls = imageUrls,
                        moderationStatusName = if (isAdmin) (existing?.moderationStatusName ?: ProductModerationStatus.APPROVED.name) else ProductModerationStatus.PENDING.name,
                        moderationReason = existing?.moderationReason
                    ),
                    isAdmin = isAdmin
                )
            }
            _uiState.update {
                it.copy(
                    showAddProductDialog = false,
                    editingProduct = null,
                    statusMessage = if (result.isSuccess) {
                        if (isAdmin) "Produit publié dans le catalogue !" else "Produit envoyé pour validation administrateur !"
                    } else "Erreur : ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun moderateProduct(productId: String, status: ProductModerationStatus, reason: String?) {
        viewModelScope.launch {
            val res = repository.moderateProduct(productId, status, reason)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Produit mis à jour : ${status.label}"
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun deleteProduct(productId: String) {
        val isAdmin = _uiState.value.isAdmin
        viewModelScope.launch {
            val res = repository.deleteProduct(productId, isAdmin = isAdmin)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Produit supprimé."
                    else "Erreur de suppression : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun requestVendorWithdrawal(amountGrossUsd: Double, method: PaymentMethod, accountDetails: String) {
        val user = _uiState.value.activeUser ?: return
        val state = _uiState.value
        val vendorOrders = state.orders.filter { it.status == OrderStatus.LIVREE && it.sellerIds.contains(user.userId) }
        val grossSales = vendorOrders.sumOf { it.subtotalUsd }
        val alreadyReservedOrPaid = state.vendorWithdrawals
            .filter { it.vendorId == user.userId && it.status != WithdrawalStatus.REFUSE }
            .sumOf { it.amountGrossUsd }
        val availableBalance = (grossSales - alreadyReservedOrPaid).coerceAtLeast(0.0)

        viewModelScope.launch {
            val res = repository.createVendorWithdrawal(
                vendor = user,
                amountGrossUsd = amountGrossUsd,
                commissionPercent = state.appSettings.commissionPercent,
                paymentMethod = method,
                accountDetails = accountDetails,
                availableBalanceUsd = availableBalance
            )
            _uiState.update {
                it.copy(
                    showWithdrawalDialog = false,
                    statusMessage = if (res.isSuccess) "Demande de retrait soumise pour vérification."
                    else "Retrait refusé : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun driverClaimOrder(orderId: String) {
        val driver = _uiState.value.activeUser ?: return
        viewModelScope.launch {
            val res = repository.driverClaimOrder(orderId, driver)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Course $orderId assignée !"
                    else "Impossible de prendre la course : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun updateOrderStatus(order: Order, newStatus: OrderStatus) {
        val isAdmin = _uiState.value.isAdmin
        viewModelScope.launch {
            val res = repository.updateOrderStatus(order, newStatus, isAdmin = isAdmin)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Commande ${order.id} : ${newStatus.label}"
                    else "Erreur de mise à jour : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun confirmOrderPayment(orderId: String, paymentReference: String) {
        viewModelScope.launch {
            val res = repository.confirmOrderPayment(orderId, paymentReference)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Paiement réel confirmé & commission 5% créditée !"
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun settleDriverOrderPayout(orderId: String) {
        viewModelScope.launch {
            val res = repository.settleDriverOrderPayout(orderId)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Règlement livreur confirmé pour $orderId."
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun createOrderDispute(orderId: String, reason: String) {
        val user = _uiState.value.activeUser ?: return
        viewModelScope.launch {
            val res = repository.createOrderDispute(orderId, user.fullName, reason)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Réclamation envoyée à l'administration."
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun resolveOrderDispute(disputeId: String, status: DisputeStatus, adminNotes: String?) {
        viewModelScope.launch {
            val res = repository.resolveOrderDispute(disputeId, status, adminNotes)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Litige mis à jour (${status.label})."
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    // --- Admin Actions ---
    fun adminUpdateUserStatus(userId: String, newStatus: AccountStatus) {
        viewModelScope.launch {
            val res = repository.updateUserAccountStatus(userId, newStatus)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Statut du compte mis à jour : ${newStatus.label}"
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun adminAssignDriver(orderId: String, driver: UserProfile) {
        viewModelScope.launch {
            val res = repository.adminAssignDriver(orderId, driver)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Livreur ${driver.fullName} assigné à $orderId."
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun saveOwnerWalletConfig(method: PaymentMethod, accountDetails: String, holderName: String) {
        val uid = _uiState.value.activeUser?.userId ?: return
        viewModelScope.launch {
            val res = repository.saveOwnerWalletConfig(
                OwnerWalletConfig(
                    ownerId = uid,
                    preferredMethod = method,
                    accountDetails = accountDetails,
                    accountHolderName = holderName
                )
            )
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Coordonnées de réception enregistrées !"
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun requestOwnerWithdrawal(amountUsd: Double, method: PaymentMethod, accountDetails: String) {
        val available = _uiState.value.ownerAvailableBalanceUsd
        viewModelScope.launch {
            val res = repository.createOwnerWithdrawal(
                amountUsd = amountUsd,
                paymentMethod = method,
                accountDetails = accountDetails,
                availableOwnerBalanceUsd = available
            )
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Demande de retrait propriétaire soumise."
                    else "Refusé : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun updateVendorWithdrawalStatus(
        withdrawalId: String,
        newStatus: WithdrawalStatus,
        reason: String?,
        verifiedConfirmed: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.updateVendorWithdrawalStatus(withdrawalId, newStatus, reason, verifiedConfirmed)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Retrait vendeur $withdrawalId : ${newStatus.label}"
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun updateOwnerWithdrawalStatus(
        withdrawalId: String,
        newStatus: WithdrawalStatus,
        reason: String?,
        verifiedConfirmed: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.updateOwnerWithdrawalStatus(withdrawalId, newStatus, reason, verifiedConfirmed)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) "Retrait propriétaire $withdrawalId : ${newStatus.label}"
                    else "Erreur : ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun saveCategory(name: String, iconName: String) {
        viewModelScope.launch {
            val order = (_uiState.value.categories.maxOfOrNull { it.displayOrder } ?: 0) + 1
            val res = repository.saveCategory(CategoryConfig(name = name, iconName = iconName, isActive = true, displayOrder = order))
            _uiState.update {
                it.copy(statusMessage = if (res.isSuccess) "Catégorie « $name » ajoutée." else "Erreur : ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun toggleCategoryActive(category: CategoryConfig) {
        viewModelScope.launch {
            repository.toggleCategoryActive(category)
        }
    }

    fun savePromotion(title: String, subtitle: String, badgeText: String) {
        viewModelScope.launch {
            val res = repository.savePromotion(PromotionBanner(title = title, subtitle = subtitle, badgeText = badgeText, isActive = true))
            _uiState.update {
                it.copy(statusMessage = if (res.isSuccess) "Bannière promotionnelle publiée." else "Erreur : ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun deletePromotion(promoId: String) {
        viewModelScope.launch {
            repository.deletePromotion(promoId)
        }
    }

    fun saveAppSettings(settings: AppConfigSettings) {
        viewModelScope.launch {
            val res = repository.saveAppSettings(settings)
            _uiState.update {
                it.copy(statusMessage = if (res.isSuccess) "Paramètres Lubumarket enregistrés." else "Erreur : ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    class Factory(private val repository: MarketplaceFirestoreRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MarketplaceViewModel(repository) as T
        }
    }
}
