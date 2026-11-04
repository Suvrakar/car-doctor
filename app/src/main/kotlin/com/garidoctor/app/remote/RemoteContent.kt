package com.garidoctor.app.remote

import android.content.Context
import android.util.Log
import com.garidoctor.core.template.TemplateRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

/**
 * Pulls template / symptom-tree / garage / sponsor JSON from Firebase Remote Config (free tier).
 *
 * Keys:
 *  - template_<id>      full VehicleTemplate JSON (applied only if its version is newer and it validates)
 *  - symptoms_<id>      SymptomTree JSON
 *  - garages            garages.json format (replaces the bundled sample list)
 *  - sponsor_<CATEGORY> {"brand":"...","text":{"en":"...","bn":"..."}} sponsored tip per part category
 *  - ai_enabled (bool, true)          cloud AI off switch
 *  - ai_model (string)                Gemini model name, e.g. gemini-2.5-flash-lite
 *  - ai_daily_free / ai_daily_pro     cloud AI uses per user per day (3 / 30; Firestore caps at 40)
 *  - ai_max_output_tokens (512)       answer length cap per request
 *  - ai_prompt_paper / ai_prompt_receipt / ai_prompt_light   prompt overrides
 *
 * Firebase is optional. Without google-services.json the app runs fully on bundled data.
 */
class RemoteContent(private val context: Context) {
    private var rc: FirebaseRemoteConfig? = null

    val available: Boolean get() = rc != null

    fun fetch(onTemplates: ((TemplateRegistry) -> Unit) -> Unit) {
        val ok = try {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        } catch (e: Exception) { false }
        if (!ok) { Log.i(TAG, "Firebase not configured; using bundled templates"); return }
        val config = FirebaseRemoteConfig.getInstance()
        config.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 6 * 3600 })
        rc = config
        config.fetchAndActivate().addOnCompleteListener {
            onTemplates { registry ->
                config.all.forEach { (key, value) ->
                    when {
                        key.startsWith("template_") -> registry.applyRemoteTemplate(value.asString()).forEach { Log.w(TAG, "rejected $key: $it") }
                        key.startsWith("symptoms_") -> if (!registry.applyRemoteTree(value.asString())) Log.w(TAG, "rejected $key")
                    }
                }
            }
        }
    }

    fun string(key: String): String? = rc?.getString(key)?.takeIf { it.isNotBlank() }

    /** Remote Config values with in-code defaults (used when Firebase isn't set up or the key is unset). */
    fun long(key: String, default: Long): Long = rc?.getValue(key)?.takeIf { it.source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC }?.asLong() ?: default
    fun bool(key: String, default: Boolean): Boolean = rc?.getValue(key)?.takeIf { it.source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC }?.asBoolean() ?: default

    companion object { private const val TAG = "RemoteContent" }
}
