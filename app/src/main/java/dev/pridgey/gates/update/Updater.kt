package dev.pridgey.gates.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.pridgey.gates.diag.DiagLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-updater. CI publishes a release with the APK on every push to main of [REPO].
 * Flow: check the latest release → download its APK → verify it is Gates,
 * newer, and signed with the same key → hand it to Android's package installer, which
 * asks the user to confirm.
 */
object Updater {
    const val REPO = "pridgey/Gate"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    private const val PREFS = "updates"

    data class Release(
        val tag: String,
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val checkedAt: Long) : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String, val release: Release? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun currentVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Check unless we checked recently. Quiet: failures only reach the log. */
    suspend fun checkIfDue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong("lastCheck", 0)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        if (_state.value is State.Downloading || _state.value is State.Installing) return
        check(context, quiet = true)
    }

    suspend fun check(context: Context, quiet: Boolean = false): State = withContext(Dispatchers.IO) {
        if (!quiet) _state.value = State.Checking
        val result = runCatching { fetchLatest() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("lastCheck", System.currentTimeMillis()).apply()
        val next: State = result.fold(
            onSuccess = { release ->
                when {
                    release == null -> State.UpToDate(System.currentTimeMillis())
                    release.versionCode > currentVersionCode(context) -> State.Available(release)
                    else -> State.UpToDate(System.currentTimeMillis())
                }
            },
            onFailure = {
                DiagLog.add(context, "update", "check failed: $it")
                if (quiet) _state.value else State.Failed("Couldn't reach GitHub. Check your connection and try again.")
            },
        )
        if (next is State.Available) DiagLog.add(context, "update", "available: ${next.release.tag}")
        _state.value = next
        next
    }

    private fun fetchLatest(): Release? {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Gates-Updater")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name")
            val code = Regex("""v?\d+\.\d+\.(\d+)""").matchEntire(tag)?.groupValues?.get(1)?.toInt() ?: return null
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
            return Release(
                tag = tag,
                versionCode = code,
                versionName = tag.removePrefix("v"),
                notes = json.optString("body").trim(),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size"),
            )
        } finally {
            conn.disconnect()
        }
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Download, verify and start installing [release]. Progress goes to [state]. */
    suspend fun downloadAndInstall(context: Context, release: Release) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            _state.value = State.Downloading(release, 0f)
            val dir = File(app.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val file = File(dir, "gates-${release.versionName}.apk")
            download(release, file)
            verify(app, file, release)
            _state.value = State.Installing(release)
            install(app, file)
            DiagLog.add(app, "update", "installing ${release.tag}")
        } catch (e: Exception) {
            DiagLog.add(app, "update", "update failed: $e")
            _state.value = State.Failed(e.message ?: "The update didn't download.", release)
        }
    }

    private fun download(release: Release, out: File) {
        var url = URL(release.apkUrl)
        // GitHub redirects asset downloads to its storage host; follow by hand to be safe.
        repeat(5) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Gates-Updater")
                setRequestProperty("Accept", "application/octet-stream")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            when (conn.responseCode) {
                in 300..399 -> {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect()
                }
                in 200..299 -> {
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastReported = 0f
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = done.toFloat() / total
                                    if (p - lastReported >= 0.01f) {
                                        lastReported = p
                                        _state.value = State.Downloading(release, p)
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    return
                }
                else -> error("Download failed (HTTP ${conn.responseCode})")
            }
        }
        error("Too many redirects")
    }

    /** Refuse anything that isn't a newer Gates signed with our own key. */
    private fun verify(context: Context, apk: File, release: Release) {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("The download isn't a valid app.")
        if (info.packageName != context.packageName) error("The download isn't Gates.")
        if (info.longVersionCode <= currentVersionCode(context)) error("The download isn't newer than this version.")
        val mine = signers(pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
        val theirs = signers(info)
        if (mine.isEmpty() || mine != theirs) error("The download is signed with a different key, so it can't update this app.")
        if (info.longVersionCode != release.versionCode.toLong()) {
            DiagLog.add(context, "update", "note: tag ${release.tag} but APK versionCode ${info.longVersionCode}")
        }
    }

    private fun signers(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("gates.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    internal fun onInstallResult(context: Context, status: Int, message: String?) {
        val release = (state.value as? State.Installing)?.release
        DiagLog.add(context, "update", "install status=$status $message")
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> State.UpToDate(System.currentTimeMillis())
            PackageInstaller.STATUS_FAILURE_ABORTED -> release?.let { State.Available(it) } ?: State.Idle
            else -> State.Failed(message ?: "Android couldn't install the update.", release)
        }
    }
}

/** Receives the package installer's result; asks the user to confirm when Android needs it. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        Updater.onInstallResult(context, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
