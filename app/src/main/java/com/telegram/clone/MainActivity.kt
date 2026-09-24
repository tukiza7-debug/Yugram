package com.telegram.clone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.telegram.clone.core.config.TelegramConfig
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.auth.LoginScreen
import com.telegram.clone.ui.calls.CallScreen
import com.telegram.clone.ui.calls.CallsScreen
import com.telegram.clone.ui.chat.ChatRoomScreen
import com.telegram.clone.ui.contacts.ContactsScreen
import com.telegram.clone.ui.groups.NewGroupScreen
import com.telegram.clone.ui.home.ChatListScreen
import com.telegram.clone.ui.newchat.NewChatScreen
import com.telegram.clone.ui.profile.ProfileScreen
import com.telegram.clone.ui.settings.AboutScreen
import com.telegram.clone.ui.settings.AppearanceSettingsScreen
import com.telegram.clone.ui.settings.DataStorageSettingsScreen
import com.telegram.clone.ui.settings.DevicesScreen
import com.telegram.clone.ui.settings.LanguageSettingsScreen
import com.telegram.clone.ui.settings.NotificationsSettingsScreen
import com.telegram.clone.ui.settings.PrivacySettingsScreen
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
 * - If authenticated: Show ChatListScreen with a bottom navigation bar
 * - When a chat is selected: Navigate to ChatRoomScreen
 *
 * The bottom navigation bar (Chats, Contacts, Calls, Settings) is shown
 * only on the four top-level destinations and hidden on detail screens
 * (chat room, call, settings sub-pages, etc.).
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
 *
 * A [Scaffold] hosts the [NavHost] in its content and a [MainBottomBar]
 * as its `bottomBar`. The `bottomBar` is only rendered on the four
 * top-level routes so that detail screens (ChatRoom, Call, etc.) are
 * full-screen. The Scaffold's `contentWindowInsets` are zeroed out so
 * that each inner screen's own Scaffold is responsible for system-bar
 * insets (edge-to-edge) — the outer Scaffold only reserves space for
 * the bottom bar via [innerPadding].
 */
