package com.speedora.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.speedora.app.R
import com.speedora.app.core.designsystem.LocalReducedMotion
import com.speedora.app.core.designsystem.SpeedoraColors
import com.speedora.app.core.designsystem.SpeedoraEmblem
import com.speedora.app.core.designsystem.SpeedoraLogoLockup
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.core.model.PeerDevice
import com.speedora.app.core.model.TransferProgressSnapshot
import com.speedora.app.core.model.TransferState
import com.speedora.app.data.local.TransferHistoryEntity
import com.speedora.app.data.local.TrustedDeviceEntity

@Composable
fun HomeScreen(
    deviceName: String,
    isDiscovering: Boolean,
    isAdvertising: Boolean,
    discoveredPeers: List<PeerDevice>,
    activeTransfer: TransferProgressSnapshot?,
    recentHistory: List<TransferHistoryEntity>,
    trustedDevices: List<TrustedDeviceEntity>,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onQrClick: () -> Unit,
    onFilesClick: () -> Unit,
    onAppsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMigrationClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onTrustedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onActiveTransferClick: () -> Unit
) {
    val activeConnectedName = activeTransfer?.takeIf {
        it.state == TransferState.TRANSFERRING || it.state == TransferState.CONNECTING
    }?.peerDeviceName

    val statusText = when {
        activeConnectedName != null -> stringResource(R.string.home_status_connected, activeConnectedName)
        isDiscovering -> stringResource(R.string.home_status_discovering)
        isAdvertising -> stringResource(R.string.home_status_advertising)
        else -> stringResource(R.string.home_status_idle)
    }

    val measuredSpeedText = activeTransfer?.takeIf {
        it.state == TransferState.TRANSFERRING && it.instantBytesPerSec > 0.0
    }?.let {
        stringResource(R.string.home_measured_speed, FileSizeFormatter.formatSpeed(it.instantBytesPerSec))
    } ?: stringResource(R.string.home_no_speed_yet)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedoraLogoLockup(
                    emblemSize = 36.dp,
                    showSubtitle = true,
                    subtitle = deviceName
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onQrClick) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode2,
                            contentDescription = stringResource(R.string.action_scan_qr),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.dest_settings),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Central Wireless Connection Visualization Hub
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WirelessHubVisualizer(
                        isActive = isDiscovering || isAdvertising || activeConnectedName != null,
                        peerCount = discoveredPeers.size,
                        onClick = onDiscoveryClick
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = measuredSpeedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpeedoraColors.RoyalBlue
                    )

                    if (activeTransfer != null && activeTransfer.state == TransferState.TRANSFERRING) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(onClick = onActiveTransferClick) {
                            Text("${activeTransfer.progressPercent}% · ${activeTransfer.currentFileName}")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary SEND & RECEIVE Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onSendClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpeedoraColors.RoyalBlue,
                                contentColor = SpeedoraColors.PureWhite
                            )
                        ) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_send),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FilledTonalButton(
                            onClick = onReceiveClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SpeedoraColors.PureWhite,
                                contentColor = SpeedoraColors.MidnightNavy
                            ),
                            border = BorderStroke(1.dp, SpeedoraColors.BorderSubtle)
                        ) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = null, tint = SpeedoraColors.RoyalBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_receive),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        // Quick Access Destinations
        item {
            Text(
                text = stringResource(R.string.home_quick_access),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    QuickActionTile(stringResource(R.string.nav_files), Icons.Outlined.Folder, onFilesClick)
                }
                item {
                    QuickActionTile(stringResource(R.string.dest_apps), Icons.Outlined.Apps, onAppsClick)
                }
                item {
                    QuickActionTile(stringResource(R.string.dest_migration), Icons.Outlined.SwapHoriz, onMigrationClick)
                }
                item {
                    QuickActionTile(stringResource(R.string.dest_discovery), Icons.Outlined.Devices, onDiscoveryClick)
                }
                item {
                    QuickActionTile(stringResource(R.string.dest_history), Icons.Outlined.History, onHistoryClick)
                }
                item {
                    QuickActionTile(stringResource(R.string.dest_favorites), Icons.Outlined.FavoriteBorder, onFavoritesClick)
                }
                item {
                    QuickActionTile(stringResource(R.string.dest_trusted), Icons.Outlined.Security, onTrustedClick)
                }
            }
        }

        // Trusted Devices Section
        item {
            SectionHeaderRow(
                title = stringResource(R.string.home_trusted_devices),
                onSeeAll = onTrustedClick
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (trustedDevices.isEmpty()) {
                QuietEmptyLine(stringResource(R.string.home_no_trusted_devices))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(trustedDevices, key = { it.deviceId }) { device ->
                        OutlinedCard(
                            onClick = onTrustedClick,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = device.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = device.lastTransportType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Transfers Section
        item {
            SectionHeaderRow(
                title = stringResource(R.string.home_recent_transfers),
                onSeeAll = onHistoryClick
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (recentHistory.isEmpty()) {
                QuietEmptyLine(stringResource(R.string.home_no_recent_transfers))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentHistory.forEach { entry ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            onClick = onHistoryClick
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.fileName,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.peerDeviceName} · ${FileSizeFormatter.format(entry.fileSizeBytes)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = entry.status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.status == "COMPLETED") SpeedoraColors.SuccessGreen else SpeedoraColors.ErrorCrimson
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
private fun WirelessHubVisualizer(
    isActive: Boolean,
    peerCount: Int,
    onClick: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "radar_hub")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            drawCircle(
                color = SpeedoraColors.PureWhite,
                radius = maxR * 0.44f,
                center = center
            )

            if (isActive && !reducedMotion) {
                val r1 = maxR * (0.48f + wave * 0.48f)
                val r2 = maxR * (0.48f + ((wave + 0.5f) % 1f) * 0.48f)
                drawCircle(
                    color = SpeedoraColors.RoyalBlue.copy(alpha = (1f - wave) * 0.35f),
                    radius = r1,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = SpeedoraColors.RoyalBlue.copy(alpha = (1f - ((wave + 0.5f) % 1f)) * 0.35f),
                    radius = r2,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            } else {
                drawCircle(
                    color = SpeedoraColors.BorderSubtle,
                    radius = maxR * 0.68f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = SpeedoraColors.BorderSubtle.copy(alpha = 0.6f),
                    radius = maxR * 0.90f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = SpeedoraColors.PureWhite,
            shadowElevation = 2.dp,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                SpeedoraEmblem(
                    size = 42.dp,
                    animateSignal = isActive && !reducedMotion
                )
            }
        }

        if (peerCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = SpeedoraColors.RoyalBlue
            ) {
                Text(
                    text = "$peerCount",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .width(92.dp)
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = SpeedoraColors.RoyalBlue,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.action_continue),
            style = MaterialTheme.typography.labelLarge,
            color = SpeedoraColors.RoyalBlue,
            modifier = Modifier.clickable(onClick = onSeeAll)
        )
    }
}

@Composable
private fun QuietEmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}
