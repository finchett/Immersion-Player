package io.github.immersionplayer

import android.app.Application
import io.github.immersionplayer.dictionary.DictionaryDatabase
import io.github.immersionplayer.dictionary.DictionaryLookup
import io.github.immersionplayer.mining.MiningStore
import io.github.immersionplayer.subs.SubtitleLoader

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

    override fun onCreate() {
        super.onCreate()
        // keep the last crash on disk (the system log isn't always available)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { java.io.File(filesDir, "last_crash.txt").writeText(error.stackTraceToString()) }
            defaultHandler?.uncaughtException(thread, error)
        }
        prefs = Prefs(this)
        dictionaryDatabase = DictionaryDatabase(this)
        lookup = DictionaryLookup(dictionaryDatabase)
        miningStore = MiningStore(this)
        subtitleLoader = SubtitleLoader(this)
        Thread { dictionaryDatabase.deleteIncomplete() }.start()
    }
}
