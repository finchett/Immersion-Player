package io.github.immersionplayer.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immersionplayer.anki.AnkiConnect
import io.github.immersionplayer.dictionary.DictionaryCatalog
import io.github.immersionplayer.dictionary.DictionaryInfo
import io.github.immersionplayer.library.isVideoName
import io.github.immersionplayer.ui.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The first launch: a hello, then the few things worth choosing before the first video (a look,
 * the video folder, dictionaries, Anki), then a thank-you. Everything here can be changed later in
 * Settings, so every step can be skipped.
 */
@Composable
fun Onboarding(app: DesktopApp, onFinish: () -> Unit, start: OnboardingStep = OnboardingStep.Welcome) {
    var step by remember { mutableStateOf(start) }
    // the dictionaries ticked so far, kept while moving back and forth
    val picked = remember { mutableStateOf(DictionaryCatalog.ENTRIES.filter { it.recommended }.map { it.id }.toSet()) }
    val scheme = MaterialTheme.colorScheme
    fun go(to: OnboardingStep) { step = to }
    // what's installed already, so the button only offers to install what isn't
    val installs by app.catalogInstalls.collectAsState()
    var installed by remember { mutableStateOf<List<DictionaryInfo>>(emptyList()) }
    LaunchedEffect(step, installs) { installed = withContext(Dispatchers.IO) { app.database.dictionaries() } }

    // no header here, so the page itself moves the window where the app draws the frame
    Box(Modifier.fillMaxSize().background(scheme.background).movesWindow()) {
        Column(
            Modifier.fillMaxSize().padding(top = HeaderHeight, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepDots(step)
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val sign = if (forward) 1 else -1
                    (fadeIn(tween(260, delayMillis = 60)) + slideInHorizontally(tween(340)) { sign * it / 8 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(340)) { -sign * it / 8 })
                },
                label = "onboarding",
            ) { shown ->
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 580.dp).padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (shown) {
                            OnboardingStep.Welcome -> WelcomeStep(onStart = { go(OnboardingStep.Look) })
                            OnboardingStep.Look -> LookStep(app)
                            OnboardingStep.Videos -> VideosStep(app)
                            OnboardingStep.Dictionaries -> DictionariesStep(app, picked.value) { picked.value = it }
                            OnboardingStep.Anki -> AnkiStep(app)
                            OnboardingStep.Done -> DoneStep(app)
                        }
                    }
                }
            }
            if (step != OnboardingStep.Welcome) {
                Footer(
                    onBack = { go(OnboardingStep.entries[step.ordinal - 1]) },
                    next = when (step) {
                        OnboardingStep.Videos -> if (app.settings.libraryRoot == null) "Skip for now" else "Continue"
                        OnboardingStep.Dictionaries ->
                            if (toInstall(picked.value, installed, installs).isEmpty()) "Continue" else "Install and continue"
                        OnboardingStep.Anki -> if (app.settings.ankiEnabled) "Continue" else "Maybe later"
                        OnboardingStep.Done -> "Start watching"
                        else -> "Continue"
                    },
                    onNext = {
                        when (step) {
                            OnboardingStep.Dictionaries -> {
                                app.installFromCatalog(toInstall(picked.value, installed, installs))
                                go(OnboardingStep.Anki)
                            }
                            OnboardingStep.Done -> onFinish()
                            else -> go(OnboardingStep.entries[step.ordinal + 1])
                        }
                    },
                )
            }
        }
        if (step == OnboardingStep.Done) Confetti(Modifier.fillMaxSize())
    }
}

enum class OnboardingStep { Welcome, Look, Videos, Dictionaries, Anki, Done }

/** The ticked dictionaries not installed and not already on their way. */
private fun toInstall(picked: Set<String>, installed: List<DictionaryInfo>, installs: Map<String, DesktopApp.CatalogInstall>) =
    DictionaryCatalog.ENTRIES.filter {
        it.id in picked && DictionaryCatalog.installed(it, installed) == null && installs[it.id] == null
    }

// ---- the steps ----

