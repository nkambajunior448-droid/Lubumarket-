package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.getStatusColor
import com.example.ui.theme.*
import java.util.Locale

enum class AdminSectionTab(val label: String) {
    FINANCES("Finances"),
    MY_REVENUES("Mes Revenus"),
    WITHDRAWALS("Retraits"),
    USERS("Utilisateurs"),
    DRIVERS("Livreurs"),
    CATALOG("Produits & Promos"),
    ORDERS_SETTINGS("Commandes & Litiges"),
    AUDIT("Journal Audit")
}

@Composable
fun AdminDashboardScreen(
    orders: List<Order>,
    products: List<Product>,
    allUsers: List<UserProfile>,
    categories: List<CategoryConfig>,
    promotions: List<PromotionBanner>,
    vendorWithdrawals: List<WithdrawalRequest>,
    ownerWalletConfig: OwnerWalletConfig?,
    ownerWithdrawals: List<OwnerWithdrawalRequest>,
    orderDisputes: List<OrderDispute>,
    adminAuditLogs: List<AdminAuditLog>,
    appSettings: AppConfigSettings,
    totalPlatformCommissionsUsd: Double,
    ownerAvailableBalanceUsd: Double,
    ownerTotalWithdrawnUsd: Double,
    useUsd: Boolean,
    onUpdateUserStatus: (String, AccountStatus) -> Unit,
    onAddProductClick: () -> Unit,
    onEditProductClick: (Product) -> Unit,
    onModerateProduct: (String, ProductModerationStatus, String?) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onToggleCategory: (CategoryConfig) -> Unit,
    onAddPromotion: (String, String, String) -> Unit,
    onDeletePromotion: (String) -> Unit,
    onUpdateOrderStatus: (Order, OrderStatus) -> Unit,
    onAssignDriver: (String, UserProfile) -> Unit,
    onConfirmOrderPayment: (String, String) -> Unit,
    onSettleDriverPayout: (String) -> Unit,
    onResolveDispute: (String, DisputeStatus, String?) -> Unit,
    onSaveOwnerWalletConfig: (PaymentMethod, String, String) -> Unit,
    onRequestOwnerWithdrawal: (Double, PaymentMethod, String) -> Unit,
    onUpdateVendorWithdrawal: (String, WithdrawalStatus, String?, Boolean) -> Unit,
    onUpdateOwnerWithdrawal: (String, WithdrawalStatus, String?, Boolean) -> Unit,
    onSaveAppSettings: (AppConfigSettings) -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSection by remember { mutableStateOf(AdminSectionTab.FINANCES) }
    var withdrawalSubTab by remember { mutableStateOf(0) }
    var userFilterIndex by remember { mutableStateOf(0) } // 0=Tous, 1=Client, 2=Vendeur, 3=Livreur, 4=En attente, 5=Bloqués
    var productFilterIndex by remember { mutableStateOf(0) } // 0=Tous, 1=En attente, 2=Publiés, 3=Refusés/Bloqués
    var orderFilterIndex by remember { mutableStateOf(0) } // 0=Toutes, 1=En cours, 2=Livrées, 3=À confirmer

    var rejectTarget by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmPaidTarget by remember { mutableStateOf<Triple<Boolean, String, String>?>(null) }
    var inspectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var moderatingProductTarget by remember { mutableStateOf<Pair<Product, ProductModerationStatus>?>(null) }
    var selectedAdminOrder by remember { mutableStateOf<Order?>(null) }
    var selectedDispute by remember { mutableStateOf<OrderDispute?>(null) }

    val cdfRate = appSettings.cdfExchangeRate
    val approvedDrivers = allUsers.filter { it.role == UserRole.LIVREUR && it.accountStatus == AccountStatus.APPROVED }
    val allDrivers = allUsers.filter { it.role == UserRole.LIVREUR }

    // Keep open order dialog in sync with live snapshots
    val liveSelectedOrder = selectedAdminOrder?.let { cur -> orders.firstOrNull { it.id == cur.id } ?: cur }

    val pendingUsersBadge = allUsers.count { it.accountStatus == AccountStatus.PENDING }
    val pendingProductsBadge = products.count { it.moderationStatus == ProductModerationStatus.PENDING }
    val pendingOrdersBadge = orders.count { !it.paymentConfirmed && it.status != OrderStatus.ANNULEE } +
        orderDisputes.count { it.status == DisputeStatus.OPEN || it.status == DisputeStatus.IN_REVIEW }
    val pendingWithdrawalsBadge = vendorWithdrawals.count { it.status == WithdrawalStatus.EN_ATTENTE } +
        ownerWithdrawals.count { it.status == WithdrawalStatus.EN_ATTENTE }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_dashboard")
    ) {
        val isWideScreen = maxWidth >= 640.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                AdminExecutiveNavigationRail(
                    activeSection = activeSection,
                    pendingUsersBadge = pendingUsersBadge,
                    pendingProductsBadge = pendingProductsBadge,
                    pendingOrdersBadge = pendingOrdersBadge,
                    pendingWithdrawalsBadge = pendingWithdrawalsBadge,
                    onSelectSection = { activeSection = it }
                )
                VerticalDivider(color = BorderColor)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Executive Blue & Red Admin Header
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(LubumBlueDark, LubumBlue, LubumRedDark)))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ADMINISTRATION LUBUMARKET",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Gestion Utilisateurs • Produits • Commandes • Revenus",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                                IconButton(
                                    onClick = onSignOutClick,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("admin_signout_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Déconnexion", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // 4-Pillar Executive Navigation Hub (Revenus & Stats, Utilisateurs, Produits, Commandes)
                if (!isWideScreen) {
                    item {
                        AdminPrimaryPillarNavBar(
                            activeSection = activeSection,
                            pendingUsersBadge = pendingUsersBadge,
                            pendingProductsBadge = pendingProductsBadge,
                            pendingOrdersBadge = pendingOrdersBadge,
                            pendingWithdrawalsBadge = pendingWithdrawalsBadge,
                            onSelectSection = { activeSection = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Specialized Operational Section Selector Chips
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(AdminSectionTab.values()) { tab ->
                            val isSel = activeSection == tab
                            FilterChip(
                                selected = isSel,
                                onClick = { activeSection = tab },
                                label = {
                                    Text(
                                        tab.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (tab == AdminSectionTab.WITHDRAWALS || tab == AdminSectionTab.ORDERS_SETTINGS) LubumRed else LubumBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                when (activeSection) {
                    AdminSectionTab.FINANCES -> {
                        item {
                            AdminRevenueStatisticsCard(
                                orders = orders,
                                totalCommissionsUsd = totalPlatformCommissionsUsd,
                                ownerAvailableBalanceUsd = ownerAvailableBalanceUsd,
                                useUsd = useUsd,
                                cdfRate = cdfRate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 960.dp)
                            )
                        }
                        adminFinancialDashboardItems(
                            orders = orders,
                            allUsers = allUsers,
                            products = products,
                            disputes = orderDisputes,
                            vendorWithdrawals = vendorWithdrawals,
                            ownerWithdrawals = ownerWithdrawals,
                            totalCommissionsUsd = totalPlatformCommissionsUsd,
                            ownerAvailableBalanceUsd = ownerAvailableBalanceUsd,
                            ownerTotalWithdrawnUsd = ownerTotalWithdrawnUsd,
                            useUsd = useUsd,
                            cdfRate = cdfRate
                        )
                    }

            AdminSectionTab.MY_REVENUES -> {
                item {
                    OwnerWalletSectionCard(
                        totalCommissionsUsd = totalPlatformCommissionsUsd,
                        availableOwnerBalanceUsd = ownerAvailableBalanceUsd,
                        totalWithdrawnUsd = ownerTotalWithdrawnUsd,
                        ownerConfig = ownerWalletConfig,
                        useUsd = useUsd,
                        cdfRate = cdfRate,
                        onSaveConfig = onSaveOwnerWalletConfig,
                        onRequestWithdrawal = onRequestOwnerWithdrawal
                    )
                }
                item {
                    Text(
                        text = "Historique de mes retraits (${ownerWithdrawals.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                if (ownerWithdrawals.isEmpty()) {
                    item { Text("Aucun retrait propriétaire enregistré.", fontSize = 12.sp, color = TextSecondary) }
                } else {
                    items(ownerWithdrawals) { ow ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${ow.id} • ${ow.paymentMethod.shortCode}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(formatMoney(ow.amountUsd, useUsd, cdfRate), fontWeight = FontWeight.Black, color = LubumBlue, fontSize = 13.sp)
                                }
                                Text(ow.accountDetails, fontSize = 11.sp, color = TextSecondary)
                                Text("Statut : ${ow.status.label}", fontSize = 11.sp, color = LubumBlueDark, fontWeight = FontWeight.Bold)
                                if (!ow.rejectionReason.isNullOrBlank()) {
                                    Text("Motif refus : ${ow.rejectionReason}", fontSize = 11.sp, color = LubumRed)
                                }
                            }
                        }
                    }
                }
            }

            AdminSectionTab.WITHDRAWALS -> {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { withdrawalSubTab = 0 },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (withdrawalSubTab == 0) LubumBlue else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (withdrawalSubTab == 0) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retraits vendeurs (${vendorWithdrawals.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { withdrawalSubTab = 1 },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (withdrawalSubTab == 1) LubumBlue else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (withdrawalSubTab == 1) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Mes retraits (${ownerWithdrawals.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (withdrawalSubTab == 0) {
                    if (vendorWithdrawals.isEmpty()) {
                        item { Text("Aucune demande de retrait vendeur.", fontSize = 12.sp, color = TextSecondary) }
                    } else {
                        items(vendorWithdrawals) { w ->
                            val vendorGrossSales = orders
                                .filter { it.status == OrderStatus.LIVREE && it.sellerIds.contains(w.vendorId) }
                                .sumOf { it.subtotalUsd }
                            val vendorReservedOrPaid = vendorWithdrawals
                                .filter { it.vendorId == w.vendorId && it.status != WithdrawalStatus.REFUSE }
                                .sumOf { it.amountGrossUsd }
                            val vendorAvailableNow = (vendorGrossSales - vendorReservedOrPaid).coerceAtLeast(0.0)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${w.vendorName} (${w.id})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(formatMoney(w.amountNetUsd, useUsd, cdfRate), fontWeight = FontWeight.Black, color = LubumBlue, fontSize = 13.sp)
                                    }
                                    Text(
                                        text = "Brut: ${formatMoney(w.amountGrossUsd, useUsd, cdfRate)} • Com 5%: ${formatMoney(w.commissionUsd, useUsd, cdfRate)}",
                                        fontSize = 11.sp,
                                        color = AccentAmberDark
                                    )
                                    Text(
                                        text = "Solde vendeur restant : ${formatMoney(vendorAvailableNow, useUsd, cdfRate)}",
                                        fontSize = 11.sp,
                                        color = LubumBlueDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("${w.paymentMethod.label} • ${w.accountDetails}", fontSize = 11.sp, color = TextSecondary)
                                    if (!w.rejectionReason.isNullOrBlank()) {
                                        Text("Motif refus : ${w.rejectionReason}", fontSize = 11.sp, color = LubumRed)
                                    }
                                    WithdrawalActionButtons(
                                        status = w.status,
                                        onApprove = { onUpdateVendorWithdrawal(w.id, WithdrawalStatus.APPROUVE, null, false) },
                                        onRequestReject = { rejectTarget = Pair(false, w.id) },
                                        onRequestConfirmPaid = {
                                            confirmPaidTarget = Triple(false, w.id, formatMoney(w.amountNetUsd, useUsd, cdfRate))
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (ownerWithdrawals.isEmpty()) {
                        item { Text("Aucune demande de retrait propriétaire.", fontSize = 12.sp, color = TextSecondary) }
                    } else {
                        items(ownerWithdrawals) { ow ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Propriétaire (${ow.id})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(formatMoney(ow.amountUsd, useUsd, cdfRate), fontWeight = FontWeight.Black, color = LubumBlue, fontSize = 13.sp)
                                    }
                                    Text("${ow.paymentMethod.label} • ${ow.accountDetails}", fontSize = 11.sp, color = TextSecondary)
                                    if (!ow.rejectionReason.isNullOrBlank()) {
                                        Text("Motif refus : ${ow.rejectionReason}", fontSize = 11.sp, color = LubumRed)
                                    }
                                    WithdrawalActionButtons(
                                        status = ow.status,
                                        onApprove = { onUpdateOwnerWithdrawal(ow.id, WithdrawalStatus.APPROUVE, null, false) },
                                        onRequestReject = { rejectTarget = Pair(true, ow.id) },
                                        onRequestConfirmPaid = {
                                            confirmPaidTarget = Triple(true, ow.id, formatMoney(ow.amountUsd, useUsd, cdfRate))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AdminSectionTab.USERS -> {
                item {
                    val chips = listOf(
                        "Tous (${allUsers.size})",
                        "Clients (${allUsers.count { it.role == UserRole.CLIENT }})",
                        "Vendeurs (${allUsers.count { it.role == UserRole.VENDEUR }})",
                        "Livreurs (${allUsers.count { it.role == UserRole.LIVREUR }})",
                        "En attente (${allUsers.count { it.accountStatus == AccountStatus.PENDING }})",
                        "Bloqués/Refusés (${allUsers.count { it.accountStatus == AccountStatus.BLOCKED || it.accountStatus == AccountStatus.REJECTED || it.accountStatus == AccountStatus.SUSPENDED }})"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chips.indices.toList()) { idx ->
                            FilterChip(
                                selected = userFilterIndex == idx,
                                onClick = { userFilterIndex = idx },
                                label = { Text(chips[idx], fontSize = 11.sp) }
                            )
                        }
                    }
                }
                val displayedUsers = allUsers.filter { u ->
                    when (userFilterIndex) {
                        1 -> u.role == UserRole.CLIENT
                        2 -> u.role == UserRole.VENDEUR
                        3 -> u.role == UserRole.LIVREUR
                        4 -> u.accountStatus == AccountStatus.PENDING
                        5 -> u.accountStatus == AccountStatus.BLOCKED || u.accountStatus == AccountStatus.REJECTED || u.accountStatus == AccountStatus.SUSPENDED
                        else -> true
                    }
                }
                items(displayedUsers) { u ->
                    val badgeColor = when (u.accountStatus) {
                        AccountStatus.APPROVED -> LubumBlueContainer
                        AccountStatus.PENDING -> AccentAmberLight
                        AccountStatus.SUSPENDED, AccountStatus.REJECTED, AccountStatus.BLOCKED -> LubumRedLight
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(u.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(6.dp), color = badgeColor) {
                                    Text(
                                        text = "${u.role.name} • ${u.accountStatus.label}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("${u.phone} • ${u.email}", fontSize = 11.sp, color = TextSecondary)
                            Text("${u.commune} (${u.quartier})", fontSize = 11.sp, color = TextSecondary)
                            if (!u.storeName.isNullOrBlank()) {
                                Text("Boutique : ${u.storeName}", fontSize = 11.sp, color = LubumBlueDark, fontWeight = FontWeight.SemiBold)
                            }
                            if (!u.vehicleType.isNullOrBlank()) {
                                Text("Transport : ${u.vehicleType} • Zone : ${u.deliveryZone ?: ""}", fontSize = 11.sp, color = DeliveryBlue)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { inspectedUser = u },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Text("Profil", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (u.accountStatus != AccountStatus.APPROVED) {
                                    Button(
                                        onClick = { onUpdateUserStatus(u.userId, AccountStatus.APPROVED) },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Approuver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (u.accountStatus == AccountStatus.PENDING) {
                                    OutlinedButton(
                                        onClick = { onUpdateUserStatus(u.userId, AccountStatus.REJECTED) },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Refuser", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LubumRed)
                                    }
                                }
                                if (u.accountStatus != AccountStatus.SUSPENDED) {
                                    OutlinedButton(
                                        onClick = { onUpdateUserStatus(u.userId, AccountStatus.SUSPENDED) },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Suspendre", fontSize = 11.sp, color = AccentAmberDark)
                                    }
                                }
                                if (u.accountStatus != AccountStatus.BLOCKED) {
                                    OutlinedButton(
                                        onClick = { onUpdateUserStatus(u.userId, AccountStatus.BLOCKED) },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Bloquer", fontSize = 11.sp, color = LubumRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminSectionTab.DRIVERS -> {
                item {
                    Text(
                        text = "Gestion des Livreurs & Frais de Course (${allDrivers.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LubumBlueDark
                    )
                }
                if (allDrivers.isEmpty()) {
                    item { Text("Aucun livreur inscrit pour le moment.", fontSize = 12.sp, color = TextSecondary) }
                } else {
                    items(allDrivers) { drv ->
                        val activeCount = orders.count {
                            it.assignedDriverId == drv.userId &&
                                (it.status == OrderStatus.EN_COURS_LIVRAISON || it.status == OrderStatus.EN_ATTENTE_LIVREUR)
                        }
                        val drvDelivered = orders.filter { it.assignedDriverId == drv.userId && it.status == OrderStatus.LIVREE }
                        val pendingDriverPayUsd = drvDelivered.filter { !it.driverPayoutSettled }.sumOf { it.deliveryFeeUsd }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(drv.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(drv.accountStatus.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (drv.accountStatus == AccountStatus.APPROVED) LubumBlue else LubumRed)
                                }
                                Text("${drv.phone} • ${drv.vehicleType ?: "Moto"} • Zone: ${drv.deliveryZone ?: drv.commune}", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = "En cours: $activeCount • Livrées: ${drvDelivered.size} • Frais à régler: ${formatMoney(pendingDriverPayUsd, useUsd, cdfRate)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LubumBlueDark
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (drv.accountStatus != AccountStatus.APPROVED) {
                                        Button(
                                            onClick = { onUpdateUserStatus(drv.userId, AccountStatus.APPROVED) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Valider Livreur", fontSize = 11.sp)
                                        }
                                    }
                                    if (drv.accountStatus != AccountStatus.SUSPENDED) {
                                        OutlinedButton(
                                            onClick = { onUpdateUserStatus(drv.userId, AccountStatus.SUSPENDED) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Suspendre", fontSize = 11.sp, color = LubumRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val unsettledDelivered = orders.filter {
                    it.status == OrderStatus.LIVREE && it.assignedDriverId != null && !it.driverPayoutSettled
                }
                item {
                    Text(
                        text = "Courses livrées à régler aux livreurs (${unsettledDelivered.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (unsettledDelivered.isEmpty()) {
                    item { Text("Tous les frais de livraison des courses terminées sont réglés.", fontSize = 12.sp, color = TextSecondary) }
                } else {
                    items(unsettledDelivered) { ord ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ord.id} • ${ord.assignedDriverName ?: "Livreur"}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Frais course : ${formatMoney(ord.deliveryFeeUsd, useUsd, cdfRate)}", fontSize = 11.sp, color = LubumBlue)
                                }
                                Button(
                                    onClick = { onSettleDriverPayout(ord.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                                ) {
                                    Text("Régler frais", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            AdminSectionTab.CATALOG -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Modération & Produits (${products.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Button(onClick = onAddProductClick, colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ajouter", fontSize = 12.sp)
                        }
                    }
                }
                item {
                    val modFilters = listOf(
                        "Tous (${products.size})",
                        "En attente (${products.count { it.moderationStatus == ProductModerationStatus.PENDING }})",
                        "Publiés (${products.count { it.moderationStatus == ProductModerationStatus.APPROVED }})",
                        "Refusés/Bloqués (${products.count { it.moderationStatus == ProductModerationStatus.REJECTED || it.moderationStatus == ProductModerationStatus.BLOCKED }})"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(modFilters.indices.toList()) { idx ->
                            FilterChip(
                                selected = productFilterIndex == idx,
                                onClick = { productFilterIndex = idx },
                                label = { Text(modFilters[idx], fontSize = 11.sp) }
                            )
                        }
                    }
                }
                val filteredCatalog = products.filter { p ->
                    when (productFilterIndex) {
                        1 -> p.moderationStatus == ProductModerationStatus.PENDING
                        2 -> p.moderationStatus == ProductModerationStatus.APPROVED
                        3 -> p.moderationStatus == ProductModerationStatus.REJECTED || p.moderationStatus == ProductModerationStatus.BLOCKED
                        else -> true
                    }
                }
                items(filteredCatalog) { prod ->
                    val statusColor = when (prod.moderationStatus) {
                        ProductModerationStatus.APPROVED -> LubumBlue
                        ProductModerationStatus.PENDING -> AccentAmberDark
                        ProductModerationStatus.REJECTED, ProductModerationStatus.BLOCKED -> LubumRed
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProductThumbnail(
                                    product = prod,
                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = "${formatMoney(prod.priceUsd, useUsd, cdfRate)} • Stock: ${prod.stock} • ${prod.sellerName}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(prod.moderationStatus.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                }
                                IconButton(onClick = { onEditProductClick(prod) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = DeliveryBlue)
                                }
                                IconButton(onClick = { onDeleteProduct(prod.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = LubumRed)
                                }
                            }
                            if (!prod.moderationReason.isNullOrBlank()) {
                                Text("Motif : ${prod.moderationReason}", fontSize = 11.sp, color = LubumRedDark)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (prod.moderationStatus != ProductModerationStatus.APPROVED) {
                                    Button(
                                        onClick = { onModerateProduct(prod.id, ProductModerationStatus.APPROVED, null) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Valider", fontSize = 11.sp)
                                    }
                                }
                                if (prod.moderationStatus != ProductModerationStatus.REJECTED) {
                                    OutlinedButton(
                                        onClick = { moderatingProductTarget = Pair(prod, ProductModerationStatus.REJECTED) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Refuser", fontSize = 11.sp, color = AccentAmberDark)
                                    }
                                }
                                if (prod.moderationStatus != ProductModerationStatus.BLOCKED) {
                                    OutlinedButton(
                                        onClick = { moderatingProductTarget = Pair(prod, ProductModerationStatus.BLOCKED) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Bloquer", fontSize = 11.sp, color = LubumRed)
                                    }
                                }
                            }
                        }
                    }
                }

                // Categories management
                item {
                    var newCatName by remember { mutableStateOf("") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Gestion des Catégories (${categories.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newCatName,
                                    onValueChange = { newCatName = it },
                                    label = { Text("Nouvelle catégorie") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (newCatName.isNotBlank()) {
                                            onAddCategory(newCatName.trim(), "Category")
                                            newCatName = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                                ) {
                                    Text("Ajouter", fontSize = 11.sp)
                                }
                            }
                            categories.forEach { cat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat.name, fontSize = 12.sp)
                                    Switch(checked = cat.isActive, onCheckedChange = { onToggleCategory(cat) })
                                }
                            }
                        }
                    }
                }

                // Promotions & Banners
                item {
                    var promoTitle by remember { mutableStateOf("") }
                    var promoSub by remember { mutableStateOf("") }
                    var promoBadge by remember { mutableStateOf("PROMO") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Promotions & Bannières (${promotions.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = promoBadge,
                                onValueChange = { promoBadge = it },
                                label = { Text("Badge (ex: -20%, FLASH)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = promoTitle,
                                onValueChange = { promoTitle = it },
                                label = { Text("Titre de la bannière") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = promoSub,
                                onValueChange = { promoSub = it },
                                label = { Text("Sous-titre / Détails") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (promoTitle.isNotBlank()) {
                                        onAddPromotion(promoTitle.trim(), promoSub.trim(), promoBadge.trim())
                                        promoTitle = ""
                                        promoSub = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumRed)
                            ) {
                                Text("Publier la bannière", fontWeight = FontWeight.Bold)
                            }
                            promotions.forEach { p ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${p.badgeText}: ${p.title}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(p.subtitle, fontSize = 11.sp, color = TextSecondary)
                                    }
                                    IconButton(onClick = { onDeletePromotion(p.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = LubumRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminSectionTab.ORDERS_SETTINGS -> {
                item {
                    val ordFilters = listOf(
                        "Toutes (${orders.size})",
                        "En cours (${orders.count { it.status != OrderStatus.LIVREE && it.status != OrderStatus.ANNULEE }})",
                        "Livrées (${orders.count { it.status == OrderStatus.LIVREE }})",
                        "Paiement à confirmer (${orders.count { !it.paymentConfirmed && it.status != OrderStatus.ANNULEE }})"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ordFilters.indices.toList()) { idx ->
                            FilterChip(
                                selected = orderFilterIndex == idx,
                                onClick = { orderFilterIndex = idx },
                                label = { Text(ordFilters[idx], fontSize = 11.sp) }
                            )
                        }
                    }
                }

                val filteredOrders = orders.filter { o ->
                    when (orderFilterIndex) {
                        1 -> o.status != OrderStatus.LIVREE && o.status != OrderStatus.ANNULEE
                        2 -> o.status == OrderStatus.LIVREE
                        3 -> !o.paymentConfirmed && o.status != OrderStatus.ANNULEE
                        else -> true
                    }
                }

                items(filteredOrders) { ord ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAdminOrder = ord },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${ord.id} - ${ord.customerName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Surface(shape = RoundedCornerShape(6.dp), color = getStatusColor(ord.status).copy(alpha = 0.15f)) {
                                    Text(
                                        text = ord.status.label,
                                        color = getStatusColor(ord.status),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${ord.deliveryAddress.commune.nom.split("(")[0]} • ${ord.deliveryAddress.quartier} • Livreur: ${ord.assignedDriverName ?: "Non assigné"}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatMoney(ord.totalUsd, useUsd, cdfRate), fontWeight = FontWeight.Black, color = LubumBlue, fontSize = 12.sp)
                                Text(
                                    text = if (ord.paymentConfirmed) "Paiement confirmé (${ord.paymentReference ?: ""})" else "Paiement non confirmé",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ord.paymentConfirmed) LubumBlue else LubumRed
                                )
                            }
                            Button(
                                onClick = { selectedAdminOrder = ord },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Gérer / Confirmer Paiement", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Disputes section
                item {
                    Text(
                        text = "Litiges & Réclamations Clients (${orderDisputes.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LubumRedDark,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (orderDisputes.isEmpty()) {
                    item { Text("Aucun litige signalé.", fontSize = 12.sp, color = TextSecondary) }
                } else {
                    items(orderDisputes) { dispute ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${dispute.orderId} • ${dispute.customerName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(dispute.status.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LubumRed)
                                }
                                Text(dispute.reason, fontSize = 12.sp)
                                if (!dispute.adminNotes.isNullOrBlank()) {
                                    Text("Note admin : ${dispute.adminNotes}", fontSize = 11.sp, color = LubumBlueDark)
                                }
                                OutlinedButton(onClick = { selectedDispute = dispute }) {
                                    Text("Traiter le litige", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // App Configuration Settings
                item {
                    var rateText by remember(appSettings) { mutableStateOf(appSettings.cdfExchangeRate.toString()) }
                    var commText by remember(appSettings) { mutableStateOf(appSettings.commissionPercent.toString()) }
                    var phoneText by remember(appSettings) { mutableStateOf(appSettings.supportPhone) }
                    val editableFees = remember(appSettings) {
                        mutableStateMapOf<String, String>().apply {
                            CommuneLubumbashi.values().forEach { com ->
                                put(com.name, String.format(Locale.US, "%.2f", appSettings.feeFor(com)))
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Paramètres & Frais de Livraison Lubumbashi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = rateText,
                                    onValueChange = { rateText = it },
                                    label = { Text("Taux 1 USD -> FC") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = commText,
                                    onValueChange = { commText = it },
                                    label = { Text("Commission (%)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = phoneText,
                                onValueChange = { phoneText = it },
                                label = { Text("Téléphone Support") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Text("Frais par commune ($ USD) :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            CommuneLubumbashi.values().forEach { com ->
                                OutlinedTextField(
                                    value = editableFees[com.name] ?: "",
                                    onValueChange = { editableFees[com.name] = it },
                                    label = { Text(com.nom, fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                            Button(
                                onClick = {
                                    val newFees = CommuneLubumbashi.values().associate { com ->
                                        com.name to (editableFees[com.name]?.toDoubleOrNull() ?: com.defaultDeliveryFeeUsd)
                                    }
                                    onSaveAppSettings(
                                        AppConfigSettings(
                                            cdfExchangeRate = rateText.toDoubleOrNull() ?: 2800.0,
                                            commissionPercent = commText.toDoubleOrNull() ?: 5.0,
                                            communeFees = newFees,
                                            supportPhone = phoneText
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                            ) {
                                Text("Enregistrer les paramètres", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            AdminSectionTab.AUDIT -> {
                item {
                    Text(
                        text = "Journal d'Audit Administrateur (${adminAuditLogs.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LubumBlueDark
                    )
                }
                if (adminAuditLogs.isEmpty()) {
                    item { Text("Aucune action enregistrée dans le journal.", fontSize = 12.sp, color = TextSecondary) }
                } else {
                    items(adminAuditLogs) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(log.action, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LubumBlue)
                                    Text(log.createdAt, fontSize = 10.sp, color = TextMuted)
                                }
                                Text(log.details, fontSize = 12.sp)
                                Text("${log.targetType} • ${log.targetId} • ${log.adminEmail}", fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            } // AUDIT
            } // when
        } // LazyColumn
        } // Row
    } // BoxWithConstraints

    rejectTarget?.let { (isOwner, id) ->
        RejectWithdrawalReasonDialog(
            onDismiss = { rejectTarget = null },
            onConfirmReject = { reason ->
                if (isOwner) onUpdateOwnerWithdrawal(id, WithdrawalStatus.REFUSE, reason, false)
                else onUpdateVendorWithdrawal(id, WithdrawalStatus.REFUSE, reason, false)
                rejectTarget = null
            }
        )
    }

    confirmPaidTarget?.let { (isOwner, id, amountText) ->
        ConfirmPaymentVerifiedDialog(
            label = if (isOwner) "Retrait Propriétaire $id" else "Retrait Vendeur $id",
            amountDisplay = amountText,
            onDismiss = { confirmPaidTarget = null },
            onConfirmPaid = {
                if (isOwner) onUpdateOwnerWithdrawal(id, WithdrawalStatus.PAYE, null, true)
                else onUpdateVendorWithdrawal(id, WithdrawalStatus.PAYE, null, true)
                confirmPaidTarget = null
            }
        )
    }

    inspectedUser?.let { u ->
        UserProfileHistoryDialog(
            user = u,
            orders = orders,
            products = products,
            useUsd = useUsd,
            cdfRate = cdfRate,
            onDismiss = { inspectedUser = null },
            onUpdateStatus = { newSt ->
                onUpdateUserStatus(u.userId, newSt)
                inspectedUser = null
            }
        )
    }

    moderatingProductTarget?.let { (prod, status) ->
        ProductModerationReasonDialog(
            productName = prod.name,
            targetStatus = status,
            onDismiss = { moderatingProductTarget = null },
            onConfirm = { reason ->
                onModerateProduct(prod.id, status, reason)
                moderatingProductTarget = null
            }
        )
    }

    liveSelectedOrder?.let { ord ->
        AdminOrderDetailDialog(
            order = ord,
            approvedDrivers = approvedDrivers,
            useUsd = useUsd,
            cdfRate = cdfRate,
            onDismiss = { selectedAdminOrder = null },
            onUpdateStatus = { st -> onUpdateOrderStatus(ord, st) },
            onAssignDriver = { drv -> onAssignDriver(ord.id, drv) },
            onConfirmPayment = { ref -> onConfirmOrderPayment(ord.id, ref) }
        )
    }

    selectedDispute?.let { dispute ->
        ResolveDisputeDialog(
            dispute = dispute,
            onDismiss = { selectedDispute = null },
            onResolve = { status, notes ->
                onResolveDispute(dispute.id, status, notes)
                selectedDispute = null
            }
        )
    }
}
