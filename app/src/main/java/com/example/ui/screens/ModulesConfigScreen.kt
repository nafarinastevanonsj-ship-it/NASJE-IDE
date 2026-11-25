package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ProjectConfigRepository
import com.example.model.DependencyItem
import com.example.model.ModuleConfig
import com.example.model.ProjectInfo
import com.example.model.SdkComponentItem
import com.example.model.SdkComponentStatus
import com.example.model.ToolchainConfig
import com.example.ui.IdeUiState
import com.example.ui.theme.GlowCard
import com.example.ui.theme.GradientPrimaryButton
import com.example.ui.theme.LanguageBadge
import com.example.ui.theme.NasjeBackdropBrush
import com.example.ui.theme.NasjeBlue
import com.example.ui.theme.NasjeBorderSubtle
import com.example.ui.theme.NasjeCyan
import com.example.ui.theme.NasjeErrorRed
import com.example.ui.theme.NasjeRunGreen
import com.example.ui.theme.NasjeSurface
import com.example.ui.theme.NasjeSurfaceElevated
import com.example.ui.theme.NasjeTextSecondary
import com.example.ui.theme.NasjeViolet
import com.example.ui.theme.NasjeWarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesConfigScreen(
    state: IdeUiState,
    toolchain: ToolchainConfig,
    sdkComponents: List<SdkComponentItem>,
    onBack: () -> Unit,
    onApplyGeneralAndDeps: (
        appName: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        minSdk: Int,
        targetSdk: Int,
        dependencies: List<DependencyItem>
    ) -> Unit,
    onAddModule: (name: String, type: String, packageName: String) -> Unit,
    onRemoveModule: (name: String) -> Unit,
    onUpdateModule: (ModuleConfig) -> Unit,
    onUpdateToolchain: ((ToolchainConfig) -> ToolchainConfig) -> Unit,
    onInstallSdkComponent: (String) -> Unit
) {
    val project: ProjectInfo? = state.activeProject
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Général", "Modules", "Dépendances")

    var appName by remember(project?.id) { mutableStateOf(project?.name ?: "") }
    var packageName by remember(project?.id) { mutableStateOf(project?.packageName ?: "") }
    var versionName by remember(project?.id) { mutableStateOf(project?.versionName ?: "1.0") }
    var versionCodeStr by remember(project?.id) { mutableStateOf((project?.versionCode ?: 1).toString()) }
    var minSdk by remember(project?.id) { mutableIntStateOf(project?.minSdk ?: 24) }
    var targetSdk by remember(project?.id) { mutableIntStateOf(project?.targetSdk ?: 35) }

    val editableDeps = remember(state.projectConfig.dependencies) {
        mutableStateListOf<DependencyItem>().apply { addAll(state.projectConfig.dependencies) }
    }

    var showAddModuleDialog by remember { mutableStateOf(false) }
    var moduleToEdit by remember { mutableStateOf<ModuleConfig?>(null) }
    var showAddDepDialog by remember { mutableStateOf(false) }
    var depToEditIndex by remember { mutableStateOf<Int?>(null) }
    var showSdkManagerDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Modules et Configuration") },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("modules_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = NasjeCyan
                ) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label) },
                            modifier = Modifier.testTag("config_tab_$idx")
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, NasjeBorderSubtle)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    GradientPrimaryButton(
                        text = "Appliquer",
                        icon = Icons.Filled.Check,
                        enabled = project != null && minSdk <= targetSdk,
                        testTag = "modules_apply_button",
                        onClick = {
                            onApplyGeneralAndDeps(
                                appName,
                                packageName,
                                versionName,
                                versionCodeStr.toIntOrNull() ?: 1,
                                minSdk,
                                targetSdk,
                                editableDeps.toList()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { innerPadding ->
        if (project == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NasjeBackdropBrush)
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Aucun projet ouvert.", color = NasjeTextSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NasjeBackdropBrush)
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Configuration error banner if any
            if (state.projectConfig.configErrors.isNotEmpty() || minSdk > targetSdk) {
                item {
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = NasjeErrorRed.copy(alpha = 0.14f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = NasjeErrorRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Erreurs de configuration", style = MaterialTheme.typography.titleSmall, color = NasjeErrorRed)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (minSdk > targetSdk) {
                            Text("• minSdk ($minSdk) ne peut pas dépasser targetSdk ($targetSdk).", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                        state.projectConfig.configErrors.forEach { err ->
                            Text("• $err", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
            }

            when (selectedTab) {
                // ONGLET GÉNÉRAL
                0 -> {
                    item {
                        GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurface) {
                            Text("Identité de l'application", style = MaterialTheme.typography.titleMedium, color = NasjeCyan)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = appName,
                                onValueChange = { appName = it },
                                label = { Text("Nom de l'application") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = packageName,
                                onValueChange = { packageName = it },
                                label = { Text("Package name / Application ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = versionName,
                                    onValueChange = { versionName = it },
                                    label = { Text("Version") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = versionCodeStr,
                                    onValueChange = { versionCodeStr = it.filter { c -> c.isDigit() } },
                                    label = { Text("Version Code") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item {
                        GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurface) {
                            Text("SDK Android & Toolchain", style = MaterialTheme.typography.titleMedium, color = NasjeCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Minimum SDK : API $minSdk", style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(21, 24, 26, 28, 30).forEach { api ->
                                    FilterChip(
                                        selected = minSdk == api,
                                        onClick = { minSdk = api },
                                        label = { Text("$api") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Target SDK : API $targetSdk", style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(33, 34, 35, 36).forEach { api ->
                                    FilterChip(
                                        selected = targetSdk == api,
                                        onClick = { targetSdk = api },
                                        label = { Text("$api") }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            ToolchainDropdown(
                                label = "Android Gradle Plugin (AGP)",
                                selected = toolchain.agpVersion,
                                options = listOf("8.5.2", "8.7.3", "8.9.0", "9.1.1"),
                                onSelect = { v -> onUpdateToolchain { it.copy(agpVersion = v) } }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ToolchainDropdown(
                                label = "Gradle",
                                selected = toolchain.gradleVersion,
                                options = listOf("8.9", "8.10.2", "8.12", "8.13"),
                                onSelect = { v -> onUpdateToolchain { it.copy(gradleVersion = v) } }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ToolchainDropdown(
                                        label = "JDK",
                                        selected = toolchain.jdkVersion,
                                        options = listOf("11", "17", "21"),
                                        onSelect = { v -> onUpdateToolchain { it.copy(jdkVersion = v) } }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ToolchainDropdown(
                                        label = "CMake",
                                        selected = toolchain.cmakeVersion,
                                        options = listOf("3.22.1", "3.26.4", "3.30.5"),
                                        onSelect = { v -> onUpdateToolchain { it.copy(cmakeVersion = v) } }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ToolchainDropdown(
                                label = "Android NDK",
                                selected = toolchain.ndkVersion,
                                options = listOf("26.3.11579264", "27.1.12297006", "28.0.12433566"),
                                onSelect = { v -> onUpdateToolchain { it.copy(ndkVersion = v) } }
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showSdkManagerDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, tint = NasjeCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gérer les composants SDK installés (${sdkComponents.count { it.status == SdkComponentStatus.INSTALLED_BUILT_IN || it.status == SdkComponentStatus.INSTALLED_LOCAL }}/${sdkComponents.size})")
                            }
                        }
                    }

                    item {
                        GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurface) {
                            Text("Informations sur le projet", style = MaterialTheme.typography.titleMedium, color = NasjeCyan)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Type : ${project.projectType.displayName}", style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                            Text("Langage principal : ${project.primaryLanguage.label}", style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                            Text("Module principal : :${project.moduleName}", style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                            Text("Emplacement : ${project.rootPath}", style = MaterialTheme.typography.labelSmall, color = NasjeTextSecondary)
                        }
                    }
                }

                // ONGLET MODULES
                1 -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Modules du projet (${state.projectConfig.modules.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = NasjeCyan
                            )
                            OutlinedButton(
                                onClick = { showAddModuleDialog = true },
                                modifier = Modifier.testTag("add_module_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ajouter")
                            }
                        }
                    }

                    itemsIndexed(state.projectConfig.modules) { _, mod ->
                        GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurfaceElevated) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Layers, contentDescription = null, tint = NasjeViolet)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(":${mod.name}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                        LanguageBadge(label = mod.type, accentColor = NasjeCyan)
                                    }
                                    Text(mod.packageName, style = MaterialTheme.typography.labelSmall, color = NasjeTextSecondary)
                                    Text(
                                        "minSdk ${mod.minSdk} • targetSdk ${mod.targetSdk} • v${mod.versionName} (${mod.versionCode})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NasjeTextSecondary
                                    )
                                }
                                IconButton(onClick = { moduleToEdit = mod }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Modifier le module", tint = NasjeCyan)
                                }
                                if (mod.name != project.moduleName) {
                                    IconButton(onClick = { onRemoveModule(mod.name) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer le module", tint = NasjeErrorRed)
                                    }
                                }
                            }
                        }
                    }
                }

                // ONGLET DÉPENDANCES
                2 -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Dépendances (${editableDeps.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = NasjeCyan
                            )
                            OutlinedButton(
                                onClick = { showAddDepDialog = true },
                                modifier = Modifier.testTag("add_dependency_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ajouter")
                            }
                        }
                    }

                    if (editableDeps.isEmpty()) {
                        item {
                            GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurface) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = NasjeCyan)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Aucune dépendance externe configurée", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                        Text(
                                            "Le module utilise directement le SDK Android standard (compilable hors-ligne sur l'appareil).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NasjeTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(editableDeps) { idx, dep ->
                            GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurfaceElevated) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (dep.isCompatible) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = if (dep.isCompatible) NasjeRunGreen else NasjeWarningAmber
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${dep.group}:${dep.artifact}", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                        Text("${dep.configuration} • v${dep.version}", style = MaterialTheme.typography.labelSmall, color = NasjeCyan)
                                        Text(dep.compatibilityMessage, style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                                    }
                                    IconButton(onClick = { depToEditIndex = idx }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Modifier la version", tint = NasjeCyan)
                                    }
                                    IconButton(onClick = { editableDeps.removeAt(idx) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = NasjeErrorRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Module Dialog
    if (showAddModuleDialog && project != null) {
        var modName by remember { mutableStateOf("core") }
        var modPkg by remember { mutableStateOf("${project.packageName}.core") }
        var modType by remember { mutableStateOf("android-library") }
        AlertDialog(
            onDismissRequest = { showAddModuleDialog = false },
            title = { Text("Ajouter un module") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = modName,
                        onValueChange = { modName = it },
                        label = { Text("Nom du module") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = modPkg,
                        onValueChange = { modPkg = it },
                        label = { Text("Package du module") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = modType == "android-library",
                            onClick = { modType = "android-library" },
                            label = { Text("Bibliothèque") }
                        )
                        FilterChip(
                            selected = modType == "application",
                            onClick = { modType = "application" },
                            label = { Text("Application") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddModule(modName, modType, modPkg)
                    showAddModuleDialog = false
                }) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { showAddModuleDialog = false }) { Text("Annuler") }
            }
        )
    }

    // Edit Module Dialog
    moduleToEdit?.let { target ->
        var pkg by remember { mutableStateOf(target.packageName) }
        var modMin by remember { mutableStateOf(target.minSdk.toString()) }
        var modTarget by remember { mutableStateOf(target.targetSdk.toString()) }
        AlertDialog(
            onDismissRequest = { moduleToEdit = null },
            title = { Text("Modifier :${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("Package") }, singleLine = true)
                    OutlinedTextField(value = modMin, onValueChange = { modMin = it.filter { c -> c.isDigit() } }, label = { Text("Min SDK") }, singleLine = true)
                    OutlinedTextField(value = modTarget, onValueChange = { modTarget = it.filter { c -> c.isDigit() } }, label = { Text("Target SDK") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateModule(
                        target.copy(
                            packageName = pkg,
                            minSdk = modMin.toIntOrNull() ?: target.minSdk,
                            targetSdk = modTarget.toIntOrNull() ?: target.targetSdk
                        )
                    )
                    moduleToEdit = null
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { moduleToEdit = null }) { Text("Annuler") }
            }
        )
    }

    // Add Dependency Dialog
    if (showAddDepDialog) {
        var customCoord by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDepDialog = false },
            title = { Text("Ajouter une dépendance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customCoord,
                        onValueChange = { customCoord = it },
                        label = { Text("Coordonnée Maven (group:artifact:version)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Bibliothèques Android populaires :", style = MaterialTheme.typography.labelMedium, color = NasjeCyan)
                    ProjectConfigRepository.POPULAR_DEPENDENCIES.take(5).forEach { popular ->
                        Surface(
                            onClick = {
                                val repo = ProjectConfigRepository()
                                val (ok, msg) = repo.checkCompatibility(popular, targetSdk)
                                editableDeps.add(popular.copy(isCompatible = ok, compatibilityMessage = msg))
                                showAddDepDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = NasjeSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Extension, null, tint = NasjeViolet, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${popular.artifact}:${popular.version}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = customCoord.split(':').size == 3,
                    onClick = {
                        val parts = customCoord.trim().split(':')
                        if (parts.size == 3) {
                            val item = DependencyItem(parts[0], parts[1], parts[2], "implementation")
                            val (ok, msg) = ProjectConfigRepository().checkCompatibility(item, targetSdk)
                            editableDeps.add(item.copy(isCompatible = ok, compatibilityMessage = msg))
                        }
                        showAddDepDialog = false
                    }
                ) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDepDialog = false }) { Text("Annuler") }
            }
        )
    }

    // Edit Dependency Version Dialog
    depToEditIndex?.let { idx ->
        val dep = editableDeps.getOrNull(idx)
        if (dep != null) {
            var newVersion by remember { mutableStateOf(dep.version) }
            AlertDialog(
                onDismissRequest = { depToEditIndex = null },
                title = { Text("Version de ${dep.artifact}") },
                text = {
                    OutlinedTextField(
                        value = newVersion,
                        onValueChange = { newVersion = it },
                        label = { Text("Version") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val updated = dep.copy(version = newVersion.trim())
                        val (ok, msg) = ProjectConfigRepository().checkCompatibility(updated, targetSdk)
                        editableDeps[idx] = updated.copy(isCompatible = ok, compatibilityMessage = msg)
                        depToEditIndex = null
                    }) { Text("Modifier") }
                },
                dismissButton = {
                    TextButton(onClick = { depToEditIndex = null }) { Text("Annuler") }
                }
            )
        }
    }

    // SDK & Toolchain Components Dialog
    if (showSdkManagerDialog) {
        AlertDialog(
            onDismissRequest = { showSdkManagerDialog = false },
            title = { Text("Gestionnaire SDK & Outils") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(sdkComponents) { _, comp ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NasjeSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(comp.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Text("${comp.version} • ${comp.status.label}", style = MaterialTheme.typography.labelSmall, color = NasjeCyan)
                                    Text(comp.description, style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                                }
                                if (comp.status == SdkComponentStatus.AVAILABLE_DOWNLOAD) {
                                    IconButton(onClick = { onInstallSdkComponent(comp.id) }) {
                                        Icon(Icons.Filled.Download, contentDescription = "Installer", tint = NasjeRunGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSdkManagerDialog = false }) { Text("Fermer") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolchainDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
