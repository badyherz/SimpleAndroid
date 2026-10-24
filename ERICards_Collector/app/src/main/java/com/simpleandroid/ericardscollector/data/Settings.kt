package com.simpleandroid.ericardscollector.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class SettingsStore(private val files: AppFiles) {
    var values: Map<String, Any?> = emptyMap()
        private set
    var loadWarning: String? = null
        private set

    init {
        val (raw, warning) = loadJson(files.settingsFile, emptyMap<String, Any?>(), files)
        loadWarning = warning
        val data = LinkedHashMap<String, Any?>()
        (raw as? Map<*, *>)?.forEach { (key, value) -> data[key.toString()] = value }
        if (data.containsKey("info_language")) {
            if (!data.containsKey("reference_language")) data["reference_language"] = data["info_language"]
            data.remove("info_language")
        }
        values = DEFAULTS + data
    }

    fun get(key: String): Any? = if (values.containsKey(key)) values[key] else DEFAULTS[key]

    @Synchronized
    fun set(key: String, value: Any?) {
        values = values + (key to value)
        saveJson(files.settingsFile, values)
    }

    fun getString(key: String): String = pyStr(get(key) ?: "")

    fun getBoolean(key: String): Boolean = pyBool(get(key))

    fun getInt(key: String): Int = (get(key) as? Number)?.toInt() ?: 0

    /** Formats an ISO date (as stored) with the configured date format. */
    fun formatDate(isoDate: String): String {
        if (isoDate.isEmpty()) return ""
        return try {
            val date = LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE)
            date.format(DateTimeFormatter.ofPattern(strftimeToPattern(getString("date_format"))))
        } catch (error: DateTimeParseException) {
            isoDate
        } catch (error: IllegalArgumentException) {
            isoDate
        }
    }

    companion object {
        const val DATE_FORMAT = "date_format"
        const val LAST_CATEGORY = "last_category"
        const val LAST_SUBSTANCE_CATEGORY = "last_substance_category"
        const val REFERENCE_LANGUAGE = "reference_language"
        const val ASK_DATE = "ask_date"
        const val QUICK_COLLECT = "quick_collect"

        // Backup of the collection into a folder chosen by the user
        const val BACKUP_FOLDER = "backup_folder"          // tree URI of the folder, empty if none
        const val BACKUP_ENABLED = "backup_enabled"
        const val BACKUP_LAST = "backup_last"              // time of the last successful backup
        const val BACKUP_HASH = "backup_last_hash"         // fingerprint of the data in the last backup
        const val BACKUP_ERROR = "backup_error"            // message of the last failed attempt, empty if fine
        const val BACKUP_PROMPT_SHOWN = "backup_prompt_shown"

        /** Version of the built-in reference texts that has been applied to the reference file. */
        const val BUNDLED_REFERENCE_VERSION = "bundled_reference_version"

        val DEFAULTS: Map<String, Any?> = linkedMapOf(
            "schema_version" to SCHEMA_VERSION,
            DATE_FORMAT to "%Y-%m-%d",
            LAST_CATEGORY to "All",
            LAST_SUBSTANCE_CATEGORY to "All",
            REFERENCE_LANGUAGE to DEFAULT_LANGUAGE,
            ASK_DATE to true,
            QUICK_COLLECT to true,
            BUNDLED_REFERENCE_VERSION to 0,
            BACKUP_FOLDER to "",
            BACKUP_ENABLED to false,
            BACKUP_LAST to "",
            BACKUP_HASH to "",
            BACKUP_ERROR to "",
            BACKUP_PROMPT_SHOWN to false,
        )
    }
}
