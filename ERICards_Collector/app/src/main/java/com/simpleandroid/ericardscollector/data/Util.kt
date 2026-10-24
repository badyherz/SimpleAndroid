package com.simpleandroid.ericardscollector.data

import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// --- Configuration ---
const val SCHEMA_VERSION = 1
const val DEFAULT_LANGUAGE = "en"

val SEARCH_CATEGORIES = listOf("All", "UN Number", "Substance Name", "ADR Class", "Hazard Number", "ERICard Number")
val STATUS_FILTERS = listOf("All", "Collected", "Missing")
val STATEMENT_FILTERS = listOf("All", "H Statements", "EUH Statements", "P Statements")

/** The two things that can be collected. */
enum class Kind { ERICARDS, SUBSTANCES }

/** Ways to combine an imported collection with the current one. */
enum class ImportMode { KEEP_EARLIER, USE_IMPORTED, REPLACE }

// --- Storage ---
/** All files of the app live in one private directory (no storage permissions needed). */
class AppFiles(val directory: File) {
    val databaseFile = File(directory, "ericards_db.json")
    val collectionFile = File(directory, "collection.json")
    val settingsFile = File(directory, "config.json")
    val referenceFile = File(directory, "reference_texts.json")
    val backupDir = File(directory, "backups")

    init {
        directory.mkdirs()
        backupDir.mkdirs()
    }
}

fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

fun todayIso(): String = LocalDate.now().toString()

/** Returns (data, warning). A damaged file is copied to the backup folder and the default is used. */
fun loadJson(file: File, default: Any?, files: AppFiles): Pair<Any?, String?> {
    if (!file.exists()) return default to null
    return try {
        Json.parse(file.readText(Charsets.UTF_8)) to null
    } catch (error: Exception) {
        if (error !is JsonParseException && error !is IOException) throw error
        val note = try {
            val copy = File(files.backupDir, "${file.name}.corrupt-${timestamp()}")
            file.copyTo(copy, overwrite = true)
            "A copy of the damaged file was kept in the app's backups."
        } catch (copyError: IOException) {
            "The damaged file could not be copied."
        }
        default to "Could not read '${file.name}': ${error.message}\n$note"
    }
}

