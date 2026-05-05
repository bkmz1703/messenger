package ru.ilyakirollov.messenger.ui.newchat

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ilyakirollov.messenger.R
import ru.ilyakirollov.messenger.data.model.User
import ru.ilyakirollov.messenger.ui.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onChatReady: (String) -> Unit,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val groupMode by viewModel.groupMode.collectAsStateWithLifecycle()
    val groupTitle by viewModel.groupTitle.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_chat)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Создать группу", modifier = Modifier.weight(1f))
                Switch(checked = groupMode, onCheckedChange = viewModel::setGroupMode)
            }

            if (groupMode) {
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = groupTitle,
                    onValueChange = viewModel::setGroupTitle,
                    label = { Text("Название группы") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.size(12.dp))
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
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.size(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(results, key = { it.uid }) { user ->
                    UserRow(
                        user = user,
                        selected = selected.any { it.uid == user.uid },
                        showCheckbox = groupMode,
                        onClick = {
                            if (groupMode) viewModel.toggleSelected(user)
                            else viewModel.startDirect(user, onChatReady)
                        },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }

            if (groupMode) {
                Button(
                    onClick = { viewModel.createGroup(onChatReady) },
                    enabled = groupTitle.isNotBlank() && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Создать группу (${selected.size})")
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    user: User,
    selected: Boolean,
    showCheckbox: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(nickname = user.nickname, color = user.avatarColor, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(user.nickname, fontWeight = FontWeight.Medium)
            Text(
                if (user.online) stringResource(R.string.online) else stringResource(R.string.offline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showCheckbox) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
