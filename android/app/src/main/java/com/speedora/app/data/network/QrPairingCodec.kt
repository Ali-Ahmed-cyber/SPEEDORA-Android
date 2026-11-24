package com.speedora.app.data.network

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.speedora.app.core.model.QrPairingPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed class QrDecodeResult {
    data class Valid(val payload: QrPairingPayload) : QrDecodeResult()
    data object Expired : QrDecodeResult()
    data class Invalid(val reason: String) : QrDecodeResult()
}

/**
 * Encodes & decodes temporary QR pairing payloads (default 5-minute expiration).
 * Strictly excludes permanent secrets or long-term private keys.
 */
object QrPairingCodec {
    const val DEFAULT_TTL_MILLIS = 5 * 60 * 1000L
    private const val URI_PREFIX = "speedora://pair?d="

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: QrPairingPayload): String {
        val raw = json.encodeToString(payload)
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
        return URI_PREFIX + b64
    }

    fun decode(rawText: String, nowEpochMillis: Long = System.currentTimeMillis()): QrDecodeResult {
        if (!rawText.startsWith(URI_PREFIX)) {
            return QrDecodeResult.Invalid("رمز QR لا ينتمي إلى SPEEDORA")
        }
        return try {
            val b64 = rawText.removePrefix(URI_PREFIX)
            val jsonStr = String(java.util.Base64.getUrlDecoder().decode(b64), Charsets.UTF_8)
            val payload = json.decodeFromString<QrPairingPayload>(jsonStr)
            if (payload.version != 1) {
                return QrDecodeResult.Invalid("إصدار اقتران غير متوافق")
            }
            if (payload.port !in 1..65535 || payload.host.isBlank() || payload.sessionId.isBlank()) {
                return QrDecodeResult.Invalid("بيانات عنوان الاقتران غير مكتملة")
            }
            if (nowEpochMillis > payload.expiresAtEpochMillis) {
                return QrDecodeResult.Expired
            }
            QrDecodeResult.Valid(payload)
        } catch (e: Exception) {
            QrDecodeResult.Invalid("تعذر قراءة رمز الاقتران: ${e.message ?: "صيغة غير صالحة"}")
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 640): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF102B50.toInt() else Color.WHITE)
            }
        }
        return bitmap
    }

    fun decodeYuvFrame(
        yuvBytes: ByteArray,
        width: Int,
        height: Int
    ): String? {
        return runCatching {
            val source = PlanarYUVLuminanceSource(
                yuvBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            MultiFormatReader().decode(bitmap).text
        }.getOrNull()
    }
}
