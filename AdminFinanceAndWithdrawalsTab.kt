package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.*
import com.example.ui.components.getWithdrawalStatusColor
import com.example.ui.theme.*
import java.util.Locale

fun formatMoney(usd: Double, useUsd: Boolean, cdfRate: Double): String {
    return if (useUsd) String.format(Locale.US, "%.2f $", usd)
    else String.format(Locale.FRENCH, "%,.0f FC", usd * cdfRate)
}

fun LazyListScope.adminFinancialDashboardItems(
    orders: List<Order>,
    allUsers: List<UserProfile>,
    products: List<Product>,
    disputes: List<OrderDispute>,
    vendorWithdrawals: List<WithdrawalRequest>,
    ownerWithdrawals: List<OwnerWithdrawalRequest>,
    totalCommissionsUsd: Double,
    ownerAvailableBalanceUsd: Double,
    ownerTotalWithdrawnUsd: Double,
    useUsd: Boolean,
    cdfRate: Double
) {
    val deliveredOrders = orders.filter { it.status == OrderStatus.LIVREE }
    val cumulativeVendorSales = deliveredOrders.sumOf { it.subtotalUsd }
    val vendorUsed = vendorWithdrawals
        .filter { it.status != WithdrawalStatus.REFUSE }
        .sumOf { it.amountGrossUsd }
    val cumulativeVendorBalances = (cumulativeVendorSales - vendorUsed).coerceAtLeast(0.0)
    val pendingVendorCount = vendorWithdrawals.count {
        it.status == WithdrawalStatus.EN_ATTENTE || it.status == WithdrawalStatus.APPROUVE
    }
    val pendingOwnerCount = ownerWithdrawals.count {
        it.status == WithdrawalStatus.EN_ATTENTE || it.status == WithdrawalStatus.APPROUVE
    }

    val clientCount = allUsers.count { it.role == UserRole.CLIENT }
    val vendorCount = allUsers.count { it.role == UserRole.VENDEUR }
    val driverCount = allUsers.count { it.role == UserRole.LIVREUR }
    val pendingAccountsCount = allUsers.count { it.accountStatus == AccountStatus.PENDING }
    val pendingProductsCount = products.count { it.moderationStatus == ProductModerationStatus.PENDING }
    val openDisputesCount = disputes.count { it.status == DisputeStatus.OPEN || it.status == DisputeStatus.IN_REVIEW }

    item {
        Text(
            text = "Vue Globale & Tableau de Bord Financier",
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = LubumBlueDark
        )
    }

    // Platform Entity Counts
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FinanceKpiCard(
                title = "Clients",
                value = "$clientCount",
                color = LubumBlue,
                modifier = Modifier.weight(1f)
            )
            FinanceKpiCard(
                title = "Vendeurs",
                value = "$vendorCount",
                color = LubumBlueDark,
                modifier = Modifier.weight(1f)
            )
            FinanceKpiCard(
                title = "Livreurs",
                value = "$driverCount",
                color = DeliveryBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }

    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinanceKpiCard(
                title = "Produits (${products.size})",
                value = "$pendingProductsCount en attente",
                color = if (pendingProductsCount > 0) LubumRed else LubumBlue,
                modifier = Modifier.weight(1f)
            )
            FinanceKpiCard(
                title = "Commandes (${orders.size})",
                value = "${deliveredOrders.size} livrées",
                color = LubumBlueDark,
                modifier = Modifier.weight(1f)
            )
        }
    }

    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinanceKpiCard(
                title = "Commissions Confirmées (5%)",
                value = formatMoney(totalCommissionsUsd, useUsd, cdfRate),
                color = AccentAmberDark,
                modifier = Modifier.weight(1f)
            )
            FinanceKpiCard(
                title = "Solde Dispo Propriétaire",
                value = formatMoney(ownerAvailableBalanceUsd, useUsd, cdfRate),
                color = LubumBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }

    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinanceKpiCard(
                title = "Total Retraits Propriétaire",
                value = formatMoney(ownerTotalWithdrawnUsd, useUsd, cdfRate),
                color = DeliveryBlue,
                modifier = Modifier.weight(1f)
            )
            FinanceKpiCard(
                title = "Soldes Cumulés Vendeurs",
                value = formatMoney(cumulativeVendorBalances, useUsd, cdfRate),
                color = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f)
            )
        }
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Actions Administrateur Requises", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Surface(shape = RoundedCornerShape(8.dp), color = LubumRedLight) {
                        Text(
                            text = "${pendingVendorCount + pendingOwnerCount + pendingAccountsCount + pendingProductsCount + openDisputesCount} à traiter",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LubumRedDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = "Comptes en attente : $pendingAccountsCount • Produits à valider : $pendingProductsCount • Litiges ouverts : $openDisputesCount",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Retraits vendeurs : $pendingVendorCount • Retraits propriétaire : $pendingOwnerCount",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }

    item {
        Text(
            text = "Historique des Transactions Récentes",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    val paidVendorWithdrawals = vendorWithdrawals.filter { it.status == WithdrawalStatus.PAYE }
    val paidOwnerWithdrawals = ownerWithdrawals.filter { it.status == WithdrawalStatus.PAYE }

    if (deliveredOrders.isEmpty() && paidVendorWithdrawals.isEmpty() && paidOwnerWithdrawals.isEmpty()) {
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Aucune transaction finalisée pour le moment.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    } else {
        items(deliveredOrders.take(8)) { ord ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vente livrée ${ord.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = "${ord.customerName} • ${ord.paymentMethod.shortCode} • ${if (ord.paymentConfirmed) "Paiement confirmé" else "À vérifier"}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatMoney(ord.totalUsd, useUsd, cdfRate), fontWeight = FontWeight.Bold, color = LubumBlue, fontSize = 12.sp)
                        Text(
                            text = if (ord.commissionCredited) "Com: ${formatMoney(ord.commissionUsd, useUsd, cdfRate)}" else "Com non créditée",
                            fontSize = 10.sp,
                            color = if (ord.commissionCredited) AccentAmberDark else TextMuted
                        )
                    }
                }
            }
        }
        items(paidVendorWithdrawals.take(5)) { w ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Paiement Vendeur ${w.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${w.vendorName} • ${w.paymentMethod.shortCode}", fontSize = 10.sp, color = TextSecondary)
                    }
                    Text("-${formatMoney(w.amountNetUsd, useUsd, cdfRate)}", fontWeight = FontWeight.Bold, color = DeliveryBlue, fontSize = 12.sp)
                }
            }
        }
        items(paidOwnerWithdrawals.take(5)) { ow ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = LubumBlueLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Retrait Propriétaire ${ow.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${ow.paymentMethod.shortCode} • ${ow.accountDetails}", fontSize = 10.sp, color = TextSecondary)
                    }
                    Text(formatMoney(ow.amountUsd, useUsd, cdfRate), fontWeight = FontWeight.Bold, color = LubumBlueDark, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FinanceKpiCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 11.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
fun OwnerWalletSectionCard(
    totalCommissionsUsd: Double,
    availableOwnerBalanceUsd: Double,
    totalWithdrawnUsd: Double,
    ownerConfig: OwnerWalletConfig?,
    useUsd: Boolean,
    cdfRate: Double,
    onSaveConfig: (PaymentMethod, String, String) -> Unit,
    onRequestWithdrawal: (Double, PaymentMethod, String) -> Unit
) {
    var selectedMethod by remember(ownerConfig) {
        mutableStateOf(ownerConfig?.preferredMethod ?: PaymentMethod.MPESA)
    }
    var accountDetails by remember(ownerConfig) {
        mutableStateOf(ownerConfig?.accountDetails ?: "+243 ")
    }
    var holderName by remember(ownerConfig) {
        mutableStateOf(ownerConfig?.accountHolderName ?: "Propriétaire LUBUMARKET")
    }
    var withdrawAmount by remember { mutableStateOf("") }
    var feedbackError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = LubumBlueContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MES REVENUS ET RETRAITS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = LubumBlueDark
                )
                Text(
                    text = "Portefeuille strictement réservé aux commissions 5 % des commandes livrées et confirmées.",
                    fontSize = 11.sp,
                    color = LubumOnBlueContainer
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Commissions 5% :", fontSize = 10.sp, color = TextSecondary)
                        Text(formatMoney(totalCommissionsUsd, useUsd, cdfRate), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column {
                        Text("Déjà retiré :", fontSize = 10.sp, color = TextSecondary)
                        Text(formatMoney(totalWithdrawnUsd, useUsd, cdfRate), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeliveryBlue)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Solde Dispo :", fontSize = 10.sp, color = LubumBlueDark)
                        Text(formatMoney(availableOwnerBalanceUsd, useUsd, cdfRate), fontWeight = FontWeight.Black, fontSize = 15.sp, color = LubumBlue)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mes Coordonnées de Réception", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PaymentMethod.values().filter { it.isWithdrawalMethod }) { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method.shortCode, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = holderName,
                    onValueChange = { holderName = it },
                    label = { Text("Nom du titulaire") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = accountDetails,
                    onValueChange = { accountDetails = it },
                    label = {
                        Text(
                            if (selectedMethod == PaymentMethod.VIREMENT_BANCAIRE) "Banque & RIB / IBAN"
                            else "Numéro ${selectedMethod.label} (+243...)"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = {
                        if (accountDetails.trim().length >= 5) {
                            onSaveConfig(selectedMethod, accountDetails.trim(), holderName.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_owner_coords_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enregistrer mes coordonnées", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Demander un retrait de mes commissions", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                OutlinedTextField(
                    value = withdrawAmount,
                    onValueChange = {
                        withdrawAmount = it
                        feedbackError = null
                    },
                    label = { Text("Montant à retirer ($ USD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (feedbackError != null) {
                    Text(feedbackError!!, color = LubumRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val amt = withdrawAmount.toDoubleOrNull() ?: 0.0
                        when {
                            amt <= 0.0 -> feedbackError = "Veuillez saisir un montant valide."
                            amt > availableOwnerBalanceUsd -> feedbackError = "Montant supérieur à votre solde disponible."
                            accountDetails.trim().length < 5 -> feedbackError = "Veuillez renseigner vos coordonnées de réception."
                            else -> {
                                onRequestWithdrawal(amt, selectedMethod, accountDetails.trim())
                                withdrawAmount = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("owner_request_withdrawal_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Demander un retrait propriétaire", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun WithdrawalActionButtons(
    status: WithdrawalStatus,
    onApprove: () -> Unit,
    onRequestReject: () -> Unit,
    onRequestConfirmPaid: () -> Unit
) {
    if (status == WithdrawalStatus.PAYE || status == WithdrawalStatus.REFUSE) {
        Text(
            text = "Décision finale enregistrée : ${status.label}",
            fontSize = 11.sp,
            color = getWithdrawalStatusColor(status),
            fontWeight = FontWeight.Bold
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (status == WithdrawalStatus.EN_ATTENTE) {
                OutlinedButton(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Text("Approuver", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeliveryBlue)
                }
            }
            OutlinedButton(
                onClick = onRequestReject,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text("Refuser", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LubumRed)
            }
            Button(
                onClick = onRequestConfirmPaid,
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text("Confirmer Payé", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RejectWithdrawalReasonDialog(
    onDismiss: () -> Unit,
    onConfirmReject: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Motif du refus obligatoire", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LubumRedDark)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Expliquez la raison du refus *") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (reason.isNotBlank()) onConfirmReject(reason.trim()) },
                        enabled = reason.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = LubumRed)
                    ) {
                        Text("Confirmer le refus")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmPaymentVerifiedDialog(
    label: String,
    amountDisplay: String,
    onDismiss: () -> Unit,
    onConfirmPaid: () -> Unit
) {
    var verified by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Confirmation après vérification réelle", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LubumBlueDark)
                Text(
                    text = "Vous êtes sur le point de marquer comme PAYÉ : $label ($amountDisplay).",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = verified, onCheckedChange = { verified = it })
                    Text(
                        text = "Je confirme avoir vérifié le transfert réel sur l'opérateur / la banque.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirmPaid,
                        enabled = verified,
                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                    ) {
                        Text("Marquer Payé")
                    }
                }
            }
        }
    }
}
