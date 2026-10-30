@file:OptIn(ExperimentalComposeUiApi::class)

package app.mizune.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.DpOffset
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.mizune.anki.MinedWord
import app.mizune.desktop.mpv.MpvPlayer
import app.mizune.dictionary.DictionaryLookup
import app.mizune.dictionary.LookupResult
import app.mizune.dictionary.TermEntry
import app.mizune.library.formatTime
import app.mizune.subs.SubtitleTrack
import app.mizune.ui.Cog
import app.mizune.ui.FullscreenIcon
import app.mizune.ui.PanelIcon
import app.mizune.ui.QuietArea
import app.mizune.ui.Scrubber
import app.mizune.ui.LineSkipIcon
import app.mizune.ui.LineStopIcon
import app.mizune.ui.ReplayIcon
import app.mizune.subs.translationFor
import app.mizune.ui.ActiveLookup
import app.mizune.ui.AnkiStatusText
import app.mizune.ui.CardButton
import app.mizune.ui.DictionaryPanel
import app.mizune.ui.TappableText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.io.File

/** Keyboard actions the window forwards to the player. */
class PlayerKeys {
    var peeking by mutableStateOf(false)
    /** Whether the player's controls are showing; the traffic lights come and go with them. */
    var chromeVisible by mutableStateOf(true)
    var onToggleFullscreen: () -> Unit = {}
    /** Whether the window is in full screen, so the button offers the way back out. */
    var fullscreen by mutableStateOf(false)
    /** Makes an Anki card of the word looked up first, when cards are turned on. */
    var onAddCard: () -> Unit = {}
    /** Opens the next episode, while the end-of-video prompt offers one. */
    var onPlayNext: (() -> Unit)? = null
    /** Closes what's open over the video (a word's lookup), if anything is: Esc does this first. */
    var onDismiss: (() -> Unit)? = null
    /** What a key just changed, said briefly over the video. */
    var notice by mutableStateOf<Notice?>(null)
    /** Takes every key while the first-play tutorial is up, so none reaches the player. */
    var onTutorialKey: ((Key) -> Unit)? = null
}

/** A [PlayerKeys.notice]. Each is its own object, so saying the same thing twice shows it again. */
class Notice(val text: String)

