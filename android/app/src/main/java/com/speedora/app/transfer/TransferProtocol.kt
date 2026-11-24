package com.speedora.app.transfer

import kotlinx.serialization.Serializable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer

enum class FrameType(val code: Byte) {
    HELLO(0x01),
    KEY_EXCHANGE(0x02),
    TRANSFER_OFFER(0x03),
    TRANSFER_ACCEPT(0x04),
    TRANSFER_REJECT(0x05),
    FILE_HEADER(0x06),
    DATA_CHUNK(0x07),
    FILE_DONE(0x08),
    FILE_VERIFIED(0x09),
    PAUSE_REQ(0x0A),
    RESUME_REQ(0x0B),
    CANCEL(0x0C),
    ERROR(0x0D);

    companion object {
        fun fromCode(code: Byte): FrameType =
            entries.firstOrNull { it.code == code }
                ?: throw IOException("Unknown SPEEDORA frame type: $code")
    }
}

data class ProtocolFrame(
    val version: Byte,
    val type: FrameType,
    val sequence: Long,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtocolFrame) return false
        return version == other.version && type == other.type && sequence == other.sequence && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

@Serializable
data class HelloPayload(
    val sessionId: String,
    val deviceId: String,
    val deviceName: String,
    val transport: String,
    val noncePrefixBase64: String
)

@Serializable
data class KeyExchangePayload(
    val publicKeyBase64: String,
    val keyFingerprintHint: String = ""
)

@Serializable
data class TransferOfferPayload(
    val sessionId: String,
    val files: List<com.speedora.app.core.model.FileTransferDescriptor>,
    val totalBytes: Long
)

@Serializable
data class TransferAcceptPayload(
    val accepted: Boolean,
    val resumeOffsets: Map<String, Long> = emptyMap(),
    val reason: String? = null
)

@Serializable
data class FileHeaderPayload(
    val fileIndex: Int,
    val fileId: String,
    val sanitizedName: String,
    val mimeType: String,
    val totalSizeBytes: Long,
    val resumeOffsetBytes: Long
)

@Serializable
data class FileDonePayload(
    val fileId: String,
    val sha256Hex: String
)

@Serializable
data class FileVerifiedPayload(
    val fileId: String,
    val verified: Boolean,
    val actualSha256Hex: String,
    val errorReason: String? = null
)

/**
 * Framed binary wire protocol for SPEEDORA direct device transfers.
 * Frame layout:
 * [4B Magic "SPDR"][1B Version][1B FrameType][8B Sequence][4B PayloadLen][Payload]
 */
object TransferProtocol {
    const val MAGIC: Int = 0x53504452 // 'SPDR'
    const val PROTOCOL_VERSION: Byte = 1
    const val MAX_FRAME_PAYLOAD_BYTES: Int = 2 * 1024 * 1024 // 2 MiB safety cap

    fun writeFrame(
        out: DataOutputStream,
        type: FrameType,
        sequence: Long,
        payload: ByteArray
    ) {
        require(payload.size <= MAX_FRAME_PAYLOAD_BYTES) {
            "Frame payload exceeds maximum allowed size: ${payload.size}"
        }
        out.writeInt(MAGIC)
        out.writeByte(PROTOCOL_VERSION.toInt())
        out.writeByte(type.code.toInt())
        out.writeLong(sequence)
        out.writeInt(payload.size)
        if (payload.isNotEmpty()) {
            out.write(payload)
        }
        out.flush()
    }

    fun readFrame(input: DataInputStream): ProtocolFrame {
        val magic = try {
            input.readInt()
        } catch (e: EOFException) {
            throw IOException("Unexpected end of stream reading frame magic", e)
        }
        if (magic != MAGIC) {
            throw IOException("Invalid SPEEDORA protocol magic: 0x${magic.toString(16)}")
        }
        val version = input.readByte()
        if (version != PROTOCOL_VERSION) {
            throw IOException("Unsupported protocol version: $version")
        }
        val type = FrameType.fromCode(input.readByte())
        val sequence = input.readLong()
        val length = input.readInt()
        if (length < 0 || length > MAX_FRAME_PAYLOAD_BYTES) {
            throw IOException("Invalid frame payload length: $length")
        }
        val payload = ByteArray(length)
        if (length > 0) {
            input.readFully(payload)
        }
        return ProtocolFrame(version = version, type = type, sequence = sequence, payload = payload)
    }

    fun buildAad(sessionId: String, type: FrameType, sequence: Long): ByteArray {
        val sidBytes = sessionId.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(2 + 8 + sidBytes.size)
            .put(PROTOCOL_VERSION)
            .put(type.code)
            .putLong(sequence)
            .put(sidBytes)
            .array()
    }
}
