package com.example.util

import android.graphics.ImageFormat
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    private var isScanningEnabled = true

    fun setScanningEnabled(enabled: Boolean) {
        isScanningEnabled = enabled
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (!isScanningEnabled) {
            image.close()
            return
        }

        val format = image.format
        if (format == ImageFormat.YUV_420_888 || format == ImageFormat.YUV_422_888 || format == ImageFormat.YUV_444_888) {
            val plane = image.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val width = image.width
            val height = image.height

            val source = PlanarYUVLuminanceSource(
                bytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decodeWithState(binaryBitmap)
                val text = result.text
                if (!text.isNullOrBlank()) {
                    isScanningEnabled = false
                    onQrCodeScanned(text)
                }
            } catch (ignored: Exception) {
                // Barcode not found in this frame - expected behavior
            } finally {
                reader.reset()
                image.close()
            }
        } else {
            image.close()
        }
    }
}
