package com.speedora.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.speedora.app.R
import com.speedora.app.core.designsystem.SpeedoraColors
import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.core.model.InstalledAppItem
import com.speedora.app.core.model.LocalFileItem
import com.speedora.app.core.model.SortOrder
import com.speedora.app.data.files.InstalledAppsRepository
import com.speedora.app.data.files.SelectionSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SendPickerScreen(
    activeCategory: FileCategory,
    files: List<LocalFileItem>,
    installedApps: List<InstalledAppItem>,
    safFolderUris: List<Uri>,
    activeFolderItems: List<LocalFileItem>,
    selection: SelectionSnapshot,
    isLoading: Boolean,
    searchQuery: String,
    sortOrder: SortOrder,
    isGridMode: Boolean,
    hasMediaPermission: Boolean,
    installedAppsRepository: InstalledAppsRepository,
    onCategorySelected: (FileCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleGrid: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleFile: (LocalFileItem) -> Unit,
    onToggleApp: (InstalledAppItem) -> Unit,
    onRemoveSelected: (String) -> Unit,
    onClearSelection: () -> Unit,
    onOpenSafFolder: (Uri, Boolean) -> Unit,
    onPermissionsGranted: () -> Unit,
    onContinueToDevicePicker: () -> Unit
) {
    val context = LocalContext.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<LocalFileItem?>(null) }

    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    val mediaPerms = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsGranted()
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onOpenSafFolder(uri, true)
        }
    }

    LaunchedEffect(activeCategory) {
        onCategorySelected(activeCategory)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 10-Category Horizontal Tabs
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FileCategory.entries) { cat ->
                FilterChip(
                    selected = cat == activeCategory,
                    onClick = { onCategorySelected(cat) },
                    label = { Text(categoryLabel(cat), maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpeedoraColors.RoyalBlue,
                        selectedLabelColor = SpeedoraColors.PureWhite
                    )
                )
            }
        }

        // Search + Sort + Grid/List + Select All bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_files_hint), maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp)
            )
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(Icons.Outlined.Sort, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(order)) },
                            onClick = {
                                onSortChange(order)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onToggleGrid) {
                Icon(
                    imageVector = if (isGridMode) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                    contentDescription = "Toggle View"
                )
            }
            if (activeCategory != FileCategory.APPS && activeCategory != FileCategory.FOLDERS) {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.action_select_all))
                }
            }
        }

        // Main Browser Area
        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasMediaPermission && activeCategory in setOf(FileCategory.ALL, FileCategory.PHOTOS, FileCategory.VIDEOS, FileCategory.MUSIC, FileCategory.RECENT) -> {
                    PermissionRationaleCard(
                        title = stringResource(R.string.perm_media_title),
                        description = stringResource(R.string.perm_media_desc),
                        onGrant = { permissionLauncher.launch(mediaPerms) }
                    )
                }

                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SpeedoraColors.RoyalBlue)
                    }
                }

                activeCategory == FileCategory.APPS -> {
                    InstalledAppsList(
                        apps = installedApps.filter {
                            searchQuery.isBlank() || it.appName.contains(searchQuery, ignoreCase = true)
                        },
                        selectedIds = selection.selectedIds,
                        installedAppsRepository = installedAppsRepository,
                        onToggleApp = onToggleApp
                    )
                }

                activeCategory == FileCategory.FOLDERS -> {
                    SafFoldersBrowser(
                        persistedTrees = safFolderUris,
                        items = activeFolderItems.filter {
                            searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                        },
                        selectedIds = selection.selectedIds,
                        onPickFolder = { safFolderLauncher.launch(null) },
                        onOpenFolder = { uri -> onOpenSafFolder(uri, false) },
                        onToggleFile = onToggleFile
                    )
                }

                files.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "لا توجد ملفات متاحة في هذا التصنيف",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                isGridMode && (activeCategory == FileCategory.PHOTOS || activeCategory == FileCategory.VIDEOS) -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files, key = { it.id }) { item ->
                            val isSelected = item.id in selection.selectedIds
                            MediaGridTile(
                                item = item,
                                isSelected = isSelected,
                                imageLoader = imageLoader,
                                onToggle = { onToggleFile(item) },
                                onPreview = { previewFile = item }
                            )
                        }
                    }
                }

                else -> {
                    val grouped = remember(files) {
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        files.groupBy { fmt.format(Date(it.dateModifiedMillis)) }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (dateLabel, dateItems) ->
                            item(key = "hdr_$dateLabel") {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SpeedoraColors.RoyalBlue,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                            items(dateItems, key = { it.id }) { item ->
                                val isSelected = item.id in selection.selectedIds
                                FileListRow(
                                    item = item,
                                    isSelected = isSelected,
                                    imageLoader = imageLoader,
                                    onToggle = { onToggleFile(item) },
                                    onPreview = { previewFile = item }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Persistent Selection Tray
        if (selection.totalCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(selection.items, key = { it.id }) { sel ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SpeedoraColors.SoftBlue
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sel.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SpeedoraColors.MidnightNavy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(88.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Remove",
                                        tint = SpeedoraColors.MidnightNavy,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onRemoveSelected(sel.id) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(
                                    R.string.selected_count_format,
                                    selection.totalCount,
                                    FileSizeFormatter.format(selection.totalBytes)
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.preserve_quality_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onClearSelection) {
                                Text(stringResource(R.string.action_clear_all))
                            }
                            Button(
                                onClick = onContinueToDevicePicker,
                                colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.RoyalBlue)
                            ) {
                                Text(stringResource(R.string.action_continue))
                            }
                        }
                    }
                }
            }
        }
    }

    // Real Media Preview Dialog (Photo / Video / Audio / Details)
    previewFile?.let { item ->
        FilePreviewDialog(
            item = item,
            imageLoader = imageLoader,
            onDismiss = { previewFile = null }
        )
    }
}

