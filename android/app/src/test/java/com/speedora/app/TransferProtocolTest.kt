package com.speedora.app

import com.google.common.truth.Truth.assertThat
import com.speedora.app.transfer.FrameType
import com.speedora.app.transfer.TransferProtocol
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class TransferProtocolTest {

    @Test
    fun `writes and reads binary frames preserving sequence and payload`() {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)
        val payload = "hello-speedora".toByteArray()

        TransferProtocol.writeFrame(out, FrameType.DATA_CHUNK, 42L, payload)

        val frame = TransferProtocol.readFrame(DataInputStream(ByteArrayInputStream(baos.toByteArray())))
        assertThat(frame.version).isEqualTo(TransferProtocol.PROTOCOL_VERSION)
        assertThat(frame.type).isEqualTo(FrameType.DATA_CHUNK)
        assertThat(frame.sequence).isEqualTo(42L)
        assertThat(frame.payload).isEqualTo(payload)
    }

    @Test
    fun `rejects invalid protocol magic bytes`() {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)
        out.writeInt(0x12345678)
        out.writeByte(1)
        out.writeByte(1)
        out.writeLong(1L)
        out.writeInt(0)
        out.flush()

        assertThrows(IOException::class.java) {
            TransferProtocol.readFrame(DataInputStream(ByteArrayInputStream(baos.toByteArray())))
        }
    }
}
