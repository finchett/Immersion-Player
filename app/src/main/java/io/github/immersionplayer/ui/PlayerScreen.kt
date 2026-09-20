package io.github.immersionplayer.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Switch
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalView
import android.view.RoundedCorner
import androidx.compose.ui.unit.Dp
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
import io.github.immersionplayer.player.PlayerCommand
import io.github.immersionplayer.triggers.ShoulderTriggers
import io.github.immersionplayer.player.PlayerCommands
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

/** Rounded-corners appearance: gap between the cards and their corner radius. */
private val CARD_GAP = 6.dp
private val FALLBACK_CARD_RADIUS = 16.dp
private val MIN_CARD_RADIUS = 10.dp
private val LocalRoundedCorners = compositionLocalOf { false }
private val LocalCardRadius = compositionLocalOf { FALLBACK_CARD_RADIUS }

/**
 * The phone's own display corner radius, minus the card gap so the curves stay concentric
 * with the screen's corners. Falls back to a plain rounded corner if the device doesn't say.
 */
@Composable
private fun deviceCardRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val corner = view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        val radius = corner?.radius?.takeIf { it > 0 }?.let { with(density) { it.toDp() } }
        // some phones report a much smaller radius than they actually look, so keep a floor
        radius?.coerceAtLeast(MIN_CARD_RADIUS) ?: FALLBACK_CARD_RADIUS
    }
}

private const val MIN_VIDEO_FRACTION = 0.25f
private const val MAX_VIDEO_FRACTION = 0.6f
private const val MIN_PANEL_FRACTION = 0.28f
private const val MAX_PANEL_FRACTION = 0.5f

private val RESIZE_ZONE_WIDTH = 24.dp

/** Invisible drag zone on the video/panel edge; a grip appears only while it's touched. */
@Composable
private fun ResizeHandle(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier,
    vertical: Boolean = false,
) {
    var touched by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier
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
            .pointerInput(vertical) {
                if (vertical) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; onDragStart() },
                        onDragEnd = { dragging = false; onDragEnd() },
                        onDragCancel = { dragging = false; onDragEnd() },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            onDrag(dy)
                        },
                    )
                } else {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; onDragStart() },
                        onDragEnd = { dragging = false; onDragEnd() },
                        onDragCancel = { dragging = false; onDragEnd() },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            onDrag(dx)
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = touched || dragging, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .width(if (vertical) 48.dp else 5.dp)
                    .height(if (vertical) 5.dp else 48.dp)
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primary else Color(0xFF8A8E95),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

/** Share of the video width on each side where double tap jumps. */
private const val EDGE_ZONE = 0.3f
private const val JUMP_SECONDS = 5
/** Window for a second tap on a video edge to count as a double tap. */
private const val VIDEO_DOUBLE_TAP_MS = 180L
/** After a jump, further taps on the same edge within this window jump again. */
private const val JUMP_CONTINUE_MS = 600L

/** Window for a second tap on the study panel to count as a double tap. */
private const val PANEL_DOUBLE_TAP_MS = 180L

/** Scrub speed multiplier when the finger has moved far up or down from where the drag began. */
private const val SCRUB_MAX_SPEED = 10.0

