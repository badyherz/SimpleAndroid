@file:OptIn(ExperimentalMaterial3Api::class)

package com.simpleandroid.ericardscollector.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simpleandroid.ericardscollector.data.ClassStat
import com.simpleandroid.ericardscollector.data.Kind
import com.simpleandroid.ericardscollector.data.cardMatches
import com.simpleandroid.ericardscollector.data.naturalSortKey
import com.simpleandroid.ericardscollector.data.substanceMatches
import com.simpleandroid.ericardscollector.ui.AppViewModel
import com.simpleandroid.ericardscollector.ui.components.AppCard
import com.simpleandroid.ericardscollector.ui.components.CountLabel
import com.simpleandroid.ericardscollector.ui.components.HintText
import com.simpleandroid.ericardscollector.ui.components.SearchField
import com.simpleandroid.ericardscollector.ui.components.SortChip
import com.simpleandroid.ericardscollector.ui.theme.AppTheme

private val CLASS_COLUMNS = listOf("adr" to "ADR Class", "collected" to "Collected", "total" to "Total", "percent" to "Percent")
private val PROFILE_CARD_COLUMNS = listOf("number" to "ERICard", "un" to "UN No.", "name" to "Substance", "adr" to "ADR Class", "date" to "Collected On")
private val PROFILE_SUBSTANCE_COLUMNS = listOf("un" to "UN No.", "name" to "Substance", "adr" to "ADR Class", "cards" to "ERICard", "date" to "Collected On")

private fun percentText(percent: Double) = "%.1f%%".format(java.util.Locale.ROOT, percent)

/** Reminds the user that the collection only exists on this phone, or that the backup is not working. */
@Composable
private fun BackupBanner(message: String, failed: Boolean, onOpenSettings: () -> Unit) {
    AppCard(
        Modifier.fillMaxWidth(),
        container = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onOpenSettings, shape = MaterialTheme.shapes.small) {
                Text(if (failed) "Open Settings" else "Set Up Backup")
            }
        }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val app = vm.data ?: return
    val revision = vm.revision
    val kind = vm.profileKind
    val state = vm.profileList
    val noun = if (kind == Kind.SUBSTANCES) "Substances" else "ERICards"

    var classSort by remember { mutableStateOf<String?>(null) }
    var classDescending by remember { mutableStateOf(false) }

    val stats = remember(revision, kind) { app.stats(kind) }
    val classRows = remember(stats, classSort, classDescending) {
        val column = classSort
        if (column == null) stats.classes else {
            fun cell(row: ClassStat) = when (column) {
                "adr" -> row.adrClass
                "collected" -> row.collected.toString()
                "total" -> row.total.toString()
                else -> percentText(row.percent)
            }
            val keyed = stats.classes.map { it to naturalSortKey(cell(it)) }
            (if (classDescending) keyed.sortedWith(compareByDescending { it.second }) else keyed.sortedWith(compareBy { it.second })).map { it.first }
        }
    }

    // Collected items, newest first unless a column is chosen
    val collected = remember(revision, kind, state.query, state.sortColumn, state.sortDescending) {
        val section = app.collection.section(kind)
        val rows = ArrayList<ListRow>()
        var totalCollected = 0
        if (kind == Kind.ERICARDS) {
            for (card in app.database.sortedCards()) {
                if (!section.isChecked(card.number)) continue
                totalCollected++
                if (cardMatches(card, state.query, "All")) rows.add(cardRow(app, card, true))
            }
        } else {
            for (record in app.database.sortedSubstances()) {
                if (!section.isChecked(record.un)) continue
                totalCollected++
                if (substanceMatches(record, state.query, "All")) rows.add(substanceRow(app, record, true))
            }
        }
        val byDate = rows.sortedByDescending { it.sortDate }
        sortRows(if (state.sortColumn == null) byDate else rows, state.sortColumn, state.sortDescending) to totalCollected
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = state.listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val backup = app.backup
        if (backup.hasCollection && !backup.enabled) {
            item {
                BackupBanner("Your collection is stored only on this phone. Uninstalling the app deletes it.", failed = false, onOpenSettings = vm::openSettings)
            }
        } else if (backup.enabled && backup.lastError.isNotEmpty()) {
            item {
                BackupBanner("The last automatic backup failed: ${backup.lastError}", failed = true, onOpenSettings = vm::openSettings)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((mode, label) in listOf(Kind.ERICARDS to "ERICards", Kind.SUBSTANCES to "Substances")) {
                    FilterChip(
                        selected = kind == mode,
                        onClick = {
                            if (kind != mode) {
                                vm.chooseProfileKind(mode)
                                state.sortColumn = null
                                state.sortDescending = false
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
        }
        item { SearchField(value = state.query, onValueChange = { state.query = it }, label = "Search collected") }

        // --- Summary ---
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (app.hasDatabase) "$noun collected: ${stats.collected} / ${stats.total}  (${percentText(stats.percent)})"
                        else "No database loaded. Use Settings > Update Database... to load one.",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = { if (stats.total > 0) stats.collected.toFloat() / stats.total else 0f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = AppTheme.colors.go,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }

        // --- Progress by ADR class ---
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Progress by ADR Class", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SortChip(CLASS_COLUMNS, classSort, classDescending, { column ->
                    if (classSort == column) classDescending = !classDescending else { classSort = column; classDescending = false }
                })
            }
        }
        items(classRows, key = { "class-" + it.adrClass }) { row ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Class ${row.adrClass}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${row.collected} / ${row.total}   ${percentText(row.percent)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(
                        progress = { if (row.total > 0) row.collected.toFloat() / row.total else 0f },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = AppTheme.colors.go,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        item { HintText("An item counts for every ADR class it carries.") }

        // --- Collected list ---
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Collected $noun", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SortChip(
                    columns = if (kind == Kind.ERICARDS) PROFILE_CARD_COLUMNS else PROFILE_SUBSTANCE_COLUMNS,
                    sortColumn = state.sortColumn, descending = state.sortDescending, onSort = { state.sortBy(it) },
                )
            }
        }
        item { CountLabel("Showing ${collected.first.size} of ${collected.second} collected") }
        items(collected.first, key = { "item-" + it.key }) { row ->
            ListRowCard(
                row = row, quickCollect = false,
                onOpen = { if (kind == Kind.SUBSTANCES) vm.openSubstance(row.key) else vm.openCard(row.key) },
                onToggle = {},
            )
        }
    }
}
