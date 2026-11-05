package com.garidoctor.app.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One entry in the in-app notifications list. [route] is an in-app destination (see [Routes]). */
@Serializable
data class InboxItem(
    val id: Long,
    val title: String,
    val body: String,
    val route: String? = null,
    val atMillis: Long,
    /** "news" (Firebase push), "reminder" (service/papers), "alert" (live check, health). */
    val kind: String,
    val vehicleId: Long? = null,
    val read: Boolean = false,
)

/**
 * In-app notifications: every Firebase message, reminder and live-check alert lands here, newest
 * first, kept on the phone (last 100). [incoming] fires for messages that arrive while the app is
 * open so the UI can show an in-app banner instead of a system notification.
 */
object Inbox {
    private const val KEY = "inbox"
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items
    private val _incoming = MutableSharedFlow<InboxItem>(extraBufferCapacity = 8)
    val incoming: SharedFlow<InboxItem> = _incoming
    @Volatile private var loaded = false

    /** True while an activity is visible; set by MainActivity. */
    @Volatile var foreground = false

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("garidoctor_inbox", Context.MODE_PRIVATE)

    @Synchronized fun load(c: Context) {
        if (loaded) return
        _items.value = runCatching { json.decodeFromString(ListSerializer(InboxItem.serializer()), sp(c).getString(KEY, "[]")!!) }.getOrDefault(emptyList())
        loaded = true
    }

    @Synchronized private fun save(c: Context, list: List<InboxItem>) {
        val trimmed = list.sortedByDescending { it.atMillis }.take(100)
        _items.value = trimmed
        sp(c).edit().putString(KEY, json.encodeToString(ListSerializer(InboxItem.serializer()), trimmed)).apply()
    }

    fun add(c: Context, item: InboxItem, announce: Boolean = false) {
        load(c)
        // The same reminder is posted once a day; keep only the newest copy of identical text.
        save(c, listOf(item) + _items.value.filterNot { it.title == item.title && it.body == item.body })
        if (announce) _incoming.tryEmit(item)
    }

    fun markRead(c: Context, id: Long) { load(c); save(c, _items.value.map { if (it.id == id) it.copy(read = true) else it }) }
    fun markAllRead(c: Context) { load(c); save(c, _items.value.map { it.copy(read = true) }) }
    fun clear(c: Context) { save(c, emptyList()) }
}

/**
 * Where a notification may take the user. Pushes can only name these, never an arbitrary screen;
 * vehicle screens open for [vehicleId] or, for a broadcast push, the vehicle used last.
 */
object Routes {
    private val VEHICLE_SCREENS = setOf("vehicle", "history", "obd", "docs", "service", "fuel", "checks", "symptoms", "vehicleid")
    private val TOP = setOf("home", "settings", "help", "fleet", "inbox", "garages")

    fun resolve(route: String?, vehicleId: Long?, lastVehicleId: Long, vehicleExists: (Long) -> Boolean): String? {
        val r = route?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (r in TOP) return r
        if (r in VEHICLE_SCREENS) {
            val id = vehicleId?.takeIf(vehicleExists) ?: lastVehicleId.takeIf { it > 0 && vehicleExists(it) } ?: return "home"
            return if (r == "vehicle") "vehicle/$id" else "$r/$id"
        }
        return null
    }
}
