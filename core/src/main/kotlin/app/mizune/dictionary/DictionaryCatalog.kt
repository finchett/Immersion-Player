package app.mizune.dictionary

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Yomitan dictionaries worth having beside the bundled JMdict, downloaded from where their makers
 * publish them. Once installed, each is known by the start of its title.
 */
object DictionaryCatalog {
    data class Entry(
        val id: String,
        val name: String,
        val description: String,
        val url: String,
        /** About how big the download is, to say before it starts. */
        val megabytes: Int,
        val titlePrefix: String,
        val recommended: Boolean,
    )

    val JITENDEX = Entry(
        id = "jitendex",
        name = "Jitendex",
        description = "Fuller English definitions, with example sentences. Takes over from the built-in JMdict.",
        url = "https://github.com/stephenmk/stephenmk.github.io/releases/latest/download/jitendex-yomitan.zip",
        megabytes = 39,
        titlePrefix = "Jitendex",
        recommended = true,
    )
    val JPDB = Entry(
        id = "jpdb",
        name = "JPDB frequency",
        description = "How common each word is, counted across anime, dramas, novels and games.",
        url = "https://github.com/Kuuuube/yomitan-dictionaries/releases/download/yomitan-permalink/JPDB_v2.2_Frequency_Kana.zip",
        megabytes = 6,
        titlePrefix = "JPDB",
        recommended = true,
    )
    val JMNEDICT = Entry(
        id = "jmnedict",
        name = "JMnedict",
        description = "Names of people and places, for the ones a regular dictionary doesn't have.",
        url = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMnedict.zip",
        megabytes = 11,
        titlePrefix = "JMnedict",
        recommended = false,
    )
    val ENTRIES = listOf(JITENDEX, JPDB, JMNEDICT)

    /** The bundled JMdict's title starts so; Jitendex turns it off. */
    const val JMDICT_PREFIX = "JMdict"

    fun installed(entry: Entry, dictionaries: List<DictionaryInfo>): DictionaryInfo? =
        dictionaries.firstOrNull { it.title.startsWith(entry.titlePrefix) }

    /** Downloads [entry] to [target], telling [onProgress] the fraction done (0 while the size is unknown). */
    fun download(entry: Entry, target: File, onProgress: (Float) -> Unit) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        val connection = URI(entry.url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode !in 200..299) error("${entry.name}: HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
    }
}
