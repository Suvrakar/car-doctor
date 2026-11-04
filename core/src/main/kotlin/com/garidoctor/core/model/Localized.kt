package com.garidoctor.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** Supported UI languages. [code] is the BCP-47 tag used for Android locales and catalog files. */
enum class Lang(val code: String, val nativeName: String, val englishName: String, val rtl: Boolean = false, val speechTag: String) {
    EN("en", "English", "English", speechTag = "en-US"),
    BN("bn", "বাংলা", "Bengali", speechTag = "bn-BD"),
    HI("hi", "हिन्दी", "Hindi", speechTag = "hi-IN"),
    UR("ur", "اردو", "Urdu", rtl = true, speechTag = "ur-PK"),
    NE("ne", "नेपाली", "Nepali", speechTag = "ne-NP"),
    AR("ar", "العربية", "Arabic", rtl = true, speechTag = "ar-SA"),
    ZH("zh", "中文（简体）", "Chinese (Simplified)", speechTag = "zh-CN"),
    JA("ja", "日本語", "Japanese", speechTag = "ja-JP"),
    RU("ru", "Русский", "Russian", speechTag = "ru-RU"),
    DE("de", "Deutsch", "German", speechTag = "de-DE"),
    IT("it", "Italiano", "Italian", speechTag = "it-IT");

    /**
     * Picks the text for this language. English and Bangla are written inline at each call
     * site; every other language is looked up in its catalog by the English text.
     */
    fun pick(en: String, bn: String): String = when (this) {
        EN -> en
        BN -> bn
        else -> Translations.tr(this, en)
    }

    companion object {
        fun of(code: String?): Lang? = entries.firstOrNull { it.code == code?.lowercase()?.substringBefore('-') }
    }
}

/** A string available in English and Bangla; other languages come from the catalogs. */
@Serializable
data class L10n(val en: String, val bn: String) {
    fun get(lang: Lang): String = lang.pick(en, bn)
}

/**
 * Translation catalogs: one JSON object per language (`i18n/<code>.json`), mapping the English
 * text to the translation. Keys may contain placeholders `{0}`, `{1}`… for values that change
 * (names, numbers): "Expires in {0} days" matches "Expires in 9 days" and the value is carried
 * into the translation. Missing entries fall back to English, so a partial catalog is safe.
 */
object Translations {
    /** Where catalogs come from; the default reads bundled resources. Remote overrides can replace it. */
    @Volatile var loader: (String) -> String? = { path ->
        Translations::class.java.classLoader?.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }

    private class Catalog(val exact: Map<String, String>, val patterns: List<Pair<Regex, String>>) {
        val memo = ConcurrentHashMap<String, String>()
    }

    private val catalogs = ConcurrentHashMap<Lang, Catalog>()
    private val placeholder = Regex("""\{(\d+)\}""")

    private fun load(lang: Lang): Catalog = catalogs.getOrPut(lang) {
        val text = runCatching { loader("i18n/${lang.code}.json") }.getOrNull()
        val map: Map<String, String> = text?.let { t ->
            runCatching {
                Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), t)
            }.getOrNull()
        } ?: emptyMap()
        val patterns = map.filterKeys { placeholder.containsMatchIn(it) }.map { (k, v) ->
            val rx = buildString {
                append('^'); var last = 0
                placeholder.findAll(k).forEach { m -> append(Regex.escape(k.substring(last, m.range.first))); append("(.+?)"); last = m.range.last + 1 }
                append(Regex.escape(k.substring(last))); append('$')
            }
            Regex(rx, RegexOption.DOT_MATCHES_ALL) to v
        }
        Catalog(map, patterns)
    }

    /** Translation of an already-formatted English string, or the English itself. */
    fun tr(lang: Lang, en: String): String {
        if (lang == Lang.EN || en.isEmpty()) return en
        val c = load(lang)
        c.exact[en]?.let { return it }
        return c.memo.getOrPut(en) {
            // Which placeholder index sits in which capture group, per pattern.
            c.patterns.firstNotNullOfOrNull { (rx, tpl) ->
                rx.matchEntire(en)?.let { m ->
                    placeholder.replace(tpl) { ph -> m.groupValues.getOrNull(ph.groupValues[1].toInt() + 1) ?: ph.value }
                }
            } ?: en
        }
    }

    /** Formats a catalog template directly: format(lang, "Your {0} is at {1}/100.", name, score). */
    fun format(lang: Lang, template: String, vararg args: Any): String {
        val t = if (lang == Lang.EN) template else load(lang).exact[template] ?: template
        return placeholder.replace(t) { ph -> args.getOrNull(ph.groupValues[1].toInt())?.toString() ?: ph.value }
    }

    /** Clears loaded catalogs (after remote updates, or in tests). */
    fun reset() = catalogs.clear()
}
