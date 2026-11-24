package com.speedora.app.core.security

import java.io.InputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SessionKeyMaterial(
    val senderToReceiverKey: SecretKey,
    val receiverToSenderKey: SecretKey,
    val sasCode: String,
    val keyFingerprintHex: String
)

/**
 * Cryptographic engine for SPEEDORA:
 * - Ephemeral ECDH P-256 (secp256r1) key agreement
 * - RFC 5869 HKDF-SHA256 key derivation
 * - AES-256-GCM authenticated chunk encryption with strictly unique 96-bit nonces
 * - 6-digit Short Authentication String (SAS) bound to both public keys & session ID
 * - Streaming SHA-256 file integrity verification
 */
object SpeedoraCrypto {
    private const val EC_CURVE = "secp256r1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val NONCE_BYTES = 12

    private val secureRandom = SecureRandom()

    fun generateSessionId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun generateEphemeralKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        return kpg.generateKeyPair()
    }

    fun encodePublicKeyBase64(publicKey: PublicKey): String =
        Base64.getEncoder().encodeToString(publicKey.encoded)

    fun decodePublicKeyBase64(encoded: String): PublicKey {
        val bytes = Base64.getDecoder().decode(encoded)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(bytes))
    }

    fun deriveSessionKeys(
        localKeyPair: KeyPair,
        remotePublicKey: PublicKey,
        sessionId: String
    ): SessionKeyMaterial {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(localKeyPair.private)
        keyAgreement.doPhase(remotePublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Sort both public key encodings lexicographically so both peers produce identical transcript salt
        val localPub = localKeyPair.public.encoded
        val remotePub = remotePublicKey.encoded
        val (firstPub, secondPub) = if (compareUnsignedBytes(localPub, remotePub) <= 0) {
            localPub to remotePub
        } else {
            remotePub to localPub
        }

        val transcriptDigest = MessageDigest.getInstance("SHA-256").apply {
            update(sessionId.toByteArray(Charsets.UTF_8))
            update(firstPub)
            update(secondPub)
        }.digest()

        val prk = hkdfExtract(salt = transcriptDigest, ikm = sharedSecret)
        val okm = hkdfExpand(
            prk = prk,
            info = "SPEEDORA-v1-AES256GCM-KEYS".toByteArray(Charsets.UTF_8),
            outputLength = 64
        )

        val s2rKeyBytes = okm.copyOfRange(0, 32)
        val r2sKeyBytes = okm.copyOfRange(32, 64)

        val sasBytes = hkdfExpand(
            prk = prk,
            info = "SPEEDORA-v1-SAS-6DIGIT".toByteArray(Charsets.UTF_8),
            outputLength = 4
        )
        val sasInt = ByteBuffer.wrap(sasBytes).int and 0x7FFFFFFF
        val sasCode = String.format("%06d", sasInt % 1_000_000)

        sharedSecret.fill(0)

        return SessionKeyMaterial(
            senderToReceiverKey = SecretKeySpec(s2rKeyBytes, "AES"),
            receiverToSenderKey = SecretKeySpec(r2sKeyBytes, "AES"),
            sasCode = sasCode,
            keyFingerprintHex = transcriptDigest.take(8).joinToString("") { "%02x".format(it) }
        )
    }

    /**
     * Builds a deterministic, collision-free 12-byte GCM nonce:
     * [4-byte direction prefix] + [8-byte monotonic chunk counter]
     */
    fun buildChunkNonce(directionPrefix: ByteArray, sequenceNumber: Long): ByteArray {
        require(directionPrefix.size == 4) { "Nonce prefix must be 4 bytes" }
        require(sequenceNumber >= 0L) { "Sequence number must be non-negative" }
        return ByteBuffer.allocate(NONCE_BYTES)
            .put(directionPrefix)
            .putLong(sequenceNumber)
            .array()
    }

    fun encryptChunk(
        key: SecretKey,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(nonce.size == NONCE_BYTES) { "GCM nonce must be 12 bytes" }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun decryptChunk(
        key: SecretKey,
        nonce: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(nonce.size == NONCE_BYTES) { "GCM nonce must be 12 bytes" }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextWithTag)
    }

    fun sha256Stream(inputStream: InputStream, bufferSize: Int = 65536): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            if (read > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    internal fun hkdfExpand(prk: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        require(outputLength > 0 && outputLength <= 255 * 32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        val result = ByteArray(outputLength)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < outputLength) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val toCopy = minOf(previous.size, outputLength - offset)
            System.arraycopy(previous, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        return result
    }

    private fun compareUnsignedBytes(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val cmp = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }
}
