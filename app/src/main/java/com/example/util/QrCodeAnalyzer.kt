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
}
