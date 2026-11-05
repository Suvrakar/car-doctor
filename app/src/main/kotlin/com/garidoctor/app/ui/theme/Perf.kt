package com.garidoctor.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lite rendering for low-end phones. When on: no blurred soft shadows (flat surfaces with a
 * hairline edge instead), no decorative animations, instant screen changes. The look stays the
 * same family; only the expensive effects go.
 */
data class PerfPolicy(val lite: Boolean) {
    val animate: Boolean get() = !lite
}

val LocalPerf = staticCompositionLocalOf { PerfPolicy(lite = false) }

object DevicePerf {
    /** "AUTO" decides from the device; "FULL" and "LITE" are the user's override. */
    fun policy(context: Context, mode: String): PerfPolicy = when (mode) {
        "FULL" -> PerfPolicy(false)
        "LITE" -> PerfPolicy(true)
        else -> PerfPolicy(isLowEnd(context))
    }

    /**
     * Low-end if any of: Android 8 or older (no hardware blur), the system flags it low-RAM,
     * under 3 GB of RAM, battery saver on, or the user turned animations off in system settings.
     */
    fun isLowEnd(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        val am = context.getSystemService(ActivityManager::class.java)
        if (am?.isLowRamDevice == true) return true
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        if (mem.totalMem in 1 until 3L * 1024 * 1024 * 1024) return true
        if (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) return true
        val animScale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        return animScale == 0f
    }
}
