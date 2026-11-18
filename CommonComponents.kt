package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun LubumarketHeader(
    currentRole: UserRole,
    useUsd: Boolean,
    onToggleCurrency: () -> Unit,
    cartItemCount: Int,
    onCartClick: () -> Unit,
    isSignedIn: Boolean = false,
    onSignOutClick: () -> Unit = {}
) {
    Surface(
        color = LubumBlue,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_lubumarket_logo),
                            contentDescription = "Logo LUBUMARKET",
                            modifier = Modifier.size(34.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "LUBUMARKET",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Achetez en ligne, recevez chez vous !",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onToggleCurrency,
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("currency_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CurrencyExchange,
                                contentDescription = "Changer devise",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (useUsd) "USD ($)" else "CDF (FC)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (currentRole == UserRole.CLIENT) {
                        IconButton(
                            onClick = onCartClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("header_cart_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (cartItemCount > 0) {
                                        Badge(containerColor = LubumRed, contentColor = Color.White) {
                                            Text(text = cartItemCount.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Panier",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    if (isSignedIn) {
                        IconButton(
                            onClick = onSignOutClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("header_signout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Se déconnecter",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    useUsd: Boolean,
    cdfRate: Double = 2800.0,
    onProductClick: () -> Unit,
    onAddToCartClick: () -> Unit,
    onBuyNowClick: () -> Unit = onAddToCartClick
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProductClick)
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LubumBlueLight)
            ) {
                ProductThumbnail(
                    product = product,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Subtle bottom scrim for badge readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                )

                if (product.discountPercent > 0) {
                    Surface(
                        color = LubumRed,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "-${product.discountPercent}%",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (product.isNew) {
                    Surface(
                        color = LubumBlue,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "NOUVEAU",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (product.imageUrls.size > 1) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${product.imageUrls.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${product.sellerName} • ${product.sellerCommune}",
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            val priceUsd = product.discountedPriceUsd()
            Column {
                if (product.discountPercent > 0) {
                    Text(
                        text = if (useUsd) String.format(Locale.US, "%.2f $", product.priceUsd)
                        else String.format(Locale.FRENCH, "%,.0f FC", product.priceCdf(cdfRate)),
                        fontSize = 10.sp,
                        color = TextMuted,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                Text(
                    text = if (useUsd) String.format(Locale.US, "%.2f $", priceUsd)
                    else String.format(Locale.FRENCH, "%,.0f FC", product.discountedPriceCdf(cdfRate)),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = LubumBlue
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBuyNowClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("order_now_${product.id}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LubumBlue),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Commander",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                FilledIconButton(
                    onClick = onAddToCartClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("add_to_cart_${product.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LubumRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddShoppingCart,
                        contentDescription = "Ajouter au panier",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OrderStatusTracker(currentStatus: OrderStatus) {
    val activeSteps = listOf(
        OrderStatus.RECU,
        OrderStatus.CONFIRME,
        OrderStatus.EN_PREPARATION,
        OrderStatus.EN_ATTENTE_LIVREUR,
        OrderStatus.EN_COURS_LIVRAISON,
        OrderStatus.LIVREE
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Progression de la Livraison",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (currentStatus == OrderStatus.ANNULEE) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LubumRedLight
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = LubumRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Cette commande a été annulée.",
                            color = LubumRedDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                activeSteps.forEachIndexed { index, status ->
                    val isDone = currentStatus.stepNumber >= status.stepNumber
                    val isCurrent = currentStatus == status

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isDone) LubumBlue else BorderColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            if (index < activeSteps.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(26.dp)
                                        .background(if (currentStatus.stepNumber > status.stepNumber) LubumBlue else BorderColor)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.padding(bottom = if (index < activeSteps.lastIndex) 10.dp else 0.dp)) {
                            Text(
                                text = status.label,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else if (isDone) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (isCurrent) LubumBlue else if (isDone) MaterialTheme.colorScheme.onSurface else TextMuted
                            )
                            if (isCurrent) {
                                Text(
                                    text = status.description,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getStatusColor(status: OrderStatus): Color {
    return when (status) {
        OrderStatus.RECU -> StatusReceived
        OrderStatus.CONFIRME -> StatusConfirmed
        OrderStatus.EN_PREPARATION -> StatusPreparing
        OrderStatus.EN_ATTENTE_LIVREUR -> StatusWaitingDriver
        OrderStatus.EN_COURS_LIVRAISON -> StatusInTransit
        OrderStatus.LIVREE -> StatusDelivered
        OrderStatus.ANNULEE -> StatusCancelled
    }
}

fun getWithdrawalStatusColor(status: WithdrawalStatus): Color {
    return when (status) {
        WithdrawalStatus.EN_ATTENTE -> AccentAmberDark
        WithdrawalStatus.APPROUVE -> DeliveryBlue
        WithdrawalStatus.PAYE -> LubumBlue
        WithdrawalStatus.REFUSE -> LubumRed
    }
}
