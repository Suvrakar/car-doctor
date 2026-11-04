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

    companion object { private const val TAG = "RemoteContent" }
}
