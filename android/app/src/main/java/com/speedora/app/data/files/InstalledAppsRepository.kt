package com.speedora.app.data.files

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.InstalledAppItem
import com.speedora.app.core.model.LocalFileItem
import com.speedora.app.core.security.FilenameSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class InstalledAppsRepository(private val context: Context) {

    suspend fun getLaunchableApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val seenPackages = HashSet<String>()
        val items = ArrayList<InstalledAppItem>()

        for (resolveInfo in resolved) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (!seenPackages.add(pkg)) continue

            val appInfo = resolveInfo.activityInfo.applicationInfo
            val pkgInfo = getPackageInfoCompat(pm, pkg)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val splitDirs = appInfo.splitPublicSourceDirs
            val isSplit = !splitDirs.isNullOrEmpty()
            val baseApkPath = appInfo.publicSourceDir ?: appInfo.sourceDir
            val baseFile = baseApkPath?.let(::File)
            val canReadBase = baseFile != null && baseFile.exists() && baseFile.canRead()

            val exportable = canReadBase && !isSplit
            val reason = when {
                isSplit -> "تطبيق مقسم (Split APK / App Bundle): لا يمكن تصديره كملف APK واحد موثوق"
                !canReadBase -> "محمي من النظام ولا يمكن قراءة حزمته"
                else -> null
            }

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo?.longVersionCode ?: 0L
            } else {
                @Suppress("DEPRECATION")
                (pkgInfo?.versionCode ?: 0).toLong()
            }

            items.add(
                InstalledAppItem(
                    packageName = pkg,
                    appName = resolveInfo.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg },
                    versionName = pkgInfo?.versionName ?: "—",
                    versionCode = versionCode,
                    apkPath = baseApkPath,
                    apkSizeBytes = if (canReadBase) baseFile!!.length() else 0L,
                    isSystemApp = isSystem,
                    isSplitApk = isSplit,
                    isExportable = exportable,
                    restrictionReason = reason
                )
            )
        }

        items.sortedWith(compareBy<InstalledAppItem> { it.isSystemApp }.thenBy { it.appName.lowercase() })
    }

    fun loadAppIcon(packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    /**
     * Copies a readable, non-split base APK into app cache for direct transfer or FileProvider sharing.
     */
    suspend fun exportApkAsLocalFileItem(app: InstalledAppItem): LocalFileItem? = withContext(Dispatchers.IO) {
        if (!app.isExportable || app.apkPath == null) return@withContext null
        val source = File(app.apkPath)
        if (!source.exists() || !source.canRead()) return@withContext null

        val exportDir = File(context.cacheDir, "exported_apks").apply { mkdirs() }
        val safeName = FilenameSanitizer.sanitize("${app.appName}_v${app.versionName}.apk")
        val target = File(exportDir, safeName)

        source.inputStream().buffered(65536).use { input ->
            target.outputStream().buffered(65536).use { output ->
                input.copyTo(output, bufferSize = 65536)
            }
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target
        )

        LocalFileItem(
            id = "apk:${app.packageName}",
            uri = uri,
            name = safeName,
            sizeBytes = target.length(),
            mimeType = "application/vnd.android.package-archive",
            category = FileCategory.APPS,
            dateModifiedMillis = target.lastModified()
        )
    }

    private fun getPackageInfoCompat(pm: PackageManager, pkg: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
    }.getOrNull()
}
