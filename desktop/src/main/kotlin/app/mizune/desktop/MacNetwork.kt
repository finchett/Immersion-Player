package app.mizune.desktop

import com.sun.jna.Callback
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * Whether the Mac's connection is metered: Low Data Mode is on for it, or macOS counts it as
 * expensive, as it does a phone's hotspot. Network.framework's path monitor says so whenever the
 * connection changes. Its update handler has to be a block, which JNA can't make, so one is laid
 * out by hand: a global block, which libdispatch keeps without copying.
 *
 * Never metered off macOS, or if the monitor can't be started.
 */
object MacNetwork {
    @Volatile var metered = false
        private set

    // the block, its callback and the monitor stay reachable for as long as the app runs
    private var handler: PathHandler? = null
    private var block: BlockLiteral? = null
    private var monitor: Pointer? = null

    /** Starts watching; [onChange] hears the new answer, on a background thread, whenever it changes. */
    fun start(onChange: (metered: Boolean) -> Unit) {
        if (!isMac || monitor != null) return
        runCatching {
            val network = NativeLibrary.getInstance("/System/Library/Frameworks/Network.framework/Network")
            val system = NativeLibrary.getInstance("System")
            handler = PathHandler { _, path ->
                if (path == null) return@PathHandler
                // C bools: only the low byte is the answer
                val expensive = network.getFunction("nw_path_is_expensive").invokeInt(arrayOf(path)) and 0xFF != 0
                val constrained = network.getFunction("nw_path_is_constrained").invokeInt(arrayOf(path)) and 0xFF != 0
                val now = expensive || constrained
                if (now != metered) {
                    metered = now
                    onChange(now)
                }
            }
            block = BlockLiteral().apply {
                isa = system.getGlobalVariableAddress("_NSConcreteGlobalBlock")
                flags = BLOCK_IS_GLOBAL
                invoke = handler
                descriptor = BlockDescriptor().also { it.size = size().toLong() }
                write()
            }
            val queue = system.getFunction("dispatch_queue_create").invokePointer(arrayOf("app.mizune.network", null))
            monitor = network.getFunction("nw_path_monitor_create").invokePointer(arrayOf()).also {
                network.getFunction("nw_path_monitor_set_queue").invoke(Void.TYPE, arrayOf(it, queue))
                network.getFunction("nw_path_monitor_set_update_handler").invoke(Void.TYPE, arrayOf(it, block))
                network.getFunction("nw_path_monitor_start").invoke(Void.TYPE, arrayOf(it))
            }
        }
    }

    private const val BLOCK_IS_GLOBAL = 1 shl 28

    private fun interface PathHandler : Callback {
        fun invoke(block: Pointer?, path: Pointer?)
    }

    @Structure.FieldOrder("reserved", "size")
    class BlockDescriptor : Structure(), Structure.ByReference {
        @JvmField var reserved = 0L
        @JvmField var size = 0L
    }

    /** The layout of a block: what the compiler would emit for `^(nw_path_t path) { ... }`. */
    @Structure.FieldOrder("isa", "flags", "reserved", "invoke", "descriptor")
    class BlockLiteral : Structure() {
        @JvmField var isa: Pointer? = null
        @JvmField var flags = 0
        @JvmField var reserved = 0
        @JvmField var invoke: Callback? = null
        @JvmField var descriptor: BlockDescriptor? = null
    }
}
