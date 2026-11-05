package com.garidoctor.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Runs a Teachable Machine (float) TFLite export of dashboard symbols, fully on-device.
 * If the model isn't bundled ([available] = false), the UI falls back to a manual picker.
 */
class WarningLightClassifier(private val context: Context) : AutoCloseable {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    val available: Boolean

    init {
        available = try {
            val fd = context.assets.openFd(MODEL)
            val buf: MappedByteBuffer = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            interpreter = Interpreter(buf)
            labels = context.assets.open(LABELS).bufferedReader().readLines().map { it.trim().substringAfter(' ').ifBlank { it } }.filter { it.isNotBlank() }
            true
        } catch (e: Exception) { false }
    }

    /** Returns (label, confidence) sorted best first. */
    fun classify(uri: Uri): List<Pair<String, Float>> {
        val itp = interpreter ?: return emptyList()
        // The model needs 224 px; decode a small copy instead of the full camera image (low-RAM phones).
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) } ?: return emptyList()
        val bmp = Bitmap.createScaledBitmap(src, SIZE, SIZE, true)
        val input = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        px.forEach { p ->
            input.putFloat(((p shr 16 and 0xFF) - 127.5f) / 127.5f)
            input.putFloat(((p shr 8 and 0xFF) - 127.5f) / 127.5f)
            input.putFloat(((p and 0xFF) - 127.5f) / 127.5f)
        }
        val out = Array(1) { FloatArray(labels.size) }
        itp.run(input, out)
        return labels.mapIndexed { i, l -> l to out[0][i] }.sortedByDescending { it.second }
    }

    override fun close() { interpreter?.close() }

    companion object {
        const val MODEL = "warning_lights.tflite"
        const val LABELS = "warning_lights_labels.txt"
        const val SIZE = 224
    }
}
