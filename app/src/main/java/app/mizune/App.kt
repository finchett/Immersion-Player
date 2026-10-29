package app.mizune

import android.app.Application
import app.mizune.anki.AnkiDroid
import app.mizune.dictionary.AndroidSql
import app.mizune.dictionary.BundledDictionaries
import app.mizune.dictionary.DictionaryDatabase
import app.mizune.dictionary.DictionaryLookup
import app.mizune.mining.MiningStore
import app.mizune.stats.UsageStats
import app.mizune.subs.SubtitleLoader
import app.mizune.ui.AnkiCards
import app.mizune.ui.Appearance
import app.mizune.ui.ThumbnailStore
import app.mizune.update.AppUpdater

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var dictionaryDatabase: DictionaryDatabase
        private set
    lateinit var lookup: DictionaryLookup
        private set
    lateinit var miningStore: MiningStore
        private set
    lateinit var subtitleLoader: SubtitleLoader
        private set
    lateinit var appearance: Appearance
        private set
    lateinit var thumbnails: ThumbnailStore
        private set
    lateinit var anki: AnkiCards
        private set
    lateinit var updater: AppUpdater
        private set

    /** Appends a non-fatal error to files/errors.log (the system log isn't always available). */
    fun logError(context: String, error: Throwable) {
        runCatching {
            val file = java.io.File(filesDir, "errors.log")
            if (file.length() > 256 * 1024) file.delete()
            file.appendText("${java.util.Date()} $context\n${error.stackTraceToString()}\n")
        }
    }

    /** Counts this install as using the app, at most once a day; see core's UsageStats. */
    private fun sendUsageStats() {
        val stats = UsageStats(BuildConfig.ANALYTICS_MEASUREMENT_ID, BuildConfig.ANALYTICS_API_SECRET)
        val now = System.currentTimeMillis()
        if (!stats.configured || !prefs.usageStats || now - prefs.usageStatsSentAt < 20 * 60 * 60 * 1000L) return
        Thread({
            val sent = stats.appOpen(prefs.installId, "android", android.os.Build.VERSION.RELEASE, BuildConfig.VERSION_NAME)
            if (sent) prefs.usageStatsSentAt = now
        }, "usage-stats").apply { isDaemon = true }.start()
    }

    override fun onCreate() {
        super.onCreate()
        // keep the last crash on disk (the system log isn't always available)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { java.io.File(filesDir, "last_crash.txt").writeText(error.stackTraceToString()) }
            defaultHandler?.uncaughtException(thread, error)
        }
        prefs = Prefs(this)
        dictionaryDatabase = DictionaryDatabase { AndroidSql(this, "dictionaries.db") }
        lookup = DictionaryLookup(dictionaryDatabase)
        miningStore = MiningStore(java.io.File(filesDir, "mined_cards.jsonl"))
        subtitleLoader = SubtitleLoader(this)
        appearance = Appearance(prefs)
        thumbnails = ThumbnailStore(this)
        anki = AnkiCards(
            connect = { AnkiDroid(this) },
            target = prefs::cardTarget,
            mediaDir = java.io.File(cacheDir, "anki"),
            logError = ::logError,
        )
        updater = AppUpdater(this).apply { start() }
        sendUsageStats()
        Thread {
            dictionaryDatabase.deleteIncomplete()
            BundledDictionaries.installMissing(this, dictionaryDatabase)
        }.start()
    }
}
