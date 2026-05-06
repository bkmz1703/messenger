package ru.ilyakirollov.messenger.ui.calls

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.R
import ru.ilyakirollov.messenger.data.model.CallSession
import ru.ilyakirollov.messenger.data.model.User
import ru.ilyakirollov.messenger.ui.components.Avatar
import ru.ilyakirollov.messenger.util.Format

@Composable
fun CallsHistoryScreen(
    onCall: (callId: String, video: Boolean) -> Unit,
    viewModel: CallsHistoryViewModel = hiltViewModel(),
) {
    val list by viewModel.history.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val uid = viewModel.currentUid
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        val msg = error
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.calls_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { call ->
                    CallRow(
                        call = call,
                        currentUid = uid,
                        onRedial = { video ->
                            scope.launch {
                                viewModel.redial(call, video = video)?.let { newId ->
                                    onCall(newId, video)
                                }
                            }
                        },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        FloatingActionButton(
            onClick = {
                viewModel.resetSearch()
                pickerOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.call_new)) }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data -> Snackbar(snackbarData = data) }
    }

    if (pickerOpen) {
        NewCallPickerDialog(
            viewModel = viewModel,
            onDismiss = { pickerOpen = false },
            onCallPlaced = { callId, video ->
                pickerOpen = false
                onCall(callId, video)
            },
        )
    }
}

@Composable
private fun NewCallPickerDialog(
    viewModel: CallsHistoryViewModel,
    onDismiss: () -> Unit,
    onCallPlaced: (callId: String, video: Boolean) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.call_new)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(stringResource(R.string.search_user_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searching) {
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.size(12.dp))
                if (results.isEmpty() && query.isNotBlank() && !searching) {
                    Text(
                        stringResource(R.string.search_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(results, key = { it.uid }) { user ->
                            CallPickerRow(
                                user = user,
                                onCall = { video ->
                                    scope.launch {
                                        viewModel.callUser(user, video)?.let { id ->
                                            onCallPlaced(id, video)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun CallPickerRow(
    user: User,
    onCall: (video: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            nickname = user.nickname,
            color = user.avatarColor,
            size = 40.dp,
            photoUrl = user.photoUrl,
        )
        Spacer(Modifier.width(12.dp))
        Text(user.nickname, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onCall(false) }) {
            Icon(
                Icons.Default.Call,
                contentDescription = stringResource(R.string.call_audio),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = { onCall(true) }) {
            Icon(
                Icons.Default.Videocam,
                contentDescription = stringResource(R.string.call_video),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CallRow(
    call: CallSession,
    currentUid: String?,
    onRedial: (video: Boolean) -> Unit,
) {
    val outgoing = call.callerId == currentUid
    val nick = if (outgoing) call.calleeNickname else call.callerNickname
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRedial(call.video) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(nickname = nick, color = 0xFF455A64, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(nick.ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (outgoing) Icons.Default.CallMade else Icons.Default.CallReceived,
                    contentDescription = null,
                    tint = if (call.status == CallSession.STATUS_MISSED)
                        MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    "${if (call.video) "Видео" else "Аудио"} • ${Format.chatTimestamp(call.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onRedial(false) }) {
            Icon(
                Icons.Default.Call,
                contentDescription = stringResource(R.string.call_audio),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = { onRedial(true) }) {
            Icon(
                Icons.Default.Videocam,
                contentDescription = stringResource(R.string.call_video),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
