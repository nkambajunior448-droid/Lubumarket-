package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.getStatusColor
import com.example.ui.components.getWithdrawalStatusColor
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun VendorDashboardScreen(
    user: UserProfile,
    products: List<Product>,
    orders: List<Order>,
    withdrawals: List<WithdrawalRequest>,
    useUsd: Boolean,
    cdfRate: Double = 2800.0,
    commissionPercent: Double = 5.0,
    onAddProductClick: () -> Unit,
    onEditProductClick: (Product) -> Unit = {},
    onDeleteProductClick: (String) -> Unit = {},
    onUpdateOrderStatus: (Order, OrderStatus) -> Unit = { _, _ -> },
    onRequestWithdrawalClick: () -> Unit,
    onSignOutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val vendorProducts = products.filter { it.sellerId == user.userId }
    val totalGrossSales = orders
        .filter { it.status == OrderStatus.LIVREE && it.sellerIds.contains(user.userId) }
        .sumOf { it.subtotalUsd }
    val commissionRate = commissionPercent / 100.0
    val estimatedCommission = totalGrossSales * commissionRate
    val reservedOrPaid = withdrawals
        .filter { it.status != WithdrawalStatus.REFUSE }
        .sumOf { it.amountGrossUsd }
    val availableBalance = (totalGrossSales - reservedOrPaid).coerceAtLeast(0.0)
    val vendorOrders = orders.filter { it.sellerIds.contains(user.userId) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("vendor_dashboard"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Welcome Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LubumBlueContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(LubumBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = user.storeName?.ifBlank { user.fullName } ?: user.fullName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = LubumOnBlueContainer
                            )
                            Text(
                                text = "Vendeur approuvé • ${user.commune} (${user.quartier})",
                                fontSize = 11.sp,
                                color = LubumBlueDark
                            )
                        }
                    }
                    IconButton(onClick = onSignOutClick, modifier = Modifier.testTag("vendor_signout_btn")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Déconnexion", tint = LubumBlueDark)
                    }
                }
            }
        }

        // Economic Model & Stats Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Portefeuille Vendeur & Revenus",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Commission de ${commissionPercent.toInt()} % prélevée uniquement lors de la demande de retrait.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Ventes Totales :", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = if (useUsd) String.format(Locale.US, "%.2f $", totalGrossSales) else String.format(Locale.FRENCH, "%,.0f FC", totalGrossSales * cdfRate),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text("Commission (${commissionPercent.toInt()}%) :", fontSize = 11.sp, color = AccentAmberDark)
                            Text(
                                text = if (useUsd) String.format(Locale.US, "%.2f $", estimatedCommission) else String.format(Locale.FRENCH, "%,.0f FC", estimatedCommission * cdfRate),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmberDark
                            )
                        }

                        Column {
                            Text("Solde Disponible :", fontSize = 11.sp, color = LubumBlue)
                            Text(
                                text = if (useUsd) String.format(Locale.US, "%.2f $", availableBalance) else String.format(Locale.FRENCH, "%,.0f FC", availableBalance * cdfRate),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = LubumBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onRequestWithdrawalClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("request_withdrawal_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Demander un retrait (Mobile Money / Banque)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Vendor Incoming Orders Management
        if (vendorOrders.isNotEmpty()) {
            item {
                Text(
                    text = "Commandes à Préparer (${vendorOrders.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(vendorOrders) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "${order.id} • ${order.customerName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = getStatusColor(order.status).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = order.status.label,
                                    color = getStatusColor(order.status),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${order.deliveryAddress.commune.nom.split("(")[0]} • ${order.deliveryAddress.quartier}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = if (order.paymentConfirmed) "Paiement client vérifié (${order.paymentMethod.shortCode})"
                            else "Paiement client en attente (${order.paymentMethod.shortCode})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (order.paymentConfirmed) LubumBlue else AccentAmberDark
                        )
                        val nextStep: OrderStatus? = when (order.status) {
                            OrderStatus.RECU -> OrderStatus.CONFIRME
                            OrderStatus.CONFIRME -> OrderStatus.EN_PREPARATION
                            OrderStatus.EN_PREPARATION -> OrderStatus.EN_ATTENTE_LIVREUR
                            else -> null
                        }
                        if (nextStep != null) {
                            Button(
                                onClick = { onUpdateOrderStatus(order, nextStep) },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                            ) {
                                Text("Passer à : ${nextStep.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Products Header + Add Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mes Produits (${vendorProducts.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = onAddProductClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = LubumBlueContainer),
                    modifier = Modifier.testTag("add_product_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = LubumBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nouveau Produit", color = LubumBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (vendorProducts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Vous n'avez pas encore publié de produit. Appuyez sur « Nouveau Produit » pour commencer à vendre.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(vendorProducts) { prod ->
                val modColor = when (prod.moderationStatus) {
                    ProductModerationStatus.APPROVED -> LubumBlue
                    ProductModerationStatus.PENDING -> AccentAmberDark
                    ProductModerationStatus.REJECTED, ProductModerationStatus.BLOCKED -> LubumRed
                }
                val isBlocked = prod.moderationStatus == ProductModerationStatus.BLOCKED

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProductThumbnail(
                                product = prod,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text(text = "Stock : ${prod.stock} unités • ${prod.sellerCommune}", fontSize = 11.sp, color = TextSecondary)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (useUsd) String.format(Locale.US, "%.2f $", prod.priceUsd) else String.format(Locale.FRENCH, "%,.0f FC", prod.priceCdf(cdfRate)),
                                        fontWeight = FontWeight.Bold,
                                        color = LubumBlue,
                                        fontSize = 12.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = modColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = prod.moderationStatus.label,
                                            color = modColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (!isBlocked) {
                                IconButton(onClick = { onEditProductClick(prod) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = DeliveryBlue)
                                }
                                IconButton(onClick = { onDeleteProductClick(prod.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = LubumRed)
                                }
                            }
                        }

                        if (!prod.moderationReason.isNullOrBlank()) {
                            Text(
                                text = "Note modération : ${prod.moderationReason}",
                                fontSize = 11.sp,
                                color = LubumRedDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Withdrawals History
        item {
            Text(
                text = "Historique des Retraits (${withdrawals.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (withdrawals.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucune demande de retrait enregistrée.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(withdrawals) { withdr ->
                val statusColor = getWithdrawalStatusColor(withdr.status)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Demande ${withdr.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "${withdr.paymentMethod.label} • ${withdr.accountDetails}", fontSize = 11.sp, color = TextSecondary)
                                Text(text = withdr.createdAt, fontSize = 10.sp, color = TextMuted)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (useUsd) String.format(Locale.US, "Net: %.2f $", withdr.amountNetUsd) else String.format(Locale.FRENCH, "Net: %,.0f FC", withdr.amountNetUsd * cdfRate),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = LubumBlue
                                )
                                Text(
                                    text = String.format(Locale.US, "Com (%.0f%%): %.2f $", withdr.commissionPercent, withdr.commissionUsd),
                                    fontSize = 10.sp,
                                    color = AccentAmberDark
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = statusColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = withdr.status.label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (withdr.status == WithdrawalStatus.REFUSE && !withdr.rejectionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Motif du refus : ${withdr.rejectionReason}",
                                fontSize = 11.sp,
                                color = LubumRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
