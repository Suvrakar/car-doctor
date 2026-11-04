package com.garidoctor.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.garidoctor.app.ads.Ads
import com.garidoctor.app.backup.CloudBackup
import com.garidoctor.app.billing.ProBilling
import com.garidoctor.app.data.AppDatabase
import com.garidoctor.app.data.BackupManager
import com.garidoctor.app.data.Prefs
import com.garidoctor.app.data.Repository
import com.garidoctor.app.remote.RemoteContent
import com.garidoctor.app.work.ReminderWorker
import com.garidoctor.core.obd.ObdDecoder
import com.garidoctor.core.template.TemplateRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Manual DI: the app is small enough that a container object is clearer than a DI framework. */
class GariDoctorApp : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var repository: Repository; private set
    lateinit var remote: RemoteContent; private set
    lateinit var billing: ProBilling; private set
    lateinit var backup: BackupManager; private set
    lateinit var cloud: CloudBackup; private set
    lateinit var ai: com.garidoctor.app.ai.AiService; private set
    private val _registry = MutableStateFlow(TemplateRegistry.fromClasspath())
    val registry: StateFlow<TemplateRegistry> = _registry

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        applyThemeMode(prefs.themeMode.value)
        val dtc = javaClass.classLoader?.getResourceAsStream("obd/dtc.json")?.bufferedReader()?.use { ObdDecoder.loadDictionary(it.readText()) } ?: emptyMap()
        val db = AppDatabase.get(this)
        repository = Repository(db.dao(), { _registry.value }, dtc)
        backup = BackupManager(db)
        cloud = CloudBackup(this)
        billing = ProBilling(this, prefs).also { it.start() }
        if (!prefs.pro.value) Ads.init(this)
        remote = RemoteContent(this)
        remote.fetch { applyRemote -> _registry.value = TemplateRegistry.fromClasspath().also(applyRemote) }
        // App Check must be installed before any Firebase AI / Firestore call (required for AI Logic).
        if (runCatching { com.google.firebase.FirebaseApp.getApps(this).isNotEmpty() }.getOrDefault(false)) {
            runCatching { com.garidoctor.app.ai.AppCheckSetup.install() }
        }
        ai = com.garidoctor.app.ai.AiService(this, prefs, remote)
        createChannels()
        ReminderWorker.schedule(this)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Service reminders / সার্ভিস রিমাইন্ডার", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_PAPERS, "Paper expiry / কাগজের মেয়াদ", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Health alerts / হেলথ অ্যালার্ট", NotificationManager.IMPORTANCE_DEFAULT))
    }

    companion object {
        /** Light / Dark / System via AppCompat night mode, which also fixes status-bar icon colours. */
        fun applyThemeMode(mode: String) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(when (mode) {
                "LIGHT" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                "DARK" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
        }
        const val CH_SERVICE = "service"
        const val CH_PAPERS = "papers"
        const val CH_ALERTS = "alerts"
        lateinit var instance: GariDoctorApp; private set
    }
}
