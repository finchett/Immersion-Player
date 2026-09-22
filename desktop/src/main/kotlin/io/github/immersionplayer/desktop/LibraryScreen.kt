package io.github.immersionplayer.desktop

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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.immersionplayer.library.episodeNumber
import io.github.immersionplayer.library.isVideoName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Folder(val dir: File, val videos: Int, val cover: File?)
private data class Listing(val folders: List<Folder>, val videos: List<File>)

private fun videosIn(folder: File): List<File> =
    folder.listFiles().orEmpty().filter { it.isFile && !it.isHidden && isVideoName(it.name) }
        .sortedWith(compareBy(NaturalOrder) { it.name })

private fun list(folder: File): Listing {
    val folders = folder.listFiles().orEmpty().filter { it.isDirectory && !it.isHidden }
        .sortedWith(compareBy(NaturalOrder) { it.name })
        // one level deep only: enough for "12 videos" under each show without a slow full scan
        .map { dir -> videosIn(dir).let { Folder(dir, it.size, it.firstOrNull()) } }
    return Listing(folders, videosIn(folder))
}

@Composable
fun LibraryScreen(
    app: DesktopApp,
    folder: File?,
    onOpenFolder: (File) -> Unit,
    onOpenVideo: (File) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings = app.settings
    val root = settings.libraryRoot?.let(::File)?.takeIf { it.isDirectory }
    val current = folder?.takeIf { root != null && it.isDirectory && it.path.startsWith(root.path) } ?: root

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = TitleBarInset + 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // the path from the library root, each part a way back up
                val crumbs = generateSequence(current) { it.parentFile?.takeIf { _ -> it != root } }
                    .toList().reversed().takeIf { current != null } ?: emptyList()
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (crumbs.isEmpty()) {
                        Text("Library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    crumbs.forEachIndexed { i, dir ->
                        if (i > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (i == crumbs.lastIndex) {
                            Text(
                                dir.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        } else {
                            TextAction(dir.name, onClick = { onOpenFolder(dir) }, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                TextAction(if (root == null) "Choose folder" else "Change folder", onClick = {
                    chooseFolder(root)?.let {
                        settings.updateLibraryRoot(it.path)
                        onOpenFolder(it)
                    }
                })
                TextAction("Settings", onClick = onOpenSettings)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pick the folder that holds your videos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {
                            chooseFolder(null)?.let {
                                settings.updateLibraryRoot(it.path)
                                onOpenFolder(it)
                            }
                        }) { Text("Choose folder") }
                    }
                }
            } else {
                FolderContents(app, current, onOpenFolder, onOpenVideo)
            }
        }
    }
}

@Composable
private fun FolderContents(app: DesktopApp, folder: File, onOpenFolder: (File) -> Unit, onOpenVideo: (File) -> Unit) {
    var listing by remember(folder) { mutableStateOf<Listing?>(null) }
    LaunchedEffect(folder) { listing = withContext(Dispatchers.IO) { list(folder) } }
    val shown = listing ?: return
    if (shown.folders.isEmpty() && shown.videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No videos here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(shown.folders, key = { it.dir.path }) { item ->
            Card(
                app = app,
                thumbnailOf = item.cover,
                badge = null,
                progress = 0f,
                title = item.dir.name,
                subtitle = if (item.videos == 1) "1 video" else "${item.videos} videos",
                onClick = { onOpenFolder(item.dir) },
            )
        }
        items(shown.videos, key = { it.path }) { video ->
            Card(
                app = app,
                thumbnailOf = video,
                badge = episodeNumber(video.name),
                progress = remember(video) { app.settings.progress(video.absolutePath) },
                title = video.nameWithoutExtension,
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
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier.hoverable(interaction).clickable(interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    2.dp,
                    if (hovered) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumbnailOf?.let { rememberThumbnail(app, it) }
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (subtitle != null) {
                Text("▤", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color(0xB0000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    trackColor = Color(0x60000000),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
