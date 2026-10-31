package app.mizune.ui

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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import app.mizune.player.MpvOwner
import app.mizune.player.ThumbnailGenerator
import androidx.compose.runtime.MutableState
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
import app.mizune.App
import app.mizune.dictionary.BundledDictionaries
import app.mizune.library.NaturalOrder
import app.mizune.library.Titles
import androidx.compose.foundation.lazy.grid.GridItemSpan
import app.mizune.library.formatTime
import app.mizune.library.isVideoName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun DocumentFile.isVideo(): Boolean = isFile && name?.let(::isVideoName) == true

private data class Listing(
    val folders: List<DocumentFile>,
    val videos: List<DocumentFile>,
    val all: List<DocumentFile>,
    val episodeCounts: Map<String, Int>,
    /** The first video inside each folder, whose frame stands for it in the grid. */
    val covers: Map<String, DocumentFile>,
)

private sealed interface Entry {
    data class Folder(val file: DocumentFile, val episodes: Int, val cover: DocumentFile?) : Entry
    /** [number]: its place among the folder's videos, from 1. */
    data class Video(val file: DocumentFile, val number: Int) : Entry
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
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            app.prefs.libraryTreeUri = uri.toString()
            treeUri = uri.toString()
        }
    }

    BackHandler(enabled = path.size > 1) { path = path.dropLast(1) }

    val controlsVisible = remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { ThumbnailGenerator.requestAbort() }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (root == null) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Your library", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Choose the folder that holds your shows, e.g. Movies/Immersion.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { pickFolder.launch(null) }) { Text("Choose folder") }
                    }
                }
            } else if (!restoring) {
                // each folder is its own grid, so one can slide out while the next slides in.
                // Held back until the folder you were last in is known, so opening the app doesn't
                // animate from the root to it.
                AnimatedContent(
                    targetState = path,
                    contentKey = { it.lastOrNull()?.uri?.toString() },
                    transitionSpec = { Motion.intoFolder(deeper = targetState.size >= initialState.size) },
                    label = "folder",
                ) { folder ->
                    folder.lastOrNull()?.let {
                        FolderGrid(
                            app = app,
                            folder = it,
                            title = if (folder.size > 1) app.title(it.name.orEmpty()) else "Your library",
                            controlsVisible = controlsVisible,
                            onOpenFolder = { child -> path = path + child },
                            onOpenVideo = onOpenVideo,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible.value,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 14.dp, end = 10.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // the folder's own name is the page title; the chip is the way back up, and
                    // gives up its width first when an update needs room
                    Box(Modifier.weight(1f)) {
                        if (path.size > 1) {
                            FolderChip(
                                name = if (path.size > 2) app.title(path[path.size - 2].name.orEmpty()) else "Library",
                                onUp = { path = path.dropLast(1) },
                            )
                        }
                    }
                    UpdatePill(app)
                    SettingsButton(onOpenSettings)
                }
            }

            val setupStatus by BundledDictionaries.status.collectAsState()
            JustUpdated(app)
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

/** The last listing of each folder, kept while the app runs so navigation doesn't wait on SAF. */
private object LibraryCache {
    private const val KEEP = 16
    private val folders = object : LinkedHashMap<String, Listing>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Listing>) = size > KEEP
    }

    operator fun get(uri: String): Listing? = synchronized(folders) { folders[uri] }

    operator fun set(uri: String, listing: Listing) {
        synchronized(folders) { folders[uri] = listing }
    }
}

/** One folder's contents: its own listing, thumbnails and scroll position. */
@Composable
private fun FolderGrid(
    app: App,
    folder: DocumentFile,
    title: String,
    controlsVisible: MutableState<Boolean>,
    onOpenFolder: (DocumentFile) -> Unit,
    onOpenVideo: (DocumentFile, List<DocumentFile>) -> Unit,
) {
    val context = LocalContext.current
    // what this folder held last time, so coming back from a video paints at once; the listing is
    // then refreshed in the background in case the folder has changed
    var listing by remember(folder) { mutableStateOf(LibraryCache[folder.uri.toString()]) }
    LaunchedEffect(folder) {
        val fresh = withContext(Dispatchers.IO) { list(folder) }
        LibraryCache[folder.uri.toString()] = fresh
        listing = fresh
    }

    // warm the cache so scrolling never waits on a decode
    LaunchedEffect(listing) {
        val videos = listing?.let { it.videos + it.covers.values } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            videos.forEach { app.thumbnails.load(it.uri.toString()) }
        }
    }

    // fill in missing thumbnails while the library is open (libmpv is free then), folder covers too
    LaunchedEffect(listing) {
        val videos = listing?.let { it.videos + it.covers.values } ?: return@LaunchedEffect
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

    val gridState = rememberLazyGridState()
    // controls hide as you scroll into the grid and come back when you scroll up
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }
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

    val shown = listing
    if (shown == null) {
        // the folder is listed on the IO thread; leave the space empty rather than flash a spinner
        Box(Modifier.fillMaxSize())
        return
    }
    val tidy = app.prefs.tidyNames
    val videoTitles = remember(shown, tidy) {
        val names = shown.videos.map { it.name.orEmpty() }
        if (tidy) Titles.episodes(names) else names.map { it.substringBeforeLast('.') }
    }
    val folders = shown.folders.map {
        Entry.Folder(it, shown.episodeCounts[it.uri.toString()] ?: 0, shown.covers[it.uri.toString()])
    }
    val videos = shown.videos.mapIndexed { i, video -> Entry.Video(video, i + 1) }
    val detail = listOfNotNull(count(folders.size, "folder"), count(videos.size, "video")).joinToString(" · ")
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(200.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(start = 2.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    detail.ifEmpty { "Nothing here." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // labelled groups only when there are two kinds to tell apart
        val both = folders.isNotEmpty() && videos.isNotEmpty()
        if (both) item(key = "folders", span = { GridItemSpan(maxLineSpan) }) { GroupLabel("Folders") }
        items(folders, key = { it.file.uri.toString() }) { entry ->
            FolderCard(app, entry) { onOpenFolder(entry.file) }
        }
        if (both) item(key = "videos", span = { GridItemSpan(maxLineSpan) }) { GroupLabel("Videos") }
        items(videos, key = { it.file.uri.toString() }) { entry ->
            val name = videoTitles[entry.number - 1]
            // a title that already says which episode it is needs no place number beside it
            val number = entry.number.takeIf { app.prefs.episodeNumbers && !namesEpisode(name) }
            VideoCard(app, entry.file, name, number) { onOpenVideo(entry.file, shown.all) }
        }
    }
}

/** Current folder, floating over the grid; tapping it goes up. */
@Composable
private fun FolderChip(name: String, onUp: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onUp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹  ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}

@Composable
private fun SettingsButton(onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shape = CircleShape,
        modifier = Modifier.clickable(onClick = onOpenSettings),
    ) {
        Box(Modifier.padding(10.dp)) { Cog(MaterialTheme.colorScheme.onSurface, diameter = 20.dp) }
    }
}

