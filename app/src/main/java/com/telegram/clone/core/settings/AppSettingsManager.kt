package com.telegram.clone.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for persistent application settings backed by SharedPreferences.
 *
 * Exposes reactive [StateFlow]s so Compose can collect preferences and recompose
 * when the user changes a setting (e.g. toggling dark mode).
 */
class AppSettingsManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "telegram_clone_settings"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"

        private const val KEY_NOTIF_MESSAGES = "notif_messages"
        private const val KEY_NOTIF_GROUPS = "notif_groups"
        private const val KEY_NOTIF_CALLS = "notif_calls"
        private const val KEY_NOTIF_PREVIEW = "notif_preview"
        private const val KEY_NOTIF_SOUND = "notif_sound"
        private const val KEY_NOTIF_VIBRATE = "notif_vibrate"

        private const val KEY_PRIVACY_LAST_SEEN = "privacy_last_seen"
        private const val KEY_PRIVACY_PHONE = "privacy_phone"
        private const val KEY_PRIVACY_PROFILE_PHOTO = "privacy_profile_photo"
        private const val KEY_PRIVACY_TWO_STEP = "privacy_two_step"
        private const val KEY_PRIVACY_PASSCODE = "privacy_passcode"
        private const val KEY_PRIVACY_AUTO_LOCK = "privacy_auto_lock"

        private const val KEY_DATA_AUTO_DOWNLOAD_PHOTOS = "data_auto_download_photos"
        private const val KEY_DATA_AUTO_DOWNLOAD_VOICE = "data_auto_download_voice"
        private const val KEY_DATA_SAVE_TO_GALLERY = "data_save_to_gallery"

        private const val KEY_LANGUAGE = "language"

        @Volatile
        private var INSTANCE: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    enum class ThemeMode { LIGHT, DARK, SYSTEM }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ============================================================
    // Theme
    // ============================================================

    private val _themeMode = MutableStateFlow(
        enumFromName(prefs.getString(KEY_THEME_MODE, null), ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    // ============================================================
    // Notifications
    // ============================================================

    private val _notifMessages = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_MESSAGES, true))
    val notifMessages: StateFlow<Boolean> = _notifMessages.asStateFlow()

    private val _notifGroups = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_GROUPS, true))
    val notifGroups: StateFlow<Boolean> = _notifGroups.asStateFlow()

    private val _notifCalls = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_CALLS, true))
    val notifCalls: StateFlow<Boolean> = _notifCalls.asStateFlow()

    private val _notifPreview = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_PREVIEW, true))
    val notifPreview: StateFlow<Boolean> = _notifPreview.asStateFlow()

    private val _notifSound = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_SOUND, true))
    val notifSound: StateFlow<Boolean> = _notifSound.asStateFlow()

    private val _notifVibrate = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_VIBRATE, true))
    val notifVibrate: StateFlow<Boolean> = _notifVibrate.asStateFlow()

    fun setNotifMessages(v: Boolean) { prefs.edit().putBoolean(KEY_NOTIF_MESSAGES, v).apply(); _notifMessages.value = v }
    fun setNotifGroups(v: Boolean) { prefs.edit().putBoolean(KEY_NOTIF_GROUPS, v).apply(); _notifGroups.value = v }
    fun setNotifCalls(v: Boolean) { prefs.edit().putBoolean(KEY_NOTIF_CALLS, v).apply(); _notifCalls.value = v }
    fun setNotifPreview(v: Boolean) { prefs.edit().putBoolean(KEY_NOTIF_PREVIEW, v).apply(); _notifPreview.value = v }
    fun setNotifSound(v: Boolean) { prefs.edit().putBoolean(KEY_NOTIF_SOUND, v).apply(); _notifSound.value = v }
    fun setNotifVibrate(v: Boolean) { prefs.edit().putBoolean(KEY_NOTIF_VIBRATE, v).apply(); _notifVibrate.value = v }

    // ============================================================
    // Privacy & Security
    // ============================================================

    private val _privacyLastSeen = MutableStateFlow(prefs.getString(KEY_PRIVACY_LAST_SEEN, "Everybody") ?: "Everybody")
    val privacyLastSeen: StateFlow<String> = _privacyLastSeen.asStateFlow()

    private val _privacyPhone = MutableStateFlow(prefs.getString(KEY_PRIVACY_PHONE, "My Contacts") ?: "My Contacts")
    val privacyPhone: StateFlow<String> = _privacyPhone.asStateFlow()

    private val _privacyProfilePhoto = MutableStateFlow(prefs.getString(KEY_PRIVACY_PROFILE_PHOTO, "Everybody") ?: "Everybody")
    val privacyProfilePhoto: StateFlow<String> = _privacyProfilePhoto.asStateFlow()

    private val _privacyTwoStep = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_TWO_STEP, false))
    val privacyTwoStep: StateFlow<Boolean> = _privacyTwoStep.asStateFlow()

    private val _privacyPasscode = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_PASSCODE, false))
    val privacyPasscode: StateFlow<Boolean> = _privacyPasscode.asStateFlow()

    private val _privacyAutoLock = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_AUTO_LOCK, false))
    val privacyAutoLock: StateFlow<Boolean> = _privacyAutoLock.asStateFlow()

    fun setPrivacyLastSeen(v: String) { prefs.edit().putString(KEY_PRIVACY_LAST_SEEN, v).apply(); _privacyLastSeen.value = v }
    fun setPrivacyPhone(v: String) { prefs.edit().putString(KEY_PRIVACY_PHONE, v).apply(); _privacyPhone.value = v }
    fun setPrivacyProfilePhoto(v: String) { prefs.edit().putString(KEY_PRIVACY_PROFILE_PHOTO, v).apply(); _privacyProfilePhoto.value = v }
    fun setPrivacyTwoStep(v: Boolean) { prefs.edit().putBoolean(KEY_PRIVACY_TWO_STEP, v).apply(); _privacyTwoStep.value = v }
    fun setPrivacyPasscode(v: Boolean) { prefs.edit().putBoolean(KEY_PRIVACY_PASSCODE, v).apply(); _privacyPasscode.value = v }
    fun setPrivacyAutoLock(v: Boolean) { prefs.edit().putBoolean(KEY_PRIVACY_AUTO_LOCK, v).apply(); _privacyAutoLock.value = v }

    // ============================================================
    // Data & Storage
    // ============================================================

    private val _dataAutoDownloadPhotos = MutableStateFlow(prefs.getBoolean(KEY_DATA_AUTO_DOWNLOAD_PHOTOS, true))
    val dataAutoDownloadPhotos: StateFlow<Boolean> = _dataAutoDownloadPhotos.asStateFlow()

    private val _dataAutoDownloadVoice = MutableStateFlow(prefs.getBoolean(KEY_DATA_AUTO_DOWNLOAD_VOICE, true))
    val dataAutoDownloadVoice: StateFlow<Boolean> = _dataAutoDownloadVoice.asStateFlow()

    private val _dataSaveToGallery = MutableStateFlow(prefs.getBoolean(KEY_DATA_SAVE_TO_GALLERY, false))
    val dataSaveToGallery: StateFlow<Boolean> = _dataSaveToGallery.asStateFlow()

    fun setDataAutoDownloadPhotos(v: Boolean) { prefs.edit().putBoolean(KEY_DATA_AUTO_DOWNLOAD_PHOTOS, v).apply(); _dataAutoDownloadPhotos.value = v }
    fun setDataAutoDownloadVoice(v: Boolean) { prefs.edit().putBoolean(KEY_DATA_AUTO_DOWNLOAD_VOICE, v).apply(); _dataAutoDownloadVoice.value = v }
    fun setDataSaveToGallery(v: Boolean) { prefs.edit().putBoolean(KEY_DATA_SAVE_TO_GALLERY, v).apply(); _dataSaveToGallery.value = v }

    // ============================================================
    // Language
    // ============================================================

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, "English") ?: "English")
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(lang: String) { prefs.edit().putString(KEY_LANGUAGE, lang).apply(); _language.value = lang }

    // ============================================================
    // Helpers
    // ============================================================

    private fun <T : Enum<T>> enumFromName(name: String?, default: T, clazz: Class<T>): T {
        if (name == null) return default
        return runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrDefault(default)
    }

    private fun <T : Enum<T>> enumFromName(name: String?, default: T): T {
        return enumFromName(name, default, default.declaringJavaClass)
    }
}
