package com.telegram.clone.ui.calls

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.theme.StatusOnline
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Call screen — a fully functional call UI with a live call timer, mute,
 * speaker, video toggle, and end-call buttons. Opens when the user taps the
 * call or video call button in the chat room.
 *
 * @param isVideoCall If true, shows the video call layout.
 * @param contactName The name of the person being called (shown on screen).
 */
@Composable
fun CallScreen(
    chatId: Long = 0,
    contactName: String = "Contact",
    isVideoCall: Boolean = false,
    onEndCall: () -> Unit
) {
    val repository = remember { TelegramRepository.getInstance() }
    var callState by remember { mutableStateOf(CallState.CONNECTING) }
    var callDurationSeconds by remember { mutableStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(isVideoCall) }
    var isOnSpeaker by remember { mutableStateOf(true) }

    // Simulate the call connecting after a short delay, then start the timer.
    LaunchedEffect(Unit) {
        delay(2000) // Connection delay
        callState = CallState.IN_CALL
        while (callState == CallState.IN_CALL) {
            delay(1000)
            callDurationSeconds++
        }
    }

    // Format the duration as MM:SS
    val durationText = remember(callDurationSeconds) {
        val mins = callDurationSeconds / 60
        val secs = callDurationSeconds % 60
        "%02d:%02d".format(mins, secs)
    }

    val gradientColors = listOf(
        Color(0xFF1A237E),  // Deep blue
        Color(0xFF0D47A1),  // Blue
        Color(0xFF01579B)   // Light blue
    )

    TelegramCloneTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (callState == CallState.IN_CALL) gradientColors else gradientColors.map { it.copy(alpha = 0.8f) }
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(80.dp))

                // Contact info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar / video area
                    Box(
                        modifier = Modifier.size(128.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideoEnabled && callState == CallState.IN_CALL) {
                            // Video placeholder
                            Icon(Icons.Default.Videocam, "Video", tint = Color.White, modifier = Modifier.size(48.dp))
                        } else {
                            Text(
                                text = contactName.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Name
                    Text(
                        text = contactName,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Call status / duration
                    Text(
                        text = when (callState) {
                            CallState.CONNECTING -> if (isVideoCall) "Video calling…" else "Calling…"
                            CallState.IN_CALL -> durationText
                            CallState.ENDED -> "Call ended"
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 18.sp
                    )

                    // Mute indicator
                    if (isMuted && callState == CallState.IN_CALL) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Muted", color = StatusOnline, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Call controls
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 64.dp)
                ) {
                    // Secondary controls (only visible during call)
                    if (callState == CallState.IN_CALL) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mute
                            CallControlButton(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Unmute" else "Mute",
                                active = isMuted,
                                onClick = { isMuted = !isMuted }
                            )

                            // Speaker
                            CallControlButton(
                                icon = Icons.Default.VolumeUp,
                                label = "Speaker",
                                active = isOnSpeaker,
                                onClick = { isOnSpeaker = !isOnSpeaker }
                            )

                            // Video toggle
                            CallControlButton(
                                icon = if (isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                label = if (isVideoEnabled) "Video Off" else "Video On",
                                active = isVideoEnabled,
                                onClick = { isVideoEnabled = !isVideoEnabled }
                            )
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                    }

                    // End call button (always visible)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEA4335)),  // Red
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            callState = CallState.ENDED
                            onEndCall()
                        }) {
                            Icon(
                                Icons.Default.CallEnd,
                                "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (active) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClick) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

enum class CallState {
    CONNECTING,
    IN_CALL,
    ENDED
}
