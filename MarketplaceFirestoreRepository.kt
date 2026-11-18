package com.example.data

import android.content.Context
import com.example.R
import com.example.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class MarketplaceFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val OWNER_ADMIN_EMAIL = "nkambajunior448@gmail.com"
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Utilisateur non authentifié. Connexion Google requise.")
    }

    fun isVerifiedOwnerAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        return user.email?.equals(OWNER_ADMIN_EMAIL, ignoreCase = true) == true && user.isEmailVerified
    }

    suspend fun checkIsAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        if (isVerifiedOwnerAdmin(user)) return true
        val path = "admins/${user.uid}"
        return try {
            val doc = db.collection("admins").document(user.uid).get().await()
            doc.exists() && (doc.getBoolean("isActive") != false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            false
        }
    }

    suspend fun getSuperAdminProfile(uid: String): SuperAdminProfile? {
        val path = "admins/$uid"
        return try {
            val doc = db.collection("admins").document(uid).get().await()
            if (!doc.exists()) return null
            doc.toObject(SuperAdminProfile::class.java)?.copy(id = doc.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun registerSuperAdminProfile(
        fullName: String,
        email: String,
        phone: String?,
        authMethod: AdminAuthMethod,
        targetAdminUid: String? = null
    ): Result<SuperAdminProfile> {
        val currentUser = auth.currentUser
            ?: return Result.failure(SecurityException("Authentification requise."))
        if (!isVerifiedOwnerAdmin(currentUser)) {
            return Result.failure(
                SecurityException("Seul le propriétaire principal vérifié ($OWNER_ADMIN_EMAIL) peut créer ou autoriser un compte Super Administrateur.")
            )
        }
        val uidToRegister = targetAdminUid?.takeIf { it.isNotBlank() } ?: currentUser.uid
        val docRef = db.collection("admins").document(uidToRegister)
        return try {
            val existing = docRef.get().await()
            val cleanName = fullName.trim().ifBlank { "Propriétaire LUBUMARKET" }
            val cleanEmail = email.trim().ifBlank { currentUser.email ?: OWNER_ADMIN_EMAIL }
            val cleanPhone = phone?.trim()?.takeIf { it.isNotBlank() }
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "adminId" to uidToRegister,
                    "fullName" to cleanName,
                    "email" to cleanEmail,
                    "role" to "SUPER_ADMIN",
                    "authMethod" to authMethod.name,
                    "isActive" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPhone != null) updates["phone"] = cleanPhone
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "adminId" to uidToRegister,
                    "fullName" to cleanName,
                    "email" to cleanEmail,
                    "role" to "SUPER_ADMIN",
                    "authMethod" to authMethod.name,
                    "isActive" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPhone != null) payload["phone"] = cleanPhone
                docRef.set(payload).await()
            }
            seedInitialMarketplaceIfEmpty()
            recordAdminAuditLog(
                action = "SUPER_ADMIN_REGISTERED",
                targetType = "ADMIN",
                targetId = uidToRegister,
                details = "Compte Super Administrateur enregistré ($cleanName • ${authMethod.label})"
            )
            Result.success(
                SuperAdminProfile(
                    id = uidToRegister,
                    adminId = uidToRegister,
                    fullName = cleanName,
                    email = cleanEmail,
                    phone = cleanPhone,
                    role = "SUPER_ADMIN",
                    authMethod = authMethod.name,
                    isActive = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun initializePrincipalAdmin(user: FirebaseUser): Result<Unit> {
        if (!checkIsAdmin(user)) {
            return Result.failure(SecurityException("Accès administrateur principal refusé."))
        }
        return try {
            seedInitialMarketplaceIfEmpty()
            recordAdminAuditLog(
                action = "ADMIN_SESSION_INIT",
                targetType = "SYSTEM",
                targetId = user.uid,
                details = "Connexion et initialisation du panneau administrateur principal (${user.email ?: user.phoneNumber ?: OWNER_ADMIN_EMAIL})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Admin Audit Trail ---
    suspend fun recordAdminAuditLog(
        action: String,
        targetType: String,
        targetId: String,
        details: String
    ) {
        val currentUser = auth.currentUser ?: return
        val logId = "log_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val docRef = db.collection("admin_audit_logs").document(logId)
        val payload = mapOf(
            "adminId" to currentUser.uid,
            "adminEmail" to (currentUser.email ?: OWNER_ADMIN_EMAIL),
            "action" to action.take(80),
            "targetType" to targetType.take(60),
            "targetId" to targetId.take(128),
            "details" to details.take(500),
            "createdAt" to FieldValue.serverTimestamp()
        )
        runCatching {
            docRef.set(payload).await()
        }.onFailure { err ->
            if (err is CancellationException) throw err
            handleFirestoreError(err, OperationType.CREATE, docRef.path)
        }
    }

    fun observeAdminAuditLogs(): Flow<List<AdminAuditLog>> {
        val uid = auth.currentUser?.uid
        return db.collection("admin_audit_logs")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    val ts = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    val dateStr = ts?.toDate()?.let {
                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.FRENCH).format(it)
                    } ?: ""
                    AdminAuditLog(
                        id = doc.id,
                        adminId = doc.getString("adminId") ?: "",
                        adminEmail = doc.getString("adminEmail") ?: "",
                        action = doc.getString("action") ?: "",
                        targetType = doc.getString("targetType") ?: "",
                        targetId = doc.getString("targetId") ?: "",
                        details = doc.getString("details") ?: "",
                        createdAt = dateStr
                    )
                }.reversed()
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "admin_audit_logs", uid)
                throw error
            }
    }

    // --- User Profile & Registration ---
    fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val path = "users/$uid"
        return db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshotToUserProfile(snapshot) else null
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, path, uid)
                throw error
            }
    }

    suspend fun getUserProfileOnce(uid: String): UserProfile? {
        val path = "users/$uid"
        return try {
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) snapshotToUserProfile(doc) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun registerUserProfile(registration: PendingRegistrationData): Result<UserProfile> {
        return try {
            val uid = requireUserId()
            val firebaseUser = auth.currentUser!!
            val safeRole = when (registration.role) {
                UserRole.VENDEUR -> UserRole.VENDEUR
                UserRole.LIVREUR -> UserRole.LIVREUR
                else -> UserRole.CLIENT // Never allow ADMIN via public registration
            }
            val initialStatus = if (safeRole == UserRole.CLIENT) AccountStatus.APPROVED else AccountStatus.PENDING
            val email = firebaseUser.email?.ifBlank { registration.emailHint } ?: registration.emailHint

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "fullName" to registration.fullName.trim(),
                "phone" to registration.phone.trim(),
                "email" to email.trim(),
                "role" to safeRole.name,
                "accountStatus" to initialStatus.name,
                "commune" to registration.commune.trim(),
                "quartier" to registration.quartier.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!registration.storeName.isNullOrBlank()) {
                payload["storeName"] = registration.storeName.trim()
            }
            if (!registration.vehicleType.isNullOrBlank()) {
                payload["vehicleType"] = registration.vehicleType.trim()
            }
            if (!registration.deliveryZone.isNullOrBlank()) {
                payload["deliveryZone"] = registration.deliveryZone.trim()
            }

            val docRef = db.collection("users").document(uid)
            docRef.set(payload).await()
            val profile = UserProfile(
                id = uid,
                userId = uid,
                fullName = registration.fullName.trim(),
                phone = registration.phone.trim(),
                email = email.trim(),
                roleName = safeRole.name,
                statusName = initialStatus.name,
                commune = registration.commune.trim(),
                quartier = registration.quartier.trim(),
                storeName = registration.storeName?.trim(),
                vehicleType = registration.vehicleType?.trim(),
                deliveryZone = registration.deliveryZone?.trim()
            )
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}")
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultClientProfile(firebaseUser: FirebaseUser): Result<UserProfile> {
        val existing = getUserProfileOnce(firebaseUser.uid)
        if (existing != null) return Result.success(existing)
        return registerUserProfile(
            PendingRegistrationData(
                role = UserRole.CLIENT,
                fullName = firebaseUser.displayName?.ifBlank { "Client Lubumarket" } ?: "Client Lubumarket",
                phone = firebaseUser.phoneNumber?.ifBlank { "+243 990 000 000" } ?: "+243 990 000 000",
                emailHint = firebaseUser.email ?: "client@lubumarket.cd",
                commune = "Lubumbashi",
                quartier = "Centre-Ville"
            )
        )
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> {
        val uid = auth.currentUser?.uid
        val path = "users"
        return db.collection(path)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { snapshotToUserProfile(it) } }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun updateUserAccountStatus(targetUserId: String, newStatus: AccountStatus): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            requireUserId()
            db.collection("users").document(targetUserId)
                .update(
                    mapOf(
                        "accountStatus" to newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            recordAdminAuditLog(
                action = "USER_STATUS_${newStatus.name}",
                targetType = "USER",
                targetId = targetUserId,
                details = "Statut du compte modifié en ${newStatus.label}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Products, Moderation, Categories, Promotions, App Settings ---
    fun observeProducts(): Flow<List<Product>> {
        val uid = auth.currentUser?.uid
        return db.collection("products")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { snapshotToProduct(it) } }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "products", uid)
                throw error
            }
    }

    suspend fun saveProduct(product: Product, isAdmin: Boolean = false): Result<String> {
        val uid = requireUserId()
        val prodId = product.id.ifBlank { "prod_${UUID.randomUUID().toString().take(12)}" }
        val docRef = db.collection("products").document(prodId)
        val initialModeration = if (isAdmin) ProductModerationStatus.APPROVED.name else ProductModerationStatus.PENDING.name
        val payload = mapOf(
            "name" to product.name.trim(),
            "description" to product.description.trim(),
            "priceUsd" to product.priceUsd,
            "category" to product.category.name,
            "sellerId" to product.sellerId.ifBlank { uid },
            "sellerName" to product.sellerName.trim(),
            "sellerCommune" to product.sellerCommune.trim(),
            "stock" to product.stock,
            "rating" to product.rating,
            "reviewCount" to product.reviewCount,
            "isPopular" to product.isPopular,
            "isBestOffer" to product.isBestOffer,
            "discountPercent" to product.discountPercent,
            "isNew" to product.isNew,
            "imageUrls" to product.imageUrls.take(5),
            "moderationStatus" to initialModeration,
            "moderationReason" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            if (isAdmin) {
                recordAdminAuditLog(
                    action = "PRODUCT_CREATE",
                    targetType = "PRODUCT",
                    targetId = prodId,
                    details = "Produit créé et publié par admin : ${product.name.trim()}"
                )
            }
            Result.success(prodId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProduct(product: Product, isAdmin: Boolean = false): Result<Unit> {
        require(product.id.isNotBlank()) { "Product ID required" }
        val docRef = db.collection("products").document(product.id)
        val nextModeration = if (isAdmin) product.moderationStatus.name else ProductModerationStatus.PENDING.name
        val payload = mutableMapOf<String, Any?>(
            "name" to product.name.trim(),
            "description" to product.description.trim(),
            "priceUsd" to product.priceUsd,
            "category" to product.category.name,
            "sellerName" to product.sellerName.trim(),
            "sellerCommune" to product.sellerCommune.trim(),
            "stock" to product.stock,
            "isPopular" to product.isPopular,
            "isBestOffer" to product.isBestOffer,
            "discountPercent" to product.discountPercent,
            "isNew" to product.isNew,
            "imageUrls" to product.imageUrls.take(5),
            "moderationStatus" to nextModeration,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isAdmin) {
            payload["moderationReason"] = product.moderationReason
        }
        return try {
            requireUserId()
            docRef.update(payload).await()
            if (isAdmin) {
                recordAdminAuditLog(
                    action = "PRODUCT_UPDATE",
                    targetType = "PRODUCT",
                    targetId = product.id,
                    details = "Produit mis à jour : ${product.name.trim()}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun moderateProduct(
        productId: String,
        status: ProductModerationStatus,
        reason: String?
    ): Result<Unit> {
        val docRef = db.collection("products").document(productId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "moderationStatus" to status.name,
                    "moderationReason" to reason?.trim()?.ifBlank { null },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAdminAuditLog(
                action = "PRODUCT_${status.name}",
                targetType = "PRODUCT",
                targetId = productId,
                details = "Modération produit -> ${status.label}${if (!reason.isNullOrBlank()) " ($reason)" else ""}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String, isAdmin: Boolean = false): Result<Unit> {
        val docRef = db.collection("products").document(productId)
        return try {
            requireUserId()
            docRef.delete().await()
            if (isAdmin) {
                recordAdminAuditLog(
                    action = "PRODUCT_DELETE",
                    targetType = "PRODUCT",
                    targetId = productId,
                    details = "Suppression définitive du produit $productId"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeCategories(): Flow<List<CategoryConfig>> {
        val uid = auth.currentUser?.uid
        return db.collection("categories")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    CategoryConfig(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        iconName = doc.getString("iconName") ?: "Category",
                        isActive = doc.getBoolean("isActive") ?: true,
                        displayOrder = (doc.getLong("displayOrder") ?: 0L).toInt()
                    )
                }.sortedBy { it.displayOrder }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "categories", uid)
                throw error
            }
    }

    suspend fun saveCategory(category: CategoryConfig): Result<Unit> {
        val catId = category.id.ifBlank { "cat_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("categories").document(catId)
        val payload = mapOf(
            "name" to category.name.trim(),
            "iconName" to category.iconName,
            "isActive" to category.isActive,
            "displayOrder" to category.displayOrder,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            requireUserId()
            docRef.set(payload).await()
            recordAdminAuditLog("CATEGORY_SAVE", "CATEGORY", catId, "Catégorie enregistrée : ${category.name.trim()}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleCategoryActive(category: CategoryConfig): Result<Unit> {
        val docRef = db.collection("categories").document(category.id)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "isActive" to !category.isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observePromotions(): Flow<List<PromotionBanner>> {
        val uid = auth.currentUser?.uid
        return db.collection("promotions")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    PromotionBanner(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        subtitle = doc.getString("subtitle") ?: "",
                        badgeText = doc.getString("badgeText") ?: "PROMO",
                        isActive = doc.getBoolean("isActive") ?: true
                    )
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "promotions", uid)
                throw error
            }
    }

    suspend fun savePromotion(promo: PromotionBanner): Result<Unit> {
        val promoId = promo.id.ifBlank { "promo_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("promotions").document(promoId)
        val payload = mapOf(
            "title" to promo.title.trim(),
            "subtitle" to promo.subtitle.trim(),
            "badgeText" to promo.badgeText.trim(),
            "isActive" to promo.isActive,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            requireUserId()
            docRef.set(payload).await()
            recordAdminAuditLog("PROMO_SAVE", "PROMOTION", promoId, "Bannière promo : ${promo.title.trim()}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePromotion(promoId: String): Result<Unit> {
        val docRef = db.collection("promotions").document(promoId)
        return try {
            requireUserId()
            docRef.delete().await()
            recordAdminAuditLog("PROMO_DELETE", "PROMOTION", promoId, "Bannière supprimée : $promoId")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeAppSettings(): Flow<AppConfigSettings> {
        val uid = auth.currentUser?.uid
        val path = "app_settings/general"
        return db.collection("app_settings").document("general")
            .snapshots()
            .map { doc ->
                if (!doc.exists()) {
                    AppConfigSettings()
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val feesRaw = doc.get("communeFees") as? Map<String, Any?> ?: emptyMap()
                    val fees = CommuneLubumbashi.values().associate { com ->
                        val v = (feesRaw[com.name] as? Number)?.toDouble() ?: com.defaultDeliveryFeeUsd
                        com.name to v
                    }
                    AppConfigSettings(
                        cdfExchangeRate = doc.getDouble("cdfExchangeRate") ?: 2800.0,
                        commissionPercent = doc.getDouble("commissionPercent") ?: 5.0,
                        communeFees = fees,
                        supportPhone = doc.getString("supportPhone") ?: "+243 800 000 001"
                    )
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, path, uid)
                throw error
            }
    }

    suspend fun saveAppSettings(settings: AppConfigSettings): Result<Unit> {
        val docRef = db.collection("app_settings").document("general")
        return try {
            requireUserId()
            val existing = docRef.get().await().exists()
            val payload = mutableMapOf<String, Any>(
                "cdfExchangeRate" to settings.cdfExchangeRate,
                "commissionPercent" to settings.commissionPercent,
                "communeFees" to settings.communeFees,
                "supportPhone" to settings.supportPhone.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existing) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            } else {
                docRef.update(payload).await()
            }
            recordAdminAuditLog("SETTINGS_UPDATE", "SETTINGS", "general", "Taux CDF=${settings.cdfExchangeRate}, Commission=${settings.commissionPercent}%")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Saved Addresses ---
    fun observeSavedAddresses(uid: String): Flow<List<DeliveryAddress>> {
        val path = "users/$uid/addresses"
        return db.collection("users").document(uid).collection("addresses")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    val communeEnum = runCatching {
                        CommuneLubumbashi.valueOf(doc.getString("commune") ?: CommuneLubumbashi.LUBUMBASHI.name)
                    }.getOrDefault(CommuneLubumbashi.LUBUMBASHI)
                    val deliveryTypeEnum = runCatching {
                        DeliveryType.valueOf(doc.getString("deliveryType") ?: DeliveryType.DOMICILE.name)
                    }.getOrDefault(DeliveryType.DOMICILE)
                    DeliveryAddress(
                        id = doc.id,
                        fullName = doc.getString("fullName") ?: "",
                        phone = doc.getString("phone") ?: "",
                        type = deliveryTypeEnum,
                        commune = communeEnum,
                        quartier = doc.getString("quartier") ?: "",
                        avenue = doc.getString("avenue") ?: "",
                        referenceLandmark = doc.getString("referenceLandmark") ?: "",
                        isDefault = doc.getBoolean("isDefault") ?: false
                    )
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun addDeliveryAddress(uid: String, address: DeliveryAddress): Result<String> {
        val addrId = address.id.ifBlank { "addr_${UUID.randomUUID().toString().take(10)}" }
        val docRef = db.collection("users").document(uid).collection("addresses").document(addrId)
        val payload = mapOf(
            "fullName" to address.fullName.trim(),
            "phone" to address.phone.trim(),
            "deliveryType" to address.type.name,
            "commune" to address.commune.name,
            "quartier" to address.quartier.trim(),
            "avenue" to address.avenue.trim(),
            "referenceLandmark" to address.referenceLandmark.trim(),
            "isDefault" to address.isDefault,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            requireUserId()
            docRef.set(payload).await()
            Result.success(addrId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Orders, Payment Confirmation & Delivery Dispatch ---
    fun observeOrdersForAdmin(): Flow<List<Order>> {
        val uid = auth.currentUser?.uid
        return db.collection("orders")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { snapshotToOrder(it) } }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "orders", uid)
                throw error
            }
    }

    fun observeCustomerOrders(uid: String): Flow<List<Order>> {
        return db.collection("orders")
            .whereEqualTo("customerId", uid)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { snapshotToOrder(it) } }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "orders", uid)
                throw error
            }
    }

    fun observeVendorOrders(uid: String): Flow<List<Order>> {
        return db.collection("orders")
            .whereArrayContains("sellerIds", uid)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { snapshotToOrder(it) } }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "orders", uid)
                throw error
            }
    }

    fun observeDriverAssignedOrders(uid: String): Flow<List<Order>> {
        return db.collection("orders")
            .whereEqualTo("assignedDriverId", uid)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { snapshotToOrder(it) } }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "orders", uid)
                throw error
            }
    }

    fun observeAvailableDeliveryJobs(): Flow<List<DeliveryJob>> {
        val uid = auth.currentUser?.uid
        return db.collection("delivery_jobs")
            .whereEqualTo("status", OrderStatus.EN_ATTENTE_LIVREUR.name)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    DeliveryJob(
                        orderId = doc.getString("orderId") ?: doc.id,
                        commune = doc.getString("commune") ?: "",
                        quartier = doc.getString("quartier") ?: "",
                        deliveryFeeUsd = doc.getDouble("deliveryFeeUsd") ?: 0.0,
                        status = OrderStatus.EN_ATTENTE_LIVREUR,
                        assignedDriverId = doc.getString("assignedDriverId")
                    )
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "delivery_jobs", uid)
                throw error
            }
    }

    suspend fun placeOrder(
        address: DeliveryAddress,
        cartItems: List<CartItem>,
        deliveryFeeUsd: Double,
        commissionPercent: Double,
        paymentMethod: PaymentMethod,
        notes: String?
    ): Result<Order> {
        val uid = requireUserId()
        val orderId = "LBM-${(10000..99999).random()}"
        val subtotal = cartItems.sumOf { it.totalUsd }
        val total = subtotal + deliveryFeeUsd
        val commissionUsd = subtotal * (commissionPercent / 100.0)
        val sellerIds = cartItems.map { it.product.sellerId.ifBlank { "platform" } }.distinct()

        val addressMap = mapOf(
            "fullName" to address.fullName,
            "phone" to address.phone,
            "deliveryType" to address.type.name,
            "commune" to address.commune.name,
            "quartier" to address.quartier,
            "avenue" to address.avenue,
            "referenceLandmark" to address.referenceLandmark
        )

        val itemMaps = cartItems.map { item ->
            mapOf(
                "productId" to item.product.id,
                "name" to item.product.name,
                "quantity" to item.quantity,
                "priceUsd" to item.product.discountedPriceUsd(),
                "sellerId" to item.product.sellerId,
                "sellerName" to item.product.sellerName
            )
        }

        val payload = mutableMapOf<String, Any>(
            "customerId" to uid,
            "customerName" to address.fullName.trim(),
            "customerPhone" to address.phone.trim(),
            "sellerIds" to sellerIds,
            "deliveryAddress" to addressMap,
            "items" to itemMaps,
            "deliveryFeeUsd" to deliveryFeeUsd,
            "subtotalUsd" to subtotal,
            "totalUsd" to total,
            "commissionUsd" to commissionUsd,
            "paymentMethod" to paymentMethod.name,
            "status" to OrderStatus.RECU.name,
            "paymentConfirmed" to false,
            "commissionCredited" to false,
            "driverPayoutSettled" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!notes.isNullOrBlank()) {
            payload["vendorNotes"] = notes.trim()
        }

        val docRef = db.collection("orders").document(orderId)
        return try {
            docRef.set(payload).await()
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.FRENCH).format(java.util.Date())
            Result.success(
                Order(
                    id = orderId,
                    customerId = uid,
                    customerName = address.fullName,
                    customerPhone = address.phone,
                    sellerIds = sellerIds,
                    items = cartItems,
                    deliveryAddress = address,
                    deliveryFeeUsd = deliveryFeeUsd,
                    subtotalUsd = subtotal,
                    totalUsd = total,
                    commissionUsd = commissionUsd,
                    paymentMethod = paymentMethod,
                    status = OrderStatus.RECU,
                    createdAt = dateStr,
                    vendorNotes = notes,
                    paymentConfirmed = false,
                    commissionCredited = false,
                    driverPayoutSettled = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(order: Order, newStatus: OrderStatus, isAdmin: Boolean = false): Result<Unit> {
        val orderRef = db.collection("orders").document(order.id)
        val jobRef = db.collection("delivery_jobs").document(order.id)
        return try {
            requireUserId()
            val batch = db.batch()
            batch.update(
                orderRef,
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (newStatus == OrderStatus.EN_ATTENTE_LIVREUR) {
                batch.set(
                    jobRef,
                    mapOf(
                        "orderId" to order.id,
                        "commune" to order.deliveryAddress.commune.nom,
                        "quartier" to order.deliveryAddress.quartier,
                        "deliveryFeeUsd" to order.deliveryFeeUsd,
                        "status" to OrderStatus.EN_ATTENTE_LIVREUR.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else if (newStatus == OrderStatus.LIVREE || newStatus == OrderStatus.ANNULEE) {
                val jobDoc = jobRef.get().await()
                if (jobDoc.exists()) {
                    batch.update(
                        jobRef,
                        mapOf(
                            "status" to newStatus.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            if (isAdmin) {
                recordAdminAuditLog("ORDER_STATUS_${newStatus.name}", "ORDER", order.id, "Statut commande changé en ${newStatus.label}")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            Result.failure(e)
        }
    }

    suspend fun confirmOrderPayment(orderId: String, paymentReference: String): Result<Unit> {
        if (paymentReference.isBlank()) {
            return Result.failure(IllegalArgumentException("Référence de paiement requise pour confirmation réelle."))
        }
        val orderRef = db.collection("orders").document(orderId)
        return try {
            requireUserId()
            db.runTransaction { transaction ->
                val snap = transaction.get(orderRef)
                val statusStr = snap.getString("status")
                if (statusStr == OrderStatus.ANNULEE.name) {
                    throw IllegalStateException("Impossible de confirmer le paiement d'une commande annulée.")
                }
                val alreadyCredited = snap.getBoolean("commissionCredited") ?: false
                if (alreadyCredited) {
                    throw IllegalStateException("Paiement et commission 5% déjà confirmés et comptabilisés pour cette commande.")
                }
                transaction.update(
                    orderRef,
                    mapOf(
                        "paymentConfirmed" to true,
                        "paymentReference" to paymentReference.trim(),
                        "commissionCredited" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            recordAdminAuditLog(
                action = "ORDER_PAYMENT_CONFIRMED",
                targetType = "ORDER",
                targetId = orderId,
                details = "Paiement réel confirmé (Réf: ${paymentReference.trim()}) & commission 5% créditée une seule fois"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            Result.failure(e)
        }
    }

    suspend fun settleDriverOrderPayout(orderId: String): Result<Unit> {
        val orderRef = db.collection("orders").document(orderId)
        return try {
            requireUserId()
            db.runTransaction { transaction ->
                val snap = transaction.get(orderRef)
                if (snap.getString("status") != OrderStatus.LIVREE.name) {
                    throw IllegalStateException("La course doit être marquée Livrée avant règlement.")
                }
                if (snap.getBoolean("driverPayoutSettled") == true) {
                    throw IllegalStateException("Frais de course déjà réglés pour cette commande.")
                }
                transaction.update(
                    orderRef,
                    mapOf(
                        "driverPayoutSettled" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            recordAdminAuditLog("DRIVER_PAYOUT_SETTLED", "ORDER", orderId, "Frais de livraison réglés au livreur")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            Result.failure(e)
        }
    }

    suspend fun driverClaimOrder(orderId: String, driver: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val orderRef = db.collection("orders").document(orderId)
        val jobRef = db.collection("delivery_jobs").document(orderId)
        return try {
            val batch = db.batch()
            batch.update(
                orderRef,
                mapOf(
                    "assignedDriverId" to uid,
                    "assignedDriverName" to driver.fullName,
                    "assignedDriverPhone" to driver.phone,
                    "status" to OrderStatus.EN_COURS_LIVRAISON.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                jobRef,
                mapOf(
                    "assignedDriverId" to uid,
                    "status" to OrderStatus.EN_COURS_LIVRAISON.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminAssignDriver(orderId: String, driver: UserProfile): Result<Unit> {
        val orderRef = db.collection("orders").document(orderId)
        val jobRef = db.collection("delivery_jobs").document(orderId)
        return try {
            requireUserId()
            val batch = db.batch()
            batch.update(
                orderRef,
                mapOf(
                    "assignedDriverId" to driver.userId,
                    "assignedDriverName" to driver.fullName,
                    "assignedDriverPhone" to driver.phone,
                    "status" to OrderStatus.EN_COURS_LIVRAISON.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val jobDoc = jobRef.get().await()
            if (jobDoc.exists()) {
                batch.update(
                    jobRef,
                    mapOf(
                        "assignedDriverId" to driver.userId,
                        "status" to OrderStatus.EN_COURS_LIVRAISON.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            recordAdminAuditLog("ORDER_ASSIGN_DRIVER", "ORDER", orderId, "Livreur ${driver.fullName} assigné")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            Result.failure(e)
        }
    }

    // --- Order Disputes / Claims ---
    fun observeOrderDisputes(uid: String, isAdmin: Boolean): Flow<List<OrderDispute>> {
        val query = if (isAdmin) {
            db.collection("order_disputes")
        } else {
            db.collection("order_disputes").whereEqualTo("customerId", uid)
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    val ts = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    val dateStr = ts?.toDate()?.let {
                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.FRENCH).format(it)
                    } ?: ""
                    OrderDispute(
                        id = doc.id,
                        orderId = doc.getString("orderId") ?: "",
                        customerId = doc.getString("customerId") ?: "",
                        customerName = doc.getString("customerName") ?: "",
                        reason = doc.getString("reason") ?: "",
                        status = runCatching {
                            DisputeStatus.valueOf(doc.getString("status") ?: DisputeStatus.OPEN.name)
                        }.getOrDefault(DisputeStatus.OPEN),
                        adminNotes = doc.getString("adminNotes"),
                        createdAt = dateStr
                    )
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "order_disputes", uid)
                throw error
            }
    }

    suspend fun createOrderDispute(
        orderId: String,
        customerName: String,
        reason: String
    ): Result<String> {
        val uid = requireUserId()
        val dispId = "LIT-${(1000..9999).random()}-${UUID.randomUUID().toString().take(4)}"
        val docRef = db.collection("order_disputes").document(dispId)
        val payload = mapOf(
            "orderId" to orderId,
            "customerId" to uid,
            "customerName" to customerName.trim(),
            "reason" to reason.trim(),
            "status" to DisputeStatus.OPEN.name,
            "adminNotes" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(dispId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun resolveOrderDispute(
        disputeId: String,
        status: DisputeStatus,
        adminNotes: String?
    ): Result<Unit> {
        val docRef = db.collection("order_disputes").document(disputeId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "status" to status.name,
                    "adminNotes" to adminNotes?.trim()?.ifBlank { null },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAdminAuditLog("DISPUTE_${status.name}", "DISPUTE", disputeId, "Litige traité (${status.label}) : ${adminNotes ?: ""}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Vendor Withdrawals (Strictly isolated from Owner Wallet) ---
    fun observeVendorWithdrawals(uid: String, isAdmin: Boolean): Flow<List<WithdrawalRequest>> {
        val query = if (isAdmin) {
            db.collection("vendor_withdrawals")
        } else {
            db.collection("vendor_withdrawals").whereEqualTo("vendorId", uid)
        }
        return query.snapshots()
            .map { snapshot -> snapshot.documents.map { snapshotToVendorWithdrawal(it) } }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "vendor_withdrawals", uid)
                throw error
            }
    }

    suspend fun createVendorWithdrawal(
        vendor: UserProfile,
        amountGrossUsd: Double,
        commissionPercent: Double,
        paymentMethod: PaymentMethod,
        accountDetails: String,
        availableBalanceUsd: Double
    ): Result<WithdrawalRequest> {
        val uid = requireUserId()
        if (amountGrossUsd <= 0.0 || amountGrossUsd > availableBalanceUsd) {
            return Result.failure(IllegalArgumentException("Solde disponible insuffisant pour ce retrait."))
        }
        val reqId = "RET-${(1000..9999).random()}-${UUID.randomUUID().toString().take(4)}"
        val commissionUsd = amountGrossUsd * (commissionPercent / 100.0)
        val netUsd = amountGrossUsd - commissionUsd
        val docRef = db.collection("vendor_withdrawals").document(reqId)

        val payload = mapOf(
            "vendorId" to uid,
            "vendorName" to (vendor.storeName?.ifBlank { vendor.fullName } ?: vendor.fullName),
            "amountGrossUsd" to amountGrossUsd,
            "commissionPercent" to commissionPercent,
            "commissionUsd" to commissionUsd,
            "amountNetUsd" to netUsd,
            "paymentMethod" to paymentMethod.name,
            "accountDetails" to accountDetails.trim(),
            "status" to WithdrawalStatus.EN_ATTENTE.firestoreValue,
            "verifiedByAdmin" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (snap.exists()) {
                    throw IllegalStateException("Demande en double détectée.")
                }
                transaction.set(docRef, payload)
            }.await()
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.FRENCH).format(java.util.Date())
            Result.success(
                WithdrawalRequest(
                    id = reqId,
                    vendorId = uid,
                    vendorName = vendor.storeName ?: vendor.fullName,
                    amountGrossUsd = amountGrossUsd,
                    commissionPercent = commissionPercent,
                    commissionUsd = commissionUsd,
                    amountNetUsd = netUsd,
                    paymentMethod = paymentMethod,
                    accountDetails = accountDetails.trim(),
                    status = WithdrawalStatus.EN_ATTENTE,
                    verifiedByAdmin = false,
                    createdAt = dateStr
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateVendorWithdrawalStatus(
        withdrawalId: String,
        newStatus: WithdrawalStatus,
        rejectionReason: String?,
        verifiedConfirmed: Boolean
    ): Result<Unit> {
        if (newStatus == WithdrawalStatus.PAYE && !verifiedConfirmed) {
            return Result.failure(IllegalStateException("Vérification réelle requise avant de confirmer le paiement."))
        }
        if (newStatus == WithdrawalStatus.REFUSE && rejectionReason.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Un motif de refus est obligatoire."))
        }
        val docRef = db.collection("vendor_withdrawals").document(withdrawalId)
        return try {
            requireUserId()
            db.runTransaction { transaction ->
                val currentSnap = transaction.get(docRef)
                val currentStatus = currentSnap.getString("status")
                if (currentStatus == WithdrawalStatus.PAYE.firestoreValue || currentStatus == WithdrawalStatus.REFUSE.firestoreValue) {
                    throw IllegalStateException("Cette demande est déjà clôturée (anti-double paiement).")
                }
                val updates = mutableMapOf<String, Any>(
                    "status" to newStatus.firestoreValue,
                    "verifiedByAdmin" to (newStatus == WithdrawalStatus.PAYE || verifiedConfirmed),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!rejectionReason.isNullOrBlank()) {
                    updates["rejectionReason"] = rejectionReason.trim()
                }
                transaction.update(docRef, updates)
            }.await()
            recordAdminAuditLog("VENDOR_WITHDRAWAL_${newStatus.firestoreValue}", "VENDOR_WITHDRAWAL", withdrawalId, "Retrait vendeur -> ${newStatus.label}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Owner Private Wallet & Withdrawals (Strictly Admin/Owner Only) ---
    fun observeOwnerWalletConfig(): Flow<OwnerWalletConfig?> {
        val uid = auth.currentUser?.uid
        val path = "owner_wallet/config"
        return db.collection("owner_wallet").document("config")
            .snapshots()
            .map { doc ->
                if (!doc.exists()) null
                else OwnerWalletConfig(
                    ownerId = doc.getString("ownerId") ?: "",
                    preferredMethod = runCatching {
                        PaymentMethod.valueOf(doc.getString("preferredMethod") ?: PaymentMethod.MPESA.name)
                    }.getOrDefault(PaymentMethod.MPESA),
                    accountDetails = doc.getString("accountDetails") ?: "",
                    accountHolderName = doc.getString("accountHolderName") ?: ""
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, path, uid)
                throw error
            }
    }

    suspend fun saveOwnerWalletConfig(config: OwnerWalletConfig): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("owner_wallet").document("config")
        val payload = mapOf(
            "ownerId" to uid,
            "preferredMethod" to config.preferredMethod.name,
            "accountDetails" to config.accountDetails.trim(),
            "accountHolderName" to config.accountHolderName.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            recordAdminAuditLog("OWNER_WALLET_CONFIG", "OWNER_WALLET", "config", "Coordonnées portefeuille admin mises à jour")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeOwnerWithdrawals(): Flow<List<OwnerWithdrawalRequest>> {
        val uid = auth.currentUser?.uid
        return db.collection("owner_withdrawals")
            .snapshots()
            .map { snapshot -> snapshot.documents.map { snapshotToOwnerWithdrawal(it) } }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "owner_withdrawals", uid)
                throw error
            }
    }

    suspend fun createOwnerWithdrawal(
        amountUsd: Double,
        paymentMethod: PaymentMethod,
        accountDetails: String,
        availableOwnerBalanceUsd: Double
    ): Result<OwnerWithdrawalRequest> {
        val uid = requireUserId()
        if (amountUsd <= 0.0 || amountUsd > availableOwnerBalanceUsd) {
            return Result.failure(IllegalArgumentException("Solde propriétaire insuffisant pour ce retrait."))
        }
        val reqId = "OWN-${(1000..9999).random()}-${UUID.randomUUID().toString().take(4)}"
        val docRef = db.collection("owner_withdrawals").document(reqId)
        val payload = mapOf(
            "ownerId" to uid,
            "amountUsd" to amountUsd,
            "paymentMethod" to paymentMethod.name,
            "accountDetails" to accountDetails.trim(),
            "status" to WithdrawalStatus.EN_ATTENTE.firestoreValue,
            "verifiedConfirmed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.runTransaction { transaction ->
                if (transaction.get(docRef).exists()) {
                    throw IllegalStateException("Retrait propriétaire en double détecté.")
                }
                transaction.set(docRef, payload)
            }.await()
            recordAdminAuditLog("OWNER_WITHDRAWAL_REQUEST", "OWNER_WITHDRAWAL", reqId, "Demande retrait admin : $amountUsd USD via ${paymentMethod.shortCode}")
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.FRENCH).format(java.util.Date())
            Result.success(
                OwnerWithdrawalRequest(
                    id = reqId,
                    ownerId = uid,
                    amountUsd = amountUsd,
                    paymentMethod = paymentMethod,
                    accountDetails = accountDetails.trim(),
                    status = WithdrawalStatus.EN_ATTENTE,
                    verifiedConfirmed = false,
                    createdAt = dateStr
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateOwnerWithdrawalStatus(
        withdrawalId: String,
        newStatus: WithdrawalStatus,
        rejectionReason: String?,
        verifiedConfirmed: Boolean
    ): Result<Unit> {
        if (newStatus == WithdrawalStatus.PAYE && !verifiedConfirmed) {
            return Result.failure(IllegalStateException("Vérification réelle requise avant confirmation de paiement."))
        }
        if (newStatus == WithdrawalStatus.REFUSE && rejectionReason.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Motif de refus obligatoire."))
        }
        val docRef = db.collection("owner_withdrawals").document(withdrawalId)
        return try {
            requireUserId()
            db.runTransaction { transaction ->
                val currentStatus = transaction.get(docRef).getString("status")
                if (currentStatus == WithdrawalStatus.PAYE.firestoreValue || currentStatus == WithdrawalStatus.REFUSE.firestoreValue) {
                    throw IllegalStateException("Retrait déjà finalisé (anti-double paiement).")
                }
                val updates = mutableMapOf<String, Any>(
                    "status" to newStatus.firestoreValue,
                    "verifiedConfirmed" to (newStatus == WithdrawalStatus.PAYE || verifiedConfirmed),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!rejectionReason.isNullOrBlank()) {
                    updates["rejectionReason"] = rejectionReason.trim()
                }
                transaction.update(docRef, updates)
            }.await()
            recordAdminAuditLog("OWNER_WITHDRAWAL_${newStatus.firestoreValue}", "OWNER_WITHDRAWAL", withdrawalId, "Retrait admin -> ${newStatus.label}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Seed Initial Marketplace Data (Admin Only) ---
    suspend fun seedInitialMarketplaceIfEmpty(): Result<Unit> {
        return try {
            val prodSnap = db.collection("products").limit(1).get().await()
            if (prodSnap.isEmpty) {
                val batch = db.batch()
                MarketplaceSeedData.starterProducts.forEach { prod ->
                    val ref = db.collection("products").document(prod.id)
                    batch.set(
                        ref,
                        mapOf(
                            "name" to prod.name,
                            "description" to prod.description,
                            "priceUsd" to prod.priceUsd,
                            "category" to prod.category.name,
                            "sellerId" to prod.sellerId,
                            "sellerName" to prod.sellerName,
                            "sellerCommune" to prod.sellerCommune,
                            "stock" to prod.stock,
                            "rating" to prod.rating,
                            "reviewCount" to prod.reviewCount,
                            "isPopular" to prod.isPopular,
                            "isBestOffer" to prod.isBestOffer,
                            "discountPercent" to prod.discountPercent,
                            "isNew" to prod.isNew,
                            "imageUrls" to prod.imageUrls,
                            "moderationStatus" to ProductModerationStatus.APPROVED.name,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                MarketplaceSeedData.starterCategories.forEach { cat ->
                    batch.set(
                        db.collection("categories").document(cat.id),
                        mapOf(
                            "name" to cat.name,
                            "iconName" to cat.iconName,
                            "isActive" to cat.isActive,
                            "displayOrder" to cat.displayOrder,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                MarketplaceSeedData.starterPromotions.forEach { promo ->
                    batch.set(
                        db.collection("promotions").document(promo.id),
                        mapOf(
                            "title" to promo.title,
                            "subtitle" to promo.subtitle,
                            "badgeText" to promo.badgeText,
                            "isActive" to promo.isActive,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                val settings = AppConfigSettings()
                batch.set(
                    db.collection("app_settings").document("general"),
                    mapOf(
                        "cdfExchangeRate" to settings.cdfExchangeRate,
                        "commissionPercent" to settings.commissionPercent,
                        "communeFees" to settings.communeFees,
                        "supportPhone" to settings.supportPhone,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "seed")
            Result.failure(e)
        }
    }

    // --- Snapshot Mappers ---
    private fun snapshotToUserProfile(doc: DocumentSnapshot): UserProfile {
        return UserProfile(
            id = doc.id,
            userId = doc.getString("userId") ?: doc.id,
            fullName = doc.getString("fullName") ?: "",
            phone = doc.getString("phone") ?: "",
            email = doc.getString("email") ?: "",
            roleName = doc.getString("role") ?: UserRole.CLIENT.name,
            statusName = doc.getString("accountStatus") ?: AccountStatus.APPROVED.name,
            commune = doc.getString("commune") ?: "Lubumbashi",
            quartier = doc.getString("quartier") ?: "",
            storeName = doc.getString("storeName"),
            vehicleType = doc.getString("vehicleType"),
            deliveryZone = doc.getString("deliveryZone"),
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun snapshotToProduct(doc: DocumentSnapshot): Product? {
        val name = doc.getString("name") ?: return null
        val catEnum = runCatching {
            ProductCategory.valueOf(doc.getString("category") ?: ProductCategory.TECH.name)
        }.getOrDefault(ProductCategory.TECH)
        val rawImages = (doc.get("imageUrls") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val fallbackSeedImages = if (rawImages.isEmpty()) {
            MarketplaceSeedData.starterProducts.firstOrNull { it.id == doc.id }?.imageUrls ?: emptyList()
        } else rawImages
        return Product(
            id = doc.id,
            name = name,
            description = doc.getString("description") ?: "",
            priceUsd = doc.getDouble("priceUsd") ?: 0.0,
            category = catEnum,
            categoryName = catEnum.label,
            sellerId = doc.getString("sellerId") ?: "",
            sellerName = doc.getString("sellerName") ?: "",
            sellerCommune = doc.getString("sellerCommune") ?: "Lubumbashi",
            stock = (doc.getLong("stock") ?: 0L).toInt(),
            rating = doc.getDouble("rating") ?: 5.0,
            reviewCount = (doc.getLong("reviewCount") ?: 0L).toInt(),
            isPopular = doc.getBoolean("isPopular") ?: false,
            isBestOffer = doc.getBoolean("isBestOffer") ?: false,
            discountPercent = (doc.getLong("discountPercent") ?: 0L).toInt(),
            isNew = doc.getBoolean("isNew") ?: false,
            imageUrls = fallbackSeedImages,
            moderationStatusName = doc.getString("moderationStatus") ?: ProductModerationStatus.APPROVED.name,
            moderationReason = doc.getString("moderationReason")
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun snapshotToOrder(doc: DocumentSnapshot): Order? {
        val customerId = doc.getString("customerId") ?: return null
        val addrMap = doc.get("deliveryAddress") as? Map<String, Any?> ?: emptyMap()
        val communeEnum = runCatching {
            CommuneLubumbashi.valueOf((addrMap["commune"] as? String) ?: CommuneLubumbashi.LUBUMBASHI.name)
        }.getOrDefault(CommuneLubumbashi.LUBUMBASHI)
        val deliveryTypeEnum = runCatching {
            DeliveryType.valueOf((addrMap["deliveryType"] as? String) ?: DeliveryType.DOMICILE.name)
        }.getOrDefault(DeliveryType.DOMICILE)
        val address = DeliveryAddress(
            fullName = (addrMap["fullName"] as? String) ?: (doc.getString("customerName") ?: ""),
            phone = (addrMap["phone"] as? String) ?: (doc.getString("customerPhone") ?: ""),
            type = deliveryTypeEnum,
            commune = communeEnum,
            quartier = (addrMap["quartier"] as? String) ?: "",
            avenue = (addrMap["avenue"] as? String) ?: "",
            referenceLandmark = (addrMap["referenceLandmark"] as? String) ?: ""
        )

        val rawItems = doc.get("items") as? List<Map<String, Any?>> ?: emptyList()
        val items = rawItems.map { itemMap ->
            val p = Product(
                id = (itemMap["productId"] as? String) ?: "",
                name = (itemMap["name"] as? String) ?: "",
                priceUsd = (itemMap["priceUsd"] as? Number)?.toDouble() ?: 0.0,
                sellerId = (itemMap["sellerId"] as? String) ?: "",
                sellerName = (itemMap["sellerName"] as? String) ?: ""
            )
            CartItem(product = p, quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 1)
        }

        val timestamp = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val dateStr = timestamp?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.FRENCH).format(it)
        } ?: ""

        return Order(
            id = doc.id,
            customerId = customerId,
            customerName = doc.getString("customerName") ?: "",
            customerPhone = doc.getString("customerPhone") ?: "",
            sellerIds = (doc.get("sellerIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            items = items,
            deliveryAddress = address,
            deliveryFeeUsd = doc.getDouble("deliveryFeeUsd") ?: 0.0,
            subtotalUsd = doc.getDouble("subtotalUsd") ?: 0.0,
            totalUsd = doc.getDouble("totalUsd") ?: 0.0,
            commissionUsd = doc.getDouble("commissionUsd") ?: 0.0,
            paymentMethod = runCatching {
                PaymentMethod.valueOf(doc.getString("paymentMethod") ?: PaymentMethod.MPESA.name)
            }.getOrDefault(PaymentMethod.MPESA),
            status = runCatching {
                OrderStatus.valueOf(doc.getString("status") ?: OrderStatus.RECU.name)
            }.getOrDefault(OrderStatus.RECU),
            createdAt = dateStr,
            assignedDriverId = doc.getString("assignedDriverId"),
            assignedDriverName = doc.getString("assignedDriverName"),
            assignedDriverPhone = doc.getString("assignedDriverPhone"),
            vendorNotes = doc.getString("vendorNotes"),
            paymentConfirmed = doc.getBoolean("paymentConfirmed") ?: false,
            paymentReference = doc.getString("paymentReference"),
            commissionCredited = doc.getBoolean("commissionCredited") ?: false,
            driverPayoutSettled = doc.getBoolean("driverPayoutSettled") ?: false
        )
    }

    private fun snapshotToVendorWithdrawal(doc: DocumentSnapshot): WithdrawalRequest {
        val timestamp = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val dateStr = timestamp?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.FRENCH).format(it)
        } ?: ""
        return WithdrawalRequest(
            id = doc.id,
            vendorId = doc.getString("vendorId") ?: "",
            vendorName = doc.getString("vendorName") ?: "",
            amountGrossUsd = doc.getDouble("amountGrossUsd") ?: 0.0,
            commissionPercent = doc.getDouble("commissionPercent") ?: 5.0,
            commissionUsd = doc.getDouble("commissionUsd") ?: 0.0,
            amountNetUsd = doc.getDouble("amountNetUsd") ?: 0.0,
            paymentMethod = runCatching {
                PaymentMethod.valueOf(doc.getString("paymentMethod") ?: PaymentMethod.MPESA.name)
            }.getOrDefault(PaymentMethod.MPESA),
            accountDetails = doc.getString("accountDetails") ?: "",
            status = WithdrawalStatus.fromFirestore(doc.getString("status")),
            rejectionReason = doc.getString("rejectionReason"),
            verifiedByAdmin = doc.getBoolean("verifiedByAdmin") ?: false,
            createdAt = dateStr
        )
    }

    private fun snapshotToOwnerWithdrawal(doc: DocumentSnapshot): OwnerWithdrawalRequest {
        val timestamp = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val dateStr = timestamp?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.FRENCH).format(it)
        } ?: ""
        return OwnerWithdrawalRequest(
            id = doc.id,
            ownerId = doc.getString("ownerId") ?: "",
            amountUsd = doc.getDouble("amountUsd") ?: 0.0,
            paymentMethod = runCatching {
                PaymentMethod.valueOf(doc.getString("paymentMethod") ?: PaymentMethod.MPESA.name)
            }.getOrDefault(PaymentMethod.MPESA),
            accountDetails = doc.getString("accountDetails") ?: "",
            status = WithdrawalStatus.fromFirestore(doc.getString("status")),
            rejectionReason = doc.getString("rejectionReason"),
            verifiedConfirmed = doc.getBoolean("verifiedConfirmed") ?: false,
            createdAt = dateStr
        )
    }

    private fun unwrapAndLog(error: Throwable, op: OperationType, path: String, expectedUid: String?) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
