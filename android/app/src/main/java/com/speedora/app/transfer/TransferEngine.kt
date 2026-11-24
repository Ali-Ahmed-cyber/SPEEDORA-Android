package com.speedora.app.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.speedora.app.core.model.FileTransferDescriptor
import com.speedora.app.core.model.IncomingTransferOffer
import com.speedora.app.core.model.LocalFileItem
import com.speedora.app.core.model.PeerDevice
import com.speedora.app.core.model.TransferDirection
import com.speedora.app.core.model.TransferProgressSnapshot
import com.speedora.app.core.model.TransferState
import com.speedora.app.core.model.TransportType
import com.speedora.app.core.security.FilenameSanitizer
import com.speedora.app.core.security.SpeedoraCrypto
import com.speedora.app.data.local.SettingsDataStore
import com.speedora.app.data.local.TransferHistoryDao
import com.speedora.app.data.local.TransferHistoryEntity
import com.speedora.app.data.local.TrustedDeviceDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.SecretKey

interface TransferSource {
    val descriptor: FileTransferDescriptor
    fun openStream(): InputStream
}

interface TransferSink {
    fun openPartFile(fileId: String, sanitizedName: String, expectedSize: Long): File
    fun commitVerifiedFile(partFile: File, descriptor: FileTransferDescriptor): String?
}

/**
 * Pure stream-capable + socket-backed encrypted transfer engine.
 * Handles ECDH P-256 handshake, HKDF key derivation, AES-256-GCM chunk encryption,
 * adaptive buffer sizing, pause/resume/cancel, SHA-256 integrity checks, and real throughput measurement.
 */
class SpeedoraTransferSessionRunner {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val paused = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    fun requestPause() { paused.set(true) }
    fun requestResume() { paused.set(false) }
    fun requestCancel() {
        cancelled.set(true)
        paused.set(false)
    }

