package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.ui.theme.*
import kotlin.math.max

private data class AdminExecutiveNavDestination(
    val tab: AdminSectionTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int,
    val testTag: String,
    val useRedAccent: Boolean = false
)

private fun buildExecutiveDestinations(
    pendingUsersBadge: Int,
    pendingProductsBadge: Int,
    pendingOrdersBadge: Int,
    pendingWithdrawalsBadge: Int
): List<AdminExecutiveNavDestination> = listOf(
    AdminExecutiveNavDestination(
        tab = AdminSectionTab.FINANCES,
        label = "Revenus",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        badgeCount = pendingWithdrawalsBadge,
        testTag = "admin_nav_revenue"
    ),
    AdminExecutiveNavDestination(
        tab = AdminSectionTab.USERS,
        label = "Utilisateurs",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People,
        badgeCount = pendingUsersBadge,
        testTag = "admin_nav_users"
    ),
    AdminExecutiveNavDestination(
        tab = AdminSectionTab.CATALOG,
        label = "Produits",
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2,
        badgeCount = pendingProductsBadge,
        testTag = "admin_nav_products"
    ),
    AdminExecutiveNavDestination(
        tab = AdminSectionTab.ORDERS_SETTINGS,
        label = "Commandes",
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong,
        badgeCount = pendingOrdersBadge,
        testTag = "admin_nav_orders",
        useRedAccent = true
    )
)

/**
 * NavigationRail adaptatif pour tablettes / grands écrans (Utilisateurs, Produits, Commandes, Revenus & Stats).
 */
@Composable
fun AdminExecutiveNavigationRail(
    activeSection: AdminSectionTab,
    pendingUsersBadge: Int,
    pendingProductsBadge: Int,
    pendingOrdersBadge: Int,
    pendingWithdrawalsBadge: Int,
    onSelectSection: (AdminSectionTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = buildExecutiveDestinations(
        pendingUsersBadge = pendingUsersBadge,
        pendingProductsBadge = pendingProductsBadge,
        pendingOrdersBadge = pendingOrdersBadge,
        pendingWithdrawalsBadge = pendingWithdrawalsBadge
    )

    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .testTag("admin_navigation_rail"),
        containerColor = Color.White,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LubumBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = "Administration",
                    tint = Color.White
                )
            }
        }
    ) {
        destinations.forEach { dest ->
            val selected = activeSection == dest.tab
            val activeBg = if (dest.useRedAccent) LubumRedLight else LubumBlueLight
            val activeTint = if (dest.useRedAccent) LubumRedDark else LubumBlue
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectSection(dest.tab) },
                modifier = Modifier.testTag(dest.testTag),
                icon = {
                    BadgedBox(
                        badge = {
                            if (dest.badgeCount > 0) {
                                Badge(containerColor = LubumRed, contentColor = Color.White) {
                                    Text("${dest.badgeCount}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label
                        )
                    }
                },
                label = {
                    Text(
                        text = dest.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = activeTint,
                    selectedTextColor = activeTint,
                    indicatorColor = activeBg,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp), color = BorderColor)

        NavigationRailItem(
            selected = activeSection == AdminSectionTab.WITHDRAWALS,
            onClick = { onSelectSection(AdminSectionTab.WITHDRAWALS) },
            icon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = "Retraits") },
            label = { Text("Retraits", fontSize = 10.sp) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = LubumRed,
                selectedTextColor = LubumRed,
                indicatorColor = LubumRedLight
            )
        )

        NavigationRailItem(
            selected = activeSection == AdminSectionTab.AUDIT,
            onClick = { onSelectSection(AdminSectionTab.AUDIT) },
            icon = { Icon(Icons.Outlined.Policy, contentDescription = "Audit") },
            label = { Text("Audit", fontSize = 10.sp) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = LubumBlue,
                selectedTextColor = LubumBlue,
                indicatorColor = LubumBlueLight
            )
        )
    }
}

/**
 * Barre de navigation exécutive 4 piliers (Bleu & Rouge) sur écrans compacts.
 */
