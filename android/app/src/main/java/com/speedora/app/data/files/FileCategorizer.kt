package com.speedora.app.data.files

import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.LocalFileItem
import com.speedora.app.core.model.SortOrder

object FileCategorizer {

    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "dng")
    private val videoExts = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts")
    private val audioExts = setOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "opus", "amr")
    private val docExts = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt", "ods", "odp", "md", "json", "xml")
    private val archiveExts = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")
    private val apkExts = setOf("apk", "apks", "xapk")

    fun categorize(fileName: String, mimeType: String?): FileCategory {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = mimeType?.lowercase().orEmpty()

        return when {
            ext in apkExts || mime == "application/vnd.android.package-archive" -> FileCategory.APPS
            ext in archiveExts || mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar") -> FileCategory.ARCHIVES
            ext in imageExts || mime.startsWith("image/") -> FileCategory.PHOTOS
            ext in videoExts || mime.startsWith("video/") -> FileCategory.VIDEOS
            ext in audioExts || mime.startsWith("audio/") -> FileCategory.MUSIC
            ext in docExts || mime.startsWith("text/") || mime.contains("pdf") || mime.contains("word") || mime.contains("excel") || mime.contains("powerpoint") || mime.contains("officedocument") -> FileCategory.DOCUMENTS
            else -> FileCategory.ALL
        }
    }

    fun isDocument(fileName: String, mimeType: String?): Boolean =
        categorize(fileName, mimeType) == FileCategory.DOCUMENTS

    fun isArchive(fileName: String, mimeType: String?): Boolean =
        categorize(fileName, mimeType) == FileCategory.ARCHIVES

    fun sortFiles(items: List<LocalFileItem>, sortOrder: SortOrder): List<LocalFileItem> {
        return when (sortOrder) {
            SortOrder.NEWEST -> items.sortedByDescending { it.dateModifiedMillis }
            SortOrder.OLDEST -> items.sortedBy { it.dateModifiedMillis }
            SortOrder.LARGEST -> items.sortedByDescending { it.sizeBytes }
            SortOrder.SMALLEST -> items.sortedBy { it.sizeBytes }
            SortOrder.NAME_ASC -> items.sortedBy { it.name.lowercase() }
        }
    }

    fun filterByQuery(items: List<LocalFileItem>, query: String): List<LocalFileItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items
        return items.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
}
