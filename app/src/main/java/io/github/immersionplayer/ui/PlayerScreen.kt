package io.github.immersionplayer.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import io.github.immersionplayer.App
import io.github.immersionplayer.dictionary.BundledDictionaries
import io.github.immersionplayer.dictionary.LookupResult
import io.github.immersionplayer.dictionary.TermEntry
import io.github.immersionplayer.mining.MiningCard
import io.github.immersionplayer.player.MpvView
import io.github.immersionplayer.player.PlayerSession
import io.github.immersionplayer.subs.SubtitleLoader
import io.github.immersionplayer.subs.SubtitleTrack
import kotlinx.coroutines.Dispatchers
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
    val lineIndex by session.lineIndex.collectAsState()
    val lineActive by session.lineActive.collectAsState()
    val paused by session.paused.collectAsState()
    val autoPause by session.autoPause.collectAsState()
    val mpvSubtitle by session.mpvSubtitle.collectAsState()

    var lookup by remember { mutableStateOf<ActiveLookup?>(null) }
    var showTranslation by remember { mutableStateOf(app.prefs.showTranslation) }
    var hasDictionaries by remember { mutableStateOf(true) }
    val dictionarySetup by BundledDictionaries.status.collectAsState()
    LaunchedEffect(dictionarySetup) {
        hasDictionaries = withContext(Dispatchers.IO) { app.dictionaryDatabase.dictionaries().any { it.enabled } }
    }

    fun startLookup(line: Int, text: String, start: Int) {
        if (app.prefs.pauseOnLookup) session.pause()
        val pending = ActiveLookup(line, text, start)
        lookup = pending
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { app.lookup.lookup(text, start) }.getOrNull() }
            if (lookup == pending) lookup = pending.copy(result = result)
        }
    }

    fun mine(entry: TermEntry, line: Int) {
        val cue = primary?.cues?.getOrNull(line) ?: return
        val card = MiningCard(
            expression = entry.expression,
            reading = entry.reading,
            glossary = entry.definitions.firstOrNull()?.let { Glossary.plainText(it.glossaryJson) }.orEmpty(),
            sentence = cue.text,
            translation = secondary?.let { translationFor(it, cue.start, cue.end) },
            videoUri = session.videoUri,
            videoName = session.videoName,
            sentenceStart = cue.start,
            sentenceEnd = cue.end,
        )
        scope.launch(Dispatchers.IO) { app.miningStore.export(card) }
        Toast.makeText(context, "Saved ${entry.expression}", Toast.LENGTH_SHORT).show()
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight

            val left: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier) {
                    VideoArea(
                        session = session,
                        videoUri = session.videoUri,
                        startPosition = app.prefs.position(session.videoUri),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    CurrentLine(
                        track = primary,
                        secondary = secondary,
                        lineIndex = lineIndex,
                        lineActive = lineActive,
                        status = subtitleStatus,
                        showTranslation = showTranslation,
                        lookup = lookup,
                        textSize = app.prefs.subtitleSize,
                        onCharTap = ::startLookup,
                    )
                    Controls(
                        session = session,
                        paused = paused,
                        autoPause = autoPause,
                        showTranslation = showTranslation,
                        hasTranslation = secondary != null,
                        mpvSubtitle = mpvSubtitle,
                        onToggleTranslation = {
                            showTranslation = !showTranslation
                            app.prefs.showTranslation = showTranslation
                        },
                        onBack = onBack,
                    )
                }
            }

            val right: @Composable (Modifier) -> Unit = { modifier ->
                Surface(modifier, color = MaterialTheme.colorScheme.surface) {
                    val current = lookup
                    if (current != null) {
                        DictionaryPanel(
                            lookup = current,
                            hasDictionaries = hasDictionaries,
                            setupStatus = dictionarySetup,
                            onClose = { lookup = null },
                            onMine = { mine(it, current.lineIndex) },
                            isMined = { entry ->
                                val cue = primary?.cues?.getOrNull(current.lineIndex)
                                cue != null && app.miningStore.contains(entry.expression, cue.text)
                            },
                        )
                    } else {
                        TranscriptPanel(
                            track = primary,
                            lineIndex = lineIndex,
                            status = subtitleStatus,
                            onPlayLine = session::playLine,
                            onCharTap = ::startLookup,
                        )
                    }
                }
            }

            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    left(Modifier.weight(0.62f).fillMaxHeight())
                    VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    right(Modifier.weight(0.38f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    left(Modifier.weight(0.55f).fillMaxWidth())
                    right(Modifier.weight(0.45f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun VideoArea(session: PlayerSession, videoUri: String, startPosition: Double, modifier: Modifier) {
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var mpvView by remember { mutableStateOf<MpvView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            session.detach()
            mpvView?.destroy()
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MpvView(ctx).also { view ->
                    view.keepScreenOn = true
                    view.initialize(startPosition)
                    session.attach(view)
                    view.playFile(videoUri)
                    mpvView = view
                }
            },
        )
        // gesture layer above the video surface: tap = play/pause, swipe = previous/next line
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { session.togglePause() })
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, amount -> total += amount },
                        onDragEnd = {
                            if (abs(total) > swipeThreshold) {
                                if (total > 0) session.previousLine() else session.nextLine()
                            }
                        },
                    )
                },
        )
    }
}

