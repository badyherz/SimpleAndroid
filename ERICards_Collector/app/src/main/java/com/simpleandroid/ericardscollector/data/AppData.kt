package com.simpleandroid.ericardscollector.data

/** Bundles the four stores of the app and the logic that spans several of them. */
class AppData(val files: AppFiles, bundledReferenceJson: () -> String) {
    val settings = SettingsStore(files)
    val database = CardDatabase(files)
    val collection = CollectionStore(files)
    val reference = ReferenceTexts(files, settings, bundledReferenceJson)
    val backup = BackupManager(settings, collection)

    val hasDatabase: Boolean get() = database.cards.isNotEmpty()

    /** Problems found while loading the stores, to be shown once at startup. */
    fun startupWarnings(): List<String> =
        listOfNotNull(database.loadWarning, collection.loadWarning, settings.loadWarning, reference.loadWarning)

    // --- Glossary Language ---
    val language: String
        get() {
            val languages = reference.languages()
            val wanted = settings.getString(SettingsStore.REFERENCE_LANGUAGE)
            if (wanted in languages || languages.isEmpty()) return if (wanted in languages) wanted else DEFAULT_LANGUAGE
            return if (DEFAULT_LANGUAGE in languages) DEFAULT_LANGUAGE else languages.keys.first()
        }

    val languageName: String get() = reference.languages()[language] ?: language

    fun formatDate(isoDate: String): String = settings.formatDate(isoDate)

    // --- Collecting ---
    /** Name of a collectible for dialogs, or null if it no longer exists. */
    fun describe(kind: Kind, key: String): String? =
        if (kind == Kind.SUBSTANCES) database.getSubstance(key)?.let { "UN ${it.un}" }
        else database.get(key)?.let { "ERICard ${it.number}" }

    fun isCollected(kind: Kind, key: String) = collection.section(kind).isChecked(key)

    fun collectedDate(kind: Kind, key: String) = collection.section(kind).getDate(key)

    // --- Statistics ---
    fun stats(kind: Kind): Stats {
        val section = collection.section(kind)
        val items = if (kind == Kind.SUBSTANCES) database.sortedSubstances().map { it.un to it.adrClasses.toList() }
        else database.sortedCards().map { it.number to it.adrClasses }
        return buildStats(items) { section.isChecked(it) }
    }
}
