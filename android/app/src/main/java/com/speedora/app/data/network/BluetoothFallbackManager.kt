package com.speedora.app.data.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.speedora.app.core.model.ConnectionQuality
import com.speedora.app.core.model.PairingState
import com.speedora.app.core.model.PeerDevice
import com.speedora.app.core.model.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Bluetooth RFCOMM fallback manager for small files and pairing assistance.
 * Honest throughput expectations: Bluetooth Classic RFCOMM is significantly slower than Wi-Fi Direct / LAN.
 */
class BluetoothFallbackManager(context: Context) {

    companion object {
        val SPEEDORA_BT_UUID: UUID = UUID.fromString("8E7B4A20-6D52-4F49-A29E-51D27C9A1001")
        const val SERVICE_NAME = "SPEEDORA_BT_FALLBACK"
    }

    private val btAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    fun getBondedPeers(): List<PeerDevice> {
        val adapter = btAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return runCatching {
            adapter.bondedDevices.map { dev ->
                PeerDevice(
                    id = "bt:${dev.address}",
                    name = dev.name ?: dev.address,
                    hostAddress = null,
                    port = 0,
                    transport = TransportType.BLUETOOTH_FALLBACK,
                    quality = ConnectionQuality.LOW_BANDWIDTH_FALLBACK,
                    pairingState = PairingState.DISCOVERED,
                    bluetoothAddress = dev.address
                )
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    suspend fun connectRfcomm(address: String): BluetoothSocket? = withContext(Dispatchers.IO) {
        val adapter = btAdapter ?: return@withContext null
        runCatching {
            adapter.cancelDiscovery()
            val device = adapter.getRemoteDevice(address)
            val socket = device.createRfcommSocketToServiceRecord(SPEEDORA_BT_UUID)
            socket.connect()
            socket
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    fun openServerSocket(): BluetoothServerSocket? {
        val adapter = btAdapter ?: return null
        return runCatching {
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPEEDORA_BT_UUID)
        }.getOrNull()
    }
}
