package com.speedora.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speedora.app.R
import com.speedora.app.core.designsystem.SpeedoraColors
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.data.local.FavoriteFileEntity
import com.speedora.app.data.local.TransferHistoryEntity
import com.speedora.app.data.local.TrustedDeviceEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    entries: List<TransferHistoryEntity>,
    onClearHistory: () -> Unit
) {
    var filter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(entries, filter, search) {
        entries.filter { e ->
            val matchesStatus = filter == "ALL" || e.status == filter
            val matchesQuery = search.isBlank() ||
                e.fileName.contains(search, ignoreCase = true) ||
                e.peerDeviceName.contains(search, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.dest_history), style = MaterialTheme.typography.headlineSmall)
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.action_clear_all), color = SpeedoraColors.ErrorCrimson)
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.search_files_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                listOf(
                    "ALL" to "الكل",
                    "COMPLETED" to "ناجحة",
                    "FAILED" to "فاشلة",
                    "CANCELLED" to "ملغاة"
                )
            ) { (code, label) ->
                FilterChip(
                    selected = filter == code,
                    onClick = { filter = code },
                    label = { Text(label) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { item ->
                val dateStr = remember(item.timestampMillis) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestampMillis))
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                item.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                item.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (item.status == "COMPLETED") SpeedoraColors.SuccessGreen else SpeedoraColors.ErrorCrimson
                            )
                        }
                        Text(
                            text = "${item.direction} · ${item.peerDeviceName} · ${FileSizeFormatter.format(item.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$dateStr · ${item.transportType} · ${if (item.integrityVerified) "SHA-256 Verified" else "Unverified"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpeedoraColors.RoyalBlue
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح سجل النقل") },
            text = { Text("سيتم مسح سجل العمليات فقط؛ لن يتم حذف أي ملفات فعلية من هاتفك.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.ErrorCrimson),
                    onClick = {
                        confirmClear = false
                        onClearHistory()
                    }
                ) {
                    Text(stringResource(R.string.action_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun TrustedDevicesScreen(
    devices: List<TrustedDeviceEntity>,
    onRename: (String, String) -> Unit,
    onToggleAutoAccept: (String, Boolean) -> Unit,
    onForget: (String) -> Unit
) {
    var renamingDevice by remember { mutableStateOf<TrustedDeviceEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(stringResource(R.string.dest_trusted), style = MaterialTheme.typography.headlineSmall)
        }
        if (devices.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_no_trusted_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(devices, key = { it.deviceId }) { dev ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dev.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${dev.lastTransportType} · SAS ${dev.lastKeyFingerprint}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            renamingDevice = dev
                            renameText = dev.displayName
                        }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_rename))
                        }
                        IconButton(onClick = { onForget(dev.deviceId) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Forget", tint = SpeedoraColors.ErrorCrimson)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("السماح بالاستلام السريع عند تفعيل خيار الثقة", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = dev.autoAcceptIncoming,
                            onCheckedChange = { onToggleAutoAccept(dev.deviceId, it) }
                        )
                    }
                }
            }
        }
    }

    renamingDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { renamingDevice = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) onRename(dev.deviceId, renameText.trim())
                    renamingDevice = null
                }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingDevice = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun FavoritesScreen(
    favorites: List<FavoriteFileEntity>,
    onRemoveFavorite: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(stringResource(R.string.dest_favorites), style = MaterialTheme.typography.headlineSmall)
        }
        if (favorites.isEmpty()) {
            item {
                Text(
                    "لا توجد ملفات مفضلة بعد. أضف ملفاتك من مدير الملفات.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(favorites, key = { it.uriString }) { fav ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fav.fileName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(
                            "${FileSizeFormatter.format(fav.sizeBytes)} · ${fav.categoryName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { onRemoveFavorite(fav.uriString) }) {
                        Text("إزالة")
                    }
                }
            }
        }
    }
}
