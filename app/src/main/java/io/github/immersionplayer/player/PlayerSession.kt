package io.github.immersionplayer.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import io.github.immersionplayer.Prefs
import io.github.immersionplayer.media.AudioTrack
import io.github.immersionplayer.subs.SubtitleTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import `is`.xyz.mpv.MPVLib

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

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    /** The video's audio tracks, read when mpv has loaded it. */
    val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()

    private val _audioTrack = MutableStateFlow<Int?>(null)
    /** The [AudioTrack.id] playing, or null for none. */
    val audioTrack: StateFlow<Int?> = _audioTrack.asStateFlow()

    private val _linesRead = MutableStateFlow(0)
    /** Goes up as lines of picture tracks are read, so what shows their text can redraw. */
    val linesRead: StateFlow<Int> = _linesRead.asStateFlow()

    private val _tracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val tracks: StateFlow<List<SubtitleTrack>> = _tracks.asStateFlow()

    /** Subtitle shift in seconds; positive means subtitles appear later. */
    private val _offset = MutableStateFlow(prefs.subtitleOffset(videoUri))
    val offset: StateFlow<Double> = _offset.asStateFlow()

    /** Cue on screen now, or the last one that started (-1 before the first line). */
    private val _lineIndex = MutableStateFlow(-1)
    val lineIndex: StateFlow<Int> = _lineIndex.asStateFlow()

    /** Whether [lineIndex] is actually being spoken/shown right now. */
    private val _lineActive = MutableStateFlow(false)
    val lineActive: StateFlow<Boolean> = _lineActive.asStateFlow()

    private val _autoPause = MutableStateFlow(prefs.autoPause)
    val autoPause: StateFlow<Boolean> = _autoPause.asStateFlow()

    private val _ended = MutableStateFlow(false)
    /** True once playback reaches the end of the file (cleared by seeking). */
    val ended: StateFlow<Boolean> = _ended.asStateFlow()

    private val _fill = MutableStateFlow(prefs.videoFill)
    val fill: StateFlow<Boolean> = _fill.asStateFlow()

    // pause when another app (or a call) takes the audio; we don't auto-resume
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
        }, mainHandler)
        .build()
    private var hasFocus = false

    private var autoPausedLine = -1
    private var lastCopiedLine = -1

    fun attach(view: MpvView) {
        this.view = view
        view.listener = this
    }

    fun detach() {
        savePosition()
        abandonFocus()
        view?.listener = null
        view = null
    }

    /** Stops reading picture tracks. For when the player goes, not just its view (which comes back on rotation). */
    fun release() {
        _tracks.value.forEach { it.pictures?.close() }
    }

    /** Loads a video's tracks, restoring the user's earlier choices for this video if any. */
    fun setTracks(all: List<SubtitleTrack>, defaultPrimary: SubtitleTrack?, defaultSecondary: SubtitleTrack?) {
        _tracks.value = all
        for (track in all) {
            val pictures = track.pictures ?: continue
            pictures.onRead = { _linesRead.update { it + 1 } }
            pictures.start()
        }
        val savedPrimary = prefs.trackChoice(videoUri, "primary")
        val savedSecondary = prefs.trackChoice(videoUri, "secondary")
        _primary.value = all.firstOrNull { it.name == savedPrimary } ?: defaultPrimary
        _secondary.value = when (savedSecondary) {
            null -> defaultSecondary
            "" -> null
            else -> all.firstOrNull { it.name == savedSecondary } ?: defaultSecondary
        }
        updateLine(_position.value)
    }

    fun selectPrimary(track: SubtitleTrack) {
        _primary.value = track
        prefs.setTrackChoice(videoUri, "primary", track.name)
        updateLine(_position.value)
    }

    fun selectSecondary(track: SubtitleTrack?) {
        _secondary.value = track
        prefs.setTrackChoice(videoUri, "secondary", track?.name ?: "")
    }

    fun selectAudio(track: AudioTrack) {
        if (view == null) return
        MPVLib.setPropertyString("aid", track.id.toString())
        _audioTrack.value = track.id
        prefs.setTrackChoice(videoUri, "audio", track.id.toString())
    }

    fun setOffset(seconds: Double) {
        val rounded = Math.round(seconds * 10) / 10.0
        _offset.value = rounded
        prefs.setSubtitleOffset(videoUri, rounded)
        updateLine(_position.value)
    }

    fun setAutoPause(enabled: Boolean) {
        _autoPause.value = enabled
        prefs.autoPause = enabled
        autoPausedLine = _lineIndex.value.takeIf { enabled && _lineActive.value.not() } ?: -1
    }

    fun setFill(fill: Boolean) {
        _fill.value = fill
        prefs.videoFill = fill
        view?.setFill(fill)
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
        _ended.value = false
        view?.seek(seconds)
        _position.value = seconds
        updateLine(seconds)
    }

    /** Jump to a line and play it (it will stop at its end when auto-pause is on). */
    fun playLine(index: Int) {
        val cues = _primary.value?.cues ?: return
        val cue = cues.getOrNull(index) ?: return
        autoPausedLine = -1
        seekTo((cue.start + _offset.value).coerceAtLeast(0.0))
        play()
    }

    fun replayLine() {
        val index = _lineIndex.value
        if (index >= 0) playLine(index) else playLine(0)
    }

    fun previousLine() {
        val index = _lineIndex.value
        val cue = _primary.value?.cues?.getOrNull(index)
        val target = when {
            // stopped partway through a line, or at its end: back to its start, to hear it again
            _paused.value && cue != null && _position.value - _offset.value > cue.start + REPLAY_MARGIN -> index
            // between lines, "previous" means the line that just finished
            index >= 0 && !_lineActive.value -> index
            else -> index - 1
        }
        playLine(target.coerceAtLeast(0))
    }

    /**
     * Forward one line, always jumping to it. Unlike the desktop keys, a swipe doesn't carry on
     * from a pause: a tap already means play, so "next" can just mean next.
     */
    fun nextLine() {
        val cues = _primary.value?.cues ?: return
        playLine((_lineIndex.value + 1).coerceAtMost(cues.lastIndex))
    }

    fun savePosition() {
        prefs.savePosition(videoUri, _position.value, _duration.value)
    }

    private fun updateLine(videoTime: Double) {
        val track = _primary.value ?: return
        // subtitle clock: shifted subtitles show later (positive offset) or earlier
        val time = videoTime - _offset.value
        val index = track.indexAt(time)
        val active = index >= 0 && time < track.cues[index].end
        _lineIndex.value = index
        _lineActive.value = active
        // picture tracks: read what's on screen (and just after it) before anything else
        track.pictures?.want(index.coerceAtLeast(0))
        _secondary.value?.pictures?.wantAt(time)

        // a picture line's text may not be read yet; it's copied once it is
        val text = track.cues.getOrNull(index)?.text.orEmpty()
        if (active && index != lastCopiedLine && prefs.copyLines && text.isNotEmpty()) {
            lastCopiedLine = index
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

    // MpvView.Listener (called on mpv's event thread, except onFileLoaded)

    override fun onFileLoaded() {
        val tracks = AudioTrack.fromMpv { runCatching { MPVLib.getPropertyString(it) }.getOrNull() }
        _audioTracks.value = tracks
        // a track picked for this video beats the language setting
        val saved = prefs.trackChoice(videoUri, "audio")?.toIntOrNull()
        if (saved != null && tracks.any { it.id == saved }) MPVLib.setPropertyString("aid", saved.toString())
        _audioTrack.value = runCatching { MPVLib.getPropertyString("aid")?.toIntOrNull() }.getOrNull()
    }

    override fun onTimePos(seconds: Double) {
        _position.value = seconds
        updateLine(seconds)
    }

    override fun onEndReached() {
        _ended.value = true
        mainHandler.post { savePosition() }
    }

    override fun onDuration(seconds: Double) {
        _duration.value = seconds
    }

    override fun onPause(paused: Boolean) {
        _paused.value = paused
        mainHandler.post {
            if (paused) savePosition() else requestFocus()
        }
    }

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager?.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    companion object {
        /** Pause slightly early so the next line's first syllable isn't heard. */
        private const val END_MARGIN = 0.05
        /** Paused less than this into a line counts as at its start, where "previous" goes further back. */
        private const val REPLAY_MARGIN = 0.5
    }
}
