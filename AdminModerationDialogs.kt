package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun UserProfileHistoryDialog(
    user: UserProfile,
    orders: List<Order>,
    products: List<Product>,
    useUsd: Boolean,
    cdfRate: Double,
    onDismiss: () -> Unit,
    onUpdateStatus: (AccountStatus) -> Unit
) {
    val userOrders = orders.filter {
        it.customerId == user.userId ||
            it.sellerIds.contains(user.userId) ||
            it.assignedDriverId == user.userId
    }
    val userProducts = products.filter { it.sellerId == user.userId }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Profil & Historique", fontWeight = FontWeight.Black, fontSize = 16.sp, color = LubumBlueDark)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(user.fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${user.role.label} • ${user.accountStatus.label}", fontSize = 12.sp, color = LubumBlue, fontWeight = FontWeight.SemiBold)
                    Text("Téléphone : ${user.phone}", fontSize = 12.sp)
                    Text("E-mail : ${user.email}", fontSize = 12.sp)
                    Text("Localisation : ${user.commune} (${user.quartier})", fontSize = 12.sp, color = TextSecondary)
                    if (!user.storeName.isNullOrBlank()) {
                        Text("Boutique : ${user.storeName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (!user.vehicleType.isNullOrBlank()) {
                        Text("Transport : ${user.vehicleType} • Zone : ${user.deliveryZone ?: ""}", fontSize = 12.sp)
                    }
                }

                if (user.role == UserRole.VENDEUR) {
                    item {
                        Text("Produits publiés : ${userProducts.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LubumBlueDark)
                    }
                }

                item {
                    Text("Activité Commandes (${userOrders.size}) :", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (userOrders.isEmpty()) {
                        Text("Aucune commande liée à ce compte.", fontSize = 12.sp, color = TextSecondary)
                    } else {
                        userOrders.take(6).forEach { ord ->
                            Text(
                                text = "• ${ord.id} : ${ord.status.label} (${formatMoney(ord.totalUsd, useUsd, cdfRate)})",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (user.accountStatus != AccountStatus.APPROVED) {
                            Button(
                                onClick = { onUpdateStatus(AccountStatus.APPROVED) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                            ) {
                                Text("Approuver", fontSize = 11.sp)
                            }
                        }
                        if (user.accountStatus != AccountStatus.BLOCKED) {
                            Button(
                                onClick = { onUpdateStatus(AccountStatus.BLOCKED) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumRed)
                            ) {
                                Text("Bloquer", fontSize = 11.sp)
                            }
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

@Composable
fun ProductModerationReasonDialog(
    productName: String,
    targetStatus: ProductModerationStatus,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${targetStatus.label} : $productName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = LubumRedDark
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif obligatoire pour le vendeur *") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (reason.isNotBlank()) onConfirm(reason.trim()) },
                        enabled = reason.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = LubumRed)
                    ) {
                        Text("Confirmer")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminOrderDetailDialog(
    order: Order,
    approvedDrivers: List<UserProfile>,
    useUsd: Boolean,
    cdfRate: Double,
    onDismiss: () -> Unit,
    onUpdateStatus: (OrderStatus) -> Unit,
    onAssignDriver: (UserProfile) -> Unit,
    onConfirmPayment: (String) -> Unit
) {
    var paymentRef by remember { mutableStateOf(order.paymentReference ?: "") }
    var verifiedTransfer by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Détails Commande ${order.id}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = LubumBlueDark)
                    Text("Créée le : ${order.createdAt}", fontSize = 11.sp, color = TextSecondary)
                    Text("Statut : ${order.status.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LubumBlue)
                }

                item {
                    HorizontalDivider()
                    Text("Client : ${order.customerName} (${order.customerPhone})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Adresse : ${order.deliveryAddress.fullAddressDisplay()}", fontSize = 11.sp, color = TextSecondary)
                    if (!order.vendorNotes.isNullOrBlank()) {
                        Text("Note : ${order.vendorNotes}", fontSize = 11.sp, color = AccentAmberDark)
                    }
                }

                item {
                    HorizontalDivider()
                    Text("Articles (${order.items.size}) :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    order.items.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.quantity}x ${item.product.name}", fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text(formatMoney(item.totalUsd, useUsd, cdfRate), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Sous-total : ${formatMoney(order.subtotalUsd, useUsd, cdfRate)}", fontSize = 11.sp)
                    Text("Livraison : ${formatMoney(order.deliveryFeeUsd, useUsd, cdfRate)}", fontSize = 11.sp)
                    Text("Commission 5% : ${formatMoney(order.commissionUsd, useUsd, cdfRate)}", fontSize = 11.sp, color = AccentAmberDark, fontWeight = FontWeight.Bold)
                    Text("Total : ${formatMoney(order.totalUsd, useUsd, cdfRate)} (${order.paymentMethod.label})", fontSize = 13.sp, fontWeight = FontWeight.Black, color = LubumBlue)
                }

                item {
                    HorizontalDivider()
                    if (order.paymentConfirmed) {
                        Surface(shape = RoundedCornerShape(8.dp), color = LubumBlueLight, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Paiement réel confirmé • Réf : ${order.paymentReference ?: "Vérifiée"} (Commission créditée)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LubumBlueDark,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else if (order.status != OrderStatus.ANNULEE) {
                        Text("Confirmation Paiement Réel (Commission 5%)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LubumRedDark)
                        OutlinedTextField(
                            value = paymentRef,
                            onValueChange = { paymentRef = it },
                            label = { Text("Référence transaction (M-Pesa, Airtel, Banque, Cash) *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = verifiedTransfer, onCheckedChange = { verifiedTransfer = it })
                            Text("Paiement réellement encaissé et vérifié", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onConfirmPayment(paymentRef.trim()) },
                            enabled = verifiedTransfer && paymentRef.trim().length >= 3,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                        ) {
                            Text("Confirmer Paiement & Créditer 5%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    var stExpanded by remember { mutableStateOf(false) }
                    var drvExpanded by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(onClick = { stExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Changer Statut", fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = stExpanded, onDismissRequest = { stExpanded = false }) {
                                OrderStatus.values().forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text(st.label, fontSize = 12.sp) },
                                        onClick = {
                                            onUpdateStatus(st)
                                            stExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(onClick = { drvExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Assigner Livreur", fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = drvExpanded, onDismissRequest = { drvExpanded = false }) {
                                approvedDrivers.forEach { drv ->
                                    DropdownMenuItem(
                                        text = { Text("${drv.fullName} (${drv.vehicleType ?: "Moto"})", fontSize = 12.sp) },
                                        onClick = {
                                            onAssignDriver(drv)
                                            drvExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

@Composable
fun ResolveDisputeDialog(
    dispute: OrderDispute,
    onDismiss: () -> Unit,
    onResolve: (DisputeStatus, String?) -> Unit
) {
    var selectedStatus by remember(dispute) { mutableStateOf(DisputeStatus.RESOLVED) }
    var notes by remember(dispute) { mutableStateOf(dispute.adminNotes ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Traiter Litige (${dispute.orderId})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LubumBlueDark)
                Text("Client : ${dispute.customerName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Motif : ${dispute.reason}", fontSize = 12.sp, color = TextSecondary)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(DisputeStatus.IN_REVIEW, DisputeStatus.RESOLVED, DisputeStatus.REJECTED).forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Décision / Réponse pour le client *") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onResolve(selectedStatus, notes.trim().ifBlank { null }) },
                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                    ) {
                        Text("Enregistrer décision")
                    }
                }
            }
        }
    }
}
