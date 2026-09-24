@file:OptIn(ExperimentalComposeUiApi::class)

package io.github.immersionplayer.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.immersionplayer.anki.MinedWord
import io.github.immersionplayer.desktop.mpv.MpvPlayer
import io.github.immersionplayer.dictionary.DictionaryLookup
import io.github.immersionplayer.dictionary.LookupResult
import io.github.immersionplayer.dictionary.TermEntry
import io.github.immersionplayer.library.formatTime
import io.github.immersionplayer.subs.SubtitleTrack
import io.github.immersionplayer.subs.translationFor
import io.github.immersionplayer.ui.ActiveLookup
import io.github.immersionplayer.ui.AnkiStatusText
import io.github.immersionplayer.ui.CardButton
import io.github.immersionplayer.ui.DictionaryPanel
import io.github.immersionplayer.ui.TappableText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor

/** Keyboard actions the window forwards to the player. */
class PlayerKeys {
    var peeking by mutableStateOf(false)
    /** Whether the player's controls are showing; the traffic lights come and go with them. */
    var chromeVisible by mutableStateOf(true)
    var onToggleFullscreen: () -> Unit = {}
    /** Makes an Anki card of the word looked up first, when cards are turned on. */
    var onAddCard: () -> Unit = {}
}

@Composable
fun PlayerScreen(app: DesktopApp, session: PlayerSession, keys: PlayerKeys, onBack: () -> Unit) {
    val settings = app.settings
    val scope = rememberCoroutineScope()

    var subtitleStatus by remember { mutableStateOf<String?>("Reading subtitles…") }
    LaunchedEffect(session) {
        val tracks = withContext(Dispatchers.IO) { runCatching { app.loadSubtitles(session.video) } }
        tracks.onSuccess { list ->
            session.setTracks(list)
            subtitleStatus = if (list.isEmpty()) "No text subtitles found for this video." else null
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

    /** The looked-up [entry] in its line, with the line's times in the video's clock. */
    fun minedWord(entry: TermEntry): MinedWord? {
        val current = lookup ?: return null
        val cue = primary?.cues?.getOrNull(current.lineIndex)?.takeIf { it.text == current.text } ?: return null
        val offset = session.offset.value
        return MinedWord(
            sentence = cue.text,
            wordStart = current.start,
            wordLength = entry.sourceLength,
            entry = entry,
            translation = session.secondary.value?.translationFor(cue.start, cue.end),
            videoName = session.video.nameWithoutExtension,
            start = cue.start + offset,
            end = cue.end + offset,
        )
    }

    fun addCard(entry: TermEntry) {
        val word = minedWord(entry) ?: return
        // the frame on screen, if it's from this line; otherwise one from the middle of it
        val position = session.position.value
        val frameAt = if (position in word.start..word.end) position else (word.start + word.end) / 2
        app.anki.add(word, VideoClips.ForCard(settings, session.video, session.player.propertyString("aid"), frameAt))
    }

    DisposableEffect(keys, settings.ankiEnabled) {
        keys.onAddCard = {
            if (settings.ankiEnabled) lookup?.result?.entries?.firstOrNull()?.let(::addCard)
        }
        onDispose { keys.onAddCard = {} }
    }

    // pan & scan: mpv crops to fill the area instead of letterboxing
    LaunchedEffect(settings.videoFill) {
        session.player.property("panscan", if (settings.videoFill) "1.0" else "0.0")
    }

    val rounded = settings.roundedCorners
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gutter = when {
        !rounded -> MaterialTheme.colorScheme.background
        dark -> Color.Black
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val corners = LocalCorners.current
    val shape = if (rounded) RoundedCornerShape(corners.cardRadius) else RectangleShape
    val gap = if (rounded) corners.cardGap else 0.dp

    BoxWithConstraints(Modifier.fillMaxSize().background(gutter).padding(gap)) {
        val totalWidth = maxWidth
        var panelFraction by remember { mutableFloatStateOf(settings.panelFraction.coerceIn(0.2f, 0.6f)) }
        Row(Modifier.fillMaxSize()) {
            VideoArea(
                session,
                keys,
                Modifier.weight(1f).fillMaxHeight().clip(shape)
                    // ⌘/Ctrl + scroll: up fills the area, down fits the whole picture
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val modifier = event.keyboardModifiers
                        if (!modifier.isMetaPressed && !modifier.isCtrlPressed) return@onPointerEvent
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                        if (dy < 0 && !settings.videoFill) settings.updateVideoFill(true)
                        if (dy > 0 && settings.videoFill) settings.updateVideoFill(false)
                        event.changes.forEach { it.consume() }
                    },
            )
            val density = LocalDensity.current
            // the divider: a hairline, or the gap between cards. The grab area is wider than
            // what's drawn and sits above both sides, so a thin gap is still easy to catch.
            Box(
                Modifier.width(if (rounded) gap else 1.dp).fillMaxHeight().zIndex(1f)
                    .wrapContentWidth(unbounded = true),
                contentAlignment = Alignment.Center,
            ) {
                if (!rounded) Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                Box(
                    Modifier.requiredWidth(11.dp).fillMaxHeight()
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
            }
            run {
                StudyPanel(
                    app = app,
                    session = session,
                    keys = keys,
                    status = subtitleStatus,
                    lookup = lookup,
                    hasDictionaries = hasDictionaries,
                    setupStatus = setupStatus,
                    onBack = onBack,
                    onLookup = { line, text, start -> startLookup(line, text, start) },
                    onSelect = { line, text, start, end -> startLookup(line, text, start, end - start) },
                    cardButton = if (settings.ankiEnabled) {
                        { entry ->
                            val word = minedWord(entry)
                            CardButton(
                                added = word != null && app.anki.isAdded(word),
                                enabled = word != null,
                                onAdd = { addCard(entry) },
                                onRemove = { word?.let(app.anki::remove) },
                                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp).pointerHoverIcon(PointerIcon.Hand),
                            )
                        }
                    } else null,
                    modifier = Modifier.width(totalWidth * panelFraction).fillMaxHeight().clip(shape),
                )
            }
        }
    }
}

/** How long the controls stay after the mouse stops moving over the video. */
private const val CONTROLS_TIMEOUT_MS = 2000L

/** The video, with controls that show on mouse movement or while paused and otherwise get out of the way. */
@Composable
private fun VideoArea(session: PlayerSession, keys: PlayerKeys, modifier: Modifier) {
    val paused by session.paused.collectAsState()
    var lastMove by remember { mutableLongStateOf(0L) }
    var overControls by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(lastMove) {
        moving = lastMove > 0
        delay(CONTROLS_TIMEOUT_MS)
        moving = false
    }
    val visible = paused || moving || overControls
    LaunchedEffect(visible) { keys.chromeVisible = visible }
    DisposableEffect(Unit) { onDispose { keys.chromeVisible = true } }
    Box(
        modifier.background(Color.Black)
            .onPointerEvent(PointerEventType.Move) { lastMove = System.nanoTime() }
            .onPointerEvent(PointerEventType.Exit) { moving = false }
            .pointerHoverIcon(if (visible) PointerIcon.Default else HiddenCursor)
            .pointerInput(session) {
                detectTapGestures(
                    onTap = { session.togglePause() },
                    onDoubleTap = { keys.onToggleFullscreen() },
                )
            },
    ) {
        VideoSurface(session.player, Modifier.fillMaxSize())
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { overControls = true }
                .onPointerEvent(PointerEventType.Exit) { overControls = false },
        ) {
            Controls(session)
        }
    }
}

private val HiddenCursor = PointerIcon(
    java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
        java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
        java.awt.Point(0, 0),
        "hidden",
    )
)

