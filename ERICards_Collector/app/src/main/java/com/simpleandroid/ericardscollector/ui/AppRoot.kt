@file:OptIn(ExperimentalMaterial3Api::class)

package com.simpleandroid.ericardscollector.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simpleandroid.ericardscollector.data.Kind
import com.simpleandroid.ericardscollector.ui.components.AppDialogHost
import com.simpleandroid.ericardscollector.ui.components.FlaskIcon
import com.simpleandroid.ericardscollector.ui.components.ProgressStrip
import com.simpleandroid.ericardscollector.ui.screens.CardDetailScreen
import com.simpleandroid.ericardscollector.ui.screens.CollectionListScreen
import com.simpleandroid.ericardscollector.ui.screens.FileActions
import com.simpleandroid.ericardscollector.ui.screens.GlossaryScreen
import com.simpleandroid.ericardscollector.ui.screens.ProfileScreen
import com.simpleandroid.ericardscollector.ui.screens.SettingsScreen
import com.simpleandroid.ericardscollector.ui.screens.StatementsScreen
import com.simpleandroid.ericardscollector.ui.screens.SubstanceDetailScreen
import com.simpleandroid.ericardscollector.ui.screens.TermScreen

private fun tabIcon(tab: TopTab): ImageVector? = when (tab) {
    TopTab.ERICARDS -> Icons.AutoMirrored.Filled.List
    TopTab.SUBSTANCES -> null // drawn by FlaskIcon
    TopTab.PROFILE -> Icons.Filled.Person
    TopTab.GLOSSARY -> Icons.Filled.Info
    TopTab.STATEMENTS -> Icons.Filled.Warning
}

@Composable
private fun TabIcon(tab: TopTab) {
    val vector = tabIcon(tab)
    if (vector != null) Icon(vector, contentDescription = tab.label) else FlaskIcon()
}

/**
 * One entry of the navigation bar or rail. The highlight behind the icon is a slightly rounded
 * rectangle (the standard Material highlight is a full pill).
 */
@Composable
private fun NavItem(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .background(if (selected) colors.secondaryContainer else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { icon() }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun BottomNavigation(vm: AppViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (tab in TopTab.entries) {
                NavItem(vm.tab == tab, tab.label, { vm.openTab(tab) }, Modifier.weight(1f)) { TabIcon(tab) }
            }
        }
    }
}

@Composable
private fun SideNavigation(vm: AppViewModel) {
    Column(
        Modifier
            .width(88.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (tab in TopTab.entries) {
            NavItem(vm.tab == tab, tab.label, { vm.openTab(tab) }, Modifier.fillMaxWidth()) { TabIcon(tab) }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    // --- File pickers (the system file chooser, no storage permission needed) ---
    val pickDatabase = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.onDatabaseFile(it) }
    val pickCollection = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.onCollectionFile(it) }
    val pickReference = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.onReferenceFile(it) }
    val exportCollection = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.onExportTarget(it) }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.onBackupFolder(it) }
    val actions = remember {
        FileActions(
            updateDatabase = { pickDatabase.launch(arrayOf("*/*")) },
            importCollection = { pickCollection.launch(arrayOf("*/*")) },
            exportCollection = { exportCollection.launch(vm.suggestedExportName()) },
            updateReference = { pickReference.launch(arrayOf("*/*")) },
            chooseBackupFolder = { pickBackupFolder.launch(null) },
        )
    }

    // The view model asks for the folder chooser, for example after the first collected item
    LaunchedEffect(vm.requestBackupFolder) {
        if (vm.requestBackupFolder) {
            vm.requestBackupFolder = false
            pickBackupFolder.launch(null)
        }
    }

    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.goBack() }

    val app = vm.data
    if (app == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
            val error = vm.loadError
            if (error == null) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The app could not load its data", style = MaterialTheme.typography.titleMedium)
                    Text(error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }
        AppDialogHost(vm.dialog) { vm.dialog = null }
        return
    }

    val page = vm.stack.lastOrNull()
    val title = when (page) {
        is SubPage.CardDetail -> "ERICard " + (app.database.get(page.key)?.number ?: page.key)
        is SubPage.SubstanceDetail -> "UN ${page.un}"
        is SubPage.Term -> page.term
        SubPage.Settings -> "Settings"
        null -> vm.tab.title
    }
    val stats = remember(vm.revision, vm.progressKind) { app.stats(vm.progressKind) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp // tablets, folded-out phones and phones in landscape get a side rail

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (page != null) {
                            IconButton(onClick = vm::goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        }
                    },
                    actions = {
                        if (page != SubPage.Settings) {
                            IconButton(onClick = vm::openSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                if (page != SubPage.Settings) {
                    Column {
                        if (wide) {
                            ProgressStrip(stats, vm.progressKind, Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                        } else {
                            ProgressStrip(stats, vm.progressKind)
                            BottomNavigation(vm)
                        }
                    }
                }
            },
        ) { padding ->
            Row(
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .imePadding()
                    .fillMaxSize(),
            ) {
                if (wide && page != SubPage.Settings) SideNavigation(vm)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (page) {
                        is SubPage.CardDetail -> CardDetailScreen(vm, page.key)
                        is SubPage.SubstanceDetail -> SubstanceDetailScreen(vm, page.un)
                        is SubPage.Term -> TermScreen(vm, page.term)
                        SubPage.Settings -> SettingsScreen(vm, actions)
                        null -> when (vm.tab) {
                            TopTab.ERICARDS -> CollectionListScreen(vm, Kind.ERICARDS, actions.updateDatabase)
                            TopTab.SUBSTANCES -> CollectionListScreen(vm, Kind.SUBSTANCES, actions.updateDatabase)
                            TopTab.PROFILE -> ProfileScreen(vm)
                            TopTab.GLOSSARY -> GlossaryScreen(vm)
                            TopTab.STATEMENTS -> StatementsScreen(vm)
                        }
                    }
                }
            }
        }
    }
    AppDialogHost(vm.dialog) { vm.dialog = null }
}
