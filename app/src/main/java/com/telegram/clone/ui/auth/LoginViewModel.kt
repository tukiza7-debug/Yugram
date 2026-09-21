package com.telegram.clone.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegram.clone.core.config.TelegramConfig
import com.telegram.clone.data.repository.TelegramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * ViewModel for the login/authentication flow.
 * Manages the multi-step authentication process:
 * Step 1: Phone number input
 * Step 2: OTP verification code input
 * Step 3: 2FA password (if required)
 *
 * Maps user actions directly to TDLib authentication functions.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TelegramRepository.getInstance()

    // ============================================================
    // UI State
    // ============================================================

    enum class AuthStep {
        PHONE_INPUT,
        CODE_INPUT,
        PASSWORD_INPUT,
        LOADING,
        AUTHENTICATED
    }

    data class LoginUiState(
        val currentStep: AuthStep = AuthStep.PHONE_INPUT,
        val countryCode: String = "+1",
        val phoneNumber: String = "",
        val verificationCode: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isConfigured: Boolean = TelegramConfig.isConfigured,
        val configurationMessage: String = TelegramConfig.configurationStatusMessage
    )

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Authorization state from TDLib */
    val authorizationState: StateFlow<TdApi.AuthorizationState?> =
        repository.authorizationState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // ============================================================
    // Initialization
    // ============================================================

    init {
        // Observe authorization state changes to update UI step
        viewModelScope.launch {
            repository.authorizationState.collect { state ->
                handleAuthorizationState(state)
            }
        }

        // Observe error events
        viewModelScope.launch {
            repository.errorFlow.collect { error ->
                handleTdLibError(error)
            }
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState?) {
        when (state?.constructor) {
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                _uiState.value = _uiState.value.copy(
                    currentStep = AuthStep.PHONE_INPUT,
                    isLoading = false,
                    errorMessage = null
                )
            }
            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                _uiState.value = _uiState.value.copy(
                    currentStep = AuthStep.CODE_INPUT,
                    isLoading = false,
                    errorMessage = null
                )
            }
            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                _uiState.value = _uiState.value.copy(
                    currentStep = AuthStep.PASSWORD_INPUT,
                    isLoading = false,
                    errorMessage = null
                )
            }
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                _uiState.value = _uiState.value.copy(
                    currentStep = AuthStep.AUTHENTICATED,
                    isLoading = false,
                    errorMessage = null
                )
            }
            else -> {
                // Other states (TdlibParameters, EncryptionKey, etc.) are handled automatically
                // by TDLibClientManager, show loading state
                if (state != null && _uiState.value.currentStep != AuthStep.AUTHENTICATED) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = true,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun handleTdLibError(error: TdApi.Error) {
        val errorMessage = when (error.code) {
            400 -> {
                when {
                    error.message.contains("PHONE_NUMBER_INVALID", ignoreCase = true) ->
                        "Invalid phone number. Please check and try again."
                    error.message.contains("PHONE_CODE_INVALID", ignoreCase = true) ->
                        "Invalid verification code. Please try again."
                    error.message.contains("PHONE_CODE_EXPIRED", ignoreCase = true) ->
                        "Verification code has expired. Please request a new one."
                    error.message.contains("PASSWORD_HASH_INVALID", ignoreCase = true) ->
                        "Incorrect password. Please try again."
                    error.message.contains("API_ID_INVALID", ignoreCase = true) ->
                        "Invalid API credentials. Please check your configuration."
                    error.message.contains("API_ID_PUBLISHED_FLOOD", ignoreCase = true) ->
                        "API ID is publicly known. Please use your own credentials."
                    else -> "Error: ${error.message}"
                }
            }
            420 -> {
                val seconds = extractFloodWaitSeconds(error.message)
                if (seconds > 0) {
                    "Too many attempts. Please wait ${seconds / 60} minutes and try again."
                } else {
                    "Too many attempts. Please wait and try again later."
                }
            }
            else -> "Error ${error.code}: ${error.message}"
        }

        _uiState.value = _uiState.value.copy(
            errorMessage = errorMessage,
            isLoading = false
        )
    }

    private fun extractFloodWaitSeconds(message: String): Int {
        val pattern = Regex("FLOOD_WAIT_(\\d+)")
        val match = pattern.find(message)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    // ============================================================
    // User Actions
    // ============================================================

    /**
     * Updates the country code in the UI state.
     */
    fun onCountryCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(countryCode = code)
    }

    /**
     * Updates the phone number in the UI state.
     */
    fun onPhoneNumberChanged(number: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = number)
    }

    /**
     * Updates the verification code in the UI state.
     */
    fun onVerificationCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(verificationCode = code)
    }

    /**
     * Updates the 2FA password in the UI state.
     */
    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Submits the phone number to TDLib for authentication.
     * Validates input and triggers the TdApi.SetAuthenticationPhoneNumber call.
     */
    fun submitPhoneNumber() {
        val state = _uiState.value
        val fullPhoneNumber = state.countryCode + state.phoneNumber.replace(Regex("[^\\d]"), "")

        if (state.phoneNumber.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter your phone number")
            return
        }

        if (fullPhoneNumber.length < 7) {
            _uiState.value = state.copy(errorMessage = "Phone number is too short")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        repository.setAuthenticationPhoneNumber(fullPhoneNumber)
    }

    /**
     * Submits the verification code to TDLib.
     * Triggers the TdApi.CheckAuthenticationCode call.
     */
    fun submitVerificationCode() {
        val state = _uiState.value

        if (state.verificationCode.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter the verification code")
            return
        }

        if (state.verificationCode.length < 4) {
            _uiState.value = state.copy(errorMessage = "Verification code is too short")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        repository.checkAuthenticationCode(state.verificationCode)
    }

    /**
     * Submits the 2FA password to TDLib.
     * Triggers the TdApi.CheckAuthenticationPassword call.
     */
    fun submitPassword() {
        val state = _uiState.value

        if (state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter your password")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        repository.checkAuthenticationPassword(state.password)
    }

    /**
     * Navigates back to the phone input step.
     */
    fun goBackToPhoneInput() {
        _uiState.value = _uiState.value.copy(
            currentStep = AuthStep.PHONE_INPUT,
            verificationCode = "",
            password = "",
            errorMessage = null
        )
    }

    /**
     * Requests a new verification code to be resent.
     */
    fun resendVerificationCode() {
        // Note: TDLib handles resending automatically when setAuthenticationPhoneNumber
        // is called again with the same number. We simulate this by re-submitting.
        val state = _uiState.value
        val fullPhoneNumber = state.countryCode + state.phoneNumber.replace(Regex("[^\\d]"), "")
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        repository.setAuthenticationPhoneNumber(fullPhoneNumber)
    }
}
