package app.mizune.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * A file or folder in the library, with what the app needs to know about it read once.
 *
 * `DocumentFile` asks the storage provider again for every name, type, size or date, and each ask
 * is a call into another process; a folder of 50 videos sorted by name took hundreds of them.
 * [list] reads a whole folder in one query instead. [uri] is the same one `DocumentFile` gives, so
 * the positions, thumbnails and subtitles stored under it still match.
 */
data class LibraryFile(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val length: Long = 0,
    val lastModified: Long = 0,
) {
    val isVideo: Boolean get() = !isDirectory && isVideoName(name)

    companion object {
        private val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        /** The folder picked as the library, from the tree URI the system gave for it. */
        fun root(tree: Uri): LibraryFile = LibraryFile(
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            name = "",
            isDirectory = true,
        )

        /**
         * What's inside [folder], [root] or a folder listed from it; nothing if it can't be read
         * (gone, or permission taken back). Call it off the main thread.
         */
        fun list(context: Context, folder: LibraryFile): List<LibraryFile> {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, DocumentsContract.getDocumentId(folder.uri))
            val found = mutableListOf<LibraryFile>()
            runCatching { context.contentResolver.query(children, columns, null, null, null) }.getOrNull()?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    found += LibraryFile(
                        DocumentsContract.buildDocumentUriUsingTree(folder.uri, id),
                        name = cursor.getString(1).orEmpty(),
                        isDirectory = cursor.getString(2) == Document.MIME_TYPE_DIR,
                        length = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                        lastModified = if (cursor.isNull(4)) 0 else cursor.getLong(4),
                    )
                }
            }
            return found
        }

        /**
         * A file opened from another app. Its provider may not be a documents provider, so this asks
         * for each field on its own the way `DocumentFile` does; the name is blank if it won't say.
         */
        fun single(context: Context, uri: Uri): LibraryFile {
            val file = DocumentFile.fromSingleUri(context, uri) ?: return LibraryFile(uri, name = "", isDirectory = false)
            return LibraryFile(uri, file.name.orEmpty(), isDirectory = false, file.length(), file.lastModified())
        }

        fun of(file: File) = LibraryFile(Uri.fromFile(file), file.name, file.isDirectory, file.length(), file.lastModified())
    }
}
