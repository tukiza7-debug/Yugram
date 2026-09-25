package com.telegram.clone.ui.premium

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.ui.components.novaGradientBrush
import com.telegram.clone.ui.theme.GlassBorder
import com.telegram.clone.ui.theme.NovaCyan
import com.telegram.clone.ui.theme.NovaGradientEnd
import com.telegram.clone.ui.theme.NovaGradientStart
import com.telegram.clone.ui.theme.NovaPurple
import com.telegram.clone.ui.theme.TextSecondaryDark
import com.telegram.clone.ui.theme.CardBg
import com.telegram.clone.ui.theme.Hairline
import com.telegram.clone.ui.theme.Ink
import com.telegram.clone.ui.theme.MutedGray

/**
 * Full-screen PIN gate shown when App Lock is enabled. Dark glass design
 * with a numeric keypad. Calls [verifyPin]; on success unlocks via
 * [onUnlocked].
 */
@Composable
fun AppLockScreen(
    verifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var errorState by rememberSaveable { mutableStateOf(false) }

    fun onDigit(d: Char) {
        if (pin.length >= 6) return
        pin += d
        errorState = false
        if (pin.length >= 4) {
            if (verifyPin(pin)) {
                onUnlocked()
            } else if (pin.length == 6) {
                // Only force-fail feedback after a full-length attempt.
                errorState = true
                pin = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Decorative glow blobs
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopStart)
                .padding(start = -60.dp, top = -60.dp)
                .background(
                    brush = Brush.radialGradient(listOf(NovaPurple.copy(alpha = 0.35f), Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomEnd)
                .padding(end = -80.dp, bottom = -80.dp)
                .background(
                    brush = Brush.radialGradient(listOf(NovaCyan.copy(alpha = 0.25f), Color.Transparent)),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CardBg)
                    .border(1.dp, Hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NovaPurple,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Yugram",
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (errorState) "Wrong PIN — try again" else "Enter your PIN",
                color = if (errorState) Color(0xFFFF5E7A) else MutedGray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN dots (hidden input)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { index ->
                    val filled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    filled -> NovaPurple
                                    else -> NovaPurple.copy(alpha = 0.25f)
                                }
                            )
                            .border(1.dp, GlassBorder, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Hidden text field keeps IME interactions simple; the custom
            // keypad below is the primary input.
            BasicTextField(
                value = pin,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() }.take(6)
                    pin = filtered
                    errorState = false
                    if (filtered.length >= 4 && verifyPin(filtered)) onUnlocked()
                    else if (filtered.length == 6) {
                        errorState = true
                        pin = ""
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = TextStyle(color = Color.Transparent),
                modifier = Modifier.fillMaxWidth().height(1.dp)
            )

            // Keypad 1..9, blank, 0, backspace
            val rows = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf(' ', '0', 'D')
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    row.forEach { key ->
                        if (key == ' ') {
                            Box(modifier = Modifier.size(72.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(CardBg)
                                    .border(1.dp, Hairline, RoundedCornerShape(24.dp))
                                    .clickable {
                                        when (key) {
                                            'D' -> { pin = pin.dropLast(1); errorState = false }
                                            else -> onDigit(key)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == 'D') {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = "Delete",
                                        tint = MutedGray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(
                                        text = key.toString(),
                                        color = Ink,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
