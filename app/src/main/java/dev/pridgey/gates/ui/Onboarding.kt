package dev.pridgey.gates.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pridgey.gates.data.Days

/** First run, full screen (RESET-6 plus the one-time Still mode offer). */
@Composable
fun Onboarding(onDone: (resetMinutes: Int, still: Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var reset by remember { mutableIntStateOf(6 * 60) }
    var still by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            // Swallow touches so nothing behind reacts.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        AnimatedContent(
            step,
            transitionSpec = {
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 400f)) { it / 6 } + fadeIn(tween(220)))
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "onboarding",
        ) { s ->
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(72.dp))
                when (s) {
                    0 -> {
                        Eyebrow("Welcome to Gates")
                        Text("When does your day usually start?", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Daily limits refill then, not at midnight, so late nights never come with a fresh allowance.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(32.dp))
                        GroupCard {
                            listOf(5, 6, 7, 8, 9).forEachIndexed { i, h ->
                                if (i > 0) CardDivider()
                                RowItem(
                                    Days.formatTime(h * 60),
                                    trailing = {
                                        if (reset == h * 60) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    },
                                ) { reset = h * 60 }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        PrimaryButton("Continue", Modifier.fillMaxWidth().padding(bottom = 16.dp)) { step = 1 }
                    }
                    else -> {
                        Eyebrow("One more thing")
                        Text("Want a calmer phone?", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Still mode adds warm, quiet colours, a slow breath when you open a gated app, and a soft fade when time's up. You can change it any time.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(32.dp))
                        GroupCard { ToggleRow("Still mode", checked = still) { still = it } }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Long-press any app to gate it or make it a favorite on home.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        PrimaryButton("Start", Modifier.fillMaxWidth()) { onDone(reset, still) }
                        QuietButton("Back", Modifier.fillMaxWidth().padding(bottom = 8.dp)) { step = 0 }
                    }
                }
            }
        }
    }
}
