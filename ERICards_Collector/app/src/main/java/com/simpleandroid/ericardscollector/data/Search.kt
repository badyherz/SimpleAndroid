package com.simpleandroid.ericardscollector.data

private val UN_PREFIX = Regex("^un\\s*")
private val CLASS_PREFIX = Regex("^(adr\\s*)?(class\\s*)?")

private fun tokens(query: String) = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

private fun classMatches(adrClasses: List<String>, query: String): Boolean {
    val wanted = query.replaceFirst(CLASS_PREFIX, "").trim()
    return adrClasses.any { it.lowercase() == wanted || it.lowercase().startsWith("$wanted.") }
}

fun cardMatches(card: Card, rawQuery: String, category: String): Boolean {
    val query = rawQuery.trim().lowercase()
    if (query.isEmpty()) return true

    when (category) {
        "UN Number" -> {
            val wanted = query.replaceFirst(UN_PREFIX, "")
            return card.unNumbers.any { wanted in it.lowercase() }
        }
        "Substance Name" -> {
            val names = card.substanceNames.joinToString(" ").lowercase()
            return tokens(query).all { it in names }
        }
        "ADR Class" -> return classMatches(card.adrClasses, query)
        "Hazard Number" -> {
            val wanted = query.replace(" ", "")
            return card.hazardNumbers.any { wanted in it.lowercase() }
        }
        "ERICard Number" -> return compact(query) in compact(card.number)
    }

    // --- All Fields ---
    val haystack = (
        listOf(card.number, compact(card.number), card.description) + card.unNumbers + card.substanceNames +
            card.adrClasses + card.hazardNumbers + card.knownHazards
        ).joinToString(" ").lowercase()
    return tokens(query).all { it in haystack }
}

fun substanceMatches(record: SubstanceRecord, rawQuery: String, category: String): Boolean {
    val query = rawQuery.trim().lowercase()
    if (query.isEmpty()) return true

    when (category) {
        "UN Number" -> return query.replaceFirst(UN_PREFIX, "") in record.un.lowercase()
        "Substance Name" -> {
            val names = record.names.joinToString(" ").lowercase()
            return tokens(query).all { it in names }
        }
        "ADR Class" -> return classMatches(record.adrClasses, query)
        "Hazard Number" -> {
            val wanted = query.replace(" ", "")
            return record.hazardNumbers.any { wanted in it.lowercase() }
        }
        "ERICard Number" -> return record.cards.any { compact(query) in compact(it) }
    }

    // --- All Fields ---
    return tokens(query).all { it in record.searchText }
}

fun statementMatches(statement: StatementRow, query: String): Boolean {
    val words = tokens(query)
    if (words.isEmpty()) return true
    val haystack = (listOf(statement.code, statement.text, statement.category) + statement.pictograms).joinToString(" ").lowercase()
    return words.all { it in haystack }
}

// --- Statistics ---
data class ClassStat(val adrClass: String, val collected: Int, val total: Int, val percent: Double)

data class Stats(val collected: Int, val total: Int, val percent: Double, val classes: List<ClassStat>)

fun buildStats(items: List<Pair<String, List<String>>>, isCollected: (String) -> Boolean): Stats {
    var total = 0
    var collected = 0
    val perClass = LinkedHashMap<String, IntArray>()
    for ((key, adrClasses) in items) {
        total++
        val collectedHere = isCollected(key)
        if (collectedHere) collected++
        for (adrClass in adrClasses.toSet()) {
            val counts = perClass.getOrPut(adrClass) { IntArray(2) }
            counts[1]++
            if (collectedHere) counts[0]++
        }
    }
    val rows = perClass.entries.sortedBy { naturalSortKey(it.key) }
        .map { (adrClass, counts) -> ClassStat(adrClass, counts[0], counts[1], calculatePercent(counts[0], counts[1])) }
    return Stats(collected, total, calculatePercent(collected, total), rows)
}
