package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.IdeLanguage
import com.example.model.ProjectInfo
import com.example.model.ProjectSortOrder
import com.example.model.ProjectType
import com.example.ui.IdeUiState
import com.example.ui.theme.GlowCard
import com.example.ui.theme.GradientPrimaryButton
import com.example.ui.theme.LanguageBadge
import com.example.ui.theme.NasjeBackdropBrush
import com.example.ui.theme.NasjeBlue
import com.example.ui.theme.NasjeBorderSubtle
import com.example.ui.theme.NasjeCyan
import com.example.ui.theme.NasjeErrorRed
import com.example.ui.theme.NasjeGlowBorderGradient
import com.example.ui.theme.NasjeLogoMark
import com.example.ui.theme.NasjeSurface
import com.example.ui.theme.NasjeSurfaceElevated
import com.example.ui.theme.NasjeTextMuted
import com.example.ui.theme.NasjeTextSecondary
import com.example.ui.theme.NasjeViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: IdeUiState,
    onNewProjectClick: () -> Unit,
    onOpenProject: (ProjectInfo) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (ProjectSortOrder) -> Unit,
    onToggleShowAll: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<ProjectInfo?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<ProjectInfo?>(null) }
    var showSearchField by remember { mutableStateOf(false) }

    val filteredProjects = remember(state.projects, state.projectSearchQuery, state.projectSortOrder) {
        val q = state.projectSearchQuery.trim().lowercase()
        val matched = if (q.isEmpty()) state.projects else {
            state.projects.filter {
                it.name.lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q) ||
                    it.primaryLanguage.label.lowercase().contains(q)
            }
        }
        when (state.projectSortOrder) {
            ProjectSortOrder.RECENT -> matched.sortedByDescending { it.lastModifiedAt }
            ProjectSortOrder.NAME -> matched.sortedBy { it.name.lowercase() }
            ProjectSortOrder.LANGUAGE -> matched.sortedBy { it.primaryLanguage.label }
        }
    }

    val visibleProjects = if (state.showAllProjects || state.projectSearchQuery.isNotBlank()) {
        filteredProjects
    } else {
        filteredProjects.take(4)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, NasjeBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.projects.size} projet(s) enregistrés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_settings_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NasjeGlowBorderGradient)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Paramètres",
                            tint = NasjeCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Paramètres", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NasjeBackdropBrush)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Header: Stylized N Logo + NASJE IDE + Slogan
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NasjeLogoMark(size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "NASJE IDE",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Développez. Compilez. Réalisez.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NasjeCyan
                                )
                            }
                        }
                        IconButton(
                            onClick = { showSearchField = !showSearchField },
                            modifier = Modifier.testTag("home_toggle_search")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Rechercher un projet",
                                tint = if (showSearchField) NasjeCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Hero Illustration + Main "+ Nouveau Projet" Gradient CTA
                item {
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(156.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, NasjeBorderSubtle, RoundedCornerShape(14.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_hero_ide),
                                contentDescription = "Illustration environnement de développement logiciel",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color(0xCC060A14))
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Studio Android Mobile",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Java • Kotlin • C++ NDK • Compilateur APK natif",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NasjeCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        GradientPrimaryButton(
                            text = "+ Nouveau Projet",
                            onClick = onNewProjectClick,
                            icon = Icons.Filled.Add,
                            testTag = "new_project_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Search bar (when toggled or query active)
                item {
                    AnimatedVisibility(visible = showSearchField || state.projectSearchQuery.isNotBlank()) {
                        OutlinedTextField(
                            value = state.projectSearchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Rechercher par nom, package ou langage...") },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = NasjeCyan)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NasjeBlue,
                                unfocusedBorderColor = NasjeBorderSubtle,
                                focusedContainerColor = NasjeSurface,
                                unfocusedContainerColor = NasjeSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_search_input")
                        )
                    }
                }

                // "Mes Projets" Section Header with Sort & Show All
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mes Projets",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                TextButton(
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier.testTag("home_sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Trier les projets",
                                        tint = NasjeViolet,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(state.projectSortOrder.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    ProjectSortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            onClick = {
                                                onSortChange(order)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (filteredProjects.size > 4) {
                                TextButton(
                                    onClick = onToggleShowAll,
                                    modifier = Modifier.testTag("home_show_all_button")
                                ) {
                                    Text(
                                        text = if (state.showAllProjects) "Réduire" else "Tout (${filteredProjects.size})",
                                        color = NasjeCyan
                                    )
                                }
                            }
                        }
                    }
                }

                // Empty State vs Saved Projects List
                if (visibleProjects.isEmpty()) {
                    item {
                        GlowCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = NasjeSurface.copy(alpha = 0.7f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    tint = NasjeViolet,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (state.projectSearchQuery.isBlank()) {
                                        "Aucun projet enregistré pour le moment"
                                    } else {
                                        "Aucun projet ne correspond à « ${state.projectSearchQuery} »"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Appuyez sur « + Nouveau Projet » pour créer votre première application Android.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NasjeTextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(visibleProjects, key = { it.id }) { project ->
                        ProjectListCard(
                            project = project,
                            onClick = { onOpenProject(project) },
                            onRename = {
                                renameInput = project.name
                                projectToRename = project
                            },
                            onDuplicate = { onDuplicateProject(project.id) },
                            onDelete = { projectToDelete = project }
                        )
                    }
                }
            }
        }
    }

    // Rename Project Dialog
    projectToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Renommer le projet") },
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
                        onRenameProject(target.id, renameInput)
                        projectToRename = null
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Renommer")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    projectToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Supprimer le projet ?") },
            text = {
                Text("Tous les fichiers de « ${target.name} » (${target.packageName}) seront définitivement supprimés de l'appareil.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(target.id)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NasjeErrorRed)
                ) {
                    Text("Supprimer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun ProjectListCard(
    project: ProjectInfo,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    val dateStr = dateFmt.format(Date(project.lastModifiedAt))
    val timeStr = timeFmt.format(Date(project.lastModifiedAt))

    val icon = when (project.projectType) {
        ProjectType.ANDROID_CPP -> Icons.Filled.Memory
        ProjectType.ANDROID_KOTLIN, ProjectType.ANDROID_JAVA -> Icons.Filled.Android
        else -> Icons.Filled.Code
    }

    GlowCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}"),
        backgroundColor = NasjeSurfaceElevated.copy(alpha = 0.85f),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(project.primaryLanguage.accentColor.copy(alpha = 0.16f))
                    .border(
                        1.dp,
                        project.primaryLanguage.accentColor.copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = project.primaryLanguage.label,
                    tint = project.primaryLanguage.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    LanguageBadge(
                        label = project.primaryLanguage.label,
                        accentColor = project.primaryLanguage.accentColor
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = project.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = NasjeTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Modifié le $dateStr à $timeStr • SDK ${project.minSdk}–${project.targetSdk}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NasjeTextMuted
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("project_menu_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options du projet",
                        tint = NasjeTextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ouvrir") },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                        onClick = { menuExpanded = false; onClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Dupliquer") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = { menuExpanded = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = NasjeErrorRed) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = NasjeErrorRed) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}
