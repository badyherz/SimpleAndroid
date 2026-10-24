@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.simpleandroid.ericardscollector.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.simpleandroid.ericardscollector.data.Kind
import com.simpleandroid.ericardscollector.data.Stats
import com.simpleandroid.ericardscollector.data.leadingLabel
import com.simpleandroid.ericardscollector.data.splitSentences
import com.simpleandroid.ericardscollector.ui.theme.AppTheme

// --- Boxes ---
/** A slightly rounded box with a thin border, optionally clickable. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, border = border) {
            Column(content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = container, border = border) {
            Column(content = content)
        }
    }
}

/** The background of an item that is already collected: a light green tint. */
@Composable
fun collectedContainer(collected: Boolean): Color =
    if (collected) AppTheme.colors.go.copy(alpha = 0.13f).compositeOver(MaterialTheme.colorScheme.surface)
    else MaterialTheme.colorScheme.surface

// --- Text helpers ---
/** A leading "Label:" of a line is shown in bold. */
fun labelled(text: String): AnnotatedString = buildAnnotatedString {
    val label = leadingLabel(text)
    if (label != null) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
        append(text.substring(label.length))
    } else {
        append(text)
    }
}

/** "23  \u2013  Flammable gas" for every value that has a meaning; "-" if there is nothing. */
fun linesWithMeaning(values: List<String>, meanings: Map<String, String>): String =
    values.joinToString("\n") { if (it in meanings) "$it  \u2013  ${meanings.getValue(it)}" else it }.ifEmpty { "-" }

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.hint,
    )
}

@Composable
fun CountLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A bold caption above a value, like the left column of the desktop detail pages. */
@Composable
fun FieldBlock(caption: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(
            caption,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Running text, shown as one paragraph per sentence. */
@Composable
fun ParagraphsText(text: String) {
    val sentences = splitSentences(text).ifEmpty { listOf("-") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (sentence in sentences) Text(labelled(sentence), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BulletItem(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Text("\u2022", modifier = Modifier.width(18.dp), style = MaterialTheme.typography.bodyMedium)
        Text(labelled(text), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BulletsText(items: List<String>) {
    if (items.isEmpty()) {
        Text("-", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (item in items) BulletItem(item)
    }
}

private val SUB_HEADING = Regex("^\\d+\\.\\d+")

/** The sections of an ERICard: bold headings, sub headings (4.1, 4.2, ...) indented, lines as bullets. */
@Composable
fun SectionsText(sections: List<Pair<String, List<String>>>) {
    if (sections.isEmpty()) {
        Text("No detailed card text is available in this database.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((heading, lines) in sections) {
            val isSub = SUB_HEADING.containsMatchIn(heading)
            Text(
                heading,
                modifier = Modifier.padding(start = if (isSub) 12.dp else 0.dp, top = if (isSub) 6.dp else 12.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            for (line in lines) BulletItem(line, Modifier.padding(start = if (isSub) 24.dp else 12.dp))
        }
    }
}

// --- Inputs ---
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focus.clearFocus()
            onSearch()
        }),
        shape = MaterialTheme.shapes.medium,
    )
}

/** A chip that opens a menu: "Category: All", "Show: Collected", ... Options are (id, text). */
@Composable
fun DropdownChip(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("$label: $value") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((id, text) in options) {
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    expanded = false
                    onSelect(id)
                })
            }
        }
    }
}

/** Replaces clicking a column heading: choose a column, choose it again to reverse the order. */
@Composable
fun SortChip(
    columns: List<Pair<String, String>>,
    sortColumn: String?,
    descending: Boolean,
    onSort: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun arrow(id: String) = if (id == sortColumn) (if (descending) " \u25BC" else " \u25B2") else ""
    val current = columns.firstOrNull { it.first == sortColumn }
    DropdownChip(
        label = "Sort",
        value = if (current == null) "Default" else current.second + arrow(current.first),
        options = columns.map { (id, text) -> id to text + arrow(id) },
        onSelect = onSort,
        modifier = modifier,
    )
}

// --- Collected status ---
/** The "Collected on ..." box of the detail pages. */
@Composable
fun StatusBox(
    collected: Boolean,
    dateText: String,
    onToggle: () -> Unit,
    onChangeDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth(), container = collectedContainer(collected)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (collected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = AppTheme.colors.go, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (collected) "Collected on $dateText" else "Not collected yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (collected) AppTheme.colors.go else AppTheme.colors.hint,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChangeDate, enabled = collected, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                    Text("Change Date...", textAlign = TextAlign.Center)
                }
                Button(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (collected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondary,
                        contentColor = if (collected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text(if (collected) "Unmark as Collected" else "Mark as Collected", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Shown on the list pages until a database has been loaded. */
@Composable
fun EmptyDatabase(onLoad: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text("No database loaded", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Load an ERICards database file to start collecting.\nUse the button below or Settings > Update Database...",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.hint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onLoad, shape = MaterialTheme.shapes.small) { Text("Load Database...") }
    }
}

/** The thin progress bar above the navigation: how much of the database is collected. */
@Composable
fun ProgressStrip(stats: Stats, kind: Kind, modifier: Modifier = Modifier) {
    val noun = if (kind == Kind.SUBSTANCES) "Substances" else "ERICards"
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            "$noun: ${stats.collected} / ${stats.total} (${"%.0f".format(java.util.Locale.ROOT, stats.percent)}%)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (stats.total > 0) stats.collected.toFloat() / stats.total else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AppTheme.colors.go,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
