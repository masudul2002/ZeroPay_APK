package com.example.util

import android.graphics.ImageFormat
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.example.data.model.ConfigData
import org.json.JSONObject
import android.net.Uri
import android.util.Log

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    private var isScanningEnabled = true
    private var lastScannedTimestamp = 0L

    fun setScanningEnabled(enabled: Boolean) {
        isScanningEnabled = enabled
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (!isScanningEnabled || (currentTime - lastScannedTimestamp < 1200L)) {
            image.close()
            return
        }

        val format = image.format
        if (format == ImageFormat.YUV_420_888 || format == ImageFormat.YUV_422_888 || format == ImageFormat.YUV_444_888) {
            try {
                val yPlane = image.planes[0]
                val yBuffer = yPlane.buffer
                val rowStride = yPlane.rowStride
                val pixelStride = yPlane.pixelStride
                val width = image.width
                val height = image.height

                // Extract exact width * height luminance data avoiding rowStride padding distortions
                val yuvBytes = if (rowStride == width && pixelStride == 1) {
                    val bytes = ByteArray(yBuffer.remaining())
                    yBuffer.get(bytes)
                    bytes
                } else {
                    val cleanBytes = ByteArray(width * height)
                    val rowBuffer = ByteArray(rowStride)
                    for (y in 0 until height) {
                        yBuffer.position(y * rowStride)
                        if (pixelStride == 1) {
                            yBuffer.get(cleanBytes, y * width, width)
                        } else {
                            val available = Math.min(rowStride, yBuffer.remaining())
                            yBuffer.get(rowBuffer, 0, available)
                            for (x in 0 until width) {
                                cleanBytes[y * width + x] = rowBuffer[x * pixelStride]
                            }
                        }
                    }
                    cleanBytes
                }

                val rotationDegrees = image.imageInfo.rotationDegrees
                val rotatedData: ByteArray
                val finalWidth: Int
                val finalHeight: Int

                when (rotationDegrees) {
                    90 -> {
                        rotatedData = rotateYuv90(yuvBytes, width, height)
                        finalWidth = height
                        finalHeight = width
                    }
                    180 -> {
                        rotatedData = rotateYuv180(yuvBytes, width, height)
                        finalWidth = width
                        finalHeight = height
                    }
                    270 -> {
                        rotatedData = rotateYuv270(yuvBytes, width, height)
                        finalWidth = height
                        finalHeight = width
                    }
                    else -> {
                        rotatedData = yuvBytes
                        finalWidth = width
                        finalHeight = height
                    }
                }

                val source = PlanarYUVLuminanceSource(
                    rotatedData,
                    finalWidth,
                    finalHeight,
                    0,
                    0,
                    finalWidth,
                    finalHeight,
                    false
                )

                var text: String? = null

                // 1. Try default HybridBinarizer
                try {
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    text = reader.decodeWithState(binaryBitmap)?.text
                } catch (_: Exception) {}
                finally { reader.reset() }

                // 2. Try GlobalHistogramBinarizer if first attempt fails (optimal for low lighting)
                if (text.isNullOrBlank()) {
                    try {
                        val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                        text = reader.decodeWithState(binaryBitmap)?.text
                    } catch (_: Exception) {}
                    finally { reader.reset() }
                }

                // 3. Try Inverted source (for dark mode or screen reflections)
                if (text.isNullOrBlank()) {
                    try {
                        val invertedSource = source.invert()
                        val binaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
                        text = reader.decodeWithState(binaryBitmap)?.text
                    } catch (_: Exception) {}
                    finally { reader.reset() }
                }

                if (!text.isNullOrBlank()) {
                    isScanningEnabled = false
                    lastScannedTimestamp = currentTime
                    onQrCodeScanned(text.trim())
                }
            } catch (e: Exception) {
                // Ignore transient frame parsing errors
            } finally {
                image.close()
            }
        } else {
            image.close()
        }
    }

    private fun rotateYuv90(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        var i = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                rotated[i++] = data[y * width + x]
            }
        }
        return rotated
    }

    private fun rotateYuv180(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        var i = 0
        for (idx in data.size - 1 downTo 0) {
            rotated[i++] = data[idx]
        }
        return rotated
    }

    private fun rotateYuv270(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        var i = 0
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) {
                rotated[i++] = data[y * width + x]
            }
        }
        return rotated
    }

    companion object {
        private const val TAG = "QrCodeAnalyzer"

        fun parseConfigurationPayload(rawPayload: String, fallbackConfig: ConfigData? = null): Result<ConfigData> {
            val trimmedRaw = rawPayload.trim()
            if (trimmedRaw.isBlank()) {
                return Result.failure(IllegalArgumentException("Payload cannot be empty"))
            }

            return try {
                var raw = trimmedRaw

                // 1. Remove markdown code block fences if present (e.g. ```json ... ```)
                if (raw.startsWith("```")) {
                    val lines = raw.lines()
                    raw = lines.filterNot { it.trim().startsWith("```") }.joinToString("\n").trim()
                }

                // 2. Unescape outer quotes if whole string was quoted: e.g. "{\"webhookUrl\": ...}"
                if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
                    raw = raw.substring(1, raw.length - 1).trim()
                }

                // 3. Unescape slashes and escaped quotes
                val normalized = raw.replace("\\\"", "\"").replace("\\/", "/")

                var extractedWebhookUrl = ""
                var extractedDeviceToken = ""
                var extractedDeviceId = ""

                // 4. Try parsing as JSON if braces are present
                val firstBrace = normalized.indexOf('{')
                val lastBrace = normalized.lastIndexOf('}')
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    val jsonCandidate = normalized.substring(firstBrace, lastBrace + 1).trim()
                    try {
                        val json = JSONObject(jsonCandidate)

                        extractedWebhookUrl = json.optString("webhookUrl", "")
                            .ifBlank { json.optString("webhook_url", "") }
                            .ifBlank { json.optString("url", "") }
                            .ifBlank { json.optString("endpoint", "") }
                            .trim()

                        extractedDeviceToken = json.optString("deviceToken", "")
                            .ifBlank { json.optString("device_token", "") }
                            .ifBlank { json.optString("deviceSecret", "") }
                            .ifBlank { json.optString("device_secret", "") }
                            .ifBlank { json.optString("token", "") }
                            .ifBlank { json.optString("secret", "") }
                            .ifBlank { json.optString("apiKey", "") }
                            .ifBlank { json.optString("api_key", "") }
                            .trim()

                        extractedDeviceId = json.optString("deviceId", "")
                            .ifBlank { json.optString("device_id", "") }
                            .ifBlank { json.optString("id", "") }
                            .trim()
                    } catch (_: Exception) {
                        // Fallback to regex below
                    }
                }

                // 5. Infallible Regex extraction fallback (handles single quotes, unquoted keys, missing commas, etc.)
                if (extractedWebhookUrl.isBlank()) {
                    val urlRegex = """(?i)["']?(?:webhookUrl|webhook_url|url|endpoint)["']?\s*[:=]\s*["']([^"'\s}]+)["']""".toRegex()
                    extractedWebhookUrl = urlRegex.find(normalized)?.groupValues?.get(1)?.trim().orEmpty()
                }
                if (extractedDeviceToken.isBlank()) {
                    val tokenRegex = """(?i)["']?(?:deviceToken|device_token|deviceSecret|device_secret|token|secret|apiKey|api_key)["']?\s*[:=]\s*["']([^"'\s,}]+)["']""".toRegex()
                    extractedDeviceToken = tokenRegex.find(normalized)?.groupValues?.get(1)?.trim().orEmpty()
                }
                if (extractedDeviceId.isBlank()) {
                    val idRegex = """(?i)["']?(?:deviceId|device_id|id)["']?\s*[:=]\s*["']([^"'\s,}]+)["']""".toRegex()
                    extractedDeviceId = idRegex.find(normalized)?.groupValues?.get(1)?.trim().orEmpty()
                }

                // 6. Direct HTTP(S) URL extraction (e.g. if raw string is or contains a URL)
                if (extractedWebhookUrl.isBlank()) {
                    val directUrlRegex = """(https?://[^\s"'<>{}]+)""".toRegex()
                    val match = directUrlRegex.find(normalized)
                    if (match != null) {
                        val fullUrl = match.groupValues[1]
                        if (extractedDeviceToken.isBlank()) {
                            extractedDeviceToken = extractQueryParam(fullUrl, "deviceToken")
                                .ifBlank { extractQueryParam(fullUrl, "token") }
                                .ifBlank { extractQueryParam(fullUrl, "deviceSecret") }
                                .ifBlank { extractQueryParam(fullUrl, "secret") }
                                .ifBlank { extractQueryParam(fullUrl, "apiKey") }
                                .ifBlank { extractQueryParam(fullUrl, "api_key") }
                        }
                        if (extractedDeviceId.isBlank()) {
                            extractedDeviceId = extractQueryParam(fullUrl, "deviceId")
                                .ifBlank { extractQueryParam(fullUrl, "device_id") }
                                .ifBlank { extractQueryParam(fullUrl, "id") }
                        }
                        extractedWebhookUrl = fullUrl.substringBefore('?').substringBefore('#').trim()
                    }
                }

                // 7. Extract deviceId pattern (e.g. dev_ec7afd5636d8a76c) if not yet resolved
                if (extractedDeviceId.isBlank()) {
                    val devIdPattern = """\b(dev_[a-zA-Z0-9_-]+)\b""".toRegex()
                    extractedDeviceId = devIdPattern.find(normalized)?.groupValues?.get(1).orEmpty()
                }

                // 8. If dynamic payment QR string (e.g. EMVCo Bangla QR starting with 000201)
                if (raw.startsWith("000201") || raw.startsWith("bkash://") || raw.startsWith("nagad://")) {
                    if (fallbackConfig != null) {
                        return Result.success(fallbackConfig)
                    }
                }

                // Default deviceId if missing
                if (extractedDeviceId.isBlank()) {
                    extractedDeviceId = fallbackConfig?.deviceId?.ifBlank { "sim-gateway-01" } ?: "sim-gateway-01"
                }

                // Default token from existing configuration if missing
                if (extractedDeviceToken.isBlank() && fallbackConfig != null && fallbackConfig.deviceToken.isNotBlank()) {
                    extractedDeviceToken = fallbackConfig.deviceToken
                }

                if (extractedWebhookUrl.isBlank()) {
                    return Result.failure(IllegalArgumentException("Webhook URL could not be detected in payload"))
                }
                if (extractedDeviceToken.isBlank()) {
                    return Result.failure(IllegalArgumentException("Device Token could not be detected in payload"))
                }

                Result.success(ConfigData(extractedWebhookUrl.trim(), extractedDeviceToken.trim(), extractedDeviceId.trim()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse QR JSON: ${e.message}", e)
                Result.failure(IllegalArgumentException("Invalid QR/JSON: ${e.localizedMessage ?: "Unable to parse credentials"}"))
            }
        }

        private fun extractQueryParam(url: String, paramName: String): String {
            val regex = """[?&]$paramName=([^&#\s]+)""".toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(url) ?: return ""
            val raw = match.groupValues[1]
            return try {
                java.net.URLDecoder.decode(raw, "UTF-8")
            } catch (_: Exception) {
                raw
            }
        }
    }
}
