package com.simpleandroid.ericardscollector.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simpleandroid.ericardscollector.data.AppData
import com.simpleandroid.ericardscollector.data.Card
import com.simpleandroid.ericardscollector.data.Kind
import com.simpleandroid.ericardscollector.data.SEARCH_CATEGORIES
import com.simpleandroid.ericardscollector.data.STATUS_FILTERS
import com.simpleandroid.ericardscollector.data.SettingsStore
import com.simpleandroid.ericardscollector.data.SubstanceRecord
import com.simpleandroid.ericardscollector.data.cardMatches
import com.simpleandroid.ericardscollector.data.naturalSortKey
import com.simpleandroid.ericardscollector.data.substanceMatches
import com.simpleandroid.ericardscollector.data.summarizeList
import com.simpleandroid.ericardscollector.ui.AppViewModel
import com.simpleandroid.ericardscollector.ui.components.AppCard
import com.simpleandroid.ericardscollector.ui.components.CountLabel
import com.simpleandroid.ericardscollector.ui.components.DropdownChip
import com.simpleandroid.ericardscollector.ui.components.EmptyDatabase
import com.simpleandroid.ericardscollector.ui.components.SearchField
import com.simpleandroid.ericardscollector.ui.components.SortChip
import com.simpleandroid.ericardscollector.ui.components.collectedContainer
import com.simpleandroid.ericardscollector.ui.theme.AppTheme

/**
 * One line of a list. The sort cells are the texts the desktop table shows in its columns, so
 * sorting behaves the same as clicking a column heading there.
 */
class ListRow(
    val key: String,
    val title: String,
    val body: String,
    val meta: String,
    val collected: Boolean,
    val dateText: String,
    val sortDate: String,
    val cells: Map<String, String>,
)

val CARD_COLUMNS = listOf(
    "collected" to "Collected", "number" to "ERICard", "un" to "UN No.", "name" to "Substance",
    "adr" to "ADR Class", "hazard" to "Hazard No.", "date" to "Collected On",
)

val SUBSTANCE_COLUMNS = listOf(
    "collected" to "Collected", "un" to "UN No.", "name" to "Substance", "adr" to "ADR Class",
    "hazard" to "Hazard No.", "cards" to "ERICard", "date" to "Collected On",
)

