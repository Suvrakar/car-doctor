package com.garidoctor.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.app.ui.theme.Good
import com.garidoctor.app.ui.theme.Warn

/**
 * Simple drawn reference pictures for guided checks, keyed by `CheckReference.image`
 * (e.g. "tyre_good", "chain_loose"). Diagrams are clearer than photos for "compare with this one",
 * need no assets, and adapt to dark mode. Unknown ids draw nothing.
 */
@Composable
fun ReferenceArt(image: String?, modifier: Modifier = Modifier.size(88.dp)) {
    if (image == null) return
    val ink = MaterialTheme.colorScheme.onSurface
    val soft = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        when {
            image.startsWith("tyre") -> tyre(image, ink, soft)
            image.startsWith("chain") -> chain(image, ink)
            image.startsWith("pad") -> pad(image, ink, soft)
            image.startsWith("oil") -> dipstick(image, ink)
            image.startsWith("batt") -> terminal(image, ink, soft)
            image.startsWith("coolant") -> reservoir(image, ink, if (image == "coolant_dirty") Color(0xFF8D5A2B) else Color(0xFF2E9BD6))
            image.startsWith("water") -> cell(image, ink)
        }
    }
}

private fun DrawScope.stroke(w: Float = 3f) = Stroke(width = w * density, cap = StrokeCap.Round)

/** Tyre cross-section: groove depth vs the wear bar. */
private fun DrawScope.tyre(id: String, ink: Color, soft: Color) {
    val w = size.width; val h = size.height
    drawRoundRect(soft, Offset(0f, h * 0.2f), Size(w, h * 0.6f), CornerRadius(12f, 12f))
    val depth = when (id) { "tyre_good" -> 0.45f; "tyre_worn" -> 0.15f; else -> 0.02f }
    val c = when (id) { "tyre_good" -> Good; "tyre_worn" -> Warn; else -> Bad }
    for (i in 1..3) {
        val x = w * i / 4f
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(x - w * 0.05f, h * 0.2f), Size(w * 0.1f, h * 0.6f * depth))
    }
    // wear bar at 1.6 mm
    drawLine(c, Offset(w * 0.1f, h * 0.2f + h * 0.6f * 0.15f), Offset(w * 0.9f, h * 0.2f + h * 0.6f * 0.15f), strokeWidth = 2 * density)
    if (id == "tyre_bald") drawLine(Bad, Offset(w * 0.3f, h * 0.55f), Offset(w * 0.45f, h * 0.75f), strokeWidth = 2 * density)
    drawRoundRect(ink, Offset(0f, h * 0.2f), Size(w, h * 0.6f), CornerRadius(12f, 12f), style = stroke(2f))
}

/** Lower chain run between sprockets, sag shown with a bracket. */
private fun DrawScope.chain(id: String, ink: Color) {
    val w = size.width; val h = size.height
    val sag = when (id) { "chain_good" -> 0.12f; "chain_loose" -> 0.35f; else -> 0.04f }
    val c = when (id) { "chain_good" -> Good; "chain_loose" -> Bad; else -> Warn }
    drawCircle(ink, w * 0.14f, Offset(w * 0.16f, h * 0.4f), style = stroke())
    drawCircle(ink, w * 0.2f, Offset(w * 0.78f, h * 0.4f), style = stroke())
    val p = Path().apply {
        moveTo(w * 0.16f, h * 0.4f + w * 0.14f)
        quadraticBezierTo(w * 0.47f, h * (0.62f + sag), w * 0.78f, h * 0.4f + w * 0.2f)
    }
    drawPath(p, if (id == "chain_rusty") Color(0xFFA0522D) else ink, style = stroke(4f))
    drawLine(c, Offset(w * 0.47f, h * 0.62f), Offset(w * 0.47f, h * (0.62f + sag / 2f)), strokeWidth = 3 * density)
}

/** Brake caliper: pad material thickness against the disc. */
private fun DrawScope.pad(id: String, ink: Color, soft: Color) {
    val w = size.width; val h = size.height
    drawRect(soft, Offset(w * 0.45f, h * 0.05f), Size(w * 0.1f, h * 0.9f)) // disc
    val t = when (id) { "pad_good" -> 0.16f; "pad_thin" -> 0.05f; else -> 0.0f }
    val c = when (id) { "pad_good" -> Good; "pad_thin" -> Warn; else -> Bad }
    listOf(-1f, 1f).forEach { side ->
        val plateX = if (side < 0) w * 0.15f else w * 0.75f
        drawRect(ink, Offset(plateX, h * 0.25f), Size(w * 0.1f, h * 0.5f)) // backing plate
        if (t > 0) {
            val padX = if (side < 0) w * 0.25f else w * (0.75f - t)
            drawRect(c, Offset(padX, h * 0.25f), Size(w * t, h * 0.5f))
        }
    }
    if (id == "pad_gone") for (i in 0..2) drawLine(Bad, Offset(w * 0.4f, h * (0.3f + i * 0.2f)), Offset(w * 0.6f, h * (0.35f + i * 0.2f)), strokeWidth = 2 * density)
}

