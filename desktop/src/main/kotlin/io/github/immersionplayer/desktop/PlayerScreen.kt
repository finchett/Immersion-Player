package io.github.immersionplayer.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.immersionplayer.desktop.mpv.MpvPlayer
import io.github.immersionplayer.dictionary.DictionaryLookup
import io.github.immersionplayer.dictionary.LookupResult
import io.github.immersionplayer.library.formatTime
import io.github.immersionplayer.subs.SubtitleFiles
import io.github.immersionplayer.subs.SubtitleTrack
import io.github.immersionplayer.subs.translationFor
import io.github.immersionplayer.ui.ActiveLookup
import io.github.immersionplayer.ui.DictionaryPanel
import io.github.immersionplayer.ui.TappableText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor

/** Keyboard actions the window forwards to the player. */
class PlayerKeys {
    var peeking by mutableStateOf(false)
    var onToggleFullscreen: () -> Unit = {}
}

@Composable
fun PlayerScreen(app: DesktopApp, session: PlayerSession, keys: PlayerKeys, onBack: () -> Unit) {
    val settings = app.settings
    val scope = rememberCoroutineScope()

    var subtitleStatus by remember { mutableStateOf<String?>("Reading subtitles…") }
    LaunchedEffect(session) {
        val tracks = withContext(Dispatchers.IO) { runCatching { app.loadSubtitles(session.video) } }
        tracks.onSuccess { list ->
            val primary = SubtitleFiles.pickPrimary(list)
            session.setTracks(list, primary, SubtitleFiles.pickSecondary(list, primary))
            subtitleStatus = if (primary == null) "No text subtitles found for this video." else null
        }.onFailure {
            app.logError("subtitles ${session.video}", it)
            subtitleStatus = "Couldn't read subtitles: ${it.message}"
        }
    }

    val primary by session.primary.collectAsState()
    val setupStatus by app.setupStatus.collectAsState()
    var hasDictionaries by remember { mutableStateOf(true) }
    LaunchedEffect(setupStatus) {
        hasDictionaries = withContext(Dispatchers.IO) { app.database.dictionaries().any { it.enabled } }
    }

    var lookup by remember { mutableStateOf<ActiveLookup?>(null) }
    // each lookup gets a generation so a slow automatic lookup can't overwrite a newer one
    var generation by remember { mutableIntStateOf(0) }

    fun startLookup(line: Int, text: String, start: Int, maxLength: Int = Int.MAX_VALUE) {
        if (settings.pauseOnLookup) session.pause()
        val mine = ++generation
        lookup = ActiveLookup(line, text, start)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { app.lookup.lookup(text, start, maxLength) }
                    .onFailure { app.logError("lookup '$text' @$start", it) }
                    .getOrNull()
            }
            if (mine == generation) lookup = ActiveLookup(line, text, start, result)
        }
    }

    // every line has a word defined: the first kanji (or next best) that has an entry
    val lineIndex by session.lineIndex.collectAsState()
    LaunchedEffect(lineIndex, primary, hasDictionaries) {
        val cue = primary?.cues?.getOrNull(lineIndex) ?: return@LaunchedEffect
        if (lookup?.lineIndex == lineIndex && lookup?.text == cue.text) return@LaunchedEffect
        val mine = ++generation
        val text = cue.text
        val positions = DictionaryLookup.defaultLookupPositions(text)
        lookup = ActiveLookup(lineIndex, text, positions.firstOrNull() ?: 0)
        val found = withContext(Dispatchers.IO) {
            var fallback: ActiveLookup? = null
            for (position in positions.take(16)) {
                val result = runCatching { app.lookup.lookup(text, position) }
                    .onFailure { app.logError("auto lookup '$text' @$position", it) }
                    .getOrNull() ?: continue
                if (result.entries.isNotEmpty()) return@withContext ActiveLookup(lineIndex, text, position, result)
                if (fallback == null) fallback = ActiveLookup(lineIndex, text, position, result)
            }
            fallback
        }
        // never leave the panel waiting: lines like ♪～ have nothing to look up
        if (mine == generation) lookup = found ?: ActiveLookup(lineIndex, text, 0, LookupResult(text, 0, 0, emptyList()))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val totalWidth = maxWidth
        var panelFraction by remember { mutableFloatStateOf(settings.panelFraction.coerceIn(0.2f, 0.6f)) }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                VideoSurface(
                    session.player,
                    Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                        .pointerInput(session) {
                            detectTapGestures(
                                onTap = { session.togglePause() },
                                onDoubleTap = { keys.onToggleFullscreen() },
                            )
                        },
                )
                SeekBar(session, onBack)
            }
            // drag to resize the panel
            val density = LocalDensity.current
            Box(
                Modifier.width(6.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { settings.updatePanelFraction(panelFraction) },
                        ) { change, amount ->
                            change.consume()
                            val widthPx = with(density) { totalWidth.toPx() }
                            panelFraction = (panelFraction - amount / widthPx).coerceIn(0.2f, 0.6f)
                        }
                    },
            )
            StudyPanel(
                app = app,
                session = session,
                keys = keys,
                status = subtitleStatus,
                lookup = lookup,
                hasDictionaries = hasDictionaries,
                setupStatus = setupStatus,
                onLookup = { line, text, start -> startLookup(line, text, start) },
                onSelect = { line, text, start, end -> startLookup(line, text, start, end - start) },
                modifier = Modifier.width(totalWidth * panelFraction).fillMaxHeight(),
            )
        }
    }
}

