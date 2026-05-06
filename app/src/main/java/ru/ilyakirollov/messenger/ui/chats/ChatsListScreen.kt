package ru.ilyakirollov.messenger.ui.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ilyakirollov.messenger.R
import ru.ilyakirollov.messenger.data.model.Chat
import ru.ilyakirollov.messenger.ui.components.Avatar
import ru.ilyakirollov.messenger.util.Format

@Composable
fun ChatsListScreen(
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val uid = viewModel.currentUid
    var pendingDelete by remember { mutableStateOf<Chat?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (chats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.chats_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(chats, key = { it.id }) { chat ->
                    ChatRow(
                        chat = chat,
                        currentUid = uid,
                        onClick = { onOpenChat(chat.id) },
                        onLongClick = {
                            // Official channel is shared and never deletable from a client.
                            if (chat.type != Chat.TYPE_CHANNEL) pendingDelete = chat
                        },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        FloatingActionButton(
            onClick = onNewChat,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp),
            shape = CircleShape,
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.new_chat))
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(toDelete.id)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    chat: Chat,
    currentUid: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val otherUid = chat.participants.firstOrNull { it != currentUid }
    val displayName = when (chat.type) {
        Chat.TYPE_CHANNEL -> chat.title.orEmpty().ifBlank { "Канал" }
        Chat.TYPE_GROUP -> chat.title.orEmpty().ifBlank { "Группа" }
        else -> otherUid?.let { chat.participantNicknames[it] }.orEmpty().ifBlank { "Без имени" }
    }
    val color = when (chat.type) {
        Chat.TYPE_CHANNEL -> 0xFF1976D2L
        Chat.TYPE_GROUP -> 0xFF455A64L
        else -> otherUid?.let { chat.participantColors[it] } ?: 0xFF455A64L
    }
    val photo = when (chat.type) {
        Chat.TYPE_CHANNEL, Chat.TYPE_GROUP -> chat.photoUrl
        else -> otherUid?.let { chat.participantPhotoUrls[it] }
    }
    val unread = chat.unreadCounts[currentUid].orZero()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(nickname = displayName, color = color, size = 48.dp, photoUrl = photo)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val preview = chatPreview(chat)
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Format.chatTimestamp(chat.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unread > 0) {
                    Spacer(Modifier.size(4.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(unread.toString(), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

private fun chatPreview(chat: Chat): String {
    return when (chat.lastMessageType) {
        "image" -> "📷 Фото"
        "video" -> "🎥 Видео"
        "voice" -> "🎙 Голосовое сообщение"
        "file" -> "📎 Файл"
        else -> chat.lastMessage.ifBlank { "—" }
    }
}

private fun Long?.orZero(): Long = this ?: 0L
