package com.speedora.app.data.files

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.speedora.app.core.model.DuplicateGroup
import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.LocalFileItem
import com.speedora.app.core.security.SpeedoraCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class DeviceStorageSummary(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long
)

class MediaStoreRepository(private val context: Context) {

    fun hasMediaPermissionFor(category: FileCategory): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val visualSelected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            when (category) {
                FileCategory.PHOTOS -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED || visualSelected
                FileCategory.VIDEOS -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED || visualSelected
                FileCategory.MUSIC -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                else -> hasAnyMediaPermission()
            }
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAnyMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun queryCategory(category: FileCategory, limit: Int = 2000): List<LocalFileItem> = withContext(Dispatchers.IO) {
        when (category) {
            FileCategory.PHOTOS -> queryImages(limit)
            FileCategory.VIDEOS -> queryVideos(limit)
            FileCategory.MUSIC -> queryAudio(limit)
            FileCategory.DOWNLOADS -> queryDownloads(limit)
            FileCategory.DOCUMENTS -> queryFilesByFilter(FileCategory.DOCUMENTS, limit)
            FileCategory.ARCHIVES -> queryFilesByFilter(FileCategory.ARCHIVES, limit)
            FileCategory.RECENT -> queryRecentFiles(limit)
            FileCategory.ALL -> queryAllAccessibleMediaAndFiles(limit)
            FileCategory.APPS, FileCategory.FOLDERS -> emptyList()
        }
    }