@Composable
private fun CurrentLine(
    track: SubtitleTrack?,
    secondary: SubtitleTrack?,
    lineIndex: Int,
    lineActive: Boolean,
    status: String?,
    showTranslation: Boolean,
    lookup: ActiveLookup?,
    textSize: Float,
    onCharTap: (Int, String, Int) -> Unit,
) {
    val cue = track?.cues?.getOrNull(lineIndex)
    var revealed by remember(lineIndex) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0C0E))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            status != null -> Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            cue == null -> Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val highlight = lookup?.takeIf { it.lineIndex == lineIndex && it.result != null }
                    ?.let { it.start until it.start + it.result!!.matchLength }
                TappableText(
                    text = cue.text,
                    highlight = highlight,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = textSize.sp,
                        lineHeight = (textSize * 1.35f).sp,
                        textAlign = TextAlign.Center,
                        color = if (lineActive) Color.White else Color(0xFFB0B4BA),
                    ),
                    onTap = { onCharTap(lineIndex, cue.text, it) },
                )
                val translation = secondary?.let { translationFor(it, cue.start, cue.end) }
                if (translation != null) {
                    if (showTranslation || revealed) {
                        Text(
                            translation,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clickable { revealed = false },
                        )
                    } else {
                        Text(
                            "tap for translation",
                            color = Color(0xFF5F6368),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable { revealed = true }.padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(
    session: PlayerSession,
    paused: Boolean,
    autoPause: Boolean,
    showTranslation: Boolean,
    hasTranslation: Boolean,
    mpvSubtitle: String?,
    onToggleTranslation: () -> Unit,
    onBack: () -> Unit,
) {
    val position by session.position.collectAsState()
    val duration by session.duration.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }

    Column(Modifier.fillMaxWidth().background(Color(0xFF0B0C0E)).padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(dragging?.toDouble() ?: position), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = (dragging ?: position.toFloat()).coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { session.seekTo(it.toDouble()) }
                    dragging = null
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        // scrolls sideways rather than wrapping when the panel is narrow
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val compact = PaddingValues(horizontal = 14.dp)
            TextButton(onClick = onBack, contentPadding = compact) { Text("✕") }
            FilledTonalButton(onClick = session::previousLine, contentPadding = compact) { Text("◁ Prev") }
            FilledTonalButton(onClick = session::replayLine, contentPadding = compact) { Text("↻") }
            FilledTonalButton(onClick = session::togglePause, contentPadding = compact, modifier = Modifier.width(60.dp)) {
                Text(if (paused) "▶" else "❚❚")
            }
            FilledTonalButton(onClick = session::nextLine, contentPadding = compact) { Text("Next ▷") }
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = autoPause,
                onClick = { session.setAutoPause(!autoPause) },
                label = { Text("Stop at end", maxLines = 1) },
            )
            if (hasTranslation) {
                FilterChip(selected = showTranslation, onClick = onToggleTranslation, label = { Text("EN", maxLines = 1) })
            }
            FilterChip(
                selected = mpvSubtitle != null,
                onClick = session::cycleMpvSubtitles,
                label = {
                    Text(
                        if (mpvSubtitle == null) "mpv subs" else "mpv: $mpvSubtitle",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                },
            )
        }
    }
}

/** English text overlapping a Japanese line's time range. */
fun translationFor(track: SubtitleTrack, start: Double, end: Double): String? {
    val lines = track.cues.filter { it.start < end - 0.1 && it.end > start + 0.1 }
    return lines.joinToString("\n") { it.text }.ifEmpty { null }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
