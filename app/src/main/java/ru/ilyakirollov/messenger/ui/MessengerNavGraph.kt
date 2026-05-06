package ru.ilyakirollov.messenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.ilyakirollov.messenger.ui.auth.AuthState
import ru.ilyakirollov.messenger.ui.auth.LoginScreen
import ru.ilyakirollov.messenger.ui.call.CallScreen
import ru.ilyakirollov.messenger.ui.chat.ChatScreen
import ru.ilyakirollov.messenger.ui.home.HomeScreen
import ru.ilyakirollov.messenger.ui.newchat.NewChatScreen
import ru.ilyakirollov.messenger.ui.status.StatusComposerScreen
import ru.ilyakirollov.messenger.ui.status.StatusViewerScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val NEW_CHAT = "newChat"
    const val CHAT = "chat/{chatId}"
    const val CALL = "call/{callId}/{video}/{caller}"
    const val STATUS_COMPOSE = "status/compose"
    const val STATUS_VIEW = "status/view/{statusId}"

    fun chat(chatId: String) = "chat/$chatId"
    fun call(callId: String, video: Boolean, caller: Boolean) = "call/$callId/$video/$caller"
    fun statusView(statusId: String) = "status/view/$statusId"
}

@Composable
fun MessengerNavGraph(
    authState: AuthState,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val nav = rememberNavController()

    LaunchedEffect(authState) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (authState is AuthState.Authenticated && current == Routes.LOGIN) {
            nav.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        } else if (authState is AuthState.Unauthenticated && current != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val start = if (authState is AuthState.Authenticated) Routes.HOME else Routes.LOGIN

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(state = authState, onSignIn = onSignIn)
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                onNewChat = { nav.navigate(Routes.NEW_CHAT) },
                onAddStatus = { nav.navigate(Routes.STATUS_COMPOSE) },
                onOpenStatus = { id -> nav.navigate(Routes.statusView(id)) },
                onStartCall = { callId, video -> nav.navigate(Routes.call(callId, video, true)) },
                onIncomingCall = { callId, video -> nav.navigate(Routes.call(callId, video, false)) },
                onSignOut = onSignOut,
            )
        }
        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = { nav.popBackStack() },
                onChatReady = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.chat(id))
                },
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { entry ->
            val chatId = entry.arguments?.getString("chatId").orEmpty()
            ChatScreen(
                chatId = chatId,
                onBack = { nav.popBackStack() },
                onStartCall = { callId, video -> nav.navigate(Routes.call(callId, video, true)) },
            )
        }
        composable(
            route = Routes.CALL,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("video") { type = NavType.BoolType },
                navArgument("caller") { type = NavType.BoolType },
            ),
        ) { entry ->
            val callId = entry.arguments?.getString("callId").orEmpty()
            val video = entry.arguments?.getBoolean("video") ?: false
            val caller = entry.arguments?.getBoolean("caller") ?: false
            CallScreen(
                callId = callId,
                isVideo = video,
                isCaller = caller,
                onFinished = { nav.popBackStack() },
            )
        }
        composable(Routes.STATUS_COMPOSE) {
            StatusComposerScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.STATUS_VIEW,
            arguments = listOf(navArgument("statusId") { type = NavType.StringType }),
        ) { entry ->
            val statusId = entry.arguments?.getString("statusId").orEmpty()
            StatusViewerScreen(statusId = statusId, onBack = { nav.popBackStack() })
        }
    }
}
