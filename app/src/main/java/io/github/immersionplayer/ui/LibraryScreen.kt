package io.github.immersionplayer.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import io.github.immersionplayer.player.MpvOwner
import io.github.immersionplayer.player.ThumbnailGenerator
import androidx.compose.runtime.State
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import io.github.immersionplayer.App
import io.github.immersionplayer.dictionary.BundledDictionaries
import io.github.immersionplayer.library.NaturalOrder
import io.github.immersionplayer.library.episodeNumber
import io.github.immersionplayer.library.formatTime
import io.github.immersionplayer.library.isVideoName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun DocumentFile.isVideo(): Boolean = isFile && name?.let(::isVideoName) == true

private data class Listing(
    val folders: List<DocumentFile>,
    val videos: List<DocumentFile>,
    val all: List<DocumentFile>,
    val episodeCounts: Map<String, Int>,
)

private sealed interface Entry {
    data class Folder(val file: DocumentFile, val episodes: Int) : Entry
    data class Video(val file: DocumentFile) : Entry
}

@Composable
fun LibraryScreen(
    app: App,
    onOpenVideo: (DocumentFile, List<DocumentFile>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var treeUri by remember { mutableStateOf(app.prefs.libraryTreeUri) }
    val root = remember(treeUri) {
        treeUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it)) }.getOrNull() }
            ?.takeIf { it.canRead() }
    }
    var path by remember(root) { mutableStateOf(listOfNotNull(root)) }
    // reopen the folder you were last in (and don't save until that's done)
    var restoring by remember(root) { mutableStateOf(true) }
    LaunchedEffect(root) {
        val names = app.prefs.libraryPath?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
        if (root == null || names.isEmpty()) {
            restoring = false
            return@LaunchedEffect
        }
        val start: DocumentFile = root
        val restored = withContext(Dispatchers.IO) {
            var current = start
            val stack = mutableListOf(start)
            for (name in names) {
                current = current.findFile(name)?.takeIf { it.isDirectory } ?: break
                stack.add(current)
            }
            stack
        }
        path = restored
        restoring = false
    }
    LaunchedEffect(path, restoring) {
        if (!restoring && path.isNotEmpty()) {
            app.prefs.libraryPath = path.drop(1).joinToString("/") { it.name.orEmpty() }
        }
    }
    var listing by remember { mutableStateOf<Listing?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            app.prefs.libraryTreeUri = uri.toString()
            treeUri = uri.toString()
        }
    }

    BackHandler(enabled = path.size > 1) { path = path.dropLast(1) }

    val current = path.lastOrNull()
    LaunchedEffect(current) {
        listing = null
        if (current != null) listing = withContext(Dispatchers.IO) { list(current) }
    }

    // warm the cache so scrolling never waits on a decode
    LaunchedEffect(listing) {
        val videos = listing?.videos ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            videos.forEach { app.thumbnails.load(it.uri.toString()) }
        }
    }

    // fill in missing thumbnails while the library is open (libmpv is free then)
    LaunchedEffect(listing) {
        val videos = listing?.videos ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            for (video in videos) {
                val uri = video.uri.toString()
                val watched = app.prefs.position(uri)
                val takenAt = app.prefs.thumbnailPosition(uri)
                val wanted = if (watched > 0) watched else 0.0
                // make one if it's missing, or refresh it when you've watched further on
                val needed = !app.thumbnails.exists(uri) || kotlin.math.abs(takenAt - wanted) > 30.0
                if (!needed) continue
                if (!MpvOwner.tryAcquire("thumbnails")) return@withContext
                try {
                    val start = if (watched > 0) watched.toInt().toString() else "20%"
                    if (ThumbnailGenerator.generate(context, uri, app.thumbnails, start)) {
                        app.prefs.setThumbnailPosition(uri, wanted)
                    }
                } finally {
                    MpvOwner.release("thumbnails")
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { ThumbnailGenerator.requestAbort() }
    }

    val gridState = rememberLazyGridState()
    // controls hide as you scroll into the grid and come back when you scroll up
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }
    val controlsVisible = remember { mutableStateOf(true) }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset + 8)
                val scrollingUp = index < lastIndex || (index == lastIndex && offset < lastOffset - 8)
                if (scrollingDown && index > 0) controlsVisible.value = false
                if (scrollingUp) controlsVisible.value = true
                lastIndex = index
                lastOffset = offset
            }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                root == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Choose the folder that holds your shows, e.g. Movies/Immersion.")
                        Button(onClick = { pickFolder.launch(null) }) { Text("Choose folder") }
                    }
                }
                listing == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    val entries = listing!!.let { l ->
                        l.folders.map { Entry.Folder(it, l.episodeCounts[it.uri.toString()] ?: 0) } +
                            l.videos.map { Entry.Video(it) }
                    }
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(200.dp),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 56.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(entries, key = {
                            when (it) {
                                is Entry.Folder -> it.file.uri.toString()
                                is Entry.Video -> it.file.uri.toString()
                            }
                        }) { entry ->
                            when (entry) {
                                is Entry.Folder -> FolderCard(entry) { path = path + entry.file }
                                is Entry.Video -> VideoCard(app, entry.file) {
                                    onOpenVideo(entry.file, listing!!.all)
                                }
                            }
                        }
                    }
                }
            }

            FolderChip(
                visible = controlsVisible,
                inFolder = path.size > 1,
                name = if (path.size > 1) path.last().name.orEmpty() else "Immersion Player",
                onUp = { path = path.dropLast(1) },
                modifier = Modifier.align(Alignment.TopStart),
            )
            LibraryMenu(
                visible = controlsVisible,
                hasFolder = root != null,
                onPickFolder = { pickFolder.launch(null) },
                onOpenSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.TopEnd),
            )

            val setupStatus by BundledDictionaries.status.collectAsState()
            setupStatus?.let {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 12.dp),
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    )
}

