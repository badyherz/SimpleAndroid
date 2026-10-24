package com.simpleandroid.ericardscollector.data

import java.io.File

data class ImportStats(val added: Int, val updated: Int, val unchanged: Int, val removed: Int)

/** Result of reading a list of cards: valid cards without duplicates, number of skipped entries, problems. */
data class ParsedCards(val cards: List<Card>, val skipped: Int, val problems: List<String>)

/** A database import that has been calculated but not applied yet. */
class DatabasePlan(
    val result: Map<String, Card>,
    val stats: ImportStats,
    val meta: Map<String, Any?>,
    val replace: Boolean,
    val parsed: ParsedCards,
)

/**
 * The ERICards database. There is no built-in data: the database is whatever was loaded from a
 * JSON file (Settings > Update Database).
 */
class CardDatabase(private val files: AppFiles) {
    var cards: Map<String, Card> = emptyMap()
        private set
    /** Top-level fields of the file (source, language, ...) are kept on save. */
    var meta: Map<String, Any?> = emptyMap()
        private set
    var lastUpdated: String = ""
        private set
    var loadWarning: String? = null
        private set

    private var sortedCardList: List<Card>? = null
    private var substanceIndex: Map<String, SubstanceRecord>? = null
    private var sortedSubstanceList: List<SubstanceRecord>? = null

    init {
        load()
    }

    val language: String get() = pyStr(meta["language"] ?: "").trim()

    // --- Loading and Saving ---
    fun load() {
        invalidate()
        val (data, warning) = loadJson(files.databaseFile, emptyMap<String, Any?>(), files)
        loadWarning = warning
        meta = extractMeta(data)
        lastUpdated = pyStr(meta["last_updated"] ?: "")
        val rawCards = if (data is Map<*, *>) data["cards"] ?: emptyList<Any?>() else data
        cards = parseCards(rawCards).cards.associateByTo(LinkedHashMap()) { it.key }
    }

    fun save(lastUpdated: String? = null) {
        this.lastUpdated = lastUpdated ?: todayIso()
        val payload = LinkedHashMap<String, Any?>()
        payload["schema_version"] = SCHEMA_VERSION
        payload["last_updated"] = this.lastUpdated
        for ((key, value) in meta) if (key != "schema_version" && key != "last_updated" && key != "cards") payload[key] = value
        payload["cards"] = sortedCards().map { it.raw }
        saveJson(files.databaseFile, payload)
    }

    fun backup(): File? = backupFile(files.databaseFile, files, "ericards_db")

    private fun invalidate() {
        sortedCardList = null
        substanceIndex = null
        sortedSubstanceList = null
    }

    // --- Queries ---
    fun get(key: String): Card? = cards[cardKey(key)]

    fun sortedCards(): List<Card> =
        sortedCardList ?: cards.values.sortedBy { naturalSortKey(it.number) }.also { sortedCardList = it }

    fun substances(): Map<String, SubstanceRecord> {
        substanceIndex?.let { return it }
        val index = buildSubstanceIndex(sortedCards())
        substanceIndex = index
        sortedSubstanceList = index.values.sortedBy { naturalSortKey(it.un) }
        return index
    }

    fun getSubstance(key: String): SubstanceRecord? = substances()[substanceKey(key)]

    fun sortedSubstances(): List<SubstanceRecord> {
        substances()
        return sortedSubstanceList ?: emptyList()
    }

    // --- Importing ---
    fun plan(data: Any?, replace: Boolean): DatabasePlan {
        val parsed = parseImport(data)
        val incoming = parsed.cards.associateBy { it.key }
        val result: Map<String, Card> = if (replace) LinkedHashMap(incoming) else LinkedHashMap(cards).also { it.putAll(incoming) }
        val added = incoming.keys.count { it !in cards }
        val updated = incoming.count { (key, card) -> key in cards && cards[key] != card }
        val removed = cards.keys.count { it !in result }
        val stats = ImportStats(added, updated, incoming.size - added - updated, removed)
        return DatabasePlan(result, stats, extractMeta(data), replace, parsed)
    }

    fun apply(plan: DatabasePlan) {
        cards = plan.result
        invalidate()
        if (plan.replace || meta.isEmpty()) meta = plan.meta.filterKeys { it != "cards" }
        // A replaced database keeps the date it was published with; a merge counts as updated today
        val published = pyStr(meta["last_updated"] ?: "")
        save(if (plan.replace && published.isNotEmpty()) published else null)
    }

    companion object {
        fun extractMeta(data: Any?): Map<String, Any?> =
            if (data is Map<*, *>) data.entries.filter { it.key != "cards" }.associate { it.key.toString() to it.value } else emptyMap()

        fun parseCards(rawCards: Any?): ParsedCards {
            if (rawCards !is List<*>) return ParsedCards(emptyList(), 0, listOf("The file does not contain a list of cards."))
            val unique = LinkedHashMap<String, Card>()
            var skipped = 0
            val problems = ArrayList<String>()
            rawCards.forEachIndexed { position, raw ->
                val index = position + 1
                val card = normalizeCard(raw)
                if (card == null) {
                    skipped++
                    problems.add("Entry $index: skipped (not an object with an 'ericard_number')")
                    return@forEachIndexed
                }
                if (card.key in unique) problems.add("Entry $index: ERICard ${card.number} appears twice (the last one is used)")
                for (warning in validateCard(card)) problems.add("ERICard ${card.number}: $warning")
                unique[card.key] = card
            }
            return ParsedCards(unique.values.toList(), skipped, problems)
        }

        fun parseImport(data: Any?): ParsedCards {
            if (data is Map<*, *>) {
                if (!data.containsKey("cards")) return ParsedCards(emptyList(), 0, listOf("The file has no 'cards' list."))
                return parseCards(data["cards"])
            }
            return parseCards(data)
        }
    }
}
