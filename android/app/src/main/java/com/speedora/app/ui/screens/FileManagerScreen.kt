package com.speedora.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.speedora.app.R
import com.speedora.app.core.designsystem.SpeedoraColors
import com.speedora.app.core.model.DuplicateGroup
import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.core.model.LocalFileItem
import com.speedora.app.data.files.DeviceStorageSummary
import com.speedora.app.data.files.MediaStoreRepository
import kotlinx.coroutines.launch

@Composable
fun FileManagerScreen(
    activeCategory: FileCategory,
    files: List<LocalFileItem>,
    storageSummary: DeviceStorageSummary,
    duplicates: List<DuplicateGroup>,
    isScanningDuplicates: Boolean,
    searchQuery: String,
    mediaStoreRepository: MediaStoreRepository,
    onCategorySelected: (FileCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleFavorite: (LocalFileItem) -> Unit,
    onScanDuplicates: () -> Unit,
    onShareFile: (LocalFileItem) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDeleteItem by remember { mutableStateOf<LocalFileItem?>(null) }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            onRefresh()
        }
    }

    val usedFraction = if (storageSummary.totalBytes > 0L) {
        (storageSummary.usedBytes.toFloat() / storageSummary.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        // Storage Usage Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("التخزين المحلي المتاح", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${FileSizeFormatter.format(storageSummary.availableBytes)} متاح من ${FileSizeFormatter.format(storageSummary.totalBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SpeedoraColors.RoyalBlue
                    )
                }
                LinearProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = SpeedoraColors.RoyalBlue,
                    trackColor = SpeedoraColors.PureWhite
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onScanDuplicates, enabled = !isScanningDuplicates) {
                        if (isScanningDuplicates) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.FindInPage, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("كشف التكرارات بـ SHA-256")
                    }
                    if (duplicates.isNotEmpty()) {
                        Text(
                            "${duplicates.size} مجموعة مكررة",
                            style = MaterialTheme.typography.labelMedium,
                            color = SpeedoraColors.WarningAmber
                        )
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                listOf(
                    FileCategory.PHOTOS to "الصور",
                    FileCategory.VIDEOS to "الفيديو",
                    FileCategory.MUSIC to "الصوتيات",
                    FileCategory.DOCUMENTS to "المستندات",
                    FileCategory.ARCHIVES to "المضغوطة",
                    FileCategory.DOWNLOADS to "التنزيلات",
                    FileCategory.RECENT to "الأخيرة"
                )
            ) { (cat, label) ->
                FilterChip(
                    selected = activeCategory == cat,
                    onClick = { onCategorySelected(cat) },
                    label = { Text(label) }
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.search_files_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (duplicates.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SpeedoraColors.WarningSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("نتائج فحص التكرار (SHA-256)", style = MaterialTheme.typography.titleMedium)
                            duplicates.take(5).forEach { grp ->
                                Text(
                                    text = "${grp.files.size} نسخ مطابقة (${FileSizeFormatter.format(grp.fileSizeBytes)}): ${grp.files.firstOrNull()?.name.orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            items(files, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${FileSizeFormatter.format(item.sizeBytes)} · ${item.mimeType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onToggleFavorite(item) }) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) SpeedoraColors.RoyalBlue else SpeedoraColors.TextMuted
                            )
                        }
                        IconButton(onClick = { onShareFile(item) }) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share))
                        }
                        IconButton(onClick = { pendingDeleteItem = item }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = SpeedoraColors.ErrorCrimson
                            )
                        }
                    }
                }
            }
        }
    }

    // Explicit Delete Confirmation Dialog
    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text("تأكيد حذف الملف") },
            text = { Text("هل تريد حذف \"${item.name}\" نهائياً من الجهاز؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.ErrorCrimson),
                    onClick = {
                        pendingDeleteItem = null
                        val sender = mediaStoreRepository.createDeleteRequestIfRequired(listOf(item.uri))
                        if (sender != null) {
                            deleteIntentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        } else {
                            scope.launch {
                                mediaStoreRepository.deleteDirectLegacy(item.uri)
                                onRefresh()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
