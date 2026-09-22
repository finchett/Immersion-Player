package io.github.immersionplayer.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.immersionplayer.desktop.mpv.MpvPlayer
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--bench") return bench(args[1], args.getOrNull(2)?.toInt() ?: 1920)
    val path = args.firstOrNull()
    application {
        val player = remember { MpvPlayer() }
        DisposableEffect(Unit) {
            path?.let(player::load)
            player.setPaused(false)
            onDispose { player.destroy() }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Immersion Player",
            onKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@Window false
                when (event.key) {
                    Key.Spacebar -> player.togglePause()
                    Key.DirectionLeft -> player.seekRelative(-5.0)
                    Key.DirectionRight -> player.seekRelative(5.0)
                    else -> return@Window false
                }
                true
            },
        ) {
            VideoSurface(player, Modifier.fillMaxSize().background(Color.Black))
        }
    }
}

@Composable
fun VideoSurface(player: MpvPlayer, modifier: Modifier = Modifier) {
    val frame by player.frame.collectAsState()
    var stats by remember { mutableStateOf("") }
    val drawn = remember { java.util.concurrent.atomic.AtomicLong() }
    LaunchedEffect(Unit) {
        var lastFrame = player.frame.value?.serial ?: 0
        var lastDrawn = 0L
        while (true) {
            delay(1000)
            val frames = (player.frame.value?.serial ?: 0).let { it - lastFrame.also { _ -> lastFrame = it } }
            val draws = drawn.get().let { it - lastDrawn.also { _ -> lastDrawn = it } }
            stats = "mpv $frames fps, drawn $draws fps"
            if (System.getProperty("immersion.stats") != null) println(stats)
        }
    }
    Box(modifier.onSizeChanged { player.setTargetSize(it.width, it.height) }) {
        Canvas(Modifier.fillMaxSize()) {
            val current = frame ?: return@Canvas
            // read serial so a new frame in the same bitmap still triggers a redraw
            current.serial
            drawn.incrementAndGet()
            drawImage(
                current.bitmap.asComposeImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(current.bitmap.width, current.bitmap.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
        Text(stats, Modifier.align(Alignment.TopStart).padding(8.dp), color = Color.Yellow)
    }
}

/** Plays [path] for 10 s at a fixed target width and prints frame and render timings. */
private fun bench(path: String, width: Int) {
    val player = MpvPlayer()
    player.setTargetSize(width, width * 9 / 16)
    player.property("mute", "yes")
    player.load(path)
    player.setPaused(false)
    Thread.sleep(2000)
    val startSerial = player.frame.value?.serial ?: 0
    Thread.sleep(10_000)
    val frames = (player.frame.value?.serial ?: 0) - startSerial
    System.getProperty("immersion.dumpFrame")?.let { out ->
        val bitmap = player.frame.value!!.bitmap
        val png = org.jetbrains.skia.Image.makeFromBitmap(bitmap).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!
        java.io.File(out).writeBytes(png.bytes)
    }
    println(
        "target ${width}x${width * 9 / 16}: %.1f fps"
            .format(frames / 10.0)
    )
    player.destroy()
    exitProcess(0)
}
