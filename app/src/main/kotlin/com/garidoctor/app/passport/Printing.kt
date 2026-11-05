package com.garidoctor.app.passport

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.garidoctor.app.ui.findActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Print, share and QR helpers. Android's print dialog also offers "Save as PDF". */
object Printing {
    /** Opens the system print dialog for a PDF we already wrote. Needs an Activity context. */
    fun printPdf(context: Context, file: File, jobName: String): Boolean {
        val activity = context.findActivity() ?: return false
        val pm = activity.getSystemService(PrintManager::class.java) ?: return false
        pm.print(jobName, PdfFileAdapter(file, file.name), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        return true
    }

    fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Folder covered by the FileProvider's "passports" cache path. */
    fun outDir(context: Context): File = File(context.cacheDir, "passports").apply { mkdirs() }

    /**
     * QR with UTF-8 (Bangla, Arabic, Hindi labels survive) and medium error correction, so a
     * slightly creased print still scans.
     */
    fun qrBitmap(text: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private class PdfFileAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, cb: PrintDocumentAdapter.LayoutResultCallback, extras: Bundle?) {
            if (cancel.isCanceled) { cb.onLayoutCancelled(); return }
            cb.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), old != new)
        }

        override fun onWrite(pages: Array<out PageRange>, dest: ParcelFileDescriptor, cancel: CancellationSignal, cb: PrintDocumentAdapter.WriteResultCallback) {
            try {
                FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                if (cancel.isCanceled) cb.onWriteCancelled() else cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) { cb.onWriteFailed(e.message) }
        }
    }
}
