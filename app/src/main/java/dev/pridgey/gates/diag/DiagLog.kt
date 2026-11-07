package dev.pridgey.gates.diag

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnostics log for the island / Live Updates investigation.
 * Lines go to logcat (tag GatesDiag) and to files/diag.log. Export writes one report to
 *   /sdcard/Android/data/dev.pridgey.gates/files/gates-island-report.txt
 * so it can be pulled with adb or shared from the app.
 */
object DiagLog {
    private const val TAG = "GatesDiag"
    private const val MAX_LINES = 400
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private fun file(context: Context) = File(context.filesDir, "diag.log")

    @Synchronized
    fun add(context: Context, area: String, message: String) {
        val line = "${LocalDateTime.now().format(fmt)} [$area] $message"
        Log.i(TAG, line)
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        runCatching {
            val f = file(context.applicationContext)
            if (f.length() > 2_000_000) f.writeText("")
            f.appendText(line + "\n")
        }
    }

    /** Multi-line blocks (reports) go to the log as-is. */
    fun block(context: Context, area: String, text: String) {
        add(context, area, "\n" + text.trimEnd())
    }

    fun clear(context: Context) {
        _lines.value = emptyList()
        runCatching { file(context).writeText("") }
    }

    fun fullLog(context: Context): String = runCatching { file(context).readText() }.getOrDefault("")

    /** Writes report + full log to external files and returns the file. */
    fun export(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val out = File(dir, "gates-island-report.txt")
        out.writeText(
            buildString {
                appendLine("=== Gates island / Live Updates report ===")
                appendLine(IslandProbe.report(context))
                appendLine()
                appendLine("=== Log ===")
                append(fullLog(context))
            },
        )
        add(context, "export", "wrote ${out.absolutePath}")
        return out
    }

    fun share(context: Context) {
        val text = export(context).readText()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Gates island report")
            // Large reports can exceed binder limits; the file path is the fallback.
            putExtra(Intent.EXTRA_TEXT, if (text.length > 400_000) text.take(400_000) + "\n…truncated" else text)
        }
        context.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
