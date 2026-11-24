package com.speedora.app

import com.google.common.truth.Truth.assertThat
import com.speedora.app.core.security.SpeedoraCrypto
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.crypto.AEADBadTagException

class SpeedoraCryptoTest {

    @Test
    fun `ecdh p256 and hkdf derive identical keys and matching 6-digit sas on both peers`() {
        val aliceKeys = SpeedoraCrypto.generateEphemeralKeyPair()
        val bobKeys = SpeedoraCrypto.generateEphemeralKeyPair()
        val sessionId = SpeedoraCrypto.generateSessionId()

        val aliceMaterial = SpeedoraCrypto.deriveSessionKeys(aliceKeys, bobKeys.public, sessionId)
        val bobMaterial = SpeedoraCrypto.deriveSessionKeys(bobKeys, aliceKeys.public, sessionId)

        assertThat(aliceMaterial.sasCode).hasLength(6)
        assertThat(aliceMaterial.sasCode).isEqualTo(bobMaterial.sasCode)
        assertThat(aliceMaterial.keyFingerprintHex).isEqualTo(bobMaterial.keyFingerprintHex)
        assertThat(aliceMaterial.senderToReceiverKey.encoded).isEqualTo(bobMaterial.senderToReceiverKey.encoded)
        assertThat(aliceMaterial.receiverToSenderKey.encoded).isEqualTo(bobMaterial.receiverToSenderKey.encoded)
    }

    @Test
    fun `aes 256 gcm encrypts decrypts and rejects tampered aad or ciphertext`() {
        val alice = SpeedoraCrypto.generateEphemeralKeyPair()
        val bob = SpeedoraCrypto.generateEphemeralKeyPair()
        val sessionId = SpeedoraCrypto.generateSessionId()
        val material = SpeedoraCrypto.deriveSessionKeys(alice, bob.public, sessionId)

        val prefix = SpeedoraCrypto.generateRandomBytes(4)
        val nonce1 = SpeedoraCrypto.buildChunkNonce(prefix, 1L)
        val nonce2 = SpeedoraCrypto.buildChunkNonce(prefix, 2L)
        assertThat(nonce1).isNotEqualTo(nonce2)

        val aad = "v1|chunk1".toByteArray()
        val plaintext = "SPEEDORA_DIRECT_ENCRYPTED_PAYLOAD".toByteArray()
        val cipher = SpeedoraCrypto.encryptChunk(material.senderToReceiverKey, nonce1, plaintext, aad)
        val decrypted = SpeedoraCrypto.decryptChunk(material.senderToReceiverKey, nonce1, cipher, aad)
        assertThat(decrypted).isEqualTo(plaintext)

        assertThrows(AEADBadTagException::class.java) {
            SpeedoraCrypto.decryptChunk(material.senderToReceiverKey, nonce1, cipher, "v1|tampered".toByteArray())
        }
    }

    @Test
    fun `sha256 stream matches known vector`() {
        val data = "speedora-integrity".toByteArray()
        val hash = SpeedoraCrypto.sha256Stream(ByteArrayInputStream(data))
        assertThat(hash).isEqualTo(SpeedoraCrypto.sha256Bytes(data))
        assertThat(hash).hasLength(64)
    }
}
