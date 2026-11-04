package com.garidoctor.app.data

import android.content.Context
import com.garidoctor.core.model.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small settings kept in SharedPreferences. Exposed as StateFlows so Compose recomposes. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("garidoctor", Context.MODE_PRIVATE)

    private val _lang = MutableStateFlow(if (sp.getString("lang", null) == "en") Lang.EN else Lang.BN)
    val lang: StateFlow<Lang> = _lang
    fun setLang(l: Lang) { sp.edit().putString("lang", l.name.lowercase()).apply(); _lang.value = l }

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

    var lastNotifiedDay: Long
        get() = sp.getLong("last_notified_day", 0)
        set(v) { sp.edit().putLong("last_notified_day", v).apply() }

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) { sp.edit().putBoolean("asked_notif", v).apply() }
}

data class SosContact(val name: String, val phone: String)
