package io.github.immersionplayer.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.immersionplayer.library.NaturalOrder
import io.github.immersionplayer.library.Titles
import io.github.immersionplayer.library.isVideoName
import io.github.immersionplayer.ui.Cog
import io.github.immersionplayer.ui.FolderMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Folder(val dir: File, val videos: Int, val cover: File?)
private data class Listing(val folders: List<Folder>, val videos: List<File>)

private fun videosIn(folder: File): List<File> =
    folder.listFiles().orEmpty().filter { it.isFile && !it.isHidden && isVideoName(it.name) }
        .sortedWith(compareBy(NaturalOrder) { it.name })

/** The video after [video] in its folder, in the order the library lists them, or null for the last one. */
fun nextVideo(video: File): File? {
    val videos = video.parentFile?.let(::videosIn).orEmpty()
    val index = videos.indexOfFirst { it.name == video.name }
    return if (index < 0) null else videos.getOrNull(index + 1)
}

private fun list(folder: File): Listing {
    val folders = folder.listFiles().orEmpty().filter { it.isDirectory && !it.isHidden }
        .sortedWith(compareBy(NaturalOrder) { it.name })
        // counts go one level deep only: enough for "12 videos" under each show without a slow full scan
        .map { dir -> videosIn(dir).let { Folder(dir, it.size, it.firstOrNull() ?: coverIn(dir)) } }
    return Listing(folders, videosIn(folder))
}

/** The first video in [folder]'s subfolders, for a show split into seasons, looking at most [depth] levels down. */
private fun coverIn(folder: File, depth: Int = 3): File? {
    if (depth == 0) return null
    return folder.listFiles().orEmpty().filter { it.isDirectory && !it.isHidden }
        .sortedWith(compareBy(NaturalOrder) { it.name })
        .firstNotNullOfOrNull { dir -> videosIn(dir).firstOrNull() ?: coverIn(dir, depth - 1) }
}

/** The library root, and the folder being shown (the root unless [folder] is inside it). */
private fun locate(app: DesktopApp, folder: File?): Pair<File?, File?> {
    val root = app.settings.libraryRoot?.let(::File)?.takeIf { it.isDirectory }
    val current = folder?.takeIf { root != null && it.isDirectory && it.path.startsWith(root.path) } ?: root
    return root to current
}

/**
 * Header content: the way back up from the folder being shown (the library, then each folder
 * above this one), and Settings. The folder's own name is the page title, so it isn't repeated.
 */
@Composable
fun LibraryHeader(app: DesktopApp, folder: File?, onOpenFolder: (File) -> Unit, onOpenSettings: () -> Unit) {
    val (root, current) = locate(app, folder)
    val above = generateSequence(current?.takeIf { it != root }?.parentFile) { it.parentFile?.takeIf { _ -> it != root } }
        .takeWhile { root != null && it.path.startsWith(root.path) }
        .toList().reversed()
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            above.forEachIndexed { i, dir ->
                if (i > 0) Text("/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                TextAction(
                    if (dir == root) "Library" else app.settings.title(dir.name),
                    onClick = { onOpenFolder(dir) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        IconAction(onClick = onOpenSettings) { Cog(it) }
    }
}

/** Everything below the header: the folder's contents, or a way to choose the library folder. */
@Composable
fun LibraryBody(app: DesktopApp, folder: File?, onOpenFolder: (File) -> Unit, onOpenVideo: (File) -> Unit) {
    val (_, current) = locate(app, folder)
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your library", style = MaterialTheme.typography.headlineMedium)
                Text("Pick the folder that holds your videos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        chooseFolder(null)?.let {
                            app.settings.updateLibraryRoot(it.path)
                            onOpenFolder(it)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    elevation = null,
                ) { Text("Choose folder", style = MaterialTheme.typography.labelLarge) }
            }
        }
    } else {
        FolderContents(app, current, isRoot = current == locate(app, null).first, onOpenFolder, onOpenVideo)
    }
}

