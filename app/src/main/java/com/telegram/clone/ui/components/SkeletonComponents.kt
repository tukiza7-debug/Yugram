package com.telegram.clone.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated shimmer brush used by every skeleton placeholder.
 *
 * A three-stop linear gradient sweeps left-to-right indefinitely,
 * producing the familiar "loading shimmer" effect. Colors are taken
 * from the current Material 3 color scheme ([surfaceVariant] base with
 * a [surface] highlight) so skeletons adapt to light/dark themes.
 */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val sweepWidthPx = 800f
    val start = -sweepWidthPx + progress * 2f * sweepWidthPx

    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + sweepWidthPx, 0f)
    )
}

/**
 * A single shimmering block — the building block for all skeleton
 * placeholders.
 *
 * @param modifier Size / positioning constraints for the block.
 * @param shape    Corner shape of the block (e.g. [CircleShape] for
 *                 avatars, [RoundedCornerShape] for text lines).
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/**
 * Skeleton placeholder for the chat list.
 *
 * Renders [itemCount] rows that mirror the real [ChatItem] row layout:
 * a circular avatar, a title line with a trailing timestamp, and a
 * message-preview line. Defaults to 8 rows as specified by FAZA 5A.
 */
@Composable
fun ChatListSkeleton(itemCount: Int = 8) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(itemCount) {
            ChatListSkeletonRow()
        }
    }
}

@Composable
private fun ChatListSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            modifier = Modifier.size(54.dp),
            shape = CircleShape
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier.size(width = 140.dp, height = 16.dp),
                    shape = RoundedCornerShape(6.dp)
                )
                ShimmerBox(
                    modifier = Modifier.size(width = 40.dp, height = 12.dp),
                    shape = RoundedCornerShape(6.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(
                modifier = Modifier.size(width = 200.dp, height = 14.dp),
                shape = RoundedCornerShape(6.dp)
            )
        }
    }
}

/**
 * Skeleton placeholder for the chat room.
 *
 * Renders [itemCount] message bubbles alternating between incoming
 * (left) and outgoing (right) alignment, with varying widths to
 * mimic a natural conversation. Defaults to 6 bubbles as specified by
 * FAZA 5A.
 */
/** Widths (in dp) cycled through to give chat-room skeleton bubbles variety. */
private val chatRoomSkeletonBubbleWidths = listOf(220.dp, 160.dp, 240.dp, 180.dp, 200.dp, 140.dp)

@Composable
fun ChatRoomSkeleton(itemCount: Int = 6) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        repeat(itemCount) { index ->
            val isOutgoing = index % 2 == 0
            val width = chatRoomSkeletonBubbleWidths[index % chatRoomSkeletonBubbleWidths.size]
            ChatRoomSkeletonBubble(isOutgoing = isOutgoing, width = width)
            if (index < itemCount - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ChatRoomSkeletonBubble(isOutgoing: Boolean, width: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        ShimmerBox(
            modifier = Modifier.size(width = width, height = 44.dp),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Skeleton placeholder for the contacts list.
 *
 * Renders [itemCount] rows that mirror a contact item: a circular
 * avatar, a name line and a status line. Defaults to 6 rows as
 * specified by FAZA 5A.
 */
@Composable
fun ContactsSkeleton(itemCount: Int = 6) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(itemCount) {
            ContactsSkeletonRow()
        }
    }
}

@Composable
private fun ContactsSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            modifier = Modifier.size(44.dp),
            shape = CircleShape
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(
                modifier = Modifier.size(width = 120.dp, height = 14.dp),
                shape = RoundedCornerShape(6.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(
                modifier = Modifier.size(width = 160.dp, height = 12.dp),
                shape = RoundedCornerShape(6.dp)
            )
        }
    }
}
