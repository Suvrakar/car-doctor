@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Photos (receipts, odometer, warning lights) stay on the phone under files/photos. */
object Photos {
    fun newPhoto(context: Context, prefix: String): Pair<Uri, String> {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val f = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f) to f.absolutePath
    }
}
