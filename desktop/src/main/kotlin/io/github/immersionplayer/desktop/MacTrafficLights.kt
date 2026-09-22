package io.github.immersionplayer.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * Moves the macOS window buttons (traffic lights) and fades them, which AWT has no API for.
 * Talks to AppKit through the Objective-C runtime, on the main thread via libdispatch, the way
 * Electron's trafficLightPosition does. AppKit lays the title bar out again on resize, so
 * callers re-apply after size changes.
 */
object MacTrafficLights {
    /** How far in from AppKit's default position, in points. */
    const val INSET = 6.0
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
    private val baseButtonX = DoubleArray(3)
    // callbacks must stay reachable until the main thread has run them
    private val pending = java.util.Collections.synchronizedSet(HashSet<Work>())

    /** Positions the buttons [INSET] in from the corner and shows or fades them. No-op off macOS. */
    fun apply(visible: Boolean) {
        if (!isMac) return
        onMain {
            val result = runCatching {
                val window = appWindow() ?: return@onMain
                val buttons = (0..2).map { send(window, "standardWindowButton:", it.toLong()) }
                if (buttons.any { it == null }) return@onMain
                // buttons live in the titlebar view, inside the titlebar container
                val container = send(send(buttons[0], "superview"), "superview") ?: return@onMain

                val contentHeight = frame(send(window, "contentView")).height
                val bar = frame(container)
                if (baseTitlebarHeight == 0.0) baseTitlebarHeight = bar.height
                // growing the container downwards carries the buttons down with it
                val height = baseTitlebarHeight + INSET
                sendVoid(container, "setFrame:", rect(bar.x, contentHeight - height, bar.width, height))

                buttons.forEachIndexed { i, button ->
                    val f = frame(button)
                    if (baseButtonX[i] == 0.0) baseButtonX[i] = f.x
                    sendVoid(button, "setFrameOrigin:", Point(baseButtonX[i] + INSET, f.y))
                    if (DEBUG) println("lights: button $i was ${f.x},${f.y} ${f.width}x${f.height} now ${frame(button).x},${frame(button).y}; bar ${bar.height} -> ${frame(container).height}")
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

    private fun sendLong(receiver: Pointer, selector: String): Long =
        msgSend.invokeLong(arrayOf(receiver, sel(selector)))

    private fun sendVoid(receiver: Pointer?, selector: String, vararg args: Any) {
        receiver ?: return
        msgSend.invoke(Void.TYPE, arrayOf(receiver, sel(selector), *args))
    }

    private fun frame(view: Pointer?): Rect =
        msgSendStret.invoke(Rect::class.java, arrayOf(view, sel("frame"))) as Rect

    private fun rect(x: Double, y: Double, width: Double, height: Double) =
        Rect().apply { this.x = x; this.y = y; this.width = width; this.height = height }
}
