package com.telegram.clone.core.config

import com.telegram.clone.BuildConfig

/**
 * Central configuration object for Telegram API credentials and client parameters.
 *
 * Reads values dynamically from BuildConfig, which are injected during the Gradle build phase
 * via pipeline secrets (GitHub Actions) or local gradle.properties for developer builds.
 *
 * Implements a graceful fallback mechanism: if placeholder/zero values are detected,
 * the [isConfigured] flag returns false, allowing the UI to display an informative banner
 * instead of crashing during TDLib initialization.
 */
object TelegramConfig {

    /**
     * Telegram API ID obtained from https://my.telegram.org
     * Injected via BuildConfig from pipeline secret ORG_GRADLE_PROJECT_TELEGRAM_API_ID
     */
    val API_ID: Int = BuildConfig.TELEGRAM_API_ID

    /**
     * Telegram API Hash obtained from https://my.telegram.org
     * Injected via BuildConfig from pipeline secret ORG_GRADLE_PROJECT_TELEGRAM_API_HASH
     */
    val API_HASH: String = BuildConfig.TELEGRAM_API_HASH

    /** Application version string reported to Telegram servers */
    const val APPLICATION_VERSION: String = "1.0.0"

    /** System language code for localization */
    const val SYSTEM_LANGUAGE_CODE: String = "en"

    /** Database directory name within app's files directory */
    const val DATABASE_DIRECTORY_NAME: String = "tdlib"

    /** Files directory name for media and downloads */
    const val FILES_DIRECTORY_NAME: String = "files"

    /** Whether to enable the local message database */
    const val USE_MESSAGE_DATABASE: Boolean = true

    /** Whether to enable secret chats support */
    const val USE_SECRET_CHATS: Boolean = true

    /** Whether to enable file storage database */
    const val USE_FILE_DATABASE: Boolean = true

    /** Whether to enable chat info database */
    const val USE_CHAT_INFO_DATABASE: Boolean = true

    /** Maximum file size for automatic download in bytes (10MB) */
    const val MAX_AUTO_DOWNLOAD_FILE_SIZE: Long = 10 * 1024 * 1024

    /** Maximum file size for automatic upload in bytes (10MB) */
    const val MAX_AUTO_UPLOAD_FILE_SIZE: Long = 10 * 1024 * 1024

    /**
     * Validates whether proper API credentials have been configured.
     *
     * @return true if both API_ID and API_HASH contain non-placeholder values,
     *         false if defaults/placeholders are detected.
     */
    val isConfigured: Boolean
        get() = API_ID > 0 && API_HASH.isNotBlank() && API_HASH != "null"

    /**
     * Returns a human-readable configuration status message for UI display.
     * Used in the configuration error banner shown to developers.
     */
    val configurationStatusMessage: String
        get() = buildString {
            appendLine("Telegram API Configuration Status:")
            appendLine("API_ID: ${if (API_ID > 0) "Configured ($API_ID)" else "Missing or invalid"}")
            appendLine("API_HASH: ${if (API_HASH.isNotBlank() && API_HASH != "null") "Configured (${API_HASH.take(4)}...${API_HASH.takeLast(4)})" else "Missing or invalid"}")
            appendLine()
            appendLine("To configure:")
            appendLine("1. Obtain credentials from https://my.telegram.org")
            appendLine("2. Add to ~/.gradle/gradle.properties:")
            appendLine("   TELEGRAM_API_ID=your_api_id")
            appendLine("   TELEGRAM_API_HASH=your_api_hash")
            appendLine("3. Or set environment variables:")
            appendLine("   ORG_GRADLE_PROJECT_TELEGRAM_API_ID")
            appendLine("   ORG_GRADLE_PROJECT_TELEGRAM_API_HASH")
        }

    /**
     * Gets the device model string for reporting to Telegram servers.
     * Falls back to a generic identifier if Build properties are unavailable.
     */
    fun getDeviceModel(): String {
        return try {
            val model = android.os.Build.MODEL
            val manufacturer = android.os.Build.MANUFACTURER
            if (manufacturer.isNotBlank()) "$manufacturer $model" else model
        } catch (e: Exception) {
            "Android Device"
        }
    }

    /**
     * Gets the Android OS version string for reporting to Telegram servers.
     */
    fun getSystemVersion(): String {
        return try {
            "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        } catch (e: Exception) {
            "Android"
        }
    }
}
