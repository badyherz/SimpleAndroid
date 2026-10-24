package com.simpleandroid.ericardscollector.data

import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the data layer with a tiny made-up database (the real ERICards database is not part of
 * this repository). They run on the JVM with `./gradlew test`.
 */
class DataLayerTest {
    private val database = """
        {
          "schema_version": 1, "last_updated": "2026-01-01", "language": "English",
          "cards": [
            {"ericard_number": "3-01", "un_numbers": ["UN 1203", "1202"], "substance_names": ["Gasoline", "Diesel"],
             "adr_classes": ["3"], "hazard_numbers": ["33", "30"], "description": "Highly flammable. Keep away from fire.",
             "substances": [{"un": "1203", "name": "Gasoline", "hin": "33"}, {"un": "1202", "name": "Diesel", "hin": "30"}],
             "sections": {"1. Hazards": ["Fire: yes"], "2. Actions": ["Call 112"]},
             "reference": {"classes": [{"adr_class": "3", "name": "Flammable liquids"}], "hazard_numbers": [{"hin": "33", "meaning": "Highly flammable liquid"}]},
             "extra_field": 42},
            {"ericard_number": "2-10", "un_numbers": ["1011"], "substance_names": ["Butane"], "adr_classes": ["2.1"], "hazard_numbers": ["23"]},
            {"ericard_number": "2-02", "un_numbers": ["1001"], "substance_names": ["Acetylene"], "adr_classes": ["2.1"], "hazard_numbers": ["239"], "verified": false},
            {"nonsense": true}
          ]
        }
    """.trimIndent()

    private val reference = """
        {"schema_version": 1, "defaults_version": 1, "languages": {"en": "English", "de": "Deutsch"},
         "glossary": {"ADR": {"order": 1, "title": {"en": "Road transport"}, "text": {"en": "Long text."}}},
         "pictograms": {"GHS02": {"name": {"en": "Flame"}}},
         "hazard_statements": {"H225": {"pictograms": ["GHS02"], "text": {"en": "Highly flammable liquid.", "de": "Leicht entzuendbar."}}},
         "supplemental_statements": {}, "precautionary_statements": {"P210": {"text": {"en": "Keep away from heat."}}}}
    """.trimIndent()

    private fun newApp(): AppData {
        val dir = Files.createTempDirectory("ericards-test").toFile()
        val files = AppFiles(dir)
        files.databaseFile.writeText(database)
        return AppData(files) { reference }
    }

    @Test
    fun jsonRoundTripKeepsOrderAndUnicode() {
        val text = """{"b": 1, "a": [true, null, 2.5, "ä\n\"x\""], "c": {}}"""
        val parsed = Json.parse(text) as Map<*, *>
        assertEquals(listOf("b", "a", "c"), parsed.keys.toList())
        assertEquals(parsed, Json.parse(Json.stringify(parsed)))
    }

    @Test(expected = JsonParseException::class)
    fun brokenJsonIsRejected() {
        Json.parse("{\"a\": ")
    }

    @Test
    fun sentencesAreSplitAfterFullStops() {
        assertEquals(listOf("Keep away from fire.", "Call 112."), splitSentences("Keep away from fire. Call 112."))
        // a non-breaking space between the sentences counts as a space
        assertEquals(2, splitSentences("Keep away from fire.\u00A0Call 112.").size)
        // abbreviations stay together
        assertEquals(1, splitSentences("Use e.g. water spray.").size)
    }

    @Test
    fun naturalSortingOrdersNumbersLikePeople() {
        val sorted = listOf("2-10", "2-02", "10", "9", "2.3", "2.1").sortedBy { naturalSortKey(it) }
        assertEquals(listOf("2-02", "2-10", "2.1", "2.3", "9", "10"), sorted)
    }

    @Test
    fun invalidCardsAreSkippedAndUnknownFieldsKept() {
        val app = newApp()
        assertEquals(3, app.database.cards.size)
        assertEquals(listOf("2-02", "2-10", "3-01"), app.database.sortedCards().map { it.number })
        assertEquals(42L, app.database.get("3-01")!!.raw["extra_field"])
        assertFalse(app.database.get("2-02")!!.verified)
        assertEquals(listOf("1203", "1202"), app.database.get("3-01")!!.unNumbers)
    }

    @Test
    fun substanceIndexCombinesCards() {
        val app = newApp()
        assertEquals(listOf("1001", "1011", "1202", "1203"), app.database.sortedSubstances().map { it.un })
        val gasoline = app.database.getSubstance("UN 1203")!!
        assertEquals(listOf("Gasoline"), gasoline.names)
        assertEquals(listOf("3-01"), gasoline.cards)
    }