@Composable
fun PlayerScreen(
    app: DesktopApp,
    session: PlayerSession,
    keys: PlayerKeys,
    onBack: () -> Unit,
    onOpenVideo: (File) -> Unit,
) {
    val settings = app.settings
    val scope = rememberCoroutineScope()

    var next by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(session) { next = withContext(Dispatchers.IO) { runCatching { nextVideo(session.video) }.getOrNull() } }

    var subtitleStatus by remember { mutableStateOf<String?>("Reading subtitles…") }
    // shown in the panel header when a video's picture (PGS) subtitles couldn't be opened
    var readingNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) {
        val tracks = withContext(Dispatchers.IO) { runCatching { app.loadSubtitles(session.video) } }
        val text = tracks.getOrElse {
            app.logError("subtitles ${session.video}", it)
            subtitleStatus = "Couldn't read subtitles: ${it.message}"
            return@LaunchedEffect
        }
        session.setTracks(text)
        subtitleStatus = if (text.isEmpty()) "No text subtitles found for this video." else null

        // picture subtitles come in a moment later: their timing now, their text as they play
        val pictures = withContext(Dispatchers.IO) { runCatching { app.openPictureTracks(session.video, text) } }
        pictures.onSuccess { list ->
            if (list.isEmpty()) return@onSuccess
            session.setTracks(text + list)
            if (text.isEmpty()) subtitleStatus = null
        }.onFailure {
            app.logError("picture subtitles ${session.video}", it)
            readingNote = "Couldn't read picture subtitles"
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
        // within a line the last result stays up until the next arrives, so nothing blinks out between them
        if (lookup?.lineIndex != line || lookup?.text != text) lookup = ActiveLookup(line, text, start)
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
    val linesRead by session.linesRead.collectAsState()
    // a picture line's text arrives after the line does; this changes when it does
    val lineText = remember(primary, lineIndex, linesRead) { primary?.cues?.getOrNull(lineIndex)?.text }
    LaunchedEffect(lineIndex, lineText, hasDictionaries) {
        val cue = primary?.cues?.getOrNull(lineIndex)?.takeIf { it.text.isNotEmpty() } ?: return@LaunchedEffect
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
            videoName = app.settings.title(session.video.name),
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

    val videoHeight by session.player.videoHeight.collectAsState()
    val anime4kMode = settings.anime4kMode ?: Anime4K.modeFor(videoHeight)
    LaunchedEffect(settings.anime4kPreset, anime4kMode, settings.anime4kFast) {
        val shaders = withContext(Dispatchers.IO) {
            if (settings.anime4kPreset == Anime4K.Preset.OFF) emptyList() else Anime4K.shaders(anime4kMode, settings.anime4kFast)
        }
        session.player.setShaders(shaders)
    }

    val cardButton: (@Composable (TermEntry) -> Unit)? = if (settings.ankiEnabled) {
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
    } else null

    // with the panel put away, a word under the pointer in the line over the video opens its entries
    // over it, until the pointer leaves them, the line changes or they're closed. If opening them
    // paused the video, closing them plays it again.
    val panelHidden = settings.panelHidden
    var popupOpen by remember { mutableStateOf(false) }
    var resumeAfterPopup by remember { mutableStateOf(false) }
    LaunchedEffect(lineIndex, panelHidden) {
        popupOpen = false
        resumeAfterPopup = false
    }
    fun openPopup() {
        if (!popupOpen) resumeAfterPopup = settings.pauseOnLookup && !session.paused.value
        popupOpen = true
    }
    val closePopup = {
        popupOpen = false
        if (resumeAfterPopup) session.play()
        resumeAfterPopup = false
    }.takeIf { popupOpen }
    DisposableEffect(keys, closePopup != null) {
        keys.onDismiss = closePopup
        onDispose { keys.onDismiss = null }
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

    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().background(gutter).padding(gap)) {
        val totalWidth = maxWidth
        var panelFraction by remember { mutableFloatStateOf(settings.panelFraction.coerceIn(0.2f, 0.6f)) }
        Row(Modifier.fillMaxSize()) {
            VideoArea(
                app,
                session,
                keys,
                next = next,
                onPlayNext = { next?.let(onOpenVideo) },
                onBack = onBack,
                dismiss = closePopup,
                overlay = if (panelHidden) {
                    { controlsUp ->
                        LineOverVideo(
                            app = app,
                            session = session,
                            keys = keys,
                            lookup = lookup.takeIf { popupOpen },
                            hasDictionaries = hasDictionaries,
                            setupStatus = setupStatus,
                            controlsUp = controlsUp,
                            onClose = { closePopup?.invoke() },
                            onLookup = { line, text, start -> openPopup(); startLookup(line, text, start) },
                            onSelect = { line, text, start, end -> openPopup(); startLookup(line, text, start, end - start) },
                            cardButton = cardButton,
                        )
                    }
                } else null,
                modifier = Modifier.weight(1f).fillMaxHeight().clip(shape)
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
            // put away, the panel slides out to the right and the video takes its place
            AnimatedVisibility(
                !panelHidden,
                enter = expandHorizontally(tween(220), expandFrom = Alignment.Start, clip = false),
                exit = shrinkHorizontally(tween(220), shrinkTowards = Alignment.Start, clip = false),
            ) {
                Row(Modifier.fillMaxHeight()) {
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
                    StudyPanel(
                        app = app,
                        session = session,
                        keys = keys,
                        status = subtitleStatus,
                        note = readingNote,
                        lookup = lookup,
                        hasDictionaries = hasDictionaries,
                        setupStatus = setupStatus,
                        onBack = onBack,
                        onLookup = { line, text, start -> startLookup(line, text, start) },
                        onSelect = { line, text, start, end -> startLookup(line, text, start, end - start) },
                        cardButton = cardButton,
                        modifier = Modifier.width(totalWidth * panelFraction).fillMaxHeight().clip(shape),
                    )
                }
            }
        }
    }
    // the first video: how to work the player, over it, paused until it's done
    if (!settings.playerTutorialSeen) {
        LaunchedEffect(Unit) { session.pause() }
        PlayerTutorial(
            keys,
            ankiEnabled = settings.ankiEnabled,
            onFinish = {
                settings.updatePlayerTutorialSeen(true)
                session.play()
            },
        )
    }
    }
}

/** How long the controls stay after the mouse stops moving over the video. */
private const val CONTROLS_TIMEOUT_MS = 2000L
private const val NOTICE_MS = 1500L
/** The bar's height: one row. */
private val BAR_HEIGHT = 40.dp
/**
 * The video, with controls that show on mouse movement or while paused and otherwise get out of
 * the way. A stop at the end of a line isn't a pause the user asked for, so it leaves them hidden.
 *
 * With the panel put away (Full) the line sits where the controls were, and they keep out of the
 * way of it: the mouse moving shows only the pointer. All of them (the bar, the way back to the
 * library top right, and the traffic lights) come for the pointer anywhere outside [QuietArea]:
 * a band along the top, down the sides, and under the line, widening into the bottom corners.
 * They stay while it's there or on them, and go once it's back in the quiet area.
 *
 * [overlay] is drawn over it all, told whether the controls are up; while [dismiss] is set, a
 * click on the video calls it instead of playing or pausing.
 */
@Composable
private fun VideoArea(
    app: DesktopApp,
    session: PlayerSession,
    keys: PlayerKeys,
    next: File?,
    onPlayNext: () -> Unit,
    onBack: () -> Unit,
    dismiss: (() -> Unit)?,
    overlay: (@Composable BoxScope.(controlsUp: Boolean) -> Unit)?,
    modifier: Modifier,
) {
    val paused by session.paused.collectAsState()
    val stoppedAtLine by session.stoppedAtLine.collectAsState()
    val ended by session.ended.collectAsState()
    val currentDismiss by rememberUpdatedState(dismiss)
    val offerNext = ended && next != null
    DisposableEffect(offerNext) {
        keys.onPlayNext = onPlayNext.takeIf { offerNext }
        onDispose { keys.onPlayNext = null }
    }
    var lastMove by remember { mutableLongStateOf(0L) }
    var overControls by remember { mutableStateOf(false) }
    // a menu opened from the controls keeps them up: the pointer is over the menu, not them
    var menuOpen by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(lastMove) {
        moving = lastMove > 0
        delay(CONTROLS_TIMEOUT_MS)
        moving = false
    }
    val full = app.settings.panelHidden
    // Full: whether the pointer is outside the quiet area, which brings the controls up
    var atEdge by remember { mutableStateOf(false) }
    // a scrub keeps them up, wherever the drag goes
    var scrubbing by remember { mutableStateOf(false) }
    // a layout change takes the controls away under the pointer, so no Exit comes to say it's gone
    LaunchedEffect(full) {
        overControls = false
        menuOpen = false
        atEdge = false
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val visible = if (full) {
        atEdge || overControls || menuOpen || scrubbing
    } else {
        (paused && !stoppedAtLine) || moving || overControls || menuOpen || scrubbing
    }
    // the traffic lights are controls too
    LaunchedEffect(visible) { keys.chromeVisible = visible }
    DisposableEffect(Unit) { onDispose { keys.chromeVisible = true } }
    Box(
        modifier.background(Color.Black)
            .onSizeChanged { size = it }
            .onPointerEvent(PointerEventType.Move) { event ->
                lastMove = System.nanoTime()
                if (!app.settings.panelHidden) return@onPointerEvent
                val at = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                if (size.width == 0 || size.height == 0) return@onPointerEvent
                atEdge = !QuietArea.contains(at.x / size.width, at.y / size.height)
            }
            // left as it is when the pointer leaves: over the traffic lights it has left the content
            // too, and past the window's edge it's still on its way to the controls
            .onPointerEvent(PointerEventType.Exit) { moving = false }
            .pointerHoverIcon(if (visible || moving) PointerIcon.Default else HiddenCursor)
            .pointerInput(session) {
                detectTapGestures(
                    onTap = { currentDismiss?.invoke() ?: session.togglePause() },
                    onDoubleTap = { keys.onToggleFullscreen() },
                )
            },
    ) {
        VideoSurface(session.player, Modifier.fillMaxSize())
        val notice = keys.notice
        var showNotice by remember { mutableStateOf(false) }
        LaunchedEffect(notice) {
            if (notice == null) return@LaunchedEffect
            showNotice = true
            delay(NOTICE_MS)
            showNotice = false
        }
        AnimatedVisibility(
            showNotice,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
        ) {
            Text(
                notice?.text.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        AnimatedVisibility(
            offerNext,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            next?.let { NextEpisode(app.settings.title(it.name), onPlayNext) }
        }
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { overControls = true }
                .onPointerEvent(PointerEventType.Exit) { overControls = false },
        ) {
            Controls(app, session, keys, onMenu = { menuOpen = it }, onScrubbing = { scrubbing = it })
        }
        // over the bar, so the line stays hoverable where the bar's shading reaches up behind it
        overlay?.invoke(this, visible)
        // nothing marks it, but where the app draws the window's frame the video's top moves the
        // window, as a title bar would; under the Library button, so that still takes its clicks
        if (ownsFrame) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(HeaderHeight).movesWindow())
        // with the panel put away, so is its header: the way back to the library comes top right,
        // clear of the traffic lights, or top left where the window's own buttons hold the right
        AnimatedVisibility(
            visible && full,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(if (ownsFrame) Alignment.TopStart else Alignment.TopEnd)
                .padding(top = 8.dp, start = 12.dp, end = 8.dp)
                .onPointerEvent(PointerEventType.Enter) { overControls = true }
                .onPointerEvent(PointerEventType.Exit) { overControls = false },
        ) {
            TextAction(
                "‹ Library",
                onClick = onBack,
                color = Color.White,
                hoverColor = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(shadow = Shadow(Color.Black, Offset(0f, 1f), blurRadius = 4f)),
            )
        }
    }
}

/** Offered over the last frame: the next video in the folder, a click (or Return) away. */
@Composable
private fun NextEpisode(title: String, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier.padding(24.dp).widthIn(max = 420.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(if (hovered) 0xE6000000 else 0xCC000000))
            .hoverable(interaction)
            // consumes the click, so it doesn't reach the video and toggle pause
            .clickable(interaction, indication = null, onClick = onPlay)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Next episode", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "▶",
                style = MaterialTheme.typography.titleMedium,
                color = if (hovered) MaterialTheme.colorScheme.primary else Color.White,
            )
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

/**
 * The bar along the bottom of the video, one compact row and the same in both layouts: play,
 * the time, the seek bar across the middle, then the line buttons, the menus and the panel, and
 * off macOS (where the green traffic light does it) full screen. [onScrubbing] tells while a
 * scrub is under way.
 */
@Composable
private fun Controls(
    app: DesktopApp,
    session: PlayerSession,
    keys: PlayerKeys,
    onMenu: (Boolean) -> Unit,
    onScrubbing: (Boolean) -> Unit,
) {
    val position by session.position.collectAsState()
    val duration by session.duration.collectAsState()
    val paused by session.paused.collectAsState()
    var scrubbing by remember { mutableStateOf<Double?>(null) }
    val white = Color.White
    val muted = white.copy(alpha = 0.85f)
    // the bar is dark in every theme, so a light theme's accent (dark green in Jade) would barely show
    val accent = app.settings.themeFamily.dark?.scheme?.primary ?: white
    Row(
        Modifier.fillMaxWidth().height(BAR_HEIGHT)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextAction(
            if (paused) "▶" else "❚❚",
            onClick = session::togglePause,
            color = white,
            style = MaterialTheme.typography.titleMedium,
            hoverColor = white,
        )
        Text(
            "${formatTime(scrubbing ?: position)} / ${formatTime(duration)}",
            style = MaterialTheme.typography.labelMedium,
            color = muted,
        )
        Box(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Scrubber(
                position = scrubbing ?: position,
                duration = duration,
                onScrub = {
                    scrubbing = it
                    onScrubbing(true)
                },
                onScrubEnd = {
                    scrubbing?.let(session::seekTo)
                    scrubbing = null
                    onScrubbing(false)
                },
            )
        }
        LineButtons(app, session, color = muted, hoverColor = white)
        LineStopButton(app, session, color = muted, hoverColor = white, accent = accent)
        TrackMenu(app.settings, session, onMenu, color = muted, hoverColor = white)
        ShaderMenu(app, session, onMenu, accent)
        PanelButton(app, color = muted, hoverColor = white)
        if (!isMac) FullscreenButton(keys, color = muted, hoverColor = white)
    }
}

/** Anime4K's preset, and its mode under Advanced: the same settings as Settings › Appearance › Shaders. */
@Composable
private fun ShaderMenu(app: DesktopApp, session: PlayerSession, onMenu: (Boolean) -> Unit, accent: Color) {
    val settings = app.settings
    val videoHeight by session.player.videoHeight.collectAsState()
    var open by remember { mutableStateOf(false) }
    // the modes stay folded away unless one has been picked
    var advanced by remember { mutableStateOf(false) }
    fun show(value: Boolean) {
        open = value
        onMenu(value)
    }
    Box {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(if (hovered || open) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                .hoverable(interaction)
                .clickable(interaction, indication = null) { show(true) }
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics { contentDescription = "Shaders" }
                .padding(5.dp),
        ) {
            ShaderIcon(if (settings.anime4kPreset != Anime4K.Preset.OFF) accent else Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { show(false) }) {
            @Composable
            /** [note], if any, follows the label muted: what the item stands for, not its name. */
            fun item(label: String, selected: Boolean, note: String? = null, onClick: () -> Unit) = DropdownMenuItem(
                text = {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        buildAnnotatedString {
                            append(label)
                            if (note != null) withStyle(SpanStyle(color = muted)) { append(" · $note") }
                        },
                        maxLines = 1,
                    )
                },
                leadingIcon = { Text(if (selected) "✓" else "", Modifier.width(12.dp)) },
                onClick = { onClick(); show(false) },
            )
            MenuHeading("Anime4K")
            if (!session.player.runsShaders) MenuHeading("Needs OpenGL, which isn't available here")
            Anime4K.Preset.entries.forEach { preset ->
                // Auto says which of Fast and Best it went with
                val note = if (preset == Anime4K.Preset.AUTO) {
                    if (Anime4K.fast(preset, Anime4K.hardware)) Anime4K.Preset.FAST.label else Anime4K.Preset.BEST.label
                } else null
                item(preset.label, settings.anime4kPreset == preset, note) { settings.updateAnime4kPreset(preset) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            DropdownMenuItem(
                text = { Text("Advanced", maxLines = 1) },
                leadingIcon = { Text(if (advanced || settings.anime4kMode != null) "▾" else "▸", Modifier.width(12.dp)) },
                // opens in place, so the menu stays up
                onClick = { advanced = !advanced },
            )
            if (advanced || settings.anime4kMode != null) {
                MenuHeading("Mode")
                item("By video (${Anime4K.modeFor(videoHeight).label})", settings.anime4kMode == null) { settings.updateAnime4kMode(null) }
                Anime4K.Mode.entries.forEach { mode ->
                    item(mode.label, settings.anime4kMode == mode) { settings.updateAnime4kMode(mode) }
                }
            }
        }
    }
}

@Composable
fun VideoSurface(player: MpvPlayer, modifier: Modifier = Modifier) {
    val frame by player.frame.collectAsState()
    val stats = remember { FrameStats.takeIf { System.getProperty("mizune.stats") != null } }
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
    note: String?,
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
    val linesRead by session.linesRead.collectAsState()
    var mouseHold by remember { mutableStateOf(false) }

    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxLineHeight = maxHeight * 0.4f
            Column(Modifier.fillMaxSize()) {
                PanelHeader(app, session, note, onBack)
                CurrentLine(
                    track = primary,
                    secondary = secondary,
                    lineIndex = lineIndex,
                    lineActive = lineActive,
                    linesRead = linesRead,
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PanelFooter(app, session)
            }
        }
    }
}

/**
 * Back to the library, what Anki is doing (or [note], while picture subtitles are being read),
 * the subtitle offset when it isn't zero, and the track menu.
 */
@Composable
private fun PanelHeader(app: DesktopApp, session: PlayerSession, note: String?, onBack: () -> Unit) {
    val offset by session.offset.collectAsState()
    val ankiStatus by app.anki.status.collectAsState()
    // same height as the other headers, so it lines up with the traffic lights across the window
    Row(
        // the panel runs to the window's right edge, under the window's buttons where the app draws them
        Modifier.fillMaxWidth().height(HeaderHeight).movesWindow().padding(start = 8.dp, end = 8.dp + HeaderEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextAction("‹ Library", onClick = onBack)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            } else {
                AnkiStatusText(ankiStatus)
            }
        }
        if (offset != 0.0) {
            Text(
                "%+.1f s".format(offset),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        // the play bar has the same menu; where the window's buttons end the header, it'd sit
        // oddly beside them, so only macOS keeps it here
        if (!ownsFrame) TrackMenu(app.settings, session)
    }
}

/**
 * The cog: corrects the automatic audio and subtitle choices for this video, which remembers them,
 * and fits the picture or fills the area with it (Z, or ⌘ + scroll).
 */
@Composable
private fun TrackMenu(
    settings: Settings,
    session: PlayerSession,
    onMenu: (Boolean) -> Unit = {},
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val audioTracks by session.audioTracks.collectAsState()
    val audioTrack by session.audioTrack.collectAsState()
    val tracks by session.tracks.collectAsState()
    val primary by session.primary.collectAsState()
    val secondary by session.secondary.collectAsState()
    var open by remember { mutableStateOf(false) }
    fun show(value: Boolean) {
        open = value
        onMenu(value)
    }
    Box {
        IconAction(
            onClick = { show(true) },
            modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = "Tracks" },
            color = color,
            hoverColor = hoverColor,
        ) { Cog(it) }
        DropdownMenu(expanded = open, onDismissRequest = { show(false) }) {
            @Composable
            fun item(label: String, selected: Boolean, onClick: () -> Unit) = DropdownMenuItem(
                text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Text(if (selected) "✓" else "", Modifier.width(12.dp)) },
                onClick = { onClick(); show(false) },
            )
            @Composable
            fun divider() = HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            MenuHeading("Picture · Z switches")
            item("Fit the whole picture", !settings.videoFill) { settings.updateVideoFill(false) }
            item("Fill the area, cropping the edges", settings.videoFill) { settings.updateVideoFill(true) }
            if (audioTracks.isNotEmpty()) {
                divider()
                MenuHeading("Audio")
                audioTracks.forEach { track -> item(track.label, track.id == audioTrack) { session.selectAudio(track) } }
            }
            if (tracks.isEmpty()) return@DropdownMenu
            divider()
            MenuHeading("Subtitles to study")
            tracks.forEach { track -> item(trackLabel(track), track === primary) { session.selectPrimary(track) } }
            divider()
            MenuHeading("Translation (hold to peek)")
            item("None", secondary == null) { session.selectSecondary(null) }
            tracks.filter { it !== primary }.forEach { track ->
                item(trackLabel(track), track === secondary) { session.selectSecondary(track) }
            }
        }
    }
}

@Composable
private fun MenuHeading(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
)

private fun trackLabel(track: SubtitleTrack): String {
    val language = track.language?.let { " · $it" }.orEmpty()
    return "${track.name}$language · ${track.cues.size} lines"
}

@Composable
private fun CurrentLine(
    track: SubtitleTrack?,
    secondary: SubtitleTrack?,
    lineIndex: Int,
    lineActive: Boolean,
    /** Goes up as picture lines are read, whose text (and translation) then needs showing. */
    linesRead: Int,
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
        val cue = remember(track, lineIndex, linesRead) { track.cues.getOrNull(lineIndex) }
        if (cue == null) {
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Box
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            val highlight = lookup?.takeIf { it.lineIndex == lineIndex && it.result != null && it.result.matchLength > 0 }
                ?.let { it.start until it.start + it.result!!.matchLength }
            // the Japanese and its English are both laid out all the time, one over the other, so the
            // line gets room for whichever is bigger and holding to peek only fades between them
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
            // with no English to show, saying so only while peeking
            val english = translation ?: (if (secondary == null) {
                "No subtitles in your peek language. Pick a translation track under ⚙."
            } else {
                "Nothing here in your peek language."
            }).takeIf { peeking }
            if (english != null) {
                val englishAlpha by animateFloatAsState(if (peeking) 1f else 0f, label = "englishAlpha")
                BasicText(
                    english,
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
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = englishAlpha },
                )
            }
        }
    }
}

/**
 * What happens when the line playing ends, from the stop-at-end setting and the keys pressed
 * since, as the stop button's hint says it.
 */
private enum class LineStop(val hint: String) {
    EVERY_LINE("Stopping at the end of each line"),
    HELD("Stops held off by H or L: click to stop again"),
    THIS_LINE("Stopping at the end of this line"),
    NONE("Stop at the end of each line"),
}

@Composable
private fun lineStop(app: DesktopApp, session: PlayerSession): LineStop {
    val held by session.stopsHeld.collectAsState()
    val once by session.stopsOnce.collectAsState()
    return when {
        app.settings.autoPause && !held -> LineStop.EVERY_LINE
        app.settings.autoPause -> LineStop.HELD
        once -> LineStop.THIS_LINE
        else -> LineStop.NONE
    }
}

/** Under the panel: the line buttons and the stop-at-end button. */
@Composable
private fun PanelFooter(app: DesktopApp, session: PlayerSession) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LineButtons(app, session)
        LineStopButton(app, session)
    }
}

