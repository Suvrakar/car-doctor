package com.garidoctor.app.sos

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import com.garidoctor.app.data.SosContact
import com.garidoctor.core.model.Lang

/** SOS: location + vehicle + latest diagnosis to a mechanic or family member via SMS / WhatsApp. */
object Sos {
    @SuppressLint("MissingPermission")
    fun lastLocation(context: Context): Location? = try {
        val lm = context.getSystemService(LocationManager::class.java)
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    } catch (_: SecurityException) { null }

    fun message(lang: Lang, vehicle: String, reg: String?, diagnosis: String?, loc: Location?): String {
        val map = loc?.let { "https://maps.google.com/?q=${"%.6f".format(it.latitude)},${"%.6f".format(it.longitude)}" }
        return buildString {
            append("🆘 " + lang.pick("I need help.", "জরুরি সাহায্য দরকার।") + "\n" + lang.pick("Vehicle:", "গাড়ি:") + " $vehicle")
            reg?.let { append(" ($it)") }
            diagnosis?.let { append("\n" + lang.pick("Problem:", "সমস্যা:") + " $it") }
            append("\n" + lang.pick("Location:", "লোকেশন:") + " " + (map ?: lang.pick("unavailable", "পাওয়া যায়নি")))
            append("\n— " + lang.pick("Car Doctor AI", "কার ডক্টর AI"))
        }
    }

    fun sms(context: Context, to: SosContact, text: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${to.phone}")).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    fun whatsapp(context: Context, to: SosContact, text: String) {
        val digits = to.phone.filter { it.isDigit() }.let { if (it.startsWith("0")) "88$it" else it } // BD numbers: 01XXXXXXXXX → 8801XXXXXXXXX
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(text)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    fun call(context: Context, phone: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun directions(context: Context, lat: Double, lng: Double, label: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
