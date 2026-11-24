package com.speedora.app.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.LocalFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Storage Access Framework (SAF) repository for user-authorized directories,
 * documents, archives, and safe folder browsing without requesting MANAGE_EXTERNAL_STORAGE.
 */
class SafDocumentRepository(private val context: Context) {

    fun persistTreePermission(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getPersistedTreeUris(): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }

    suspend fun listDirectory(treeOrDirUri: Uri): List<LocalFileItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeOrDirUri)
            ?: DocumentFile.fromSingleUri(context, treeOrDirUri)
            ?: return@withContext emptyList()

        root.listFiles().mapNotNull { doc ->
            ensureActive()
            val name = doc.name ?: return@mapNotNull null
            val mime = doc.type ?: "application/octet-stream"
            val isDir = doc.isDirectory
            LocalFileItem(
                id = doc.uri.toString(),
                uri = doc.uri,
                name = name,
                sizeBytes = if (isDir) 0L else doc.length(),
                mimeType = if (isDir) "vnd.android.document/directory" else mime,
                category = if (isDir) FileCategory.FOLDERS else FileCategorizer.categorize(name, mime),
                dateModifiedMillis = doc.lastModified(),
                isDirectory = isDir
            )
        }.sortedWith(compareByDescending<LocalFileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Safely computes folder total size with bounded recursion depth to stay responsive on mid-range phones.
     */
    suspend fun calculateFolderSizeSafely(dirUri: Uri, maxDepth: Int = 5, maxEntries: Int = 3000): Long? =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, dirUri) ?: return@withContext null
            var total = 0L
            var visited = 0

            suspend fun walk(node: DocumentFile, depth: Int) {
                if (depth > maxDepth || visited >= maxEntries) return
                for (child in node.listFiles()) {
                    ensureActive()
                    visited++
                    if (visited > maxEntries) return
                    if (child.isDirectory) {
                        walk(child, depth + 1)
                    } else {
                        total += child.length().coerceAtLeast(0L)
                    }
                }
            }
            walk(root, 0)
            total
        }

    suspend fun renameDocument(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DocumentsContract.renameDocument(context.contentResolver, uri, newName) != null
        }.getOrDefault(false)
    }

    suspend fun deleteDocument(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }.getOrDefault(false)
    }
}
