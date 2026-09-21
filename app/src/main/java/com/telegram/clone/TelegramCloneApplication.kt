package com.telegram.clone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.telegram.clone.core.network.TDLibClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom Application class for the Telegram Clone app.
 *
 * Responsibilities:
 * - Initializes the TDLib client manager singleton
 * - Creates notification channels for Android O+
 * - Sets up global application-level state
 */
class TelegramCloneApplication : Application() {

    companion object {
        private const val TAG = "TelegramCloneApp"
        const val NOTIFICATION_CHANNEL_MESSAGES = "messages_channel"
        const val NOTIFICATION_CHANNEL_CALLS = "calls_channel"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Application onCreate - initializing TDLib client")

        // Initialize TDLib client manager
        applicationScope.launch {
            try {
                TDLibClientManager.getInstance().initialize(this@TelegramCloneApplication)
                Log.i(TAG, "TDLib client manager initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TDLib client manager", e)
            }
        }

        // Create notification channels
        createNotificationChannels()
    }

    /**
     * Creates notification channels required for Android Oreo (API 26) and above.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Messages channel
            val messagesChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_MESSAGES,
                getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_messages_desc)
                enableLights(true)
                enableVibration(true)
            }

            // Calls channel
            val callsChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_CALLS,
                getString(R.string.notification_channel_calls),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_calls_desc)
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(callsChannel)

            Log.i(TAG, "Notification channels created")
        }
    }
}
