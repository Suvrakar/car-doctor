package com.garidoctor.app.ai

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation

/**
 * Gemini Nano through the ML Kit GenAI Prompt API (beta). Available on recent flagship phones
 * (e.g. Pixel 9+, Galaxy S24+); elsewhere [ready] is false and callers fall back to the cloud.
 * Opt-in (-PenableNano) because the beta API may change; verify on a device before enabling.
 */
object OnDeviceLlmFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): OnDeviceLlm = object : OnDeviceLlm {
        private val model by lazy { Generation.getClient() }
        override suspend fun ready(): Boolean = runCatching {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> true
                FeatureStatus.DOWNLOADABLE -> { runCatching { model.download().collect { } }; false }
                else -> false
            }
        }.getOrDefault(false)
        override suspend fun generate(prompt: String): String? = runCatching {
            model.generateContent(prompt).candidates.firstOrNull()?.text
        }.getOrNull()
    }
}
