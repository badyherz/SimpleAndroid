@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.simpleandroid.ericardscollector.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simpleandroid.ericardscollector.data.Card
import com.simpleandroid.ericardscollector.data.Kind
import com.simpleandroid.ericardscollector.data.SubstanceRow
import com.simpleandroid.ericardscollector.data.naturalSortKey
import com.simpleandroid.ericardscollector.ui.AppViewModel
import com.simpleandroid.ericardscollector.ui.components.AdrIcon
import com.simpleandroid.ericardscollector.ui.components.AppCard
import com.simpleandroid.ericardscollector.ui.components.BulletsText
import com.simpleandroid.ericardscollector.ui.components.CountLabel
import com.simpleandroid.ericardscollector.ui.components.FieldBlock
import com.simpleandroid.ericardscollector.ui.components.ParagraphsText
import com.simpleandroid.ericardscollector.ui.components.SearchField
import com.simpleandroid.ericardscollector.ui.components.SectionsText
import com.simpleandroid.ericardscollector.ui.components.SortChip
import com.simpleandroid.ericardscollector.ui.components.StatusBox
import com.simpleandroid.ericardscollector.ui.components.collectedContainer
import com.simpleandroid.ericardscollector.ui.components.linesWithMeaning
import com.simpleandroid.ericardscollector.ui.theme.AppTheme

private val PAGE_PADDING = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)

/** The "ADR Hazard Labels" box shared by the ERICard and substance pages. */
@Composable
private fun IconPanel(adrClasses: List<String>) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ADR Hazard Labels", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (adrClasses.isEmpty()) {
                Text("-")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (adrClass in adrClasses) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AdrIcon(adrClass, 88.dp)
                            Text("Class $adrClass", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(selected = selected == index, onClick = { onSelect(index) }, label = { Text(label) })
        }
    }
}

private val CARD_TAB_COLUMNS = listOf("un" to "UN No.", "name" to "Substance", "hin" to "Hazard No.")

private fun substanceCell(row: SubstanceRow, column: String) = when (column) {
    "un" -> row.un
    "name" -> row.name
    else -> row.hin
}

// --- ERICard ---
@Composable
fun CardDetailScreen(vm: AppViewModel, key: String) {
    val app = vm.data ?: return
    val revision = vm.revision
    val card: Card? = app.database.get(key)
    if (card == null) {
        // The card is gone, for example after a database update
        LaunchedEffect(key) { vm.goBack() }
        return
    }

    var tab by remember(key) { mutableIntStateOf(0) }
    var filter by remember(key) { mutableStateOf("") }
    var sortColumn by remember(key) { mutableStateOf<String?>(null) }
    var sortDescending by remember(key) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val substanceRows = remember(card) { card.substances() }
    val shownSubstances = remember(card, filter, sortColumn, sortDescending, revision) {
        val tokens = filter.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val filtered = substanceRows.filter { row ->
            val haystack = "${row.un} ${row.name} ${row.hin}".lowercase()
            tokens.all { it in haystack }
        }
        val column = sortColumn
        if (column == null) filtered else {
            val keyed = filtered.map { it to naturalSortKey(substanceCell(it, column)) }
            (if (sortDescending) keyed.sortedWith(compareByDescending { it.second }) else keyed.sortedWith(compareBy { it.second })).map { it.first }
        }
    }

    val collected = app.collection.ericards.isChecked(card.key)
    val sourceUrl = card.sourceUrl()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            StatusBox(
                collected = collected,
                dateText = app.formatDate(app.collectedDate(Kind.ERICARDS, card.key)),
                onToggle = { vm.toggleCollected(card.key, Kind.ERICARDS) },
                onChangeDate = { vm.changeCollectDate(card.key, Kind.ERICARDS) },
            )
        }
        if (!card.verified) {
            item {
                Text(
                    "This entry is marked as not verified in the database.",
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.danger,
                )
            }
        }
        if (sourceUrl.isNotEmpty()) {
            item {
                TextButton(onClick = { try { uriHandler.openUri(sourceUrl) } catch (error: Exception) { /* no browser installed */ } }) {
                    Text("Open Source Page")
                }
            }
        }
        item {
            TabChips(listOf("Overview", "Substances (${substanceRows.size})", "Card Text"), tab) { tab = it }
        }

        when (tab) {
            0 -> {
                item { IconPanel(card.adrClasses) }
                item {
                    val unCount = substanceRows.map { it.un }.filter { it.isNotEmpty() }.toSet().size
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            FieldBlock("ADR Class(es)") { Text(linesWithMeaning(card.adrClasses, card.classNames())) }
                            FieldBlock("Label Combinations") { Text(card.adrLabels.joinToString("    |    ").ifEmpty { "-" }) }
                            FieldBlock("Hazard Number(s)") { Text(linesWithMeaning(card.hazardNumbers, card.hazardMeanings())) }
                            FieldBlock("Substances") {
                                Text("${substanceRows.size} substance entries, $unCount UN numbers (see the Substances tab)")
                            }
                            FieldBlock("Description") { ParagraphsText(card.description) }
                            FieldBlock("Known Hazards") { BulletsText(card.knownHazards) }
                        }
                    }
                }
            }
            1 -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SearchField(value = filter, onValueChange = { filter = it }, label = "Filter")
                        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            SortChip(
                                columns = CARD_TAB_COLUMNS, sortColumn = sortColumn, descending = sortDescending,
                                onSort = { column ->
                                    if (sortColumn == column) sortDescending = !sortDescending else { sortColumn = column; sortDescending = false }
                                },
                            )
                        }
                        CountLabel("Showing ${shownSubstances.size} of ${substanceRows.size}")
                    }
                }
                itemsIndexed(shownSubstances) { _, row ->
                    val rowCollected = row.un.isNotEmpty() && app.collection.substances.isChecked(row.un)
                    // Tapping a substance opens its UN number page
                    val canOpen = row.un.isNotEmpty() && app.database.getSubstance(row.un) != null
                    AppCard(
                        Modifier.fillMaxWidth(),
                        onClick = if (canOpen) ({ vm.openSubstance(row.un) }) else null,
                        container = collectedContainer(rowCollected),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (row.un.isNotEmpty()) "UN ${row.un}" else "-",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                )
                                Text(row.name, style = MaterialTheme.typography.bodyMedium)
                                if (row.hin.isNotEmpty()) {
                                    Text("Hazard ${row.hin}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (rowCollected) Icon(Icons.Filled.Check, contentDescription = "Collected", tint = AppTheme.colors.go)
                        }
                    }
                }
            }
            else -> {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) { SectionsText(card.sections()) }
                    }
                }
            }
        }
    }
}

