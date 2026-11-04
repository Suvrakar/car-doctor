package com.garidoctor.core.ai

import com.garidoctor.core.model.DocumentType
import com.garidoctor.core.symptom.WarningLights
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * Prompts for the cloud fallback (Gemini via Firebase AI Logic). Each asks for one small JSON
 * object; answers are parsed by [AiJson], which tolerates code fences and extra text.
 * Every prompt can be replaced from Remote Config (`ai_prompt_<name>`) without an app update.
 */
object AiPrompts {
    val paper = """
        You read Bangladeshi vehicle papers (Bangla or English). Look at the photo and reply with ONLY this JSON:
        {"type": one of ${DocumentType.entries.joinToString(",", "[", "]") { "\"${it.name}\"" }} or null,
         "number": the certificate/policy/licence/registration number as printed, or null,
         "expiry": the date this paper stops being valid as "YYYY-MM-DD", or null}
        Rules: expiry is the "valid till / মেয়াদ / to" date, never the issue date. Do not guess; use null when unsure.
    """.trimIndent()

    fun receipt(partIds: Collection<String>) = """
        You read garage bills and parts receipts from Bangladesh (Bangla, English or mixed, often handwritten).
        Reply with ONLY this JSON:
        {"items":[{"label": text as written, "partId": one of ${partIds.joinToString(",", "[", "]") { "\"$it\"" }} or null, "amount": taka as integer or null}],
         "total": taka integer or null, "date": "YYYY-MM-DD" or null, "odometerKm": integer or null, "garage": shop name or null}
        Map each line to the closest partId only when it clearly matches. Do not invent lines.
    """.trimIndent()

    fun voice(partIds: Collection<String>, transcript: String) = """
        A vehicle owner in Bangladesh said (Bangla or English): "$transcript"
        Reply with ONLY one JSON object, one of:
        {"kind":"service","partIds":[subset of ${partIds.joinToString(",", "[", "]") { "\"$it\"" }}],"cost":int|null,"km":int|null}
        {"kind":"fuel","amount":number|null,"cost":int|null,"km":int|null,"cng":true|false}
        {"kind":"odometer","km":int}
        {"kind":"unknown"}
    """.trimIndent()

    /** Text-only variants: used with on-device Gemini Nano on the OCR text, before any photo leaves the phone. */
    fun paperFromText(ocr: String) = paper.replace("Look at the photo", "Read this OCR text from the paper:\n<<<\n${ocr.take(3000)}\n>>>\n")
    fun receiptFromText(partIds: Collection<String>, ocr: String) = receipt(partIds) + "\nOCR text of the bill:\n<<<\n${ocr.take(3000)}\n>>>"

    val warningLight = """
        Identify the dashboard warning light in the photo. Reply with ONLY this JSON:
        {"id": one of ${WarningLights.all.joinToString(",", "[", "]") { "\"${it.id}\"" }} or null, "confidence": 0..1}
    """.trimIndent()
}

object AiJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Extracts the first {...} object from a model reply (handles ```json fences and chatter). */
    fun obj(reply: String): JsonObject? {
        val start = reply.indexOf('{'); val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(reply.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    private fun JsonObject.str(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.int(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.doubleOrNull?.toInt() }.getOrNull() }
    private fun JsonObject.dbl(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }
    private fun day(s: String?) = s?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }

    fun paper(reply: String): ScannedDocument? {
        val o = obj(reply) ?: return null
        val type = o.str("type")?.let { t -> DocumentType.entries.firstOrNull { it.name == t } }
        val exp = day(o.str("expiry"))
        val doc = ScannedDocument(type, o.str("number"), exp, if (type != null && exp != null) 0.9 else 0.5, "ai")
        return doc.takeIf { it.type != null || it.expiryEpochDay != null || it.number != null }
    }

    fun receipt(reply: String, available: Set<String>): ScannedReceipt? {
        val o = obj(reply) ?: return null
        val items = runCatching { o["items"]!!.jsonArray }.getOrNull().orEmpty().mapNotNull { e: JsonElement ->
            val io = runCatching { e.jsonObject }.getOrNull() ?: return@mapNotNull null
            val label = io.str("label") ?: return@mapNotNull null
            ReceiptItem(label, io.str("partId")?.takeIf { it in available }, io.int("amount"))
        }
        if (items.isEmpty() && o.int("total") == null) return null
        return ScannedReceipt(items, o.int("total"), day(o.str("date")), o.int("odometerKm"), o.str("garage"), 0.85, "ai")
    }

    fun voice(reply: String, available: Set<String>): VoiceAction {
        val o = obj(reply) ?: return VoiceAction.Unknown
        return when (o.str("kind")) {
            "service" -> {
                val ids = runCatching { o["partIds"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty().filter { it in available }
                if (ids.isEmpty()) VoiceAction.Unknown else VoiceAction.Service(ids, o.int("cost"), o.int("km"))
            }
            "fuel" -> VoiceAction.Fuel(o.dbl("amount"), o.int("cost"), o.int("km"), o["cng"]?.jsonPrimitive?.contentOrNull == "true")
            "odometer" -> o.int("km")?.let { VoiceAction.Odometer(it) } ?: VoiceAction.Unknown
            else -> VoiceAction.Unknown
        }
    }

    fun warningLight(reply: String): Pair<String, Double>? {
        val o = obj(reply) ?: return null
        val id = o.str("id")?.takeIf { WarningLights.byId(it) != null } ?: return null
        return id to (o.dbl("confidence") ?: 0.5)
    }
}

/**
 * Daily cloud-AI allowance per user. The app checks this before a request; Firestore security
 * rules enforce a hard ceiling server-side (see firestore.rules), and App Check blocks modified apps.
 */
data class AiQuota(val used: Int, val bonus: Int, val limit: Int) {
    val remaining: Int get() = (limit + bonus - used).coerceAtLeast(0)
    val exhausted: Boolean get() = remaining == 0

    companion object {
        /** Firestore rules' absolute cap per user per day, whatever the plan says. */
        const val HARD_CAP = 40
        const val MAX_AD_BONUS = 3
        fun limitFor(pro: Boolean, freeLimit: Int, proLimit: Int) = (if (pro) proLimit else freeLimit).coerceIn(0, HARD_CAP)
        /** Usage documents are keyed by UTC day so the rules can check "today" from request.time. */
        fun utcDay(nowMillis: Long): Long = Math.floorDiv(nowMillis, 86_400_000L)
    }
}
