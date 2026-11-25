package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.editor.CodeSyntaxVisualTransformation
import com.example.editor.SyntaxPalette
import com.example.model.DiagnosticSeverity
import com.example.model.EditorTab
import com.example.model.FileNode
import com.example.model.IdeSettings
import com.example.ui.IdeUiState
import com.example.ui.theme.GlowCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LanguageBadge
import com.example.ui.theme.NasjeBgDeep
import com.example.ui.theme.NasjeBgPrimary
import com.example.ui.theme.NasjeBlue
import com.example.ui.theme.NasjeBorderSubtle
import com.example.ui.theme.NasjeCyan
import com.example.ui.theme.NasjeEditorBg
import com.example.ui.theme.NasjeErrorRed
import com.example.ui.theme.NasjeGutterBg
import com.example.ui.theme.NasjeRunGreen
import com.example.ui.theme.NasjeSurface
import com.example.ui.theme.NasjeSurfaceElevated
import com.example.ui.theme.NasjeTextMuted
import com.example.ui.theme.NasjeTextSecondary
import com.example.ui.theme.NasjeViolet
import com.example.ui.theme.NasjeWarningAmber
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    state: IdeUiState,
    settings: IdeSettings,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onContentChange: (String, Int) -> Unit,
    onConsumeLineJump: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onRunBuild: () -> Unit,
    onReplaceText: (search: String, replacement: String, replaceAll: Boolean) -> Unit,
    onOpenFile: (FileNode) -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onDeleteNode: (String) -> Unit,
    onCopyOrMove: (String, String, Boolean) -> Unit,
    onPasteInto: (String) -> Unit,
    onImportUri: (android.net.Uri, String) -> Unit,
    onRefreshTree: () -> Unit,
    onOpenModulesConfig: () -> Unit,
    onOpenErrors: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val project = state.activeProject
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showMainMenu by remember { mutableStateOf(false) }
    var showExtraMenu by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val activeTab: EditorTab? = state.openTabs.getOrNull(state.activeTabIndex)
    val activeFileDiags = remember(state.liveDiagnostics, activeTab?.filePath) {
        if (activeTab == null) emptyList()
        else state.liveDiagnostics.filter { it.filePath == activeTab.filePath }
    }
    val totalErrors = remember(state.liveDiagnostics) {
        state.liveDiagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    }
    val totalWarnings = remember(state.liveDiagnostics) {
        state.liveDiagnostics.count { it.severity == DiagnosticSeverity.WARNING }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            if (project != null) {
                FileExplorerPanel(
                    projectName = project.name,
                    projectRootPath = project.rootPath,
                    rootNode = state.fileTree,
                    activeFilePath = activeTab?.filePath,
                    clipboardOp = state.clipboardOp,
                    onOpenFile = { node ->
                        onOpenFile(node)
                        scope.launch { drawerState.close() }
                    },
                    onCreateFile = onCreateFile,
                    onCreateFolder = onCreateFolder,
                    onRenameNode = onRenameNode,
                    onDeleteNode = onDeleteNode,
                    onCopyOrMove = onCopyOrMove,
                    onPasteInto = onPasteInto,
                    onImportUri = onImportUri,
                    onRefresh = onRefreshTree,
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = if (settings.darkTheme) NasjeBgDeep else MaterialTheme.colorScheme.background,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (settings.darkTheme) NasjeBgPrimary else MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                ) {
                    // Editor Top Action Bar: Menu, Explorateur, RUN (Green), Save, Extra Menu
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { showMainMenu = true },
                                modifier = Modifier.testTag("editor_menu_button")
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu principal", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            DropdownMenu(
                                expanded = showMainMenu,
                                onDismissRequest = { showMainMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Accueil (Mes Projets)") },
                                    leadingIcon = { Icon(Icons.Filled.Home, null) },
                                    onClick = { showMainMenu = false; onNavigateHome() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Modules et Configuration") },
                                    leadingIcon = { Icon(Icons.Filled.Tune, null) },
                                    onClick = { showMainMenu = false; onOpenModulesConfig() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Erreurs & Sortie") },
                                    leadingIcon = { Icon(Icons.Filled.Error, null, tint = NasjeErrorRed) },
                                    onClick = { showMainMenu = false; onOpenErrors() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Paramètres") },
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    onClick = { showMainMenu = false; onOpenSettings() }
                                )
                            }
                        }

                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("editor_explorer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderCopy,
                                contentDescription = "Explorateur de fichiers",
                                tint = NasjeCyan
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = project?.name ?: "NASJE IDE",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeTab?.relativePath ?: "Aucun fichier ouvert",
                                style = MaterialTheme.typography.labelSmall,
                                color = NasjeTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Green RUN Button
                        Button(
                            onClick = onRunBuild,
                            enabled = project != null && !state.isCompiling,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NasjeRunGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("editor_run_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Compiler et exécuter", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RUN", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Save Button
                        IconButton(
                            onClick = onSave,
                            enabled = activeTab != null,
                            modifier = Modifier.testTag("editor_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Sauvegarder",
                                tint = if (activeTab?.isDirty == true) NasjeCyan else NasjeTextSecondary
                            )
                        }

                        // Extra Menu
                        Box {
                            IconButton(
                                onClick = { showExtraMenu = true },
                                modifier = Modifier.testTag("editor_more_button")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu supplémentaire")
                            }
                            DropdownMenu(
                                expanded = showExtraMenu,
                                onDismissRequest = { showExtraMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Annuler (Undo)") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) },
                                    enabled = (activeTab?.undoStack?.isNotEmpty() == true),
                                    onClick = { showExtraMenu = false; onUndo() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rétablir (Redo)") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Redo, null) },
                                    enabled = (activeTab?.redoStack?.isNotEmpty() == true),
                                    onClick = { showExtraMenu = false; onRedo() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rechercher / Remplacer") },
                                    leadingIcon = { Icon(Icons.Filled.FindReplace, null) },
                                    onClick = { showExtraMenu = false; showFindReplace = !showFindReplace }
                                )
                                DropdownMenuItem(
                                    text = { Text("Modules et Configuration") },
                                    leadingIcon = { Icon(Icons.Filled.Build, null) },
                                    onClick = { showExtraMenu = false; onOpenModulesConfig() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Erreurs & Sortie") },
                                    leadingIcon = { Icon(Icons.Filled.Error, null, tint = NasjeErrorRed) },
                                    onClick = { showExtraMenu = false; onOpenErrors() }
                                )
                            }
                        }
                    }

                    // Multi-tab Bar
                    if (state.openTabs.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NasjeSurface)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(state.openTabs) { index, tab ->
                                val selected = index == state.activeTabIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) NasjeSurfaceElevated else Color.Transparent,
                                    border = if (selected) BorderStroke(1.dp, NasjeBlue.copy(alpha = 0.6f)) else null,
                                    modifier = Modifier
                                        .clickable { onSelectTab(index) }
                                        .testTag("editor_tab_$index")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(tab.language.accentColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (tab.isDirty) "${tab.fileName} •" else tab.fileName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) Color.White else NasjeTextSecondary,
                                            maxLines = 1
                                        )
                                        IconButton(
                                            onClick = { onCloseTab(index) },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Fermer l'onglet ${tab.fileName}",
                                                tint = NasjeTextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Find & Replace Bar
                    AnimatedVisibility(visible = showFindReplace) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NasjeSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = findQuery,
                                    onValueChange = { findQuery = it },
                                    placeholder = { Text("Rechercher...") },
                                    leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { showFindReplace = false }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Fermer recherche")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = replaceQuery,
                                    onValueChange = { replaceQuery = it },
                                    placeholder = { Text("Remplacer par...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onReplaceText(findQuery, replaceQuery, false) }) {
                                    Text("Remplacer")
                                }
                                TextButton(onClick = { onReplaceText(findQuery, replaceQuery, true) }) {
                                    Text("Tout")
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = NasjeBorderSubtle)
                }
            },
            bottomBar = {
                // Bottom Status Bar: Line, Cursor Position, Encoding, Language + Diagnostic badge
                val (lineNum, colNum) = remember(activeTab?.content, activeTab?.cursorIndex) {
                    if (activeTab == null) 1 to 1 else computeLineAndCol(activeTab.content, activeTab.cursorIndex)
                }
                Surface(
                    color = if (settings.darkTheme) NasjeBgPrimary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, NasjeBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Ln $lineNum, Col $colNum",
                                style = MaterialTheme.typography.labelSmall,
                                color = NasjeCyan,
                                modifier = Modifier.testTag("status_cursor_pos")
                            )
                            Text(
                                text = "UTF-8",
                                style = MaterialTheme.typography.labelSmall,
                                color = NasjeTextSecondary
                            )
                            if (activeTab != null) {
                                LanguageBadge(
                                    label = activeTab.language.label,
                                    accentColor = activeTab.language.accentColor
                                )
                            }
                        }

                        // Diagnostic chip -> opens Errors screen
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenErrors)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("editor_diagnostics_chip"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = "Erreurs",
                                tint = if (totalErrors > 0) NasjeErrorRed else NasjeTextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$totalErrors",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (totalErrors > 0) NasjeErrorRed else NasjeTextMuted
                            )
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Avertissements",
                                tint = if (totalWarnings > 0) NasjeWarningAmber else NasjeTextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$totalWarnings",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (totalWarnings > 0) NasjeWarningAmber else NasjeTextMuted
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (settings.darkTheme) NasjeEditorBg else MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                if (activeTab == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderCopy,
                            contentDescription = null,
                            tint = NasjeViolet,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aucun fichier ouvert",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ouvrez l'explorateur de fichiers pour sélectionner ou créer un fichier source.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NasjeTextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = { scope.launch { drawerState.open() } }) {
                            Text("Ouvrir l'explorateur")
                        }
                    }
                } else {
                    CodeEditorCanvas(
                        tab = activeTab,
                        tabIndex = state.activeTabIndex,
                        settings = settings,
                        diagnosticsForFile = activeFileDiags,
                        searchQuery = if (showFindReplace) findQuery else "",
                        onContentChange = onContentChange,
                        onConsumeLineJump = onConsumeLineJump
                    )
                }
            }
        }
    }

    // Compilation Progress Overlay
    if (state.isCompiling) {
        Dialog(onDismissRequest = {}) {
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NasjeSurfaceElevated
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = NasjeRunGreen, modifier = Modifier.size(36.dp))
                    Column {
                        Text(
                            text = "Compilation en cours...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.compileStatusMessage.ifEmpty { "Préparation des fichiers..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = NasjeCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeEditorCanvas(
    tab: EditorTab,
    tabIndex: Int,
    settings: IdeSettings,
    diagnosticsForFile: List<com.example.model.DiagnosticItem>,
    searchQuery: String,
    onContentChange: (String, Int) -> Unit,
    onConsumeLineJump: (Int) -> Unit
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    var textFieldValue by remember(tab.filePath) {
        mutableStateOf(
            TextFieldValue(
                text = tab.content,
                selection = TextRange(tab.cursorIndex.coerceIn(0, tab.content.length))
            )
        )
    }

    // Sync external content updates (Undo, Redo, Replace, Disk reload)
    LaunchedEffect(tab.content, tab.pendingLineJump) {
        if (tab.content != textFieldValue.text || tab.pendingLineJump != null) {
            val targetSel = tab.cursorIndex.coerceIn(0, tab.content.length)
            textFieldValue = TextFieldValue(
                text = tab.content,
                selection = TextRange(targetSel)
            )
        }
        tab.pendingLineJump?.let { targetLine ->
            val approxLinePx = (settings.editorFontSizeSp * 3).coerceAtLeast(36)
            verticalScroll.animateScrollTo(((targetLine - 3).coerceAtLeast(0)) * approxLinePx)
            onConsumeLineJump(tabIndex)
        }
    }

    val fontFamily = when (settings.editorFontFamily) {
        "Monospace" -> FontFamily.Monospace
        "Sans Serif" -> FontFamily.SansSerif
        else -> JetBrainsMonoFontFamily
    }
    val palette = remember(settings.syntaxTheme, settings.darkTheme) {
        SyntaxPalette.forPreset(settings.syntaxTheme, settings.darkTheme)
    }
    val visualTransform = remember(tab.language, palette, searchQuery) {
        CodeSyntaxVisualTransformation(tab.language, palette, searchQuery)
    }

    val lines = remember(textFieldValue.text) { textFieldValue.text.split('\n') }
    val errorLines = remember(diagnosticsForFile) {
        diagnosticsForFile.associate { it.line to it.severity }
    }
    val lineHeightSp = (settings.editorFontSizeSp + 6).sp
    val codeTextStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = settings.editorFontSizeSp.sp,
        lineHeight = lineHeightSp,
        color = palette.defaultText
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll)
    ) {
        // Line Numbers Gutter
        Column(
            modifier = Modifier
                .background(if (settings.darkTheme) NasjeGutterBg else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            lines.forEachIndexed { idx, _ ->
                val lineNumber = idx + 1
                val severity = errorLines[lineNumber]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(lineHeightSp.value.dp)
                ) {
                    if (severity != null) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (severity == DiagnosticSeverity.ERROR) NasjeErrorRed else NasjeWarningAmber
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "$lineNumber",
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = (settings.editorFontSizeSp - 2).coerceAtLeast(10).sp,
                            lineHeight = lineHeightSp,
                            color = when (severity) {
                                DiagnosticSeverity.ERROR -> NasjeErrorRed
                                DiagnosticSeverity.WARNING -> NasjeWarningAmber
                                else -> NasjeTextMuted
                            }
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(NasjeBorderSubtle)
        )

        // Editable Syntax-Highlighted Code Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScroll)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { incoming ->
                    val processed = applyAutoIndentIfNewline(textFieldValue, incoming)
                    textFieldValue = processed
                    onContentChange(processed.text, processed.selection.start)
                },
                textStyle = codeTextStyle,
                cursorBrush = SolidColor(NasjeCyan),
                visualTransformation = visualTransform,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("code_editor_field")
            )
        }
    }
}

private fun applyAutoIndentIfNewline(old: TextFieldValue, incoming: TextFieldValue): TextFieldValue {
    if (incoming.text.length != old.text.length + 1) return incoming
    val cursor = incoming.selection.start
    if (cursor <= 0 || incoming.text[cursor - 1] != '\n') return incoming

    val beforeNewline = incoming.text.substring(0, cursor - 1)
    val prevLine = beforeNewline.substringAfterLast('\n')
    val baseIndent = prevLine.takeWhile { it == ' ' || it == '\t' }
    val trimmedPrev = prevLine.trimEnd()
    val extraIndent = if (trimmedPrev.endsWith("{") || trimmedPrev.endsWith("(")) "    " else ""
    val indentToInsert = baseIndent + extraIndent
    if (indentToInsert.isEmpty()) return incoming

    val updatedText = StringBuilder(incoming.text).insert(cursor, indentToInsert).toString()
    val newCursor = cursor + indentToInsert.length
    return incoming.copy(text = updatedText, selection = TextRange(newCursor))
}

private fun computeLineAndCol(text: String, cursorIndex: Int): Pair<Int, Int> {
    val safeIdx = cursorIndex.coerceIn(0, text.length)
    var line = 1
    var lastNewline = -1
    for (i in 0 until safeIdx) {
        if (text[i] == '\n') {
            line++
            lastNewline = i
        }
    }
    val col = safeIdx - lastNewline
    return line to col
}