@Composable
private fun FolderContents(
    app: DesktopApp,
    folder: File,
    isRoot: Boolean,
    onOpenFolder: (File) -> Unit,
    onOpenVideo: (File) -> Unit,
) {
    var listing by remember(folder) { mutableStateOf<Listing?>(null) }
    LaunchedEffect(folder) { listing = withContext(Dispatchers.IO) { list(folder) } }
    val shown = listing ?: return
    val title = if (isRoot) "Your library" else app.settings.title(folder.name)
    val tidy = app.settings.tidyNames
    val videoTitles = remember(shown, tidy) {
        if (tidy) Titles.episodes(shown.videos.map { it.name }) else shown.videos.map { it.nameWithoutExtension }
    }
    if (shown.folders.isEmpty() && shown.videos.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp)) {
            PageTitle(title, null)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No videos here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(232.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            PageTitle(title, listOfNotNull(
                count(shown.folders.size, "folder"),
                count(shown.videos.size, "video"),
            ).joinToString(" · "))
        }
        // labelled groups only when there are two kinds to tell apart
        val both = shown.folders.isNotEmpty() && shown.videos.isNotEmpty()
        if (both) {
            item(key = "folders", span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Folders", Modifier.padding(top = 4.dp)) }
        }
        items(shown.folders, key = { it.dir.path }) { item ->
            Card(
                app = app,
                thumbnailOf = item.cover,
                badge = null,
                progress = 0f,
                title = app.settings.title(item.dir.name),
                subtitle = when (item.videos) {
                    0 -> null
                    1 -> "1 video"
                    else -> "${item.videos} videos"
                },
                onClick = { onOpenFolder(item.dir) },
            )
        }
        if (both) {
            item(key = "videos", span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Videos", Modifier.padding(top = 10.dp)) }
        }
        // numbered by place in the folder: names carry too many other numbers (1080p, 10bit) to guess from
        itemsIndexed(shown.videos, key = { _, video -> video.path }) { index, video ->
            Card(
                app = app,
                thumbnailOf = video,
                // a title that already says which episode it is needs no place number beside it
                badge = if (app.settings.episodeNumbers && !namesEpisode(videoTitles[index])) "${index + 1}" else null,
                progress = remember(video) { app.settings.progress(video.absolutePath) },
                title = videoTitles[index],
                subtitle = null,
                onClick = { onOpenVideo(video) },
            )
        }
    }
}

@Composable
private fun Card(
    app: DesktopApp,
    thumbnailOf: File?,
    badge: String?,
    progress: Float,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ring by animateColorAsState(if (hovered) scheme.primary else scheme.primary.copy(alpha = 0f), tween(120), label = "ring")
    Column(
        Modifier.hoverable(interaction).clickable(interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(scheme.surfaceVariant)
                .border(1.5.dp, ring, shape),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumbnailOf?.let { rememberThumbnail(app, it) }
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (subtitle != null) {
                FolderMark(scheme.onSurfaceVariant.copy(alpha = 0.55f))
            }
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(scheme.background.copy(alpha = 0.82f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            if (progress > 0f) {
                // a thin line along the foot of the frame: how far in, nothing more
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x59000000))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(scheme.primary))
                }
            }
        }
        Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (hovered) scheme.primary else scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** The page's name, large and plain, with what's in it underneath. */
@Composable
private fun PageTitle(title: String, detail: String?) {
    Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!detail.isNullOrEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun namesEpisode(title: String) = title.startsWith("Episode ") || Regex("""^S\d+ E\d+""").containsMatchIn(title)

private fun count(n: Int, noun: String) = when (n) {
    0 -> null
    1 -> "1 $noun"
    else -> "$n ${noun}s"
}

/** A frame from [video]: where you left off, or a fifth of the way in if unwatched. */
@Composable
private fun rememberThumbnail(app: DesktopApp, video: File): ImageBitmap? {
    val version = app.thumbnails.versions[video.path]
    LaunchedEffect(video) {
        if (app.thumbnails.cached(video) == null) {
            val position = app.settings.position(video.absolutePath)
            app.thumbnails.request(video, if (position > 0) position.toString() else "20%")
        }
    }
    return remember(video, version) { app.thumbnails.cached(video) }
}
