package io.github.immersionplayer.subs

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Reads subtitle pictures with Tesseract (Tesseract4Android) in one [model] ("eng", "jpn").
 * Holds native memory: [close] it when done. Not thread-safe; one per reading thread.
 */
class TesseractRecognizer(context: Context, model: String) : TextRecognizer {
    private val api = TessBaseAPI()

    init {
        // Tesseract4Android wants the folder that holds "tessdata", not tessdata itself
        check(api.init(dataRoot(context, model).path, model)) { "Tesseract couldn't load the $model model" }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
        // subtitle pictures carry no resolution; this is about what their text size corresponds to
        api.setVariable("user_defined_dpi", "300")
    }

    override fun read(image: SubtitleImage): String {
        api.setImage(image.pixels, image.width, image.height, 1, image.width)
        return api.utF8Text.orEmpty()
    }

    override fun close() = api.recycle()

    companion object {
        /**
         * The folder holding tessdata/, with [model] copied out of the app's assets on first use
         * and again after an app update (Tesseract reads models from files). Synchronized: each
         * track's reader calls it.
         */
        @Synchronized
        private fun dataRoot(context: Context, model: String): File {
            val root = File(context.noBackupFilesDir, "ocr")
            val dir = File(root, "tessdata").apply { mkdirs() }
            val file = File(dir, "$model.traineddata")
            // assets are compressed, so their size isn't known without reading them: the install
            // time says whether this copy came from the app as it is now
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            val stamp = File(dir, "$model.installed")
            if (!file.isFile || !stamp.isFile || stamp.readText() != installed) {
                val partial = File(dir, "$model.traineddata.part")
                context.assets.open("tessdata/$model.traineddata").use { input -> partial.outputStream().use { input.copyTo(it) } }
                partial.renameTo(file)
                stamp.writeText(installed)
            }
            return root
        }
    }
}