    suspend fun runSendSession(
        inputRaw: InputStream,
        outputRaw: java.io.OutputStream,
        sessionId: String,
        localDeviceId: String,
        localDeviceName: String,
        peerDeviceName: String,
        transport: TransportType,
        sources: List<TransferSource>,
        onProgress: (TransferProgressSnapshot) -> Unit,
        onSasGenerated: (String) -> Unit = {}
    ): Result<List<FileTransferDescriptor>> = withContext(Dispatchers.IO) {
        val dataIn = DataInputStream(BufferedInputStream(inputRaw, 65536))
        val dataOut = DataOutputStream(BufferedOutputStream(outputRaw, 65536))
        val totalBytes = sources.sumOf { it.descriptor.sizeBytes }

        try {
            onProgress(
                baseSnapshot(
                    sessionId, TransferDirection.SEND, TransferState.CONNECTING,
                    peerDeviceName, transport, "", "", 0, sources.size, 0L, 0L, 0L, totalBytes
                )
            )

            val localNoncePrefix = SpeedoraCrypto.generateRandomBytes(4)
            val hello = HelloPayload(
                sessionId = sessionId,
                deviceId = localDeviceId,
                deviceName = localDeviceName,
                transport = transport.name,
                noncePrefixBase64 = Base64.getEncoder().encodeToString(localNoncePrefix)
            )
            var outSeq = 1L
            TransferProtocol.writeFrame(dataOut, FrameType.HELLO, outSeq++, json.encodeToString(hello).toByteArray())

            val peerHelloFrame = TransferProtocol.readFrame(dataIn)
            if (peerHelloFrame.type != FrameType.HELLO) throw IOException("Expected peer HELLO")
            val peerHello = json.decodeFromString<HelloPayload>(peerHelloFrame.payload.toString(Charsets.UTF_8))

            // Ephemeral ECDH P-256 key agreement
            val keyPair = SpeedoraCrypto.generateEphemeralKeyPair()
            val keyExchange = KeyExchangePayload(SpeedoraCrypto.encodePublicKeyBase64(keyPair.public))
            TransferProtocol.writeFrame(dataOut, FrameType.KEY_EXCHANGE, outSeq++, json.encodeToString(keyExchange).toByteArray())

            val peerKeyFrame = TransferProtocol.readFrame(dataIn)
            if (peerKeyFrame.type != FrameType.KEY_EXCHANGE) throw IOException("Expected peer KEY_EXCHANGE")
            val peerKeyPayload = json.decodeFromString<KeyExchangePayload>(peerKeyFrame.payload.toString(Charsets.UTF_8))
            val peerPublicKey = SpeedoraCrypto.decodePublicKeyBase64(peerKeyPayload.publicKeyBase64)

            val keys = SpeedoraCrypto.deriveSessionKeys(keyPair, peerPublicKey, sessionId)
            onSasGenerated(keys.sasCode)

            // Send encrypted transfer offer
            onProgress(
                baseSnapshot(
                    sessionId, TransferDirection.SEND, TransferState.AWAITING_RECEIVER_APPROVAL,
                    peerHello.deviceName, transport, "", "", 0, sources.size, 0L, 0L, 0L, totalBytes,
                    sasCode = keys.sasCode
                )
            )

            val offer = TransferOfferPayload(
                sessionId = sessionId,
                files = sources.map { it.descriptor },
                totalBytes = totalBytes
            )
            val offerPlain = json.encodeToString(offer).toByteArray(Charsets.UTF_8)
            val offerSeq = outSeq++
            val offerEnc = encryptWithSeq(keys.senderToReceiverKey, localNoncePrefix, sessionId, FrameType.TRANSFER_OFFER, offerSeq, offerPlain)
            TransferProtocol.writeFrame(dataOut, FrameType.TRANSFER_OFFER, offerSeq, offerEnc)

            // Wait for receiver accept/reject
            val responseFrame = TransferProtocol.readFrame(dataIn)
            if (responseFrame.type == FrameType.TRANSFER_REJECT) {
                throw IOException("تم رفض النقل من قِبل المستلم")
            }
            if (responseFrame.type != FrameType.TRANSFER_ACCEPT) {
                throw IOException("استجابة غير متوقعة من المستلم: ${responseFrame.type}")
            }
            val peerNoncePrefix = Base64.getDecoder().decode(peerHello.noncePrefixBase64)
            val acceptPlain = decryptWithSeq(keys.receiverToSenderKey, peerNoncePrefix, sessionId, FrameType.TRANSFER_ACCEPT, responseFrame.sequence, responseFrame.payload)
            val acceptPayload = json.decodeFromString<TransferAcceptPayload>(acceptPlain.toString(Charsets.UTF_8))
            if (!acceptPayload.accepted) {
                throw IOException(acceptPayload.reason ?: "تم رفض النقل")
            }

            // Stream each file in adaptive chunks
            val sessionStartNanos = System.nanoTime()
            var windowStartNanos = sessionStartNanos
            var windowBytes = 0L
            var totalTransferred = 0L
            var currentInstantSpeed = 0.0
            var chunkSize = if (transport == TransportType.BLUETOOTH_FALLBACK) 16 * 1024 else 64 * 1024

            for ((index, source) in sources.withIndex()) {
                if (cancelled.get()) {
                    TransferProtocol.writeFrame(dataOut, FrameType.CANCEL, outSeq++, ByteArray(0))
                    throw IOException("تم إلغاء النقل")
                }

                val desc = source.descriptor
                val resumeOffset = (acceptPayload.resumeOffsets[desc.id] ?: 0L).coerceIn(0L, desc.sizeBytes)
                totalTransferred += resumeOffset

                val header = FileHeaderPayload(
                    fileIndex = index,
                    fileId = desc.id,
                    sanitizedName = desc.sanitizedName,
                    mimeType = desc.mimeType,
                    totalSizeBytes = desc.sizeBytes,
                    resumeOffsetBytes = resumeOffset
                )
                val hdrSeq = outSeq++
                val hdrEnc = encryptWithSeq(keys.senderToReceiverKey, localNoncePrefix, sessionId, FrameType.FILE_HEADER, hdrSeq, json.encodeToString(header).toByteArray())
                TransferProtocol.writeFrame(dataOut, FrameType.FILE_HEADER, hdrSeq, hdrEnc)

                var fileTransferred = resumeOffset
                source.openStream().use { rawStream ->
                    if (resumeOffset > 0L) {
                        var toSkip = resumeOffset
                        val skipBuf = ByteArray(16384)
                        while (toSkip > 0L) {
                            val skipped = rawStream.read(skipBuf, 0, minOf(skipBuf.size.toLong(), toSkip).toInt())
                            if (skipped <= 0) break
                            toSkip -= skipped
                        }
                    }

                    val buffer = ByteArray(chunkSize)
                    while (true) {
                        while (paused.get() && !cancelled.get()) {
                            onProgress(
                                baseSnapshot(
                                    sessionId, TransferDirection.SEND, TransferState.PAUSED,
                                    peerHello.deviceName, transport, desc.sanitizedName, desc.mimeType,
                                    index, sources.size, fileTransferred, desc.sizeBytes,
                                    totalTransferred, totalBytes, 0.0,
                                    computeAverageSpeed(totalTransferred, sessionStartNanos), 0L, keys.sasCode
                                )
                            )
                            delay(150)
                        }
                        if (cancelled.get()) {
                            TransferProtocol.writeFrame(dataOut, FrameType.CANCEL, outSeq++, ByteArray(0))
                            throw IOException("تم إلغاء النقل")
                        }

                        val read = rawStream.read(buffer)
                        if (read == -1) break
                        if (read == 0) continue

                        val chunkSlice = if (read == buffer.size) buffer else buffer.copyOf(read)
                        val chunkSeq = outSeq++
                        val encryptedChunk = encryptWithSeq(
                            keys.senderToReceiverKey, localNoncePrefix, sessionId,
                            FrameType.DATA_CHUNK, chunkSeq, chunkSlice
                        )
                        TransferProtocol.writeFrame(dataOut, FrameType.DATA_CHUNK, chunkSeq, encryptedChunk)

                        fileTransferred += read
                        totalTransferred += read
                        windowBytes += read

                        val now = System.nanoTime()
                        val windowElapsedSec = (now - windowStartNanos) / 1_000_000_000.0
                        if (windowElapsedSec >= 0.35) {
                            currentInstantSpeed = windowBytes / windowElapsedSec
                            windowBytes = 0L
                            windowStartNanos = now
                            if (transport != TransportType.BLUETOOTH_FALLBACK && currentInstantSpeed > 12_000_000.0 && chunkSize < 256 * 1024) {
                                chunkSize = 256 * 1024
                            }
                        }

                        val avgSpeed = computeAverageSpeed(totalTransferred, sessionStartNanos)
                        val remaining = (totalBytes - totalTransferred).coerceAtLeast(0L)
                        val eta = if (avgSpeed > 1.0) (remaining / avgSpeed).toLong() else 0L

                        onProgress(
                            baseSnapshot(
                                sessionId, TransferDirection.SEND, TransferState.TRANSFERRING,
                                peerHello.deviceName, transport, desc.sanitizedName, desc.mimeType,
                                index, sources.size, fileTransferred, desc.sizeBytes,
                                totalTransferred, totalBytes, currentInstantSpeed, avgSpeed, eta, keys.sasCode
                            )
                        )
                    }
                }

                // Signal file completion and verify receiver SHA-256 confirmation
                onProgress(
                    baseSnapshot(
                        sessionId, TransferDirection.SEND, TransferState.VERIFYING_INTEGRITY,
                        peerHello.deviceName, transport, desc.sanitizedName, desc.mimeType,
                        index, sources.size, fileTransferred, desc.sizeBytes,
                        totalTransferred, totalBytes, currentInstantSpeed,
                        computeAverageSpeed(totalTransferred, sessionStartNanos), 0L, keys.sasCode
                    )
                )

                val doneSeq = outSeq++
                val donePayload = FileDonePayload(fileId = desc.id, sha256Hex = desc.sha256)
                val doneEnc = encryptWithSeq(keys.senderToReceiverKey, localNoncePrefix, sessionId, FrameType.FILE_DONE, doneSeq, json.encodeToString(donePayload).toByteArray())
                TransferProtocol.writeFrame(dataOut, FrameType.FILE_DONE, doneSeq, doneEnc)

                val verifiedFrame = TransferProtocol.readFrame(dataIn)
                if (verifiedFrame.type != FrameType.FILE_VERIFIED) {
                    throw IOException("لم يرسل المستلم نتيجة التحقق من سلامة الملف")
                }
                val verifiedPlain = decryptWithSeq(keys.receiverToSenderKey, peerNoncePrefix, sessionId, FrameType.FILE_VERIFIED, verifiedFrame.sequence, verifiedFrame.payload)
                val verifiedResult = json.decodeFromString<FileVerifiedPayload>(verifiedPlain.toString(Charsets.UTF_8))
                if (!verifiedResult.verified) {
                    throw IOException("فشل التحقق من SHA-256 للملف ${desc.sanitizedName}")
                }
            }

            val finalAvg = computeAverageSpeed(totalTransferred, sessionStartNanos)
            onProgress(
                baseSnapshot(
                    sessionId, TransferDirection.SEND, TransferState.COMPLETED,
                    peerHello.deviceName, transport, sources.lastOrNull()?.descriptor?.sanitizedName.orEmpty(),
                    sources.lastOrNull()?.descriptor?.mimeType.orEmpty(),
                    (sources.size - 1).coerceAtLeast(0), sources.size,
                    sources.lastOrNull()?.descriptor?.sizeBytes ?: 0L,
                    sources.lastOrNull()?.descriptor?.sizeBytes ?: 0L,
                    totalBytes, totalBytes, finalAvg, finalAvg, 0L, keys.sasCode, integrityVerified = true
                )
            )
            Result.success(sources.map { it.descriptor })
        } catch (e: Exception) {
            val isCancel = cancelled.get()
            onProgress(
                baseSnapshot(
                    sessionId, TransferDirection.SEND,
                    if (isCancel) TransferState.CANCELLED else TransferState.FAILED,
                    peerDeviceName, transport, "", "", 0, sources.size, 0L, 0L, 0L, totalBytes,
                    errorMessage = e.message
                )
            )
            Result.failure(e)
        }
    }

