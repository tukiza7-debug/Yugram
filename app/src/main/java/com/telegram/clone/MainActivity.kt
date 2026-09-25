package com.telegram.clone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.auth.LoginScreen
import com.telegram.clone.ui.calls.CallScreen
import com.telegram.clone.ui.calls.CallsScreen
import com.telegram.clone.ui.chat.ChatRoomScreen
import com.telegram.clone.ui.components.NovaBottomBar
import com.telegram.clone.ui.components.NovaTab
import com.telegram.clone.ui.contacts.ContactsScreen
import com.telegram.clone.ui.groups.NewGroupScreen
import com.telegram.clone.ui.home.ChatListScreen
import com.telegram.clone.ui.newchat.NewChatScreen
import com.telegram.clone.ui.premium.PremiumScreen
import com.telegram.clone.ui.premium.AppLockScreen
import com.telegram.clone.ui.profile.EditProfileScreen
import com.telegram.clone.ui.profile.ProfileScreen
import com.telegram.clone.ui.settings.AboutScreen
import com.telegram.clone.ui.settings.AppearanceSettingsScreen
import com.telegram.clone.ui.settings.DataStorageSettingsScreen
import com.telegram.clone.ui.settings.DevicesScreen
import com.telegram.clone.ui.settings.LanguageSettingsScreen
import com.telegram.clone.ui.settings.NekogramSettingsScreen
import com.telegram.clone.ui.settings.NotificationsSettingsScreen
import com.telegram.clone.ui.settings.PrivacySettingsScreen
import com.telegram.clone.ui.settings.SettingsScreen
import com.telegram.clone.ui.theme.TelegramCloneTheme
import org.drinkless.tdlib.TdApi

/**
 * Main host activity that initializes the TDLibClientManager and hosts
 * the Jetpack Compose navigation graph with the Nova floating glass
 * bottom bar.
 *
 * Navigation flow:
 * - If not authenticated: Show LoginScreen
 * - If authenticated: Show ChatListScreen with the floating bottom navigation
 * - When a chat is selected: Navigate to ChatRoomScreen
 *
 * The bottom bar is shown only on the three top-level destinations
 * (Chats, Calls, Settings) and hidden on detail screens.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TelegramCloneTheme {
                NovaAppLockGate {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        TelegramCloneApp()
                    }
                }
            }
        }
    }
}

/**
 * App Lock gate — when a PIN lock is enabled in settings, the whole app is
 * covered by [AppLockScreen] until the correct PIN is entered. The lock
 * re-engages whenever the activity pauses/stops (user leaves the app).
 */
@Composable
private fun NovaAppLockGate(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { AppSettingsManager.getInstance(context) }
    val lockEnabled by settings.appLockEnabled.collectAsStateWithLifecycle()
    var unlocked by rememberSaveable { mutableStateOf(!lockEnabled) }

    // Re-lock when the activity loses foreground (ON_PAUSE / ON_STOP).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, lockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (lockEnabled && event == Lifecycle.Event.ON_PAUSE) {
                unlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (lockEnabled && !unlocked) {
        AppLockScreen(
            verifyPin = { pin -> settings.verifyAppLockPin(pin) },
            onUnlocked = { unlocked = true }
        )
    } else {
        content()
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

    val currentTab = when (currentRoute) {
        Screen.ChatList.route -> NovaTab.CHATS
        Screen.Calls.route -> NovaTab.CALLS
        Screen.Settings.route -> NovaTab.SETTINGS
        else -> null
    }

    // The bottom bar appears only on the top-level destinations.
    // For Contacts we also hide it when opened in "pick" mode (secret
    // chat selection flow), since that is a modal flow, not a tab.
    val showBottomBar = when (currentRoute) {
        Screen.ChatList.route, Screen.Calls.route, Screen.Settings.route -> true
        Screen.Contacts.route ->
            navBackStackEntry?.arguments?.getString("pickMode") == null
        else -> false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { it / 3 } +
                    fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(300)) { -it / 3 } +
                    fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { -it / 3 } +
                    fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(300)) { it / 3 } +
                    fadeOut(animationSpec = tween(300))
            }
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
                    onPremiumClick = { navController.navigate(Screen.Premium.route) },
                    onAboutClick = { navController.navigate(Screen.About.route) },
                    onSavedMessagesClick = {
                        val selfId = repository.currentUser.value?.id
                        if (selfId != null) {
                            repository.openPrivateChat(selfId) { chatId ->
                                chatId?.let {
                                    navController.navigate(Screen.ChatRoom.createRoute(it))
                                }
                            }
                        }
                    },
                    onNekogramClick = { navController.navigate(Screen.Nekogram.route) }
                )
            }

            composable(Screen.Nekogram.route) {
                NekogramSettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onBackClick = { navController.popBackStack() },
                    onEditProfileClick = {
                        navController.navigate(Screen.EditProfile.route)
                    }
                )
            }

            composable(Screen.EditProfile.route) {
                EditProfileScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Premium.route) {
                PremiumScreen(
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

        // Floating glass bottom bar — overlays the content so lists scroll
        // beneath it for the premium "detached" look.
        if (showBottomBar) {
            NovaBottomBar(
                currentTab = currentTab,
                unreadCount = totalUnread,
                onTabSelected = { tab ->
                    val route = when (tab) {
                        NovaTab.CHATS -> Screen.ChatList.route
                        NovaTab.CALLS -> Screen.Calls.route
                        NovaTab.SETTINGS -> Screen.Settings.route
                    }
                    navController.navigate(route) {
                        // Pop up to the start destination so the back
                        // stack stays flat, saving state of the other
                        // tabs so switching is instant and stateful.
                        popUpTo(Screen.ChatList.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .navigationBarsPadding()
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

    object EditProfile : Screen("edit_profile")

    object Premium : Screen("premium")

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

    object Nekogram : Screen("nekogram_settings")
}
