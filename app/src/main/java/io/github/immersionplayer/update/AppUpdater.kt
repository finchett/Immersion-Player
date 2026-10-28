package io.github.immersionplayer.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.immersionplayer.App
import io.github.immersionplayer.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/** Where an update stands, for the library. */
sealed interface AppUpdateState {
    data object None : AppUpdateState
    data class Downloading(val version: String, val progress: Float) : AppUpdateState
    /** Downloaded and checked, waiting to go in. [notes] are its release notes. */
    data class Ready(val version: String, val notes: String, val apk: File) : AppUpdateState
    data class Installing(val version: String, val notes: String) : AppUpdateState
}

/**
 * Keeps the app on the latest release. It checks the feed ([Updates]) at launch and every few
 * hours and downloads a newer APK in the background; Android then checks it carries our signing
 * key before it replaces the app.
 *
 * The first update asks: Android wants "Install unknown apps" allowed for this app, then a tap on
 * its confirmation. That install makes this app its own installer, after which (Android 12 and
 * newer) updates go in without asking, whenever the app is put away off the player.
 */
class AppUpdater(private val app: App) {
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.None)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** The notes of the update on its way, for its first launch. */
    val whatsNew = WhatsNew(File(app.filesDir, "whats-new.json"))

    private val dir = File(app.cacheDir, "updates")

    fun start() {
        Thread({
            while (true) {
                try {
                    check()
                } catch (e: Exception) {
                    app.logError("update check", e)
                    if (_state.value is AppUpdateState.Downloading) _state.value = AppUpdateState.None
                }
                Thread.sleep(CHECK_EVERY_MS)
            }
        }, "updates").apply { isDaemon = true }.start()
    }

    private fun check() {
        val release = Updates.latest()
        val code = versionCode(release.version)
        if (code <= BuildConfig.VERSION_CODE) {
            dir.deleteRecursively()
            _state.value = AppUpdateState.None
            return
        }
        if (_state.value.let { it is AppUpdateState.Ready && it.version == release.version || it is AppUpdateState.Installing }) return
        val asset = release.assets[PLATFORM] ?: return
        _state.value = AppUpdateState.Downloading(release.version, 0f)
        val apk = Updates.download(asset, File(dir, asset.name)) { progress ->
            _state.value = AppUpdateState.Downloading(release.version, progress)
        }
        val info = app.packageManager.getPackageArchiveInfo(apk.path, 0)
        if (info?.packageName != app.packageName || info.longVersionCode != code.toLong()) {
            apk.delete()
            throw IOException("${asset.name} isn't version ${release.version} of this app")
        }
        whatsNew.expect(release.version, release.notes)
        _state.value = AppUpdateState.Ready(release.version, release.notes, apk)
    }

    /** Android 12 and newer let the app that installed this one update it without asking. */
    val installsSilently: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            app.packageManager.canRequestPackageInstalls() &&
            runCatching { app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName }
                .getOrNull() == app.packageName

    /**
     * Installs the downloaded update. Until "Install unknown apps" is allowed for this app, this
     * opens that setting instead; tap again once it's on.
     */
    fun install(context: Context) {
        val ready = _state.value as? AppUpdateState.Ready ?: return
        if (!app.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        _state.value = AppUpdateState.Installing(ready.version, ready.notes)
        Thread({
            try {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(ready.apk.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, ready.apk.length()).use { out ->
                        ready.apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    // PackageInstaller fills in the outcome, so the intent has to be mutable
                    val result = PendingIntent.getBroadcast(
                        app, id, Intent(app, InstallResultReceiver::class.java),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    session.commit(result.intentSender)
                }
            } catch (e: Exception) {
                app.logError("update install", e)
                _state.value = ready
            }
        }, "update-install").start()
    }

    /** The install didn't go through (declined, or it failed): offer it again. */
    internal fun installFailed(message: String?) {
        app.logError("update install", IOException(message ?: "failed"))
        val installing = _state.value as? AppUpdateState.Installing ?: return
        val apk = dir.listFiles().orEmpty().firstOrNull { it.name.endsWith(".apk") }
        _state.value = if (apk != null) AppUpdateState.Ready(installing.version, installing.notes, apk) else AppUpdateState.None
    }

    companion object {
        private const val PLATFORM = "android-arm64"
        private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

        /** The same sum as app/build.gradle.kts: 0.5.0 is 500. */
        fun versionCode(version: String): Int {
            val (major, minor, patch) = (version.split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)).take(3)
            return major * 10000 + minor * 100 + patch
        }
    }
}

/** PackageInstaller's answer to a commit: its confirmation screen when it needs one, or the outcome. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> app.updater.installFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
