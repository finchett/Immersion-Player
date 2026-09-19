package io.github.immersionplayer.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import io.github.immersionplayer.App
import io.github.immersionplayer.dictionary.BundledDictionaries
import io.github.immersionplayer.dictionary.DictionaryLookup
import io.github.immersionplayer.dictionary.LookupResult
import io.github.immersionplayer.dictionary.TermEntry
import io.github.immersionplayer.player.MpvView
import io.github.immersionplayer.player.PlayerSession
import io.github.immersionplayer.subs.SubtitleLoader
import io.github.immersionplayer.subs.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** A lookup started by tapping a character in a subtitle line. */
data class ActiveLookup(
    val lineIndex: Int,
    val text: String,
    val start: Int,
    val result: LookupResult? = null,
)

/** Seconds covered by dragging across the full width of the video at normal speed. */
private const val SCRUB_SECONDS_PER_WIDTH = 90.0

private const val MIN_PANEL_FRACTION = 0.28f
private const val MAX_PANEL_FRACTION = 0.5f

/** Drag handle between the video and the study panel; the grip only shows while touched. */
@Composable
private fun ResizeHandle(onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    var touched by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxHeight()
            .width(12.dp)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touched = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    touched = false
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; onDragEnd() },
                    onDragCancel = { dragging = false; onDragEnd() },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        onDrag(dx)
                    },
                )
            },
    ) {
        // hairline where the video meets the panel
        Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        AnimatedVisibility(
            visible = touched || dragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primary else Color(0xFF6A6E75),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

/** Window for a second tap on the study panel to count as a double tap. */
private const val PANEL_DOUBLE_TAP_MS = 180L

/** Scrub speed multiplier when the finger has moved far up or down from where the drag began. */
private const val SCRUB_MAX_SPEED = 10.0

@Composable
fun PlayerScreen(app: App, video: DocumentFile, siblings: List<DocumentFile>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember(video) {
        PlayerSession(context.applicationContext, app.prefs, video.uri.toString(), video.name ?: "video")
    }

    // fullscreen while playing
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    var subtitleStatus by remember { mutableStateOf<String?>("Reading subtitles…") }
    LaunchedEffect(video) {
        val tracks = withContext(Dispatchers.IO) {
            runCatching { app.subtitleLoader.load(video, siblings) }
        }
        tracks.onSuccess { list ->
            val primary = SubtitleLoader.pickPrimary(list)
            session.setTracks(primary, SubtitleLoader.pickSecondary(list, primary))
            subtitleStatus = if (primary == null) "No text subtitles found for this video" else null
        }.onFailure {
            subtitleStatus = "Couldn't read subtitles: ${it.message}"
        }
    }

    val primary by session.primary.collectAsState()
    val secondary by session.secondary.collectAsState()

    var lookup by remember { mutableStateOf<ActiveLookup?>(null) }
    var hasDictionaries by remember { mutableStateOf(true) }
    val dictionarySetup by BundledDictionaries.status.collectAsState()
    LaunchedEffect(dictionarySetup) {
        hasDictionaries = withContext(Dispatchers.IO) { app.dictionaryDatabase.dictionaries().any { it.enabled } }
    }

    // each lookup gets a generation so a slow automatic lookup can't overwrite a newer one
    var lookupGeneration by remember { mutableIntStateOf(0) }

    /** Lookup the user asked for: a tapped character, or a dragged selection (max [maxLength]). */
    fun startLookup(line: Int, text: String, start: Int, maxLength: Int = Int.MAX_VALUE) {
        if (app.prefs.pauseOnLookup) session.pause()
        val generation = ++lookupGeneration
        lookup = ActiveLookup(line, text, start)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { app.lookup.lookup(text, start, maxLength) }
                    .onFailure { app.logError("lookup '$text' @$start", it) }
                    .getOrNull()
            }
            if (generation == lookupGeneration) lookup = ActiveLookup(line, text, start, result)
        }
    }

    // every line always has a word defined: the first kanji (or next best) that has an entry
    val lineIndex by session.lineIndex.collectAsState()
    LaunchedEffect(lineIndex, primary, hasDictionaries) {
        val cue = primary?.cues?.getOrNull(lineIndex) ?: return@LaunchedEffect
        if (lookup?.lineIndex == lineIndex) return@LaunchedEffect
        val generation = ++lookupGeneration
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
        // never leave the panel spinning: lines like ♪～ have nothing to look up
        if (generation == lookupGeneration) {
            lookup = found ?: ActiveLookup(lineIndex, text, 0, LookupResult(text, 0, 0, emptyList()))
        }
    }


    var panelFraction by remember {
        mutableFloatStateOf(app.prefs.panelFraction.coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION))
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val portraitVideoHeight = maxWidth * 9 / 16
            val videoArea = @Composable { modifier: Modifier ->
                VideoArea(
                    session = session,
                    startPosition = app.prefs.position(session.videoUri),
                    onBack = onBack,
                    modifier = modifier,
                )
            }
            val sidePanel = @Composable { modifier: Modifier ->
                StudyPanel(
                    app = app,
                    session = session,
                    status = subtitleStatus,
                    lookup = lookup,
                    hasDictionaries = hasDictionaries,
                    dictionarySetup = dictionarySetup,
                    onLookup = { line, text, start -> startLookup(line, text, start) },
                    onSelect = { line, text, start, end -> startLookup(line, text, start, end - start) },
                    modifier = modifier,
                )
            }
            if (landscape) {
                val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                Row(Modifier.fillMaxSize()) {
                    videoArea(Modifier.weight(1f - panelFraction).fillMaxHeight())
                    ResizeHandle(
                        onDrag = { dx ->
                            panelFraction = (panelFraction - dx / totalWidthPx).coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION)
                        },
                        onDragEnd = { app.prefs.panelFraction = panelFraction },
                    )
                    sidePanel(Modifier.weight(panelFraction).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    videoArea(Modifier.fillMaxWidth().height(portraitVideoHeight))
                    sidePanel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Full-bleed video. Tap toggles play/pause; dragging sideways scrubs.
 * A thin progress line sits on the bottom edge; time and a close button show while paused.
 */
@Composable
private fun VideoArea(session: PlayerSession, startPosition: Double, onBack: () -> Unit, modifier: Modifier) {
    var mpvView by remember { mutableStateOf<MpvView?>(null) }
    val paused by session.paused.collectAsState()
    val position by session.position.collectAsState()
    val duration by session.duration.collectAsState()

    var scrubTarget by remember { mutableStateOf<Double?>(null) }
    var scrubOffset by remember { mutableStateOf(0.0) }
    var scrubSpeed by remember { mutableStateOf(1.0) }
    var flash by remember { mutableIntStateOf(0) }
    var flashFill by remember { mutableIntStateOf(0) }
    var fillNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flashFill) {
        if (flashFill > 0) {
            delay(900)
            fillNotice = null
        }
    }
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash > 0) {
            showFlash = true
            delay(500)
            showFlash = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            session.detach()
            mpvView?.destroy()
        }
    }

    BoxWithConstraints(modifier.background(Color.Black)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MpvView(ctx).also { view ->
                    view.keepScreenOn = true
                    view.initialize(startPosition, session.fill.value)
                    session.attach(view)
                    view.playFile(session.videoUri)
                    mpvView = view
                }
            },
        )

        // gesture layer above the video surface
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // pinch out = fill, pinch in = fit. Runs in the Initial pass so a second finger
                    // cancels the scrub/tap the first finger started.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var zoom = 1f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom *= event.calculateZoom()
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        val fill = when {
                            zoom > 1.12f -> true
                            zoom < 0.9f -> false
                            else -> null
                        }
                        if (fill != null && fill != session.fill.value) {
                            session.setFill(fill)
                            fillNotice = if (fill) "Fill" else "Fit"
                            flashFill++
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        session.togglePause()
                        flash++
                    })
                }
                .pointerInput(widthPx, heightPx) {
                    // horizontal movement scrubs; moving away vertically from where the drag
                    // started speeds it up (applies to movement from then on, so you can slow back down)
                    var start = 0.0
                    var target = 0.0
                    var startY = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            start = session.position.value
                            target = start
                            startY = offset.y
                            scrubOffset = 0.0
                            scrubSpeed = 1.0
                            scrubTarget = start
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val distance = (abs(change.position.y - startY) / (heightPx * 0.45f)).coerceIn(0f, 1f)
                            scrubSpeed = 1.0 + (SCRUB_MAX_SPEED - 1.0) * distance * distance
                            val maxTime = session.duration.value.takeIf { it > 0 } ?: Double.MAX_VALUE
                            target = (target + amount.x / widthPx * SCRUB_SECONDS_PER_WIDTH * scrubSpeed).coerceIn(0.0, maxTime)
                            scrubOffset = target - start
                            scrubTarget = target
                        },
                        onDragEnd = {
                            scrubTarget?.let(session::seekTo)
                            scrubTarget = null
                        },
                        onDragCancel = { scrubTarget = null },
                    )
                },
        )

        // play/pause flash in the middle
        AnimatedVisibility(showFlash, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(
                Modifier.background(Color(0x99000000), CircleShape).padding(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (paused) "❚❚" else "▶", color = Color.White, fontSize = 28.sp)
            }
        }

        // fill/fit notice
        AnimatedVisibility(fillNotice != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Text(
                fillNotice.orEmpty(),
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        // scrub bubble
        scrubTarget?.let { target ->
            val sign = if (scrubOffset >= 0) "+" else "−"
            val speed = if (scrubSpeed >= 1.5) "   ×${scrubSpeed.toInt()}" else ""
            Text(
                "${formatTime(target)}   $sign${formatTime(abs(scrubOffset))}$speed",
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        // paused: time (the back gesture closes the player)
        AnimatedVisibility(paused && scrubTarget == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "${formatTime(position)} / ${formatTime(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp)
                        .background(Color(0x88000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        // progress line: only while paused or scrubbing
        AnimatedVisibility(
            visible = paused || scrubTarget != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            val shown = scrubTarget ?: position
            val fraction = if (duration > 0) (shown / duration).toFloat().coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

/**
 * The study side: the current line at the top, definitions right below.
 * Swipe anywhere for previous/next line, hold anywhere to peek at the English, tap to play/pause.
 */
@Composable
private fun StudyPanel(
    app: App,
    session: PlayerSession,
    status: String?,
    lookup: ActiveLookup?,
    hasDictionaries: Boolean,
    dictionarySetup: String?,
    onLookup: (Int, String, Int) -> Unit,
    onSelect: (Int, String, Int, Int) -> Unit,
    modifier: Modifier,
) {
    val primary by session.primary.collectAsState()
    val secondary by session.secondary.collectAsState()
    val lineIndex by session.lineIndex.collectAsState()
    val lineActive by session.lineActive.collectAsState()

    val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    var peeking by remember { mutableStateOf(false) }
    val onHold: (Boolean) -> Unit = remember(haptics) {
        { hold ->
            if (hold && !peeking) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            peeking = hold
        }
    }

    // brief "Stop at end: on/off" notice after a double tap
    val autoPause by session.autoPause.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(noticeCount) {
        if (noticeCount > 0) {
            delay(1200)
            notice = null
        }
    }
    val panelScope = rememberCoroutineScope()
    var pendingPanelTap by remember { mutableStateOf<Job?>(null) }
    fun toggleStopAtEnd() {
        val enabled = !autoPause
        session.setAutoPause(enabled)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        notice = if (enabled) "Stop at end of line: on" else "Stop at end of line: off"
        noticeCount++
    }

    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            total += amount
                        },
                        onDragEnd = {
                            if (abs(total) > swipeThreshold) {
                                if (total < 0) session.nextLine() else session.previousLine()
                            }
                        },
                    )
                }
                .pointerInput(onHold) {
                    // observe (without consuming) so it also sees holds that started on the text,
                    // even if that text is swapped out by a new line mid-hold
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        onHold(false)
                    }
                }
                .pointerInput(onHold) {
                    detectTapGestures(
                        onLongPress = { onHold(true) },
                        // single tap = play/pause after a short wait; a second tap within it = stop-at-end.
                        // (shorter than Android's 300ms double-tap timeout so pausing stays snappy)
                        onTap = {
                            val pending = pendingPanelTap
                            if (pending != null && pending.isActive) {
                                pending.cancel()
                                pendingPanelTap = null
                                toggleStopAtEnd()
                            } else {
                                pendingPanelTap = panelScope.launch {
                                    delay(PANEL_DOUBLE_TAP_MS)
                                    session.togglePause()
                                }
                            }
                        },
                    )
                },
        ) {
            val maxLineHeight = maxHeight * 0.4f
            Column(Modifier.fillMaxSize()) {
                CurrentLine(
                    track = primary,
                    secondary = secondary,
                    lineIndex = lineIndex,
                    lineActive = lineActive,
                    status = status,
                    lookup = lookup,
                    textSize = app.prefs.subtitleSize,
                    peeking = peeking,
                    onHold = onHold,
                    onCharTap = onLookup,
                    onSelect = onSelect,
                    onTapOutside = session::togglePause,
                    modifier = Modifier.fillMaxWidth().heightIn(max = maxLineHeight),
                )
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (lookup != null) {
                        DictionaryPanel(
                            lookup = lookup,
                            hasDictionaries = hasDictionaries,
                            setupStatus = dictionarySetup,
                        )
                    } else if (dictionarySetup != null) {
                        Text(
                            dictionarySetup,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = notice != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).zIndex(2f),
            ) {
                Text(
                    notice.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(Color(0xE0303338), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

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
    onTapOutside: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        if (status != null || track == null) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                if (status == "Reading subtitles…") CircularProgressIndicator()
                else Text(status ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            return@Box
        }

        AnimatedContent(
            targetState = lineIndex,
            transitionSpec = {
                val forward = targetState >= initialState
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "line",
        ) { index ->
            val cue = track.cues.getOrNull(index)
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                if (cue == null) {
                    Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Box
                }
                val highlight = lookup?.takeIf { it.lineIndex == index && it.result != null && it.result.matchLength > 0 }
                    ?.let { it.start until it.start + it.result!!.matchLength }
                // compact, but long lines shrink rather than grow past the panel's cap.
                // While peeking the Japanese stays laid out (invisible) under the English.
                val japaneseAlpha by animateFloatAsState(if (peeking) 0f else 1f, label = "japaneseAlpha")
                TappableText(
                    text = cue.text,
                    highlight = highlight,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = textSize.sp,
                        lineHeight = 1.35.em,
                        textAlign = TextAlign.Center,
                        color = if (lineActive) Color.White else Color(0xFFC4C7CC),
                    ),
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = textSize.sp, stepSize = 1.sp),
                    onTap = { onCharTap(index, cue.text, it) },
                    onHold = onHold,
                    onSelect = { start, end -> onSelect(index, cue.text, start, end) },
                    onTapOutside = onTapOutside,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = japaneseAlpha },
                )
                val translation = secondary?.let { translationFor(it, cue.start, cue.end) }
                AnimatedVisibility(peeking, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        BasicText(
                            translation ?: "No English line here.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (translation != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 1.3.em,
                            ),
                            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 20.sp, stepSize = 1.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

    }
}

/** English text overlapping a Japanese line's time range. */
fun translationFor(track: SubtitleTrack, start: Double, end: Double): String? {
    val lines = track.cues.filter { it.start < end - 0.1 && it.end > start + 0.1 }
    return lines.joinToString("\n") { it.text }.ifEmpty { null }
}
