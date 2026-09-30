package io.github.immersionplayer.desktop

import io.github.immersionplayer.desktop.mpv.MpvPlayer
import io.github.immersionplayer.media.AudioTrack
import io.github.immersionplayer.subs.SubtitleFiles
import io.github.immersionplayer.subs.SubtitleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    /** Whether the video has played to its end. */
    val ended: StateFlow<Boolean> get() = player.ended

    private val _primary = MutableStateFlow<SubtitleTrack?>(null)
    val primary: StateFlow<SubtitleTrack?> = _primary.asStateFlow()

    private val _secondary = MutableStateFlow<SubtitleTrack?>(null)
    val secondary: StateFlow<SubtitleTrack?> = _secondary.asStateFlow()

    val audioTracks: StateFlow<List<AudioTrack>> get() = player.audioTracks
    val audioTrack: StateFlow<Int?> get() = player.audioTrack

    private val _linesRead = MutableStateFlow(0)
    /** Goes up as lines of picture tracks are read, so what shows their text can redraw. */
    val linesRead: StateFlow<Int> = _linesRead.asStateFlow()

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
    /** A line to stop at the end of once, whatever the stop-at-end setting says (-1 for none). */
    private var stopAfterLine = -1
    /**
     * Whether the stop-at-end setting is held off. The plain keys (h/l, and carrying on from a
     * pause) mean "don't stop", so playback runs on until a key that stops at the end (j/k/;)
     * or the setting itself asks for stops again.
     */
    private var keepPlaying = false
    private var lastCopiedLine = -1

    init {
        player.property("alang", SubtitleFiles.mpvLanguages(settings.targetLanguage))
        // the player is shared, so a track picked in the last video mustn't carry over
        player.property("aid", "auto")
        player.property("start", settings.position(path).toString())
        player.load(path)
        player.setPaused(false)
        scope.launch { player.position.collect(::updateLine) }
        // a track picked for this video beats the language setting, once mpv has listed the tracks
        scope.launch {
            val tracks = player.audioTracks.first { it.isNotEmpty() }
            val saved = settings.trackChoice(path, "audio")?.toIntOrNull()
            if (saved != null && tracks.any { it.id == saved } && saved != player.audioTrack.value) player.selectAudio(saved)
        }
        // save on every pause, so a crash or force quit loses little
        scope.launch { player.paused.drop(1).collect { if (it) savePosition() } }
    }

    fun close() {
        savePosition()
        _tracks.value.forEach { it.pictures?.close() }
        scope.cancel()
        player.command("stop")
    }

    /** Picks the line and peek tracks by the languages chosen in settings; a track picked for this video still wins. */
    fun setTracks(all: List<SubtitleTrack>) {
        _tracks.value = all
        for (track in all) {
            val pictures = track.pictures ?: continue
            pictures.onRead = { _linesRead.update { it + 1 } }
            pictures.start()
        }
        val savedPrimary = settings.trackChoice(path, "primary")
        val savedSecondary = settings.trackChoice(path, "secondary")
        val primary = all.firstOrNull { it.name == savedPrimary } ?: SubtitleFiles.pickPrimary(all, settings.targetLanguage)
        val defaultSecondary = { SubtitleFiles.pickSecondary(all, primary, settings.peekLanguage) }
        _primary.value = primary
        _secondary.value = when (savedSecondary) {
            null -> defaultSecondary()
            "" -> null
            else -> all.firstOrNull { it.name == savedSecondary } ?: defaultSecondary()
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

    fun selectAudio(track: AudioTrack) {
        player.selectAudio(track.id)
        settings.setTrackChoice(path, "audio", track.id.toString())
    }

    fun shiftOffset(delta: Double) {
        val rounded = Math.round((_offset.value + delta) * 10) / 10.0
        _offset.value = rounded
        settings.setSubtitleOffset(path, rounded)
        updateLine(player.position.value)
    }

    fun setAutoPause(enabled: Boolean) {
        settings.updateAutoPause(enabled)
        keepPlaying = false
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

    /**
     * Jump to a line and play it. With [stopAtEnd] it pauses when that line finishes; otherwise
     * it plays on past the end of the line, holding off the stop-at-end setting ([keepPlaying]).
     */
    fun playLine(index: Int, stopAtEnd: Boolean = false) {
        val cue = _primary.value?.cues?.getOrNull(index) ?: return
        autoPausedLine = -1
        stopAfterLine = if (stopAtEnd) index else -1
        keepPlaying = !stopAtEnd
        seekTo(cue.start + _offset.value)
        play()
    }

    fun replayLine(stopAtEnd: Boolean = false) {
        val index = _lineIndex.value
        playLine(if (index >= 0) index else 0, stopAtEnd)
    }

    fun previousLine(stopAtEnd: Boolean = false) {
        val index = _lineIndex.value
        val cue = _primary.value?.cues?.getOrNull(index)
        val target = when {
            // stopped partway through a line, or at its end: back to its start, to hear it again
            player.paused.value && cue != null && player.position.value - _offset.value > cue.start + REPLAY_MARGIN -> index
            // between lines, "previous" means the line that just finished
            index >= 0 && !_lineActive.value -> index
            else -> index - 1
        }
        playLine(target.coerceAtLeast(0), stopAtEnd)
    }

    /**
     * Forward one line. While paused the first press only carries on playing, so the rest of the
     * line and the pause after it aren't skipped; pressing again, now that it plays, jumps to the
     * line ahead.
     */
    fun nextLine(stopAtEnd: Boolean = false) {
        val cues = _primary.value?.cues ?: return
        if (player.paused.value) return resume(stopAtEnd)
        playLine((_lineIndex.value + 1).coerceAtMost(cues.lastIndex), stopAtEnd)
    }

    /** Carries on from where playback stopped, without seeking. */
    private fun resume(stopAtEnd: Boolean) {
        // the line ahead is the current one while it's still on screen, otherwise the next one
        val ahead = if (_lineActive.value) _lineIndex.value else _lineIndex.value + 1
        stopAfterLine = if (stopAtEnd) ahead else -1
        keepPlaying = !stopAtEnd
        // autoPausedLine is left alone: it's what stops us pausing again on the line we just left
        play()
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
        // picture tracks: read what's on screen (and just after it) before anything else
        track.pictures?.want(index.coerceAtLeast(0))
        _secondary.value?.pictures?.wantAt(time)

        // a picture line's text may not be read yet; it's copied once it is
        val text = track.cues.getOrNull(index)?.text.orEmpty()
        if (active && index != lastCopiedLine && settings.copyLines && text.isNotEmpty()) {
            lastCopiedLine = index
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            }
        }

        val stopHere = (settings.autoPause && !keepPlaying) || index == stopAfterLine
        if (stopHere && index >= 0 && index != autoPausedLine && !player.paused.value) {
            val cue = track.cues[index]
            if (time >= cue.end - END_MARGIN && time < cue.end + 1.0) {
                autoPausedLine = index
                stopAfterLine = -1
                pause()
            }
        }
    }

    companion object {
        /** Pause slightly early so the next line's first syllable isn't heard. */
        private const val END_MARGIN = 0.05
        /** Paused less than this into a line counts as at its start, where "previous" goes further back. */
        private const val REPLAY_MARGIN = 0.5
    }
}