@Composable
private fun MediaGridTile(
    item: LocalFileItem,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    onToggle: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) SpeedoraColors.RoyalBlue else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onPreview() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.uri,
                imageLoader = imageLoader,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.category == FileCategory.VIDEOS) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                )
                item.durationMillis?.let { dur ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = FileSizeFormatter.formatDuration(dur),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.88f))
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) SpeedoraColors.RoyalBlue else SpeedoraColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FileListRow(
    item: LocalFileItem,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    onToggle: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) SpeedoraColors.SoftBlue.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) SpeedoraColors.RoyalBlue else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (item.category == FileCategory.PHOTOS || item.category == FileCategory.VIDEOS) {
                AsyncImage(
                    model = item.uri,
                    imageLoader = imageLoader,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPreview() }
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SpeedoraColors.SoftBlue,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onPreview() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (item.category) {
                                FileCategory.MUSIC -> Icons.Outlined.AudioFile
                                FileCategory.ARCHIVES -> Icons.Outlined.Archive
                                FileCategory.DOCUMENTS -> Icons.Outlined.Description
                                FileCategory.APPS -> Icons.Outlined.Android
                                FileCategory.FOLDERS -> Icons.Outlined.Folder
                                else -> Icons.Outlined.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = SpeedoraColors.RoyalBlue
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    append(FileSizeFormatter.format(item.sizeBytes))
                    if (!item.artist.isNullOrBlank()) append(" · ${item.artist}")
                    item.durationMillis?.let { append(" · ${FileSizeFormatter.formatDuration(it)}") }
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) SpeedoraColors.RoyalBlue else SpeedoraColors.TextMuted
            )
        }
    }
}

