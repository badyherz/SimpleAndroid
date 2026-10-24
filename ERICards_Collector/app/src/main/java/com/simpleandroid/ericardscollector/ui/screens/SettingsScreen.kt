package com.simpleandroid.ericardscollector.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.simpleandroid.ericardscollector.backup.SafBackupFolder
import com.simpleandroid.ericardscollector.data.SettingsStore
import com.simpleandroid.ericardscollector.ui.AppViewModel

private const val REPOSITORY_URL = "https://github.com/badyherz/SimpleAndroid"

/** What the file pickers (owned by the activity) offer to the settings page. */
class FileActions(
    val updateDatabase: () -> Unit,
    val importCollection: () -> Unit,
    val exportCollection: () -> Unit,
    val updateReference: () -> Unit,
    val chooseBackupFolder: () -> Unit,
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun NoteText(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SettingsScreen(vm: AppViewModel, actions: FileActions) {
    val app = vm.data ?: return
    val revision = vm.revision
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: ""
        } catch (error: Exception) {
            ""
        }
    }

    // Reading the revision keeps the texts below up to date after a change
    val database = app.database
    val databaseInfo = buildString {
        append("ERICards loaded: ${database.cards.size}\nUN numbers: ${database.substances().size}")
        if (database.language.isNotEmpty()) append("\nDatabase language: ${database.language}")
        append("\nLast updated: ${database.lastUpdated.ifEmpty { "-" }}")
        val source = (database.meta["source"] as? String).orEmpty()
        if (source.isNotEmpty()) append("\nSource: $source")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

        SectionTitle("Database")
        ActionRow("Update Database...", databaseInfo, actions.updateDatabase)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        SectionTitle("Collection")
        ActionRow("Import Collection...", "Add a collection file exported from this app or the desktop program", actions.importCollection)
        ActionRow("Export Collection...", "Save your collection as a file, for example as a backup", actions.exportCollection)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        SectionTitle("Backup")
        val backup = app.backup
        ActionRow(
            "Backup Folder",
            if (backup.hasFolder) SafBackupFolder.describe(backup.folder)
            else "Not set. Choose a folder outside the app, for example a new folder in Documents.",
            actions.chooseBackupFolder,
        )
        SwitchRow("Automatic Backup", backup.enabled, vm::setBackupEnabled, enabled = backup.hasFolder)
        ActionRow("Back Up Now", backup.statusText(), vm::backupNow)
        NoteText(
            "Your collection is stored on this phone only, and uninstalling the app deletes it. With a backup folder, " +
                "a copy is saved there whenever you leave the app and something has changed. The newest 14 copies are kept. " +
                "To restore, use Import Collection... and choose one of the files." ,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        SectionTitle("Glossary Texts")
        ActionRow("Glossary Text Language", app.languageName, vm::chooseLanguage)
        ActionRow("Update Reference Texts...", "Load newer glossary texts and H & P statements from a file", actions.updateReference)
        ActionRow("Reset Reference Texts", "Go back to the texts that come with the app", vm::resetReferenceTexts)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        SectionTitle("Behavior")
        SwitchRow("Ask for Date When Marking", app.settings.getBoolean(SettingsStore.ASK_DATE), vm::setAskDate)
        SwitchRow("Quick Collect in Search Results", app.settings.getBoolean(SettingsStore.QUICK_COLLECT), vm::setQuickCollect)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        SectionTitle("About")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("ERICards Collector", style = MaterialTheme.typography.bodyLarge)
            if (version.isNotEmpty()) {
                Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Part of SimpleAndroid", style = MaterialTheme.typography.bodyLarge)
            // Opens the repository in the browser
            Text(
                REPOSITORY_URL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable(onClickLabel = "Open the repository in the browser") {
                        try {
                            uriHandler.openUri(REPOSITORY_URL)
                        } catch (error: Exception) {
                            // no browser installed
                        }
                    }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