/**
 * Back a line, replay it, on a line. Back and on keep to what the stop button says, so they never
 * change whether playback stops; replay stops at the line's end, as ; does.
 */
@Composable
private fun LineButtons(
    app: DesktopApp,
    session: PlayerSession,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val stopping = lineStop(app, session) == LineStop.EVERY_LINE
    Row(verticalAlignment = Alignment.CenterVertically) {
        Hint("Previous line", "H, or J to stop at its end") {
            IconAction({ session.previousLine(stopAtEnd = stopping) }, color = color, hoverColor = hoverColor) {
                LineSkipIcon(it, forward = false)
            }
        }
        Hint("Replay this line", ";") {
            IconAction({ session.replayLine(stopAtEnd = true) }, color = color, hoverColor = hoverColor) { ReplayIcon(it) }
        }
        Hint("Next line", "L, or K to stop at its end") {
            IconAction({ session.nextLine(stopAtEnd = stopping) }, color = color, hoverColor = hoverColor) {
                LineSkipIcon(it, forward = true)
            }
        }
    }
}

/**
 * Stop at the end of every line, on or off. Lit while playback will stop at a line's end, faintly
 * while the setting is held off by H or L (a click then stops again); its hint says which.
 */
@Composable
private fun LineStopButton(
    app: DesktopApp,
    session: PlayerSession,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface,
    /** The colour while it's on: the theme's own, unless it sits somewhere dark. */
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val stop = lineStop(app, session)
    Hint(stop.hint, "U") {
        IconAction(
            { session.setAutoPause(stop != LineStop.EVERY_LINE) },
            modifier = Modifier
                .background(if (stop == LineStop.EVERY_LINE) accent.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(6.dp))
                .semantics { contentDescription = stop.hint },
            color = when (stop) {
                LineStop.EVERY_LINE, LineStop.THIS_LINE -> accent
                LineStop.HELD -> accent.copy(alpha = 0.5f)
                LineStop.NONE -> color
            },
            hoverColor = if (stop == LineStop.NONE) hoverColor else accent,
        ) { LineStopIcon(it) }
    }
}

