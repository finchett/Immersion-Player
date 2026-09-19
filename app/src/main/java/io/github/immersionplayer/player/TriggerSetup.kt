package io.github.immersionplayer.player

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Learning mode for shoulder-trigger keys. While [active], the activity forwards every key
 * press here instead of handling it, and reports touches so we can tell when a phone's game
 * mode turns trigger presses into screen taps rather than keys.
 */
object TriggerSetup {
    data class Seen(val description: String, val keyCode: Int?)

    var active = false
        private set

    private val _lastKey = MutableStateFlow<Seen?>(null)
    val lastKey: StateFlow<Seen?> = _lastKey.asStateFlow()

    private val _lastTouch = MutableStateFlow<String?>(null)
    val lastTouch: StateFlow<String?> = _lastTouch.asStateFlow()

    fun start() {
        active = true
        _lastKey.value = null
        _lastTouch.value = null
    }

    fun stop() {
        active = false
    }

    fun onKey(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return
        val device = event.device?.name ?: "unknown device"
        _lastKey.value = Seen("${KeyEvent.keyCodeToString(event.keyCode)} from $device", event.keyCode)
    }

    fun onTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN && event.actionMasked != MotionEvent.ACTION_POINTER_DOWN) return
        val index = event.actionIndex
        val device = event.device?.name ?: if (event.deviceId < 0) "injected" else "device ${event.deviceId}"
        val source = if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) "touch" else "pointer"
        _lastTouch.value = "$source at (${event.getX(index).toInt()}, ${event.getY(index).toInt()}) from $device"
    }
}