@Composable
fun AdminPrimaryPillarNavBar(
    activeSection: AdminSectionTab,
    pendingUsersBadge: Int,
    pendingProductsBadge: Int,
    pendingOrdersBadge: Int,
    pendingWithdrawalsBadge: Int,
    onSelectSection: (AdminSectionTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = buildExecutiveDestinations(
        pendingUsersBadge = pendingUsersBadge,
        pendingProductsBadge = pendingProductsBadge,
        pendingOrdersBadge = pendingOrdersBadge,
        pendingWithdrawalsBadge = pendingWithdrawalsBadge
    )

    Surface(
        modifier = modifier
            .widthIn(max = 960.dp)
            .testTag("admin_primary_nav_bar"),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BorderColor),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            destinations.forEach { dest ->
                val selected = activeSection == dest.tab
                val bgColor = when {
                    selected && dest.useRedAccent -> LubumRed
                    selected -> LubumBlue
                    else -> LightSurfaceVariant
                }
                val fgColor = if (selected) Color.White else TextPrimary

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectSection(dest.tab) }
                        .testTag(dest.testTag),
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (dest.badgeCount > 0) {
                                    Badge(
                                        containerColor = if (selected && dest.useRedAccent) Color.White else LubumRed,
                                        contentColor = if (selected && dest.useRedAccent) LubumRed else Color.White
                                    ) {
                                        Text("${dest.badgeCount}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = dest.label,
                                tint = fgColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = dest.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = fgColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Graphique statistique des revenus & volumes de commandes (Canvas Compose Bleu & Rouge).
 */
@Composable
fun AdminRevenueStatisticsCard(
    orders: List<Order>,
    totalCommissionsUsd: Double,
    ownerAvailableBalanceUsd: Double,
    useUsd: Boolean,
    cdfRate: Double,
    modifier: Modifier = Modifier
) {
    val deliveredRevenue = orders.filter { it.status == OrderStatus.LIVREE }.sumOf { it.totalUsd }
    val activePipelineRevenue = orders
        .filter { it.status != OrderStatus.LIVREE && it.status != OrderStatus.ANNULEE }
        .sumOf { it.totalUsd }
    val cancelledVolume = orders.filter { it.status == OrderStatus.ANNULEE }.sumOf { it.totalUsd }

    val bars = listOf(
        Triple("Livrées", deliveredRevenue, LubumBlue),
        Triple("En cours", activePipelineRevenue, LubumBlueMid),
        Triple("Commissions 5%", totalCommissionsUsd, AccentAmberDark),
        Triple("Solde Dispo", ownerAvailableBalanceUsd, DeliveryBlue),
        Triple("Annulées", cancelledVolume, LubumRed)
    )
    val maxVal = max(1.0, bars.maxOf { it.second })

    Card(
        modifier = modifier.testTag("admin_revenue_stats_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Statistiques des Revenus",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = LubumBlueDark
                    )
                    Text(
                        text = "Répartition financière en temps réel (Bleu = Actif • Rouge = Annulé)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LubumBlueLight
                ) {
                    Text(
                        text = formatMoney(deliveredRevenue, useUsd, cdfRate),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = LubumBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Graphique à barres Compose Canvas responsive
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
            ) {
                val barCount = bars.size
                val slotWidth = size.width / barCount
                val barWidth = (slotWidth * 0.54f).coerceAtMost(48.dp.toPx())
                val topMargin = 8.dp.toPx()
                val usableHeight = (size.height - topMargin).coerceAtLeast(16f)

                // Ligne de base
                drawLine(
                    color = BorderColor,
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 2f
                )

                bars.forEachIndexed { idx, (_, value, barColor) ->
                    val ratio = (value / maxVal).toFloat().coerceIn(0f, 1f)
                    val barHeight = max(8f, usableHeight * ratio)
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val top = size.height - barHeight

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                bars.forEach { (label, value, color) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatMoney(value, useUsd, cdfRate),
                            fontSize = 9.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
