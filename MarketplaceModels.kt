package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val label: String) {
    CLIENT("Espace Client"),
    VENDEUR("Espace Vendeur"),
    LIVREUR("Espace Livreur"),
    ADMIN("Administration Lubumarket")
}

enum class AccountStatus(val label: String) {
    APPROVED("Approuvé"),
    PENDING("En attente d'approbation"),
    SUSPENDED("Suspendu"),
    REJECTED("Refusé"),
    BLOCKED("Bloqué (Fraude)")
}

enum class ProductModerationStatus(val label: String) {
    PENDING("En attente de validation"),
    APPROVED("Publié"),
    REJECTED("Refusé"),
    BLOCKED("Bloqué (Interdit)")
}

enum class DisputeStatus(val label: String) {
    OPEN("Ouvert"),
    IN_REVIEW("En examen"),
    RESOLVED("Résolu"),
    REJECTED("Rejeté")
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val roleName: String = UserRole.CLIENT.name,
    val statusName: String = AccountStatus.APPROVED.name,
    val commune: String = "Lubumbashi",
    val quartier: String = "",
    val storeName: String? = null,
    val vehicleType: String? = null,
    val deliveryZone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val name: String get() = fullName
    val role: UserRole
        get() = runCatching { UserRole.valueOf(roleName) }.getOrDefault(UserRole.CLIENT)
    val accountStatus: AccountStatus
        get() = runCatching { AccountStatus.valueOf(statusName) }.getOrDefault(AccountStatus.APPROVED)
}

enum class CommuneLubumbashi(
    val nom: String,
    val defaultDeliveryFeeUsd: Double,
    val estimatedTime: String
) {
    LUBUMBASHI("Commune de Lubumbashi (Centre/Golf)", 2.00, "20-40 min"),
    KENYA("Commune de la Kenya", 2.50, "30-50 min"),
    KAMALONDO("Commune de Kamalondo", 2.50, "25-45 min"),
    KAMPEMBA("Commune de Kampemba", 3.00, "35-55 min"),
    KATUBA("Commune de la Katuba", 3.00, "35-55 min"),
    RUASHI("Commune de la Ruashi", 3.50, "40-60 min"),
    ANNEXE("Commune Annexe (Kasapa/Kisanga/Luano)", 4.00, "45-75 min")
}

enum class DeliveryType(val label: String) {
    DOMICILE("Livraison à domicile"),
    BUREAU("Livraison au bureau"),
    AUTRE("Autre adresse choisie")
}

data class DeliveryAddress(
    val id: String = "",
    val fullName: String = "",
    val phone: String = "",
    val type: DeliveryType = DeliveryType.DOMICILE,
    val commune: CommuneLubumbashi = CommuneLubumbashi.LUBUMBASHI,
    val quartier: String = "",
    val avenue: String = "",
    val referenceLandmark: String = "",
    val isDefault: Boolean = false
) {
    fun fullAddressDisplay(): String {
        return "$avenue, Q. $quartier, ${commune.nom} (Réf: $referenceLandmark)"
    }
}

enum class ProductCategory(val label: String, val iconName: String) {
    TOUS("Tous", "Category"),
    TECH("Électronique & Téléphones", "Smartphone"),
    ALIMENTATION("Vivres Frais & Marché", "Restaurant"),
    MODE("Mode & Habillement", "Checkroom"),
    MAISON("Maison & Électroménager", "Home"),
    BEAUTE("Beauté & Soins", "Spa"),
    QUINCAILLERIE("Outillage & Quincaillerie", "Build")
}

data class CategoryConfig(
    val id: String = "",
    val name: String = "",
    val iconName: String = "Category",
    val isActive: Boolean = true,
    val displayOrder: Int = 0
)

data class PromotionBanner(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val badgeText: String = "",
    val isActive: Boolean = true
)

