package com.telegram.clone.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.components.LightSearchBar
import com.telegram.clone.ui.components.TdFileImage
import com.telegram.clone.ui.theme.BrandBlue
import com.telegram.clone.ui.theme.BrandPurple
import com.telegram.clone.ui.theme.CardBg
import com.telegram.clone.ui.theme.ChipAmber
import com.telegram.clone.ui.theme.ChipAmberBg
import com.telegram.clone.ui.theme.ChipBlue
import com.telegram.clone.ui.theme.ChipBlueBg
import com.telegram.clone.ui.theme.ChipCyan
import com.telegram.clone.ui.theme.ChipCyanBg
import com.telegram.clone.ui.theme.ChipGreen
import com.telegram.clone.ui.theme.ChipGreenBg
import com.telegram.clone.ui.theme.ChipOrange
import com.telegram.clone.ui.theme.ChipOrangeBg
import com.telegram.clone.ui.theme.ChipPink
import com.telegram.clone.ui.theme.ChipPinkBg
import com.telegram.clone.ui.theme.ChipPurple
import com.telegram.clone.ui.theme.ChipPurpleBg
import com.telegram.clone.ui.theme.ChipRed
import com.telegram.clone.ui.theme.ChipRedBg
import com.telegram.clone.ui.theme.Hairline
import com.telegram.clone.ui.theme.Ink
import com.telegram.clone.ui.theme.MutedGray
import com.telegram.clone.ui.theme.StatusGreenSpec
import com.telegram.clone.ui.theme.TelegramCloneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings screen — faithful implementation of the redesigned interface
 * spec:
 *
 * - Purple-to-blue gradient header (#7B5FE8 -> #3FB6F5) with rounded
 *   bottom corners and the floating white pill search bar overlapping it.
 * - Centered profile block: gradient-ringed avatar, name, handle, and a
 *   green online status.
 * - Three quick actions: Edit Profil, Kode QR, Bagikan.
 * - Sectioned menu cards ("Umum", "Keamanan", "Lainnya") on soft white
 *   cards with hairline dividers and colorful rounded icon chips.
 * - Floating dark bottom navigation is provided by MainActivity.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onDevicesClick: () -> Unit = {},
    onDataStorageClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onPremiumClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onSavedMessagesClick: () -> Unit = {},
    onNekogramClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { TelegramRepository.getInstance() }
    val settings = remember { AppSettingsManager.getInstance(context) }
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val premiumBadge by settings.premiumBadge.collectAsState()
    val connectionState by repository.connectionState.collectAsState()
    val connectionReady =
        connectionState?.constructor == org.drinkless.tdlib.TdApi.ConnectionStateReady.CONSTRUCTOR

    val fullName = buildString {
        append(currentUser?.firstName ?: "")
        currentUser?.lastName?.let { if (it.isNotBlank()) append(" $it") }
    }.ifBlank { "User" }

    val username = currentUser?.usernames?.activeUsernames?.firstOrNull() ?: ""
    val phoneNumber = currentUser?.phoneNumber ?: ""
    val profileLink = if (username.isNotBlank()) "https://t.me/$username" else ""

    var searchQuery by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }

    fun shareProfile() {
        val text = if (profileLink.isNotBlank()) {
            "Hubungi saya di Yugram: $profileLink"
        } else {
            "Yugram — teman Telegram baharu anda"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Bagikan profil"))
        } catch (_: Exception) {
            // No share target installed — ignore.
        }
    }

    // Menu structure per the spec, filterable from the search pill.
    val sections = remember(onProfileClick, onNotificationsClick, onPrivacyClick,
        onDevicesClick, onDataStorageClick, onAppearanceClick, onLanguageClick,
        onPremiumClick, onAboutClick, onSavedMessagesClick, onNekogramClick) {
        listOf(
            "Umum" to listOf(
                MenuEntry(
                    icon = Icons.Default.Star,
                    chipBg = ChipAmberBg,
                    chipTint = ChipAmber,
                    label = "Pesan Tersimpan",
                    sub = "Favorit & pesan penting",
                    onClick = onSavedMessagesClick
                ),
                MenuEntry(
                    icon = Icons.Default.Person,
                    chipBg = ChipPurpleBg,
                    chipTint = ChipPurple,
                    label = "Akun Saya",
                    sub = "Nama, foto, bio",
                    onClick = onProfileClick
                ),
                MenuEntry(
                    icon = Icons.Default.Notifications,
                    chipBg = ChipOrangeBg,
                    chipTint = ChipOrange,
                    label = "Notifikasi",
                    sub = "Bunyi, pratinjau, pengecualian",
                    onClick = onNotificationsClick
                )
            ),
            "Keamanan" to listOf(
                MenuEntry(
                    icon = Icons.Default.Lock,
                    chipBg = ChipCyanBg,
                    chipTint = ChipCyan,
                    label = "Privasi & Keamanan",
                    sub = "Nomor telepon, terakhir dilihat",
                    onClick = onPrivacyClick
                ),
                MenuEntry(
                    icon = Icons.Default.Devices,
                    chipBg = ChipGreenBg,
                    chipTint = ChipGreen,
                    label = "Perangkat Aktif",
                    sub = "Sesi login aktif",
                    onClick = onDevicesClick
                ),
                MenuEntry(
                    icon = Icons.Default.Storage,
                    chipBg = ChipBlueBg,
                    chipTint = ChipBlue,
                    label = "Data & Penyimpanan",
                    sub = "Unduh otomatis, cache",
                    onClick = onDataStorageClick
                )
            ),
            "Lainnya" to listOf(
                MenuEntry(
                    icon = Icons.Default.Tune,
                    chipBg = ChipPurpleBg,
                    chipTint = ChipPurple,
                    label = "Fitur Nekogram",
                    sub = "Terjemah, muat turun pukal, hantar senyap",
                    onClick = onNekogramClick
                ),
                MenuEntry(
                    icon = Icons.Default.WorkspacePremium,
                    chipBg = ChipAmberBg,
                    chipTint = ChipAmber,
                    label = "Yugram Premium",
                    sub = "Percuma selamanya — semua fitur",
                    onClick = onPremiumClick
                ),
                MenuEntry(
                    icon = Icons.Default.Palette,
                    chipBg = ChipPinkBg,
                    chipTint = ChipPink,
                    label = "Penampilan",
                    sub = "Tema, warna, latar chat",
                    onClick = onAppearanceClick
                ),
                MenuEntry(
                    icon = Icons.Default.Language,
                    chipBg = ChipRedBg,
                    chipTint = ChipRed,
                    label = "Bahasa",
                    sub = "Bahasa paparan",
                    onClick = onLanguageClick
                ),
                MenuEntry(
                    icon = Icons.Default.Info,
                    chipBg = ChipBlueBg,
                    chipTint = ChipBlue,
                    label = "Tentang",
                    sub = "Versi, sumber, kontak",
                    onClick = onAboutClick
                )
            )
        )
    }

    val query = searchQuery.trim()
    val filteredSections = if (query.isEmpty()) {
        sections
    } else {
        sections.mapNotNull { (title, entries) ->
            val matches = entries.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.sub.contains(query, ignoreCase = true)
            }
            if (matches.isEmpty()) null else title to matches
        }
    }

    TelegramCloneTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
        ) {
            // ---------- Gradient header + floating search ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(listOf(BrandPurple, BrandBlue)),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .statusBarsPadding()
            ) {
                Text(
                    text = "Yugram",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 62.dp)
                )
                LightSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp)
                        .offset(y = 24.dp)
                )
            }

            // ---------- Profile block ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(listOf(BrandPurple, BrandBlue)),
                            shape = CircleShape
                        )
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(BrandPurple),
                    contentAlignment = Alignment.Center
                ) {
                    val photo = currentUser?.profilePhoto?.small
                    if (photo != null) {
                        TdFileImage(
                            file = photo,
                            contentDescription = fullName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = fullName.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fullName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    if (premiumBadge) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val handleLine = buildString {
                    if (username.isNotBlank()) append("@$username")
                    if (phoneNumber.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("+$phoneNumber")
                    }
                }
                if (handleLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = handleLine,
                        fontSize = 14.sp,
                        color = MutedGray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (connectionReady) StatusGreenSpec else MutedGray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (connectionReady) "Online" else "Luar talian",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (connectionReady) StatusGreenSpec else MutedGray
                    )
                }

                // ---------- Quick actions ----------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickAction(
                        icon = Icons.Default.Edit,
                        iconBg = ChipPurpleBg,
                        iconTint = ChipPurple,
                        label = "Edit Profil",
                        onClick = onProfileClick,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        icon = Icons.Default.QrCode2,
                        iconBg = ChipCyanBg,
                        iconTint = ChipCyan,
                        label = "Kode QR",
                        onClick = { showQrDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        icon = Icons.Default.Share,
                        iconBg = ChipGreenBg,
                        iconTint = ChipGreen,
                        label = "Bagikan",
                        onClick = { shareProfile() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---------- Sectioned menu cards ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                filteredSections.forEach { (sectionTitle, entries) ->
                    Text(
                        text = sectionTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedGray,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardBg)
                            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                    ) {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 66.dp)
                                        .height(1.dp)
                                        .background(Hairline)
                                )
                            }
                            SettingsMenuRow(entry = entry)
                        }
                    }
                }

                if (filteredSections.isEmpty()) {
                    Text(
                        text = "Tiada hasil untuk \"$query\"",
                        fontSize = 14.sp,
                        color = MutedGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize()
                            .padding(top = 24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    // ---------- QR dialog ----------
    if (showQrDialog) {
        val qrLink = profileLink.ifBlank {
            if (phoneNumber.isNotBlank()) "tel:+$phoneNumber" else "Yugram"
        }
        val qrBitmap by produceState<Bitmap?>(initialValue = null, qrLink) {
            value = withContext(Dispatchers.Default) {
                try {
                    val hints = mapOf(EncodeHintType.MARGIN to 1)
                    val matrix = QRCodeWriter().encode(
                        qrLink, BarcodeFormat.QR_CODE, 640, 640, hints
                    )
                    val bmp = Bitmap.createBitmap(
                        matrix.width, matrix.height, Bitmap.Config.ARGB_8888
                    )
                    for (x in 0 until matrix.width) {
                        for (y in 0 until matrix.height) {
                            bmp.setPixel(
                                x, y,
                                if (matrix[x, y]) android.graphics.Color.BLACK
                                else android.graphics.Color.WHITE
                            )
                        }
                    }
                    bmp
                } catch (_: Exception) {
                    null
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Text(
                    text = "Kode QR Saya",
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val bmp = qrBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Kode QR",
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Hairline),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Menjana…", color = MutedGray, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (username.isNotBlank()) "@$username" else fullName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    if (profileLink.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = profileLink,
                            fontSize = 12.sp,
                            color = MutedGray
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { shareProfile() }) {
                    Text("Bagikan", color = BrandPurple, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Tutup", color = MutedGray)
                }
            }
        )
    }
}

private data class MenuEntry(
    val icon: ImageVector,
    val chipBg: Color,
    val chipTint: Color,
    val label: String,
    val sub: String,
    val onClick: () -> Unit
)

@Composable
private fun SettingsMenuRow(entry: MenuEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(entry.chipBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = entry.chipTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            if (entry.sub.isNotBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = entry.sub,
                    fontSize = 12.sp,
                    color = MutedGray
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFC7C7CC),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink
        )
    }
}
