package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.ui.theme.LubumBlue
import com.example.ui.theme.LubumBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

fun defaultDrawableForCategory(category: ProductCategory): Int {
    return when (category) {
        ProductCategory.TECH -> R.drawable.img_prod_smartphone_1794545054563
        ProductCategory.ALIMENTATION -> R.drawable.img_prod_alimentation_1794545054755
        ProductCategory.MAISON -> R.drawable.img_prod_solaire_1794545055327
        ProductCategory.MODE, ProductCategory.BEAUTE -> R.drawable.img_prod_mode_beaute_1794545055955
        ProductCategory.QUINCAILLERIE -> R.drawable.img_prod_quincaillerie_1794545056313
        ProductCategory.TOUS -> R.drawable.img_prod_smartphone_1794545054563
    }
}

fun resolveImageModel(rawUrl: String, fallbackCategory: ProductCategory): Any {
    val trimmed = rawUrl.trim()
    if (trimmed.startsWith("res://")) {
        val key = trimmed.removePrefix("res://")
        return when {
            key.contains("smartphone") -> R.drawable.img_prod_smartphone_1794545054563
            key.contains("alimentation") -> R.drawable.img_prod_alimentation_1794545054755
            key.contains("solaire") -> R.drawable.img_prod_solaire_1794545055327
            key.contains("mode") || key.contains("beaute") -> R.drawable.img_prod_mode_beaute_1794545055955
            key.contains("quincaillerie") -> R.drawable.img_prod_quincaillerie_1794545056313
            key.contains("logo") -> R.drawable.ic_lubumarket_logo
            else -> defaultDrawableForCategory(fallbackCategory)
        }
    }
    if (trimmed.startsWith("data:image") && trimmed.contains("base64,")) {
        val b64 = trimmed.substringAfter("base64,")
        return runCatching { Base64.decode(b64, Base64.DEFAULT) }
            .getOrElse { defaultDrawableForCategory(fallbackCategory) }
    }
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("content://")) {
        return trimmed
    }
    return defaultDrawableForCategory(fallbackCategory)
}

fun resolveProductImageModels(product: Product): List<Any> {
    val resolved = product.imageUrls
        .filter { it.isNotBlank() }
        .map { resolveImageModel(it, product.category) }
    return if (resolved.isNotEmpty()) resolved else listOf(defaultDrawableForCategory(product.category))
}

suspend fun compressUriToBase64DataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 560) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@runCatching null

        val targetMax = 480f
        val scale = minOf(targetMax / decoded.width, targetMax / decoded.height, 1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 55, out)
        if (scaled != decoded) scaled.recycle()
        decoded.recycle()
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$encoded"
    }.getOrNull()
}

@Composable
fun ProductThumbnail(
    product: Product,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val fallbackRes = remember(product.category) { defaultDrawableForCategory(product.category) }
    val primaryModel = remember(product.id, product.imageUrls, product.category) {
        resolveProductImageModels(product).first()
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(primaryModel)
            .crossfade(true)
            .error(fallbackRes)
            .placeholder(fallbackRes)
            .build(),
        contentDescription = product.name,
        contentScale = contentScale,
        modifier = modifier.background(LubumBlueLight)
    )
}

@Composable
fun ProductPhotoGallery(
    product: Product,
    modifier: Modifier = Modifier,
    onImageClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val fallbackRes = remember(product.category) { defaultDrawableForCategory(product.category) }
    val models = remember(product.id, product.imageUrls, product.category) {
        resolveProductImageModels(product)
    }
    var selectedIndex by remember(product.id) { mutableStateOf(0) }
    val activeIdx = selectedIndex.coerceIn(0, models.lastIndex)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LubumBlueLight)
                .clickable { onImageClick(activeIdx) }
                .testTag("product_gallery_hero")
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(models[activeIdx])
                    .crossfade(true)
                    .error(fallbackRes)
                    .placeholder(fallbackRes)
                    .build(),
                contentDescription = "${product.name} photo ${activeIdx + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoomer",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Zoom (${activeIdx + 1}/${models.size})",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (models.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(models) { index, model ->
                    val isSelected = index == activeIdx
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) LubumBlue else Color.LightGray,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedIndex = index }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(model)
                                .crossfade(true)
                                .error(fallbackRes)
                                .build(),
                            contentDescription = "Miniature ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomablePhotoViewerDialog(
    images: List<Any>,
    productTitle: String,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))) }
    var scale by remember(currentIndex) { mutableStateOf(1f) }
    var offset by remember(currentIndex) { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("zoom_photo_viewer"),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (images.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(images[currentIndex])
                            .crossfade(true)
                            .build(),
                        contentDescription = productTitle,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentIndex) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    scale = newScale
                                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                                }
                            }
                            .pointerInput(currentIndex) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1.2f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                        }
                                    }
                                )
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }

                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(14.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$productTitle (${currentIndex + 1}/${images.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer le zoom", tint = Color.White)
                    }
                }

                // Bottom Zoom + Navigation Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (images.size > 1) {
                        IconButton(
                            onClick = {
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else images.lastIndex
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Photo précédente", tint = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                                scale = (scale - 0.5f).coerceAtLeast(1f)
                                if (scale == 1f) offset = Offset.Zero
                            }
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Dézoomer")
                        }
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        FilledIconButton(
                            onClick = { scale = (scale + 0.5f).coerceAtMost(4f) }
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoomer")
                        }
                    }

                    if (images.size > 1) {
                        IconButton(
                            onClick = {
                                currentIndex = (currentIndex + 1) % images.size
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Photo suivante", tint = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}