/** Current folder, floating over the grid; tapping it goes up. */
@Composable
private fun FolderChip(
    visible: State<Boolean>,
    inFolder: Boolean,
    name: String,
    onUp: () -> Unit,
    modifier: Modifier,
) {
    AnimatedVisibility(visible = visible.value, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .padding(start = 14.dp, top = 8.dp)
                .clickable(enabled = inFolder, onClick = onUp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (inFolder) {
                    Text("‹  ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryMenu(
    visible: State<Boolean>,
    hasFolder: Boolean,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    AnimatedVisibility(visible = visible.value, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.safeDrawingPadding().padding(end = 10.dp, top = 8.dp)) {
            val menuState = remember { MutableTransitionState(false) }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier.clickable { menuState.targetState = true },
            ) {
                Text(
                    "⋯",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            // kept mounted until the close animation finishes
            if (menuState.currentState || menuState.targetState) {
                val density = LocalDensity.current
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, with(density) { 42.dp.roundToPx() }),
                    onDismissRequest = { menuState.targetState = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    AnimatedVisibility(
                        visibleState = menuState,
                        enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.88f, transformOrigin = TransformOrigin(1f, 0f)),
                        exit = fadeOut(tween(90)) + scaleOut(tween(120), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 12.dp,
                        ) {
                            Column(Modifier.width(260.dp).padding(vertical = 6.dp)) {
                                MenuRow(if (hasFolder) "Change folder" else "Choose folder") {
                                    menuState.targetState = false
                                    onPickFolder()
                                }
                                MenuRow("Dictionaries & settings") {
                                    menuState.targetState = false
                                    onOpenSettings()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Current folder, floating over the grid; tapping it goes up. */
@Composable
private fun FolderCard(folder: Entry.Folder, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📁", fontSize = 22.sp, modifier = Modifier.width(38.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder.file.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (folder.episodes > 0) {
                    Text(
                        "${folder.episodes} ${if (folder.episodes == 1) "video" else "videos"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCard(app: App, video: DocumentFile, onClick: () -> Unit) {
    val uri = video.uri.toString()
    val position = app.prefs.position(uri)
    val duration = app.prefs.duration(uri)
    val progress = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    var thumbnail by remember(uri) { mutableStateOf(app.thumbnails.cached(uri)) }
    // reloads only when this video's thumbnail changes
    val version = app.thumbnails.versions[uri] ?: 0L
    LaunchedEffect(uri, version) {
        thumbnail = withContext(Dispatchers.IO) { app.thumbnails.load(uri) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
                val image = thumbnail
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // unwatched: a quiet placeholder with the episode number, if the name has one
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.surfaceVariant,
                                )
                            )
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            episodeNumber(video.name.orEmpty()) ?: "▶",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
                if (progress > 0f) {
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                            .background(Color(0x55000000)),
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(progress)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    video.name?.substringBeforeLast('.').orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        position > 0 && duration > 0 -> "${formatTime(position)} / ${formatTime(duration)}"
                        position > 0 -> "resume ${formatTime(position)}"
                        duration > 0 -> formatTime(duration)
                        else -> " "
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (position > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun list(folder: DocumentFile): Listing {
    val all = folder.listFiles().toList()
    val folders = all.filter { it.isDirectory }.sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
    val videos = all.filter { it.isVideo() }.sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
    // one level deep only: enough for "12 videos" under each show without a slow full scan
    val counts = folders.associate { child ->
        child.uri.toString() to runCatching { child.listFiles().count { it.isVideo() } }.getOrDefault(0)
    }
    return Listing(folders, videos, all, counts)
}
