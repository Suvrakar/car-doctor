package com.garidoctor.core.template

import com.garidoctor.core.symptom.SymptomTree
import kotlinx.serialization.json.Json

/** Validation problem found in a template. Templates that fail validation are rejected. */
data class TemplateIssue(val templateId: String, val message: String)

/**
 * Holds all vehicle templates and symptom trees. Bundled JSON is loaded first; remote JSON
 * (Firebase Remote Config) replaces a bundled template only if its version is newer and it validates.
 */
class TemplateRegistry private constructor(
    private val templates: MutableMap<String, VehicleTemplate>,
    private val trees: MutableMap<String, SymptomTree>,
) {
    val isEmpty: Boolean get() = templates.isEmpty()
    val all: List<VehicleTemplate> get() = templates.values.sortedBy { ORDER.indexOf(it.id).let { i -> if (i < 0) 99 else i } }
    fun template(id: String): VehicleTemplate? = templates[id]
    fun tree(id: String?): SymptomTree? = id?.let { trees[it] }
    val allTrees: Collection<SymptomTree> get() = trees.values

    /** Apply a remote template JSON. Returns issues; empty list means it was accepted (or older and ignored). */
    fun applyRemoteTemplate(jsonText: String): List<TemplateIssue> {
        val t = try { json.decodeFromString(VehicleTemplate.serializer(), jsonText) } catch (e: Exception) {
            return listOf(TemplateIssue("?", "parse error: ${e.message}"))
        }
        val issues = validate(t)
        if (issues.isNotEmpty()) return issues
        val current = templates[t.id]
        if (current == null || t.version > current.version) templates[t.id] = t
        return emptyList()
    }

    fun applyRemoteTree(jsonText: String): Boolean = try {
        val tree = json.decodeFromString(SymptomTree.serializer(), jsonText)
        if (tree.validate().isEmpty()) { trees[tree.id] = tree; true } else false
    } catch (_: Exception) { false }

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val ORDER = listOf("motorcycle", "scooter", "car", "cng_auto", "easy_bike", "e_scooter", "suv", "microbus", "bus", "pickup", "truck", "other")
        val BUNDLED_TEMPLATES = ORDER
        val BUNDLED_TREES = listOf("motorcycle", "car", "easy_bike", "cng_auto", "heavy")

        fun validate(t: VehicleTemplate): List<TemplateIssue> {
            val out = mutableListOf<TemplateIssue>()
            if (t.parts.isEmpty()) out += TemplateIssue(t.id, "no parts")
            val ids = t.parts.map { it.id }
            if (ids.size != ids.toSet().size) out += TemplateIssue(t.id, "duplicate part ids")
            t.parts.forEach { p ->
                if (p.weight !in 1..40) out += TemplateIssue(t.id, "part ${p.id} weight ${p.weight} out of 1..40")
                if (p.intervalKm == null && p.intervalMonths == null) out += TemplateIssue(t.id, "part ${p.id} has no interval")
                if (p.checkId != null && t.checks.none { it.id == p.checkId }) out += TemplateIssue(t.id, "part ${p.id} references missing check ${p.checkId}")
            }
            t.models.forEach { m -> m.overrides.keys.filter { it !in ids }.forEach { out += TemplateIssue(t.id, "model ${m.id} overrides unknown part $it") } }
            return out
        }

        /** Load from a resource reader (classpath in tests, Android assets in the app). */
        fun load(read: (String) -> String?): TemplateRegistry {
            val templates = LinkedHashMap<String, VehicleTemplate>()
            BUNDLED_TEMPLATES.forEach { id ->
                val text = read("templates/$id.json") ?: return@forEach
                val t = json.decodeFromString(VehicleTemplate.serializer(), text)
                val issues = validate(t)
                require(issues.isEmpty()) { "Bundled template $id invalid: $issues" }
                templates[t.id] = t
            }
            val trees = LinkedHashMap<String, SymptomTree>()
            BUNDLED_TREES.forEach { id ->
                val text = read("symptoms/$id.json") ?: return@forEach
                val tree = json.decodeFromString(SymptomTree.serializer(), text)
                val issues = tree.validate()
                require(issues.isEmpty()) { "Bundled tree $id invalid: $issues" }
                trees[tree.id] = tree
            }
            return TemplateRegistry(templates, trees)
        }

        /** Placeholder used while the real registry loads in the background. */
        fun empty(): TemplateRegistry = TemplateRegistry(LinkedHashMap(), LinkedHashMap())

        fun fromClasspath(): TemplateRegistry = load { path ->
            TemplateRegistry::class.java.classLoader.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }
    }
}
