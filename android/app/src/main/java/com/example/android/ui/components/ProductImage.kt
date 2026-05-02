package com.example.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

private const val DEFAULT_FALLBACK_EMOJI = "🛒"

@Composable
fun ProductImage(
    imageUrl: String?,
    contentDescription: String?,
    fallbackEmoji: String = DEFAULT_FALLBACK_EMOJI,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val container = modifier
        .size(size)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (imageUrl.isNullOrBlank()) {
        EmojiFallback(emoji = fallbackEmoji, modifier = container)
    } else {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = container,
            contentScale = ContentScale.Crop,
            loading = { EmojiFallback(emoji = fallbackEmoji, modifier = Modifier) },
            error = { EmojiFallback(emoji = fallbackEmoji, modifier = Modifier) },
        )
    }
}

@Composable
private fun EmojiFallback(emoji: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
    }
}
