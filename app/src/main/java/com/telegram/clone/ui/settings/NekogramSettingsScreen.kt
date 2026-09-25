package com.telegram.clone.ui.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.core.download.MediaDownloadManager
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.ui.theme.BrandBlue
import com.telegram.clone.ui.theme.BrandPurple
import com.telegram.clone.ui.theme.CardBg
import com.telegram.clone.ui.theme.Hairline
import com.telegram.clone.ui.theme.Ink
import com.telegram.clone.ui.theme.MutedGray
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Nekogram-inspired power-feature settings:
 *
 * - Confirm before send / silent send
 * - Message translation (auto-translate + target language)
 * - Bulk download parallel connections (pushed to TDLib live)
 * - Default "forward without sender name"
 */
@Composable
fun NekogramSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettingsManager.getInstance(context) }
    val downloadManager = remember { MediaDownloadManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val confirmSend by settings.confirmSend.collectAsState()
    val silentSend by settings.silentSend.collectAsState()
    val autoTranslate by settings.autoTranslate.collectAsState()
    val translateLang by settings.translateLang.collectAsState()
    val downloadConnections by settings.downloadConnections.collectAsState()
    val forwardNoQuote by settings.forwardNoQuote.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        // ---------- Gradient header ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(listOf(BrandPurple, BrandBlue)),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .statusBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Fitur Nekogram",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // ---------- Messages section ----------
            SectionTitle("Mesej")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "Sahkan sebelum hantar",
                    subtitle = "Papar dialog pengesahan setiap kali menghantar",
                    checked = confirmSend,
                    onChange = settings::setConfirmSend
                )
                CardDivider()
                ToggleRow(
                    icon = Icons.Default.Download,
                    title = "Hantar senyap",
                    subtitle = "Semua mesej keluar tanpa pemberitahuan bunyi",
                    checked = silentSend,
                    onChange = settings::setSilentSend
                )
                CardDivider()
                ToggleRow(
                    icon = Icons.Default.Translate,
                    title = "Terjemah automatik",
                    subtitle = "Mesej teks masuk diterjemah secara automatik",
                    checked = autoTranslate,
                    onChange = settings::setAutoTranslate
                )
                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageDialog = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconChip(icon = Icons.Default.Translate)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bahasa terjemahan",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Text(
                            text = languageName(translateLang),
                            fontSize = 12.sp,
                            color = MutedGray
                        )
                    }
                    Text(
                        text = translateLang.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPurple
                    )
                }
            }

            // ---------- Download section ----------
            SectionTitle("Muat Turun Pukal")
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(icon = Icons.Default.Download)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sambungan serentak",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink
                            )
                            Text(
                                text = "Laju muat turun media pukal ($downloadConnections / 8)",
                                fontSize = 12.sp,
                                color = MutedGray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = downloadConnections.toFloat(),
                        onValueChange = { settings.setDownloadConnections(it.toInt().coerceIn(1, 8)) },
                        onValueChangeFinished = {
                            scope.launch { downloadManager.applyTdLibDownloadConnections() }
                        },
                        valueRange = 1f..8f,
                        steps = 6
                    )
                }
                CardDivider()
                ToggleRow(
                    icon = Icons.Default.Info,
                    title = "Majukan tanpa nama pengirim",
                    subtitle = "Default dialog majukan: buang nama pengirim asal",
                    checked = forwardNoQuote,
                    onChange = settings::setForwardNoQuote
                )
            }

            // ---------- Info card ----------
            SectionTitle("Petua")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    IconChip(icon = Icons.Default.Info)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Cara guna muat turun pukal",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dalam mana-mana chat: tekan lama mesej untuk menu " +
                                "Pilih / Salin / Terjemah / Butiran, atau menu (tiga titik) " +
                                "dan pilih \"Pilih mesej\" atau \"Muat turun semua media\". " +
                                "Fail siap disimpan automatik ke galeri (folder Yugram).",
                            fontSize = 12.sp,
                            color = MutedGray,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ---------- Language picker ----------
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Bahasa terjemahan") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    languageOptions.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    settings.setTranslateLang(code)
                                    showLanguageDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontSize = 15.sp,
                                fontWeight = if (code == translateLang) FontWeight.Bold else FontWeight.Normal,
                                color = if (code == translateLang) BrandPurple else Ink,
                                modifier = Modifier.weight(1f)
                            )
                            if (code == translateLang) {
                                Text(
                                    text = code.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPurple
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Tutup") }
            }
        )
    }
}

// ---------- Local building blocks ----------

private val languageOptions = listOf(
    "ms" to "Bahasa Melayu",
    "id" to "Bahasa Indonesia",
    "en" to "English",
    "zh" to "中文",
    "ar" to "العربية",
    "es" to "Español",
    "fr" to "Français",
    "ja" to "日本語",
    "ko" to "한국어",
    "ru" to "Русский",
    "th" to "ไทย",
    "vi" to "Tiếng Việt"
)

private fun languageName(code: String): String =
    languageOptions.firstOrNull { it.first == code }?.second ?: code

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MutedGray,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
    ) {
        content()
    }
}

@Composable
private fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 66.dp)
            .height(1.dp)
            .background(Hairline)
    )
}

/** 40dp rounded icon chip matching the Settings screen style. */
@Composable
private fun IconChip(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE4DCFB)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandPurple,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(icon = icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MutedGray
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}
