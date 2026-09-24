package com.telegram.clone.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.components.novaGradientBrush
import com.telegram.clone.ui.theme.GlassBorder
import com.telegram.clone.ui.theme.GlassFillStrong
import com.telegram.clone.ui.theme.NovaGradientEnd
import com.telegram.clone.ui.theme.NovaGradientStart
import com.telegram.clone.ui.theme.TelegramCloneTheme
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Edit Profile screen — fixes the previously-dead pencil button.
 * Lets the user change:
 * - profile photo (SetProfilePhoto via photo picker)
 * - first / last name (SetName)
 * - bio (SetBio)
 * - username (SetUsername)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBackClick: () -> Unit
) {
    val repository = remember { TelegramRepository.getInstance() }
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }

    // Seed fields from the current user once loaded.
    LaunchedEffect(currentUser?.id) {
        val user = currentUser ?: return@LaunchedEffect
        if (!initialized) {
            firstName = user.firstName
            lastName = user.lastName
            username = user.usernames?.activeUsernames?.firstOrNull() ?: ""
            initialized = true
            // Bio comes from GetUserFullInfo, fetched asynchronously.
            scope.launch {
                bio = repository.getUserBio(user.id)
            }
        }
    }

    // Photo picker — copies the picked image into cache for TDLib.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    uploadingPhoto = true
                    val tempFile = java.io.File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    repository.setProfilePhoto(tempFile.absolutePath) { ok, error ->
                        scope.launch {
                            uploadingPhoto = false
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.edit_profile_photo_updated)
                                else context.getString(R.string.edit_profile_update_failed, error ?: "")
                            )
                        }
                    }
                } catch (e: Exception) {
                    uploadingPhoto = false
                    snackbarHostState.showSnackbar("Failed to read image: ${e.message}")
                }
            }
        }
    }

    TelegramCloneTheme {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Gradient header strip with back + save actions
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(brush = novaGradientBrush())
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.edit_profile_title),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 20.dp)
                                .size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Avatar with gradient border + add-photo button
                Spacer(modifier = Modifier.height(28.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .border(
                                width = 3.dp,
                                brush = Brush.linearGradient(listOf(NovaGradientStart, NovaGradientEnd)),
                                shape = CircleShape
                            )
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        ProfileAvatar(currentUser = currentUser)
                    }
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(brush = novaGradientBrush())
                            .border(1.dp, GlassBorder, CircleShape)
                    ) {
                        if (uploadingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = stringResource(R.string.edit_profile_change_photo),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Name section
                SectionLabel(stringResource(R.string.edit_profile_name_section))
                GlassField {
                    LabeledInput(
                        label = stringResource(R.string.profile_first_name),
                        value = firstName,
                        onValueChange = { firstName = it },
                        singleLine = true
                    )
                    FieldDivider()
                    LabeledInput(
                        label = stringResource(R.string.profile_last_name),
                        value = lastName,
                        onValueChange = { lastName = it },
                        singleLine = true
                    )
                }
                SaveButton(
                    enabled = !saving && firstName.isNotBlank(),
                    label = stringResource(R.string.edit_profile_save_name)
                ) {
                    saving = true
                    repository.setProfileName(firstName.trim(), lastName.trim()) { ok, error ->
                        scope.launch {
                            saving = false
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.edit_profile_name_updated)
                                else context.getString(R.string.edit_profile_update_failed, error ?: "")
                            )
                        }
                    }
                }

                // Username section
                SectionLabel(stringResource(R.string.profile_username))
                GlassField {
                    LabeledInput(
                        label = stringResource(R.string.profile_username),
                        value = username,
                        onValueChange = { username = it.removePrefix("@") },
                        singleLine = true
                    )
                }
                SaveButton(
                    enabled = !saving,
                    label = stringResource(R.string.edit_profile_save_username)
                ) {
                    saving = true
                    repository.setProfileUsername(username.trim()) { ok, error ->
                        scope.launch {
                            saving = false
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.edit_profile_username_updated)
                                else context.getString(R.string.edit_profile_update_failed, error ?: "")
                            )
                        }
                    }
                }

                // Bio section
                SectionLabel(stringResource(R.string.profile_bio))
                GlassField {
                    LabeledInput(
                        label = stringResource(R.string.profile_bio),
                        value = bio,
                        onValueChange = { if (it.length <= 70) bio = it },
                        singleLine = false,
                        maxLines = 4
                    )
                }
                SaveButton(
                    enabled = !saving,
                    label = stringResource(R.string.edit_profile_save_bio)
                ) {
                    saving = true
                    repository.setProfileBio(bio.trim()) { ok, error ->
                        scope.launch {
                            saving = false
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.edit_profile_bio_updated)
                                else context.getString(R.string.edit_profile_update_failed, error ?: "")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProfileAvatar(currentUser: TdApi.User?) {
    // Prefer the real photo when it is available locally.
    val photo = currentUser?.profilePhoto?.small
    if (photo != null) {
        com.telegram.clone.ui.components.TdFileImage(
            file = photo,
            contentDescription = "Profile photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Text(
            text = currentUser?.firstName?.firstOrNull()?.uppercase() ?: "U",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun GlassField(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassFillStrong)
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)),
        color = Color.Transparent
    ) {
        Column { content() }
    }
}

@Composable
private fun FieldDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    maxLines: Int = 1
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SaveButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(
                brush = if (enabled) novaGradientBrush()
                else Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
            )
            .clickable(enabled = enabled, onClick = onClick),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
