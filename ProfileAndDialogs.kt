package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.*
import com.example.ui.components.ProductPhotoGallery
import com.example.ui.components.ZoomablePhotoViewerDialog
import com.example.ui.components.compressUriToBase64DataUrl
import com.example.ui.components.defaultDrawableForCategory
import com.example.ui.components.resolveImageModel
import com.example.ui.components.resolveProductImageModels
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProfileScreen(
    user: UserProfile,
    useUsd: Boolean,
    cdfRate: Double = 2800.0,
    savedAddresses: List<DeliveryAddress>,
    onToggleCurrency: () -> Unit,
    onAddNewAddress: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("profile_screen"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Profile Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(LubumBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = LubumBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.fullName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = user.phone,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        if (user.email.isNotBlank()) {
                            Text(
                                text = user.email,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "${user.commune} (${user.quartier})",
                            fontSize = 11.sp,
                            color = LubumBlueDark
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = LubumBlue.copy(alpha = 0.12f),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "${user.role.label} • ${user.accountStatus.label}",
                                color = LubumBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Preferences & Currency
        item {
            Text(
                text = "Préférences",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCurrency() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = LubumBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Devise d'affichage", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    text = String.format(Locale.FRENCH, "Taux de référence : 1 USD = %,.0f FC", cdfRate),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LubumBlueContainer
                        ) {
                            Text(
                                text = if (useUsd) "Dollar (USD $)" else "Franc (CDF FC)",
                                color = LubumOnBlueContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Saved Addresses Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Adresses enregistrées à Lubumbashi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(onClick = onAddNewAddress) {
                    Text("+ Ajouter", color = LubumBlue, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (savedAddresses.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucune adresse enregistrée. Ajoutez votre adresse à Lubumbashi pour commander en 1 clic.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(savedAddresses) { addr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${addr.fullName} (${addr.type.label})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = addr.phone,
                                fontSize = 11.sp,
                                color = LubumBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${addr.commune.nom} - ${addr.quartier}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = addr.avenue,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Réf : ${addr.referenceLandmark}",
                            fontSize = 11.sp,
                            color = LubumBlueDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Sign out button
        item {
            OutlinedButton(
                onClick = onSignOutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("profile_signout_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LubumRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LubumRed)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Se déconnecter", fontWeight = FontWeight.Bold)
            }
        }

        // Official Slogan & About LUBUMARKET
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = LubumBlueLight)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LUBUMARKET",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = LubumBlueDark,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "« Achetez en ligne, recevez chez vous ! »",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = LubumRed,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        text = "Application marketplace pour les habitants de Lubumbashi, République Démocratique du Congo.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AddAddressDialog(
    appSettings: AppConfigSettings = AppConfigSettings(),
    onDismiss: () -> Unit,
    onSave: (fullName: String, phone: String, type: DeliveryType, commune: CommuneLubumbashi, quartier: String, avenue: String, landmark: String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+243 ") }
    var type by remember { mutableStateOf(DeliveryType.DOMICILE) }
    var commune by remember { mutableStateOf(CommuneLubumbashi.LUBUMBASHI) }
    var quartier by remember { mutableStateOf("") }
    var avenue by remember { mutableStateOf("") }
    var landmark by remember { mutableStateOf("") }
    var communeExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Nouvelle Adresse à Lubumbashi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = LubumBlueDark
                    )
                }

                item {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Nom complet *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Numéro de téléphone (+243) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Commune de Lubumbashi :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedButton(
                            onClick = { communeExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${commune.nom} (${String.format(Locale.US, "%.2f", appSettings.feeFor(commune))} $)", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = communeExpanded,
                            onDismissRequest = { communeExpanded = false }
                        ) {
                            CommuneLubumbashi.values().forEach { com ->
                                DropdownMenuItem(
                                    text = { Text("${com.nom} (${String.format(Locale.US, "%.2f", appSettings.feeFor(com))} $)") },
                                    onClick = {
                                        commune = com
                                        communeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = quartier,
                        onValueChange = { quartier = it },
                        label = { Text("Quartier (ex: Golf, Gambela...) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = avenue,
                        onValueChange = { avenue = it },
                        label = { Text("Avenue et No de parcelle *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = landmark,
                        onValueChange = { landmark = it },
                        label = { Text("Point de repère / Références *") },
                        placeholder = { Text("ex: Derrière la paroisse, portail noir") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Annuler") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (fullName.isNotBlank() && phone.isNotBlank()) {
                                    onSave(fullName, phone, type, commune, quartier, avenue, landmark)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                        ) {
                            Text("Enregistrer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddProductDialog(
    existingProduct: Product? = null,
    isAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        name: String,
        description: String,
        priceUsd: Double,
        category: ProductCategory,
        stock: Int,
        commune: String,
        isPopular: Boolean,
        isBestOffer: Boolean,
        discountPercent: Int,
        imageUrls: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(existingProduct) { mutableStateOf(existingProduct?.name ?: "") }
    var description by remember(existingProduct) { mutableStateOf(existingProduct?.description ?: "") }
    var priceText by remember(existingProduct) { mutableStateOf(existingProduct?.priceUsd?.toString() ?: "") }
    var stockText by remember(existingProduct) { mutableStateOf(existingProduct?.stock?.toString() ?: "10") }
    var category by remember(existingProduct) { mutableStateOf(existingProduct?.category ?: ProductCategory.TECH) }
    var commune by remember(existingProduct) { mutableStateOf(existingProduct?.sellerCommune ?: "Lubumbashi Centre") }
    var isPopular by remember(existingProduct) { mutableStateOf(existingProduct?.isPopular ?: false) }
    var isBestOffer by remember(existingProduct) { mutableStateOf(existingProduct?.isBestOffer ?: false) }
    var discountText by remember(existingProduct) { mutableStateOf(existingProduct?.discountPercent?.toString() ?: "0") }
    var imageUrls by remember(existingProduct) { mutableStateOf(existingProduct?.imageUrls ?: emptyList()) }
    var urlInput by remember { mutableStateOf("") }
    var isCompressingImages by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            isCompressingImages = true
            scope.launch {
                val compressed = uris.mapNotNull { uri -> compressUriToBase64DataUrl(context, uri) }
                imageUrls = (imageUrls + compressed).take(4)
                isCompressingImages = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (existingProduct == null) "Publier un Produit sur Lubumarket" else "Modifier le Produit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = LubumBlueDark
                    )
                    if (!isAdmin) {
                        Text(
                            text = "Votre produit sera visible dès validation par l'administrateur.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Product Photos Picker + Preview Strip
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Photos du produit (${imageUrls.size}/4) :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isCompressingImages && imageUrls.size < 4,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_product_photos_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isCompressingImages) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Optimisation...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = LubumBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Choisir des photos", fontSize = 12.sp, color = LubumBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Ou coller URL image (https://...)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            FilledIconButton(
                                onClick = {
                                    val clean = urlInput.trim()
                                    if ((clean.startsWith("https://") || clean.startsWith("http://")) && imageUrls.size < 4) {
                                        imageUrls = imageUrls + clean
                                        urlInput = ""
                                    }
                                },
                                enabled = urlInput.trim().startsWith("http") && imageUrls.size < 4,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = LubumBlue)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Ajouter URL")
                            }
                        }

                        if (imageUrls.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(imageUrls) { idx, rawUrl ->
                                    val model = remember(rawUrl, category) { resolveImageModel(rawUrl, category) }
                                    val fallbackRes = remember(category) { defaultDrawableForCategory(category) }
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LubumBlueLight)
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(model)
                                                .error(fallbackRes)
                                                .build(),
                                            contentDescription = "Photo ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        IconButton(
                                            onClick = {
                                                imageUrls = imageUrls.toMutableList().also { it.removeAt(idx) }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(LubumRed)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Retirer photo", tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom du produit *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Catégorie :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedButton(
                            onClick = { categoryExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(category.label, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            ProductCategory.values().filterNot { it == ProductCategory.TOUS }.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.label) },
                                    onClick = {
                                        category = cat
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("Prix ($ USD) *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { stockText = it },
                            label = { Text("Stock dispo") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = commune,
                        onValueChange = { commune = it },
                        label = { Text("Commune de la boutique") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description du produit *") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }

                if (isAdmin) {
                    item {
                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = it },
                            label = { Text("Réduction (%)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isPopular, onCheckedChange = { isPopular = it })
                            Text("Mettre en avant (Populaire)", fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isBestOffer, onCheckedChange = { isBestOffer = it })
                            Text("Badge Meilleure Offre", fontSize = 12.sp)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Annuler") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val price = priceText.toDoubleOrNull() ?: 10.0
                                val stock = stockText.toIntOrNull() ?: 5
                                val discount = discountText.toIntOrNull()?.coerceIn(0, 90) ?: 0
                                if (name.isNotBlank()) {
                                    onSave(
                                        existingProduct?.id,
                                        name,
                                        description,
                                        price,
                                        category,
                                        stock,
                                        commune,
                                        isPopular,
                                        isBestOffer,
                                        discount,
                                        imageUrls
                                    )
                                }
                            },
                            enabled = !isCompressingImages && name.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                        ) {
                            Text(if (existingProduct == null) "Publier" else "Enregistrer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WithdrawalDialog(
    availableBalanceUsd: Double,
    commissionPercent: Double = 5.0,
    cdfRate: Double = 2800.0,
    onDismiss: () -> Unit,
    onConfirm: (amountUsd: Double, method: PaymentMethod, accountDetails: String) -> Unit
) {
    var amountText by remember { mutableStateOf(if (availableBalanceUsd >= 10.0) "10" else String.format(Locale.US, "%.2f", availableBalanceUsd)) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.MPESA) }
    var accountDetails by remember { mutableStateOf("+243 ") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val commission = amount * (commissionPercent / 100.0)
    val net = (amount - commission).coerceAtLeast(0.0)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Demande de Retrait Vendeur",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = LubumBlueDark
                )

                Text(
                    text = String.format(Locale.US, "Solde disponible : %.2f $ • Commission : %.0f%%", availableBalanceUsd, commissionPercent),
                    fontSize = 12.sp,
                    color = LubumBlueDark,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        validationError = null
                    },
                    label = { Text("Montant brut à retirer ($ USD) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Commission Lubumarket (${commissionPercent.toInt()}%) :", fontSize = 11.sp)
                            Text(String.format(Locale.US, "-%.2f $", commission), color = LubumRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Net à recevoir :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(String.format(Locale.FRENCH, "%.2f $ (~%,.0f FC)", net, net * cdfRate), color = LubumBlue, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                }

                Text("Moyen de réception (RDC) :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PaymentMethod.values().filter { it.isWithdrawalMethod }) { m ->
                        val isSel = m == selectedMethod
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedMethod = m
                                if (m == PaymentMethod.VIREMENT_BANCAIRE && accountDetails.trim() == "+243") {
                                    accountDetails = ""
                                } else if (m != PaymentMethod.VIREMENT_BANCAIRE && accountDetails.isBlank()) {
                                    accountDetails = "+243 "
                                }
                            },
                            label = { Text(m.shortCode, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LubumBlueContainer,
                                selectedLabelColor = LubumOnBlueContainer
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = accountDetails,
                    onValueChange = {
                        accountDetails = it
                        validationError = null
                    },
                    label = {
                        Text(
                            if (selectedMethod == PaymentMethod.VIREMENT_BANCAIRE) "Nom banque & Numéro de compte / IBAN *"
                            else "Numéro ${selectedMethod.label} (+243...) *"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (validationError != null) {
                    Text(validationError!!, color = LubumRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when {
                                amount <= 0.0 -> validationError = "Montant invalide."
                                amount > availableBalanceUsd -> validationError = "Le montant dépasse votre solde disponible."
                                accountDetails.trim().length < 5 -> validationError = "Veuillez saisir vos coordonnées de réception."
                                else -> onConfirm(amount, selectedMethod, accountDetails.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LubumBlue)
                    ) {
                        Text("Confirmer le Retrait")
                    }
                }
            }
        }
    }
}

@Composable
fun ProductDetailDialog(
    product: Product,
    useUsd: Boolean,
    cdfRate: Double = 2800.0,
    onDismiss: () -> Unit,
    onAddToCart: (Product, Int) -> Unit,
    onBuyNow: (Product, Int) -> Unit = onAddToCart
) {
    var quantity by remember { mutableStateOf(1) }
    var showZoomViewer by remember { mutableStateOf(false) }
    var zoomInitialIndex by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ProductPhotoGallery(
                        product = product,
                        onImageClick = { index ->
                            zoomInitialIndex = index
                            showZoomViewer = true
                        }
                    )
                }

                item {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Vendeur : ${product.sellerName} (${product.sellerCommune})",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val priceUsd = product.discountedPriceUsd()
                            Text(
                                text = if (useUsd) String.format(Locale.US, "%.2f $", priceUsd) else String.format(Locale.FRENCH, "%,.0f FC", product.discountedPriceCdf(cdfRate)),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = LubumBlue
                            )
                            Text(
                                text = if (useUsd) String.format(Locale.FRENCH, "~%,.0f FC", product.discountedPriceCdf(cdfRate)) else String.format(Locale.US, "~%.2f $", priceUsd),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (quantity > 1) quantity-- }) {
                                Icon(Icons.Default.Remove, contentDescription = "Diminuer")
                            }
                            Text(text = "$quantity", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { quantity++ }) {
                                Icon(Icons.Default.Add, contentDescription = "Augmenter")
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Description :",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = product.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                }

                item {
                    Surface(
                        color = LubumBlueLight,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = LubumBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Livraison à domicile disponible dans toutes les 7 communes de Lubumbashi.",
                                fontSize = 11.sp,
                                color = LubumBlueDark
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onBuyNow(product, quantity)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("detail_buy_now_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Commander", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    onAddToCart(product, quantity)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("detail_add_cart_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = LubumRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Panier", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }

    if (showZoomViewer) {
        val models = remember(product.id, product.imageUrls, product.category) {
            resolveProductImageModels(product)
        }
        ZoomablePhotoViewerDialog(
            images = models,
            productTitle = product.name,
            initialIndex = zoomInitialIndex,
            onDismiss = { showZoomViewer = false }
        )
    }
}