    suspend fun runReceiveSession(
        inputRaw: InputStream,
        outputRaw: java.io.OutputStream,
        localDeviceId: String,
        localDeviceName: String,
        transport: TransportType,
        sink: TransferSink,
        awaitUserApproval: suspend (IncomingTransferOffer) -> TransferAcceptPayload,
        onProgress: (TransferProgressSnapshot) -> Unit,
        onFileCommitted: (FileTransferDescriptor, String?, String, Double) -> Unit = { _, _, _, _ -> }
    ): Result<List<FileTransferDescriptor>> = withContext(Dispatchers.IO) {
        val dataIn = DataInputStream(BufferedInputStream(inputRaw, 65536))
        val dataOut = DataOutputStream(BufferedOutputStream(outputRaw, 65536))
        var sessionId = ""
        var senderName = ""

        try {
            val peerHelloFrame = TransferProtocol.readFrame(dataIn)
            if (peerHelloFrame.type != FrameType.HELLO) throw IOException("Expected sender HELLO")
            val peerHello = json.decodeFromString<HelloPayload>(peerHelloFrame.payload.toString(Charsets.UTF_8))
            sessionId = peerHello.sessionId
            senderName = peerHello.deviceName
            val peerNoncePrefix = Base64.getDecoder().decode(peerHello.noncePrefixBase64)

            val localNoncePrefix = SpeedoraCrypto.generateRandomBytes(4)
            val localHello = HelloPayload(
                sessionId = sessionId,
                deviceId = localDeviceId,
                deviceName = localDeviceName,
                transport = transport.name,
                noncePrefixBase64 = Base64.getEncoder().encodeToString(localNoncePrefix)
            )
            var outSeq = 1L
            TransferProtocol.writeFrame(dataOut, FrameType.HELLO, outSeq++, json.encodeToString(localHello).toByteArray())

            // Ephemeral ECDH P-256 key exchange
            val peerKeyFrame = TransferProtocol.readFrame(dataIn)
            if (peerKeyFrame.type != FrameType.KEY_EXCHANGE) throw IOException("Expected sender KEY_EXCHANGE")
            val peerKey = SpeedoraCrypto.decodePublicKeyBase64(
                json.decodeFromString<KeyExchangePayload>(peerKeyFrame.payload.toString(Charsets.UTF_8)).publicKeyBase64
            )

            val keyPair = SpeedoraCrypto.generateEphemeralKeyPair()
            TransferProtocol.writeFrame(
                dataOut, FrameType.KEY_EXCHANGE, outSeq++,
                json.encodeToString(KeyExchangePayload(SpeedoraCrypto.encodePublicKeyBase64(keyPair.public))).toByteArray()
            )
            val keys = SpeedoraCrypto.deriveSessionKeys(keyPair, peerPublicKey = peerKey, sessionId = sessionId)

            // Receive encrypted transfer offer
            val offerFrame = TransferProtocol.readFrame(dataIn)
            if (offerFrame.type != FrameType.TRANSFER_OFFER) throw IOException("Expected TRANSFER_OFFER")
            val offerPlain = decryptWithSeq(keys.senderToReceiverKey, peerNoncePrefix, sessionId, FrameType.TRANSFER_OFFER, offerFrame.sequence, offerFrame.payload)
            val offerPayload = json.decodeFromString<TransferOfferPayload>(offerPlain.toString(Charsets.UTF_8))

            val incomingOffer = IncomingTransferOffer(
                sessionId = sessionId,
                senderDeviceName = senderName,
                senderEndpoint = peerHello.deviceId,
                sasCode = keys.sasCode,
                transport = transport,
                files = offerPayload.files,
                totalBytes = offerPayload.totalBytes,
                isTrustedSender = false
            )

            onProgress(
                baseSnapshot(
                    sessionId, TransferDirection.RECEIVE, TransferState.AWAITING_RECEIVER_APPROVAL,
                    senderName, transport, "", "", 0, offerPayload.files.size, 0L, 0L, 0L, offerPayload.totalBytes,
                    sasCode = keys.sasCode
                )
            )

            val approval = awaitUserApproval(incomingOffer)
            if (!approval.accepted) {
                TransferProtocol.writeFrame(dataOut, FrameType.TRANSFER_REJECT, outSeq++, ByteArray(0))
                onProgress(
                    baseSnapshot(
                        sessionId, TransferDirection.RECEIVE, TransferState.CANCELLED,
                        senderName, transport, "", "", 0, offerPayload.files.size, 0L, 0L, 0L, offerPayload.totalBytes,
                        sasCode = keys.sasCode
                    )
                )
                return@withContext Result.failure(IOException("تم رفض الاستلام من قِبل المستخدم"))
            }

            val acceptSeq = outSeq++
            val acceptEnc = encryptWithSeq(keys.receiverToSenderKey, localNoncePrefix, sessionId, FrameType.TRANSFER_ACCEPT, acceptSeq, json.encodeToString(approval).toByteArray())
            TransferProtocol.writeFrame(dataOut, FrameType.TRANSFER_ACCEPT, acceptSeq, acceptEnc)

            val sessionStartNanos = System.nanoTime()
            var windowStartNanos = sessionStartNanos
            var windowBytes = 0L
            var currentInstantSpeed = 0.0
            var totalReceived = 0L
            val receivedDescriptors = mutableListOf<FileTransferDescriptor>()

            for ((index, expectedDesc) in offerPayload.files.withIndex()) {
                val hdrFrame = TransferProtocol.readFrame(dataIn)
                if (hdrFrame.type == FrameType.CANCEL) throw IOException("ألغى المرسل عملية النقل")
                if (hdrFrame.type != FrameType.FILE_HEADER) throw IOException("Expected FILE_HEADER")

                val hdrPlain = decryptWithSeq(keys.senderToReceiverKey, peerNoncePrefix, sessionId, FrameType.FILE_HEADER, hdrFrame.sequence, hdrFrame.payload)
                val hdr = json.decodeFromString<FileHeaderPayload>(hdrPlain.toString(Charsets.UTF_8))

                val partFile = sink.openPartFile(hdr.fileId, hdr.sanitizedName, hdr.totalSizeBytes)
                val resumeAt = hdr.resumeOffsetBytes.coerceAtMost(partFile.length())
                totalReceived += resumeAt
                var fileReceived = resumeAt

                val digest = MessageDigest.getInstance("SHA-256")
                if (resumeAt > 0L) {
                    FileInputStream(partFile).use { existing ->
                        val buf = ByteArray(65536)
                        var remainingToHash = resumeAt
                        while (remainingToHash > 0L) {
                            val r = existing.read(buf, 0, minOf(buf.size.toLong(), remainingToHash).toInt())
                            if (r <= 0) break
                            digest.update(buf, 0, r)
                            remainingToHash -= r
                        }
                    }
                }

                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.seek(resumeAt)
                    raf.setLength(resumeAt)
                    while (true) {
                        val frame = TransferProtocol.readFrame(dataIn)
                        when (frame.type) {
                            FrameType.CANCEL -> throw IOException("تم إلغاء النقل من المرسل")
                            FrameType.DATA_CHUNK -> {
                                val plainChunk = decryptWithSeq(keys.senderToReceiverKey, peerNoncePrefix, sessionId, FrameType.DATA_CHUNK, frame.sequence, frame.payload)
                                raf.write(plainChunk)
                                digest.update(plainChunk)
                                fileReceived += plainChunk.size
                                totalReceived += plainChunk.size
                                windowBytes += plainChunk.size

                                val now = System.nanoTime()
                                val elapsed = (now - windowStartNanos) / 1_000_000_000.0
                                if (elapsed >= 0.35) {
                                    currentInstantSpeed = windowBytes / elapsed
                                    windowBytes = 0L
                                    windowStartNanos = now
                                }
                                val avg = computeAverageSpeed(totalReceived, sessionStartNanos)
                                val eta = if (avg > 1.0) ((offerPayload.totalBytes - totalReceived).coerceAtLeast(0L) / avg).toLong() else 0L

                                onProgress(
                                    baseSnapshot(
                                        sessionId, TransferDirection.RECEIVE, TransferState.TRANSFERRING,
                                        senderName, transport, hdr.sanitizedName, hdr.mimeType,
                                        index, offerPayload.files.size, fileReceived, hdr.totalSizeBytes,
                                        totalReceived, offerPayload.totalBytes, currentInstantSpeed, avg, eta, keys.sasCode
                                    )
                                )
                            }
                            FrameType.FILE_DONE -> {
                                val donePlain = decryptWithSeq(keys.senderToReceiverKey, peerNoncePrefix, sessionId, FrameType.FILE_DONE, frame.sequence, frame.payload)
                                val done = json.decodeFromString<FileDonePayload>(donePlain.toString(Charsets.UTF_8))
                                val computedSha = digest.digest().joinToString("") { "%02x".format(it) }
                                val matches = computedSha.equals(done.sha256Hex, ignoreCase = true)

                                val verSeq = outSeq++
                                val verPayload = FileVerifiedPayload(
                                    fileId = hdr.fileId,
                                    verified = matches,
                                    actualSha256Hex = computedSha,
                                    errorReason = if (matches) null else "SHA-256 mismatch"
                                )
                                val verEnc = encryptWithSeq(keys.receiverToSenderKey, localNoncePrefix, sessionId, FrameType.FILE_VERIFIED, verSeq, json.encodeToString(verPayload).toByteArray())
                                TransferProtocol.writeFrame(dataOut, FrameType.FILE_VERIFIED, verSeq, verEnc)

                                if (!matches) {
                                    partFile.delete()
                                    throw IOException("فشل التحقق من سلامة الملف ${hdr.sanitizedName}")
                                }

                                val committedUri = sink.commitVerifiedFile(partFile, expectedDesc)
                                val avg = computeAverageSpeed(totalReceived, sessionStartNanos)
                                onFileCommitted(expectedDesc, committedUri, senderName, avg)
                                receivedDescriptors.add(expectedDesc)
                                break
                            }
                            else -> throw IOException("إطار غير متوقع أثناء الاستلام: ${frame.type}")
                        }
                    }
                }
            }

            val finalAvg = computeAverageSpeed(totalReceived, sessionStartNanos)
            onProgress(
                baseSnapshot(
                    sessionId, TransferDirection.RECEIVE, TransferState.COMPLETED,
                    senderName, transport,
                    receivedDescriptors.lastOrNull()?.sanitizedName.orEmpty(),
                    receivedDescriptors.lastOrNull()?.mimeType.orEmpty(),
                    (receivedDescriptors.size - 1).coerceAtLeast(0), receivedDescriptors.size,
                    receivedDescriptors.lastOrNull()?.sizeBytes ?: 0L,
                    receivedDescriptors.lastOrNull()?.sizeBytes ?: 0L,
                    offerPayload.totalBytes, offerPayload.totalBytes, finalAvg, finalAvg, 0L, keys.sasCode,
                    integrityVerified = true
                )
            )
            Result.success(receivedDescriptors)
        } catch (e: Exception) {
            onProgress(
                baseSnapshot(
                    sessionId, TransferDirection.RECEIVE,
                    if (cancelled.get()) TransferState.CANCELLED else TransferState.FAILED,
                    senderName, transport, "", "", 0, 0, 0L, 0L, 0L, 0L,
                    errorMessage = e.message
                )
            )
            Result.failure(e)
        }
    }

    private fun encryptWithSeq(
        key: SecretKey,
        noncePrefix: ByteArray,
        sessionId: String,
        type: FrameType,
        sequence: Long,
        plaintext: ByteArray
    ): ByteArray {
        val nonce = SpeedoraCrypto.buildChunkNonce(noncePrefix, sequence)
        val aad = TransferProtocol.buildAad(sessionId, type, sequence)
        return SpeedoraCrypto.encryptChunk(key, nonce, plaintext, aad)
    }

    private fun decryptWithSeq(
        key: SecretKey,
        noncePrefix: ByteArray,
        sessionId: String,
        type: FrameType,
        sequence: Long,
        ciphertext: ByteArray
    ): ByteArray {
        val nonce = SpeedoraCrypto.buildChunkNonce(noncePrefix, sequence)
        val aad = TransferProtocol.buildAad(sessionId, type, sequence)
        return SpeedoraCrypto.decryptChunk(key, nonce, ciphertext, aad)
    }

    private fun computeAverageSpeed(bytes: Long, startNanos: Long): Double {
        val elapsedSec = ((System.nanoTime() - startNanos) / 1_000_000_000.0).coerceAtLeast(0.05)
        return bytes / elapsedSec
    }

    private fun baseSnapshot(
        sessionId: String,
        direction: TransferDirection,
        state: TransferState,
        peerName: String,
        transport: TransportType,
        fileName: String,
        fileMime: String,
        fileIndex: Int,
        totalFiles: Int,
        fileBytes: Long,
        fileTotal: Long,
        totalBytes: Long,
        sessionTotal: Long,
        instantSpeed: Double = 0.0,
        avgSpeed: Double = 0.0,
        eta: Long = 0L,
        sasCode: String = "",
        integrityVerified: Boolean = false,
        errorMessage: String? = null
    ) = TransferProgressSnapshot(
        sessionId = sessionId,
        direction = direction,
        state = state,
        peerDeviceName = peerName,
        transport = transport,
        currentFileName = fileName,
        currentFileMime = fileMime,
        currentFileIndex = fileIndex,
        totalFilesCount = totalFiles,
        currentFileBytesTransferred = fileBytes,
        currentFileTotalBytes = fileTotal,
        totalBytesTransferred = totalBytes,
        totalSessionBytes = sessionTotal,
        instantBytesPerSec = instantSpeed,
        averageBytesPerSec = avgSpeed,
        etaSeconds = eta,
        sasCode = sasCode,
        integrityVerified = integrityVerified,
        errorMessage = errorMessage
    )
}