/** Puts the study panel away (Full), or brings it back. */
@Composable
private fun PanelButton(app: DesktopApp, color: Color, hoverColor: Color) {
    val hidden = app.settings.panelHidden
    val hint = if (hidden) "Bring the panel back" else "Put the panel away"
    Hint(hint, "S") {
        IconAction(
            { app.settings.updatePanelHidden(!hidden) },
            modifier = Modifier.semantics { contentDescription = hint },
            color = color,
            hoverColor = hoverColor,
        ) { PanelIcon(it, shown = !hidden) }
    }
}

@Composable
private fun FullscreenButton(keys: PlayerKeys, color: Color, hoverColor: Color) {
    val hint = if (keys.fullscreen) "Leave full screen" else "Full screen"
    Hint(hint, "F") {
        IconAction(
            { keys.onToggleFullscreen() },
            modifier = Modifier.semantics { contentDescription = hint },
            color = color,
            hoverColor = hoverColor,
        ) { FullscreenIcon(it, full = keys.fullscreen) }
    }
}

/** What a control does and its key, shown after the pointer rests on it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hint(text: String, key: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp) {
                Text(
                    buildAnnotatedString {
                        append(text)
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.65f))) { append("  $key") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.TopCenter,
            alignment = Alignment.TopCenter,
            offset = DpOffset(0.dp, (-6).dp),
        ),
    ) { content() }
}

/**
 * With the panel put away, the line goes over the video, as subtitles do: the word under the
 * pointer (or clicked, or dragged across) opens its [lookup] above the line, which closes as soon
 * as the pointer leaves both. The line shows while it's spoken, while paused, and while a lookup
 * is open. It sits where the bar would be, and rises above it while that's up.
 */