/** A show: a frame from the first video in it, its name, and how many videos are inside. */
@Composable
private fun FolderCard(app: App, folder: Entry.Folder, onClick: () -> Unit) {
    val thumbnail = folder.cover?.let { rememberThumbnail(app, it.uri.toString()) }
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FolderMark(scheme.onSurfaceVariant.copy(alpha = 0.55f))
            }
        }
        Column(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp)) {
            Text(
                app.title(folder.file.name.orEmpty()),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (folder.episodes > 0) {
                Text(
                    "${folder.episodes} ${if (folder.episodes == 1) "video" else "videos"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The cached frame for a video, kept up to date as thumbnails are generated. */
@Composable
private fun rememberThumbnail(app: App, uri: String): android.graphics.Bitmap? {
    var thumbnail by remember(uri) { mutableStateOf(app.thumbnails.cached(uri)) }
    // reloads only when this video's thumbnail changes
    val version = app.thumbnails.versions[uri] ?: 0L
    LaunchedEffect(uri, version) {
        thumbnail = withContext(Dispatchers.IO) { app.thumbnails.load(uri) }
    }
    return thumbnail
}

@Composable
private fun VideoCard(app: App, video: DocumentFile, title: String, number: Int?, onClick: () -> Unit) {
    val uri = video.uri.toString()
    val position = app.prefs.position(uri)
    val duration = app.prefs.duration(uri)
    val progress = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    val thumbnail = rememberThumbnail(app, uri)

    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant)) {
                val image = thumbnail
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (number != null) {
                        Text(
                            "$number",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                .background(scheme.background.copy(alpha = 0.82f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    // not seen yet: its place in the folder, quietly, on the plain frame
                    Text(
                        number?.toString() ?: "▶",
                        style = MaterialTheme.typography.headlineMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (progress > 0f) {
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                            .background(Color(0x59000000)),
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(progress)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            Column(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp)) {
                Text(
                    title,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = if (position > 0) scheme.primary else scheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
}

/** How a file or folder called [name] is shown. */
private fun App.title(name: String): String =
    if (prefs.tidyNames) Titles.clean(name) else if (isVideoName(name)) name.substringBeforeLast('.') else name

private fun namesEpisode(title: String) = title.startsWith("Episode ") || Regex("""^S\d+ E\d+""").containsMatchIn(title)

private fun count(n: Int, noun: String) = when (n) {
    0 -> null
    1 -> "1 $noun"
    else -> "$n ${noun}s"
}

private fun list(folder: DocumentFile): Listing {
    val all = folder.listFiles().toList()
    val folders = all.filter { it.isDirectory }.sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
    val videos = all.filter { it.isVideo() }.sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
    // one level deep only: enough for "12 videos" and a cover under each show without a slow full scan
    val inside = folders.associate { child ->
        child.uri.toString() to runCatching { child.listFiles().toList() }.getOrDefault(emptyList())
    }
    val insideVideos = inside.mapValues { (_, files) ->
        files.filter { it.isVideo() }.sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
    }
    return Listing(
        folders, videos, all,
        episodeCounts = insideVideos.mapValues { it.value.size },
        // a show split into seasons has no videos of its own: take the cover from its first season
        covers = inside.mapNotNull { (uri, files) ->
            (insideVideos[uri]?.firstOrNull() ?: coverIn(files))?.let { uri to it }
        }.toMap(),
    )
}

/** The first video in the subfolders among [files], looking at most [depth] levels down. */
private fun coverIn(files: List<DocumentFile>, depth: Int = 3): DocumentFile? {
    if (depth == 0) return null
    return files.filter { it.isDirectory }.sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
        .firstNotNullOfOrNull { dir ->
            val children = runCatching { dir.listFiles().toList() }.getOrDefault(emptyList())
            children.filter { it.isVideo() }.minWithOrNull(compareBy(NaturalOrder) { it.name.orEmpty() })
                ?: coverIn(children, depth - 1)
        }
}
