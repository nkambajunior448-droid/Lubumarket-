package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.getStatusColor
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun DriverDashboardScreen(
    user: UserProfile,
    orders: List<Order>,
    availableJobs: List<DeliveryJob>,
    useUsd: Boolean,
    cdfRate: Double = 2800.0,
    onAcceptOrder: (String) -> Unit,
    onUpdateStatus: (Order, OrderStatus) -> Unit,
    onSignOutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val myActiveDeliveries = orders.filter {
        it.assignedDriverId == user.userId && (it.status == OrderStatus.EN_COURS_LIVRAISON || it.status == OrderStatus.EN_ATTENTE_LIVREUR)
    }
    val openJobs = availableJobs.filter { it.assignedDriverId == null }
    val deliveredOrders = orders.filter {
        it.assignedDriverId == user.userId && it.status == OrderStatus.LIVREE
    }
    val settledFeesUsd = deliveredOrders.filter { it.driverPayoutSettled }.sumOf { it.deliveryFeeUsd }
    val pendingFeesUsd = deliveredOrders.filter { !it.driverPayoutSettled }.sumOf { it.deliveryFeeUsd }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("driver_dashboard"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Driver Profile Card
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
                            Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = user.fullName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = LubumBlueDark
                            )
                            Text(
                                text = "Livreur Lubumbashi • ${user.vehicleType ?: "Moto Express"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!user.deliveryZone.isNullOrBlank()) {
                                Text(
                                    text = "Zone : ${user.deliveryZone}",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSignOutClick, modifier = Modifier.testTag("driver_signout_btn")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Déconnexion", tint = LubumBlueDark)
                    }
                }
            }
        }

        // Driver Earnings & Rides Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Courses livrées", fontSize = 11.sp, color = TextSecondary)
                        Text("${deliveredOrders.size}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = LubumBlue)
                    }
                    Column {
                        Text("Frais réglés", fontSize = 11.sp, color = TextSecondary)
                        Text(formatMoney(settledFeesUsd, useUsd, cdfRate), fontWeight = FontWeight.Black, fontSize = 15.sp, color = LubumBlueDark)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("À régler", fontSize = 11.sp, color = AccentAmberDark)
                        Text(formatMoney(pendingFeesUsd, useUsd, cdfRate), fontWeight = FontWeight.Black, fontSize = 15.sp, color = AccentAmberDark)
                    }
                }
            }
        }

        // Active Deliveries (Mes courses en cours)
        item {
            Text(
                text = "Mes Courses en Cours (${myActiveDeliveries.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (myActiveDeliveries.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucune course en cours. Prenez en charge une commande disponible ci-dessous.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(myActiveDeliveries) { order ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_active_order_${order.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Course ${order.id}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = getStatusColor(order.status).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = order.status.label,
                                    color = getStatusColor(order.status),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = LubumBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "${order.customerName} (${order.customerPhone})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = LubumRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "${order.deliveryAddress.commune.nom} - ${order.deliveryAddress.quartier}",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = order.deliveryAddress.avenue,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Réf : ${order.deliveryAddress.referenceLandmark}",
                                    fontSize = 11.sp,
                                    color = AccentAmberDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customerPhone}"))
                                    context.startActivity(dialIntent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Appeler", fontSize = 12.sp)
                            }

                            if (order.status == OrderStatus.EN_ATTENTE_LIVREUR) {
                                Button(
                                    onClick = { onUpdateStatus(order, OrderStatus.EN_COURS_LIVRAISON) },
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DeliveryBlue)
                                ) {
                                    Text("Démarrer course", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (order.status == OrderStatus.EN_COURS_LIVRAISON) {
                                Button(
                                    onClick = { onUpdateStatus(order, OrderStatus.LIVREE) },
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Confirmer Livrée", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Available Delivery Jobs (non-PII dispatch board)
        item {
            Text(
                text = "Commandes en attente de livreur (${openJobs.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (openJobs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Toutes les commandes de Lubumbashi sont prises en charge.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(openJobs) { job ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Commande ${job.orderId}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "${job.commune.split("(")[0].trim()} • ${job.quartier}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "Frais course : ${if (useUsd) String.format(Locale.US, "%.2f $", job.deliveryFeeUsd) else String.format(Locale.FRENCH, "%,.0f FC", job.deliveryFeeUsd * cdfRate)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LubumBlue
                            )
                        }

                        Button(
                            onClick = { onAcceptOrder(job.orderId) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                            modifier = Modifier.testTag("driver_take_order_${job.orderId}")
                        ) {
                            Text("Prendre en charge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Completed Deliveries
        if (deliveredOrders.isNotEmpty()) {
            item {
                Text(
                    text = "Livraisons effectuées (${deliveredOrders.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            items(deliveredOrders) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Livré : ${order.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "${order.customerName} - ${order.deliveryAddress.commune.nom.split("(")[0]}", fontSize = 11.sp, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatMoney(order.deliveryFeeUsd, useUsd, cdfRate), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LubumBlue)
                            Text(
                                text = if (order.driverPayoutSettled) "Frais réglés" else "À régler",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (order.driverPayoutSettled) LubumBlue else AccentAmberDark
                            )
                        }
                    }
                }
            }
        }
    }
}
