package com.telegram.clone.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegram.clone.R
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import com.telegram.clone.ui.theme.TelegramTextStyles

/**
 * Login screen implementing the multi-step authentication flow:
 * Step 1: Country code + Phone number input
 * Step 2: OTP verification code input
 * Step 3: 2FA password input (if required)
 *
 * Uses Material 3 design and Jetpack Compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfigDialog by remember { mutableStateOf(!uiState.isConfigured) }

    // Navigate to main app when authenticated
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == LoginViewModel.AuthStep.AUTHENTICATED) {
            onAuthenticated()
        }
    }

    // Update config dialog visibility when config state changes
    LaunchedEffect(uiState.isConfigured) {
        showConfigDialog = !uiState.isConfigured
    }

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                if (uiState.currentStep != LoginViewModel.AuthStep.PHONE_INPUT) {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.goBackToPhoneInput() }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = TelegramBlue,
                            titleContentColor = Color.White
                        )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    // Telegram Logo
                    TelegramLogo()

                    Spacer(modifier = Modifier.height(32.dp))

                    // Title
                    Text(
                        text = stringResource(R.string.login_title),
                        style = TelegramTextStyles.loginTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    Text(
                        text = stringResource(R.string.login_subtitle),
                        style = TelegramTextStyles.loginSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    // Step content
                    when (uiState.currentStep) {
                        LoginViewModel.AuthStep.PHONE_INPUT -> {
                            PhoneInputStep(
                                countryCode = uiState.countryCode,
                                phoneNumber = uiState.phoneNumber,
                                onCountryCodeChanged = viewModel::onCountryCodeChanged,
                                onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
                                onSubmit = viewModel::submitPhoneNumber,
                                isLoading = uiState.isLoading,
                                errorMessage = uiState.errorMessage,
                                onClearError = viewModel::clearError
                            )
                        }
                        LoginViewModel.AuthStep.CODE_INPUT -> {
                            CodeInputStep(
                                code = uiState.verificationCode,
                                onCodeChanged = viewModel::onVerificationCodeChanged,
                                onSubmit = viewModel::submitVerificationCode,
                                onResendCode = viewModel::resendVerificationCode,
                                isLoading = uiState.isLoading,
                                errorMessage = uiState.errorMessage,
                                onClearError = viewModel::clearError,
                                phoneNumber = uiState.countryCode + uiState.phoneNumber
                            )
                        }
                        LoginViewModel.AuthStep.PASSWORD_INPUT -> {
                            PasswordInputStep(
                                password = uiState.password,
                                onPasswordChanged = viewModel::onPasswordChanged,
                                onSubmit = viewModel::submitPassword,
                                isLoading = uiState.isLoading,
                                errorMessage = uiState.errorMessage,
                                onClearError = viewModel::clearError
                            )
                        }
                        LoginViewModel.AuthStep.LOADING -> {
                            LoadingStep()
                        }
                        LoginViewModel.AuthStep.AUTHENTICATED -> {
                            LoadingStep(message = "Signing in…")
                        }
                    }
                }

                // Configuration error dialog
                if (showConfigDialog) {
                    ConfigurationErrorDialog(
                        message = uiState.configurationMessage,
                        onDismiss = { showConfigDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun TelegramLogo() {
    Surface(
        modifier = Modifier.size(96.dp),
        shape = CircleShape,
        color = TelegramBlue
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Simple paper plane icon using text
            Text(
                text = "✈",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PhoneInputStep(
    countryCode: String,
    phoneNumber: String,
    onCountryCodeChanged: (String) -> Unit,
    onPhoneNumberChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Country code field
            OutlinedTextField(
                value = countryCode,
                onValueChange = { newValue ->
                    onClearError()
                    // Allow only digits and + sign
                    val filtered = newValue.filter { it.isDigit() || it == '+' }
                    onCountryCodeChanged(filtered.take(5))
                },
                modifier = Modifier.width(100.dp),
                label = { Text(stringResource(R.string.login_country_code)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Phone number field
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { newValue ->
                    onClearError()
                    // Allow only digits, spaces, and dashes
                    val filtered = newValue.filter { it.isDigit() || it == ' ' || it == '-' }
                    onPhoneNumberChanged(filtered)
                },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.login_phone_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onSubmit()
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Error message
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            ErrorBanner(message = error)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Submit button
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TelegramBlue
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.login_next),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeInputStep(
    code: String,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onResendCode: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    phoneNumber: String
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.login_verification_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.login_verification_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = phoneNumber,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Code input field
        OutlinedTextField(
            value = code,
            onValueChange = { newValue ->
                onClearError()
                val filtered = newValue.filter { it.isDigit() }.take(6)
                onCodeChanged(filtered)
                // Auto-submit when full code is entered
                if (filtered.length == 6) {
                    onSubmit()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.login_code_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                }
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center
            )
        )

        // Error message
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            ErrorBanner(message = error)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Resend code button
        TextButton(
            onClick = onResendCode,
            enabled = !isLoading
        ) {
            Text(
                text = stringResource(R.string.login_resend_code),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Submit button
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading && code.length >= 4,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TelegramBlue
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.login_continue),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordInputStep(
    password: String,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Two-Step Verification",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Please enter your cloud password to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Password input field
        OutlinedTextField(
            value = password,
            onValueChange = { newValue ->
                onClearError()
                onPasswordChanged(newValue)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                }
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Error message
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            ErrorBanner(message = error)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Submit button
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading && password.isNotEmpty(),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TelegramBlue
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.login_continue),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingStep(message: String = stringResource(R.string.login_loading)) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = TelegramBlue,
            strokeWidth = 3.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ConfigurationErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = stringResource(R.string.config_error_title))
        },
        text = {
            Text(
                text = stringResource(R.string.config_error_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.config_error_dismiss))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
