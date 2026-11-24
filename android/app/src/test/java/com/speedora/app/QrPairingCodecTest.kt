package com.speedora.app

import com.google.common.truth.Truth.assertThat
import com.speedora.app.core.model.QrPairingPayload
import com.speedora.app.data.network.QrDecodeResult
import com.speedora.app.data.network.QrPairingCodec
import org.junit.Test

class QrPairingCodecTest {

    @Test
    fun `encodes and decodes valid non-expired temporary qr payload`() {
        val now = 1_700_000_000_000L
        val payload = QrPairingPayload(
            sessionId = "sess123",
            deviceName = "Pixel 8",
            host = "192.168.43.1",
            port = 45892,
            transport = "WIFI_DIRECT",
            ephemeralPublicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE",
            expiresAtEpochMillis = now + 60_000L
        )
        val encoded = QrPairingCodec.encode(payload)
        val decoded = QrPairingCodec.decode(encoded, nowEpochMillis = now + 5_000L)

        assertThat(decoded).isInstanceOf(QrDecodeResult.Valid::class.java)
        assertThat((decoded as QrDecodeResult.Valid).payload.deviceName).isEqualTo("Pixel 8")
    }

    @Test
    fun `rejects expired or malformed qr payloads`() {
        val now = 1_700_000_000_000L
        val expired = QrPairingPayload(
            sessionId = "sess123",
            deviceName = "Pixel 8",
            host = "192.168.43.1",
            port = 45892,
            transport = "WIFI_DIRECT",
            ephemeralPublicKeyBase64 = "ABC",
            expiresAtEpochMillis = now - 1000L
        )
        val encoded = QrPairingCodec.encode(expired)
        assertThat(QrPairingCodec.decode(encoded, nowEpochMillis = now)).isEqualTo(QrDecodeResult.Expired)
        assertThat(QrPairingCodec.decode("https://example.com")).isInstanceOf(QrDecodeResult.Invalid::class.java)
    }
}