/**
 * Android-integrated coordinator managing TCP listener, client sends, foreground service hooks,
 * Room history logging, and safe destination storage (MediaStore.Downloads / SAF / App directory).
 */
class TransferEngine(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val historyDao: TransferHistoryDao,
    private val trustedDeviceDao: TrustedDeviceDao
) {
    companion object {
        const val DEFAULT_PORT = 45892
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var activeRunner: SpeedoraTransferSessionRunner? = null
    private var activeApprovalDeferred: CompletableDeferred<TransferAcceptPayload>? = null

    private val _activePort = MutableStateFlow(DEFAULT_PORT)
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    private val _progress = MutableStateFlow<TransferProgressSnapshot?>(null)
    val progress: StateFlow<TransferProgressSnapshot?> = _progress.asStateFlow()

    private val _incomingOffer = MutableStateFlow<IncomingTransferOffer?>(null)
    val incomingOffer: StateFlow<IncomingTransferOffer?> = _incomingOffer.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun startReceiveServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            val socket = runCatching { ServerSocket(DEFAULT_PORT) }.getOrElse { ServerSocket(0) }
            serverSocket = socket
            _activePort.value = socket.localPort
            _isListening.value = true

            try {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    handleIncomingSocket(client)
                }
            } finally {
                _isListening.value = false
            }
        }
    }

    fun stopReceiveServer() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        _isListening.value = false
    }

    fun respondToIncomingOffer(accepted: Boolean) {
        val deferred = activeApprovalDeferred ?: return
        _incomingOffer.value = null
        deferred.complete(TransferAcceptPayload(accepted = accepted))
    }

    fun pauseActiveTransfer() { activeRunner?.requestPause() }
    fun resumeActiveTransfer() { activeRunner?.requestResume() }
    fun cancelActiveTransfer() { activeRunner?.requestCancel() }

    suspend fun sendFiles(peer: PeerDevice, items: List<LocalFileItem>): Result<List<FileTransferDescriptor>> {
        val host = peer.hostAddress ?: return Result.failure(IOException("عنوان IP للجهاز المستلم غير متاح"))
        val settings = settingsDataStore.settingsFlow.first()
        val sessionId = SpeedoraCrypto.generateSessionId()
        val runner = SpeedoraTransferSessionRunner()
        activeRunner = runner

        TransferForegroundService.start(context)

        return withContext(Dispatchers.IO) {
            try {
                val sources = items.map { item ->
                    val sha256 = context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        SpeedoraCrypto.sha256Stream(stream)
                    } ?: throw IOException("تعذر قراءة الملف: ${item.name}")

                    val desc = FileTransferDescriptor(
                        id = item.id,
                        sanitizedName = FilenameSanitizer.sanitize(item.name),
                        mimeType = item.mimeType,
                        sizeBytes = item.sizeBytes,
                        sha256 = sha256,
                        categoryName = item.category.name
                    )
                    object : TransferSource {
                        override val descriptor: FileTransferDescriptor = desc
                        override fun openStream(): InputStream =
                            context.contentResolver.openInputStream(item.uri)
                                ?: throw IOException("تعذر فتح الملف: ${item.name}")
                    }
                }

                Socket().use { sock ->
                    sock.tcpNoDelay = true
                    sock.soTimeout = 30_000
                    sock.connect(InetSocketAddress(host, peer.port), 10_000)

                    val res = runner.runSendSession(
                        inputRaw = sock.getInputStream(),
                        outputRaw = sock.getOutputStream(),
                        sessionId = sessionId,
                        localDeviceId = settings.deviceName,
                        localDeviceName = settings.deviceName,
                        peerDeviceName = peer.name,
                        transport = peer.transport,
                        sources = sources,
                        onProgress = { snap ->
                            _progress.value = snap
                            TransferForegroundService.update(context, snap)
                        }
                    )

                    val finalSnap = _progress.value
                    sources.forEach { src ->
                        historyDao.insert(
                            TransferHistoryEntity(
                                sessionId = sessionId,
                                fileName = src.descriptor.sanitizedName,
                                fileSizeBytes = src.descriptor.sizeBytes,
                                mimeType = src.descriptor.mimeType,
                                direction = TransferDirection.SEND.name,
                                peerDeviceName = peer.name,
                                peerEndpoint = "$host:${peer.port}",
                                transportType = peer.transport.name,
                                status = if (res.isSuccess) "COMPLETED" else "FAILED",
                                integrityVerified = res.isSuccess,
                                sha256Hex = src.descriptor.sha256,
                                averageSpeedBytesPerSec = finalSnap?.averageBytesPerSec ?: 0.0,
                                timestampMillis = System.currentTimeMillis()
                            )
                        )
                    }
                    res
                }
            } finally {
                activeRunner = null
                TransferForegroundService.stop(context)
            }
        }
    }

    private suspend fun handleIncomingSocket(client: Socket) {
        val settings = settingsDataStore.settingsFlow.first()
        val runner = SpeedoraTransferSessionRunner()
        activeRunner = runner
        TransferForegroundService.start(context)

        try {
            client.tcpNoDelay = true
            client.soTimeout = 30_000
            val sink = createAndroidSink(settings.customDownloadTreeUri)

            runner.runReceiveSession(
                inputRaw = client.getInputStream(),
                outputRaw = client.getOutputStream(),
                localDeviceId = settings.deviceName,
                localDeviceName = settings.deviceName,
                transport = TransportType.LOCAL_WIFI_NSD,
                sink = sink,
                awaitUserApproval = { offer ->
                    val trusted = trustedDeviceDao.getById(offer.senderEndpoint)
                    val isTrusted = trusted != null
                    val enriched = offer.copy(isTrustedSender = isTrusted)
                    if (!settings.requireIncomingApproval && settings.allowTrustedAutoAccept && trusted?.autoAcceptIncoming == true) {
                        TransferAcceptPayload(accepted = true)
                    } else {
                        val deferred = CompletableDeferred<TransferAcceptPayload>()
                        activeApprovalDeferred = deferred
                        _incomingOffer.value = enriched
                        deferred.await()
                    }
                },
                onProgress = { snap ->
                    _progress.value = snap
                    TransferForegroundService.update(context, snap)
                },
                onFileCommitted = { desc, savedUri, senderName, avgSpeed ->
                    scope.launch {
                        historyDao.insert(
                            TransferHistoryEntity(
                                sessionId = _progress.value?.sessionId ?: "",
                                fileName = desc.sanitizedName,
                                fileSizeBytes = desc.sizeBytes,
                                mimeType = desc.mimeType,
                                direction = TransferDirection.RECEIVE.name,
                                peerDeviceName = senderName,
                                peerEndpoint = client.inetAddress?.hostAddress.orEmpty(),
                                transportType = TransportType.LOCAL_WIFI_NSD.name,
                                status = "COMPLETED",
                                integrityVerified = true,
                                sha256Hex = desc.sha256,
                                averageSpeedBytesPerSec = avgSpeed,
                                timestampMillis = System.currentTimeMillis(),
                                localSavedUri = savedUri
                            )
                        )
                    }
                }
            )
        } finally {
            runCatching { client.close() }
            activeApprovalDeferred = null
            _incomingOffer.value = null
            activeRunner = null
            TransferForegroundService.stop(context)
        }
    }

    private fun createAndroidSink(customTreeUri: String?): TransferSink {
        val tempDir = File(context.cacheDir, "speedora_part").apply { mkdirs() }
        return object : TransferSink {
            override fun openPartFile(fileId: String, sanitizedName: String, expectedSize: Long): File {
                val safeTemp = FilenameSanitizer.sanitize("${SpeedoraCrypto.sha256Bytes(fileId.toByteArray()).take(12)}_$sanitizedName.part")
                return File(tempDir, safeTemp)
            }

            override fun commitVerifiedFile(partFile: File, descriptor: FileTransferDescriptor): String? {
                // 1. User-selected SAF folder if configured
                if (!customTreeUri.isNullOrBlank()) {
                    val treeDoc = DocumentFile.fromTreeUri(context, Uri.parse(customTreeUri))
                    if (treeDoc != null && treeDoc.canWrite()) {
                        val targetDoc = treeDoc.createFile(descriptor.mimeType, descriptor.sanitizedName)
                        if (targetDoc != null) {
                            context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                                partFile.inputStream().use { it.copyTo(out, 65536) }
                            }
                            partFile.delete()
                            return targetDoc.uri.toString()
                        }
                    }
                }

                // 2. Android 10+ MediaStore.Downloads (scoped storage compliant)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, descriptor.sanitizedName)
                        put(MediaStore.Downloads.MIME_TYPE, descriptor.mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SPEEDORA")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            partFile.inputStream().use { it.copyTo(out, 65536) }
                        }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                        partFile.delete()
                        return uri.toString()
                    }
                }

                // 3. Fallback to app external files directory
                val destDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SPEEDORA")
                val finalFile = FilenameSanitizer.resolveConfinedFile(destDir, descriptor.sanitizedName)
                partFile.inputStream().use { input ->
                    FileOutputStream(finalFile).use { output -> input.copyTo(output, 65536) }
                }
                partFile.delete()
                return Uri.fromFile(finalFile).toString()
            }
        }
    }
}
