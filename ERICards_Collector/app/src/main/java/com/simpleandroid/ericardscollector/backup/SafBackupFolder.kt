package com.simpleandroid.ericardscollector.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.simpleandroid.ericardscollector.data.BackupFolder
import java.io.IOException

/**
 * A folder the user picked with the system folder chooser (Storage Access Framework). The app needs
 * no storage permission for it, and the files stay when the app is uninstalled.
 */
class SafBackupFolder(private val resolver: ContentResolver, private val tree: Uri) : BackupFolder {
    private val treeId = DocumentsContract.getTreeDocumentId(tree)

    /** File name -> document id of everything in the folder. */
    private fun children(): Map<String, String> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val result = LinkedHashMap<String, String>()
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                if (id != null && name != null) result[name] = id
            }
        }
        return result
    }

    private fun documentUri(documentId: String) = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)

    override fun listNames(): List<String> = children().keys.toList()

    override fun write(name: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val existing = children()[name]
        if (existing == null) {
            val created = DocumentsContract.createDocument(resolver, documentUri(treeId), "application/json", name)
                ?: throw IOException("The backup file could not be created in the folder.")
            writeTo(created, bytes)
            return
        }
        try {
            writeTo(documentUri(existing), bytes) // replaces the file of the same day
        } catch (error: Exception) {
            // A few providers cannot overwrite in place: replace the file instead
            DocumentsContract.deleteDocument(resolver, documentUri(existing))
            val created = DocumentsContract.createDocument(resolver, documentUri(treeId), "application/json", name)
                ?: throw IOException("The backup file could not be created in the folder.")
            writeTo(created, bytes)
        }
    }

    private fun writeTo(uri: Uri, bytes: ByteArray) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("The backup file could not be opened.")
        stream.use { it.write(bytes) }
    }

    override fun delete(name: String) {
        val id = children()[name] ?: return
        DocumentsContract.deleteDocument(resolver, documentUri(id))
    }

    companion object {
        /** A readable name for the settings page, for example "Internal storage/Documents/ERICards". */
        fun describe(treeUri: String): String = try {
            val id = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
            val volume = id.substringBefore(":")
            val path = id.substringAfter(":", "")
            val root = if (volume == "primary") "Internal storage" else volume
            if (path.isEmpty()) root else "$root/$path"
        } catch (error: Exception) {
            treeUri
        }
    }
}
