package dev.pridgey.gates.timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pridgey.gates.Gates
import dev.pridgey.gates.data.Limits
import dev.pridgey.gates.store
import dev.pridgey.gates.ui.Eyebrow
import dev.pridgey.gates.ui.GSheet
import dev.pridgey.gates.ui.GatesTheme
import dev.pridgey.gates.ui.PrimaryButton
import dev.pridgey.gates.ui.ReasonQuote
import dev.pridgey.gates.ui.SheetTitle
import dev.pridgey.gates.ui.TimeChoices

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
                GSheet(onDismiss = ::close) {
                    Eyebrow("Time's up")
                    SheetTitle(session.label)
                    session.reason?.let { ReasonQuote(it) }
                    Spacer(Modifier.height(28.dp))
                    PrimaryButton("Close", Modifier.fillMaxWidth(), onClick = ::close)
                    Spacer(Modifier.height(20.dp))
                    if (more.isNotEmpty()) {
                        Text(
                            "Or a little more", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        TimeChoices(more) { minutes ->
                            Gates.extendSession(this@TimesUpActivity, minutes)
                            finish()
                        }
                    } else {
                        Text(
                            "That's all for today. ${Gates.backAt(this@TimesUpActivity)}.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    private fun close() {
        Gates.endSession(this)
        Gates.goHome(this)
        finish()
    }
}
