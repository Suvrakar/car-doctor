@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui

import android.app.DatePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.LocalNeu
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.app.ui.theme.isSoft
import com.garidoctor.app.ui.theme.neuRaised
import com.garidoctor.app.ui.theme.softWell
import com.garidoctor.app.ui.theme.Tone
import com.garidoctor.app.ui.theme.color
import com.garidoctor.app.ui.theme.container
import com.garidoctor.app.ui.theme.onContainer
import com.garidoctor.app.ui.theme.tone
import com.garidoctor.core.health.HealthBand
import com.garidoctor.core.symptom.Urgency
import java.time.LocalDate

// ───────────────────────────── Score ring ─────────────────────────────

/**
 * Fitness-ring style health score. Sweeps in on first show and animates between values,
 * and announces itself to TalkBack as "Health score 72 of 100".
 */
@Composable
fun ScoreRing(score: Int, size: Dp = 96.dp, stroke: Dp = 10.dp, showLabel: Boolean = size >= 80.dp) {
    val tone = HealthBand.of(score).tone()
    val color = tone.color()
    val track = if (isSoft) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(score) { sweep.animateTo(score / 100f, tween(durationMillis = 900, easing = FastOutSlowInEasing)) }
    val a11y = t("Health score $score of 100", "হেলথ স্কোর ১০০ এর মধ্যে ${n(score)}")
    val soft = isSoft
    val neu = LocalNeu.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).semantics { contentDescription = a11y }
            .then(if (soft) Modifier.neuRaised(size / 2, (size.value / 16f).coerceIn(4f, 10f).dp, neu.base, neu.highlight, neu.shadow) else Modifier),
    ) {
        if (soft) Box(Modifier.matchParentSize().padding(stroke * 0.35f).softWell(size / 2, (stroke.value / 3f).dp))
        Canvas(Modifier.matchParentSize().padding(if (soft) stroke * 0.6f else 0.dp)) {
            val st = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arc = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val tl = Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft = tl, size = arc, style = st)
            drawArc(color, -90f, 360f * sweep.value, false, topLeft = tl, size = arc, style = st)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(n((sweep.value * 100f).toInt()), fontSize = (size.value / 3.2f).sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (showLabel) Text(t("of 100", "১০০ এর মধ্যে"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───────────────────────────── Pills, badges ─────────────────────────────

@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.background(tone.container(), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(8.dp).background(tone.color(), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tone.onContainer(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun UrgencyChip(u: Urgency) = StatusPill(u.label.tr(), u.tone())

@Composable
fun Dot(color: androidx.compose.ui.graphics.Color) = Box(Modifier.size(10.dp).background(color, CircleShape))

/** Tinted circular icon used as the leading element of rows. */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone = Tone.NEUTRAL, size: Dp = 40.dp) {
    val bg = if (tone == Tone.NEUTRAL) MaterialTheme.colorScheme.secondaryContainer else tone.container()
    val fg = if (tone == Tone.NEUTRAL) MaterialTheme.colorScheme.onSecondaryContainer else tone.onContainer()
    val neu = LocalNeu.current
    val base = if (isSoft && tone == Tone.NEUTRAL) Modifier.neuRaised(size / 2, 3.dp, neu.base, neu.highlight, neu.shadow) else Modifier.background(bg, CircleShape)
    val fgSoft = if (isSoft && tone == Tone.NEUTRAL) MaterialTheme.colorScheme.primary else fg
    Box(Modifier.size(size).then(base), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fgSoft, modifier = Modifier.size(size * 0.55f))
    }
}

/** Points deducted, shown as a compact pill ("−15"). */
@Composable
fun PointsPill(points: Int) {
    Text("−${n(points)}", style = MaterialTheme.typography.labelLarge, color = Tone.BAD.onContainer(),
        modifier = Modifier.background(Tone.BAD.container(), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp))
}

// ───────────────────────────── Layout ─────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.l, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Grouped surface for related rows, Google-settings style. */
@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppSurface(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** A tappable row with leading badge, two lines of text, optional trailing slot and chevron. */
@Composable
fun ActionRow(
    icon: ImageVector, title: String, subtitle: String? = null, tone: Tone = Tone.NEUTRAL,
    trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (subtitle != null) { { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) } } else null,
        leadingContent = { IconBadge(icon, tone) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = if (onClick != null) Modifier.clickableItem(onClick) else Modifier,
    )
}

/** Interval progress bar: how much of the service interval is used, coloured by status. */
@Composable
fun UsageBar(fraction: Float, tone: Tone, modifier: Modifier = Modifier.fillMaxWidth()) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "usage")
    LinearProgressIndicator(
        progress = { animated }, modifier = modifier.height(if (isSoft) 8.dp else 6.dp).softWell(4.dp, 2.dp),
        color = tone.color(), trackColor = if (isSoft) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round,
        drawStopIndicator = {},
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        IconBadge(icon, Tone.NEUTRAL, 72.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

// ───────────────────────────── Scaffold & navigation ─────────────────────────────

enum class Tab(val route: String) { GARAGE("home"), FLEET("fleet"), HELP("help"), SETTINGS("settings") }

@Composable
fun MainNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = if (isSoft) 0.dp else 3.dp) {
        listOf(
            Triple(Tab.GARAGE, t("Garage", "গ্যারেজ"), Icons.Filled.DirectionsCar to Icons.Outlined.DirectionsCar),
            Triple(Tab.FLEET, t("Fleet", "ফ্লিট"), Icons.Filled.Dashboard to Icons.Outlined.Dashboard),
            Triple(Tab.HELP, t("Help & SOS", "সাহায্য ও SOS"), Icons.Filled.LocalHospital to Icons.Outlined.LocalHospital),
            Triple(Tab.SETTINGS, t("Settings", "সেটিংস"), Icons.Filled.Settings to Icons.Outlined.Settings),
        ).forEach { (tab, label, icons) ->
            NavigationBarItem(
                selected = current == tab, onClick = { if (current != tab) onSelect(tab) },
                icon = { Icon(if (current == tab) icons.first else icons.second, contentDescription = null) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

/**
 * Standard screen: top app bar that tints on scroll (or a large collapsing one for top-level
 * screens), optional bottom bar / FAB / snackbar host.
 */
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    large: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val behavior = if (large) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val nav: @Composable () -> Unit = {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "পেছনে")) }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            if (large) LargeTopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = nav, actions = actions, scrollBehavior = behavior)
            else TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = nav, actions = actions, scrollBehavior = behavior)
        },
        bottomBar = bottomBar,
        floatingActionButton = fab,
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

// ───────────────────────────── Inputs ─────────────────────────────

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, decimal: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), supporting: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(com.garidoctor.core.health.Bn.toAscii(s).filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        supportingText = if (supporting != null) { { Text(supporting) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@Composable
fun DateButton(epochDay: Long, onPick: (Long) -> Unit, label: String) {
    val ctx = LocalContext.current
    val d = LocalDate.ofEpochDay(epochDay)
    OutlinedButton(onClick = {
        DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("$label: $d") }
}

@Composable
fun Gap(h: Dp = Spacing.s) = Spacer(Modifier.height(h))

fun Modifier.clickableItem(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
