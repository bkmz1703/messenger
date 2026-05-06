package ru.ilyakirollov.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.ilyakirollov.messenger.ui.MessengerNavGraph
import ru.ilyakirollov.messenger.ui.auth.AuthViewModel
import ru.ilyakirollov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessengerTheme {
                Root()
            }
        }
    }
}

@Composable
private fun Root(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MessengerNavGraph(authState = state, onSignIn = viewModel::signIn, onSignOut = viewModel::signOut)
}
