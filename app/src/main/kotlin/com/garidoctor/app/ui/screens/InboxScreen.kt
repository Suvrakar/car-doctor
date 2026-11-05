package com.garidoctor.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.notify.Inbox
import com.garidoctor.app.notify.InboxItem
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.app.ui.theme.Tone
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "news" -> Icons.Default.Campaign
    "alert" -> Icons.Default.Warning
    else -> Icons.Default.NotificationsActive
}

/** All notifications: Firebase news, service and paper reminders, live-check alerts. */
@Composable
fun InboxScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val items by Inbox.items.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Inbox.load(ctx) }
    Screen(t("Notifications", "নোটিফিকেশন"), onBack, actions = {
        if (items.any { !it.read }) TextButton(onClick = { Inbox.markAllRead(ctx) }) { Text(t("Mark all read", "সব পড়া হয়েছে")) }
        if (items.isNotEmpty()) IconButton(onClick = { Inbox.clear(ctx) }) { Icon(Icons.Default.DeleteSweep, t("Clear all", "সব মুছুন")) }
    }) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Default.NotificationsNone, t("No notifications yet", "এখনো কোনো নোটিফিকেশন নেই"),
                    t("Service and paper reminders, live-check alerts and news from Car Doctor AI appear here.",
                        "সার্ভিস ও কাগজের রিমাইন্ডার, লাইভ চেকের সতর্কতা এবং কার ডক্টর AI এর খবর এখানে দেখাবে।"))
            }
            return@Screen
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(Spacing.gutter, Spacing.s, Spacing.gutter, Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(items, key = { it.id }) { item ->
                AppSurface(Modifier.fillMaxWidth(), radius = 20.dp, onClick = {
                    Inbox.markRead(ctx, item.id)
                    vm.open(item.route, item.vehicleId)
                }) {
                    Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.Top) {
                        IconBadge(kindIcon(item.kind), if (item.kind == "alert") Tone.WARN else Tone.NEUTRAL)
                        Spacer(Modifier.width(Spacing.m))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (item.read) FontWeight.Normal else FontWeight.Bold)
                            if (item.body.isNotBlank()) Text(item.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.atMillis)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!item.read) Box(Modifier.padding(top = 6.dp).size(8.dp)) { Badge() }
                    }
                }
            }
        }
    }
}

/** Bell for the Home top bar, with the unread count. */
@Composable
fun InboxBell(onClick: () -> Unit) {
    val ctx = LocalContext.current
    val items by Inbox.items.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Inbox.load(ctx) }
    val unread = items.count { !it.read }
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else n(unread)) } }) {
            Icon(if (unread > 0) Icons.Default.Notifications else Icons.Default.NotificationsNone, t("Notifications", "নোটিফিকেশন"))
        }
    }
}

/**
 * A Firebase message that arrives while the app is open slides in at the top for 6 seconds
 * (tap to open its screen) instead of a system notification. It also stays in the inbox.
 */
@Composable
fun InAppBanner(vm: MainViewModel) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<InboxItem?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Inbox.incoming.collect { shown = it; visible = true } }
    LaunchedEffect(shown) { if (shown != null) { delay(6000); visible = false } }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.s)) {
        AnimatedVisibility(visible && shown != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            val item = shown ?: return@AnimatedVisibility
            AppSurface(Modifier.fillMaxWidth(), radius = 20.dp, elevation = 10.dp, tint = MaterialTheme.colorScheme.secondaryContainer, onClick = {
                visible = false; Inbox.markRead(ctx, item.id); vm.open(item.route ?: "inbox", item.vehicleId)
            }) {
                Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    Icon(kindIcon(item.kind), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (item.body.isNotBlank()) Text(item.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    IconButton(onClick = { visible = false }) { Icon(Icons.Default.Close, t("Close", "বন্ধ"), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }
        }
    }
}
