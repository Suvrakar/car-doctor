package com.garidoctor.app.ocr

import android.content.Context
import android.net.Uri
import com.garidoctor.core.ocr.OdometerCandidate
import com.garidoctor.core.ocr.OdometerParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device ML Kit text recognition (free, offline) → odometer candidates. */
object OdometerReader {
    suspend fun read(context: Context, image: Uri, previousKm: Int?): List<OdometerCandidate> {
        val input = InputImage.fromFilePath(context, image)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        recognizer.close()
        return OdometerParser.candidates(text, previousKm).take(4)
    }
}
