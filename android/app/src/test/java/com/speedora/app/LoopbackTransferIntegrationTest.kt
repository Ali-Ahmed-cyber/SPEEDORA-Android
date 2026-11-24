package com.speedora.app

import com.google.common.truth.Truth.assertThat
import com.speedora.app.core.model.FileTransferDescriptor
import com.speedora.app.core.model.TransportType
import com.speedora.app.core.security.FilenameSanitizer
import com.speedora.app.core.security.SpeedoraCrypto
import com.speedora.app.transfer.SpeedoraTransferSessionRunner
import com.speedora.app.transfer.TransferAcceptPayload
import com.speedora.app.transfer.TransferSink
import com.speedora.app.transfer.TransferSource
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Random

class LoopbackTransferIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `real tcp loopback multi-chunk encrypted transfer verifies sha256 and commits file`() = runBlocking {
        val payloadBytes = ByteArray(256 * 1024 + 777)
        Random(42L).nextBytes(payloadBytes)
        val expectedSha = SpeedoraCrypto.sha256Bytes(payloadBytes)

        val descriptor = FileTransferDescriptor(
            id = "file-1",
            sanitizedName = "test_video_chunked.bin",
            mimeType = "application/octet-stream",
            sizeBytes = payloadBytes.size.toLong(),
            sha256 = expectedSha,
            categoryName = "DOCUMENTS"
        )
        val source = object : TransferSource {
            override val descriptor: FileTransferDescriptor = descriptor
            override fun openStream(): InputStream = ByteArrayInputStream(payloadBytes)
        }

        val partDir = tempFolder.newFolder("parts")
        val destDir = tempFolder.newFolder("dest")
        val sink = object : TransferSink {
            override fun openPartFile(fileId: String, sanitizedName: String, expectedSize: Long): File =
                File(partDir, "$fileId.part")

            override fun commitVerifiedFile(partFile: File, descriptor: FileTransferDescriptor): String {
                val outFile = FilenameSanitizer.resolveConfinedFile(destDir, descriptor.sanitizedName)
                partFile.copyTo(outFile, overwrite = true)
                partFile.delete()
                return outFile.absolutePath
            }
        }

        val server = ServerSocket(0)
        val port = server.localPort

        var senderSas = ""
        var receiverSas = ""

        val receiverJob = async {
            server.accept().use { client ->
                val runner = SpeedoraTransferSessionRunner()
                runner.runReceiveSession(
                    inputRaw = client.getInputStream(),
                    outputRaw = client.getOutputStream(),
                    localDeviceId = "dev-rx",
                    localDeviceName = "Receiver Phone",
                    transport = TransportType.LOCAL_WIFI_NSD,
                    sink = sink,
                    awaitUserApproval = { offer ->
                        receiverSas = offer.sasCode
                        TransferAcceptPayload(accepted = true)
                    },
                    onProgress = {}
                )
            }
        }

        val senderJob = async {
            Socket("127.0.0.1", port).use { sock ->
                val runner = SpeedoraTransferSessionRunner()
                runner.runSendSession(
                    inputRaw = sock.getInputStream(),
                    outputRaw = sock.getOutputStream(),
                    sessionId = SpeedoraCrypto.generateSessionId(),
                    localDeviceId = "dev-tx",
                    localDeviceName = "Sender Phone",
                    peerDeviceName = "Receiver Phone",
                    transport = TransportType.LOCAL_WIFI_NSD,
                    sources = listOf(source),
                    onProgress = {},
                    onSasGenerated = { senderSas = it }
                )
            }
        }

        val sendResult = senderJob.await()
        val receiveResult = receiverJob.await()
        server.close()

        assertThat(sendResult.isSuccess).isTrue()
        assertThat(receiveResult.isSuccess).isTrue()
        assertThat(senderSas).hasLength(6)
        assertThat(senderSas).isEqualTo(receiverSas)

        val committedFile = File(destDir, "test_video_chunked.bin")
        assertThat(committedFile.exists()).isTrue()
        assertThat(committedFile.readBytes()).isEqualTo(payloadBytes)
    }

    @Test
    fun `receiver rejection aborts transfer cleanly without committing file`() = runBlocking {
        val payloadBytes = "secret-doc".toByteArray()
        val descriptor = FileTransferDescriptor(
            id = "file-reject",
            sanitizedName = "doc.txt",
            mimeType = "text/plain",
            sizeBytes = payloadBytes.size.toLong(),
            sha256 = SpeedoraCrypto.sha256Bytes(payloadBytes),
            categoryName = "DOCUMENTS"
        )
        val source = object : TransferSource {
            override val descriptor = descriptor
            override fun openStream() = ByteArrayInputStream(payloadBytes)
        }
        val destDir = tempFolder.newFolder("dest_reject")
        val partDir = tempFolder.newFolder("parts_reject")
        val sink = object : TransferSink {
            override fun openPartFile(fileId: String, sanitizedName: String, expectedSize: Long) =
                File(partDir, "$fileId.part")
            override fun commitVerifiedFile(partFile: File, descriptor: FileTransferDescriptor): String? = null
        }

        val server = ServerSocket(0)
        val port = server.localPort

        val rx = async {
            server.accept().use { client ->
                SpeedoraTransferSessionRunner().runReceiveSession(
                    inputRaw = client.getInputStream(),
                    outputRaw = client.getOutputStream(),
                    localDeviceId = "rx",
                    localDeviceName = "RX",
                    transport = TransportType.LOCAL_WIFI_NSD,
                    sink = sink,
                    awaitUserApproval = { TransferAcceptPayload(accepted = false, reason = "Rejected by user") },
                    onProgress = {}
                )
            }
        }
        val tx = async {
            Socket("127.0.0.1", port).use { sock ->
                SpeedoraTransferSessionRunner().runSendSession(
                    inputRaw = sock.getInputStream(),
                    outputRaw = sock.getOutputStream(),
                    sessionId = SpeedoraCrypto.generateSessionId(),
                    localDeviceId = "tx",
                    localDeviceName = "TX",
                    peerDeviceName = "RX",
                    transport = TransportType.LOCAL_WIFI_NSD,
                    sources = listOf(source),
                    onProgress = {}
                )
            }
        }

        assertThat(tx.await().isFailure).isTrue()
        assertThat(rx.await().isFailure).isTrue()
        assertThat(destDir.listFiles()).isEmpty()
        server.close()
    }
}
