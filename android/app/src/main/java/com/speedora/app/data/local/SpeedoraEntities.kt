package com.speedora.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val direction: String, // SEND or RECEIVE
    val peerDeviceName: String,
    val peerEndpoint: String,
    val transportType: String,
    val status: String, // COMPLETED, FAILED, CANCELLED
    val integrityVerified: Boolean,
    val sha256Hex: String,
    val averageSpeedBytesPerSec: Double,
    val timestampMillis: Long,
    val localSavedUri: String? = null
)

@Entity(tableName = "trusted_devices")
data class TrustedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val originalName: String,
    val lastTransportType: String,
    val lastKeyFingerprint: String,
    val autoAcceptIncoming: Boolean = false,
    val pairedAtMillis: Long,
    val lastConnectedMillis: Long
)

@Entity(tableName = "favorite_files")
data class FavoriteFileEntity(
    @PrimaryKey val uriString: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val categoryName: String,
    val addedAtMillis: Long
)
