package com.garidoctor.core.model

import kotlinx.serialization.Serializable

/** Supported UI languages. Every user-facing string in templates carries both. */
enum class Lang { EN, BN }

/** A string available in English and Bangla. */
@Serializable
data class L10n(val en: String, val bn: String) {
    fun get(lang: Lang): String = if (lang == Lang.BN) bn else en
}
