package com.garidoctor.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.garidoctor.app.data.Prefs
import com.garidoctor.app.remote.RemoteContent
import com.garidoctor.core.ai.AiJson
import com.garidoctor.core.ai.AiPrompts
import com.garidoctor.core.ai.AiQuota
import com.garidoctor.core.ai.ScannedDocument
import com.garidoctor.core.ai.ScannedReceipt
import com.garidoctor.core.ai.VoiceAction
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** Why a cloud AI request didn't run. The UI shows a specific message for each. */
class AiException(val reason: Reason, message: String? = null) : Exception(message ?: reason.name) {
    enum class Reason { NOT_CONFIGURED, DISABLED, NO_CONSENT, QUOTA, FAILED }
}

/**
 * Cloud AI through Firebase AI Logic (Gemini), with no server of our own.
 *
 * Every request: (1) needs the user's consent, (2) runs only if Firebase is configured and the
 * Remote Config off switch allows it, (3) reserves one unit of the user's daily allowance in
 * Firestore first (rules enforce a hard cap even against modified apps), (4) is protected by
 * App Check, (5) sends a downscaled photo and caps the answer length. Usage counts against the
 * app owner's Firebase project, so these limits are what keep the quota and any bill under control.
 */
class AiService(private val context: Context, private val prefs: Prefs, private val remote: RemoteContent) {
    val onDevice: OnDeviceLlm = OnDeviceLlmFactory.create(context)

    private val _quota = MutableStateFlow<AiQuota?>(null)
    /** Today's allowance, null until loaded (or when cloud AI isn't configured). */
    val quota: StateFlow<AiQuota?> = _quota

    val configured: Boolean get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    val enabled: Boolean get() = configured && remote.bool("ai_enabled", true)

    private val freeLimit get() = remote.long("ai_daily_free", 3).toInt()
    private val proLimit get() = remote.long("ai_daily_pro", 30).toInt()
    private fun limit() = AiQuota.limitFor(prefs.pro.value, freeLimit, proLimit)

    private suspend fun uid(): String {
        val auth = FirebaseAuth.getInstance()
        // Anonymous sign-in: gives per-user limits with no login screen.
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    }

    private suspend fun usageRef() = FirebaseFirestore.getInstance()
        .collection("usage").document(uid()).collection("days").document(AiQuota.utcDay(System.currentTimeMillis()).toString())

    suspend fun refreshQuota(): AiQuota? {
        if (!enabled) { _quota.value = null; return null }
        return runCatching {
            val snap = usageRef().get().await()
            AiQuota((snap.getLong("count") ?: 0).toInt(), (snap.getLong("bonus") ?: 0).toInt(), limit())
        }.onFailure { Log.w(TAG, "quota read failed", it) }.getOrNull().also { _quota.value = it }
    }

    /** Reserve one use. Firestore rules reject the write past the hard cap; the plan limit is checked here. */
    private suspend fun reserve() {
        if (!configured) throw AiException(AiException.Reason.NOT_CONFIGURED)
        if (!remote.bool("ai_enabled", true)) throw AiException(AiException.Reason.DISABLED)
        if (!prefs.aiConsent.value) throw AiException(AiException.Reason.NO_CONSENT)
        val ref = usageRef()
        val lim = limit()
        try {
            FirebaseFirestore.getInstance().runTransaction { tx ->
                val snap = tx.get(ref)
                val count = (snap.getLong("count") ?: 0L).toInt()
                val bonus = (snap.getLong("bonus") ?: 0L).toInt()
                if (count >= lim + bonus) throw AiException(AiException.Reason.QUOTA)
                tx.set(ref, mapOf("count" to (count + 1).toLong(), "bonus" to bonus.toLong()))
                AiQuota(count + 1, bonus, lim)
            }.await().also { _quota.value = it }
        } catch (e: AiException) { throw e
        } catch (e: Exception) {
            // A rules rejection (hard cap) or our own QUOTA thrown inside the transaction.
            if (generateSequence(e as Throwable?) { it.cause }.any { it is AiException }) throw AiException(AiException.Reason.QUOTA)
            throw AiException(AiException.Reason.FAILED, e.message)
        }
    }

    /** One extra use after a completed rewarded ad (rules allow at most [AiQuota.MAX_AD_BONUS] a day). */
    suspend fun grantAdBonus(): Boolean = runCatching {
        val ref = usageRef()
        FirebaseFirestore.getInstance().runTransaction { tx ->
            val snap = tx.get(ref)
            val count = snap.getLong("count") ?: 0L
            val bonus = snap.getLong("bonus") ?: 0L
            if (bonus >= AiQuota.MAX_AD_BONUS) error("bonus cap")
            tx.set(ref, mapOf("count" to count, "bonus" to bonus + 1))
        }.await()
        refreshQuota(); true
    }.getOrDefault(false)

    private fun model() = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = remote.string("ai_model") ?: DEFAULT_MODEL,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            maxOutputTokens = remote.long("ai_max_output_tokens", 512).toInt()
            temperature = 0.1f
        },
    )

    private suspend fun ask(prompt: String, photo: Bitmap?): String {
        reserve()
        return try {
            val res = model().generateContent(content { if (photo != null) image(photo); text(prompt) })
            Log.i(TAG, "AI tokens used: ${res.usageMetadata?.totalTokenCount}")
            res.text ?: throw AiException(AiException.Reason.FAILED, "empty answer")
        } catch (e: AiException) { throw e
        } catch (e: Exception) { throw AiException(AiException.Reason.FAILED, e.message) }
    }

    // ---- Features. Each tries free on-device options first; the cloud is the last step. ----

    /** On-device Nano over the OCR text (free); null if Nano isn't on this phone or can't tell. */
    suspend fun paperOnDevice(ocr: String): ScannedDocument? =
        if (ocr.isBlank() || !onDevice.ready()) null else onDevice.generate(AiPrompts.paperFromText(ocr))?.let { AiJson.paper(it) }?.copy(source = "nano")

    suspend fun receiptOnDevice(ocr: String, parts: Set<String>): ScannedReceipt? =
        if (ocr.isBlank() || !onDevice.ready()) null else onDevice.generate(AiPrompts.receiptFromText(parts, ocr))?.let { AiJson.receipt(it, parts) }?.copy(source = "nano")

    suspend fun voiceOnDevice(transcript: String, parts: Set<String>): VoiceAction =
        if (!onDevice.ready()) VoiceAction.Unknown else onDevice.generate(AiPrompts.voice(parts, transcript))?.let { AiJson.voice(it, parts) } ?: VoiceAction.Unknown

    suspend fun paperCloud(photo: Bitmap): ScannedDocument? = AiJson.paper(ask(remote.string("ai_prompt_paper") ?: AiPrompts.paper, photo))
    suspend fun receiptCloud(photo: Bitmap, parts: Set<String>): ScannedReceipt? = AiJson.receipt(ask(remote.string("ai_prompt_receipt") ?: AiPrompts.receipt(parts), photo), parts)
    suspend fun voiceCloud(transcript: String, parts: Set<String>): VoiceAction = AiJson.voice(ask(AiPrompts.voice(parts, transcript), null), parts)
    suspend fun warningLightCloud(photo: Bitmap): Pair<String, Double>? = AiJson.warningLight(ask(remote.string("ai_prompt_light") ?: AiPrompts.warningLight, photo))

    companion object {
        private const val TAG = "AiService"
        /** Cheapest Flash-Lite model; change remotely with `ai_model` when Google retires it. */
        const val DEFAULT_MODEL = "gemini-2.5-flash-lite"
    }
}
