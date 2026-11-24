package com.speedora.app.core.model

enum class TransferDirection {
    SEND,
    RECEIVE
}

enum class TransferState {
    IDLE,
    CONNECTING,
    VERIFYING_SAS,
    AWAITING_RECEIVER_APPROVAL,
    TRANSFERRING,
    VERIFYING_INTEGRITY,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class IncomingTransferOffer(
    val sessionId: String,
    val senderDeviceName: String,
    val senderEndpoint: String,
    val sasCode: String,
    val transport: TransportType,
    val files: List<FileTransferDescriptor>,
    val totalBytes: Long,
    val isTrustedSender: Boolean
)

data class TransferProgressSnapshot(
    val sessionId: String,
    val direction: TransferDirection,
    val state: TransferState,
    val peerDeviceName: String,
    val transport: TransportType,
    val currentFileName: String,
    val currentFileMime: String,
    val currentFileIndex: Int,
    val totalFilesCount: Int,
    val currentFileBytesTransferred: Long,
    val currentFileTotalBytes: Long,
    val totalBytesTransferred: Long,
    val totalSessionBytes: Long,
    val instantBytesPerSec: Double,
    val averageBytesPerSec: Double,
    val etaSeconds: Long,
    val sasCode: String = "",
    val integrityVerified: Boolean = false,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalSessionBytes > 0L) {
            (totalBytesTransferred.toFloat() / totalSessionBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}