@Composable
private fun BoxScope.LineOverVideo(
    app: DesktopApp,
    session: PlayerSession,
    keys: PlayerKeys,
    lookup: ActiveLookup?,
    hasDictionaries: Boolean,
    setupStatus: String?,
    controlsUp: Boolean,
    onClose: () -> Unit,
    onLookup: (Int, String, Int) -> Unit,
    onSelect: (Int, String, Int, Int) -> Unit,
    cardButton: (@Composable (TermEntry) -> Unit)?,
) {
    val primary by session.primary.collectAsState()
    val secondary by session.secondary.collectAsState()
    val lineIndex by session.lineIndex.collectAsState()
    val lineActive by session.lineActive.collectAsState()
    val linesRead by session.linesRead.collectAsState()
    val paused by session.paused.collectAsState()
    var mouseHold by remember { mutableStateOf(false) }
    var overLine by remember { mutableStateOf(false) }
    var overLookup by remember { mutableStateOf(false) }
    val pointedAt = overLine || overLookup
    // gone as soon as the pointer is: the gap up to the entries counts as theirs, so crossing it
    // never leaves both
    LaunchedEffect(lookup != null, pointedAt) {
        if (lookup != null && !pointedAt) onClose()
    }
    // it rises above the bar while that's up, but never moves out from under the pointer or its
    // entries: there it waits for them to be left
    var raised by remember { mutableStateOf(controlsUp) }
    val still = pointedAt || lookup != null
    LaunchedEffect(controlsUp, still) { if (!still) raised = controlsUp }
    val cue = remember(primary, lineIndex, linesRead) { primary?.cues?.getOrNull(lineIndex) }
    val textSize = app.settings.subtitleSize

    BoxWithConstraints(Modifier.matchParentSize()) {
        // at rest, clear of the strip under it that brings the controls up (see QuietArea)
        val rest = maxOf(32.dp, maxHeight * QuietArea.LINE_REST)
        val lift by animateDpAsState(if (raised) maxOf(rest, BAR_HEIGHT + 16.dp) else rest, tween(300), label = "lineLift")
        val lookupHeight = minOf(360.dp, maxHeight * 0.5f)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = lift),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (lookup != null) {
                Box(
                    Modifier.widthIn(max = 460.dp).fillMaxWidth()
                        .onPointerEvent(PointerEventType.Enter) { overLookup = true }
                        .onPointerEvent(PointerEventType.Exit) { overLookup = false }
                        .padding(bottom = 12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().height(lookupHeight)
                            // clicks inside stay inside, rather than reaching the video and closing it
                            .pointerInput(Unit) { detectTapGestures {} },
                    ) {
                        DictionaryPanel(
                            lookup = lookup,
                            hasDictionaries = hasDictionaries,
                            setupStatus = setupStatus,
                            noDictionariesMessage = "No dictionaries yet. Import a Yomitan dictionary under Settings.",
                            nothingFoundMessage = "Nothing to look up here. Point at another word to try another spot.",
                            entryAction = cardButton,
                        )
                    }
                }
            }
            if (cue != null && cue.text.isNotEmpty() && (lineActive || paused || lookup != null)) {
                val peeking = keys.peeking || mouseHold
                val translation = secondary?.translationFor(cue.start, cue.end)
                val shadow = Shadow(Color.Black, Offset(0f, 2f), blurRadius = 6f)
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x99000000))
                        .onPointerEvent(PointerEventType.Enter) { overLine = true }
                        .onPointerEvent(PointerEventType.Exit) { overLine = false }
                        // a click beside the words is still on the line, not the video
                        .pointerInput(Unit) { detectTapGestures {} }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val highlight = lookup?.takeIf { it.lineIndex == lineIndex && it.result != null && it.result.matchLength > 0 }
                        ?.let { it.start until it.start + it.result!!.matchLength }
                    val japaneseAlpha by animateFloatAsState(if (peeking) 0f else 1f, label = "japaneseAlpha")
                    TappableText(
                        text = cue.text,
                        highlight = highlight,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = textSize.sp,
                            lineHeight = 1.3.em,
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            shadow = shadow,
                        ),
                        onTap = { onLookup(lineIndex, cue.text, it) },
                        onHold = { mouseHold = it },
                        onSelect = { start, end -> onSelect(lineIndex, cue.text, start, end) },
                        onHover = { index -> if (index != null && !peeking) onLookup(lineIndex, cue.text, index) },
                        modifier = Modifier.graphicsLayer { alpha = japaneseAlpha },
                    )
                    // while peeking, the Japanese stays laid out (invisible) under the English, and the
                    // line's backing grows to fit whichever is bigger
                    if (peeking) {
                        BasicText(
                            translation ?: if (secondary == null) "No subtitles in your peek language" else "Nothing here in your peek language",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = if (translation != null) Color.White else Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                shadow = shadow,
                            ),
                            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 22.sp, stepSize = 1.sp),
                        )
                    }
                }
            }
        }
    }
}

/** -Dmizune.stats: prints frames drawn per second, to check nothing is dropped between mpv and Compose. */
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
