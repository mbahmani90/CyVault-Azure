package com.cypressit.cyvault.systemdesign.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypressit.cyvault.systemdesign.theme.CyColors

/**
 * A round avatar: the user's [photo] when there is one, otherwise their initials.
 * The initials' colour comes from the name, so the same user always gets the same colour.
 */
@Composable
fun CyAvatar(
    name: String,
    modifier: Modifier = Modifier,
    fallback: String = "",
    photo: ImageBitmap? = null,
    size: Dp = 56.dp,
) {
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
        return
    }

    val label = initials(name, fallback)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(name.ifBlank { fallback })),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = (size.value * 0.4f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * First letters of the first and last word ("Majid Bahmani" → "MB"), or of the only word
 * ("Majid" → "M"). Falls back to the first letter of [fallback] (e.g. the email), then "?".
 */
internal fun initials(name: String, fallback: String = ""): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words.first().first().toString()
        fallback.isNotBlank() -> fallback.trim().first().toString()
        else -> "?"
    }.uppercase()
}

// A plain character sum instead of hashCode(), so the colour is the same on every platform.
private fun avatarColor(key: String): Color =
    CyColors.AvatarPalette[key.sumOf { it.code } % CyColors.AvatarPalette.size]