data class AppConfigSettings(
    val cdfExchangeRate: Double = 2800.0,
    val commissionPercent: Double = 5.0,
    val communeFees: Map<String, Double> = CommuneLubumbashi.values().associate { it.name to it.defaultDeliveryFeeUsd },
    val supportPhone: String = "+243 800 000 001"
) {
    fun feeFor(commune: CommuneLubumbashi): Double {
        return communeFees[commune.name] ?: commune.defaultDeliveryFeeUsd
    }
}

data class Product(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val priceUsd: Double = 0.0,
    val category: ProductCategory = ProductCategory.TECH,
    val categoryName: String = category.label,
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerCommune: String = "",
    val stock: Int = 0,
    val rating: Double = 5.0,
    val reviewCount: Int = 0,
    val isPopular: Boolean = false,
    val isBestOffer: Boolean = false,
    val discountPercent: Int = 0,
    val isNew: Boolean = false,
    val imageUrls: List<String> = emptyList(),
    val moderationStatusName: String = ProductModerationStatus.APPROVED.name,
    val moderationReason: String? = null
) {
    val moderationStatus: ProductModerationStatus
        get() = runCatching { ProductModerationStatus.valueOf(moderationStatusName) }
            .getOrDefault(ProductModerationStatus.APPROVED)

    fun priceCdf(rate: Double = 2800.0): Double = priceUsd * rate
    fun discountedPriceUsd(): Double = if (discountPercent > 0) priceUsd * (1 - discountPercent / 100.0) else priceUsd
    fun discountedPriceCdf(rate: Double = 2800.0): Double = discountedPriceUsd() * rate
}

data class CartItem(
    val product: Product,
    val quantity: Int
) {
    val totalUsd: Double get() = product.discountedPriceUsd() * quantity
}

enum class OrderStatus(val stepNumber: Int, val label: String, val description: String) {
    RECU(1, "Commande reçue", "Votre commande a été enregistrée avec succès sur Lubumarket"),
    CONFIRME(2, "Commande confirmée", "Le vendeur a validé la commande"),
    EN_PREPARATION(3, "Préparation en cours", "Vos articles sont soigneusement emballés par le vendeur"),
    EN_ATTENTE_LIVREUR(4, "En attente du livreur", "Colis prêt, un livreur Lubumarket se charge de la prise en charge"),
    EN_COURS_LIVRAISON(5, "En cours de livraison", "Le livreur est en route vers votre adresse à Lubumbashi"),
    LIVREE(6, "Livrée", "Colis remis en main propre. Merci pour votre confiance !"),
    ANNULEE(7, "Annulée", "Cette commande a été annulée")
}

enum class PaymentMethod(val label: String, val shortCode: String, val details: String, val isWithdrawalMethod: Boolean = true) {
    MPESA("Vodacom M-Pesa", "M-PESA", "Paiement direct mobile via compte M-Pesa RDC", true),
    AIRTEL_MONEY("Airtel Money", "AIRTEL", "Paiement mobile instantané via Airtel Money", true),
    ORANGE_MONEY("Orange Money", "ORANGE", "Paiement sécurisé via Orange Money", true),
    VIREMENT_BANCAIRE("Compte Bancaire (RDC)", "BANQUE", "Virement vers compte bancaire (Rawbank, EquityBCDC, TMB...)", true),
    CASH_LIVRAISON("Cash à la livraison", "CASH", "Payez en espèces (USD ou Francs Congolais) à la réception", false)
}

enum class WithdrawalStatus(val label: String, val firestoreValue: String) {
    EN_ATTENTE("En attente", "EN_ATTENTE"),
    APPROUVE("Approuvé", "APPROUVE"),
    PAYE("Payé", "PAYE"),
    REFUSE("Refusé", "REFUSE");

    companion object {
        fun fromFirestore(value: String?): WithdrawalStatus {
            return values().firstOrNull { it.firestoreValue == value } ?: EN_ATTENTE
        }
    }
}