    private fun queryImages(limit: Int): List<LocalFileItem> {
        val result = ArrayList<LocalFileItem>()
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Images.Media.RELATIVE_PATH)
        }
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext() && result.size < limit) {
                val id = cursor.getLong(idIdx)
                val size = cursor.getLong(sizeIdx)
                if (size <= 0L) continue
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                result.add(
                    LocalFileItem(
                        id = uri.toString(),
                        uri = uri,
                        name = cursor.getString(nameIdx) ?: "image_$id",
                        sizeBytes = size,
                        mimeType = cursor.getString(mimeIdx) ?: "image/*",
                        category = FileCategory.PHOTOS,
                        dateModifiedMillis = cursor.getLong(dateIdx) * 1000L,
                        relativePath = if (pathIdx >= 0) cursor.getString(pathIdx).orEmpty() else ""
                    )
                )
            }
        }
        return result
    }

    private fun queryVideos(limit: Int): List<LocalFileItem> {
        val result = ArrayList<LocalFileItem>()
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Video.Media.RELATIVE_PATH)
        }
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext() && result.size < limit) {
                val id = cursor.getLong(idIdx)
                val size = cursor.getLong(sizeIdx)
                if (size <= 0L) continue
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                result.add(
                    LocalFileItem(
                        id = uri.toString(),
                        uri = uri,
                        name = cursor.getString(nameIdx) ?: "video_$id",
                        sizeBytes = size,
                        mimeType = cursor.getString(mimeIdx) ?: "video/*",
                        category = FileCategory.VIDEOS,
                        dateModifiedMillis = cursor.getLong(dateIdx) * 1000L,
                        relativePath = if (pathIdx >= 0) cursor.getString(pathIdx).orEmpty() else "",
                        durationMillis = if (durIdx >= 0) cursor.getLong(durIdx) else null
                    )
                )
            }
        }
        return result
    }

    private fun queryAudio(limit: Int): List<LocalFileItem> {
        val result = ArrayList<LocalFileItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.SIZE} > 0",
            null,
            sortOrder
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)

            while (cursor.moveToNext() && result.size < limit) {
                val id = cursor.getLong(idIdx)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val rawArtist = if (artistIdx >= 0) cursor.getString(artistIdx) else null
                result.add(
                    LocalFileItem(
                        id = uri.toString(),
                        uri = uri,
                        name = cursor.getString(nameIdx) ?: "audio_$id",
                        sizeBytes = cursor.getLong(sizeIdx),
                        mimeType = cursor.getString(mimeIdx) ?: "audio/*",
                        category = FileCategory.MUSIC,
                        dateModifiedMillis = cursor.getLong(dateIdx) * 1000L,
                        durationMillis = if (durIdx >= 0) cursor.getLong(durIdx) else null,
                        artist = rawArtist?.takeIf { it != "<unknown>" && it.isNotBlank() }
                    )
                )
            }
        }
        return result
    }

    private fun queryDownloads(limit: Int): List<LocalFileItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
        return queryGenericCollection(collection, FileCategory.DOWNLOADS, limit) { name, relPath ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) true
            else relPath.contains("Download", ignoreCase = true) || name.isNotBlank()
        }
    }

    private fun queryFilesByFilter(targetCategory: FileCategory, limit: Int): List<LocalFileItem> {
        val collection = MediaStore.Files.getContentUri("external")
        return queryGenericCollection(collection, targetCategory, limit) { name, _ ->
            when (targetCategory) {
                FileCategory.DOCUMENTS -> FileCategorizer.isDocument(name, null)
                FileCategory.ARCHIVES -> FileCategorizer.isArchive(name, null)
                else -> true
            }
        }
    }

    private fun queryRecentFiles(limit: Int): List<LocalFileItem> {
        val items = queryAllAccessibleMediaAndFiles(limit)
        return items.sortedByDescending { it.dateModifiedMillis }.take(150)
    }

    private fun queryAllAccessibleMediaAndFiles(limit: Int): List<LocalFileItem> {
        val combined = LinkedHashMap<String, LocalFileItem>()
        if (hasMediaPermissionFor(FileCategory.PHOTOS)) {
            queryImages(limit / 3).forEach { combined[it.id] = it }
        }
        if (hasMediaPermissionFor(FileCategory.VIDEOS)) {
            queryVideos(limit / 3).forEach { combined[it.id] = it }
        }
        if (hasMediaPermissionFor(FileCategory.MUSIC)) {
            queryAudio(limit / 4).forEach { combined[it.id] = it }
        }
        queryDownloads(limit / 4).forEach { combined[it.id] = it }
        return combined.values.sortedByDescending { it.dateModifiedMillis }
    }

    private fun queryGenericCollection(
        collectionUri: Uri,
        forcedCategory: FileCategory,
        limit: Int,
        predicate: (name: String, relPath: String) -> Boolean
    ): List<LocalFileItem> {
        val result = ArrayList<LocalFileItem>()
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        try {
            context.contentResolver.query(
                collectionUri,
                projection.toTypedArray(),
                "${MediaStore.MediaColumns.SIZE} > 0",
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext() && result.size < limit) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: continue
                    val relPath = if (pathIdx >= 0) cursor.getString(pathIdx).orEmpty() else ""
                    if (!predicate(name, relPath)) continue
                    val mime = cursor.getString(mimeIdx) ?: "application/octet-stream"
                    val uri = ContentUris.withAppendedId(collectionUri, id)
                    result.add(
                        LocalFileItem(
                            id = uri.toString(),
                            uri = uri,
                            name = name,
                            sizeBytes = cursor.getLong(sizeIdx),
                            mimeType = mime,
                            category = if (forcedCategory == FileCategory.DOWNLOADS) FileCategory.DOWNLOADS else FileCategorizer.categorize(name, mime),
                            dateModifiedMillis = cursor.getLong(dateIdx) * 1000L,
                            relativePath = relPath
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Respect scoped storage boundaries gracefully without crashing
        }
        return result
    }

    /**
     * Explicit user-triggered duplicate detection:
     * Groups candidates by exact byte size first, then computes streaming SHA-256 only on size collisions.
     */
    suspend fun findDuplicatesBySha256(items: List<LocalFileItem>): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val sizeGroups = items
            .filter { !it.isDirectory && it.sizeBytes > 0L }
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }

        val duplicates = ArrayList<DuplicateGroup>()
        for ((size, candidates) in sizeGroups) {
            ensureActive()
            val byHash = HashMap<String, MutableList<LocalFileItem>>()
            for (item in candidates) {
                ensureActive()
                val hash = runCatching {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        SpeedoraCrypto.sha256Stream(stream)
                    }
                }.getOrNull() ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(item)
            }
            byHash.forEach { (hash, matched) ->
                if (matched.size > 1) {
                    duplicates.add(DuplicateGroup(sha256 = hash, fileSizeBytes = size, files = matched))
                }
            }
        }
        duplicates.sortedByDescending { it.fileSizeBytes * it.files.size }
    }

    fun getStorageSummary(): DeviceStorageSummary {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        return DeviceStorageSummary(
            totalBytes = total,
            availableBytes = avail,
            usedBytes = (total - avail).coerceAtLeast(0L)
        )
    }

    fun createDeleteRequestIfRequired(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }
        return null
    }

    suspend fun deleteDirectLegacy(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }
}
