package com.speedora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedora.app.R
import com.speedora.app.core.designsystem.SpeedoraColors
import com.speedora.app.core.designsystem.SpeedoraEmblem
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.core.model.IncomingTransferOffer
import com.speedora.app.data.network.QrPairingCodec

@Composable
fun ReceiveScreen(
    deviceName: String,
    isListening: Boolean,
    activePort: Int,
    qrPayloadText: String?,
    incomingOffer: IncomingTransferOffer?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onRefreshQr: () -> Unit,
    onRespondOffer: (Boolean) -> Unit
) {
    LaunchedEffect(Unit) {
        onStartListening()
    }
    DisposableEffect(Unit) {
        onDispose { onStopListening() }
    }

    val qrBitmap = remember(qrPayloadText) {
        qrPayloadText?.let { runCatching { QrPairingCodec.generateQrBitmap(it, 520).asImageBitmap() }.getOrNull() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SpeedoraEmblem(size = 56.dp, animateSignal = isListening)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isListening) stringResource(R.string.home_status_advertising) else stringResource(R.string.home_status_idle),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "$deviceName · Port $activePort",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpeedoraColors.RoyalBlue
                )
            }
        }

        if (qrBitmap != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SpeedoraColors.PureWhite,
                    border = BorderStroke(1.dp, SpeedoraColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "SPEEDORA Temporary QR",
                            modifier = Modifier.size(230.dp)
                        )
                        Text(
                            text = "رمز اقتران مؤقت صالح لمدة 5 دقائق (بدون مفاتيح دائمة)",
                            style = MaterialTheme.typography.labelMedium,
                            color = SpeedoraColors.TextSecondary
                        )
                        OutlinedButton(onClick = onRefreshQr) {
                            Text("تجديد رمز QR")
                        }
                    }
                }
            }
        }
    }

    // Explicit Receiver Approval Dialog with SAS Code & File List
    if (incomingOffer != null) {
        AlertDialog(
            onDismissRequest = { onRespondOffer(false) },
            title = {
                Text(text = stringResource(R.string.sas_verify_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${incomingOffer.senderDeviceName} (${incomingOffer.senderEndpoint})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.sas_verify_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SpeedoraColors.SoftBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = incomingOffer.sasCode,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 6.sp
                            ),
                            color = SpeedoraColors.MidnightNavy,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Text(
                        text = "${incomingOffer.files.size} ملف · ${FileSizeFormatter.format(incomingOffer.totalBytes)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = SpeedoraColors.RoyalBlue
                    )
                    incomingOffer.files.take(4).forEach { file ->
                        Text(
                            text = "• ${file.sanitizedName} (${FileSizeFormatter.format(file.sizeBytes)})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onRespondOffer(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.RoyalBlue)
                ) {
                    Text(stringResource(R.string.action_accept))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onRespondOffer(false) }) {
                    Text(stringResource(R.string.action_reject))
                }
            }
        )
    }
}
