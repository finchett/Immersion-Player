package io.github.immersionplayer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.immersionplayer.library.NaturalOrder
import io.github.immersionplayer.library.episodeNumber
import io.github.immersionplayer.library.isVideoName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Listing(val folders: List<Pair<File, Int>>, val videos: List<File>)

private fun list(folder: File): Listing {
    val all = folder.listFiles().orEmpty().filter { !it.isHidden }
    val folders = all.filter { it.isDirectory }.sortedWith(compareBy(NaturalOrder) { it.name })
    val videos = all.filter { it.isFile && isVideoName(it.name) }.sortedWith(compareBy(NaturalOrder) { it.name })
    // one level deep only: enough for "12 videos" under each show without a slow full scan
    val counted = folders.map { it to it.listFiles().orEmpty().count { f -> f.isFile && isVideoName(f.name) } }
    return Listing(counted, videos)
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (current != null && root != null && current != root) {
                    TextButton(onClick = { onOpenFolder(current.parentFile) }) { Text("‹") }
                }
                Text(
                    current?.name ?: "Immersion Player",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = {
                    chooseFolder(root)?.let {
                        settings.updateLibraryRoot(it.path)
                        onOpenFolder(it)
                    }
                }) { Text(if (root == null) "Choose folder" else "Change folder") }
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
            HorizontalDivider()
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(shown.folders, key = { it.first.path }) { (dir, count) ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenFolder(dir) }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dir.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (count > 0) {
                    Text(
                        if (count == 1) "1 video" else "$count videos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(shown.videos, key = { it.path }) { video ->
            val progress = remember(video) { app.settings.progress(video.absolutePath) }
            Row(
                Modifier.fillMaxWidth().clickable { onOpenVideo(video) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.width(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        episodeNumber(video.name) ?: "▶",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        video.nameWithoutExtension,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress > 0f) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            drawStopIndicator = {},
                        )
                    }
                }
            }
        }
    }
}
