@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import com.garidoctor.app.ui.theme.bandColor
import com.garidoctor.app.ui.theme.urgencyColor
import com.garidoctor.core.health.HealthBand
import com.garidoctor.core.symptom.Urgency
import java.time.LocalDate

/** Fitness-ring style health score. */
@Composable
fun ScoreRing(score: Int, size: Dp = 96.dp, stroke: Dp = 10.dp) {
    val color = bandColor(HealthBand.of(score))
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val st = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft = tl, size = arcSize, style = st)
            drawArc(color, -90f, 360f * score / 100f, false, topLeft = tl, size = arcSize, style = st)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(n(score), fontSize = (size.value / 3.4).sp, fontWeight = FontWeight.Bold, color = color)
            if (size >= 80.dp) Text("/" + n(100), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun UrgencyChip(u: Urgency) {
    val c = urgencyColor(u)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Box(Modifier.size(10.dp).background(c, RoundedCornerShape(50)))
        Spacer(Modifier.width(6.dp))
        Text(u.label.tr(), color = c, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "পেছনে")) }
        }, actions = { actions() })
    }, content = content)
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, decimal: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(com.garidoctor.core.health.Bn.toAscii(s).filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@Composable
fun DateButton(epochDay: Long, onPick: (Long) -> Unit, label: String) {
    val ctx = LocalContext.current
    val d = LocalDate.ofEpochDay(epochDay)
    OutlinedButton(onClick = {
        DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("$label: $d") }
}

@Composable
fun Gap(h: Dp = 8.dp) = Spacer(Modifier.height(h))
