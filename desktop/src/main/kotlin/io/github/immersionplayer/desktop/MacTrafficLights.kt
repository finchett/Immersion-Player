package io.github.immersionplayer.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot

/**
 * Moves the macOS window buttons (traffic lights) and fades them, which AWT has no API for.
 * Talks to AppKit through the Objective-C runtime, on the main thread via libdispatch, the way
 * Electron's trafficLightPosition does. AppKit lays the title bar out again on resize, so
 * callers re-apply after size changes.
 */
object MacTrafficLights {
    /** The window's corner radius in points, read from AppKit once the window exists. */
    var cornerRadius by mutableStateOf(16.0)
        private set

    /** Radius of one light, and the distance between neighbouring lights' centres. */
    const val LIGHT_RADIUS = 7.0
    const val LIGHT_SPACING = 20.0
    private val DEBUG = System.getProperty("immersion.debugLights") != null

    private val objc by lazy { NativeLibrary.getInstance("objc") }
    private val msgSend by lazy { objc.getFunction("objc_msgSend") }
    // Intel returns large structs through a hidden pointer, via a separate entry point
    private val msgSendStret by lazy {
        if (Platform.isARM()) msgSend else objc.getFunction("objc_msgSend_stret")
    }
    private val dispatch by lazy { Native.load("System", Dispatch::class.java) }
    private val mainQueue by lazy { NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q") }

    @Suppress("FunctionName")
    private interface Dispatch : Library {
        fun dispatch_async_f(queue: Pointer, context: Pointer?, work: Work)
    }

    private fun interface Work : Callback {
        fun invoke(context: Pointer?)
    }

    @Structure.FieldOrder("x", "y", "width", "height")
    class Rect : Structure(), Structure.ByValue {
        @JvmField var x = 0.0
        @JvmField var y = 0.0
        @JvmField var width = 0.0
        @JvmField var height = 0.0
    }

    @Structure.FieldOrder("x", "y")
    class Point(@JvmField var x: Double = 0.0, @JvmField var y: Double = 0.0) : Structure(), Structure.ByValue

    // AppKit's own layout, remembered the first time, so repeated calls don't keep adding the inset
    private var baseTitlebarHeight = 0.0
    // callbacks must stay reachable until the main thread has run them
    private val pending = java.util.Collections.synchronizedSet(HashSet<Work>())

    /**
     * Centres the first light on the centre of the window's corner curve, so the lights, the pill
     * behind them and the video card are all concentric with the window corner, and shows or fades
     * them. No-op off macOS.
     */
    fun apply(visible: Boolean) {
        if (!isMac) return
        onMain {
            val result = runCatching {
                val window = appWindow() ?: return@onMain
                val responds = sendLong(window, "respondsToSelector:", sel("_cornerRadius")) and 0xFF
                if (responds != 0L) {
                    val radius = msgSend.invokeDouble(arrayOf(window, sel("_cornerRadius")))
                    if (radius > 0 && radius != cornerRadius) Snapshot.withMutableSnapshot { cornerRadius = radius }
                }
                val center = cornerRadius

                val buttons = (0..2).map { send(window, "standardWindowButton:", it.toLong()) }
                if (buttons.any { it == null }) return@onMain
                // buttons live in the titlebar view, inside the titlebar container (y grows upwards)
                val container = send(send(buttons[0], "superview"), "superview") ?: return@onMain
                val contentHeight = frame(send(window, "contentView")).height
                val bar = frame(container)
                if (baseTitlebarHeight == 0.0) baseTitlebarHeight = bar.height
                val buttonHeight = frame(buttons[0]).height
                // tall enough that the buttons aren't clipped at their new height
                val height = maxOf(baseTitlebarHeight, center + buttonHeight / 2)
                sendVoid(container, "setFrame:", rect(bar.x, contentHeight - height, bar.width, height))

                buttons.forEachIndexed { i, button ->
                    val f = frame(button)
                    val x = center + i * LIGHT_SPACING - f.width / 2
                    val y = height - center - f.height / 2
                    sendVoid(button, "setFrameOrigin:", Point(x, y))
                    if (DEBUG) println("lights: button $i at $x,$y (${f.width}x${f.height}), bar $height, radius $cornerRadius")
                    sendVoid(send(button, "animator"), "setAlphaValue:", if (visible) 1.0 else 0.0)
                }
            }
            if (DEBUG) result.exceptionOrNull()?.printStackTrace()
        }
    }

    /** The app's (single) AWT window: AWTWindow_Normal, as opposed to file dialogs and menus. */
    private fun appWindow(): Pointer? {
        val app = send(cls("NSApplication"), "sharedApplication")
        val windows = send(app, "windows") ?: return null
        val count = sendLong(windows, "count")
        for (i in 0 until count) {
            val w = send(windows, "objectAtIndex:", i) ?: continue
            val className = objc.getFunction("object_getClassName").invokeString(arrayOf(w), false)
            if (DEBUG) println("lights: window class=$className visible=${sendLong(w, "isVisible") and 0xFF}")
            if (className == "AWTWindow_Normal" && (sendLong(w, "isVisible") and 0xFF) != 0L) return w
        }
        return null
    }

    private fun onMain(block: () -> Unit) {
        lateinit var work: Work
        work = Work {
            try { block() } finally { pending.remove(work) }
        }
        pending.add(work)
        dispatch.dispatch_async_f(mainQueue, null, work)
    }

    private fun cls(name: String): Pointer = objc.getFunction("objc_getClass").invokePointer(arrayOf(name))
    private fun sel(name: String): Pointer = objc.getFunction("sel_registerName").invokePointer(arrayOf(name))

    private fun send(receiver: Pointer?, selector: String, vararg args: Any): Pointer? {
        receiver ?: return null
        return msgSend.invokePointer(arrayOf(receiver, sel(selector), *args))
    }

    private fun sendLong(receiver: Pointer, selector: String, vararg args: Any): Long =
        msgSend.invokeLong(arrayOf(receiver, sel(selector), *args))

    private fun sendVoid(receiver: Pointer?, selector: String, vararg args: Any) {
        receiver ?: return
        msgSend.invoke(Void.TYPE, arrayOf(receiver, sel(selector), *args))
    }

    private fun frame(view: Pointer?): Rect =
        msgSendStret.invoke(Rect::class.java, arrayOf(view, sel("frame"))) as Rect

    private fun rect(x: Double, y: Double, width: Double, height: Double) =
        Rect().apply { this.x = x; this.y = y; this.width = width; this.height = height }
}
