package com.telegram.clone.ui.premium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.ui.components.novaGradientBrush
import com.telegram.clone.ui.theme.GlassBorder
import com.telegram.clone.ui.theme.GlassBorderSoft
import com.telegram.clone.ui.theme.GlassFill
import com.telegram.clone.ui.theme.GlassFillStrong
import com.telegram.clone.ui.theme.NovaCyan
import com.telegram.clone.ui.theme.NovaPurple
import com.telegram.clone.ui.theme.TelegramCloneTheme
import kotlinx.coroutines.launch

/**
 * Yugram Premium — free forever.
 *
 * Client-side premium-style features that cost nothing:
 * 1. App Lock (PIN)              — privacy gate for the whole app
 * 2. Stealth Mode                — open chats without sending read receipts
 * 3. Double-tap heart reaction   — quick heart on any message
 * 4. Premium star badge          — cosmetic badge next to your name
 * 5. Fancy text generator        — unicode-styled text not available in
 *                                  official Telegram clients
 * 6. Scheduled sending           — long-press the send button in any chat
 *                                  (server-side scheduled messages)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val stealth by settings.stealthMode.collectAsState()
    val doubleTap by settings.doubleTapReaction.collectAsState()
    val badge by settings.premiumBadge.collectAsState()
    val lockEnabled by settings.appLockEnabled.collectAsState()

    var showPinDialog by rememberSaveable { mutableStateOf(false) }

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
                // Gradient header
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
                        text = stringResource(R.string.premium_title),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Hero banner
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            brush = Brush.linearGradient(
                                listOf(NovaPurple.copy(alpha = 0.35f), NovaCyan.copy(alpha = 0.25f))
                            )
                        )
                        .border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.premium_hero_title),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.premium_hero_subtitle),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                // Feature toggles
                FeatureCard(
                    icon = Icons.Default.Lock,
                    iconTint = NovaPurple,
                    title = stringResource(R.string.premium_app_lock),
                    subtitle = stringResource(R.string.premium_app_lock_desc)
                ) {
                    Switch(
                        checked = lockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPinDialog = true
                            } else {
                                settings.setAppLock(false, null)
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.premium_app_lock_off))
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NovaPurple)
                    )
                }

                FeatureCard(
                    icon = Icons.Default.VisibilityOff,
                    iconTint = NovaCyan,
                    title = stringResource(R.string.premium_stealth),
                    subtitle = stringResource(R.string.premium_stealth_desc)
                ) {
                    Switch(
                        checked = stealth,
                        onCheckedChange = { settings.setStealthMode(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = NovaPurple)
                    )
                }

                FeatureCard(
                    icon = Icons.Default.Favorite,
                    iconTint = Color(0xFFFF5E7A),
                    title = stringResource(R.string.premium_double_tap),
                    subtitle = stringResource(R.string.premium_double_tap_desc)
                ) {
                    Switch(
                        checked = doubleTap,
                        onCheckedChange = { settings.setDoubleTapReaction(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = NovaPurple)
                    )
                }

                FeatureCard(
                    icon = Icons.Default.Star,
                    iconTint = Color(0xFFFFD54F),
                    title = stringResource(R.string.premium_badge),
                    subtitle = stringResource(R.string.premium_badge_desc)
                ) {
                    Switch(
                        checked = badge,
                        onCheckedChange = { settings.setPremiumBadge(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = NovaPurple)
                    )
                }

                FeatureCard(
                    icon = Icons.Default.Schedule,
                    iconTint = Color(0xFF34E0A1),
                    title = stringResource(R.string.premium_schedule),
                    subtitle = stringResource(R.string.premium_schedule_desc)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF34E0A1)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fancy text generator
                FancyTextTool()

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onPinConfirmed = { pin ->
                settings.setAppLock(true, pin)
                showPinDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.premium_app_lock_on))
                }
            }
        )
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            trailing()
        }
    }
}

// ============================================================
// Fancy text generator (unicode styling not in official Telegram)
// ============================================================

object FancyTextGenerator {

    private val boldUpper = "𝗔𝗕𝗖𝗗𝗘𝗙𝗚𝗛𝗜𝗝𝗞𝗟𝗠𝗡𝗢𝗣𝗤𝗥𝗦𝗧𝗨𝗩𝗪𝗫𝗬𝗭"
    private val boldLower = "𝗮𝗯𝗰𝗱𝗲𝗳𝗴𝗵𝗶𝗷𝗸𝗹𝗺𝗻𝗼𝗽𝗾𝗿𝘀𝘁𝘂𝘃𝘄𝘅𝘆𝘇"
    private val boldDigits = "𝟬𝟭𝟮𝟯𝟰𝟱𝟲𝟳𝟴𝟵"
    private val italicUpper = "𝘈𝘉𝘊𝘋𝘌𝘍𝘎𝘏𝘐𝘑𝘒𝘓𝘔𝘕𝘖𝘗𝘘𝘙𝘚𝘛𝘜𝘝𝘞𝘟𝘠𝘡"
    private val italicLower = "𝘢𝘣𝘤𝘥𝘦𝘧𝘨𝘩𝘪𝘫𝘬𝘭𝘮𝘯𝘰𝘱𝘲𝘳𝘴𝘵𝘶𝘷𝘸𝘹𝘺𝘻"
    private val scriptUpper = "𝓐𝓑𝓒𝓓𝓔𝓕𝓖𝓗𝓘𝓙𝓚𝓛𝓜𝓝𝓞𝓟𝓠𝓡𝓢𝓣𝓤𝓥𝓦𝓧𝓨𝓩"
    private val scriptLower = "𝓪𝓫𝓬𝓭𝓮𝓯𝓰𝓱𝓲𝓳𝓴𝓵𝓶𝓷𝓸𝓹𝓺𝓻𝓼𝓽𝓾𝓿𝔀𝔁𝔂𝔃"
    private val monoUpper = "𝙰𝙱𝙲𝙳𝙴𝙵𝙶𝙷𝙸𝙹𝙺𝙻𝙼𝙽𝙾𝙿𝚀𝚁𝚂𝚃𝚄𝚅𝚆𝚇𝚈𝚉"
    private val monoLower = "𝚊𝚋𝚌𝚍𝚎𝚏𝚐𝚑𝚒𝚓𝚔𝚕𝚖𝚗𝚘𝚙𝚚𝚛𝚜𝚝𝚞𝚟𝚠𝚡𝚢𝚣"
    private val monoDigits = "𝟶𝟷𝟸𝟹𝟺𝟻𝟼𝟽𝟾𝟿"
    private val doubleUpper = "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ"
    private val doubleLower = "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫"
    private val doubleDigits = "𝟘𝟙𝟚𝟛𝟜𝟝𝟞𝟟𝟠𝟡"

    private fun transform(text: String, up: String, low: String, digits: String? = null): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch in 'A'..'Z' -> sb.append(up[ch - 'A'])
                ch in 'a'..'z' -> sb.append(low[ch - 'a'])
                digits != null && ch in '0'..'9' -> sb.append(digits[ch - '0'])
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun styles(): List<Pair<String, (String) -> String>> = listOf(
        "𝗕𝗼𝗹𝗱" to { t -> transform(t, boldUpper, boldLower, boldDigits) },
        "𝘐𝘵𝘢𝘭𝘪𝘤" to { t -> transform(t, italicUpper, italicLower) },
        "𝓢𝓬𝓻𝓲𝓹𝓽" to { t -> transform(t, scriptUpper, scriptLower) },
        "𝙼𝚘𝚗𝚘" to { t -> transform(t, monoUpper, monoLower, monoDigits) },
        "𝔻𝕠𝕦𝕓𝕝𝕖" to { t -> transform(t, doubleUpper, doubleLower, doubleDigits) },
        "sᴍᴀʟʟ ᴄᴀᴘs" to { t -> smallCaps(t) },
        "Ｗｉｄｅ" to { t -> wide(t) }
    )

    private fun smallCaps(text: String): String {
        val small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘqʀsᴛᴜᴠᴡxʏᴢ"
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch in 'A'..'Z' -> sb.append(small[ch - 'A'])
                ch in 'a'..'z' -> sb.append(small[ch - 'a'])
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun wide(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                ' ' -> sb.append(' ')
                in '!'..'~' -> sb.append(ch + 0xFEE0)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

@Composable
private fun FancyTextTool() {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val styles = remember { FancyTextGenerator.styles() }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.TextFields,
                contentDescription = null,
                tint = NovaCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.premium_fancy_text),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.premium_fancy_text_desc),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.premium_fancy_hint), fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (input.isNotBlank()) {
            styles.forEach { (name, transform) ->
                val transformed = remember(input, name) { transform(input) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassFillStrong)
                        .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
                        .clickable {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("yugram_fancy", transformed))
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.premium_copied),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transformed,
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.premium_copy),
                            color = NovaCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// PIN setup dialog
// ============================================================

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinConfirmed: (String) -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var stage by rememberSaveable { mutableStateOf(0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (stage == 0) stringResource(R.string.premium_pin_create) else stringResource(R.string.premium_pin_confirm),
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it
                    },
                    enabled = stage == 0,
                    label = { Text(stringResource(R.string.premium_pin_label)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                if (stage == 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) confirm = it
                        },
                        label = { Text(stringResource(R.string.premium_pin_label)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (stage) {
                        0 -> {
                            if (pin.length < 4) {
                                error = "PIN must be 4-6 digits"
                            } else {
                                error = null
                                stage = 1
                            }
                        }
                        else -> {
                            if (confirm == pin) onPinConfirmed(pin)
                            else error = "PINs do not match"
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.action_next))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