@Composable
fun VideoSurface(player: MpvPlayer, modifier: Modifier = Modifier) {
    val frame by player.frame.collectAsState()
    val stats = remember { FrameStats.takeIf { System.getProperty("immersion.stats") != null } }
    Box(modifier.onSizeChanged { player.setTargetSize(it.width, it.height) }) {
        Canvas(Modifier.fillMaxSize()) {
            val current = frame ?: return@Canvas
            stats?.drawn(current.serial)
            // mpv letterboxes into the target size itself, so the bitmap fills the box
            drawImage(
                current.bitmap.asComposeImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(current.bitmap.width, current.bitmap.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
    }
}

@Composable
private fun SeekBar(session: PlayerSession, onBack: () -> Unit) {
    val position by session.position.collectAsState()
    val duration by session.duration.collectAsState()
    val paused by session.paused.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ Library") }
            TextButton(onClick = session::togglePause, modifier = Modifier.width(56.dp)) {
                Text(if (paused) "▶" else "❚❚")
            }
            Text(
                formatTime(dragging?.toDouble() ?: position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = (dragging ?: position.toFloat()).coerceIn(0f, duration.toFloat().coerceAtLeast(0.01f)),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { session.seekTo(it.toDouble()) }
                    dragging = null
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(0.01f),
                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.weight(1f),
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyPanel(
    app: DesktopApp,
    session: PlayerSession,
    keys: PlayerKeys,
    status: String?,
    lookup: ActiveLookup?,
    hasDictionaries: Boolean,
    setupStatus: String?,
    onLookup: (Int, String, Int) -> Unit,
    onSelect: (Int, String, Int, Int) -> Unit,
    modifier: Modifier,
) {
    val primary by session.primary.collectAsState()
    val secondary by session.secondary.collectAsState()
    val lineIndex by session.lineIndex.collectAsState()
    val lineActive by session.lineActive.collectAsState()
    var mouseHold by remember { mutableStateOf(false) }

    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxLineHeight = maxHeight * 0.4f
            Column(Modifier.fillMaxSize()) {
                PanelHeader(app, session)
                CurrentLine(
                    track = primary,
                    secondary = secondary,
                    lineIndex = lineIndex,
                    lineActive = lineActive,
                    status = status,
                    lookup = lookup,
                    textSize = app.settings.subtitleSize,
                    peeking = keys.peeking || mouseHold,
                    onHold = { mouseHold = it },
                    onCharTap = onLookup,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = maxLineHeight),
                )
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (lookup != null) {
                        DictionaryPanel(
                            lookup = lookup,
                            hasDictionaries = hasDictionaries,
                            setupStatus = setupStatus,
                            noDictionariesMessage = "No dictionaries yet. Import a Yomitan dictionary under Settings.",
                            nothingFoundMessage = "Nothing to look up here. Click or drag across a word to try another spot.",
                        )
                    } else if (setupStatus != null) {
                        Text(
                            setupStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Track choice, subtitle offset and stop-at-end, above the line. */
@Composable
private fun PanelHeader(app: DesktopApp, session: PlayerSession) {
    val tracks by session.tracks.collectAsState()
    val primary by session.primary.collectAsState()
    val secondary by session.secondary.collectAsState()
    val offset by session.offset.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackMenu("日本語", primary, tracks, allowNone = false) { it?.let(session::selectPrimary) }
        TrackMenu("English", secondary, tracks, allowNone = true, onSelect = session::selectSecondary)
        Box(Modifier.weight(1f))
        if (offset != 0.0) {
            Text(
                "%+.1fs".format(offset),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        val autoPause = app.settings.autoPause
        TextButton(onClick = { session.setAutoPause(!autoPause) }) {
            Text(
                if (autoPause) "Stop at end ✓" else "Stop at end",
                style = MaterialTheme.typography.labelMedium,
                color = if (autoPause) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackMenu(
    label: String,
    selected: SubtitleTrack?,
    tracks: List<SubtitleTrack>,
    allowNone: Boolean,
    onSelect: (SubtitleTrack?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = tracks.isNotEmpty()) {
            Text(
                if (selected == null) "$label: off" else "$label ▾",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); open = false })
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${trackLabel(track)} · ${track.cues.size} lines",
                            color = if (track === selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    },
                    onClick = { onSelect(track); open = false },
                )
            }
        }
    }
}

private fun trackLabel(track: SubtitleTrack): String =
    track.language?.let { "${track.name} ($it)" } ?: track.name

@Composable
private fun CurrentLine(
    track: SubtitleTrack?,
    secondary: SubtitleTrack?,
    lineIndex: Int,
    lineActive: Boolean,
    status: String?,
    lookup: ActiveLookup?,
    textSize: Float,
    peeking: Boolean,
    onHold: (Boolean) -> Unit,
    onCharTap: (Int, String, Int) -> Unit,
    onSelect: (Int, String, Int, Int) -> Unit,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (status != null || track == null) {
            Text(
                status ?: "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
            )
            return@Box
        }
        val cue = track.cues.getOrNull(lineIndex)
        if (cue == null) {
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Box
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            val highlight = lookup?.takeIf { it.lineIndex == lineIndex && it.result != null && it.result.matchLength > 0 }
                ?.let { it.start until it.start + it.result!!.matchLength }
            // while peeking, the Japanese stays laid out (invisible) under the English
            val japaneseAlpha by animateFloatAsState(if (peeking) 0f else 1f, label = "japaneseAlpha")
            TappableText(
                text = cue.text,
                highlight = highlight,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = textSize.sp,
                    lineHeight = 1.35.em,
                    textAlign = TextAlign.Center,
                    color = if (lineActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    },
                ),
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = textSize.sp, stepSize = 1.sp),
                onTap = { onCharTap(lineIndex, cue.text, it) },
                onHold = onHold,
                onSelect = { start, end -> onSelect(lineIndex, cue.text, start, end) },
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = japaneseAlpha },
            )
            val translation = secondary?.translationFor(cue.start, cue.end)
            AnimatedVisibility(peeking, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
                Box(contentAlignment = Alignment.Center) {
                    BasicText(
                        translation ?: "No English line here.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (translation != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            lineHeight = 1.3.em,
                        ),
                        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 22.sp, stepSize = 1.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** -Dimmersion.stats: prints frames drawn per second, to check nothing is dropped between mpv and Compose. */
private object FrameStats {
    private var windowStart = 0L
    private var count = 0
    private var lastSerial = 0L
    private var skipped = 0L

    fun drawn(serial: Long) {
        if (serial == lastSerial) return
        if (lastSerial > 0) skipped += serial - lastSerial - 1
        lastSerial = serial
        count++
        val now = System.nanoTime()
        if (now - windowStart > 1_000_000_000L) {
            if (windowStart > 0) println("drawn $count fps, frames never drawn so far: $skipped")
            windowStart = now
            count = 0
        }
    }
}
