package com.garidoctor.app.ui.theme

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/** BlurMaskFilter is only honoured by the hardware renderer from Android 9 (API 28). */
private val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/**
 * Raised neumorphic surface: fills with [base] and casts a light shadow up-left and a dark
 * shadow down-right, so the shape reads as extruded from a background of the same colour.
 * On Android 8 and older (no blur in hardware rendering) it draws a flat fill with a hairline edge.
 */
fun Modifier.neuRaised(radius: Dp, elevation: Dp, base: Color, highlight: Color, shadow: Color): Modifier = drawBehind {
    val r = radius.toPx()
    val e = elevation.toPx()
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        if (e > 0f && blurSupported) {
            val p = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = BlurMaskFilter(e * 1.6f, BlurMaskFilter.Blur.NORMAL)
            }
            p.color = shadow.toArgb()
            c.drawRoundRect(e, e, size.width + e, size.height + e, r, r, p)
            p.color = highlight.toArgb()
            c.drawRoundRect(-e, -e, size.width - e, size.height - e, r, r, p)
        }
        val fill = Paint().asFrameworkPaint().apply { isAntiAlias = true; color = base.toArgb() }
        c.drawRoundRect(0f, 0f, size.width, size.height, r, r, fill)
        if (!blurSupported) {
            val edge = Paint().asFrameworkPaint().apply {
                isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 1.dp.toPx(); color = shadow.copy(alpha = 0.5f).toArgb()
            }
            c.drawRoundRect(0f, 0f, size.width, size.height, r, r, edge)
        }
    }
}

/**
 * Pressed-in ("well") surface: inner shadow dark along the top-left edges and light along the
 * bottom-right, drawn as blurred strokes clipped to the shape.
 */
fun Modifier.neuInset(radius: Dp, depth: Dp, base: Color, highlight: Color, shadow: Color): Modifier = drawBehind {
    val r = radius.toPx()
    val d = depth.toPx()
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        val fill = Paint().asFrameworkPaint().apply { isAntiAlias = true; color = base.toArgb() }
        c.drawRoundRect(0f, 0f, size.width, size.height, r, r, fill)
        if (!blurSupported || d <= 0f) return@drawIntoCanvas
        val clip = android.graphics.Path().apply { addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        val stroke = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = d * 2f
            maskFilter = BlurMaskFilter(d, BlurMaskFilter.Blur.NORMAL)
        }
        // A rect shifted down-right leaves its top/left stroke just inside the top/left edges.
        stroke.color = shadow.toArgb()
        c.drawRoundRect(d, d, size.width + d, size.height + d, r, r, stroke)
        stroke.color = highlight.toArgb()
        c.drawRoundRect(-d, -d, size.width - d, size.height - d, r, r, stroke)
        c.restore()
    }
}

/**
 * The app's one surface primitive. In the Soft style it is a raised neumorphic slab that sinks
 * when pressed; in the Material style it is a standard card. Screens use this instead of Card,
 * so the Appearance setting restyles every screen at once.
 */
@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    elevation: Dp = 7.dp,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalUiStyle.current
    if (style == UiStyle.MATERIAL) {
        val shape = RoundedCornerShape(radius)
        val colors = CardDefaults.cardColors(containerColor = tint ?: MaterialTheme.colorScheme.surfaceContainerLow)
        if (onClick != null) ElevatedCard(onClick = onClick, shape = shape, modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = tint ?: MaterialTheme.colorScheme.surfaceContainerLow)) { Box(content = content) }
        else Card(shape = shape, modifier = modifier, colors = colors) { Box(content = content) }
        return
    }
    val neu = LocalNeu.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = if (onClick != null && pressed) 1.dp else elevation
    val e by animateDpAsState(target, tween(120), label = "neuElevation")
    val shape = RoundedCornerShape(radius)
    var m = modifier.neuRaised(radius, e, tint ?: neu.base, neu.highlight, neu.shadow).clip(shape)
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
    Box(m, content = content)
}

/** A recessed well (progress tracks, the ring's bed, selected chips) in the Soft style. */
@Composable
fun Modifier.softWell(radius: Dp, depth: Dp = 3.dp): Modifier {
    if (LocalUiStyle.current != UiStyle.SOFT) return this
    val neu = LocalNeu.current
    return this.neuInset(radius, depth, neu.well, neu.highlight, neu.shadow)
}

/** True when the Soft (neumorphic) style is active. */
val isSoft: Boolean @Composable get() = LocalUiStyle.current == UiStyle.SOFT

