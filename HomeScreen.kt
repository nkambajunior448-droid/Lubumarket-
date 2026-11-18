package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.ProductCard
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun HomeScreen(
    products: List<Product>,
    promotions: List<PromotionBanner> = emptyList(),
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: ProductCategory,
    onCategorySelect: (ProductCategory) -> Unit,
    useUsd: Boolean,
    cdfRate: Double = 2800.0,
    onProductClick: (Product) -> Unit,
    onAddToCart: (Product) -> Unit,
    onBuyNow: (Product) -> Unit = onAddToCart,
    onDeliveryBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Search bar
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    placeholder = {
                        Text(
                            text = "Rechercher à Lubumbashi (téléphone, farine, pagne...)",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Rechercher",
                            tint = LubumBlue
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = LubumBlue,
                        unfocusedBorderColor = BorderColor
                    )
                )
            }
        }

        // Hero Banner - Lubumbashi Delivery Priority (Blue & Red)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable(onClick = onDeliveryBannerClick)
                    .testTag("hero_delivery_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(LubumBlueDark, LubumBlue, LubumRedDark)
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = LubumRed
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricScooter,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVRAISON EXPRESS",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Text(
                                text = String.format(Locale.FRENCH, "Dès 2.00 $ (~%,.0f FC)", 2.0 * cdfRate),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Livraison Rapide à Domicile & Bureau",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 22.sp
                        )

                        Text(
                            text = "7 communes desservies : Lubumbashi, Kenya, Katuba, Kampemba, Kamalondo, Ruashi, Annexe.",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Commander maintenant",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dynamic Admin Promotional Banners
        val activePromos = promotions.filter { it.isActive }
        if (activePromos.isNotEmpty() && searchQuery.isEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activePromos) { promo ->
                        Card(
                            modifier = Modifier.width(275.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = LubumBlueLight)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = LubumRed
                                ) {
                                    Text(
                                        text = promo.badgeText.ifBlank { "PROMO" },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = promo.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = LubumBlueDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = promo.subtitle,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Categories Row
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Catégories",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProductCategory.values()) { category ->
                        val isSelected = category == selectedCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelect(category) },
                            label = {
                                Text(
                                    text = category.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (category) {
                                        ProductCategory.TOUS -> Icons.Default.GridView
                                        ProductCategory.TECH -> Icons.Default.PhoneAndroid
                                        ProductCategory.ALIMENTATION -> Icons.Default.LocalGroceryStore
                                        ProductCategory.MODE -> Icons.Default.Checkroom
                                        ProductCategory.MAISON -> Icons.Default.Home
                                        ProductCategory.BEAUTE -> Icons.Default.Spa
                                        ProductCategory.QUINCAILLERIE -> Icons.Default.Build
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) LubumBlue else TextSecondary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LubumBlueContainer,
                                selectedLabelColor = LubumOnBlueContainer
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

        // Popular Products Highlight (Horizontal)
        val popularProducts = products.filter { it.isPopular }
        if (popularProducts.isNotEmpty() && searchQuery.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Les Plus Populaires",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${popularProducts.size} articles",
                            fontSize = 11.sp,
                            color = LubumBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(popularProducts) { product ->
                            Box(modifier = Modifier.width(190.dp)) {
                                ProductCard(
                                    product = product,
                                    useUsd = useUsd,
                                    cdfRate = cdfRate,
                                    onProductClick = { onProductClick(product) },
                                    onAddToCartClick = { onAddToCart(product) },
                                    onBuyNowClick = { onBuyNow(product) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Best Offers / Discounts (Horizontal)
        val offerProducts = products.filter { it.isBestOffer || it.discountPercent > 0 }
        if (offerProducts.isNotEmpty() && searchQuery.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Meilleures Offres & Réductions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = LubumRedDark
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(offerProducts) { product ->
                            Box(modifier = Modifier.width(190.dp)) {
                                ProductCard(
                                    product = product,
                                    useUsd = useUsd,
                                    cdfRate = cdfRate,
                                    onProductClick = { onProductClick(product) },
                                    onAddToCartClick = { onAddToCart(product) },
                                    onBuyNowClick = { onBuyNow(product) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // All Products Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "Résultats de recherche" else "Catalogue Lubumbashi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${products.size} produits trouvés",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // Product Grid items (paired in rows for LazyColumn)
        val productPairs = products.chunked(2)
        if (productPairs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aucun produit ne correspond à votre recherche.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(productPairs) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ProductCard(
                            product = pair[0],
                            useUsd = useUsd,
                            cdfRate = cdfRate,
                            onProductClick = { onProductClick(pair[0]) },
                            onAddToCartClick = { onAddToCart(pair[0]) },
                            onBuyNowClick = { onBuyNow(pair[0]) }
                        )
                    }
                    if (pair.size > 1) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProductCard(
                                product = pair[1],
                                useUsd = useUsd,
                                cdfRate = cdfRate,
                                onProductClick = { onProductClick(pair[1]) },
                                onAddToCartClick = { onAddToCart(pair[1]) },
                                onBuyNowClick = { onBuyNow(pair[1]) }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
