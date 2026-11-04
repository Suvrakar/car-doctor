package com.garidoctor.app.ai

/** On-device language model (Gemini Nano). Free, offline, never touches the cloud quota. */
interface OnDeviceLlm {
    /** True if the model is ready on this phone right now (may start a background download). */
    suspend fun ready(): Boolean
    /** Plain-text generation; null on any failure. */
    suspend fun generate(prompt: String): String?
}
