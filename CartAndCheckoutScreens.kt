package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun CartScreen(
    cart: List<CartItem>,
    useUsd: Boolean,
    cdfRate: Double = 2800.0,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onProceedToCheckout: () -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtotal = cart.sumOf { it.totalUsd }

    if (cart.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("cart_empty_state"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(LubumBlueContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ShoppingCart,
                        contentDescription = null,
                        tint = LubumBlue,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Votre panier est vide",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Découvrez des centaines de produits vendus à Lubumbashi avec livraison à domicile rapide.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onExploreClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                    modifier = Modifier.testTag("explore_products_button")
                ) {
                    Text("Commencer mes achats")
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("cart_screen")
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Mon Panier (${cart.sumOf { it.quantity }} articles)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(cart) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cart_item_${item.product.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProductThumbnail(
                                product = item.product,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.product.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 2
                                )
                                Text(
                                    text = "Vendeur : ${item.product.sellerName} (${item.product.sellerCommune})",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (useUsd) {
                                        String.format(Locale.US, "%.2f $ / unité", item.product.discountedPriceUsd())
                                    } else {
                                        String.format(Locale.FRENCH, "%,.0f FC / unité", item.product.discountedPriceCdf(cdfRate))
                                    },
                                    fontSize = 11.sp,
                                    color = LubumBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                IconButton(
                                    onClick = { onRemoveItem(item.product.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Supprimer",
                                        tint = LubumRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { onQuantityChange(item.product.id, item.quantity - 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Diminuer", modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        text = "${item.quantity}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    IconButton(
                                        onClick = { onQuantityChange(item.product.id, item.quantity + 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Augmenter", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sous-total articles :",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = if (useUsd) String.format(Locale.US, "%.2f $", subtotal) else String.format(Locale.FRENCH, "%,.0f FC", subtotal * cdfRate),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = LubumBlue
                            )
                        }

                        Button(
                            onClick = onProceedToCheckout,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("proceed_checkout_button")
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Passer la commande", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutDialog(
    cart: List<CartItem>,
    savedAddresses: List<DeliveryAddress>,
    useUsd: Boolean,
    appSettings: AppConfigSettings = AppConfigSettings(),
    defaultCustomerName: String = "",
    defaultCustomerPhone: String = "",
    onDismiss: () -> Unit,
    onConfirmOrder: (DeliveryAddress, PaymentMethod, String?) -> Unit
) {
    val cdfRate = appSettings.cdfExchangeRate
    var selectedDeliveryType by remember { mutableStateOf(DeliveryType.DOMICILE) }
    var selectedAddressIndex by remember { mutableStateOf(0) }
    var useExistingAddress by remember { mutableStateOf(savedAddresses.isNotEmpty()) }

    var customName by remember { mutableStateOf(savedAddresses.firstOrNull()?.fullName ?: defaultCustomerName) }
    var customPhone by remember { mutableStateOf(savedAddresses.firstOrNull()?.phone ?: defaultCustomerPhone.ifBlank { "+243 " }) }
    var customCommune by remember { mutableStateOf(CommuneLubumbashi.LUBUMBASHI) }
    var customQuartier by remember { mutableStateOf("") }
    var customAvenue by remember { mutableStateOf("") }
    var customLandmark by remember { mutableStateOf("") }

    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.MPESA) }
    var orderNotes by remember { mutableStateOf("") }

    val currentCommune = if (useExistingAddress && savedAddresses.isNotEmpty()) {
        savedAddresses[selectedAddressIndex.coerceIn(0, savedAddresses.size - 1)].commune
    } else {
        customCommune
    }

    val subtotal = cart.sumOf { it.totalUsd }
    val deliveryFee = appSettings.feeFor(currentCommune)
    val grandTotal = subtotal + deliveryFee

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .testTag("checkout_sheet"),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Validation & Livraison",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lubumbashi, RDC",
                            fontSize = 12.sp,
                            color = LubumBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // 1. Delivery Type
            item {
                Text(
                    text = "1. Destination de la livraison",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeliveryType.values().forEach { type ->
                        val isSel = type == selectedDeliveryType
                        OutlinedButton(
                            onClick = { selectedDeliveryType = type },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) LubumBlueContainer else Color.Transparent
                            ),
                            border = BorderStroke(1.5.dp, if (isSel) LubumBlue else BorderColor)
                        ) {
                            Text(
                                text = when (type) {
                                    DeliveryType.DOMICILE -> "Domicile"
                                    DeliveryType.BUREAU -> "Bureau"
                                    DeliveryType.AUTRE -> "Autre"
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) LubumBlue else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. Address selector / Input
            item {
                Text(
                    text = "2. Adresse à Lubumbashi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (savedAddresses.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useExistingAddress,
                            onClick = { useExistingAddress = true }
                        )
                        Text("Utiliser une adresse enregistrée", fontSize = 13.sp)
                    }

                    if (useExistingAddress) {
                        savedAddresses.forEachIndexed { idx, addr ->
                            val isSel = idx == selectedAddressIndex
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedAddressIndex = idx },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSel) LubumBlueLight else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSel) BorderStroke(1.5.dp, LubumBlue) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSel,
                                        onClick = { selectedAddressIndex = idx }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "${addr.fullName} (${addr.phone})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${addr.commune.nom} - ${addr.quartier}, ${addr.avenue}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Réf : ${addr.referenceLandmark}",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !useExistingAddress,
                            onClick = { useExistingAddress = false }
                        )
                        Text("Saisir une nouvelle adresse", fontSize = 13.sp)
                    }
                }

                if (!useExistingAddress || savedAddresses.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Nom complet du destinataire *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = customPhone,
                            onValueChange = { customPhone = it },
                            label = { Text("Numéro de téléphone (+243...) *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("Commune de Lubumbashi :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        var communeExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { communeExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${customCommune.nom} (Frais: ${String.format(Locale.US, "%.2f", appSettings.feeFor(customCommune))} $)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LubumBlue
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = communeExpanded,
                                onDismissRequest = { communeExpanded = false }
                            ) {
                                CommuneLubumbashi.values().forEach { com ->
                                    val fee = appSettings.feeFor(com)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(com.nom, fontSize = 13.sp)
                                                Text(
                                                    String.format(Locale.FRENCH, "%.2f $ (~%,.0f FC)", fee, fee * cdfRate),
                                                    fontWeight = FontWeight.Bold,
                                                    color = LubumBlue,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            customCommune = com
                                            communeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customQuartier,
                            onValueChange = { customQuartier = it },
                            label = { Text("Quartier (ex: Golf Malela, Gambela...) *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = customAvenue,
                            onValueChange = { customAvenue = it },
                            label = { Text("Avenue et Numéro de parcelle *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = customLandmark,
                            onValueChange = { customLandmark = it },
                            label = { Text("Point de repère / Références (obligatoire) *") },
                            placeholder = { Text("ex: En face de la station Total, portail bleu") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. Payment Method
            item {
                Text(
                    text = "3. Moyen de paiement (RDC)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                PaymentMethod.values().forEach { method ->
                    val isSel = method == selectedPaymentMethod
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedPaymentMethod = method },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) LubumBlueLight else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSel) BorderStroke(1.5.dp, LubumBlue) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = { selectedPaymentMethod = method }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = method.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = method.details,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. Notes
            item {
                OutlinedTextField(
                    value = orderNotes,
                    onValueChange = { orderNotes = it },
                    label = { Text("Instructions spéciales pour le livreur (optionnel)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 5. Total breakdown & Confirmation button
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sous-total articles :", fontSize = 12.sp)
                            Text(
                                if (useUsd) String.format(Locale.US, "%.2f $", subtotal) else String.format(Locale.FRENCH, "%,.0f FC", subtotal * cdfRate),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Frais livraison (${currentCommune.nom.split("(")[0]}) :", fontSize = 12.sp)
                            Text(
                                if (useUsd) String.format(Locale.US, "%.2f $", deliveryFee) else String.format(Locale.FRENCH, "%,.0f FC", deliveryFee * cdfRate),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentAmberDark
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL À PAYER :", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (useUsd) String.format(Locale.US, "%.2f $", grandTotal) else String.format(Locale.FRENCH, "%,.0f FC", grandTotal * cdfRate),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = LubumBlue
                                )
                                Text(
                                    if (useUsd) String.format(Locale.FRENCH, "~%,.0f FC", grandTotal * cdfRate) else String.format(Locale.US, "~%.2f $", grandTotal),
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val finalAddress = if (useExistingAddress && savedAddresses.isNotEmpty()) {
                            savedAddresses[selectedAddressIndex.coerceIn(0, savedAddresses.size - 1)]
                        } else {
                            DeliveryAddress(
                                id = "addr_${System.currentTimeMillis()}",
                                fullName = customName.ifBlank { "Client Lubumarket" },
                                phone = customPhone.ifBlank { "+243 000 000 000" },
                                type = selectedDeliveryType,
                                commune = customCommune,
                                quartier = customQuartier.ifBlank { "Centre" },
                                avenue = customAvenue.ifBlank { "Avenue Principale" },
                                referenceLandmark = customLandmark.ifBlank { "Près du carrefour" }
                            )
                        }
                        onConfirmOrder(finalAddress, selectedPaymentMethod, orderNotes.ifBlank { null })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_order_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Valider la commande (${if (useUsd) String.format(Locale.US, "%.2f $", grandTotal) else String.format(Locale.FRENCH, "%,.0f FC", grandTotal * cdfRate)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
