package io.github.immersionplayer.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.immersionplayer.Prefs
import io.github.immersionplayer.subs.SubtitleTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Playback state and line-by-line navigation for one video. */
class PlayerSession(
    private val context: Context,
    private val prefs: Prefs,
    val videoUri: String,
    val videoName: String,
) : MpvView.Listener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: MpvView? = null

    private val _position = MutableStateFlow(prefs.position(videoUri))
    val position: StateFlow<Double> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _paused = MutableStateFlow(true)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _primary = MutableStateFlow<SubtitleTrack?>(null)
    val primary: StateFlow<SubtitleTrack?> = _primary.asStateFlow()

    private val _secondary = MutableStateFlow<SubtitleTrack?>(null)
    val secondary: StateFlow<SubtitleTrack?> = _secondary.asStateFlow()

    /** Cue on screen now, or the last one that started (-1 before the first line). */
    private val _lineIndex = MutableStateFlow(-1)
    val lineIndex: StateFlow<Int> = _lineIndex.asStateFlow()

    /** Whether [lineIndex] is actually being spoken/shown right now. */
    private val _lineActive = MutableStateFlow(false)
    val lineActive: StateFlow<Boolean> = _lineActive.asStateFlow()

    private val _autoPause = MutableStateFlow(prefs.autoPause)
    val autoPause: StateFlow<Boolean> = _autoPause.asStateFlow()

    private var autoPausedLine = -1
    private var lastCopiedLine = -1

    fun attach(view: MpvView) {
        this.view = view
        view.listener = this
    }

    fun detach() {
        savePosition()
        view?.listener = null
        view = null
    }

    fun setTracks(primary: SubtitleTrack?, secondary: SubtitleTrack?) {
        _primary.value = primary
        _secondary.value = secondary
        updateLine(_position.value)
    }

    fun setAutoPause(enabled: Boolean) {
        _autoPause.value = enabled
        prefs.autoPause = enabled
        autoPausedLine = _lineIndex.value.takeIf { enabled && _lineActive.value.not() } ?: -1
    }

    fun togglePause() {
        val v = view ?: return
        v.paused = !v.paused
    }

    fun pause() {
        view?.paused = true
    }

    fun play() {
        view?.paused = false
    }

    fun seekTo(seconds: Double) {
        view?.seek(seconds)
        _position.value = seconds
        updateLine(seconds)
    }

    /** Jump to a line and play it (it will stop at its end when auto-pause is on). */
    fun playLine(index: Int) {
        val cues = _primary.value?.cues ?: return
        val cue = cues.getOrNull(index) ?: return
        autoPausedLine = -1
        seekTo(cue.start)
        play()
    }

    fun replayLine() {
        val index = _lineIndex.value
        if (index >= 0) playLine(index) else playLine(0)
    }

    fun previousLine() {
        val index = _lineIndex.value
        // between lines, "previous" means the line that just finished
        val target = if (_lineActive.value || index < 0) index - 1 else index
        playLine(target.coerceAtLeast(0))
    }

    fun nextLine() {
        val cues = _primary.value?.cues ?: return
        playLine((_lineIndex.value + 1).coerceAtMost(cues.lastIndex))
    }

    fun savePosition() {
        prefs.savePosition(videoUri, _position.value, _duration.value)
    }

    private fun updateLine(time: Double) {
        val track = _primary.value ?: return
        val index = track.indexAt(time)
        val active = index >= 0 && time < track.cues[index].end
        _lineIndex.value = index
        _lineActive.value = active

        if (active && index != lastCopiedLine && prefs.copyLines) {
            lastCopiedLine = index
            val text = track.cues[index].text
            mainHandler.post { copyToClipboard(text) }
        }

        if (_autoPause.value && index >= 0 && index != autoPausedLine && !_paused.value) {
            val cue = track.cues[index]
            if (time >= cue.end - END_MARGIN && time < cue.end + 1.0) {
                autoPausedLine = index
                mainHandler.post { pause() }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("subtitle", text))
    }

    // MpvView.Listener (called on mpv's event thread)

    override fun onTimePos(seconds: Double) {
        _position.value = seconds
        updateLine(seconds)
    }

    override fun onDuration(seconds: Double) {
        _duration.value = seconds
    }

    override fun onPause(paused: Boolean) {
        _paused.value = paused
        if (paused) mainHandler.post { savePosition() }
    }

    companion object {
        /** Pause slightly early so the next line's first syllable isn't heard. */
        private const val END_MARGIN = 0.05
    }
}
