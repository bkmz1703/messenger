package ru.ilyakirollov.messenger.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ru.ilyakirollov.messenger.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.ilyakirollov.messenger.ui.components.Avatar
import ru.ilyakirollov.messenger.util.Format

@Composable
fun StatusViewerScreen(
    statusId: String,
    onBack: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val status = remember(statuses, statusId) { statuses.firstOrNull { it.id == statusId } }
    val isMine = status?.authorId == viewModel.currentUid
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(statusId) { viewModel.markViewed(statusId) }

    if (status == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Статус недоступен", color = Color.White)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (status.type == "image" && !status.mediaUrl.isNullOrBlank()) {
            AsyncImage(
                model = status.mediaUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(status.backgroundColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    status.text,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(nickname = status.authorNickname, color = status.authorColor, size = 36.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(status.authorNickname, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    Format.chatTimestamp(status.createdAt),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (isMine) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White)
                }
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.status_delete_title)) },
                text = { Text(stringResource(R.string.status_delete_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.delete(statusId)
                        onBack()
                    }) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        if (status.text.isNotBlank() && status.type == "image") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
            ) {
                Text(status.text, color = Color.White)
            }
        }

        if (isMine) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Просмотров: ${status.viewers.size}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