@Composable
private fun ColumnScope.WelcomeStep(onStart: () -> Unit) {
    Mascot(170.dp)
    Spacer(Modifier.height(18.dp))
    Eyebrow("ようこそ")
    Headline("Welcome to Immersion Player")
    Spacer(Modifier.height(12.dp))
    Body(
        "Watch the shows you love in Japanese, look up any word with a click, and keep the ones " +
            "you want as flashcards. A minute of setting up and you're watching.",
    )
    Spacer(Modifier.height(30.dp))
    BigButton("Let's get started", onStart)
}

@Composable
private fun ColumnScope.LookStep(app: DesktopApp) {
    Eyebrow("Step 1 of 4")
    Headline("Pick a look")
    Spacer(Modifier.height(10.dp))
    Body("Your eyes, your call. Auto follows your computer's light and dark mode.")
    Spacer(Modifier.height(26.dp))
    ThemePicker(app.settings)
}

@Composable
private fun ColumnScope.VideosStep(app: DesktopApp) {
    val settings = app.settings
    val folder = settings.libraryRoot?.let(::File)
    var count by remember(folder) { mutableStateOf<Int?>(null) }
    LaunchedEffect(folder) { if (folder != null) count = withContext(Dispatchers.IO) { countVideos(folder) } }
    Eyebrow("Step 2 of 4")
    Headline("Where do your videos live?")
    Spacer(Modifier.height(10.dp))
    Body(
        "Pick the folder that holds your shows. Each folder inside becomes a series, and subtitles " +
            "sitting next to a video are found on their own.",
    )
    Spacer(Modifier.height(26.dp))
    val choose = {
        chooseFolder(folder)?.let { settings.updateLibraryRoot(it.path) }
        Unit
    }
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.fillMaxWidth().height(168.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.primary.copy(alpha = if (hovered) 0.09f else 0.05f))
            .dashedBorder(if (folder == null) scheme.primary.copy(alpha = 0.45f) else Color.Transparent)
            .border(1.dp, if (folder != null) scheme.primary.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(16.dp))
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = choose)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderIcon(scheme.primary, filled = folder != null)
            if (folder == null) {
                Text("Choose a folder", style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                Text("Anywhere on this computer or a drive", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            } else {
                Text(folder.name.ifEmpty { folder.path }, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(
                    when (val n = count) {
                        null -> "Looking for videos…"
                        0 -> "No videos in here yet, and that's fine"
                        1 -> "1 video found"
                        else -> "${if (n >= MAX_COUNT) "$MAX_COUNT+" else n} videos found"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text("Change…", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            }
        }
    }
}

@Composable
private fun ColumnScope.DictionariesStep(app: DesktopApp, picked: Set<String>, onPicked: (Set<String>) -> Unit) {
    var installed by remember { mutableStateOf<List<DictionaryInfo>>(emptyList()) }
    val setup by app.setupStatus.collectAsState()
    val installs by app.catalogInstalls.collectAsState()
    LaunchedEffect(setup, installs) { installed = withContext(Dispatchers.IO) { app.database.dictionaries() } }
    Eyebrow("Step 3 of 4")
    Headline("Choose your dictionaries")
    Spacer(Modifier.height(10.dp))
    Body("JMdict comes built in. These add more, downloaded straight from the people who make them.")
    Spacer(Modifier.height(24.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        DictionaryRow(
            name = "JMdict",
            detail = "The free Japanese–English dictionary everything else builds on.",
            badge = "Built in",
            trailing = { Status(setup ?: "Ready", done = setup == null) },
        )
        DictionaryCatalog.ENTRIES.forEach { entry ->
            Divider()
            val state = installs[entry.id]
            val already = DictionaryCatalog.installed(entry, installed) != null
            val ticked = entry.id in picked
            DictionaryRow(
                name = entry.name,
                detail = entry.description,
                badge = if (entry.recommended) "Recommended" else null,
                onClick = if (already || state != null) null else ({ onPicked(if (ticked) picked - entry.id else picked + entry.id) }),
                trailing = {
                    when {
                        already && state == null -> Status("Installed", done = true)
                        state != null -> InstallStatus(state)
                        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${entry.megabytes} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Check(ticked)
                        }
                    }
                },
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "More in Settings › Dictionaries, where any Yomitan dictionary can be added.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ColumnScope.AnkiStep(app: DesktopApp) {
    val settings = app.settings
    var attempt by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(attempt) {
        running = null
        running = withContext(Dispatchers.IO) { runCatching { AnkiConnect(settings.ankiUrl).version() }.isSuccess }
    }
    val scheme = MaterialTheme.colorScheme
    Eyebrow("Step 4 of 4 · Optional")
    Headline("Make flashcards with Anki")
    Spacer(Modifier.height(10.dp))
    Body(
        "Look up a word, press A, and it becomes an Anki card with the sentence, its audio and a " +
            "still from the scene.",
    )
    Spacer(Modifier.height(24.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surfaceVariant).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val dot = when (running) {
            true -> scheme.primary
            false -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
            null -> scheme.onSurfaceVariant.copy(alpha = 0.25f)
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when (running) {
                    true -> "Anki is open and ready"
                    false -> "Anki isn't answering"
                    null -> "Looking for Anki…"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                when (running) {
                    true -> if (settings.ankiEnabled) "Cards are on. Fields and deck are under Settings › Anki." else "Turn cards on to start making them."
                    else -> "Cards need Anki open with the AnkiConnect add-on. Or set it up later in Settings › Anki."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (running == true) {
            Toggle(settings.ankiEnabled, settings::updateAnkiEnabled)
        } else if (running == false) {
            TextAction("Check again", onClick = { attempt++ }, color = scheme.primary)
        }
    }
    Spacer(Modifier.height(14.dp))
    TextAction(
        "Get AnkiConnect ↗",
        onClick = { openInBrowser(ANKICONNECT_PAGE) },
        color = scheme.primary,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ColumnScope.DoneStep(app: DesktopApp) {
    val installs by app.catalogInstalls.collectAsState()
    val setup by app.setupStatus.collectAsState()
    val busy = setup != null || installs.values.any { it !is DesktopApp.CatalogInstall.Done && it !is DesktopApp.CatalogInstall.Failed }
    Mascot(150.dp)
    Spacer(Modifier.height(18.dp))
    Eyebrow("準備完了")
    Headline("You're all set!")
    Spacer(Modifier.height(12.dp))
    Body(
        "Thank you for giving Immersion Player a go. It's young and growing quickly, and new versions " +
            "arrive on their own, with a note on what's new." +
            if (busy) " Your dictionaries are still settling in; they'll be ready in a minute." else "",
    )
    Spacer(Modifier.height(24.dp))
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surfaceVariant).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tip(listOf("Space"), "Play and pause")
        Tip(listOf("←", "→"), "The previous or next line")
        Tip(listOf("A"), "Turn the word you looked up into an Anki card")
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Found a bug, or wish it did something? ", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        TextAction(
            "Tell us on GitHub ↗",
            onClick = { openInBrowser(ISSUES_PAGE) },
            color = scheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---- pieces ----

@Composable
private fun StepDots(step: OnboardingStep) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OnboardingStep.entries.forEach {
            val current = it == step
            val width by animateDpAsState(if (current) 22.dp else 7.dp, label = "dot")
            Box(
                Modifier.size(width, 7.dp).clip(CircleShape)
                    .background(if (it.ordinal <= step.ordinal) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.22f)),
            )
        }
    }
}

@Composable
private fun Footer(onBack: () -> Unit, next: String, onNext: () -> Unit) {
    Row(
        Modifier.widthIn(max = 580.dp).fillMaxWidth().padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextAction("‹  Back", onClick = onBack)
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, shape = RoundedCornerShape(10.dp), elevation = null) {
            Text(next, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(12.dp), elevation = null) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

/** The app's own girl, bobbing gently. */
@Composable
private fun Mascot(size: androidx.compose.ui.unit.Dp) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "bob",
    )
    Image(
        painterResource("icon.png"),
        contentDescription = null,
        modifier = Modifier.size(size).offset(y = (sin(bob * 2 * PI) * 5).dp),
    )
}

/** A burst of paper confetti in the theme's colours, falling once. */
@Composable
private fun Confetti(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary.copy(alpha = 0.6f))
    val pieces = remember {
        val random = Random(7)
        List(90) {
            Piece(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                drift = (random.nextFloat() - 0.5f) * 0.25f,
                spin = (random.nextFloat() - 0.5f) * 900f,
                color = it % 4,
                wide = random.nextBoolean(),
            )
        }
    }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(1f, tween(2600, easing = LinearEasing)) }
    Canvas(modifier) {
        val t = time.value
        pieces.forEach { piece ->
            val local = ((t - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val x = size.width * (piece.x + piece.drift * local)
            val y = size.height * (-0.1f + 1.15f * local * local)
            val alpha = if (local > 0.75f) (1f - local) / 0.25f else 1f
            val w = (if (piece.wide) 9f else 5f) * density
            val h = 6f * density
            rotate(piece.spin * local, Offset(x, y)) {
                drawRect(colors[piece.color].copy(alpha = alpha * colors[piece.color].alpha), Offset(x - w / 2, y - h / 2), Size(w, h))
            }
        }
    }
}

private class Piece(val x: Float, val delay: Float, val drift: Float, val spin: Float, val color: Int, val wide: Boolean)

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Headline(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
    )
}

@Composable
private fun DictionaryRow(
    name: String,
    detail: String,
    badge: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(if (hovered && onClick != null) scheme.onSurface.copy(alpha = 0.03f) else Color.Transparent)
            .then(
                if (onClick != null) Modifier.hoverable(interaction).clickable(interaction, indication = null, onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
                else Modifier,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (badge != null) {
                    Text(
                        badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(scheme.primary.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(MaterialTheme.colorScheme.hairline))
}

/** A round tick box. */
@Composable
private fun Check(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .background(if (checked) scheme.primary else Color.Transparent)
            .border(1.5.dp, if (checked) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
    }
}

@Composable
private fun Status(text: String, done: Boolean) {
    Text(
        (if (done) "✓  " else "") + text,
        style = MaterialTheme.typography.bodySmall,
        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 200.dp),
    )
}

@Composable
private fun InstallStatus(state: DesktopApp.CatalogInstall) {
    when (state) {
        DesktopApp.CatalogInstall.Queued -> Status("Waiting its turn…", done = false)
        is DesktopApp.CatalogInstall.Downloading -> Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Downloading", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { state.fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                drawStopIndicator = {},
            )
        }
        is DesktopApp.CatalogInstall.Importing -> Status("Setting up… ${"%,d".format(state.terms)}", done = false)
        DesktopApp.CatalogInstall.Done -> Status("Installed", done = true)
        is DesktopApp.CatalogInstall.Failed -> Text(
            "Couldn't install",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Tip(keys: List<String>, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.width(60.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { keys.forEach { Keycap(it) } }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A folder, outlined until one's chosen. */
@Composable
private fun FolderIcon(color: Color, filled: Boolean) {
    Canvas(Modifier.size(46.dp, 38.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.4f * density
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.18f)
            quadraticTo(0f, 0f, w * 0.08f, 0f)
            lineTo(w * 0.36f, 0f)
            lineTo(w * 0.46f, h * 0.14f)
            lineTo(w * 0.92f, h * 0.14f)
            quadraticTo(w, h * 0.14f, w, h * 0.28f)
            lineTo(w, h * 0.88f)
            quadraticTo(w, h, w * 0.92f, h)
            lineTo(w * 0.08f, h)
            quadraticTo(0f, h, 0f, h * 0.88f)
            close()
        }
        if (filled) drawPath(path, color.copy(alpha = 0.18f))
        drawPath(path, color, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawLine(color, Offset(0f, h * 0.3f), Offset(w, h * 0.3f), stroke)
    }
}

private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    if (color.alpha == 0f) return@drawBehind
    drawRoundRect(
        color,
        cornerRadius = CornerRadius(16.dp.toPx()),
        style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}

private fun countVideos(folder: File): Int =
    folder.walkTopDown().maxDepth(4).filter { it.isFile && isVideoName(it.name) }.take(MAX_COUNT).count()

private const val MAX_COUNT = 999
private const val ISSUES_PAGE = "https://github.com/finchett/Immersion-Player/issues"
private const val ANKICONNECT_PAGE = "https://ankiweb.net/shared/info/2055492159"
