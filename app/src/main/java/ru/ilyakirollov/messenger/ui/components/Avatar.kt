package ru.ilyakirollov.messenger.ui.components

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun Avatar(
    nickname: String,
    color: Long,
    size: Dp = 44.dp,
    photoUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val initial = nickname.trim().firstOrNull()?.uppercase() ?: "?"
    val fontSize = (size.value * 0.4f).sp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
