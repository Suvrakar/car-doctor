package com.garidoctor.app.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.garidoctor.app.R
import com.garidoctor.app.ui.MainActivity

/** Posts a notification and records it in the in-app [Inbox]. Used by reminders, pushes and alerts. */
object Notifier {
    /**
     * [system] false = in-app only (the user is looking at the app, or it's an informational alert).
     * Returns the inbox item.
     */
    fun post(
        context: Context, channel: String, title: String, body: String, kind: String,
        route: String? = null, vehicleId: Long? = null, system: Boolean = true, announce: Boolean = false,
    ): InboxItem {
        val ctx = context.applicationContext
        val item = InboxItem(System.currentTimeMillis(), title, body, route, System.currentTimeMillis(), kind, vehicleId)
        Inbox.add(ctx, item, announce)
        if (system) show(ctx, channel, item)
        return item
    }

    fun show(ctx: Context, channel: String, item: InboxItem) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = (item.id % Int.MAX_VALUE).toInt()
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, item.route ?: "inbox")
            .putExtra(MainActivity.EXTRA_INBOX_ID, item.id)
            .apply { item.vehicleId?.let { putExtra(MainActivity.EXTRA_VEHICLE, it) } }
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }
}