@Composable
fun TelegramCloneApp() {
    val navController = rememberNavController()
    val repository = remember { TelegramRepository.getInstance() }
    val authState by repository.authorizationState.collectAsStateWithLifecycle(initialValue = null)

    // Determine start destination based on auth state
    val startDestination = when (authState?.constructor) {
        TdApi.AuthorizationStateReady.CONSTRUCTOR -> Screen.ChatList.route
        else -> Screen.Login.route
    }

    // Track the current destination to drive bottom-bar visibility, tab
    // selection and the unread badge.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Total unread count across all chats for the Chats tab badge.
    val chats by repository.chatList.collectAsStateWithLifecycle()
    val totalUnread = chats.sumOf { it.unreadCount }.coerceAtMost(99)

    // The bottom bar appears only on the four top-level destinations.
    // For Contacts we also hide it when opened in "pick" mode (secret
    // chat selection flow), since that is a modal flow, not a tab.
    val showBottomBar = when (currentRoute) {
        Screen.ChatList.route, Screen.Calls.route, Screen.Settings.route -> true
        Screen.Contacts.route ->
            navBackStackEntry?.arguments?.getString("pickMode") == null
        else -> false
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MainBottomBar(
                    currentRoute = currentRoute,
                    totalUnread = totalUnread,
                    onTabSelected = { tab ->
                        val route = when (tab) {
                            BottomTab.Chats -> Screen.ChatList.route
                            BottomTab.Contacts -> Screen.Contacts.createRoute()
                            BottomTab.Calls -> Screen.Calls.route
                            BottomTab.Settings -> Screen.Settings.route
                        }
                        navController.navigate(route) {
                            // Pop up to the start destination so the back
                            // stack stays flat, saving state of the other
                            // tabs so switching is instant and stateful.
                            popUpTo(Screen.ChatList.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
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
                    },
                    onNewGroupClick = {
                        navController.navigate(Screen.NewGroup.route)
                    },
                    onContactsClick = {
                        navController.navigate(Screen.Contacts.createRoute())
                    },
                    onCallsClick = {
                        navController.navigate(Screen.Calls.route)
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
                    onBackClick = { navController.popBackStack() },
                    onCallClick = { contactName, isVideo ->
                        navController.navigate(Screen.Call.createRoute(chatId, contactName, isVideo))
                    }
                )
            }

            composable(
                route = Screen.Call.route,
                arguments = Screen.Call.navArguments
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L
                val contactName = backStackEntry.arguments?.getString("contactName") ?: "Contact"
                val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
                CallScreen(
                    chatId = chatId,
                    contactName = contactName,
                    isVideoCall = isVideo,
                    onEndCall = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onProfileClick = {
                        navController.navigate(Screen.Profile.route)
                    },
                    onAppearanceClick = { navController.navigate(Screen.Appearance.route) },
                    onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                    onPrivacyClick = { navController.navigate(Screen.Privacy.route) },
                    onDevicesClick = { navController.navigate(Screen.Devices.route) },
                    onDataStorageClick = { navController.navigate(Screen.DataStorage.route) },
                    onLanguageClick = { navController.navigate(Screen.Language.route) },
                    onAboutClick = { navController.navigate(Screen.About.route) }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.NewChat.route) {
                NewChatScreen(
                    onBackClick = { navController.popBackStack() },
                    onNewGroup = { navController.navigate(Screen.NewGroup.route) },
                    onNewSecretChat = { navController.navigate(Screen.Contacts.createRoute("secret")) },
                    onContacts = { navController.navigate(Screen.Contacts.createRoute()) },
                    onSavedMessages = {
                        val selfId = repository.currentUser.value?.id
                        if (selfId != null) {
                            repository.openPrivateChat(selfId) { chatId ->
                                chatId?.let { navController.navigate(Screen.ChatRoom.createRoute(it)) }
                            }
                        }
                    },
                    onChannelCreate = { title ->
                        repository.createNewChannel(title) { chatId ->
                            chatId?.let {
                                navController.navigate(Screen.ChatRoom.createRoute(it)) { popUpTo(Screen.ChatList.route) }
                            }
                        }
                    }
                )
            }

            composable(Screen.NewGroup.route) {
                NewGroupScreen(
                    onBackClick = { navController.popBackStack() },
                    onGroupCreated = { chatId ->
                        navController.navigate(Screen.ChatRoom.createRoute(chatId)) {
                            popUpTo(Screen.ChatList.route)
                        }
                    }
                )
            }

            composable(
                route = Screen.Contacts.route,
                arguments = Screen.Contacts.navArguments
            ) { backStackEntry ->
                val pickMode = backStackEntry.arguments?.getString("pickMode")
                ContactsScreen(
                    onBackClick = { navController.popBackStack() },
                    onContactClick = { chatId ->
                        navController.navigate(Screen.ChatRoom.createRoute(chatId)) {
                            popUpTo(Screen.ChatList.route)
                        }
                    },
                    onContactPick = if (pickMode == "secret") {
                        { userId ->
                            repository.createNewSecretChat(userId) { chatId ->
                                chatId?.let {
                                    navController.navigate(Screen.ChatRoom.createRoute(it)) {
                                        popUpTo(Screen.ChatList.route)
                                    }
                                }
                            }
                        }
                    } else null
                )
            }

            composable(Screen.Calls.route) {
                CallsScreen(
                    onBackClick = { navController.popBackStack() },
                    onCallClick = { chatId ->
                        navController.navigate(Screen.ChatRoom.createRoute(chatId))
                    }
                )
            }

            composable(Screen.Appearance.route) {
                AppearanceSettingsScreen(onBackClick = { navController.popBackStack() })
            }

            composable(Screen.Notifications.route) {
                NotificationsSettingsScreen(onBackClick = { navController.popBackStack() })
            }

            composable(Screen.Privacy.route) {
                PrivacySettingsScreen(onBackClick = { navController.popBackStack() })
            }

            composable(Screen.Devices.route) {
                DevicesScreen(onBackClick = { navController.popBackStack() })
            }

            composable(Screen.DataStorage.route) {
                DataStorageSettingsScreen(onBackClick = { navController.popBackStack() })
            }

            composable(Screen.Language.route) {
                LanguageSettingsScreen(onBackClick = { navController.popBackStack() })
            }

            composable(Screen.About.route) {
                AboutScreen(onBackClick = { navController.popBackStack() })
            }
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
 * The four top-level tabs shown in the bottom navigation bar.
 */
private enum class BottomTab {
    Chats,
    Contacts,
    Calls,
    Settings
}

/**
 * Material 3 bottom navigation bar with four tabs.
 *
 * The Chats tab carries a [Badge] showing the total number of unread
 * messages across all chats (capped at 99+). Tab selection is driven by
 * [currentRoute] so the active indicator follows navigation state.
 *
 * @param currentRoute The current NavHost destination route, used to
 *                     mark the active tab.
 * @param totalUnread  Total unread message count for the Chats badge.
 * @param onTabSelected Callback invoked when a tab is tapped.
 */
@Composable
private fun MainBottomBar(
    currentRoute: String?,
    totalUnread: Int,
    onTabSelected: (BottomTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Screen.ChatList.route,
            onClick = { onTabSelected(BottomTab.Chats) },
            icon = {
                BadgedBox(
                    badge = {
                        if (totalUnread > 0) {
                            Badge { Text(text = if (totalUnread > 99) "99+" else totalUnread.toString()) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Chats"
                    )
                }
            },
            label = { Text("Chats") }
        )

        NavigationBarItem(
            selected = currentRoute == Screen.Contacts.route,
            onClick = { onTabSelected(BottomTab.Contacts) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Contacts,
                    contentDescription = "Contacts"
                )
            },
            label = { Text("Contacts") }
        )

        NavigationBarItem(
            selected = currentRoute == Screen.Calls.route,
            onClick = { onTabSelected(BottomTab.Calls) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Calls"
                )
            },
            label = { Text("Calls") }
        )

        NavigationBarItem(
            selected = currentRoute == Screen.Settings.route,
            onClick = { onTabSelected(BottomTab.Settings) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            },
            label = { Text("Settings") }
        )
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

    object Call : Screen("call/{chatId}/{contactName}/{isVideo}") {
        fun createRoute(chatId: Long, contactName: String, isVideo: Boolean): String {
            // Encode contact name to be URL-safe
            val encodedName = contactName.replace("/", "_")
            return "call/$chatId/$encodedName/$isVideo"
        }

        val navArguments: List<androidx.navigation.NamedNavArgument>
            get() = listOf(
                androidx.navigation.navArgument("chatId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = 0L
                },
                androidx.navigation.navArgument("contactName") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = "Contact"
                },
                androidx.navigation.navArgument("isVideo") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = false
                }
            )
    }

    object Profile : Screen("profile")

    object Settings : Screen("settings")

    object NewChat : Screen("new_chat")

    object NewGroup : Screen("new_group")

    object Contacts : Screen("contacts?pick={pickMode}") {
        fun createRoute(pickMode: String? = null): String =
            if (pickMode != null) "contacts?pick=$pickMode" else "contacts"
        val navArguments: List<androidx.navigation.NamedNavArgument>
            get() = listOf(
                androidx.navigation.navArgument("pickMode") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
    }

    object Calls : Screen("calls")

    object Appearance : Screen("appearance")

    object Notifications : Screen("notifications")

    object Privacy : Screen("privacy")

    object Devices : Screen("devices")

    object DataStorage : Screen("data_storage")

    object Language : Screen("language")

    object About : Screen("about")
}
