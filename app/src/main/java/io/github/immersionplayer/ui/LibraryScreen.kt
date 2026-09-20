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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "webm", "avi", "m4v", "mov", "ts", "m2ts", "wmv", "flv")

fun DocumentFile.isVideo(): Boolean =
    isFile && name?.substringAfterLast('.', "")?.lowercase() in VIDEO_EXTENSIONS

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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = path.lastOrNull()?.takeIf { path.size > 1 }?.name ?: "Immersion Player",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (path.size > 2) {
                        Text(
                            path.drop(1).dropLast(1).joinToString(" / ") { it.name.orEmpty() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (path.size > 1) TextButton(onClick = { path = path.dropLast(1) }) { Text("Up") }
                TextButton(onClick = { pickFolder.launch(null) }) {
                    Text(if (root == null) "Choose folder" else "Folder")
                }
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
            val setupStatus by BundledDictionaries.status.collectAsState()
            setupStatus?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

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
                        columns = GridCells.Adaptive(240.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
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
        }
    }
}

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
    var thumbnail by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, app.thumbnails.version(uri)) {
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

/** "日常 12.mkv" -> "12" */
private fun episodeNumber(name: String): String? =
    Regex("""(\d{1,3})(?!.*\d)""").find(name.substringBeforeLast('.'))?.groupValues?.get(1)

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

/** Sorts "Episode 2" before "Episode 10". */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("""\d+|\D+""")

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a).map { it.value }.toList()
        val cb = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                x.toBigInteger().compareTo(y.toBigInteger())
            } else {
                x.compareTo(y, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return ca.size - cb.size
    }
}

fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
