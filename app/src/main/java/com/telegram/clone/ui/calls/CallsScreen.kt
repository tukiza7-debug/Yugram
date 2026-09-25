package com.telegram.clone.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.data.model.ChatType
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.theme.StatusError
import com.telegram.clone.ui.theme.StatusOnline
import com.telegram.clone.ui.theme.NovaPurple
import com.telegram.clone.ui.theme.TelegramCloneTheme
import com.telegram.clone.ui.theme.TelegramTextStyles
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Calls screen — shows recent call log entries and lets the user start a new
 * call from the chat list. Call entries are derived from the chat list where
 * call messages exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    onBackClick: () -> Unit,
    onCallClick: (Long) -> Unit
) {
    val repository = remember { TelegramRepository.getInstance() }
    val chats by repository.chatList.collectAsState(initial = emptyList())

    TelegramCloneTheme {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Nova gradient header (tab screen — no back arrow)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(
                                    com.telegram.clone.ui.theme.NovaGradientStart,
                                    com.telegram.clone.ui.theme.NovaGradientEnd
                                )
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                bottomStart = 28.dp, bottomEnd = 28.dp
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Calls",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
                if (chats.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No recent calls", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Start a call from any chat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(chats, key = { it.chatId }) { chat ->
                            // Show private chats as call targets
                            if (chat.chatType == ChatType.PRIVATE || chat.chatType == ChatType.SECRET) {
                                CallLogItem(
                                    name = chat.title,
                                    avatarColor = chat.avatarPlaceholderColor,
                                    isOnline = false,
                                    onCallClick = { onCallClick(chat.chatId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogItem(
    name: String,
    avatarColor: Int,
    isOnline: Boolean,
    onCallClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCallClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(avatarColor)), contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = TelegramTextStyles.chatTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CallReceived, null, tint = StatusOnline, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tap to call", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Call button
        IconButton(onClick = onCallClick) {
            Icon(Icons.Default.Call, "Call", tint = NovaPurple)
        }
    }
}
