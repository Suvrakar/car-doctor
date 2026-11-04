package com.garidoctor.app.ai

import android.content.Context

/** Default build: no Gemini Nano. Build with -PenableNano to use it on supported phones. */
object OnDeviceLlmFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): OnDeviceLlm = object : OnDeviceLlm {
        override suspend fun ready() = false
        override suspend fun generate(prompt: String): String? = null
    }
}
