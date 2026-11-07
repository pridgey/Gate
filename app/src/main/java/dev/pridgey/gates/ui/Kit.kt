package dev.pridgey.gates.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pridgey.gates.data.GateType
import dev.pridgey.gates.data.LaunchApp

// ---------------------------------------------------------------- feel

/** Gentle spring used for every press and selection change. */
val Soft = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** Haptic helpers: a confirm tick for choices, a firmer one for long-press. */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics { type -> view.performHapticFeedback(type) } }
}

class Haptics(private val perform: (Int) -> Unit) {
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)
    fun long() = perform(HapticFeedbackConstants.LONG_PRESS)
}

/**
 * Calm press feedback: no ripple; the content dims and settles slightly.
 * Long-press gives a haptic.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.55f else 1f, Soft, label = "pressAlpha")
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, Soft, label = "pressScale")
    val haptics = rememberHaptics()
    return this
        .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { { haptics.long(); it() } },
            onClick = onClick,
        )
}

// ---------------------------------------------------------------- sheets

/** The one bottom sheet style used everywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Previews and screenshot tests can't show popups; draw the sheet in place instead.
    if (LocalInspectionMode.current) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.padding(top = 12.dp, bottom = 8.dp).size(36.dp, 4.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp), content = content)
            }
        }
        return
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 8.dp).size(36.dp, 4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            content = content,
        )
    }
}

/** Small label above a title: an optional mark plus spaced capitals. */
@Composable
fun Eyebrow(text: String, gate: GateType? = null, color: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        if (gate != null && gate != GateType.NONE) {
            GateMark(gate, color, 14.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun SheetTitle(text: String, subtitle: String? = null) {
    Text(text, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun TonalButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressable(onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).pressable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** A large time choice on the Pause and time's-up sheets: "10" over "min". */
@Composable
fun TimeChoice(minutes: Int, isAll: Boolean, modifier: Modifier = Modifier, onPick: () -> Unit) {
    val haptics = rememberHaptics()
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isAll) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressable { haptics.confirm(); onPick() }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = if (isAll) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        Text(if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}" else "$minutes", style = MaterialTheme.typography.displaySmall, color = color)
        Text(
            when {
                isAll -> "all left"
                minutes >= 60 && minutes % 60 == 0 -> if (minutes == 60) "hour" else "hours"
                else -> "min"
            },
            style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.7f),
        )
    }
}

@Composable
fun TimeChoices(choices: List<Pair<Int, Boolean>>, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        choices.forEach { (m, all) -> TimeChoice(m, all, Modifier.weight(1f)) { onPick(m) } }
    }
}

/** Small selectable pill. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh, label = "pill",
    )
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    val haptics = rememberHaptics()
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).pressable { haptics.tick(); onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Segmented control with a sliding thumb. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val index = options.indexOf(selected).coerceAtLeast(0)
    val haptics = rememberHaptics()
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.8f, stiffness = 500f), label = "segment")
        Box(
            Modifier.offset(x = segment * x).width(segment).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, option ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                        .pressable { if (i != index) { haptics.tick(); onSelect(option) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        color = if (i == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- lists

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp),
    )
}

/** A rounded card holding rows, with hairline dividers between them. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
fun CardDivider() = HorizontalDivider(Modifier.padding(start = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun RowItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing?.invoke(this)
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    RowItem(
        title = title,
        subtitle = subtitle,
        titleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = {
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked, onCheckedChange = onChange, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                ),
            )
        },
    )
}

/** A thin proportional bar for the weekly summary and groups. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), Soft, label = "meter")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val r = size.height / 2
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        if (f > 0f) drawRoundRect(color, size = Size(size.width * f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    }
}

// ---------------------------------------------------------------- glyphs

/** Thin line marks for each gate, drawn to sit beside text. */
@Composable
fun GateMark(gate: GateType, color: Color, size: Dp = 16.dp) {
    when (gate) {
        GateType.PAUSE -> Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.1f
            val r = this.size.minDimension / 2 - stroke
            drawCircle(color, r, style = Stroke(stroke))
            drawLine(color, center, center.copy(y = center.y - r * 0.55f), stroke, StrokeCap.Round)
            drawLine(color, center, Offset(center.x + r * 0.4f, center.y + r * 0.25f), stroke, StrokeCap.Round)
        }
        GateType.ASK -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text("?", color = color, fontSize = (size.value * 0.95f).sp, fontFamily = Manrope, style = MaterialTheme.typography.labelLarge)
        }
        GateType.VAULT -> Icon(Icons.Outlined.Lock, null, tint = color, modifier = Modifier.size(size))
        GateType.NONE -> Unit
    }
}

/** The app's own icon, or Gates' mark for the Gates app. */
@Composable
fun AppGlyph(app: LaunchApp, size: Dp = 32.dp) {
    if (app.icon != null) {
        Image(app.icon, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(Color(0xFF2F5D47)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.5f)) {
                val w = this.size.width
                val stroke = w * 0.16f
                val c = Color(0xFFF2EBDD)
                drawArc(
                    color = c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.12f, w * 0.12f), size = Size(w * 0.76f, w * 0.76f), style = Stroke(stroke),
                )
                drawLine(c, Offset(w * 0.12f, w * 0.5f), Offset(w * 0.12f, w), stroke)
                drawLine(c, Offset(w * 0.88f, w * 0.5f), Offset(w * 0.88f, w), stroke)
            }
        }
    }
}
