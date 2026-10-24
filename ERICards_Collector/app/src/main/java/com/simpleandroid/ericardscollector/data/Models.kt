package com.simpleandroid.ericardscollector.data

private val LIST_FIELDS = listOf("un_numbers", "substance_names", "adr_classes", "hazard_numbers", "known_hazards", "adr_labels")

/** One row of the substance table of an ERICard: UN number, name and hazard identification number. */
data class SubstanceRow(val un: String, val name: String, val hin: String)

/**
 * An ERICard. [raw] is the normalized JSON object; unknown fields are kept, so saving the database
 * never loses data. Two cards are equal when their JSON content is equal.
 */
class Card(val raw: Map<String, Any?>) {
    val number: String = pyStr(raw["ericard_number"])
    val unNumbers: List<String> = toStringList(raw["un_numbers"])
    val substanceNames: List<String> = toStringList(raw["substance_names"])
    val adrClasses: List<String> = toStringList(raw["adr_classes"])
    val hazardNumbers: List<String> = toStringList(raw["hazard_numbers"])
    val knownHazards: List<String> = toStringList(raw["known_hazards"])
    val adrLabels: List<String> = toStringList(raw["adr_labels"])
    val description: String = pyStrOr(raw["description"]).trim()
    val verified: Boolean = pyBool(raw["verified"] ?: true)
    val key: String = cardKey(number)

    override fun equals(other: Any?) = other is Card && other.raw == raw
    override fun hashCode() = raw.hashCode()

    /** [(UN number, substance name, hazard number)] */
    fun substances(): List<SubstanceRow> {
        val rows = ArrayList<SubstanceRow>()
        val entries = raw["substances"]
        if (entries is List<*>) {
            for (entry in entries) {
                if (entry is Map<*, *>) {
                    rows.add(
                        SubstanceRow(
                            pyStr(entry["un"] ?: "").trim(),
                            pyStr(entry["name"] ?: "").trim(),
                            pyStr(entry["hin"] ?: "").trim(),
                        )
                    )
                }
            }
        }
        if (rows.isNotEmpty()) return rows
        return (0 until maxOf(unNumbers.size, substanceNames.size)).map { index ->
            SubstanceRow(unNumbers.getOrElse(index) { "" }, substanceNames.getOrElse(index) { "" }, "")
        }
    }

    /** [(heading, lines)] in the order stored in the database. */
    fun sections(): List<Pair<String, List<String>>> {
        val sections = raw["sections"] as? Map<*, *> ?: return emptyList()
        return sections.map { (heading, lines) -> pyStr(heading) to toStringList(lines) }
    }

    private fun referencePairs(field: String, keyName: String, valueName: String): Map<String, String> {
        val reference = raw["reference"] as? Map<*, *>
        val entries = reference?.get(field) as? List<*>
        val pairs = LinkedHashMap<String, String>()
        if (entries != null) {
            for (entry in entries) {
                if (entry is Map<*, *> && pyBool(entry[keyName]) && pyBool(entry[valueName])) {
                    pairs.getOrPut(pyStr(entry[keyName])) { pyStr(entry[valueName]) }
                }
            }
        }
        return pairs
    }

    /** ADR class -> name, for example "2.1" -> "Flammable gases". */
    fun classNames() = referencePairs("classes", "adr_class", "name")

    /** Hazard number -> meaning, for example "23" -> "Flammable gas". */
    fun hazardMeanings() = referencePairs("hazard_numbers", "hin", "meaning")

    fun sourceUrl(): String {
        val url = pyStrOr(raw["source_url"]).trim()
        return if (url.startsWith("http://") || url.startsWith("https://")) url else ""
    }
}

/** Returns null if the entry is not an object with an ericard_number. */
fun normalizeCard(raw: Any?): Card? {
    if (raw !is Map<*, *>) return null
    val number = pyStr(if (raw.containsKey("ericard_number")) raw["ericard_number"] else "").trim()
    if (number.isEmpty()) return null

    val card = LinkedHashMap<String, Any?>()
    card["ericard_number"] = number
    for (field in LIST_FIELDS) card[field] = toStringList(raw[field])
    card["un_numbers"] = toStringList(raw["un_numbers"]).map { it.replace(Regex("^(?i)UN\\s*"), "") }
    card["description"] = pyStrOr(raw["description"]).trim()
    card["verified"] = pyBool(if (raw.containsKey("verified")) raw["verified"] else true)
    for ((key, value) in raw) card.putIfAbsent(key.toString(), value)
    return Card(card)
}

fun validateCard(card: Card): List<String> {
    val warnings = ArrayList<String>()
    for (un in card.unNumbers) {
        if (!Regex("\\d{4}").matches(un)) warnings.add("UN number '$un' is not a 4-digit number")
    }
    if (card.substanceNames.isEmpty()) warnings.add("no substance name")
    return warnings
}

/** One UN number with everything the database knows about it. */
class SubstanceRecord(val un: String) {
    val names = ArrayList<String>()
    val hazardNumbers = ArrayList<String>()
    val adrClasses = ArrayList<String>()
    val cards = ArrayList<String>()
    val entries = ArrayList<Triple<String, String, String>>() // (name, hazard, card number)
    var searchText: String = ""
}

fun buildSubstanceIndex(sortedCards: List<Card>): Map<String, SubstanceRecord> {
    val records = LinkedHashMap<String, SubstanceRecord>()
    for (card in sortedCards) {
        val number = card.number
        for ((un, name, hazard) in card.substances()) {
            val key = substanceKey(un)
            if (key.isEmpty()) continue
            val record = records.getOrPut(key) { SubstanceRecord(key) }
            if (name.isNotEmpty() && name !in record.names) record.names.add(name)
            if (hazard.isNotEmpty() && hazard !in record.hazardNumbers) record.hazardNumbers.add(hazard)
            for (adrClass in card.adrClasses) if (adrClass !in record.adrClasses) record.adrClasses.add(adrClass)
            if (number !in record.cards) record.cards.add(number)
            record.entries.add(Triple(name, hazard, number))
        }
    }
    for (record in records.values) {
        record.adrClasses.sortBy { naturalSortKey(it) }
        record.hazardNumbers.sortBy { naturalSortKey(it) }
        record.searchText = (
            listOf(record.un, "un" + record.un) + record.names + record.adrClasses + record.hazardNumbers +
                record.cards + record.cards.map { compact(it) }
            ).joinToString(" ").lowercase()
    }
    return records
}
