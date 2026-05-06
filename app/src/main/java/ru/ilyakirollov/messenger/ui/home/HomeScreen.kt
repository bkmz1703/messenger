package ru.ilyakirollov.messenger.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ilyakirollov.messenger.R
import ru.ilyakirollov.messenger.ui.calls.CallsHistoryScreen
import ru.ilyakirollov.messenger.ui.chats.ChatsListScreen
import ru.ilyakirollov.messenger.ui.profile.ProfileScreen
import ru.ilyakirollov.messenger.ui.status.StatusListScreen

private enum class HomeTab(val labelRes: Int) {
    Chats(R.string.tab_chats),
    Status(R.string.tab_status),
    Calls(R.string.tab_calls),
    Profile(R.string.tab_profile),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onAddStatus: () -> Unit,
    onOpenStatus: (String) -> Unit,
    onStartCall: (callId: String, video: Boolean) -> Unit,
    onIncomingCall: (callId: String, video: Boolean) -> Unit,
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
    val incoming by viewModel.incomingCall.collectAsStateWithLifecycle()

    LaunchedEffect(incoming?.id) {
        val call = incoming
        if (call != null) {
            onIncomingCall(call.id, call.video)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                when (entry) {
                                    HomeTab.Chats -> Icons.AutoMirrored.Filled.Chat
                                    HomeTab.Status -> Icons.Default.RadioButtonChecked
                                    HomeTab.Calls -> Icons.Default.Call
                                    HomeTab.Profile -> Icons.Default.Person
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(entry.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                HomeTab.Chats -> ChatsListScreen(
                    onOpenChat = onOpenChat,
                    onNewChat = onNewChat,
                )
                HomeTab.Status -> StatusListScreen(
                    onAddStatus = onAddStatus,
                    onOpenStatus = onOpenStatus,
                )
                HomeTab.Calls -> CallsHistoryScreen(
                    onCall = onStartCall,
                )
                HomeTab.Profile -> ProfileScreen(onSignOut = onSignOut)
            }
        }
    }
}
