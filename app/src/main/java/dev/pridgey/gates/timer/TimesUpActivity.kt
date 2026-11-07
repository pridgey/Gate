package dev.pridgey.gates.timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.store
import dev.pridgey.gates.ui.GatesTheme
import dev.pridgey.gates.ui.TimeChips

/** TIME-2: at zero, Close (default) or More time drawn from what's left today. */
class TimesUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = store.state.value.session
        if (session == null) {
            finish()
            return
        }
        val remaining = Gates.remaining(this, session.pkg)
        val more = Limits.choices(listOf(5, 10), remaining).filter { it.first > 0 }
        setContent {
            GatesTheme(still = store.state.value.settings.stillActive()) {
                BackHandler { close() }
                TimesUp(
                    label = session.label,
                    reason = session.reason,
                    more = more,
                    onClose = ::close,
                    onMore = { minutes ->
                        Gates.extendSession(this, minutes)
                        finish()
                    },
                )
            }
        }
    }

    private fun close() {
        Gates.endSession(this)
        Gates.goHome(this)
        finish()
    }
}

@Composable
private fun TimesUp(
    label: String,
    reason: String?,
    more: List<Pair<Int, Boolean>>,
    onClose: () -> Unit,
    onMore: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Time's up", style = MaterialTheme.typography.headlineSmall)
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (reason != null) {
                    Text("You came to: “$reason”", fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                if (more.isNotEmpty()) {
                    Text("More time", style = MaterialTheme.typography.labelLarge)
                    TimeChips(more, onPick = onMore)
                } else {
                    Text("No time left today.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
