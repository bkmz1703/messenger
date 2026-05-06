package ru.ilyakirollov.messenger.ui.profile

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    var editingNick by remember { mutableStateOf(false) }
    var editingColor by remember { mutableStateOf(false) }
    var draftNick by remember(nickname) { mutableStateOf(nickname.orEmpty()) }
    val activeColor = color ?: 0xFF455A64

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.size(16.dp))
        Avatar(nickname = nickname.orEmpty(), color = activeColor, size = 120.dp)
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
        OutlinedButton(onClick = { editingColor = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ColorLens, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.profile_change_avatar))
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
                    Avatar(nickname = nickname.orEmpty(), color = activeColor, size = 96.dp)
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