data class Order(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val sellerIds: List<String> = emptyList(),
    val items: List<CartItem> = emptyList(),
    val deliveryAddress: DeliveryAddress = DeliveryAddress(),
    val deliveryFeeUsd: Double = 0.0,
    val subtotalUsd: Double = 0.0,
    val totalUsd: Double = 0.0,
    val commissionUsd: Double = 0.0,
    val paymentMethod: PaymentMethod = PaymentMethod.MPESA,
    val status: OrderStatus = OrderStatus.RECU,
    val createdAt: String = "",
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    val vendorNotes: String? = null,
    val paymentConfirmed: Boolean = false,
    val paymentReference: String? = null,
    val commissionCredited: Boolean = false,
    val driverPayoutSettled: Boolean = false
)

data class DeliveryJob(
    val orderId: String = "",
    val commune: String = "",
    val quartier: String = "",
    val deliveryFeeUsd: Double = 0.0,
    val status: OrderStatus = OrderStatus.EN_ATTENTE_LIVREUR,
    val assignedDriverId: String? = null
)

data class WithdrawalRequest(
    val id: String = "",
    val vendorId: String = "",
    val vendorName: String = "",
    val amountGrossUsd: Double = 0.0,
    val commissionPercent: Double = 5.0,
    val commissionUsd: Double = 0.0,
    val amountNetUsd: Double = 0.0,
    val paymentMethod: PaymentMethod = PaymentMethod.MPESA,
    val accountDetails: String = "",
    val status: WithdrawalStatus = WithdrawalStatus.EN_ATTENTE,
    val rejectionReason: String? = null,
    val verifiedByAdmin: Boolean = false,
    val createdAt: String = ""
)

data class OwnerWalletConfig(
    val ownerId: String = "",
    val preferredMethod: PaymentMethod = PaymentMethod.MPESA,
    val accountDetails: String = "",
    val accountHolderName: String = ""
)

data class OwnerWithdrawalRequest(
    val id: String = "",
    val ownerId: String = "",
    val amountUsd: Double = 0.0,
    val paymentMethod: PaymentMethod = PaymentMethod.MPESA,
    val accountDetails: String = "",
    val status: WithdrawalStatus = WithdrawalStatus.EN_ATTENTE,
    val rejectionReason: String? = null,
    val verifiedConfirmed: Boolean = false,
    val createdAt: String = ""
)

data class OrderDispute(
    val id: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val reason: String = "",
    val status: DisputeStatus = DisputeStatus.OPEN,
    val adminNotes: String? = null,
    val createdAt: String = ""
)

data class AdminAuditLog(
    val id: String = "",
    val adminId: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    val createdAt: String = ""
)

data class PendingRegistrationData(
    val role: UserRole,
    val fullName: String,
    val phone: String,
    val emailHint: String,
    val commune: String,
    val quartier: String,
    val storeName: String? = null,
    val vehicleType: String? = null,
    val deliveryZone: String? = null
)

enum class AdminAuthMethod(val label: String) {
    GMAIL("Gmail (Propriétaire vérifié)"),
    PHONE_SMS("Téléphone (+243 SMS 6 chiffres)"),
    GMAIL_AND_SMS("Gmail + Double vérification SMS")
}

@IgnoreExtraProperties
data class SuperAdminProfile(
    @DocumentId val id: String = "",
    val adminId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String? = null,
    val role: String = "SUPER_ADMIN",
    val authMethod: String = AdminAuthMethod.GMAIL.name,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun normalizeCongoPhoneE164(raw: String): String {
    val digitsOnly = raw.trim().replace(Regex("[^0-9+]"), "")
    return when {
        digitsOnly.startsWith("+") -> digitsOnly
        digitsOnly.startsWith("243") -> "+$digitsOnly"
        digitsOnly.startsWith("0") -> "+243${digitsOnly.drop(1)}"
        digitsOnly.isNotEmpty() -> "+243$digitsOnly"
        else -> ""
    }
}

