package com.garidoctor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.garidoctor.core.health.HealthBand
import com.garidoctor.core.symptom.Urgency

val Brand = Color(0xFF0E7C66)
val Good = Color(0xFF1E8E3E)
val Warn = Color(0xFFE69600)
val Bad = Color(0xFFC62828)

private val Light = lightColorScheme(primary = Brand, secondary = Color(0xFF3A6B5E), tertiary = Color(0xFFB4561C), background = Color(0xFFF7F8F6), surface = Color.White)
private val Dark = darkColorScheme(primary = Color(0xFF5FD3B5), secondary = Color(0xFF9CCBBE), tertiary = Color(0xFFFFB68A))

@Composable
fun GariTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

fun bandColor(b: HealthBand) = when (b) { HealthBand.EXCELLENT, HealthBand.GOOD -> Good; HealthBand.FAIR -> Warn; else -> Bad }
fun urgencyColor(u: Urgency) = when (u) { Urgency.SAFE -> Good; Urgency.THIS_WEEK -> Warn; Urgency.STOP_NOW -> Bad }