@Composable
private fun Controls(session: PlayerSession) {
    val position by session.position.collectAsState()
    val duration by session.duration.collectAsState()
    val paused by session.paused.collectAsState()
    var scrubbing by remember { mutableStateOf<Double?>(null) }
    val white = Color.White
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA6000000))))
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Scrubber(
            position = scrubbing ?: position,
            duration = duration,
            onScrub = { scrubbing = it },
            onScrubEnd = {
                scrubbing?.let(session::seekTo)
                scrubbing = null
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextAction(
                if (paused) "▶" else "❚❚",
                onClick = session::togglePause,
                color = white,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${formatTime(scrubbing ?: position)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.labelMedium,
                color = white.copy(alpha = 0.85f),
            )
        }
    }
}

/** A thin seek bar that thickens under the pointer. */
@Composable
private fun Scrubber(position: Double, duration: Double, onScrub: (Double) -> Unit, onScrubEnd: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val thickness by animateDpAsState(if (hovered) 6.dp else 3.dp, label = "scrubber")
    val played = MaterialTheme.colorScheme.primary
    fun at(x: Float, width: Int) = (x / width).coerceIn(0f, 1f) * duration
    Canvas(
        Modifier.fillMaxWidth().height(14.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(duration) {
                detectTapGestures { onScrub(at(it.x, size.width)); onScrubEnd() }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { onScrub(at(it.x, size.width)) },
                    onDragEnd = onScrubEnd,
                    onDragCancel = onScrubEnd,
                ) { change, _ ->
                    change.consume()
                    onScrub(at(change.position.x, size.width))
                }
            },
    ) {
        val h = thickness.toPx()
        val y = (size.height - h) / 2
        val radius = CornerRadius(h / 2)
        val fraction = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
        drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(0f, y), Size(size.width, h), radius)
        drawRoundRect(played, Offset(0f, y), Size(size.width * fraction, h), radius)
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
private fun StudyPanel(
    app: DesktopApp,
    session: PlayerSession,
    keys: PlayerKeys,
    status: String?,
    lookup: ActiveLookup?,
    hasDictionaries: Boolean,
    setupStatus: String?,
    onBack: () -> Unit,
    onLookup: (Int, String, Int) -> Unit,
    onSelect: (Int, String, Int, Int) -> Unit,
    cardButton: (@Composable (TermEntry) -> Unit)?,
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
                PanelHeader(app, session, onBack)
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (lookup != null) {
                        DictionaryPanel(
                            lookup = lookup,
                            hasDictionaries = hasDictionaries,
                            setupStatus = setupStatus,
                            noDictionariesMessage = "No dictionaries yet. Import a Yomitan dictionary under Settings.",
                            nothingFoundMessage = "Nothing to look up here. Click or drag across a word to try another spot.",
                            entryAction = cardButton,
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

/** Back to the library, what Anki is doing, and the subtitle offset when it isn't zero. */
@Composable
private fun PanelHeader(app: DesktopApp, session: PlayerSession, onBack: () -> Unit) {
    val offset by session.offset.collectAsState()
    val ankiStatus by app.anki.status.collectAsState()
    // same height as the other headers, so it lines up with the traffic lights across the window
    Row(
        Modifier.fillMaxWidth().height(HeaderHeight).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextAction("‹ Library", onClick = onBack)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { AnkiStatusText(ankiStatus) }
        if (offset != 0.0) {
            Text(
                "%+.1f s".format(offset),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
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
                        translation ?: if (secondary == null) "No subtitles in your peek language." else "Nothing here in your peek language.",
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
