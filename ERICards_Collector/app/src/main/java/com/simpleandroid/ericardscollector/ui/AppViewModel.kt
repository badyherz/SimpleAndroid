package com.simpleandroid.ericardscollector.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.simpleandroid.ericardscollector.data.AppData
import com.simpleandroid.ericardscollector.backup.SafBackupFolder
import com.simpleandroid.ericardscollector.data.AppFiles
import com.simpleandroid.ericardscollector.data.BackupOutcome
import com.simpleandroid.ericardscollector.data.BackupResult
import com.simpleandroid.ericardscollector.data.CardDatabase
import com.simpleandroid.ericardscollector.data.ImportMode
import com.simpleandroid.ericardscollector.data.ImportStats
import com.simpleandroid.ericardscollector.data.Json
import com.simpleandroid.ericardscollector.data.Kind
import com.simpleandroid.ericardscollector.data.SEARCH_CATEGORIES
import com.simpleandroid.ericardscollector.data.SettingsStore
import com.simpleandroid.ericardscollector.data.formatProblems
import com.simpleandroid.ericardscollector.data.todayIso
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** The five main pages, reachable from the navigation bar. */
enum class TopTab(val label: String, val title: String) {
    ERICARDS("ERICards", "ERICards"),
    SUBSTANCES("Substances", "Substances"),
    PROFILE("Profile", "Your Profile"),
    GLOSSARY("Glossary", "Glossary"),
    STATEMENTS("H & P", "H & P Statements"),
}

/** Pages that open on top of a main page and have a Back button. */
sealed interface SubPage {
    data class CardDetail(val key: String) : SubPage
    data class SubstanceDetail(val un: String) : SubPage
    data class Term(val term: String) : SubPage
    data object Settings : SubPage
}

/** Dialogs that replace the message boxes of the desktop program. */
sealed interface AppDialog {
    class Info(val title: String, val message: String) : AppDialog
    class Confirm(val title: String, val message: String, val confirmText: String, val onConfirm: () -> Unit) : AppDialog
    class AskDate(val title: String, val message: String, val initialIso: String, val onDate: (String) -> Unit) : AppDialog
    class Choice(
        val title: String,
        val message: String,
        val options: List<String>,
        val defaultIndex: Int,
        val confirmText: String,
        val onChoice: (Int) -> Unit,
    ) : AppDialog
}

/** Search, filter and sort state of a list page. It lives here so it survives opening a detail page. */
class ListUiState {
    var query by mutableStateOf("")
    var status by mutableStateOf("All")
    var category by mutableStateOf("All")
    var sortColumn by mutableStateOf<String?>(null)
    var sortDescending by mutableStateOf(false)
    val listState = LazyListState()

    /** Same behaviour as clicking a column heading: first ascending, then reversed. */
    fun sortBy(column: String) {
        if (sortColumn == column) {
            sortDescending = !sortDescending
        } else {
            sortColumn = column
            sortDescending = false
        }
    }

