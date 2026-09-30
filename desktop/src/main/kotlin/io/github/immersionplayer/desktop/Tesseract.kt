package io.github.immersionplayer.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.github.immersionplayer.subs.SubtitleImage
import io.github.immersionplayer.subs.TextRecognizer
import java.io.File

/** Tesseract's C API, as much of it as reading subtitle pictures needs. */
@Suppress("FunctionName")
internal interface TesseractLib : Library {
    fun TessBaseAPICreate(): Pointer
    fun TessBaseAPIInit3(handle: Pointer, dataPath: String, language: String): Int
    fun TessBaseAPISetPageSegMode(handle: Pointer, mode: Int)
    fun TessBaseAPISetVariable(handle: Pointer, name: String, value: String): Int
    fun TessBaseAPISetImage(handle: Pointer, data: ByteArray, width: Int, height: Int, bytesPerPixel: Int, bytesPerLine: Int)
    fun TessBaseAPIGetUTF8Text(handle: Pointer): Pointer?
    fun TessDeleteText(text: Pointer)
    fun TessBaseAPIEnd(handle: Pointer)
    fun TessBaseAPIDelete(handle: Pointer)

    companion object {
        val INSTANCE: TesseractLib by lazy { Native.load(bundled() ?: "tesseract", TesseractLib::class.java) }

        /** The libtesseract packaged with the app, if this is a packaged build; else the system's. */
        private fun bundled(): String? {
            val dir = System.getProperty("compose.application.resources.dir") ?: return null
            return File(dir, "libtesseract.5.dylib").takeIf { it.isFile }?.path
        }
    }
}

/**
 * Reads subtitle pictures with Tesseract in one [model] ("eng", "jpn"). Holds native memory:
 * [close] it when done. Not thread-safe; one per reading thread.
 */
class TesseractRecognizer(dataDir: File, model: String) : TextRecognizer {
    private val lib = TesseractLib.INSTANCE
    private val handle = lib.TessBaseAPICreate()

    init {
        check(lib.TessBaseAPIInit3(handle, dataDir.path, model) == 0) { "Tesseract couldn't load the $model model" }
        lib.TessBaseAPISetPageSegMode(handle, PSM_SINGLE_BLOCK)
        // subtitle pictures carry no resolution; this is about what their text size corresponds to
        lib.TessBaseAPISetVariable(handle, "user_defined_dpi", "300")
    }

    override fun read(image: SubtitleImage): String {
        lib.TessBaseAPISetImage(handle, image.pixels, image.width, image.height, 1, image.width)
        val text = lib.TessBaseAPIGetUTF8Text(handle) ?: return ""
        return try { text.getString(0, "UTF-8") } finally { lib.TessDeleteText(text) }
    }

    override fun close() {
        lib.TessBaseAPIEnd(handle)
        lib.TessBaseAPIDelete(handle)
    }

    companion object {
        private const val PSM_SINGLE_BLOCK = 6

        /**
         * The bundled models, copied out of the app's resources into [cacheDir] once (Tesseract
         * reads them from a folder). Returns that folder. Synchronized: each track's reader calls it.
         */
        @Synchronized
        fun dataDir(cacheDir: File, models: Collection<String>): File {
            val dir = File(cacheDir, "tessdata").apply { mkdirs() }
            for (model in models) {
                val file = File(dir, "$model.traineddata")
                val resource = TesseractRecognizer::class.java.getResource("/tessdata/$model.traineddata") ?: continue
                val size = resource.openConnection().contentLengthLong
                if (file.isFile && file.length() == size) continue
                val partial = File(dir, "$model.traineddata.part")
                resource.openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                partial.renameTo(file)
            }
            return dir
        }
    }
}
