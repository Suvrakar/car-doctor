package com.garidoctor.core.symptom

import com.garidoctor.core.model.L10n
import kotlinx.serialization.Serializable

/** Traffic-light urgency shown with every diagnosis. */
@Serializable
enum class Urgency(val label: L10n) {
    SAFE(L10n("Safe to ride — keep an eye on it", "চালানো নিরাপদ — নজর রাখুন")),
    THIS_WEEK(L10n("Fix this week", "এই সপ্তাহে ঠিক করুন")),
    STOP_NOW(L10n("Stop now — do not ride/drive", "এখনই থামুন — চালাবেন না")),
}

@Serializable
data class Cause(val text: L10n, val likelihood: Int = 50)

@Serializable
data class Diagnosis(
    val causes: List<Cause>,
    val urgency: Urgency,
    val costMinBdt: Int,
    val costMaxBdt: Int,
    val advice: L10n,
    /** Part the health score should flag while this symptom is unresolved. */
    val partId: String? = null,
    /** Garage category to suggest ("bike_mechanic", "car_workshop", "battery_shop", "cng_kit"). */
    val garageType: String? = null,
)

@Serializable
data class Option(val label: L10n, val next: String? = null, val diagnosis: Diagnosis? = null)

@Serializable
data class Node(val id: String, val question: L10n, val options: List<Option>)

/** A start symptom ("White smoke from exhaust") and the node it begins at. */
@Serializable
data class Entry(val id: String, val label: L10n, val start: String)

/**
 * Rule-based decision tree per vehicle type. Every leaf is a [Diagnosis] phrased as
 * "possible causes" with urgency + cost, never a definitive verdict.
 */
@Serializable
data class SymptomTree(val id: String, val entries: List<Entry>, val nodes: List<Node>) {
    fun node(id: String): Node? = nodes.firstOrNull { it.id == id }

    fun validate(): List<String> {
        val ids = nodes.map { it.id }.toSet()
        val issues = mutableListOf<String>()
        if (ids.size != nodes.size) issues += "duplicate node ids in $id"
        entries.forEach { if (it.start !in ids) issues += "entry ${it.id} → missing node ${it.start}" }
        nodes.forEach { n ->
            if (n.options.isEmpty()) issues += "node ${n.id} has no options"
            n.options.forEach { o ->
                if ((o.next == null) == (o.diagnosis == null)) issues += "option '${o.label.en}' in ${n.id} must have exactly one of next/diagnosis"
                if (o.next != null && o.next !in ids) issues += "option in ${n.id} → missing node ${o.next}"
                o.diagnosis?.let { d -> if (d.costMinBdt > d.costMaxBdt) issues += "cost range inverted in ${n.id}" }
            }
        }
        // cycle detection
        fun dfs(nid: String, stack: Set<String>) {
            if (nid in stack) { issues += "cycle at $nid"; return }
            node(nid)?.options?.mapNotNull { it.next }?.forEach { dfs(it, stack + nid) }
        }
        entries.forEach { dfs(it.start, emptySet()) }
        return issues.distinct()
    }
}

/** Walks a tree one answer at a time; keeps history so the UI can go back. */
class SymptomSession(private val tree: SymptomTree, entryId: String) {
    private val path = ArrayDeque<Node>()
    var diagnosis: Diagnosis? = null
        private set

    init {
        val e = tree.entries.first { it.id == entryId }
        path.addLast(tree.node(e.start)!!)
    }

    val current: Node? get() = if (diagnosis == null) path.lastOrNull() else null
    val depth: Int get() = path.size

    fun answer(optionIndex: Int) {
        val n = current ?: return
        val o = n.options[optionIndex]
        if (o.diagnosis != null) diagnosis = o.diagnosis
        else path.addLast(tree.node(o.next!!)!!)
    }

    fun back(): Boolean {
        if (diagnosis != null) { diagnosis = null; return true }
        if (path.size <= 1) return false
        path.removeLast(); return true
    }

    companion object {
        val DISCLAIMER = L10n(
            "These are possible causes, not a final diagnosis. Please confirm with a mechanic.",
            "এগুলো সম্ভাব্য কারণ, চূড়ান্ত রোগনির্ণয় নয়। অবশ্যই একজন মেকানিক দিয়ে নিশ্চিত হয়ে নিন।",
        )
    }
}
