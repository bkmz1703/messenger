package ru.ilyakirollov.messenger.ui.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.R
import ru.ilyakirollov.messenger.data.model.Chat
import ru.ilyakirollov.messenger.data.model.Message
import ru.ilyakirollov.messenger.data.model.MessageType
import ru.ilyakirollov.messenger.ui.components.Avatar
import ru.ilyakirollov.messenger.util.Format

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    onStartCall: (callId: String, video: Boolean) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val currentUid = viewModel.currentUid

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var draftTitle by remember(chat?.title) { mutableStateOf(chat?.title.orEmpty()) }

    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::sendImage) }
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::sendVideo) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (name, size) = queryFileMeta(context, uri)
        viewModel.sendFile(uri, name, size)
    }
    val chatPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::changeChatPhoto) }

    LaunchedEffect(error) {
        val msg = error
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val uid = currentUid
                    val isGroup = chat?.type == Chat.TYPE_GROUP
                    val otherUid = chat?.participants?.firstOrNull { it != uid }
                    val title = when {
                        isGroup -> chat?.title.orEmpty()
                        else -> otherUid?.let { chat?.participantNicknames?.get(it) }.orEmpty()
                    }
                    val color = when {
                        isGroup -> 0xFF455A64L
                        else -> otherUid?.let { chat?.participantColors?.get(it) } ?: 0xFF455A64L
                    }
                    val photo = when {
                        isGroup -> chat?.photoUrl
                        else -> otherUid?.let { chat?.participantPhotoUrls?.get(it) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(nickname = title, color = color, size = 36.dp, photoUrl = photo)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title.ifBlank { "Чат" },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (chat?.type != Chat.TYPE_GROUP) {
                        IconButton(onClick = {
                            scope.launch { viewModel.startCall(video = false)?.let { onStartCall(it, false) } }
                        }) { Icon(Icons.Default.Call, contentDescription = stringResource(R.string.call_audio)) }
                        IconButton(onClick = {
                            scope.launch { viewModel.startCall(video = true)?.let { onStartCall(it, true) } }
                        }) { Icon(Icons.Default.Videocam, contentDescription = stringResource(R.string.call_video)) }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.chat_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            if (chat?.type == Chat.TYPE_GROUP) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_change_title)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        draftTitle = chat?.title.orEmpty()
                                        menuOpen = false
                                        renameOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_change_photo)) },
                                    leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        chatPhotoPicker.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    },
                                )
                                if (!chat?.photoUrl.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_clear_photo)) },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.clearChatPhoto()
                                        },
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_delete)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    deleteOpen = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        isMine = msg.senderId == currentUid,
                        isGroup = chat?.type == Chat.TYPE_GROUP,
                    )
                }
            }

            ChatInput(
                input = input,
                recording = recording,
                onInputChange = viewModel::setInput,
                onSendText = viewModel::sendText,
                onPickImage = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest()) },
                onPickVideo = {
                    videoPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.VideoOnly,
                        )
                    )
                },
                onPickFile = { filePicker.launch("*/*") },
                onStartRecord = {
                    if (recordPermission.status.isGranted) viewModel.startVoiceRecording()
                    else recordPermission.launchPermissionRequest()
                },
                onStopRecord = { send -> viewModel.stopVoiceRecording(send) },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.fillMaxWidth(),
        ) { data -> Snackbar(snackbarData = data) }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(R.string.chat_change_title)) },
            text = {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { if (it.length <= 60) draftTitle = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.chat_title_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameChat(draftTitle)
                        renameOpen = false
                    },
                    enabled = draftTitle.trim().isNotEmpty(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    scope.launch {
                        if (viewModel.deleteChatAndExit()) onBack()
                    }
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean, isGroup: Boolean) {
    val align = if (isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (isMine)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp,
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(vertical = 2.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isGroup && !isMine) {
                    Text(
                        message.senderNickname,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                when (MessageType.from(message.type)) {
                    MessageType.IMAGE -> {
                        AsyncImage(
                            model = message.mediaUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    MessageType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }
                    MessageType.VOICE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = textColor)
                            Text(Format.voiceDuration(message.durationMs), color = textColor)
                        }
                    }
                    MessageType.FILE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = textColor)
                            Column {
                                Text(
                                    message.fileName ?: "Файл",
                                    color = textColor,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    Format.fileSize(message.fileSize),
                                    color = textColor.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    MessageType.TEXT, MessageType.SYSTEM -> {
                        Text(message.text, color = textColor)
                    }
                }
                Text(
                    Format.timeOnly(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInput(
    input: String,
    recording: Boolean,
    onInputChange: (String) -> Unit,
    onSendText: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickFile: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: (Boolean) -> Unit,
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (recording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.recording),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Отпустите чтобы отправить",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (showAttachMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
                ) {
                    AttachButton("Фото", Icons.Default.Image) { showAttachMenu = false; onPickImage() }
                    AttachButton("Видео", Icons.Default.Videocam) { showAttachMenu = false; onPickVideo() }
                    AttachButton("Файл", Icons.Default.AttachFile) { showAttachMenu = false; onPickFile() }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                    Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.attach))
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text(stringResource(R.string.message_hint)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    keyboardOptions = KeyboardOptions(),
                    maxLines = 4,
                )
                Spacer(Modifier.width(4.dp))
                if (input.isBlank()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when {
                                            event.changes.any { it.pressed } && !recording -> onStartRecord()
                                            event.changes.all { !it.pressed } && recording -> onStopRecord(true)
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (recording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = stringResource(R.string.hold_to_record),
                            tint = if (recording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(onClick = onSendText) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun queryFileMeta(context: android.content.Context, uri: Uri): Pair<String?, Long> {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null to 0L
    return cursor.use { c ->
        if (!c.moveToFirst()) return@use null to 0L
        val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
        val name = if (nameIdx >= 0) c.getString(nameIdx) else null
        val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
        name to size
    }
}
