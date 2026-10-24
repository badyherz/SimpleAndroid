package com.simpleandroid.ericardscollector.data

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class CollectionStats(val added: Int, val changed: Int, val unchanged: Int, val removed: Int)

/** One kind of collected items: key -> ISO date. */
class CollectionSection(private val normalizeKey: (String) -> String) {
    var entries: Map<String, String> = emptyMap()

    fun load(raw: Any?) {
        val loaded = LinkedHashMap<String, String>()
        if (raw is Map<*, *>) {
            for ((key, entry) in raw) {
                if (entry is Map<*, *>) loaded[normalizeKey(key.toString())] = pyStr(entry["date"] ?: "")
            }
        }
        entries = loaded
    }

    fun isChecked(key: String) = normalizeKey(key) in entries

    fun getDate(key: String): String = entries[normalizeKey(key)] ?: ""

    /** Returns (entries, number of skipped entries, problems). */
    fun parseImport(raw: Map<*, *>): Triple<Map<String, String>, Int, List<String>> {
        val parsed = LinkedHashMap<String, String>()
        var skipped = 0
        val problems = ArrayList<String>()
        for ((key, entry) in raw) {
            val date = if (entry is Map<*, *>) parseIsoDate(entry["date"] ?: "") else null
            if (date == null) {
                skipped++
                problems.add("$key: missing or invalid date")
                continue
            }
            parsed[normalizeKey(key.toString())] = date.toString()
        }
        return Triple(parsed, skipped, problems)
    }

    /** Returns (resulting entries, statistics) without changing anything yet. */
    fun mergePlan(imported: Map<String, String>, mode: ImportMode): Pair<Map<String, String>, CollectionStats> {
        val result = LinkedHashMap<String, String>()
        if (mode == ImportMode.REPLACE) {
            result.putAll(imported)
        } else {
            result.putAll(entries)
            for ((key, date) in imported) {
                val current = result[key]
                if (current == null || mode == ImportMode.USE_IMPORTED || current.isEmpty() || date < current) result[key] = date
            }
        }
        val added = result.keys.count { it !in entries }
        val removed = entries.keys.count { it !in result }
        val changed = result.count { (key, date) -> key in entries && entries[key] != date }
        val unchanged = result.keys.count { it in entries } - changed
        return result to CollectionStats(added, changed, unchanged, removed)
    }

    fun toJson(): Map<String, Any?> = entries.mapValues { mapOf("date" to it.value) }
}

/** Imported sections: Kind -> entries, or null if the file has no such section. */
data class CollectionImport(
    val sections: Map<Kind, Map<String, String>?>,
    val skipped: Int,
    val problems: List<String>,
)

class CollectionPlan(
    val results: Map<Kind, Map<String, String>>,
    val stats: Map<Kind, CollectionStats>,
)

class CollectionStore(private val files: AppFiles) {
    val ericards = CollectionSection(::cardKey)
    val substances = CollectionSection(::substanceKey)
    var loadWarning: String? = null
        private set

    init {
        load()
    }

    fun section(kind: Kind) = if (kind == Kind.SUBSTANCES) substances else ericards

    // --- Loading and Saving ---
    fun load() {
        val (raw, warning) = loadJson(files.collectionFile, emptyMap<String, Any?>(), files)
        loadWarning = warning
        val data = raw as? Map<*, *> ?: emptyMap<String, Any?>()
        ericards.load(data[FIELD_ERICARDS])
        substances.load(data[FIELD_SUBSTANCES])
    }

    fun save() {
        saveJson(
            files.collectionFile,
            linkedMapOf(
                "schema_version" to SCHEMA_VERSION,
                FIELD_ERICARDS to ericards.toJson(),
                FIELD_SUBSTANCES to substances.toJson(),
            ),
        )
    }

    fun backup(): File? = backupFile(files.collectionFile, files, "collection")

    // --- Changes ---
    fun setEntry(kind: Kind, key: String, isoDate: String) {
        val section = section(kind)
        section.entries = LinkedHashMap(section.entries).also { it[normalizeFor(kind, key)] = isoDate }
        save()
    }

    fun removeEntry(kind: Kind, key: String) {
        val section = section(kind)
        section.entries = LinkedHashMap(section.entries).also { it.remove(normalizeFor(kind, key)) }
        save()
    }

    private fun normalizeFor(kind: Kind, key: String) = if (kind == Kind.SUBSTANCES) substanceKey(key) else cardKey(key)

    // --- ERICard shortcuts ---
    fun isChecked(number: String) = ericards.isChecked(number)
    fun getDate(number: String) = ericards.getDate(number)

    // --- Export and Import ---
    fun exportData(): Map<String, Any?> = linkedMapOf(
        "schema_version" to SCHEMA_VERSION,
        "exported_at" to LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        FIELD_ERICARDS to ericards.toJson(),
        FIELD_SUBSTANCES to substances.toJson(),
    )

    /**
     * A section missing from the file (for example in an export from an older version) stays null,
     * so it is left untouched even when the whole collection is replaced.
     */
    fun parseImport(data: Any?): CollectionImport {
        if (data !is Map<*, *>) return CollectionImport(emptyMap(), 0, listOf("The file is not a collection export."))
        val imported = LinkedHashMap<Kind, Map<String, String>?>()
        var skipped = 0
        val problems = ArrayList<String>()
        for ((kind, field) in FIELDS) {
            val raw = data[field]
            if (raw == null) {
                imported[kind] = null
            } else if (raw !is Map<*, *>) {
                imported[kind] = null
                problems.add("'$field' is not an object and was ignored")
            } else {
                val (entries, bad, sectionProblems) = section(kind).parseImport(raw)
                imported[kind] = entries
                skipped += bad
                problems += sectionProblems.map { "$field: $it" }
            }
        }
        return CollectionImport(imported, skipped, problems)
    }

    fun mergePlan(imported: CollectionImport, mode: ImportMode): CollectionPlan {
        val results = LinkedHashMap<Kind, Map<String, String>>()
        val stats = LinkedHashMap<Kind, CollectionStats>()
        for ((kind, entries) in imported.sections) {
            if (entries != null) {
                val (result, kindStats) = section(kind).mergePlan(entries, mode)
                results[kind] = result
                stats[kind] = kindStats
            }
        }
        return CollectionPlan(results, stats)
    }

    fun apply(plan: CollectionPlan) {
        for ((kind, entries) in plan.results) section(kind).entries = entries
        save()
    }

    companion object {
        const val FIELD_ERICARDS = "checked"
        const val FIELD_SUBSTANCES = "substances_checked"
        val FIELDS = linkedMapOf(Kind.ERICARDS to FIELD_ERICARDS, Kind.SUBSTANCES to FIELD_SUBSTANCES)
    }
}
