package com.telegram.clone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.telegram.clone.core.config.TelegramConfig
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.auth.LoginScreen
import com.telegram.clone.ui.chat.ChatRoomScreen
import com.telegram.clone.ui.home.ChatListScreen
import com.telegram.clone.ui.newchat.NewChatScreen
import com.telegram.clone.ui.profile.ProfileScreen
import com.telegram.clone.ui.settings.SettingsScreen
import com.telegram.clone.ui.theme.TelegramCloneTheme
import kotlinx.coroutines.flow.collectLatest
import org.drinkless.tdlib.TdApi

/**
 * Main host activity that initializes the TDLibClientManager and hosts
 * the Jetpack Compose navigation graph.
 *
 * Navigation flow:
 * - If not authenticated: Show LoginScreen
 * - If authenticated: Show ChatListScreen
 * - When a chat is selected: Navigate to ChatRoomScreen
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TelegramCloneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TelegramCloneApp()
                }
            }
        }
    }
}

/**
 * Root composable that manages navigation between screens based on
 * the authentication state from TDLib.
 */
@Composable
fun TelegramCloneApp() {
    val navController = rememberNavController()
    val repository = remember { TelegramRepository.getInstance() }
    val authState by repository.authorizationState.collectAsState(initial = null)

    // Determine start destination based on auth state
    val startDestination = when (authState?.constructor) {
        TdApi.AuthorizationStateReady.CONSTRUCTOR -> Screen.ChatList.route
        else -> Screen.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                onChatClick = { chatId ->
                    navController.navigate(Screen.ChatRoom.createRoute(chatId))
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onNewChatClick = {
                    navController.navigate(Screen.NewChat.route)
                }
            )
        }

        composable(
            route = Screen.ChatRoom.route,
            arguments = Screen.ChatRoom.navArguments
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L
            ChatRoomScreen(
                chatId = chatId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.NewChat.route) {
            NewChatScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }

    // Listen for auth state changes and navigate accordingly
    LaunchedEffect(authState) {
        when (authState?.constructor) {
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                if (navController.currentDestination?.route == Screen.Login.route) {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR,
            TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                if (navController.currentDestination?.route != Screen.Login.route) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }
}

/**
 * Sealed class defining navigation screens and their routes.
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")

    object ChatList : Screen("chat_list")

    object ChatRoom : Screen("chat_room/{chatId}") {
        fun createRoute(chatId: Long): String = "chat_room/$chatId"

        val navArguments: List<androidx.navigation.NamedNavArgument>
            get() = listOf(
                androidx.navigation.navArgument("chatId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = 0L
                }
            )
    }

    object Profile : Screen("profile")

    object Settings : Screen("settings")

    object NewChat : Screen("new_chat")
}
