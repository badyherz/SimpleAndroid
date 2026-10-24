package com.simpleandroid.ericardscollector.data

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** A place where backup files are stored. On Android this is a folder the user picked. */
interface BackupFolder {
    fun listNames(): List<String>
    fun write(name: String, content: String)
    fun delete(name: String)
}

enum class BackupResult { BACKED_UP, UNCHANGED, NOTHING_TO_BACK_UP, FAILED }

class BackupOutcome(val result: BackupResult, val message: String, val fileName: String? = null)

/**
 * Keeps a copy of the collection outside the app, so that uninstalling the app does not lose it.
 * The copies use the export format, so "Import Collection" can read them (also in the desktop program).
 */
class BackupManager(private val settings: SettingsStore, private val collection: CollectionStore) {
    val folder: String get() = settings.getString(SettingsStore.BACKUP_FOLDER)
    val hasFolder: Boolean get() = folder.isNotEmpty()
    val enabled: Boolean get() = hasFolder && settings.getBoolean(SettingsStore.BACKUP_ENABLED)
    val lastBackup: String get() = settings.getString(SettingsStore.BACKUP_LAST)
    val lastError: String get() = settings.getString(SettingsStore.BACKUP_ERROR)

    /** True as soon as there is something worth protecting. */
    val hasCollection: Boolean get() = collection.ericards.entries.isNotEmpty() || collection.substances.entries.isNotEmpty()

    /** One line for the settings page. */
    fun statusText(): String = when {
        lastError.isNotEmpty() -> "The last attempt failed: $lastError"
        lastBackup.isNotEmpty() -> "Last backup: $lastBackup"
        hasFolder -> "No backup has been made yet"
        else -> "No backup folder chosen"
    }

    /**
     * Writes a backup if the collection changed since the last one ([force] writes it anyway).
     * An empty collection is never written, so a good backup is not replaced by nothing.
     * Never throws: a problem is returned and remembered for the settings page.
     */
    fun run(target: BackupFolder, force: Boolean = false, now: LocalDateTime = LocalDateTime.now()): BackupOutcome {
        val data = collection.exportData()
        val fingerprint = fingerprint(data)
        if (!hasCollection) return BackupOutcome(BackupResult.NOTHING_TO_BACK_UP, "There is nothing to back up yet.")
        if (!force && fingerprint == settings.getString(SettingsStore.BACKUP_HASH) && lastError.isEmpty()) {
            return BackupOutcome(BackupResult.UNCHANGED, "Nothing has changed since the last backup.")
        }

        val name = FILE_PREFIX + now.format(DateTimeFormatter.BASIC_ISO_DATE) + FILE_SUFFIX
        try {
            target.write(name, Json.stringify(data, 2))
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            settings.set(SettingsStore.BACKUP_ERROR, message)
            return BackupOutcome(BackupResult.FAILED, message)
        }
        settings.set(SettingsStore.BACKUP_HASH, fingerprint)
        settings.set(SettingsStore.BACKUP_ERROR, "")
        settings.set(SettingsStore.BACKUP_LAST, now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        prune(target)
        return BackupOutcome(BackupResult.BACKED_UP, "Backup saved.", name)
    }

    /** Keeps the newest [KEEP] backups. Other files in the folder are never touched. */
    private fun prune(target: BackupFolder) {
        try {
            val backups = target.listNames().filter { NAME_PATTERN.matches(it) }.sortedDescending()
            for (old in backups.drop(KEEP)) target.delete(old)
        } catch (error: Exception) {
            // Not being able to tidy up is no reason to report a failed backup
        }
    }

    companion object {
        const val FILE_PREFIX = "ericards_collection_backup_"
        const val FILE_SUFFIX = ".json"
        const val KEEP = 14
        private val NAME_PATTERN = Regex("^ericards_collection_backup_\\d{8}\\.json$")

        /** The content without the export time, so that "changed or not" ignores the clock. */
        private fun fingerprint(data: Map<String, Any?>): String {
            val content = Json.stringify(data.filterKeys { it != "exported_at" }, 0)
            return MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
