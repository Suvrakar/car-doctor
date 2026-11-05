package com.garidoctor.app.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.garidoctor.app.GariDoctorApp
import com.garidoctor.app.R
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.MainActivity
import com.garidoctor.core.docs.DocumentExpiry
import com.garidoctor.core.fuel.FuelAnalyzer
import com.garidoctor.core.model.Lang
import com.garidoctor.core.schedule.DueState
import com.garidoctor.core.VehicleDoctor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs once a day, fully on the phone (no server): checks every vehicle for services that
 * just became due/overdue, papers hitting a reminder day, and mileage anomalies.
 */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GariDoctorApp
        // WorkManager may start the process just for this job; templates load in the background,
        // so wait for them (or retry later) instead of silently skipping every vehicle.
        withTimeoutOrNull(15_000) { app.registryReady.first { it } } ?: return Result.retry()
        val day = today()
        if (app.prefs.lastNotifiedDay == day) return Result.success()
        val lang = app.prefs.lang.value
        val repo = app.repository
        var nid = 1000
        repo.vehiclesOnce().forEach { ve ->
            val rec = repo.recordsOnce(ve)
            val c = repo.checkup(rec) ?: return@forEach
            val overdue = c.parts.filter { it.state == DueState.OVERDUE }
            val soon = c.parts.filter { it.state == DueState.DUE_SOON }
            if (overdue.isNotEmpty() || soon.isNotEmpty()) {
                val names = (overdue + soon).take(3).joinToString { it.part.name.get(lang) }
                val title = lang.pick("${ve.nickname}: service due", "${ve.nickname}: সার্ভিসের সময় হয়েছে")
                val body = lang.pick("$names · health score ${c.health.score}/100", "$names · হেলথ স্কোর ${c.health.score}/100")
                notify(GariDoctorApp.CH_SERVICE, nid++, title, body, ve.id)
            }
            rec.documents.filter { DocumentExpiry.shouldRemind(it, day) }.forEach { d ->
                val st = DocumentExpiry.status(d, day)
                val name = d.type.label.get(lang)
                val title = "${ve.nickname}: $name"
                val body = when {
                    st.daysLeft < 0 -> lang.pick("Expired ${-st.daysLeft} days ago. Renew to avoid fines.", "মেয়াদ ${-st.daysLeft} দিন আগে শেষ। জরিমানা এড়াতে নবায়ন করুন।")
                    st.daysLeft == 0L -> lang.pick("Expires today!", "আজ মেয়াদ শেষ!")
                    else -> lang.pick("Expires in ${st.daysLeft} days.", "${st.daysLeft} দিনে মেয়াদ শেষ হবে।")
                }
                notify(GariDoctorApp.CH_PAPERS, nid++, title, body, ve.id)
            }
            FuelAnalyzer.alert(c.fuel.trend, VehicleDoctor.isElectric(rec.vehicle.energyType))?.let { a ->
                // Weekly at most.
                if (day % 7 == 0L) notify(GariDoctorApp.CH_ALERTS, nid++, "${ve.nickname}: ${a.title.get(lang)}", a.causes.take(3).joinToString { it.get(lang) }, ve.id)
            }
        }
        app.prefs.lastNotifiedDay = day
        return Result.success()
    }

    /** System notification + in-app inbox entry; tapping opens the matching screen of that vehicle. */
    @Suppress("UNUSED_PARAMETER")
    private fun notify(channel: String, id: Int, title: String, body: String, vehicleId: Long) {
        com.garidoctor.app.notify.Notifier.post(applicationContext, channel, title, body,
            kind = if (channel == GariDoctorApp.CH_ALERTS) "alert" else "reminder",
            route = when (channel) { GariDoctorApp.CH_PAPERS -> "docs"; GariDoctorApp.CH_ALERTS -> "fuel"; else -> "vehicle" },
            vehicleId = vehicleId)
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily-reminders", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
