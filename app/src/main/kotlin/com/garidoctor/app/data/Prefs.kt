package com.garidoctor.app.data

import android.content.Context
import com.garidoctor.core.model.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small settings kept in SharedPreferences. Exposed as StateFlows so Compose recomposes. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("garidoctor", Context.MODE_PRIVATE)

    private val _lang = MutableStateFlow(Lang.of(sp.getString("lang", null)) ?: Lang.BN)
    val lang: StateFlow<Lang> = _lang
    fun setLang(l: Lang) { sp.edit().putString("lang", l.code).putBoolean("lang_chosen", true).apply(); _lang.value = l; _langChosen.value = true }

    /** False until the user picks a language (first-launch picker). Existing installs count as chosen. */
    private val _langChosen = MutableStateFlow(sp.getBoolean("lang_chosen", sp.contains("lang")))
    val langChosen: StateFlow<Boolean> = _langChosen

    private val _pro = MutableStateFlow(sp.getBoolean("pro", false))
    val pro: StateFlow<Boolean> = _pro
    fun setPro(v: Boolean, code: String? = null) { sp.edit().putBoolean("pro", v).putString("pro_code", code).apply(); _pro.value = v }

    private val _sos = MutableStateFlow(readSos())
    val sosContacts: StateFlow<List<SosContact>> = _sos
    private fun readSos(): List<SosContact> = (sp.getString("sos", "") ?: "").lines().filter { it.contains('|') }.map {
        val (n, p) = it.split('|', limit = 2); SosContact(n, p)
    }
    fun setSos(list: List<SosContact>) {
        sp.edit().putString("sos", list.joinToString("\n") { "${it.name.replace("|", " ")}|${it.phone}" }).apply(); _sos.value = list
    }

    // ---- Appearance ----
    private val _style = MutableStateFlow(sp.getString("ui_style", "SOFT")!!)
    /** "SOFT" (neumorphic) or "MATERIAL". */
    val uiStyle: StateFlow<String> = _style
    fun setUiStyle(v: String) { sp.edit().putString("ui_style", v).apply(); _style.value = v }

    private val _themeMode = MutableStateFlow(sp.getString("theme_mode", "SYSTEM")!!)
    /** "SYSTEM", "LIGHT" or "DARK". */
    val themeMode: StateFlow<String> = _themeMode
    fun setThemeMode(v: String) { sp.edit().putString("theme_mode", v).apply(); _themeMode.value = v }

    private val _accent = MutableStateFlow(sp.getString("accent", "TEAL")!!)
    /** "TEAL", "BLUE", "ORANGE" or "PURPLE". */
    val accent: StateFlow<String> = _accent
    fun setAccent(v: String) { sp.edit().putString("accent", v).apply(); _accent.value = v }

    // ---- Cloud AI consent (photos/voice text leave the phone only after this is true) ----
    private val _aiConsent = MutableStateFlow(sp.getBoolean("ai_consent", false))
    val aiConsent: StateFlow<Boolean> = _aiConsent
    fun setAiConsent(v: Boolean) { sp.edit().putBoolean("ai_consent", v).apply(); _aiConsent.value = v }

    var lastNotifiedDay: Long
        get() = sp.getLong("last_notified_day", 0)
        set(v) { sp.edit().putLong("last_notified_day", v).apply() }

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) { sp.edit().putBoolean("asked_notif", v).apply() }
}

data class SosContact(val name: String, val phone: String)
