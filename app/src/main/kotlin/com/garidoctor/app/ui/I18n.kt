package com.garidoctor.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.garidoctor.core.health.Bn
import com.garidoctor.core.model.L10n
import com.garidoctor.core.model.Lang

/** UI strings live next to their usage as (English, Bangla) pairs, same as template text. */
val LocalLang = staticCompositionLocalOf { Lang.BN }

@Composable @ReadOnlyComposable
fun t(en: String, bn: String): String = if (LocalLang.current == Lang.BN) bn else en

@Composable @ReadOnlyComposable
fun L10n.tr(): String = get(LocalLang.current)

/** Numbers in the user's script. */
@Composable @ReadOnlyComposable
fun n(v: Number): String = if (LocalLang.current == Lang.BN) Bn.digits("%,d".format(v.toLong())) else "%,d".format(v.toLong())