/** Dipstick with MIN/MAX marks. */
private fun DrawScope.dipstick(id: String, ink: Color) {
    val w = size.width; val h = size.height
    val x = w * 0.5f
    drawLine(ink, Offset(x, h * 0.05f), Offset(x, h * 0.95f), strokeWidth = 4 * density)
    val max = h * 0.6f; val min = h * 0.8f
    drawLine(ink, Offset(x - w * 0.15f, max), Offset(x + w * 0.15f, max), strokeWidth = 2 * density)
    drawLine(ink, Offset(x - w * 0.15f, min), Offset(x + w * 0.15f, min), strokeWidth = 2 * density)
    val level = when (id) { "oil_good" -> h * 0.66f; "oil_low" -> h * 0.88f; else -> h * 0.62f }
    val color = when (id) { "oil_good" -> Color(0xFFC8962E); "oil_low" -> Color(0xFFC8962E); else -> Color(0xFF222222) }
    drawRoundRect(color, Offset(x - 4 * density, level), Size(8 * density, h * 0.95f - level), CornerRadius(4f, 4f))
}

/** Battery terminal clamp. */
private fun DrawScope.terminal(id: String, ink: Color, soft: Color) {
    val w = size.width; val h = size.height
    drawRoundRect(soft, Offset(w * 0.05f, h * 0.4f), Size(w * 0.9f, h * 0.55f), CornerRadius(8f, 8f))
    drawRoundRect(ink, Offset(w * 0.05f, h * 0.4f), Size(w * 0.9f, h * 0.55f), CornerRadius(8f, 8f), style = stroke(2f))
    drawRect(ink, Offset(w * 0.25f, h * 0.25f), Size(w * 0.14f, h * 0.15f)) // post
    val gap = if (id == "batt_loose") h * 0.08f else 0f
    drawRect(Color(0xFFB0B0B0), Offset(w * 0.18f, h * 0.2f - gap), Size(w * 0.28f, h * 0.1f)) // clamp
    drawLine(Color(0xFFC62828), Offset(w * 0.46f, h * 0.25f - gap), Offset(w * 0.9f, h * 0.1f), strokeWidth = 5 * density) // cable
    if (id == "batt_corroded") for (i in 0..6) drawCircle(Color(0xFFB9E3C6), 3.5f * density, Offset(w * (0.2f + i * 0.04f), h * (0.32f + (i % 2) * 0.05f)))
}

/** Coolant reservoir with LOW / FULL lines. */
private fun DrawScope.reservoir(id: String, ink: Color, fluid: Color) {
    val w = size.width; val h = size.height
    val top = h * 0.1f; val bottom = h * 0.92f
    val level = when (id) { "coolant_good" -> h * 0.45f; "coolant_low" -> h * 0.85f; else -> h * 0.5f }
    drawRect(fluid.copy(alpha = 0.7f), Offset(w * 0.25f, level), Size(w * 0.5f, bottom - level))
    drawRect(ink, Offset(w * 0.25f, top), Size(w * 0.5f, bottom - top), style = stroke(2f))
    drawLine(Good, Offset(w * 0.2f, h * 0.35f), Offset(w * 0.32f, h * 0.35f), strokeWidth = 2 * density) // FULL
    drawLine(Warn, Offset(w * 0.2f, h * 0.7f), Offset(w * 0.32f, h * 0.7f), strokeWidth = 2 * density) // LOW
}

/** Lead-acid cell viewed through the filler hole: plates vs electrolyte. */
private fun DrawScope.cell(id: String, ink: Color) {
    val w = size.width; val h = size.height
    val bulge = if (id == "water_bulge") w * 0.06f else 0f
    val level = when (id) { "water_good" -> h * 0.25f; else -> h * 0.5f }
    drawRect(Color(0xFF7FB3D5).copy(alpha = 0.6f), Offset(w * 0.15f, level), Size(w * 0.7f, h * 0.88f - level))
    for (i in 0..4) drawLine(ink.copy(alpha = 0.7f), Offset(w * (0.25f + i * 0.125f), h * 0.35f), Offset(w * (0.25f + i * 0.125f), h * 0.85f), strokeWidth = 3 * density)
    val p = Path().apply {
        moveTo(w * 0.15f, h * 0.1f); lineTo(w * 0.85f, h * 0.1f)
        quadraticBezierTo(w * 0.85f + bulge * 2, h * 0.5f, w * 0.85f, h * 0.9f); lineTo(w * 0.15f, h * 0.9f)
        quadraticBezierTo(w * 0.15f - bulge * 2, h * 0.5f, w * 0.15f, h * 0.1f)
    }
    drawPath(p, if (id == "water_bulge") Bad else ink, style = stroke(2f))
}
