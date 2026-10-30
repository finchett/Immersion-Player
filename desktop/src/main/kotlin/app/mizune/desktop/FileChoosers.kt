package app.mizune.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Native folder picker on macOS (FileDialog can pick directories there); Swing's elsewhere. */
fun chooseFolder(start: File?): File? {
    if (isMac) {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val dialog = FileDialog(null as Frame?, "Choose your video folder", FileDialog.LOAD)
            start?.let { dialog.directory = it.path }
            dialog.isVisible = true
            val name = dialog.file ?: return null
            return File(dialog.directory, name)
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
    }
    val chooser = JFileChooser(start).apply {
        dialogTitle = "Choose your video folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

fun chooseDictionaryZip(): File? {
    if (isMac) {
        val dialog = FileDialog(null as Frame?, "Import a Yomitan dictionary", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".zip", ignoreCase = true) }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
    val chooser = JFileChooser().apply {
        dialogTitle = "Import a Yomitan dictionary"
        fileFilter = FileNameExtensionFilter("Yomitan dictionary (.zip)", "zip")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Where to save the key map, as a .json file. */
fun chooseKeyMapToSave(): File? {
    if (isMac) {
        val dialog = FileDialog(null as Frame?, "Export keys", FileDialog.SAVE)
        dialog.file = "mizune-keys.json"
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, if (name.endsWith(".json", ignoreCase = true)) name else "$name.json")
    }
    val chooser = JFileChooser().apply {
        dialogTitle = "Export keys"
        selectedFile = File("mizune-keys.json")
        fileFilter = FileNameExtensionFilter("Mizune keys (.json)", "json")
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val file = chooser.selectedFile
    return if (file.name.endsWith(".json", ignoreCase = true)) file else File(file.path + ".json")
}

/** A key map file to import. */
fun chooseKeyMapToOpen(): File? {
    if (isMac) {
        val dialog = FileDialog(null as Frame?, "Import keys", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
    val chooser = JFileChooser().apply {
        dialogTitle = "Import keys"
        fileFilter = FileNameExtensionFilter("Mizune keys (.json)", "json")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
