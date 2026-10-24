package com.simpleandroid.ericardscollector.data

import java.io.File

val H_CATEGORIES = mapOf("2" to "Physical", "3" to "Health", "4" to "Environmental")
val P_CATEGORIES = mapOf("1" to "General", "2" to "Prevention", "3" to "Response", "4" to "Storage", "5" to "Disposal")
const val EUH_CATEGORY = "Supplemental"

private val LANGUAGE_CODE_PATTERN = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$")

/** The sections of the reference file that hold entries keyed by code or term. */
val REFERENCE_SECTIONS = listOf("glossary", "pictograms", "hazard_statements", "supplemental_statements", "precautionary_statements")

private val STATEMENT_SECTIONS = mapOf("H" to "hazard_statements", "EUH" to "supplemental_statements", "P" to "precautionary_statements")

data class GlossaryRow(val term: String, val title: String, val text: String, val usedLanguage: String, val order: Double)

data class StatementRow(val code: String, val kind: String, val category: String, val text: String, val pictograms: List<String>)

/** A reference text import that has been calculated but not applied yet. */
class ReferencePlan(
    val result: Map<String, Any?>,
    val stats: ImportStats,
    val skipped: Int,
    val problems: List<String>,
)

/**
 * Glossary, pictogram names and H/EUH/P statements in several languages. The texts ship with the
 * app (assets/reference_texts.json); a file can replace or extend them later (Settings > Update
 * Reference Texts), because the texts may change over time.
 */
