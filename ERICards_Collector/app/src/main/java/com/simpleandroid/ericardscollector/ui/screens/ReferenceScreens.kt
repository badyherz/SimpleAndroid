@file:OptIn(ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simpleandroid.ericardscollector.data.STATEMENT_FILTERS
import com.simpleandroid.ericardscollector.data.StatementRow
import com.simpleandroid.ericardscollector.data.naturalSortKey
import com.simpleandroid.ericardscollector.data.statementMatches
import com.simpleandroid.ericardscollector.ui.AppViewModel
import com.simpleandroid.ericardscollector.ui.components.AppCard
import com.simpleandroid.ericardscollector.ui.components.CountLabel
import com.simpleandroid.ericardscollector.ui.components.DropdownChip
import com.simpleandroid.ericardscollector.ui.components.GhsIcon
import com.simpleandroid.ericardscollector.ui.components.HintText
import com.simpleandroid.ericardscollector.ui.components.SearchField
import com.simpleandroid.ericardscollector.ui.components.SortChip

private val GLOSSARY_COLUMNS = listOf("term" to "Term", "title" to "Meaning")
private val STATEMENT_COLUMNS = listOf("code" to "Code", "category" to "Category", "text" to "Statement", "pictograms" to "Pictograms")

/** Sorts like a column heading: natural order of the cell text. */
private fun <T> sortByCell(items: List<T>, column: String?, descending: Boolean, cell: (T, String) -> String): List<T> {
    if (column == null) return items
    val keyed = items.map { it to naturalSortKey(cell(it, column)) }
    val sorted = if (descending) keyed.sortedWith(compareByDescending { it.second }) else keyed.sortedWith(compareBy { it.second })
    return sorted.map { it.first }
}

// --- Glossary ---
@Composable
fun GlossaryScreen(vm: AppViewModel) {
    val app = vm.data ?: return
    val revision = vm.revision
    val state = vm.glossaryList
    val language = app.language
    val rows = remember(revision, language, state.sortColumn, state.sortDescending) {
        sortByCell(app.reference.glossary(language), state.sortColumn, state.sortDescending) { row, column ->
            if (column == "term") row.term else row.title
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = state.listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HintText(
                    "Tap a term to see what it stands for.\n" +
                        "Texts are shown in ${app.languageName} (change it in Settings > Glossary Text Language).",
                )
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    SortChip(GLOSSARY_COLUMNS, state.sortColumn, state.sortDescending, { state.sortBy(it) })
                }
            }
        }
        items(rows, key = { it.term }) { row ->
            AppCard(Modifier.fillMaxWidth(), onClick = { vm.openTerm(row.term) }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.term, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(row.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// --- H & P Statements ---
@Composable
fun StatementsScreen(vm: AppViewModel) {
    val app = vm.data ?: return
    val revision = vm.revision
    val state = vm.statementsList
    val language = app.language

    // Like the desktop page: whenever the results change, no statement is selected
    LaunchedEffect(state.query, state.status, language) { vm.selectedStatement = null }

    val result = remember(revision, language, state.query, state.status, state.sortColumn, state.sortDescending) {
        val everything = ArrayList<StatementRow>()
        if (state.status == "All" || state.status == "H Statements") everything += app.reference.statements("H", language)
        if (state.status == "All" || state.status == "EUH Statements") everything += app.reference.statements("EUH", language)
        if (state.status == "All" || state.status == "P Statements") everything += app.reference.statements("P", language)
        val matching = everything.filter { statementMatches(it, state.query) }
        val sorted = sortByCell(matching, state.sortColumn, state.sortDescending) { row, column ->
            when (column) {
                "code" -> row.code
                "category" -> row.category
                "text" -> row.text
                else -> row.pictograms.joinToString(", ")
            }
        }
        sorted to everything.size
    }
    val rows = result.first

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(value = state.query, onValueChange = { state.query = it }, label = "Search")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DropdownChip("Show", state.status, STATEMENT_FILTERS.map { it to it }, { state.status = it })
                SortChip(STATEMENT_COLUMNS, state.sortColumn, state.sortDescending, { state.sortBy(it) })
                TextButton(onClick = { state.clear() }) { Text("Clear") }
            }
            CountLabel("Showing ${rows.size} of ${result.second} statements")
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = state.listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                HintText(
                    "Search by code (e.g. H301) or by word (e.g. toxic) in the selected glossary language: ${app.languageName}.\n" +
                        "Tap a statement to see its pictograms.",
                )
            }
            items(rows, key = { it.code }) { row ->
                val selected = vm.selectedStatement == row.code
                AppCard(
                    Modifier.fillMaxWidth(),
                    onClick = { vm.selectedStatement = if (selected) null else row.code },
                    container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(row.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(row.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(row.text, style = MaterialTheme.typography.bodyMedium)
                        if (row.pictograms.isNotEmpty() && !selected) {
                            Text(row.pictograms.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            if (row.pictograms.isEmpty()) {
                                HintText("No pictogram")
                            } else {
                                FlowRow(
                                    Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    for (code in row.pictograms) {
                                        val name = app.reference.pictogramName(code, language)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
                                            GhsIcon(code, 88.dp)
                                            Text(
                                                if (name.isNotEmpty()) "$code\n$name" else code,
                                                style = MaterialTheme.typography.labelSmall,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(top = 2.dp).width(112.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
