package com.speedora.app.core.model

import kotlinx.serialization.Serializable

enum class TransportType {
    WIFI_DIRECT,
    LOCAL_WIFI_NSD,
    HOTSPOT_ASSISTED,
    BLUETOOTH_FALLBACK,
    MANUAL_IP
}

enum class PairingState {
    DISCOVERED,
    CONNECTING,
    VERIFYING_SAS,
    PAIRED_TEMPORARY,
    TRUSTED,
    FAILED
}

enum class ConnectionQuality {
    HIGH_BANDWIDTH,
    STANDARD_LOCAL,
    LOW_BANDWIDTH_FALLBACK,
    UNKNOWN
}

data class PeerDevice(
    val id: String,
    val name: String,
    val hostAddress: String?,
    val port: Int,
    val transport: TransportType,
    val quality: ConnectionQuality,
    val pairingState: PairingState = PairingState.DISCOVERED,
    val macAddress: String? = null,
    val bluetoothAddress: String? = null,
    val isTrusted: Boolean = false,
    val signalRssiDbm: Int? = null
)

data class DeviceCapabilities(
    val wifiDirectSupported: Boolean,
    val wifiEnabled: Boolean,
    val nsdSupported: Boolean,
    val bluetoothSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val androidSdkVersion: Int
)

/**
 * Ephemeral QR pairing payload. Contains ONLY temporary session parameters
 * and never embeds permanent private keys or persistent credentials.
 */
@Serializable
data class QrPairingPayload(
    val version: Int = 1,
    val sessionId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val transport: String,
    val ephemeralPublicKeyBase64: String,
    val expiresAtEpochMillis: Long
)
