package io.github.immersionplayer.desktop

import io.github.immersionplayer.desktop.mpv.MpvPlayer
import io.github.immersionplayer.subs.SubtitleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** Playback state and line-by-line navigation for one video. Mirrors the Android PlayerSession. */
class PlayerSession(
    private val settings: Settings,
    val player: MpvPlayer,
    val video: File,
) {
    private val path = video.absolutePath
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val position: StateFlow<Double> get() = player.position
    val duration: StateFlow<Double> get() = player.duration
    val paused: StateFlow<Boolean> get() = player.paused

    private val _primary = MutableStateFlow<SubtitleTrack?>(null)
    val primary: StateFlow<SubtitleTrack?> = _primary.asStateFlow()

    private val _secondary = MutableStateFlow<SubtitleTrack?>(null)
    val secondary: StateFlow<SubtitleTrack?> = _secondary.asStateFlow()

    private val _tracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val tracks: StateFlow<List<SubtitleTrack>> = _tracks.asStateFlow()

    /** Subtitle shift in seconds; positive means subtitles appear later. */
    private val _offset = MutableStateFlow(settings.subtitleOffset(path))
    val offset: StateFlow<Double> = _offset.asStateFlow()

    /** Cue on screen now, or the last one that started (-1 before the first line). */
    private val _lineIndex = MutableStateFlow(-1)
    val lineIndex: StateFlow<Int> = _lineIndex.asStateFlow()

    /** Whether [lineIndex] is actually being spoken/shown right now. */
    private val _lineActive = MutableStateFlow(false)
    val lineActive: StateFlow<Boolean> = _lineActive.asStateFlow()

    private var autoPausedLine = -1
    private var lastCopiedLine = -1

    init {
        player.property("start", settings.position(path).toString())
        player.load(path)
        player.setPaused(false)
        scope.launch { player.position.collect(::updateLine) }
        // save on every pause, so a crash or force quit loses little
        scope.launch { player.paused.drop(1).collect { if (it) savePosition() } }
    }

    fun close() {
        savePosition()
        scope.cancel()
        player.command("stop")
    }

    /** Loads a video's tracks, restoring the user's earlier choices for this video if any. */
    fun setTracks(all: List<SubtitleTrack>, defaultPrimary: SubtitleTrack?, defaultSecondary: SubtitleTrack?) {
        _tracks.value = all
        val savedPrimary = settings.trackChoice(path, "primary")
        val savedSecondary = settings.trackChoice(path, "secondary")
        _primary.value = all.firstOrNull { it.name == savedPrimary } ?: defaultPrimary
        _secondary.value = when (savedSecondary) {
            null -> defaultSecondary
            "" -> null
            else -> all.firstOrNull { it.name == savedSecondary } ?: defaultSecondary
        }
        updateLine(player.position.value)
    }

    fun selectPrimary(track: SubtitleTrack) {
        _primary.value = track
        settings.setTrackChoice(path, "primary", track.name)
        updateLine(player.position.value)
    }

    fun selectSecondary(track: SubtitleTrack?) {
        _secondary.value = track
        settings.setTrackChoice(path, "secondary", track?.name ?: "")
    }

    fun shiftOffset(delta: Double) {
        val rounded = Math.round((_offset.value + delta) * 10) / 10.0
        _offset.value = rounded
        settings.setSubtitleOffset(path, rounded)
        updateLine(player.position.value)
    }

    fun setAutoPause(enabled: Boolean) {
        settings.updateAutoPause(enabled)
        autoPausedLine = _lineIndex.value.takeIf { enabled && !_lineActive.value } ?: -1
    }

    fun togglePause() = player.togglePause()
    fun pause() = player.setPaused(true)
    fun play() = player.setPaused(false)

    fun seekTo(seconds: Double) {
        player.seek(seconds.coerceAtLeast(0.0))
        updateLine(seconds)
    }

    fun seekBy(seconds: Double) = seekTo(player.position.value + seconds)

    /** Jump to a line and play it (it will stop at its end when auto-pause is on). */
    fun playLine(index: Int) {
        val cue = _primary.value?.cues?.getOrNull(index) ?: return
        autoPausedLine = -1
        seekTo(cue.start + _offset.value)
        play()
    }

    fun replayLine() {
        val index = _lineIndex.value
        playLine(if (index >= 0) index else 0)
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
        settings.savePosition(path, player.position.value, player.duration.value)
    }

    private fun updateLine(videoTime: Double) {
        val track = _primary.value ?: return
        // subtitle clock: shifted subtitles show later (positive offset) or earlier
        val time = videoTime - _offset.value
        val index = track.indexAt(time)
        val active = index >= 0 && time < track.cues[index].end
        _lineIndex.value = index
        _lineActive.value = active

        if (active && index != lastCopiedLine && settings.copyLines) {
            lastCopiedLine = index
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(track.cues[index].text), null)
            }
        }

        if (settings.autoPause && index >= 0 && index != autoPausedLine && !player.paused.value) {
            val cue = track.cues[index]
            if (time >= cue.end - END_MARGIN && time < cue.end + 1.0) {
                autoPausedLine = index
                pause()
            }
        }
    }

    companion object {
        /** Pause slightly early so the next line's first syllable isn't heard. */
        private const val END_MARGIN = 0.05
    }
}