    @Test
    fun searchFindsByAllCategories() {
        val card = newApp().database.get("3-01")!!
        assertTrue(cardMatches(card, "gasoline fire", "All"))
        assertTrue(cardMatches(card, "UN 1203", "UN Number"))
        assertTrue(cardMatches(card, "class 3", "ADR Class"))
        assertFalse(cardMatches(card, "2", "ADR Class"))
        assertTrue(cardMatches(card, "3 3", "Hazard Number"))
        assertTrue(cardMatches(card, "301", "ERICard Number"))
        assertFalse(cardMatches(card, "water", "All"))
    }

    @Test
    fun collectingAndStatistics() {
        val app = newApp()
        app.collection.setEntry(Kind.ERICARDS, "2-10", "2026-02-03")
        app.collection.setEntry(Kind.SUBSTANCES, "UN 1203", "2026-02-04")
        assertTrue(app.isCollected(Kind.ERICARDS, "2-10"))
        val stats = app.stats(Kind.ERICARDS)
        assertEquals(1, stats.collected)
        assertEquals(3, stats.total)
        val classes = stats.classes.associateBy { it.adrClass }
        assertEquals(1, classes.getValue("2.1").collected)
        assertEquals(2, classes.getValue("2.1").total)
        // everything survives a restart
        val again = CollectionStore(app.files)
        assertEquals("2026-02-03", again.getDate("2-10"))
        assertTrue(again.substances.isChecked("1203"))
    }

    @Test
    fun collectionImportMergesByEarlierDate() {
        val app = newApp()
        app.collection.setEntry(Kind.ERICARDS, "2-10", "2026-02-03")
        val file = Json.parse("""{"checked": {"2-10": {"date": "2026-01-01"}, "3-01": {"date": "2026-03-01"}, "bad": {"date": "x"}}}""")
        val imported = app.collection.parseImport(file)
        assertEquals(1, imported.skipped)
        val plan = app.collection.mergePlan(imported, ImportMode.KEEP_EARLIER)
        assertEquals(CollectionStats(added = 1, changed = 1, unchanged = 0, removed = 0), plan.stats.getValue(Kind.ERICARDS))
        app.collection.apply(plan)
        assertEquals("2026-01-01", app.collection.getDate("2-10"))
        assertNull(plan.results[Kind.SUBSTANCES]) // section missing in the file: left alone
    }

    @Test
    fun databaseUpdateMergeAndReplace() {
        val app = newApp()
        val update = Json.parse("""{"cards": [{"ericard_number": "2-10", "substance_names": ["Butane (new)"]}, {"ericard_number": "9-99", "substance_names": ["X"]}]}""")
        val merge = app.database.plan(update, replace = false)
        assertEquals(ImportStats(added = 1, updated = 1, unchanged = 0, removed = 0), merge.stats)
        val replace = app.database.plan(update, replace = true)
        assertEquals(2, replace.stats.removed)
        app.database.apply(merge)
        assertEquals(4, CardDatabase(app.files).cards.size)
    }

    @Test
    fun referenceTextsFallBackToEnglishAndCanBeUpdated() {
        val app = newApp()
        val ref = app.reference
        assertEquals("Leicht entzuendbar.", ref.statements("H", "de").single().text)
        assertEquals("Keep away from heat.", ref.statements("P", "de").single().text) // falls back to English
        assertEquals("Flame", ref.pictogramName("GHS02", "en"))

        val update = Json.parse("""{"languages": {"it": "Italiano"}, "hazard_statements": {"H225": {"text": {"it": "Liquido."}}}}""")
        val plan = ref.plan(update, replace = false)
        assertNotNull(plan)
        ref.apply(plan!!)
        assertEquals("Liquido.", ref.statements("H", "it").single().text)
        assertEquals("Highly flammable liquid.", ref.statements("H", "en").single().text) // kept
        assertNull(ref.plan(Json.parse("""{"unrelated": 1}"""), replace = false))
    }

    /** A backup folder in memory. */
    private class FakeFolder(var failing: Boolean = false) : BackupFolder {
        val files = LinkedHashMap<String, String>()
        override fun listNames() = files.keys.toList()
        override fun write(name: String, content: String) {
            if (failing) throw java.io.IOException("Folder is not available")
            files[name] = content
        }
        override fun delete(name: String) { files.remove(name) }
    }

    private fun day(n: Int) = LocalDateTime.of(2026, 3, n, 12, 0)

