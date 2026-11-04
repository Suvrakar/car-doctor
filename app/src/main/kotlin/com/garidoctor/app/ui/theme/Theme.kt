package com.garidoctor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garidoctor.core.health.HealthBand
import com.garidoctor.core.symptom.Urgency

// Material 3 tonal palette generated from the brand seed #0E7C66 (teal-green, "health").
private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58), onPrimary = Color.White,
    primaryContainer = Color(0xFF7FF8D6), onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DE), onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF416277), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5E7FF), onTertiaryContainer = Color(0xFF001E2D),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF7), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF7), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF), onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBFC9C4),
    inverseSurface = Color(0xFF2B322F), inverseOnSurface = Color(0xFFECF2EE), inversePrimary = Color(0xFF5FDBBA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB), surfaceContainerHigh = Color(0xFFE4EAE6), surfaceContainerHighest = Color(0xFFDEE4E0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FDBBA), onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142), onPrimaryContainer = Color(0xFF7FF8D6),
    secondary = Color(0xFFB1CCC2), onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF344C44), onSecondaryContainer = Color(0xFFCDE8DE),
    tertiary = Color(0xFFA9CBE3), onTertiary = Color(0xFF0F3447),
    tertiaryContainer = Color(0xFF284B5E), onTertiaryContainer = Color(0xFFC5E7FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF89938F), outlineVariant = Color(0xFF3F4945),
    inverseSurface = Color(0xFFDEE4E0), inverseOnSurface = Color(0xFF2B322F), inversePrimary = Color(0xFF006B58),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28), surfaceContainerHighest = Color(0xFF303633),
)

/** Semantic status colours (good / warning / bad), each with a container, tuned per theme for contrast. */
@Immutable
data class StatusColors(
    val good: Color, val onGoodContainer: Color, val goodContainer: Color,
    val warn: Color, val onWarnContainer: Color, val warnContainer: Color,
    val bad: Color, val onBadContainer: Color, val badContainer: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF1E7D3A), onGoodContainer = Color(0xFF002109), goodContainer = Color(0xFFB7F1BE),
    warn = Color(0xFF8A5A00), onWarnContainer = Color(0xFF2C1A00), warnContainer = Color(0xFFFFDDB3),
    bad = Color(0xFFBA1A1A), onBadContainer = Color(0xFF410002), badContainer = Color(0xFFFFDAD6),
)
private val DarkStatus = StatusColors(
    good = Color(0xFF7DDB8E), onGoodContainer = Color(0xFFB7F1BE), goodContainer = Color(0xFF00531F),
    warn = Color(0xFFFFB951), onWarnContainer = Color(0xFFFFDDB3), warnContainer = Color(0xFF693F00),
    bad = Color(0xFFFFB4AB), onBadContainer = Color(0xFFFFDAD6), badContainer = Color(0xFF93000A),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val Base = Typography()
/** Slightly heavier headings and roomier body line-height: reads well in both Latin and Bangla script. */
private val AppTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 22.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** 4dp spacing grid. */
object Spacing {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp
    /** Standard screen gutter. */
    val gutter = 16.dp
}

@Composable
fun GariTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object Status {
    val colors: StatusColors @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}

// Kept for older call sites: theme-aware via composable getters.
val Good: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.good
val Warn: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.warn
val Bad: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.bad
val Brand = Color(0xFF006B58)

enum class Tone { GOOD, WARN, BAD, NEUTRAL }

fun HealthBand.tone() = when (this) { HealthBand.EXCELLENT, HealthBand.GOOD -> Tone.GOOD; HealthBand.FAIR -> Tone.WARN; else -> Tone.BAD }
fun Urgency.tone() = when (this) { Urgency.SAFE -> Tone.GOOD; Urgency.THIS_WEEK -> Tone.WARN; Urgency.STOP_NOW -> Tone.BAD }

@Composable @ReadOnlyComposable
fun Tone.color(): Color = when (this) { Tone.GOOD -> Status.colors.good; Tone.WARN -> Status.colors.warn; Tone.BAD -> Status.colors.bad; Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant }
@Composable @ReadOnlyComposable
fun Tone.container(): Color = when (this) { Tone.GOOD -> Status.colors.goodContainer; Tone.WARN -> Status.colors.warnContainer; Tone.BAD -> Status.colors.badContainer; Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh }
@Composable @ReadOnlyComposable
fun Tone.onContainer(): Color = when (this) { Tone.GOOD -> Status.colors.onGoodContainer; Tone.WARN -> Status.colors.onWarnContainer; Tone.BAD -> Status.colors.onBadContainer; Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurface }

@Composable @ReadOnlyComposable
fun bandColor(b: HealthBand) = b.tone().color()
@Composable @ReadOnlyComposable
fun urgencyColor(u: Urgency) = u.tone().color()
