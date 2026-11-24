package com.speedora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speedora.app.R
import com.speedora.app.core.designsystem.SpeedoraColors
import com.speedora.app.core.designsystem.SpeedoraTransferEmblem
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.core.model.TransferProgressSnapshot
import com.speedora.app.core.model.TransferState

@Composable
fun TransferScreen(
    snapshot: TransferProgressSnapshot?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onBackHome: () -> Unit
) {
    if (snapshot == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("لا توجد عملية نقل نشطة حالياً", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBackHome) {
                Text(stringResource(R.string.nav_home))
            }
        }
        return
    }

    val isActive = snapshot.state == TransferState.TRANSFERRING ||
        snapshot.state == TransferState.CONNECTING ||
        snapshot.state == TransferState.VERIFYING_INTEGRITY
    val isCompleted = snapshot.state == TransferState.COMPLETED
    val isFailed = snapshot.state == TransferState.FAILED || snapshot.state == TransferState.CANCELLED

    val stateLabel = when (snapshot.state) {
        TransferState.CONNECTING -> stringResource(R.string.state_connecting)
        TransferState.AWAITING_RECEIVER_APPROVAL -> stringResource(R.string.state_awaiting_approval)
        TransferState.TRANSFERRING -> if (snapshot.direction.name == "SEND") stringResource(R.string.state_sending) else stringResource(R.string.state_receiving)
        TransferState.VERIFYING_INTEGRITY -> stringResource(R.string.state_verifying)
        TransferState.PAUSED -> stringResource(R.string.state_paused)
        TransferState.COMPLETED -> stringResource(R.string.state_completed)
        TransferState.FAILED -> stringResource(R.string.state_failed)
        TransferState.CANCELLED -> stringResource(R.string.state_cancelled)
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SpeedoraTransferEmblem(
            isActive = isActive,
            isCompleted = isCompleted,
            isFailed = isFailed
        )

        Text(
            text = stateLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = if (isFailed) SpeedoraColors.ErrorCrimson else MaterialTheme.colorScheme.onBackground
        )

        if (snapshot.sasCode.isNotBlank()) {
            Text(
                text = "SAS: ${snapshot.sasCode} · ${snapshot.peerDeviceName} · ${snapshot.transport.name}",
                style = MaterialTheme.typography.labelLarge,
                color = SpeedoraColors.RoyalBlue
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = snapshot.currentFileName.ifBlank { snapshot.peerDeviceName },
                    style = MaterialTheme.typography.titleMedium
                )
                LinearProgressIndicator(
                    progress = { snapshot.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = if (isFailed) SpeedoraColors.ErrorCrimson else SpeedoraColors.RoyalBlue,
                    trackColor = SpeedoraColors.SoftBlue
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${snapshot.progressPercent}% (${snapshot.currentFileIndex + 1}/${snapshot.totalFilesCount.coerceAtLeast(1)})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${FileSizeFormatter.format(snapshot.totalBytesTransferred)} / ${FileSizeFormatter.format(snapshot.totalSessionBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem("السرعة الفورية", FileSizeFormatter.formatSpeed(snapshot.instantBytesPerSec))
                    MetricItem("متوسط السرعة", FileSizeFormatter.formatSpeed(snapshot.averageBytesPerSec))
                    MetricItem("الوقت المتبقي", "${snapshot.etaSeconds}s")
                }

                if (snapshot.integrityVerified) {
                    Text(
                        text = "SHA-256 Verified · AES-256-GCM",
                        style = MaterialTheme.typography.labelMedium,
                        color = SpeedoraColors.SuccessGreen
                    )
                }
                snapshot.errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpeedoraColors.ErrorCrimson
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isCompleted || isFailed) {
            Button(
                onClick = onBackHome,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.RoyalBlue)
            ) {
                Text(stringResource(R.string.nav_home))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (snapshot.state == TransferState.PAUSED) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_resume))
                    }
                } else {
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_pause))
                    }
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel), color = SpeedoraColors.ErrorCrimson)
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}