/** Writes to a temporary file first so a crash cannot leave a half-written file behind. */
fun saveJson(file: File, data: Any?) {
    val temp = File(file.path + ".tmp")
    temp.writeText(Json.stringify(data, 2), Charsets.UTF_8)
    try {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (error: Exception) {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

fun backupFile(source: File, files: AppFiles, prefix: String): File? {
    if (!source.exists()) return null
    val target = File(files.backupDir, "$prefix-${timestamp()}.json")
    source.copyTo(target, overwrite = true)
    return target
}

// --- Helper Functions ---
/** Sort key where "2-10" sorts after "2-02", "2.1" before "2.3" and "10" after "9". */
class NaturalKey(private val parts: List<Any>) : Comparable<NaturalKey> {
    override fun compareTo(other: NaturalKey): Int {
        val common = minOf(parts.size, other.parts.size)
        for (index in 0 until common) {
            val mine = parts[index]
            val theirs = other.parts[index]
            val result = if (mine is String && theirs is String) mine.compareTo(theirs)
            else (mine as BigInteger).compareTo(theirs as BigInteger)
            if (result != 0) return result
        }
        return parts.size.compareTo(other.parts.size)
    }
}

private val DIGITS = Regex("\\d+")

fun naturalSortKey(text: String): NaturalKey {
    val parts = ArrayList<Any>()
    var last = 0
    for (match in DIGITS.findAll(text)) {
        parts.add(text.substring(last, match.range.first).lowercase())
        parts.add(BigInteger(match.value))
        last = match.range.last + 1
    }
    parts.add(text.substring(last).lowercase())
    return NaturalKey(parts)
}

fun cardKey(number: Any?): String = pyStr(number).trim().uppercase()

fun substanceKey(un: Any?): String = pyStr(un).trim().replace(Regex("^(?i)UN\\s*"), "").trim()

fun compact(text: Any?): String = pyStr(text).lowercase().replace(Regex("[\\s\\-_]"), "")

fun calculatePercent(part: Int, total: Int): Double = if (total != 0) part.toDouble() / total * 100 else 0.0

fun parseIsoDate(text: Any?): LocalDate? = try {
    LocalDate.parse(pyStr(text).trim(), DateTimeFormatter.ISO_LOCAL_DATE)
} catch (error: DateTimeParseException) {
    null
}

fun formatProblems(problems: List<String>, limit: Int = 6): String {
    if (problems.isEmpty()) return ""
    var text = "\n\nProblems found:\n" + problems.take(limit).joinToString("\n") { "- $it" }
    if (problems.size > limit) text += "\n... and ${problems.size - limit} more"
    return text
}

/** Long lists (hundreds of UN numbers per card) are shortened in list rows. */
fun summarizeList(values: List<String>, limit: Int = 3, separator: String = ", "): String {
    if (values.size <= limit) return values.joinToString(separator)
    return "${values.take(limit).joinToString(separator)} (+${values.size - limit} more)"
}

// Android uses the ICU regex engine, which does not know the JVM-only flag (?U). Non-breaking spaces
// are therefore matched explicitly with \p{Z}.
private val SENTENCE_SPLIT = Regex("(?<=[a-zäöüß0-9)]{2}\\.)[\\s\\p{Z}]+(?=[A-ZÄÖÜ])")

/** Splits running text into sentences. Abbreviations like "z. B." stay together. */
fun splitSentences(text: String): List<String> =
    SENTENCE_SPLIT.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }

private val LABEL_PATTERN = Regex("^([^:.;]{2,40}:)(?=\\s|$)")

/** The leading "Label:" of a line, if it has one (shown in bold). */
fun leadingLabel(text: String): String? = LABEL_PATTERN.find(text)?.groupValues?.get(1)

// --- Python-like conversions, so loosely typed JSON behaves like it does in the desktop program ---
fun pyStr(value: Any?): String = when (value) {
    null -> "None"
    true -> "True"
    false -> "False"
    else -> value.toString()
}

/** Python's `str(value or "")`. */
fun pyStrOr(value: Any?): String = if (pyBool(value)) pyStr(value) else ""

fun pyBool(value: Any?): Boolean = when (value) {
    null -> false
    is Boolean -> value
    is Number -> value.toDouble() != 0.0
    is String -> value.isNotEmpty()
    is Collection<*> -> value.isNotEmpty()
    is Map<*, *> -> value.isNotEmpty()
    else -> true
}

fun toStringList(value: Any?): List<String> {
    val list: List<Any?> = when (value) {
        null -> return emptyList()
        is String, is Number, is Boolean -> listOf(value)
        is List<*> -> value
        else -> return emptyList()
    }
    return list.map { pyStr(it).trim() }.filter { it.isNotEmpty() }
}

/** Converts a strftime format (as stored by the desktop program) into a Java date pattern. */
fun strftimeToPattern(format: String): String {
    val out = StringBuilder()
    var index = 0
    while (index < format.length) {
        val char = format[index]
        if (char == '%' && index + 1 < format.length) {
            index++
            out.append(
                when (format[index]) {
                    'Y' -> "yyyy"; 'y' -> "yy"; 'm' -> "MM"; 'd' -> "dd"
                    'B' -> "MMMM"; 'b' -> "MMM"; 'A' -> "EEEE"; 'a' -> "EEE"
                    'H' -> "HH"; 'M' -> "mm"; 'S' -> "ss"; '%' -> "%"
                    else -> "'${format[index]}'"
                }
            )
        } else if (char.isLetter()) {
            out.append('\'').append(char).append('\'')
        } else if (char == '\'') {
            out.append("''")
        } else {
            out.append(char)
        }
        index++
    }
    return out.toString()
}
