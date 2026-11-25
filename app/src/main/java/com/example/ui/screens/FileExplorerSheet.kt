package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FileNode
import com.example.ui.ClipboardFileOp
import com.example.ui.theme.NasjeBgPrimary
import com.example.ui.theme.NasjeBlue
import com.example.ui.theme.NasjeBorderSubtle
import com.example.ui.theme.NasjeCyan
import com.example.ui.theme.NasjeErrorRed
import com.example.ui.theme.NasjeRunGreen
import com.example.ui.theme.NasjeSurface
import com.example.ui.theme.NasjeSurfaceElevated
import com.example.ui.theme.NasjeTextMuted
import com.example.ui.theme.NasjeTextSecondary
import com.example.ui.theme.NasjeViolet

private data class FlattenedTreeItem(
    val node: FileNode,
    val depth: Int
)

@Composable
fun FileExplorerPanel(
    projectName: String,
    projectRootPath: String,
    rootNode: FileNode?,
    activeFilePath: String?,
    clipboardOp: ClipboardFileOp?,
    onOpenFile: (FileNode) -> Unit,
    onCreateFile: (parentDir: String, fileName: String) -> Unit,
    onCreateFolder: (parentDir: String, folderName: String) -> Unit,
    onRenameNode: (path: String, newName: String) -> Unit,
    onDeleteNode: (path: String) -> Unit,
    onCopyOrMove: (path: String, name: String, isMove: Boolean) -> Unit,
    onPasteInto: (destDir: String) -> Unit,
    onImportUri: (uri: android.net.Uri, destDir: String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val expandedPaths = remember(projectRootPath) {
        mutableStateMapOf<String, Boolean>().apply {
            put(projectRootPath, true)
            rootNode?.children?.forEach { if (it.isDirectory) put(it.absolutePath, true) }
        }
    }

    var targetDirForCreate by remember { mutableStateOf<String?>(null) }
    var createModeIsFolder by remember { mutableStateOf(false) }
    var createNameInput by remember { mutableStateOf("") }

    var nodeToRename by remember { mutableStateOf<FileNode?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var nodeToDelete by remember { mutableStateOf<FileNode?>(null) }
    var importDestDir by remember { mutableStateOf(projectRootPath) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportUri(uri, importDestDir)
        }
    }

    val visibleItems = remember(rootNode, searchQuery, expandedPaths.toMap()) {
        if (rootNode == null) {
            emptyList()
        } else if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            val matches = mutableListOf<FlattenedTreeItem>()
            fun searchWalk(node: FileNode) {
                if (node.relativePath.isNotEmpty() && node.name.lowercase().contains(q)) {
                    matches.add(FlattenedTreeItem(node, 0))
                }
                node.children.forEach { searchWalk(it) }
            }
            searchWalk(rootNode)
            matches
        } else {
            val items = mutableListOf<FlattenedTreeItem>()
            fun walk(node: FileNode, depth: Int) {
                if (node.relativePath.isNotEmpty()) {
                    items.add(FlattenedTreeItem(node, depth))
                }
                val isExpanded = if (node.relativePath.isEmpty()) true else (expandedPaths[node.absolutePath] ?: false)
                if (node.isDirectory && isExpanded) {
                    val nextDepth = if (node.relativePath.isEmpty()) 0 else depth + 1
                    node.children.forEach { walk(it, nextDepth) }
                }
            }
            walk(rootNode, 0)
            items
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp),
        color = NasjeBgPrimary,
        border = BorderStroke(1.dp, NasjeBorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
        ) {
            // Explorer Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NasjeSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EXPLORATEUR",
                        style = MaterialTheme.typography.labelMedium,
                        color = NasjeCyan
                    )
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(
                        onClick = {
                            createModeIsFolder = false
                            createNameInput = ""
                            targetDirForCreate = projectRootPath
                        },
                        modifier = Modifier.size(36.dp).testTag("explorer_new_file")
                    ) {
                        Icon(Icons.Filled.NoteAdd, contentDescription = "Nouveau fichier", tint = NasjeCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            createModeIsFolder = true
                            createNameInput = ""
                            targetDirForCreate = projectRootPath
                        },
                        modifier = Modifier.size(36.dp).testTag("explorer_new_folder")
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nouveau dossier", tint = NasjeViolet, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(36.dp).testTag("explorer_refresh")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualiser", tint = NasjeTextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp).testTag("explorer_close")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer l'explorateur", tint = NasjeTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un fichier...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("explorer_search_input")
            )

            // Paste / Import bar if clipboard active
            if (clipboardOp != null) {
                Surface(
                    color = NasjeBlue.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${if (clipboardOp.isMove) "Déplacer" else "Copier"} : ${clipboardOp.fileName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NasjeCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onPasteInto(projectRootPath) }) {
                            Text("Coller racine", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider(color = NasjeBorderSubtle)

            // File Tree
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(visibleItems, key = { it.node.absolutePath }) { item ->
                    val node = item.node
                    val isExpanded = expandedPaths[node.absolutePath] ?: false
                    val isSelected = node.absolutePath == activeFilePath
                    var menuOpen by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) NasjeBlue.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable {
                                if (node.isDirectory) {
                                    expandedPaths[node.absolutePath] = !isExpanded
                                } else {
                                    onOpenFile(node)
                                }
                            }
                            .padding(
                                start = (10 + item.depth * 14).dp,
                                end = 4.dp,
                                top = 6.dp,
                                bottom = 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (node.isDirectory) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                contentDescription = null,
                                tint = NasjeTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                contentDescription = null,
                                tint = NasjeCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(20.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = node.language.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) NasjeCyan else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (searchQuery.isNotBlank()) {
                                Text(
                                    text = node.relativePath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NasjeTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Options fichier",
                                    tint = NasjeTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                if (node.isDirectory) {
                                    DropdownMenuItem(
                                        text = { Text("Nouveau fichier ici") },
                                        leadingIcon = { Icon(Icons.Filled.NoteAdd, null) },
                                        onClick = {
                                            menuOpen = false
                                            createModeIsFolder = false
                                            createNameInput = ""
                                            targetDirForCreate = node.absolutePath
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Nouveau dossier ici") },
                                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                        onClick = {
                                            menuOpen = false
                                            createModeIsFolder = true
                                            createNameInput = ""
                                            targetDirForCreate = node.absolutePath
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Importer un fichier ici") },
                                        leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                                        onClick = {
                                            menuOpen = false
                                            importDestDir = node.absolutePath
                                            openDocLauncher.launch(arrayOf("*/*"))
                                        }
                                    )
                                    if (clipboardOp != null) {
                                        DropdownMenuItem(
                                            text = { Text("Coller '${clipboardOp.fileName}'") },
                                            leadingIcon = { Icon(Icons.Filled.ContentPaste, null, tint = NasjeRunGreen) },
                                            onClick = {
                                                menuOpen = false
                                                onPasteInto(node.absolutePath)
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text("Renommer") },
                                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                    onClick = {
                                        menuOpen = false
                                        renameInput = node.name
                                        nodeToRename = node
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copier") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                    onClick = {
                                        menuOpen = false
                                        onCopyOrMove(node.absolutePath, node.name, false)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Déplacer") },
                                    leadingIcon = { Icon(Icons.Filled.DriveFileMove, null) },
                                    onClick = {
                                        menuOpen = false
                                        onCopyOrMove(node.absolutePath, node.name, true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer", color = NasjeErrorRed) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = NasjeErrorRed) },
                                    onClick = {
                                        menuOpen = false
                                        nodeToDelete = node
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create File or Folder Dialog
    targetDirForCreate?.let { dirPath ->
        AlertDialog(
            onDismissRequest = { targetDirForCreate = null },
            title = { Text(if (createModeIsFolder) "Nouveau dossier" else "Nouveau fichier") },
            text = {
                OutlinedTextField(
                    value = createNameInput,
                    onValueChange = { createNameInput = it },
                    label = { Text(if (createModeIsFolder) "Nom du dossier" else "Nom du fichier (ex: Helper.java)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createModeIsFolder) {
                            onCreateFolder(dirPath, createNameInput)
                        } else {
                            onCreateFile(dirPath, createNameInput)
                        }
                        targetDirForCreate = null
                    },
                    enabled = createNameInput.isNotBlank()
                ) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetDirForCreate = null }) { Text("Annuler") }
            }
        )
    }

    // Rename Dialog
    nodeToRename?.let { node ->
        AlertDialog(
            onDismissRequest = { nodeToRename = null },
            title = { Text("Renommer") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Nouveau nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameNode(node.absolutePath, renameInput)
                        nodeToRename = null
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToRename = null }) { Text("Annuler") }
            }
        )
    }

    // Delete Confirmation Dialog
    nodeToDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text("Supprimer '${node.name}' ?") },
            text = {
                Text(
                    if (node.isDirectory) "Ce dossier et tout son contenu seront supprimés."
                    else "Ce fichier sera définitivement supprimé."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNode(node.absolutePath)
                        nodeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NasjeErrorRed)
                ) {
                    Text("Supprimer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToDelete = null }) { Text("Annuler") }
            }
        )
    }
}
