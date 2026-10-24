@file:OptIn(ExperimentalMaterial3Api::class)

package com.simpleandroid.ericardscollector.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.simpleandroid.ericardscollector.data.parseIsoDate
import com.simpleandroid.ericardscollector.ui.AppDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Shows the current dialog (message box, confirmation, choice or date picker), if there is one. */
@Composable
fun AppDialogHost(dialog: AppDialog?, onDismiss: () -> Unit) {
    if (dialog == null) return
    key(dialog) {
        when (dialog) {
            is AppDialog.Info -> AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(dialog.title) },
                text = { ScrollableMessage(dialog.message) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            )
            is AppDialog.Confirm -> AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(dialog.title) },
                text = { ScrollableMessage(dialog.message) },
                confirmButton = {
                    TextButton(onClick = {
                        onDismiss()
                        dialog.onConfirm()
                    }) { Text(dialog.confirmText) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
            is AppDialog.Choice -> ChoiceDialog(dialog, onDismiss)
            is AppDialog.AskDate -> DateDialog(dialog, onDismiss)
        }
    }
}

@Composable
private fun ScrollableMessage(message: String) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(message)
    }
}

@Composable
private fun ChoiceDialog(dialog: AppDialog.Choice, onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(dialog.defaultIndex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (dialog.message.isNotEmpty()) {
                    Text(dialog.message)
                    Spacer(Modifier.height(8.dp))
                }
                dialog.options.forEachIndexed { index, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == index, onClick = { selected = index }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(option, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                dialog.onChoice(selected)
            }) { Text(dialog.confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DateDialog(dialog: AppDialog.AskDate, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val initial = parseIsoDate(dialog.initialIso) ?: today
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            // The date cannot be in the future
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val iso = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        onDismiss()
                        dialog.onDate(iso)
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column {
            Text(dialog.message, modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp))
            DatePicker(state = state)
        }
    }
}