private fun joinMeta(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString("  \u00B7  ")

fun cardRow(app: AppData, card: Card, collected: Boolean): ListRow {
    val isoDate = if (collected) app.collection.getDate(card.number) else ""
    val date = if (collected) app.formatDate(isoDate) else ""
    val un = summarizeList(card.unNumbers, 3)
    val name = summarizeList(card.substanceNames, 2, "; ")
    val adr = card.adrClasses.joinToString(", ")
    val hazard = card.hazardNumbers.joinToString(", ")
    return ListRow(
        key = card.key,
        title = card.number,
        body = name,
        meta = joinMeta(if (un.isNotEmpty()) "UN $un" else "", if (adr.isNotEmpty()) "ADR $adr" else "", if (hazard.isNotEmpty()) "Hazard $hazard" else ""),
        collected = collected,
        dateText = date,
        sortDate = isoDate,
        cells = mapOf(
            "collected" to if (collected) "\u2714" else "", "number" to card.number, "un" to un, "name" to name,
            "adr" to adr, "hazard" to hazard, "date" to date,
        ),
    )
}

fun substanceRow(app: AppData, record: SubstanceRecord, collected: Boolean): ListRow {
    val isoDate = if (collected) app.collection.getDate(record.un) else ""
    val date = if (collected) app.formatDate(isoDate) else ""
    val name = summarizeList(record.names, 2, "; ")
    val adr = record.adrClasses.joinToString(", ")
    val hazard = summarizeList(record.hazardNumbers, 3)
    val cards = summarizeList(record.cards, 2)
    return ListRow(
        key = record.un,
        title = "UN ${record.un}",
        body = name,
        meta = joinMeta(if (adr.isNotEmpty()) "ADR $adr" else "", if (hazard.isNotEmpty()) "Hazard $hazard" else "", if (cards.isNotEmpty()) "ERICard $cards" else ""),
        collected = collected,
        dateText = date,
        sortDate = isoDate,
        cells = mapOf(
            "collected" to if (collected) "\u2714" else "", "un" to record.un, "name" to name, "adr" to adr,
            "hazard" to hazard, "cards" to cards, "date" to date,
        ),
    )
}

/** Sorts like a column heading of the desktop table: natural order of the cell text (the date by its ISO value). */
fun sortRows(rows: List<ListRow>, column: String?, descending: Boolean): List<ListRow> {
    if (column == null) return rows
    val keyed = rows.map { it to naturalSortKey(if (column == "date") it.sortDate else it.cells[column] ?: "") }
    val sorted = if (descending) keyed.sortedWith(compareByDescending { it.second }) else keyed.sortedWith(compareBy { it.second })
    return sorted.map { it.first }
}

fun statusAllows(status: String, collected: Boolean): Boolean =
    !((status == "Collected" && !collected) || (status == "Missing" && collected))

/** Returns the rows to show and the total number of records. */
fun buildRows(app: AppData, kind: Kind, query: String, category: String, status: String, sortColumn: String?, descending: Boolean): Pair<List<ListRow>, Int> {
    val section = app.collection.section(kind)
    val rows = ArrayList<ListRow>()
    val total: Int
    if (kind == Kind.SUBSTANCES) {
        val records = app.database.sortedSubstances()
        total = records.size
        for (record in records) {
            val collected = section.isChecked(record.un)
            if (!statusAllows(status, collected) || !substanceMatches(record, query, category)) continue
            rows.add(substanceRow(app, record, collected))
        }
    } else {
        val cards = app.database.sortedCards()
        total = cards.size
        for (card in cards) {
            val collected = section.isChecked(card.number)
            if (!statusAllows(status, collected) || !cardMatches(card, query, category)) continue
            rows.add(cardRow(app, card, collected))
        }
    }
    return sortRows(rows, sortColumn, descending) to total
}

/** The ERICards page and the Substances page: search, filter, sort and collect. */
@Composable
fun CollectionListScreen(vm: AppViewModel, kind: Kind, onLoadDatabase: () -> Unit) {
    val app = vm.data ?: return
    val state = if (kind == Kind.SUBSTANCES) vm.substancesList else vm.cardsList
    val revision = vm.revision // reading it makes this page refresh after every change

    if (!app.hasDatabase) {
        EmptyDatabase(onLoad = onLoadDatabase, modifier = Modifier.fillMaxSize())
        return
    }

    val result = remember(revision, state.query, state.category, state.status, state.sortColumn, state.sortDescending) {
        buildRows(app, kind, state.query, state.category, state.status, state.sortColumn, state.sortDescending)
    }
    val rows = result.first
    val noun = if (kind == Kind.SUBSTANCES) "substances" else "ERICards"
    val quickCollect = app.settings.getBoolean(SettingsStore.QUICK_COLLECT)
    val open: (ListRow) -> Unit = { row -> if (kind == Kind.SUBSTANCES) vm.openSubstance(row.key) else vm.openCard(row.key) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(
                value = state.query,
                onValueChange = { state.query = it },
                label = "Search",
                // Enter with exactly one result opens it
                onSearch = { if (rows.size == 1) open(rows[0]) },
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DropdownChip(
                    label = "Category", value = state.category, options = SEARCH_CATEGORIES.map { it to it },
                    onSelect = { vm.setCategory(kind, it) },
                )
                DropdownChip(
                    label = "Show", value = state.status, options = STATUS_FILTERS.map { it to it },
                    onSelect = { state.status = it },
                )
                SortChip(
                    columns = if (kind == Kind.SUBSTANCES) SUBSTANCE_COLUMNS else CARD_COLUMNS,
                    sortColumn = state.sortColumn, descending = state.sortDescending, onSort = { state.sortBy(it) },
                )
                TextButton(onClick = { state.clear() }) { Text("Clear") }
            }
            CountLabel("Showing ${rows.size} of ${result.second} $noun")
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = state.listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                ListRowCard(row, quickCollect, onOpen = { open(row) }, onToggle = { vm.toggleCollected(row.key, kind) })
            }
        }
    }
}

/** One result. Tapping opens it; the check box collects it directly (Quick Collect). */
@Composable
fun ListRowCard(row: ListRow, quickCollect: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth(), onClick = onOpen, container = collectedContainer(row.collected)) {
        Row(
            Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = if (quickCollect) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (row.body.isNotEmpty()) {
                    Text(row.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (row.meta.isNotEmpty()) {
                    Text(row.meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (row.collected) {
                    Text("Collected ${row.dateText}", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.go)
                }
            }
            if (quickCollect) {
                Checkbox(
                    checked = row.collected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = AppTheme.colors.go),
                )
            } else if (row.collected) {
                Icon(Icons.Filled.Check, contentDescription = "Collected", tint = AppTheme.colors.go)
            }
        }
    }
}
