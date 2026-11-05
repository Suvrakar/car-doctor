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
import com.garidoctor.core.model.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val _registry = MutableStateFlow(TemplateRegistry.empty())
    /** Vehicle templates and symptom trees; empty until [registryReady]. */
    val registry: StateFlow<TemplateRegistry> = _registry
    private val _ready = MutableStateFlow(false)
    val registryReady: StateFlow<Boolean> = _ready

    /** Background work that must never block the first frame. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bundled JSON lives in Android assets: much faster than Java resources inside the APK. */
    val assetText: (String) -> String? = { path -> runCatching { assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Only cheap, synchronous setup here: the first frame waits for onCreate.
        prefs = Prefs(this)
        applyThemeMode(prefs.themeMode.value)
        Translations.loader = assetText
        val db = AppDatabase.get(this) // opens lazily on first query (off the main thread)
        val dtc by lazy { assetText("obd/dtc.json")?.let { runCatching { ObdDecoder.loadDictionary(it) }.getOrNull() } ?: emptyMap() }
        repository = Repository(db.dao(), { _registry.value }, { dtc })
        backup = BackupManager(db)
        cloud = CloudBackup(this)
        remote = RemoteContent(this)
        billing = ProBilling(this, prefs)
        ai = com.garidoctor.app.ai.AiService(this, prefs, remote)

        appScope.launch {
            // 1. What the first screen needs: templates and the chosen language's catalog.
            _registry.value = TemplateRegistry.load(assetText)
            _ready.value = true
            Translations.preload(prefs.lang.value)
            // 2. Everything else, after the UI is up.
            createChannels()
            ReminderWorker.schedule(this@GariDoctorApp)
            // App Check must be installed before any Firebase AI / Firestore call (required for AI Logic).
            if (runCatching { com.google.firebase.FirebaseApp.getApps(this@GariDoctorApp).isNotEmpty() }.getOrDefault(false)) {
                runCatching { com.garidoctor.app.ai.AppCheckSetup.install() }
            }
            delay(1500) // ads, billing and remote config are heavy; let the first screens settle
            withContext(Dispatchers.Main) {
                billing.start()
                remote.fetch { applyRemote -> appScope.launch { _registry.value = TemplateRegistry.load(assetText).also(applyRemote) } }
            }
            if (!prefs.pro.value) Ads.init(this@GariDoctorApp) // MobileAds may initialise off the main thread
        }
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
