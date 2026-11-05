package com.garidoctor.app.notify

import android.content.Context
import com.garidoctor.app.GariDoctorApp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging. Messages are sent from the Firebase Console (Messaging → campaign) to
 * topics, so no server is needed:
 *  - "all"                    everyone with news enabled
 *  - "lang_bn", "lang_en", …  by app language
 *  - "type_car", "type_motorcycle", "type_bus", …  owners of that vehicle type
 *  - "plan_free" / "plan_pro"
 * Optional custom data on a message: "route" (home, settings, inbox, vehicle, history, obd, docs,
 * service, fuel…), and "title"/"body" so it also reaches the in-app inbox when the app was closed.
 */
object Push {
    private const val TOPICS = "push_topics"

    fun available(c: Context) = runCatching { FirebaseApp.getApps(c).isNotEmpty() }.getOrDefault(false)

    fun wantedTopics(enabled: Boolean, langCode: String, templateIds: Collection<String>, pro: Boolean): Set<String> =
        if (!enabled) emptySet()
        else (setOf("all", "lang_$langCode", if (pro) "plan_pro" else "plan_free") + templateIds.map { "type_$it" }).toSet()

    /** Subscribes/unsubscribes only the differences from last time. Safe to call often. */
    fun sync(c: Context, wanted: Set<String>) {
        if (!available(c)) return
        val sp = c.applicationContext.getSharedPreferences("garidoctor", Context.MODE_PRIVATE)
        val have = sp.getStringSet(TOPICS, emptySet())!!.toSet()
        if (have == wanted) return
        val fm = FirebaseMessaging.getInstance()
        fm.isAutoInitEnabled = wanted.isNotEmpty()
        (wanted - have).forEach { fm.subscribeToTopic(it) }
        (have - wanted).forEach { fm.unsubscribeFromTopic(it) }
        sp.edit().putStringSet(TOPICS, wanted).apply()
    }

    /**
     * Keeps this device's token next to the user's account when they're already signed in (cloud
     * backup or AI), so a Cloud Function can later message one user. Never signs in by itself.
     */
    fun saveToken(c: Context, token: String) {
        if (!available(c)) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val app = c.applicationContext as? GariDoctorApp ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("devices").document(token.takeLast(32))
            .set(mapOf("token" to token, "lang" to app.prefs.lang.value.code, "platform" to "android", "updated" to System.currentTimeMillis()), SetOptions.merge())
    }
}

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.saveToken(this, token)

    /**
     * Called for data messages always, and for notification messages while the app is open.
     * Open app → in-app banner + inbox; closed → system notification + inbox.
     */
    override fun onMessageReceived(m: RemoteMessage) {
        val title = m.notification?.title ?: m.data["title"] ?: return
        val body = m.notification?.body ?: m.data["body"] ?: ""
        val fg = Inbox.foreground
        Notifier.post(this, GariDoctorApp.CH_NEWS, title, body, kind = "news", route = m.data["route"], system = !fg, announce = fg)
    }
}
