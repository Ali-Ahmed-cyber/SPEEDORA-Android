package com.speedora.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.speedora.app.R
import com.speedora.app.core.designsystem.SpeedoraColors
import com.speedora.app.core.model.DeviceCapabilities
import com.speedora.app.core.model.PeerDevice
import com.speedora.app.core.model.TransportType
import com.speedora.app.data.network.QrPairingCodec
import com.speedora.app.transfer.TransferEngine
import java.util.concurrent.Executors

@Composable
fun DiscoveryScreen(
    capabilities: DeviceCapabilities,
    missingPermissions: List<String>,
    peers: List<PeerDevice>,
    isDiscovering: Boolean,
    selectedCount: Int,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onSelectPeer: (PeerDevice) -> Unit,
    onConnectManualIp: (String, Int) -> Unit,
    onOpenQrScanner: () -> Unit
) {
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf(TransferEngine.DEFAULT_PORT.toString()) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onStartDiscovery()
    }

    LaunchedEffect(Unit) {
        if (missingPermissions.isEmpty()) onStartDiscovery()
    }
    DisposableEffect(Unit) {
        onDispose { onStopDiscovery() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.discovery_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "ملفات جاهزة للإرسال: $selectedCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = SpeedoraColors.RoyalBlue
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenQrScanner) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_scan_qr))
                    }
                    Button(
                        onClick = { if (isDiscovering) onStopDiscovery() else onStartDiscovery() },
                        colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.RoyalBlue)
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                    }
                }
            }
        }

        if (missingPermissions.isNotEmpty()) {
            item {
                PermissionRationaleCard(
                    title = stringResource(R.string.perm_nearby_title),
                    description = stringResource(R.string.perm_nearby_desc),
                    onGrant = { permLauncher.launch(missingPermissions.toTypedArray()) }
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "قدرات الشبكة: Wi-Fi Direct (${if (capabilities.wifiDirectSupported) "مدعوم" else "غير مدعوم"}) · Wi-Fi (${if (capabilities.wifiEnabled) "مفعل" else "متوقف"})",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = stringResource(R.string.discovery_bluetooth_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (peers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.discovery_no_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(peers, key = { it.id }) { peer ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPeer(peer) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (peer.transport) {
                                TransportType.WIFI_DIRECT -> Icons.Outlined.WifiTethering
                                TransportType.BLUETOOTH_FALLBACK -> Icons.Outlined.Bluetooth
                                else -> Icons.Outlined.Wifi
                            },
                            contentDescription = null,
                            tint = SpeedoraColors.RoyalBlue
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peer.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${peer.transport.name} · ${peer.hostAddress ?: peer.macAddress ?: ""} · ${peer.pairingState.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(R.string.action_send),
                            style = MaterialTheme.typography.labelLarge,
                            color = SpeedoraColors.RoyalBlue
                        )
                    }
                }
            }
        }

        // Manual IP Fallback
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.discovery_manual_ip),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualHost,
                            onValueChange = { manualHost = it },
                            label = { Text("IP (e.g. 192.168.1.24)") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { onConnectManualIp(manualHost, manualPort.toIntOrNull() ?: TransferEngine.DEFAULT_PORT) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.RoyalBlue)
                    ) {
                        Text(stringResource(R.string.action_continue))
                    }
                }
            }
        }
    }
}

@Composable
fun QrScannerScreen(
    onQrScanned: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scannedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPerm = granted
    }

    if (!hasCameraPerm) {
        PermissionRationaleCard(
            title = stringResource(R.string.perm_camera_title),
            description = stringResource(R.string.perm_camera_desc),
            onGrant = { launcher.launch(Manifest.permission.CAMERA) }
        )
        return
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                    if (!scannedOnce) {
                                        val yPlane = imageProxy.planes.firstOrNull()
                                        if (yPlane != null) {
                                            val buffer = yPlane.buffer
                                            val bytes = ByteArray(buffer.remaining())
                                            buffer.get(bytes)
                                            val decoded = QrPairingCodec.decodeYuvFrame(
                                                bytes,
                                                imageProxy.width,
                                                imageProxy.height
                                            )
                                            if (decoded != null) {
                                                scannedOnce = true
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    onQrScanned(decoded)
                                                }
                                            }
                                        }
                                    }
                                    imageProxy.close()
                                }
                            }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }
        Surface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("وجّه الكاميرا نحو رمز QR المؤقت على جهاز المستلم", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}
