package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.*
import com.example.ui.components.OrderStatusTracker
import com.example.ui.components.getStatusColor
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun DeliveryTrackingScreen(
    orders: List<Order>,
    selectedOrder: Order?,
    disputes: List<OrderDispute> = emptyList(),
    useUsd: Boolean,
    cdfRate: Double = 2800.0,
    onSelectOrder: (Order?) -> Unit,
    onCreateDispute: (orderId: String, reason: String) -> Unit = { _, _ -> },
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDisputeDialog by remember { mutableStateOf(false) }

    if (selectedOrder != null) {
        val orderDisputes = disputes.filter { it.orderId == selectedOrder.id }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("order_detail_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSelectOrder(null) },
                        modifier = Modifier.testTag("back_to_orders_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour aux commandes")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Commande ${selectedOrder.id}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Effectuée le ${selectedOrder.createdAt}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            item {
                OrderStatusTracker(currentStatus = selectedOrder.status)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Livreur Assigné à Lubumbashi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (selectedOrder.assignedDriverName != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(DeliveryBlueLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TwoWheeler,
                                            contentDescription = null,
                                            tint = DeliveryBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = selectedOrder.assignedDriverName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Livreur certifié Lubumarket",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = selectedOrder.assignedDriverPhone ?: "",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = LubumBlue
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        selectedOrder.assignedDriverPhone?.let { phone ->
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            context.startActivity(dialIntent)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("call_driver_btn")
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Appeler", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = AccentAmber
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Un livreur de votre commune sera assigné dès la fin de la préparation.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = LubumBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Adresse de livraison à Lubumbashi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${selectedOrder.deliveryAddress.fullName} (${selectedOrder.deliveryAddress.phone})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = selectedOrder.deliveryAddress.fullAddressDisplay(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                        if (!selectedOrder.vendorNotes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Note spéciale : ${selectedOrder.vendorNotes}",
                                fontSize = 11.sp,
                                color = AccentAmberDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Articles commandés (${selectedOrder.items.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        selectedOrder.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.quantity}x ${item.product.name}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = item.product.sellerName,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = if (useUsd) String.format(Locale.US, "%.2f $", item.totalUsd) else String.format(Locale.FRENCH, "%,.0f FC", item.totalUsd * cdfRate),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Frais livraison :", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                if (useUsd) String.format(Locale.US, "%.2f $", selectedOrder.deliveryFeeUsd) else String.format(Locale.FRENCH, "%,.0f FC", selectedOrder.deliveryFeeUsd * cdfRate),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total (${selectedOrder.paymentMethod.shortCode}) :", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (useUsd) String.format(Locale.US, "%.2f $", selectedOrder.totalUsd) else String.format(Locale.FRENCH, "%,.0f FC", selectedOrder.totalUsd * cdfRate),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = LubumBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedOrder.paymentConfirmed) LubumBlueLight else AccentAmberLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (selectedOrder.paymentConfirmed) {
                                    "Paiement vérifié et confirmé${selectedOrder.paymentReference?.let { " • Réf: $it" } ?: ""}"
                                } else {
                                    "Paiement en attente de confirmation réelle (${selectedOrder.paymentMethod.shortCode})"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedOrder.paymentConfirmed) LubumBlueDark else AccentAmberDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Disputes / Claims Section
            item {
                OutlinedButton(
                    onClick = { showDisputeDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("report_dispute_btn"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LubumRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LubumRed)
                ) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signaler un litige / réclamation", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (orderDisputes.isNotEmpty()) {
                item {
                    Text(
                        text = "Mes réclamations sur cette commande (${orderDisputes.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(orderDisputes) { dispute ->
                    val statusColor = when (dispute.status) {
                        DisputeStatus.OPEN -> LubumRed
                        DisputeStatus.IN_REVIEW -> AccentAmberDark
                        DisputeStatus.RESOLVED -> LubumBlue
                        DisputeStatus.REJECTED -> TextSecondary
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Réclamation #${dispute.id.takeLast(6)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(dispute.status.label, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text(dispute.reason, fontSize = 12.sp)
                            if (!dispute.adminNotes.isNullOrBlank()) {
                                Text("Réponse Administration : ${dispute.adminNotes}", fontSize = 11.sp, color = LubumBlueDark, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if (showDisputeDialog) {
            OrderDisputeDialog(
                orderId = selectedOrder.id,
                onDismiss = { showDisputeDialog = false },
                onSubmit = { reason ->
                    onCreateDispute(selectedOrder.id, reason)
                    showDisputeDialog = false
                }
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("orders_list_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Suivi de vos Livraisons",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lubumbashi à domicile & bureau",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "${orders.size} commandes",
                        fontSize = 12.sp,
                        color = LubumBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (orders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aucune commande en cours",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Vos achats avec livraison à domicile apparaîtront ici.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onExploreClick,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                            ) {
                                Text("Acheter maintenant")
                            }
                        }
                    }
                }
            } else {
                items(orders) { order ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectOrder(order) }
                            .testTag("order_item_${order.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = LubumBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = order.id,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = getStatusColor(order.status).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = order.status.label,
                                        color = getStatusColor(order.status),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "${order.items.size} article(s) • ${order.deliveryAddress.commune.nom.split("(")[0].trim()}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = order.createdAt,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (useUsd) String.format(Locale.US, "%.2f $", order.totalUsd) else String.format(Locale.FRENCH, "%,.0f FC", order.totalUsd * cdfRate),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = LubumBlue
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Voir le suivi",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LubumBlue
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = LubumBlue,
                                        modifier = Modifier.size(16.dp)
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

@Composable
private fun OrderDisputeDialog(
    orderId: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Signaler un litige ($orderId)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LubumRedDark)
                Text("Décrivez le problème rencontré (colis manquant, produit endommagé, retard, erreur de paiement...).", fontSize = 12.sp, color = TextSecondary)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif détaillé de la réclamation *") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (reason.isNotBlank()) onSubmit(reason.trim()) },
                        enabled = reason.trim().length >= 5,
                        colors = ButtonDefaults.buttonColors(containerColor = LubumRed)
                    ) {
                        Text("Envoyer")
                    }
                }
            }
        }
    }
}