// --- UN number ---
private val SUBSTANCE_PAGE_CARD_COLUMNS = listOf(
    "collected" to "Collected", "number" to "ERICard", "adr" to "ADR Class", "hazard" to "Hazard No.",
)

private class CardRowData(val number: String, val key: String, val collected: Boolean, val adr: String, val hazard: String)

@Composable
fun SubstanceDetailScreen(vm: AppViewModel, un: String) {
    val app = vm.data ?: return
    val revision = vm.revision
    val record = app.database.getSubstance(un)
    if (record == null) {
        LaunchedEffect(un) { vm.goBack() }
        return
    }

    var sortColumn by remember(un) { mutableStateOf<String?>(null) }
    var sortDescending by remember(un) { mutableStateOf(false) }

    // Class names and hazard number meanings come from the ERICards that list this substance
    val cards = remember(record, revision) { record.cards.mapNotNull { app.database.get(it) } }
    val classNames = remember(cards) {
        val names = LinkedHashMap<String, String>()
        for (card in cards) for ((adrClass, name) in card.classNames()) names.putIfAbsent(adrClass, name)
        names
    }
    val meanings = remember(cards) {
        val result = LinkedHashMap<String, String>()
        for (card in cards) for ((number, meaning) in card.hazardMeanings()) result.putIfAbsent(number, meaning)
        result
    }
    val cardRows = remember(cards, revision, sortColumn, sortDescending) {
        val rows = cards.map { card ->
            val hazards = ArrayList<String>()
            for ((_, hazard, entryNumber) in record.entries) {
                if (entryNumber == card.number && hazard.isNotEmpty() && hazard !in hazards) hazards.add(hazard)
            }
            CardRowData(card.number, card.key, app.collection.ericards.isChecked(card.number), card.adrClasses.joinToString(", "), hazards.joinToString(", "))
        }
        val column = sortColumn
        if (column == null) rows else {
            fun cell(row: CardRowData) = when (column) {
                "collected" -> if (row.collected) "\u2714" else ""
                "number" -> row.number
                "adr" -> row.adr
                else -> row.hazard
            }
            val keyed = rows.map { it to naturalSortKey(cell(it)) }
            (if (sortDescending) keyed.sortedWith(compareByDescending { it.second }) else keyed.sortedWith(compareBy { it.second })).map { it.first }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            StatusBox(
                collected = app.collection.substances.isChecked(record.un),
                dateText = app.formatDate(app.collectedDate(Kind.SUBSTANCES, record.un)),
                onToggle = { vm.toggleCollected(record.un, Kind.SUBSTANCES) },
                onChangeDate = { vm.changeCollectDate(record.un, Kind.SUBSTANCES) },
            )
        }
        item { IconPanel(record.adrClasses) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FieldBlock("Substance Name(s)") { BulletsText(record.names) }
                    FieldBlock("ADR Class(es)") { Text(linesWithMeaning(record.adrClasses, classNames)) }
                    FieldBlock("Hazard Number(s)") { Text(linesWithMeaning(record.hazardNumbers, meanings)) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ERICard(s)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                    SortChip(
                        columns = SUBSTANCE_PAGE_CARD_COLUMNS, sortColumn = sortColumn, descending = sortDescending,
                        onSort = { column ->
                            if (sortColumn == column) sortDescending = !sortDescending else { sortColumn = column; sortDescending = false }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        items(cardRows, key = { it.key }) { row ->
            AppCard(Modifier.fillMaxWidth(), onClick = { vm.openCard(row.key) }, container = collectedContainer(row.collected)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ERICard ${row.number}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val meta = listOf(
                            if (row.adr.isNotEmpty()) "ADR ${row.adr}" else "",
                            if (row.hazard.isNotEmpty()) "Hazard ${row.hazard}" else "",
                        ).filter { it.isNotEmpty() }.joinToString("  \u00B7  ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (row.collected) Icon(Icons.Filled.Check, contentDescription = "Collected", tint = AppTheme.colors.go)
                }
            }
        }
    }
}

// --- Glossary term ---
@Composable
fun TermScreen(vm: AppViewModel, term: String) {
    val app = vm.data ?: return
    val revision = vm.revision
    val language = app.language
    val entry = remember(term, language, revision) { app.reference.glossaryEntry(term, language) }
    if (entry == null) {
        LaunchedEffect(term) { vm.goBack() }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (entry.usedLanguage != language) {
            val usedName = app.reference.languages()[entry.usedLanguage] ?: entry.usedLanguage
            Text(
                "Not available in ${app.languageName}; showing $usedName instead.",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.hint,
            )
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(entry.text.ifEmpty { "-" }, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