    fun clear() {
        query = ""
        status = "All"
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Null while the data is loading in the background. */
    var data by mutableStateOf<AppData?>(null)
        private set

    /** Set if the data could not be loaded at startup; the app then shows this text instead of crashing. */
    var loadError by mutableStateOf<String?>(null)
        private set

    /** Increased after every change of the data, so pages know they have to refresh. */
    var revision by mutableIntStateOf(0)
        private set

    var tab by mutableStateOf(TopTab.ERICARDS)
        private set
    val stack = mutableStateListOf<SubPage>()
    var dialog by mutableStateOf<AppDialog?>(null)

    /** Which progress the bar at the bottom shows. */
    var progressKind by mutableStateOf(Kind.ERICARDS)
        private set
    var profileKind by mutableStateOf(Kind.ERICARDS)
        private set

    /** Set to true to make the screen open the folder chooser for the backup folder. */
    var requestBackupFolder by mutableStateOf(false)

    private val backupRunning = AtomicBoolean(false)

    val cardsList = ListUiState()
    val substancesList = ListUiState()
    val profileList = ListUiState()
    val glossaryList = ListUiState()
    val statementsList = ListUiState()
    var selectedStatement by mutableStateOf<String?>(null)

    init {
        thread(name = "load-data") {
            try {
                val app = getApplication<Application>()
                val files = AppFiles(File(app.filesDir, "ERICardsCollector"))
                val loaded = AppData(files) { app.assets.open("reference_texts.json").bufferedReader(Charsets.UTF_8).use { it.readText() } }
                loaded.database.substances() // build the UN number index now instead of on first use
                mainHandler.post {
                    cardsList.category = savedCategory(loaded, SettingsStore.LAST_CATEGORY)
                    substancesList.category = savedCategory(loaded, SettingsStore.LAST_SUBSTANCE_CATEGORY)
                    data = loaded
                    loaded.startupWarnings().takeIf { it.isNotEmpty() }?.let {
                        dialog = AppDialog.Info("Data Warning", it.joinToString("\n\n"))
                    }
                }
            } catch (error: Throwable) {
                // Throwable on purpose: class initialisation problems are Errors, not Exceptions
                val cause = generateSequence<Throwable>(error) { it.cause }.last()
                mainHandler.post {
                    loadError = "${cause.javaClass.simpleName}: ${cause.message ?: "no details"}"
                }
            }
        }
    }

    private fun savedCategory(app: AppData, setting: String): String =
        app.settings.getString(setting).let { if (it in SEARCH_CATEGORIES) it else "All" }

    private fun changed() {
        revision++
    }

    // --- Navigation ---
    /** A main page: the back stack is cleared. */
    fun openTab(target: TopTab) {
        stack.clear()
        tab = target
        when (target) {
            TopTab.ERICARDS -> progressKind = Kind.ERICARDS
            TopTab.SUBSTANCES -> progressKind = Kind.SUBSTANCES
            TopTab.PROFILE -> progressKind = profileKind
            else -> {}
        }
    }

    fun openCard(key: String) {
        stack.add(SubPage.CardDetail(key))
        progressKind = Kind.ERICARDS
    }

    fun openSubstance(un: String) {
        stack.add(SubPage.SubstanceDetail(un))
        progressKind = Kind.SUBSTANCES
    }

    fun openTerm(term: String) {
        stack.add(SubPage.Term(term))
    }

    fun openSettings() {
        stack.add(SubPage.Settings)
    }

    fun goBack() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        // The progress bar follows the page that is shown again
        when (val page = stack.lastOrNull()) {
            is SubPage.CardDetail -> progressKind = Kind.ERICARDS
            is SubPage.SubstanceDetail -> progressKind = Kind.SUBSTANCES
            null -> progressKind = when (tab) {
                TopTab.ERICARDS -> Kind.ERICARDS
                TopTab.SUBSTANCES -> Kind.SUBSTANCES
                TopTab.PROFILE -> profileKind
                else -> progressKind
            }
            else -> {}
        }
    }

    fun chooseProfileKind(kind: Kind) {
        profileKind = kind
        progressKind = kind
    }

    // --- Settings ---
    fun setAskDate(value: Boolean) {
        data?.settings?.set(SettingsStore.ASK_DATE, value)
        changed()
    }

    fun setQuickCollect(value: Boolean) {
        data?.settings?.set(SettingsStore.QUICK_COLLECT, value)
        changed()
    }

    fun setCategory(kind: Kind, category: String) {
        val state = if (kind == Kind.SUBSTANCES) substancesList else cardsList
        state.category = category
        data?.settings?.set(if (kind == Kind.SUBSTANCES) SettingsStore.LAST_SUBSTANCE_CATEGORY else SettingsStore.LAST_CATEGORY, category)
    }

    fun chooseLanguage() {
        val app = data ?: return
        val languages = app.reference.languages()
        if (languages.isEmpty()) {
            dialog = AppDialog.Info("Glossary Text Language", "No languages found.")
            return
        }
        val codes = languages.keys.toList()
        val labels = languages.map { (code, name) -> if (name == code) name else "$name ($code)" }
        dialog = AppDialog.Choice(
            title = "Glossary Text Language",
            message = "Language of the glossary and the H & P statements:",
            options = labels,
            defaultIndex = codes.indexOf(app.language).coerceAtLeast(0),
            confirmText = "OK",
        ) { index ->
            app.settings.set(SettingsStore.REFERENCE_LANGUAGE, codes[index])
            changed()
        }
    }

    // --- Collecting ---
    fun toggleCollected(key: String, kind: Kind) {
        val app = data ?: return
        val name = app.describe(kind, key) ?: return
        val section = app.collection.section(kind)

        if (section.isChecked(key)) {
            val collectedOn = app.formatDate(section.getDate(key))
            dialog = AppDialog.Confirm(
                "Unmark", "Remove $name from your collection?\n\nThe collection date ($collectedOn) will be lost.", "Remove",
            ) {
                app.collection.removeEntry(kind, key)
                changed()
            }
        } else if (app.settings.getBoolean(SettingsStore.ASK_DATE)) {
            dialog = AppDialog.AskDate("Collection Date", "When did you see $name?", todayIso()) { iso ->
                app.collection.setEntry(kind, key, iso)
                changed()
                suggestBackupOnce(app)
            }
        } else {
            app.collection.setEntry(kind, key, todayIso())
            changed()
            suggestBackupOnce(app)
        }
    }

