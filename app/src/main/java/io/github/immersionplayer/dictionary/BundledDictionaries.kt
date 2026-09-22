package io.github.immersionplayer.dictionary

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Installs the dictionaries shipped in assets/dictionaries on first launch.
 * Each bundled file is installed once; removing it afterwards is respected.
 */
object BundledDictionaries {
    private const val ASSET_DIR = "dictionaries"

    private val _status = MutableStateFlow<String?>(null)
    /** Human-readable progress while a bundled dictionary is being set up, else null. */
    val status: StateFlow<String?> = _status.asStateFlow()

    fun installMissing(context: Context, database: DictionaryDatabase) {
        val prefs = context.getSharedPreferences("bundled_dictionaries", Context.MODE_PRIVATE)
        val assets = context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".zip") }
        for (asset in assets) {
            val key = "installed:$asset"
            if (prefs.getBoolean(key, false)) continue
            val label = asset.removeSuffix(".zip").replace('_', ' ')
            try {
                _status.value = "Setting up $label…"
                YomitanImporter(database).import(asset, { progress ->
                    _status.value = "Setting up $label… ${progress.terms} terms"
                }) {
                    context.assets.open("$ASSET_DIR/$asset")
                }
                prefs.edit { putBoolean(key, true) }
            } catch (e: Exception) {
                _status.value = "Couldn't set up $label: ${e.message}"
                return
            }
        }
        _status.value = null
    }
}