class ReferenceTexts(
    private val files: AppFiles,
    private val settings: SettingsStore,
    private val bundledJson: () -> String,
) {
    var data: Map<String, Any?> = emptyMap()
        private set
    var loadWarning: String? = null
        private set

    init {
        load()
    }

    // --- Loading ---
    fun load() {
        val bundled = bundledData()
        if (!files.referenceFile.exists()) {
            saveJson(files.referenceFile, bundled)
            settings.set(SettingsStore.BUNDLED_REFERENCE_VERSION, versionOf(bundled))
        }
        val (loaded, warning) = loadJson(files.referenceFile, emptyMap<String, Any?>(), files)
        loadWarning = warning
        data = asStringMap(loaded)

        // A newer app version can ship updated texts: merge them in (the built-in entry wins per code)
        val bundledVersion = versionOf(bundled)
        if (warning == null && bundledVersion > settings.getInt(SettingsStore.BUNDLED_REFERENCE_VERSION)) {
            backup()
            data = mergeData(data, bundled)
            saveJson(files.referenceFile, data)
            settings.set(SettingsStore.BUNDLED_REFERENCE_VERSION, bundledVersion)
        }
    }

    private fun bundledData(): Map<String, Any?> = asStringMap(Json.parse(bundledJson()))

    private fun versionOf(map: Map<String, Any?>): Int = (map["defaults_version"] as? Number)?.toInt() ?: 0

    fun backup(): File? = backupFile(files.referenceFile, files, "reference_texts")

    private fun entries(section: String): Map<String, Map<String, Any?>> {
        val raw = data[section] as? Map<*, *> ?: return emptyMap()
        val result = LinkedHashMap<String, Map<String, Any?>>()
        for ((key, value) in raw) if (value is Map<*, *>) result[key.toString()] = asStringMap(value)
        return result
    }

    // --- Languages ---
    fun languages(): Map<String, String> {
        val names = LinkedHashMap<String, String>()
        (data["languages"] as? Map<*, *>)?.forEach { (code, name) -> names[code.toString()] = pyStr(name) }
        for (section in REFERENCE_SECTIONS) {
            for (entry in entries(section).values) {
                for (field in listOf("title", "text", "name")) {
                    val values = entry[field]
                    if (values is Map<*, *>) {
                        for (code in values.keys) if (LANGUAGE_CODE_PATTERN.matches(code.toString())) names.putIfAbsent(code.toString(), code.toString())
                    }
                }
            }
        }
        val ordered = names.keys.sortedWith(compareBy({ it != DEFAULT_LANGUAGE }, { it }))
        return ordered.associateWithTo(LinkedHashMap()) { names.getValue(it) }
    }

    /** Returns (text, language actually used). Falls back to English, then to any language. */
    fun pick(values: Any?, language: String): Pair<String, String> {
        if (values is String) return values to language
        if (values !is Map<*, *>) return "" to language
        for (code in listOf(language, DEFAULT_LANGUAGE)) {
            val text = values[code]
            if (text is String && text.isNotBlank()) return text to code
        }
        for ((code, text) in values) {
            if (text is String && text.isNotBlank()) return text to code.toString()
        }
        return "" to language
    }

    // --- Glossary ---
    fun glossary(language: String): List<GlossaryRow> {
        val rows = entries("glossary").map { (term, entry) ->
            val title = pick(entry["title"], language).first
            val (text, usedLanguage) = pick(entry["text"], language)
            val order = (entry["order"] as? Number)?.toDouble() ?: 1000.0
            GlossaryRow(term, title, text, usedLanguage, order)
        }
        return rows.sortedWith(compareBy({ it.order }, { it.term.lowercase() }))
    }

    fun glossaryEntry(term: String, language: String): GlossaryRow? = glossary(language).firstOrNull { it.term == term }

    // --- Statements ---
    fun statements(kind: String, language: String): List<StatementRow> {
        val categories = if (kind == "H") H_CATEGORIES else P_CATEGORIES
        val rows = entries(STATEMENT_SECTIONS.getValue(kind)).map { (code, entry) ->
            val digit = Regex("^[A-Za-z]*?(\\d)").find(code)?.groupValues?.get(1)
            StatementRow(
                code = code,
                kind = kind,
                category = if (kind == "EUH") EUH_CATEGORY else if (digit != null) categories[digit] ?: "Other" else "Other",
                text = pick(entry["text"], language).first,
                pictograms = toStringList(entry["pictograms"]),
            )
        }
        return rows.sortedBy { naturalSortKey(it.code) }
    }

    fun pictogramName(code: String, language: String): String {
        val entry = entries("pictograms")[code] ?: emptyMap()
        return pick(entry["name"], language).first
    }

    // --- Updating the texts ---
    /** Reads an update file and calculates the result without changing anything. Null result: nothing usable. */
    fun plan(update: Any?, replace: Boolean): ReferencePlan? {
        if (update !is Map<*, *>) return null
        val incoming = asStringMap(update)
        var skipped = 0
        val problems = ArrayList<String>()
        val cleaned = LinkedHashMap<String, Any?>()
        for ((key, value) in incoming) {
            if (key in REFERENCE_SECTIONS) {
                if (value !is Map<*, *>) {
                    problems.add("'$key' is not an object and was ignored")
                    continue
                }
                val valid = LinkedHashMap<String, Any?>()
                for ((code, entry) in value) {
                    if (entry is Map<*, *>) valid[code.toString()] = entry else { skipped++; problems.add("$key: '$code' is not an object") }
                }
                cleaned[key] = valid
            } else {
                cleaned[key] = value
            }
        }
        if (REFERENCE_SECTIONS.none { (cleaned[it] as? Map<*, *>)?.isNotEmpty() == true }) return null

        val current = data
        val result: Map<String, Any?> = if (replace) cleaned else mergeData(current, cleaned)

        var added = 0
        var updated = 0
        var unchanged = 0
        var removed = 0
        for (section in REFERENCE_SECTIONS) {
            val before = current[section] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val after = result[section] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            for ((code, entry) in after) {
                when {
                    code !in before -> added++
                    before[code] != entry -> updated++
                    else -> unchanged++
                }
            }
            removed += before.keys.count { it !in after }
        }
        return ReferencePlan(result, ImportStats(added, updated, unchanged, removed), skipped, problems)
    }

    fun apply(plan: ReferencePlan) {
        data = plan.result
        saveJson(files.referenceFile, data)
    }

    /** Back to the texts that ship with the app. */
    fun resetToBundled() {
        val bundled = bundledData()
        data = bundled
        saveJson(files.referenceFile, data)
        settings.set(SettingsStore.BUNDLED_REFERENCE_VERSION, versionOf(bundled))
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun asStringMap(value: Any?): Map<String, Any?> =
            if (value is Map<*, *>) value.entries.associateTo(LinkedHashMap()) { it.key.toString() to it.value } else emptyMap()

        /** Adds [incoming] to [base]: entries are combined per code, language texts per language. */
        fun mergeData(base: Map<String, Any?>, incoming: Map<String, Any?>): Map<String, Any?> {
            val result = LinkedHashMap(base)
            for ((key, value) in incoming) {
                val existing = result[key]
                result[key] = when {
                    key == "languages" && existing is Map<*, *> && value is Map<*, *> -> asStringMap(existing) + asStringMap(value)
                    key in REFERENCE_SECTIONS && existing is Map<*, *> && value is Map<*, *> -> mergeSection(existing, value)
                    else -> value
                }
            }
            return result
        }

        private fun mergeSection(existing: Map<*, *>, incoming: Map<*, *>): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            for ((code, entry) in existing) result[code.toString()] = entry
            for ((code, entry) in incoming) {
                val old = result[code.toString()]
                result[code.toString()] = if (old is Map<*, *> && entry is Map<*, *>) mergeEntry(old, entry) else entry
            }
            return result
        }

        private fun mergeEntry(old: Map<*, *>, incoming: Map<*, *>): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            for ((field, value) in old) result[field.toString()] = value
            for ((field, value) in incoming) {
                val existing = result[field.toString()]
                result[field.toString()] =
                    if (existing is Map<*, *> && value is Map<*, *>) asStringMap(existing) + asStringMap(value) else value
            }
            return result
        }
    }
}