@Composable
fun PlayerScreen(
    app: App,
    video: DocumentFile,
    siblings: List<DocumentFile>,
    nextEpisodeName: String?,
    onNextEpisode: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember(video) {
        PlayerSession(context.applicationContext, app.prefs, video.uri.toString(), video.name ?: "video")
    }

    // pause when the app is backgrounded or the screen turns off, and when headphones disconnect
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> session.savePosition()
                Lifecycle.Event.ON_STOP -> session.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = session.pause()
        }
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(noisyReceiver)
        }
    }

    // shoulder triggers: only while the player is actually on screen, so presses (and the
    // taps the phone's game service injects alongside them) can't reach other apps
    if (app.prefs.shoulderTriggers) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> ShoulderTriggers.start(context)
                    Lifecycle.Event.ON_STOP -> ShoulderTriggers.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            ShoulderTriggers.start(context)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                ShoulderTriggers.stop()
            }
        }
    }

    // hardware keys and triggers arrive as commands from outside the UI
    LaunchedEffect(session) {
        PlayerCommands.events.collect { command ->
            when (command) {
                PlayerCommand.PreviousLine -> session.previousLine()
                PlayerCommand.NextLine -> session.nextLine()
            }
        }
    }

    var subtitleStatus by remember { mutableStateOf<String?>("Reading subtitles…") }
    LaunchedEffect(video) {
        val tracks = withContext(Dispatchers.IO) {
            runCatching { app.subtitleLoader.load(video, siblings) }
        }
        tracks.onSuccess { list ->
            val primary = SubtitleLoader.pickPrimary(list)
            session.setTracks(list, primary, SubtitleLoader.pickSecondary(list, primary))
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


    // read only during layout/drawing so dragging the divider never recomposes the screen
    val videoFraction = remember {
        mutableFloatStateOf(app.prefs.portraitVideoFraction.coerceIn(MIN_VIDEO_FRACTION, MAX_VIDEO_FRACTION))
    }
    val panelFraction = remember {
        mutableFloatStateOf(app.prefs.panelFraction.coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION))
    }

    val rounded = app.appearance.roundedCorners
    val gap = if (rounded) CARD_GAP else 0.dp
    val cardRadius = deviceCardRadius()
    val panelShape = if (rounded) RoundedCornerShape(cardRadius) else RectangleShape

    // first time in a video: show what all the gestures are
    var showGuide by remember { mutableStateOf(!app.prefs.gestureGuideSeen) }
    LaunchedEffect(showGuide) {
        if (showGuide) session.pause()
    }

    Surface(Modifier.fillMaxSize(), color = if (rounded) MaterialTheme.colorScheme.background else Color.Black) {
      CompositionLocalProvider(LocalRoundedCorners provides rounded, LocalCardRadius provides cardRadius) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val portraitVideoHeight = maxWidth * 9 / 16
            val videoArea = @Composable { modifier: Modifier ->
                VideoArea(
                    session = session,
                    startPosition = app.prefs.position(session.videoUri),
                    nextEpisodeName = nextEpisodeName,
                    onNextEpisode = onNextEpisode,
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
                val zonePx = with(LocalDensity.current) { RESIZE_ZONE_WIDTH.toPx() }
                Box(Modifier.fillMaxSize()) {
                    Layout(
                        content = {
                            videoArea(Modifier.padding(start = gap, top = gap, bottom = gap, end = gap / 2))
                            sidePanel(Modifier.padding(start = gap / 2, top = gap, end = gap, bottom = gap).clip(panelShape))
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { measurables, constraints ->
                        val width = constraints.maxWidth
                        val height = constraints.maxHeight
                        val panelWidth = (width * panelFraction.floatValue).roundToInt()
                        val videoWidth = width - panelWidth
                        val video = measurables[0].measure(Constraints.fixed(videoWidth, height))
                        val panel = measurables[1].measure(Constraints.fixed(panelWidth, height))
                        layout(width, height) {
                            video.place(0, 0)
                            panel.place(videoWidth, 0)
                        }
                    }
                    // invisible grab zone straddling the edge, mid-height; takes no layout space
                    ResizeHandle(
                        onDragStart = {},
                        onDrag = { dx ->
                            panelFraction.floatValue = (panelFraction.floatValue - dx / totalWidthPx)
                                .coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION)
                        },
                        onDragEnd = { app.prefs.panelFraction = panelFraction.floatValue },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((totalWidthPx * (1f - panelFraction.floatValue) - zonePx / 2).roundToInt(), 0) }
                            .width(RESIZE_ZONE_WIDTH)
                            .height(140.dp),
                    )
                }
            } else {
                val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                val zonePx = with(LocalDensity.current) { RESIZE_ZONE_WIDTH.toPx() }
                Box(Modifier.fillMaxSize()) {
                    Layout(
                        content = {
                            videoArea(Modifier.padding(start = gap, top = gap, end = gap, bottom = gap / 2))
                            sidePanel(Modifier.padding(start = gap, top = gap / 2, end = gap, bottom = gap).clip(panelShape))
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { measurables, constraints ->
                        val width = constraints.maxWidth
                        val height = constraints.maxHeight
                        val videoHeight = (height * videoFraction.floatValue).roundToInt()
                        val video = measurables[0].measure(Constraints.fixed(width, videoHeight))
                        val panel = measurables[1].measure(Constraints.fixed(width, height - videoHeight))
                        layout(width, height) {
                            video.place(0, 0)
                            panel.place(0, videoHeight)
                        }
                    }
                    // grab zone on the boundary, in the middle of the width
                    ResizeHandle(
                        vertical = true,
                        onDragStart = {},
                        onDrag = { dy ->
                            videoFraction.floatValue = (videoFraction.floatValue + dy / totalHeightPx)
                                .coerceIn(MIN_VIDEO_FRACTION, MAX_VIDEO_FRACTION)
                        },
                        onDragEnd = { app.prefs.portraitVideoFraction = videoFraction.floatValue },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, (totalHeightPx * videoFraction.floatValue - zonePx / 2).roundToInt()) }
                            .height(RESIZE_ZONE_WIDTH)
                            .width(160.dp),
                    )
                }
            }
        }
        if (showGuide) {
            Box(Modifier.fillMaxSize().zIndex(10f)) {
                GestureGuide(onDismiss = {
                    showGuide = false
                    app.prefs.gestureGuideSeen = true
                    session.play()
                })
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
private fun VideoArea(
    session: PlayerSession,
    startPosition: Double,
    nextEpisodeName: String?,
    onNextEpisode: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val ended by session.ended.collectAsState()

    var mpvView by remember { mutableStateOf<MpvView?>(null) }
    val paused by session.paused.collectAsState()
    val position by session.position.collectAsState()
    val duration by session.duration.collectAsState()

    var scrubTarget by remember { mutableStateOf<Double?>(null) }
    var scrubOffset by remember { mutableStateOf(0.0) }
    var scrubSpeed by remember { mutableStateOf(1.0) }
    var flash by remember { mutableIntStateOf(0) }
    var showOptions by remember { mutableStateOf(false) }

    // double-tap jumps on the video's edges
    val videoScope = rememberCoroutineScope()
    var pendingVideoTap by remember { mutableStateOf<Job?>(null) }
    var pendingZone by remember { mutableIntStateOf(0) }
    var jumpZone by remember { mutableIntStateOf(0) }
    var lastJumpTap by remember { mutableStateOf(0L) }
    var jumpTotal by remember { mutableIntStateOf(0) }
    var jumpShown by remember { mutableIntStateOf(0) }
    // what the pill displays; only changed by a jump so the fade-out keeps showing the last one
    var jumpLabelZone by remember { mutableIntStateOf(0) }
    var jumpLabelTotal by remember { mutableIntStateOf(0) }
    var jumpLabelVisible by remember { mutableStateOf(false) }
    LaunchedEffect(jumpShown) {
        if (jumpShown > 0) {
            delay(JUMP_CONTINUE_MS)
            jumpLabelVisible = false
            jumpZone = 0
            jumpTotal = 0
        }
    }
    fun jump(direction: Int) {
        jumpTotal += JUMP_SECONDS
        jumpLabelZone = direction
        jumpLabelTotal = jumpTotal
        jumpLabelVisible = true
        val target = session.position.value + direction * JUMP_SECONDS
        val maxTime = session.duration.value.takeIf { it > 0 } ?: Double.MAX_VALUE
        session.seekTo(target.coerceIn(0.0, maxTime))
        jumpShown++
    }
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

    val rounded = LocalRoundedCorners.current
    val cardRadius = LocalCardRadius.current
    val maskColor = MaterialTheme.colorScheme.background
    BoxWithConstraints(
        modifier
            .clip(if (rounded) RoundedCornerShape(cardRadius) else RectangleShape)
            .background(Color.Black),
    ) {
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
        if (rounded) {
            // SurfaceView ignores Compose clipping, so paint the corners over it instead
            Canvas(Modifier.fillMaxSize()) {
                val radius = cardRadius.toPx()
                val corners = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                }
                drawPath(corners, maskColor)
            }
        }

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
                    detectTapGestures(onTap = { offset ->
                        // middle: instant play/pause. Edges: double tap = jump ±5s (more taps stack);
                        // a single edge tap still plays/pauses after a short wait.
                        val zone = when {
                            offset.x < widthPx * EDGE_ZONE -> -1
                            offset.x > widthPx * (1 - EDGE_ZONE) -> 1
                            else -> 0
                        }
                        val now = SystemClock.uptimeMillis()
                        val pending = pendingVideoTap
                        when {
                            zone == 0 -> {
                                pending?.cancel()
                                session.togglePause()
                                flash++
                            }
                            zone == jumpZone && now - lastJumpTap < JUMP_CONTINUE_MS -> {
                                lastJumpTap = now
                                jump(zone)
                            }
                            pending != null && pending.isActive && pendingZone == zone -> {
                                pending.cancel()
                                jumpZone = zone
                                lastJumpTap = now
                                jumpTotal = 0
                                jump(zone)
                            }
                            else -> {
                                pending?.cancel()
                                pendingZone = zone
                                pendingVideoTap = videoScope.launch {
                                    delay(VIDEO_DOUBLE_TAP_MS)
                                    session.togglePause()
                                    flash++
                                }
                            }
                        }
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

        // end of episode: offer the next one in the folder
        AnimatedVisibility(
            visible = ended && onNextEpisode != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                Modifier
                    .background(Color(0xCC1E2126), RoundedCornerShape(16.dp))
                    .clickable { onNextEpisode?.invoke() }
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Next episode  ▶", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    nextEpisodeName.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // ±seconds indicator on the tapped edge
        AnimatedVisibility(
            visible = jumpLabelVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(if (jumpLabelZone < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 28.dp),
        ) {
            Text(
                if (jumpLabelZone < 0) "« −${jumpLabelTotal}s" else "+${jumpLabelTotal}s »",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier
                    .background(Color(0x99000000), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
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

        // paused: close, time and options
        AnimatedVisibility(paused && scrubTarget == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "${formatTime(position)} / ${formatTime(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 16.dp)
                        .background(Color(0x88000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    "✕",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color(0x88000000), CircleShape)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Text(
                    "⋯",
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color(0x88000000), CircleShape)
                        .clickable { showOptions = true }
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
        if (showOptions) PlayerOptions(session, onDismiss = { showOptions = false })

        // seek bar: a thin line on the bottom edge, hidden while playing. While paused it can be
        // tapped or dragged (via an invisible touch strip); it fattens while held.
        AnimatedVisibility(
            visible = paused || scrubTarget != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            val shown = scrubTarget ?: position
            val fraction = if (duration > 0) (shown / duration).toFloat().coerceIn(0f, 1f) else 0f
            var held by remember { mutableStateOf(false) }
            val barHeight by animateDpAsState(if (held) 10.dp else 4.dp, label = "seekBarHeight")
            fun timeAt(x: Float) = (x / widthPx).coerceIn(0f, 1f) * session.duration.value
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            held = true
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                            held = false
                        }
                    }
                    .pointerInput(widthPx) {
                        detectTapGestures(onTap = { offset -> session.seekTo(timeAt(offset.x)) })
                    }
                    .pointerInput(widthPx) {
                        var start = 0.0
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                start = session.position.value
                                scrubSpeed = 1.0
                                scrubTarget = timeAt(offset.x)
                                scrubOffset = scrubTarget!! - start
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                scrubTarget = timeAt(change.position.x)
                                scrubOffset = scrubTarget!! - start
                            },
                            onDragEnd = {
                                scrubTarget?.let(session::seekTo)
                                scrubTarget = null
                            },
                            onDragCancel = { scrubTarget = null },
                        )
                    },
                contentAlignment = Alignment.BottomStart,
            ) {
                Box(Modifier.fillMaxWidth().height(barHeight).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(MaterialTheme.colorScheme.primary))
                }
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
                    onReplay = session::replayLine,
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
    onReplay: () -> Unit,
    modifier: Modifier,
) {
    val replayThreshold = with(LocalDensity.current) { 40.dp.toPx() }
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                // swipe up on the line to hear it again
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        total += amount
                    },
                    onDragEnd = { if (total < -replayThreshold) onReplay() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (status != null || track == null) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(status ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
                        color = if (lineActive) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        },
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
                                color = if (translation != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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

/** Per-video options, opened from the ⋯ button while paused. */
@Composable
private fun PlayerOptions(session: PlayerSession, onDismiss: () -> Unit) {
    val tracks by session.tracks.collectAsState()
    val primary by session.primary.collectAsState()
    val secondary by session.secondary.collectAsState()
    val offset by session.offset.collectAsState()
    val fill by session.fill.collectAsState()
    val autoPause by session.autoPause.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(0.8f),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Options", style = MaterialTheme.typography.titleLarge)

                if (tracks.isEmpty()) {
                    Text("No text subtitle tracks in this video.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Subtitles to study", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tracks.forEach { track ->
                            FilterChip(
                                selected = track === primary,
                                onClick = { session.selectPrimary(track) },
                                label = { Text(trackLabel(track), maxLines = 1) },
                            )
                        }
                    }
                    Text("Translation (hold the panel to peek)", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = secondary == null,
                            onClick = { session.selectSecondary(null) },
                            label = { Text("None") },
                        )
                        tracks.filter { it !== primary }.forEach { track ->
                            FilterChip(
                                selected = track === secondary,
                                onClick = { session.selectSecondary(track) },
                                label = { Text(trackLabel(track), maxLines = 1) },
                            )
                        }
                    }
                }

                Text("Subtitle timing", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-1.0 to "−1s", -0.1 to "−0.1s").forEach { (delta, label) ->
                        FilledTonalButton(onClick = { session.setOffset(offset + delta) }) { Text(label) }
                    }
                    Text(
                        when {
                            offset == 0.0 -> "in sync"
                            offset > 0 -> "+%.1fs later".format(offset)
                            else -> "%.1fs earlier".format(-offset)
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(96.dp),
                    )
                    listOf(0.1 to "+0.1s", 1.0 to "+1s").forEach { (delta, label) ->
                        FilledTonalButton(onClick = { session.setOffset(offset + delta) }) { Text(label) }
                    }
                    if (offset != 0.0) TextButton(onClick = { session.setOffset(0.0) }) { Text("Reset") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fill the video area (or pinch)", Modifier.weight(1f))
                    Switch(checked = fill, onCheckedChange = session::setFill)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stop at the end of each line (or double-tap the panel)", Modifier.weight(1f))
                    Switch(checked = autoPause, onCheckedChange = session::setAutoPause)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}

private fun trackLabel(track: SubtitleTrack): String {
    val language = track.language?.let { " · $it" }.orEmpty()
    return "${track.name}$language · ${track.cues.size} lines"
}

/** English text overlapping a Japanese line's time range. */
fun translationFor(track: SubtitleTrack, start: Double, end: Double): String? {
    val lines = track.cues.filter { it.start < end - 0.1 && it.end > start + 0.1 }
    return lines.joinToString("\n") { it.text }.ifEmpty { null }
}
