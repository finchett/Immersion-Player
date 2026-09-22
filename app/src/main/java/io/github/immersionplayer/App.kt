package io.github.immersionplayer

import android.app.Application
import io.github.immersionplayer.dictionary.AndroidSql
import io.github.immersionplayer.dictionary.BundledDictionaries
import io.github.immersionplayer.dictionary.DictionaryDatabase
import io.github.immersionplayer.dictionary.DictionaryLookup
import io.github.immersionplayer.mining.MiningStore
import io.github.immersionplayer.subs.SubtitleLoader
import io.github.immersionplayer.ui.Appearance
import io.github.immersionplayer.ui.ThumbnailStore

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

    /** Appends a non-fatal error to files/errors.log (the system log isn't always available). */
    fun logError(context: String, error: Throwable) {
        runCatching {
            val file = java.io.File(filesDir, "errors.log")
            if (file.length() > 256 * 1024) file.delete()
            file.appendText("${java.util.Date()} $context\n${error.stackTraceToString()}\n")
        }
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
        Thread {
            dictionaryDatabase.deleteIncomplete()
            BundledDictionaries.installMissing(this, dictionaryDatabase)
        }.start()
    }
}
