package dev.pridgey.gates.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.LaunchApp
import dev.pridgey.gates.data.formatMinutes

@Composable
fun AppGlyph(app: LaunchApp, size: Dp = 52.dp) {
    if (app.icon != null) {
        Image(app.icon, contentDescription = null, modifier = Modifier.size(size))
    } else {
        // The Gates app's own icon: an arch on green.
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(GatesGreen),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.5f)) {
                val w = this.size.width
                drawArc(
                    color = GatesCream, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.1f, w * 0.1f),
                    size = androidx.compose.ui.geometry.Size(w * 0.8f, w * 0.8f),
                    style = Stroke(width = w * 0.18f),
                )
                drawLine(GatesCream, Offset(w * 0.1f, w * 0.5f), Offset(w * 0.1f, w), strokeWidth = w * 0.18f)
                drawLine(GatesCream, Offset(w * 0.9f, w * 0.5f), Offset(w * 0.9f, w), strokeWidth = w * 0.18f)
            }
        }
    }
}

/** HOME-2: gate badge in the icon's corner. */
@Composable
fun GateBadge(gate: GateType, modifier: Modifier = Modifier) {
    if (gate == GateType.NONE) return
    Box(
        modifier.size(18.dp).clip(CircleShape).background(Color(0xFF202020)),
        contentAlignment = Alignment.Center,
    ) {
        when (gate) {
            GateType.PAUSE -> Canvas(Modifier.size(11.dp)) {
                val r = size.minDimension / 2
                drawCircle(Color.White, r, style = Stroke(1.5.dp.toPx()))
                drawLine(Color.White, center, center.copy(y = center.y - r * 0.6f), 1.5.dp.toPx())
                drawLine(Color.White, center, center.copy(x = center.x + r * 0.5f), 1.5.dp.toPx())
            }
            GateType.ASK -> Text("?", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            GateType.VAULT -> Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(11.dp))
            GateType.NONE -> Unit
        }
    }
}

private val OnWallpaper = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f),
)

/**
 * An app icon as it appears on home, in All apps and Upstairs.
 * Time-limited apps show remaining time under the name; [tag] marks Upstairs apps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: LaunchApp,
    gate: GateType,
    remaining: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    onWallpaper: Boolean = true,
    still: Boolean = false,
) {
    val style = if (onWallpaper) OnWallpaper else TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    Column(
        modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppGlyph(app)
            GateBadge(gate, Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
        }
        Spacer(Modifier.height(4.dp))
        val alpha = if (still) 0.7f else 1f
        Text(
            app.label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, color = style.color.copy(alpha = alpha * style.color.alpha),
        )
        when {
            tag != null -> Text(tag, style = style.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            remaining != null -> Text(
                if (remaining == 0) "out of time" else "${formatMinutes(remaining)} left",
                style = style.copy(fontSize = 10.sp), maxLines = 1,
                color = style.color.copy(alpha = 0.8f * alpha),
            )
        }
    }
}
