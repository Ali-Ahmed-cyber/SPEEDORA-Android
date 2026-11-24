package com.speedora.app.core.model

import android.net.Uri
import kotlinx.serialization.Serializable

enum class FileCategory {
    ALL,
    PHOTOS,
    VIDEOS,
    MUSIC,
    APPS,
    DOCUMENTS,
    ARCHIVES,
    DOWNLOADS,
    FOLDERS,
    RECENT
}

enum class SortOrder {
    NEWEST,
    OLDEST,
    LARGEST,
    SMALLEST,
    NAME_ASC
}

data class LocalFileItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory,
    val dateModifiedMillis: Long,
    val relativePath: String = "",
    val durationMillis: Long? = null,
    val artist: String? = null,
    val isDirectory: Boolean = false,
    val childCount: Int? = null,
    val isFavorite: Boolean = false
)

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String?,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean,
    val isSplitApk: Boolean,
    val isExportable: Boolean,
    val restrictionReason: String? = null
)

data class DuplicateGroup(
    val sha256: String,
    val fileSizeBytes: Long,
    val files: List<LocalFileItem>
)

@Serializable
data class FileTransferDescriptor(
    val id: String,
    val sanitizedName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val categoryName: String
)

object FileSizeFormatter {
    fun format(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    fun formatSpeed(bytesPerSec: Double): String {
        if (bytesPerSec <= 0.0) return "0.0 MB/s"
        val mbps = bytesPerSec / (1024.0 * 1024.0)
        return if (mbps >= 1.0) {
            String.format("%.2f MB/s", mbps)
        } else {
            val kbps = bytesPerSec / 1024.0
            String.format("%.0f KB/s", kbps)
        }
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