@Composable
private fun InstalledAppsList(
    apps: List<InstalledAppItem>,
    selectedIds: Set<String>,
    installedAppsRepository: InstalledAppsRepository,
    onToggleApp: (InstalledAppItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            val isSelected = "apk:${app.packageName}" in selectedIds
            val iconBitmap = remember(app.packageName) {
                installedAppsRepository.loadAppIcon(app.packageName)?.toBitmap(96, 96)?.asImageBitmap()
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isSelected) SpeedoraColors.RoyalBlue else MaterialTheme.colorScheme.outline),
                color = if (isSelected) SpeedoraColors.SoftBlue.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleApp(app) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(Icons.Outlined.Android, contentDescription = null, modifier = Modifier.size(44.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${app.packageName} · v${app.versionName} · ${FileSizeFormatter.format(app.apkSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!app.isExportable && app.restrictionReason != null) {
                            Text(
                                text = app.restrictionReason,
                                style = MaterialTheme.typography.labelSmall,
                                color = SpeedoraColors.WarningAmber
                            )
                        }
                    }
                    if (app.isExportable) {
                        Icon(
                            imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) SpeedoraColors.RoyalBlue else SpeedoraColors.TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SafFoldersBrowser(
    persistedTrees: List<Uri>,
    items: List<LocalFileItem>,
    selectedIds: Set<String>,
    onPickFolder: () -> Unit,
    onOpenFolder: (Uri) -> Unit,
    onToggleFile: (LocalFileItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("اختيار مجلد عبر منتقي أندرويد الآمن (SAF)")
        }
        Text(
            text = stringResource(R.string.perm_saf_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (persistedTrees.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(persistedTrees) { tree ->
                    FilterChip(
                        selected = false,
                        onClick = { onOpenFolder(tree) },
                        label = { Text(tree.lastPathSegment ?: "Folder", maxLines = 1) }
                    )
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                val isSelected = item.id in selectedIds
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (isSelected) SpeedoraColors.RoyalBlue else MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (item.isDirectory) onOpenFolder(item.uri) else onToggleFile(item)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                            contentDescription = null,
                            tint = SpeedoraColors.RoyalBlue
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!item.isDirectory) {
                                Text(
                                    FileSizeFormatter.format(item.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRationaleCard(
    title: String,
    description: String,
    onGrant: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = SpeedoraColors.SoftBlue,
            border = BorderStroke(1.dp, SpeedoraColors.BorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = SpeedoraColors.MidnightNavy)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = SpeedoraColors.TextSecondary)
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.RoyalBlue)
                ) {
                    Text(stringResource(R.string.action_grant_permission))
                }
            }
        }
    }
}

@Composable
private fun FilePreviewDialog(
    item: LocalFileItem,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                when (item.category) {
                    FileCategory.PHOTOS -> {
                        AsyncImage(
                            model = item.uri,
                            imageLoader = imageLoader,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                    FileCategory.VIDEOS, FileCategory.MUSIC -> {
                        val player = remember(item.uri) {
                            ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(item.uri))
                                prepare()
                                playWhenReady = true
                            }
                        }
                        DisposableEffect(player) {
                            onDispose { player.release() }
                        }
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply { this.player = player }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (item.category == FileCategory.VIDEOS) 220.dp else 110.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = "${item.mimeType}\n${FileSizeFormatter.format(item.sizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: FileCategory): String = when (category) {
    FileCategory.ALL -> stringResource(R.string.cat_all)
    FileCategory.PHOTOS -> stringResource(R.string.cat_photos)
    FileCategory.VIDEOS -> stringResource(R.string.cat_videos)
    FileCategory.MUSIC -> stringResource(R.string.cat_music)
    FileCategory.APPS -> stringResource(R.string.cat_apps)
    FileCategory.DOCUMENTS -> stringResource(R.string.cat_documents)
    FileCategory.ARCHIVES -> stringResource(R.string.cat_archives)
    FileCategory.DOWNLOADS -> stringResource(R.string.cat_downloads)
    FileCategory.FOLDERS -> stringResource(R.string.cat_folders)
    FileCategory.RECENT -> stringResource(R.string.cat_recent)
}

@Composable
private fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.NEWEST -> stringResource(R.string.sort_newest)
    SortOrder.OLDEST -> stringResource(R.string.sort_oldest)
    SortOrder.LARGEST -> stringResource(R.string.sort_largest)
    SortOrder.SMALLEST -> stringResource(R.string.sort_smallest)
    SortOrder.NAME_ASC -> stringResource(R.string.sort_az)
}