    @Test
    fun backupIsWrittenOnlyWhenSomethingChanged() {
        val app = newApp()
        val folder = FakeFolder()
        assertEquals(BackupResult.NOTHING_TO_BACK_UP, app.backup.run(folder, now = day(1)).result)
        assertTrue(folder.files.isEmpty())

        app.collection.setEntry(Kind.ERICARDS, "2-10", "2026-02-03")
        val first = app.backup.run(folder, now = day(1))
        assertEquals(BackupResult.BACKED_UP, first.result)
        assertEquals("ericards_collection_backup_20260301.json", first.fileName)
        assertEquals("Last backup: 2026-03-01 12:00", app.backup.statusText())

        assertEquals(BackupResult.UNCHANGED, app.backup.run(folder, now = day(2)).result)
        assertEquals(1, folder.files.size)

        app.collection.setEntry(Kind.SUBSTANCES, "1203", "2026-02-04")
        assertEquals(BackupResult.BACKED_UP, app.backup.run(folder, now = day(2)).result)
        assertEquals(2, folder.files.size)
        // "Back up now" writes even if nothing changed
        assertEquals(BackupResult.BACKED_UP, app.backup.run(folder, force = true, now = day(2)).result)
        assertEquals(2, folder.files.size) // same day: the file is replaced
    }

    @Test
    fun backupFilesCanBeImportedAgain() {
        val app = newApp()
        app.collection.setEntry(Kind.ERICARDS, "3-01", "2026-01-05")
        app.collection.setEntry(Kind.SUBSTANCES, "1203", "2026-01-06")
        val folder = FakeFolder()
        app.backup.run(folder, now = day(1))

        val fresh = newApp() // a new installation
        val imported = fresh.collection.parseImport(Json.parse(folder.files.values.single()))
        fresh.collection.apply(fresh.collection.mergePlan(imported, ImportMode.REPLACE))
        assertEquals("2026-01-05", fresh.collection.getDate("3-01"))
        assertTrue(fresh.collection.substances.isChecked("1203"))
    }

    @Test
    fun onlyTheNewestBackupsAreKeptAndOtherFilesStay() {
        val app = newApp()
        val folder = FakeFolder()
        folder.files["my_notes.txt"] = "keep me"
        folder.files["ericards_collection_20260101.json"] = "a manual export" // not an automatic backup
        for (n in 1..20) {
            app.collection.setEntry(Kind.ERICARDS, "2-10", "2026-02-%02d".format(n))
            app.backup.run(folder, now = day(n))
        }
        val backups = folder.files.keys.filter { it.startsWith(BackupManager.FILE_PREFIX) }
        assertEquals(BackupManager.KEEP, backups.size)
        assertEquals("ericards_collection_backup_20260320.json", backups.maxOrNull())
        assertEquals("ericards_collection_backup_20260307.json", backups.minOrNull())
        assertTrue("my_notes.txt" in folder.files && "ericards_collection_20260101.json" in folder.files)
    }

    @Test
    fun aFailedBackupIsRememberedAndRetried() {
        val app = newApp()
        app.collection.setEntry(Kind.ERICARDS, "2-10", "2026-02-03")
        val folder = FakeFolder(failing = true)
        val failed = app.backup.run(folder, now = day(1))
        assertEquals(BackupResult.FAILED, failed.result)
        assertEquals("The last attempt failed: Folder is not available", app.backup.statusText())

        folder.failing = false
        // nothing changed, but the failure means the next run tries again
        assertEquals(BackupResult.BACKED_UP, app.backup.run(folder, now = day(2)).result)
        assertEquals("", app.backup.lastError)
    }

    @Test
    fun backupSettingsSurviveARestart() {
        val app = newApp()
        app.settings.set(SettingsStore.BACKUP_FOLDER, "content://tree/x")
        app.settings.set(SettingsStore.BACKUP_ENABLED, true)
        val again = AppData(app.files) { reference }
        assertTrue(again.backup.enabled)
        assertEquals("content://tree/x", again.backup.folder)
    }

    @Test
    fun damagedFilesAreKeptAndReported() {
        val dir = Files.createTempDirectory("ericards-test").toFile()
        val files = AppFiles(dir)
        files.collectionFile.writeText("{ not json")
        val store = CollectionStore(files)
        assertNotNull(store.loadWarning)
        assertTrue(files.backupDir.listFiles()!!.any { it.name.contains(".corrupt-") })
        assertTrue(File(dir, "collection.json").exists())
    }
}