    fun changeCollectDate(key: String, kind: Kind) {
        val app = data ?: return
        val name = app.describe(kind, key) ?: return
        val section = app.collection.section(kind)
        if (!section.isChecked(key)) return
        dialog = AppDialog.AskDate("Change Collection Date", "New collection date for $name:", section.getDate(key)) { iso ->
            app.collection.setEntry(kind, key, iso)
            changed()
        }
    }

    // --- Reading files the user picked ---
    private fun readJson(uri: Uri, failTitle: String, onData: (Any?) -> Unit) {
        val resolver = getApplication<Application>().contentResolver
        thread(name = "read-file") {
            try {
                val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IOException("The file could not be opened.")
                val parsed = Json.parse(text)
                mainHandler.post { onData(parsed) }
            } catch (error: Exception) {
                mainHandler.post {
                    dialog = AppDialog.Info(failTitle, "Could not read the selected file:\n${error.message ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    // --- Database Updates ---
    fun onDatabaseFile(uri: Uri?) {
        val app = data ?: return
        if (uri == null) return
        readJson(uri, "Import Failed") { fileData ->
            val parsed = CardDatabase.parseImport(fileData)
            if (parsed.cards.isEmpty()) {
                dialog = AppDialog.Info(
                    "Import Failed", "No valid ERICards were found in the selected file." + formatProblems(parsed.problems),
                )
                return@readJson
            }
            // An empty database is simply filled; otherwise the user chooses how to combine
            if (!app.hasDatabase) {
                confirmDatabasePlan(app, fileData, replace = true)
            } else {
                dialog = AppDialog.Choice(
                    "Update Database",
                    "${parsed.cards.size} valid ERICard(s) found in the file.\nHow should they be combined with the current database?",
                    listOf(
                        "Replace the current database with the file (recommended for a full database)",
                        "Merge \u2013 add new cards and update changed ones, keep all other cards",
                    ),
                    defaultIndex = 1,
                    confirmText = "Next",
                ) { choice -> confirmDatabasePlan(app, fileData, replace = choice == 0) }
            }
        }
    }

    private fun confirmDatabasePlan(app: AppData, fileData: Any?, replace: Boolean) {
        val plan = app.database.plan(fileData, replace)
        val stats = plan.stats
        dialog = AppDialog.Confirm(
            "Update Database",
            "${plan.parsed.cards.size} valid ERICard(s) found.\n\n" +
                "New: ${stats.added}\nUpdated: ${stats.updated}\nUnchanged: ${stats.unchanged}\n" +
                "Removed: ${stats.removed}\nSkipped (invalid): ${plan.parsed.skipped}" +
                formatProblems(plan.parsed.problems) +
                "\n\nA backup of the current database is created first. Your collection is not affected.\n\nContinue?",
            "Continue",
        ) {
            try {
                app.database.backup()
                app.database.apply(plan)
                changed()
                dialog = AppDialog.Info("Database Updated", "The database now has ${app.database.cards.size} ERICard(s).")
            } catch (error: IOException) {
                dialog = AppDialog.Info("Update Failed", "Could not update the database:\n${error.message}")
            }
        }
    }

    // --- Reference Texts ---
    fun onReferenceFile(uri: Uri?) {
        val app = data ?: return
        if (uri == null) return
        readJson(uri, "Import Failed") { fileData ->
            if (app.reference.plan(fileData, replace = false) == null) {
                dialog = AppDialog.Info(
                    "Import Failed",
                    "No reference texts were found in the selected file. It should contain sections like " +
                        "\"glossary\", \"hazard_statements\" or \"precautionary_statements\".",
                )
                return@readJson
            }
            dialog = AppDialog.Choice(
                "Update Reference Texts",
                "How should the texts in the file be combined with the current ones?",
                listOf(
                    "Merge \u2013 add new texts and update changed ones, keep all other texts and languages",
                    "Replace the current texts with the file",
                ),
                defaultIndex = 0,
                confirmText = "Next",
            ) { choice -> confirmReferencePlan(app, fileData, replace = choice == 1) }
        }
    }

    private fun confirmReferencePlan(app: AppData, fileData: Any?, replace: Boolean) {
        val plan = app.reference.plan(fileData, replace) ?: return
        val stats: ImportStats = plan.stats
        dialog = AppDialog.Confirm(
            "Update Reference Texts",
            "New: ${stats.added}\nUpdated: ${stats.updated}\nUnchanged: ${stats.unchanged}\n" +
                "Removed: ${stats.removed}\nSkipped (invalid): ${plan.skipped}" +
                formatProblems(plan.problems) +
                "\n\nA backup of the current texts is created first.\n\nContinue?",
            "Continue",
        ) {
            try {
                app.reference.backup()
                app.reference.apply(plan)
                changed()
                dialog = AppDialog.Info(
                    "Reference Texts Updated", "Available languages: " + app.reference.languages().values.joinToString(", ").ifEmpty { "none" },
                )
            } catch (error: IOException) {
                dialog = AppDialog.Info("Update Failed", "Could not save the texts:\n${error.message}")
            }
        }
    }

    fun resetReferenceTexts() {
        val app = data ?: return
        dialog = AppDialog.Confirm(
            "Reset Reference Texts",
            "Replace all glossary texts and H & P statements with the texts that come with the app?\n\n" +
                "A backup of the current texts is created first.",
            "Reset",
        ) {
            try {
                app.reference.backup()
                app.reference.resetToBundled()
                changed()
                dialog = AppDialog.Info("Reference Texts Reset", "The built-in texts are in use again.")
            } catch (error: IOException) {
                dialog = AppDialog.Info("Reset Failed", "Could not save the texts:\n${error.message}")
            }
        }
    }

    // --- Collection Import and Export ---
    fun suggestedExportName(): String = "ericards_collection_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json"

    fun onExportTarget(uri: Uri?) {
        val app = data ?: return
        if (uri == null) return
        try {
            val json = Json.stringify(app.collection.exportData(), 2)
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("The file could not be opened.")
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            dialog = AppDialog.Info(
                "Export Complete",
                "Exported ${app.collection.ericards.entries.size} ERICard(s) and ${app.collection.substances.entries.size} substance(s).",
            )
        } catch (error: IOException) {
            dialog = AppDialog.Info("Export Failed", "Could not write the file:\n${error.message}")
        }
    }

    fun onCollectionFile(uri: Uri?) {
        val app = data ?: return
        if (uri == null) return
        readJson(uri, "Import Failed") { fileData ->
            val imported = app.collection.parseImport(fileData)
            val found = imported.sections.filterValues { !it.isNullOrEmpty() }.mapValues { it.value!!.size }
            if (found.isEmpty()) {
                dialog = AppDialog.Info(
                    "Import Failed", "No collected items were found in the selected file." + formatProblems(imported.problems),
                )
                return@readJson
            }
            val labels = mapOf(Kind.ERICARDS to "ERICard(s)", Kind.SUBSTANCES to "substance(s)")
            val foundText = found.entries.joinToString(" and ") { "${it.value} collected ${labels.getValue(it.key)}" }
            dialog = AppDialog.Choice(
                "Import Collection",
                "$foundText found in the file.\nHow should they be combined with your current collection?",
                listOf(
                    "Merge \u2013 keep the earlier date if an item is in both",
                    "Merge \u2013 use the imported date if an item is in both",
                    "Replace my collection with the imported one",
                ),
                defaultIndex = 0,
                confirmText = "Next",
            ) { choice -> confirmCollectionPlan(app, fileData, ImportMode.entries[choice], labels) }
        }
    }

    private fun confirmCollectionPlan(app: AppData, fileData: Any?, mode: ImportMode, labels: Map<Kind, String>) {
        val imported = app.collection.parseImport(fileData)
        val plan = app.collection.mergePlan(imported, mode)
        val summary = plan.stats.entries.joinToString("\n") { (kind, s) ->
            "${labels.getValue(kind).replaceFirstChar { it.uppercase() }}: new ${s.added}, date changed ${s.changed}, " +
                "unchanged ${s.unchanged}, removed ${s.removed}"
        }
        dialog = AppDialog.Confirm(
            "Import Collection",
            "$summary\nSkipped (invalid): ${imported.skipped}${formatProblems(imported.problems)}\n\n" +
                "A backup of your current collection is created first.\n\nContinue?",
            "Continue",
        ) {
            try {
                app.collection.backup()
                app.collection.apply(plan)
                changed()
                dialog = AppDialog.Info(
                    "Import Complete",
                    "Your collection now has ${app.collection.ericards.entries.size} ERICard(s) and " +
                        "${app.collection.substances.entries.size} substance(s).",
                )
            } catch (error: IOException) {
                dialog = AppDialog.Info("Import Failed", "Could not save the collection:\n${error.message}")
            }
        }
    }

    // --- Backup of the collection ---
    /** Said once, after the first collected item: the collection lives only on this phone. */
    private fun suggestBackupOnce(app: AppData) {
        if (dialog != null || app.backup.enabled || app.settings.getBoolean(SettingsStore.BACKUP_PROMPT_SHOWN)) return
        app.settings.set(SettingsStore.BACKUP_PROMPT_SHOWN, true)
        dialog = AppDialog.Confirm(
            "Protect Your Collection",
            "Your collection is stored only on this phone, and uninstalling the app deletes it.\n\n" +
                "Choose a backup folder now? The app then saves a copy there automatically whenever you leave it " +
                "and your collection has changed.",
            "Choose Folder",
        ) { requestBackupFolder = true }
    }

    /** Called when the app is left: saves a backup copy if a folder is set up and the collection changed. */
    fun backupIfNeeded() {
        val app = data ?: return
        if (app.backup.enabled) runBackup(force = false, showResult = false)
    }

    fun onBackupFolder(uri: Uri?) {
        val app = data ?: return
        if (uri == null) return
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (error: SecurityException) {
            dialog = AppDialog.Info(
                "Backup Folder",
                "Android does not allow this folder to be used. Please choose another one, for example a new folder inside Documents.",
            )
            return
        }
        val previous = app.backup.folder
        if (previous.isNotEmpty() && previous != uri.toString()) {
            try {
                resolver.releasePersistableUriPermission(Uri.parse(previous), flags)
            } catch (error: Exception) {
                // the old folder is not used any more anyway
            }
        }
        app.settings.set(SettingsStore.BACKUP_FOLDER, uri.toString())
        app.settings.set(SettingsStore.BACKUP_ENABLED, true)
        changed()
        runBackup(force = true, showResult = true, justSetUp = true)
    }

    fun setBackupEnabled(value: Boolean) {
        val app = data ?: return
        app.settings.set(SettingsStore.BACKUP_ENABLED, value)
        changed()
        if (value) runBackup(force = false, showResult = false)
    }

    fun backupNow() {
        val app = data ?: return
        if (!app.backup.hasFolder) requestBackupFolder = true else runBackup(force = true, showResult = true)
    }

    private fun runBackup(force: Boolean, showResult: Boolean, justSetUp: Boolean = false) {
        val app = data ?: return
        val folderUri = app.backup.folder
        if (folderUri.isEmpty() || !backupRunning.compareAndSet(false, true)) return
        val resolver = getApplication<Application>().contentResolver
        thread(name = "backup") {
            val outcome = try {
                app.backup.run(SafBackupFolder(resolver, Uri.parse(folderUri)), force)
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                app.settings.set(SettingsStore.BACKUP_ERROR, message)
                BackupOutcome(BackupResult.FAILED, message)
            }
            backupRunning.set(false)
            mainHandler.post {
                changed()
                if (showResult) dialog = backupResultDialog(app, outcome, justSetUp)
            }
        }
    }

    private fun backupResultDialog(app: AppData, outcome: BackupOutcome, justSetUp: Boolean): AppDialog {
        val folder = SafBackupFolder.describe(app.backup.folder)
        return when (outcome.result) {
            BackupResult.BACKED_UP -> AppDialog.Info(
                if (justSetUp) "Backup Folder Set" else "Backup Saved",
                "A copy of your collection was saved as ${outcome.fileName} in:\n$folder" +
                    if (justSetUp) "\n\nFrom now on a copy is saved automatically whenever you leave the app and your collection has changed." else "",
            )
            BackupResult.NOTHING_TO_BACK_UP -> AppDialog.Info(
                if (justSetUp) "Backup Folder Set" else "Nothing to Back Up",
                if (justSetUp) "A copy is saved automatically in:\n$folder\n\nas soon as your collection has something in it, whenever you leave the app."
                else "Your collection is empty. Collect an ERICard or a substance first.",
            )
            BackupResult.UNCHANGED -> AppDialog.Info("Backup", outcome.message)
            BackupResult.FAILED -> AppDialog.Info(
                "Backup Failed", "${outcome.message}\n\nChoose the backup folder again in Settings > Backup Folder.",
            )
        }
    }
}
