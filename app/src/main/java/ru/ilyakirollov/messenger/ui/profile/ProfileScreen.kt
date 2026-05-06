package ru.ilyakirollov.messenger.ui.profile

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ilyakirollov.messenger.R
import ru.ilyakirollov.messenger.ui.components.Avatar

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val color by viewModel.avatarColor.collectAsStateWithLifecycle()
    val photoUrl by viewModel.photoUrl.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val phoneNumber by viewModel.phoneNumber.collectAsStateWithLifecycle()
    val phoneStep by viewModel.phoneStep.collectAsStateWithLifecycle()
    var editingNick by remember { mutableStateOf(false) }
    var editingColor by remember { mutableStateOf(false) }
    var draftNick by remember(nickname) { mutableStateOf(nickname.orEmpty()) }
    val activeColor = color ?: 0xFF455A64

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let(viewModel::updateAvatarPhoto)
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        val msg = error
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.size(16.dp))
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(
                    nickname = nickname.orEmpty(),
                    color = activeColor,
                    size = 120.dp,
                    photoUrl = photoUrl,
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                    )
                }
            }
            Text(
                nickname.orEmpty().ifBlank { "—" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { editingNick = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.profile_change_nickname))
            }
            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.profile_change_photo))
            }
            OutlinedButton(onClick = { editingColor = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ColorLens, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.profile_change_avatar))
            }
            if (!photoUrl.isNullOrBlank()) {
                TextButton(
                    onClick = { viewModel.clearAvatarPhoto() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.profile_clear_photo))
                }
            }

            // Phone link section: shows the linked number if any, otherwise a button to attach
            // one. Linking uses Firebase's linkWithCredential under the hood, so the existing
            // anonymous UID — and therefore all chats — are preserved.
            if (!phoneNumber.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(
                            stringResource(R.string.profile_phone_linked),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            phoneNumber.orEmpty(),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.startPhoneLink() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.profile_link_phone))
                }
            }

            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.profile_logout))
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data -> Snackbar(snackbarData = data) }
    }

    if (editingNick) {
        AlertDialog(
            onDismissRequest = { editingNick = false },
            title = { Text(stringResource(R.string.profile_change_nickname)) },
            text = {
                OutlinedTextField(
                    value = draftNick,
                    onValueChange = { if (it.length <= 32) draftNick = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.nickname_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateNickname(draftNick)
                    editingNick = false
                }, enabled = draftNick.trim().length >= 2) {
                    Text(stringResource(R.string.continue_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNick = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (editingColor) {
        AlertDialog(
            onDismissRequest = { editingColor = false },
            title = { Text(stringResource(R.string.profile_change_avatar)) },
            text = {
                Column {
                    Avatar(
                        nickname = nickname.orEmpty(),
                        color = activeColor,
                        size = 96.dp,
                        photoUrl = photoUrl,
                    )
                    Spacer(Modifier.size(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(viewModel.palette) { paletteColor ->
                            ColorSwatch(
                                color = paletteColor,
                                selected = paletteColor == activeColor,
                                onClick = {
                                    viewModel.updateAvatarColor(paletteColor)
                                    editingColor = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingColor = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (phoneStep != PhoneLinkStep.Idle) {
        PhoneLinkDialog(
            step = phoneStep,
            error = error,
            busy = busy,
            onSendCode = { activity, phone -> viewModel.submitPhoneNumber(activity, phone) },
            onSubmitCode = { code -> viewModel.submitSmsCode(code) },
            onDismiss = { viewModel.cancelPhoneLink() },
        )
    }
}

@Composable
private fun PhoneLinkDialog(
    step: PhoneLinkStep,
    error: String?,
    busy: Boolean,
    onSendCode: (Activity, String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    var phone by remember { mutableStateOf("+7") }
    var code by remember { mutableStateOf("") }
    val codeSent = step is PhoneLinkStep.CodeSent

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_link_phone)) },
        text = {
            Column {
                if (!codeSent) {
                    Text(
                        stringResource(R.string.phone_enter_number),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(12.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { value ->
                            phone = value.filter { it == '+' || it.isDigit() }.take(16)
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.phone_hint)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        stringResource(R.string.phone_enter_sms),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            if (it.length <= 8 && it.all(Char::isDigit)) code = it
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.phone_sms_hint)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                if (busy || step is PhoneLinkStep.SendingSms) {
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!codeSent) {
                        if (activity != null) onSendCode(activity, phone)
                    } else {
                        onSubmitCode(code)
                    }
                },
                enabled = !busy && activity != null &&
                    (if (codeSent) code.length >= 4 else phone.length >= 8),
            ) {
                Text(
                    if (codeSent) stringResource(R.string.phone_confirm)
                    else stringResource(R.string.phone_send_sms),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ColorSwatch(color: Long, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(color))
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}
