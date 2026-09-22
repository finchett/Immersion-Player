package io.github.immersionplayer.library

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "webm", "avi", "m4v", "mov", "ts", "m2ts", "wmv", "flv")

fun isVideoName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

/** "日常 12.mkv" -> "12" */
fun episodeNumber(name: String): String? =
    Regex("""(\d{1,3})(?!.*\d)""").find(name.substringBeforeLast('.'))?.groupValues?.get(1)

/** Sorts "Episode 2" before "Episode 10". */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("""\d+|\D+""")

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a).map { it.value }.toList()
        val cb = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                x.toBigInteger().compareTo(y.toBigInteger())
            } else {
                x.compareTo(y, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return ca.size - cb.size
    }
}

fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
