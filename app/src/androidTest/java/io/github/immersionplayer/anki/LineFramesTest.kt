package io.github.immersionplayer.anki

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.immersionplayer.Prefs
import io.github.immersionplayer.library.isVideoName
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Card media from a real video in the library the app has been given, on the phone. The files
 * stay in the app's files/card-media-test for a look:
 *
 *     ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
 *     adb install -r app/build/outputs/apk/debug/app-debug.apk
 *     adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 *     adb shell am instrument -w io.github.immersionplayer.test/androidx.test.runner.AndroidJUnitRunner
 *
 * The results stay in the app's files/card-media-test for a look (adb shell run-as ...).
 */
@RunWith(AndroidJUnit4::class)
class LineFramesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(context.filesDir, "card-media-test").apply { mkdirs() }

    /** The first video under the library folder, looking a few folders deep. */
    private fun video(): Uri? {
        val tree = Prefs(context).libraryTreeUri ?: return null
        fun find(dir: DocumentFile, depth: Int): DocumentFile? {
            for (file in dir.listFiles()) {
                if (file.isFile && isVideoName(file.name.orEmpty())) return file
                if (file.isDirectory && depth < 3) find(file, depth + 1)?.let { return it }
            }
            return null
        }
        return DocumentFile.fromTreeUri(context, Uri.parse(tree))?.let { find(it, 0) }?.uri
    }

    @Test fun lineAnimatesAsWebp() {
        val video = video()
        assumeTrue("no library folder with a video", video != null)
        out.listFiles()?.filter { it.name.startsWith("line.") && it.extension in setOf("avif", "webp") }?.forEach { it.delete() }
        val started = System.currentTimeMillis()
        val file = LineFrames.animate(context, video.toString(), 300.0, 303.0, 360, File(out, "line"))
        val took = System.currentTimeMillis() - started
        assertTrue("no animation", file != null)
        val bytes = file!!.readBytes()
        File(out, "line.txt").writeText("${file.name}: ${bytes.size} bytes in $took ms (${LineFrames.lastReport}) from $video\n")
        if (file.extension == "avif") {
            assertTrue(String(bytes, 4, 8), String(bytes, 4, 8) == "ftypavis")
        } else {
            assertTrue(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" && String(bytes, 12, 4) == "VP8X")
            // the animation decodes, with more than one frame
            val drawable = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
            assertTrue("not animated: $drawable", drawable is android.graphics.drawable.AnimatedImageDrawable)
        }
    }

    @Test fun lineAudioIsOpus() {
        val video = video()
        assumeTrue("no library folder with a video", video != null)
        val file = File(out, "line.ogg")
        assertTrue(AudioClipper.clip(context, video!!, 299.7, 303.3, null, file))
        val head = file.readBytes().take(64).toByteArray().toString(Charsets.ISO_8859_1)
        File(out, "line-audio.txt").writeText("${file.length()} bytes\n")
        assertTrue(head, head.startsWith("OggS") && "OpusHead" in head)
    }
}
