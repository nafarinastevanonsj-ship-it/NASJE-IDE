package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.compiler.BuildEngine
import com.example.data.FileManagerRepository
import com.example.data.LoadedProjectConfiguration
import com.example.data.ProjectConfigRepository
import com.example.data.ProjectRepository
import com.example.data.SettingsRepository
import com.example.editor.CodeAnalyzer
import com.example.model.BuildResult
import com.example.model.DependencyItem
import com.example.model.DiagnosticItem
import com.example.model.DiagnosticSeverity
import com.example.model.EditorTab
import com.example.model.FileNode
import com.example.model.IdeLanguage
import com.example.model.IdeSettings
import com.example.model.ModuleConfig
import com.example.model.ProjectInfo
import com.example.model.ProjectSortOrder
import com.example.model.ProjectType
import com.example.model.SdkComponentItem
import com.example.model.SyntaxThemePreset
import com.example.model.ToolchainConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ClipboardFileOp(
    val sourcePath: String,
    val fileName: String,
    val isMove: Boolean
)

data class IdeUiState(
    val projects: List<ProjectInfo> = emptyList(),
    val projectSearchQuery: String = "",
    val projectSortOrder: ProjectSortOrder = ProjectSortOrder.RECENT,
    val showAllProjects: Boolean = false,
    val activeProject: ProjectInfo? = null,
    val fileTree: FileNode? = null,
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabIndex: Int = 0,
    val clipboardOp: ClipboardFileOp? = null,
    val liveDiagnostics: List<DiagnosticItem> = emptyList(),
    val projectConfig: LoadedProjectConfiguration = LoadedProjectConfiguration(emptyList(), emptyList(), emptyList()),
    val isCompiling: Boolean = false,
    val compileStatusMessage: String = "",
    val lastBuildResult: BuildResult? = null,
    val snackbarMessage: String? = null,
    val projectsStorageBytes: Long = 0L,
    val buildsStorageBytes: Long = 0L
)

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    private val projectRepo = ProjectRepository(application)
    private val fileRepo = FileManagerRepository(application)
    private val configRepo = ProjectConfigRepository()
    val settingsRepo = SettingsRepository(application)
    private val buildEngine = BuildEngine(application, configRepo)

    private val _uiState = MutableStateFlow(IdeUiState())
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    val settings: StateFlow<IdeSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IdeSettings())

    val toolchain: StateFlow<ToolchainConfig> = settingsRepo.toolchainFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ToolchainConfig())

    val sdkComponents: StateFlow<List<SdkComponentItem>> = combine(
        settingsRepo.installedSdkIdsFlow,
        settingsRepo.settingsFlow
    ) { installedIds, s ->
        settingsRepo.getAvailableSdkComponents(installedIds, s.remoteBuildBridgeUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var autoSaveJob: Job? = null
    private var analyzeJob: Job? = null

    init {
        refreshProjects()
        refreshStorageStats()
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            val list = projectRepo.listProjects()
            _uiState.update { it.copy(projects = list) }
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val (pSize, bSize) = settingsRepo.calculateStorageUsageBytes()
            _uiState.update { it.copy(projectsStorageBytes = pSize, buildsStorageBytes = bSize) }
        }
    }

    fun updateProjectSearch(query: String) {
        _uiState.update { it.copy(projectSearchQuery = query) }
    }

    fun updateProjectSort(order: ProjectSortOrder) {
        _uiState.update { it.copy(projectSortOrder = order) }
    }

    fun toggleShowAllProjects() {
        _uiState.update { it.copy(showAllProjects = !it.showAllProjects) }
    }

    fun createProject(
        name: String,
        packageName: String,
        projectType: ProjectType,
        primaryLanguage: IdeLanguage,
        moduleName: String,
        versionName: String,
        minSdk: Int,
        targetSdk: Int,
        onCreated: (ProjectInfo) -> Unit
    ) {
        viewModelScope.launch {
            val created = projectRepo.createProject(
                name = name,
                packageName = packageName,
                projectType = projectType,
                primaryLanguage = primaryLanguage,
                moduleName = moduleName,
                versionName = versionName,
                minSdk = minSdk,
                targetSdk = targetSdk
            )
            refreshProjects()
            refreshStorageStats()
            openProject(created)
            onCreated(created)
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            val updated = projectRepo.renameProject(projectId, newName)
            refreshProjects()
            if (updated != null && _uiState.value.activeProject?.id == projectId) {
                _uiState.update { it.copy(activeProject = updated) }
                refreshFileTree()
            }
            showMessage("Projet renommé en '$newName'")
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            val copy = projectRepo.duplicateProject(projectId)
            refreshProjects()
            refreshStorageStats()
            if (copy != null) showMessage("Projet dupliqué : ${copy.name}")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepo.deleteProject(projectId)
            if (_uiState.value.activeProject?.id == projectId) {
                _uiState.update { it.copy(activeProject = null, openTabs = emptyList(), fileTree = null) }
            }
            refreshProjects()
            refreshStorageStats()
            showMessage("Projet supprimé")
        }
    }

    fun openProject(project: ProjectInfo) {
        viewModelScope.launch {
            val tree = fileRepo.buildFileTree(project.rootPath)
            val primaryFile = fileRepo.findPrimarySourceFile(project.rootPath, project.moduleName)
            val initialTabs = if (primaryFile != null) {
                val content = fileRepo.readFile(primaryFile.absolutePath)
                val rel = primaryFile.relativeTo(File(project.rootPath)).path.replace(File.separatorChar, '/')
                listOf(
                    EditorTab(
                        filePath = primaryFile.absolutePath,
                        relativePath = rel,
                        fileName = primaryFile.name,
                        language = IdeLanguage.fromFileName(primaryFile.name),
                        content = content,
                        savedContent = content
                    )
                )
            } else {
                emptyList()
            }
            val cfg = configRepo.loadConfiguration(project)
            val diags = CodeAnalyzer.analyzeProject(project.rootPath)

            _uiState.update {
                it.copy(
                    activeProject = project,
                    fileTree = tree,
                    openTabs = initialTabs,
                    activeTabIndex = 0,
                    projectConfig = cfg,
                    liveDiagnostics = diags
                )
            }
        }
    }

    fun refreshFileTree() {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val tree = fileRepo.buildFileTree(project.rootPath)
            _uiState.update { it.copy(fileTree = tree) }
        }
    }

    fun openFileInEditor(filePath: String, targetLine: Int? = null) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val currentTabs = _uiState.value.openTabs.toMutableList()
            val existingIdx = currentTabs.indexOfFirst { it.filePath == filePath }
            if (existingIdx >= 0) {
                if (targetLine != null) {
                    val tab = currentTabs[existingIdx]
                    val cursorOffset = offsetForLine(tab.content, targetLine)
                    currentTabs[existingIdx] = tab.copy(cursorIndex = cursorOffset, pendingLineJump = targetLine)
                }
                _uiState.update { it.copy(openTabs = currentTabs, activeTabIndex = existingIdx) }
                return@launch
            }

            val file = File(filePath)
            if (!file.exists() || !file.isFile) return@launch
            val content = fileRepo.readFile(filePath)
            val rel = runCatching { file.relativeTo(File(project.rootPath)).path.replace(File.separatorChar, '/') }
                .getOrDefault(file.name)
            val cursorOffset = if (targetLine != null) offsetForLine(content, targetLine) else 0
            val newTab = EditorTab(
                filePath = file.absolutePath,
                relativePath = rel,
                fileName = file.name,
                language = IdeLanguage.fromFileName(file.name),
                content = content,
                savedContent = content,
                cursorIndex = cursorOffset,
                pendingLineJump = targetLine
            )
            currentTabs.add(newTab)
            _uiState.update {
                it.copy(openTabs = currentTabs, activeTabIndex = currentTabs.lastIndex)
            }
        }
    }

    fun consumePendingLineJump(tabIndex: Int) {
        _uiState.update { state ->
            val tabs = state.openTabs.toMutableList()
            val tab = tabs.getOrNull(tabIndex) ?: return@update state
            tabs[tabIndex] = tab.copy(pendingLineJump = null)
            state.copy(openTabs = tabs)
        }
    }

    fun selectTab(index: Int) {
        if (index in _uiState.value.openTabs.indices) {
            _uiState.update { it.copy(activeTabIndex = index) }
        }
    }

    fun closeTab(index: Int) {
        _uiState.update { state ->
            val tabs = state.openTabs.toMutableList()
            if (index !in tabs.indices) return@update state
            tabs.removeAt(index)
            val nextIdx = when {
                tabs.isEmpty() -> 0
                index < state.activeTabIndex -> state.activeTabIndex - 1
                state.activeTabIndex >= tabs.size -> tabs.lastIndex
                else -> state.activeTabIndex
            }
            state.copy(openTabs = tabs, activeTabIndex = nextIdx)
        }
    }

    fun updateActiveTabContent(newContent: String, cursorIndex: Int) {
        val state = _uiState.value
        val idx = state.activeTabIndex
        val tab = state.openTabs.getOrNull(idx) ?: return

        val contentChanged = newContent != tab.content
        val updatedUndo = if (contentChanged) (tab.undoStack + tab.content).takeLast(40) else tab.undoStack
        val updatedRedo = if (contentChanged) emptyList() else tab.redoStack

        val updatedTab = tab.copy(
            content = newContent,
            cursorIndex = cursorIndex.coerceIn(0, newContent.length),
            undoStack = updatedUndo,
            redoStack = updatedRedo
        )
        val newTabs = state.openTabs.toMutableList().apply { this[idx] = updatedTab }
        _uiState.update { it.copy(openTabs = newTabs) }

        if (contentChanged) {
            scheduleLiveAnalyze(updatedTab)
            if (settings.value.autoSave) {
                scheduleAutoSave(updatedTab)
            }
        }
    }

    fun undoActiveTab() {
        val state = _uiState.value
        val idx = state.activeTabIndex
        val tab = state.openTabs.getOrNull(idx) ?: return
        if (tab.undoStack.isEmpty()) return

        val prev = tab.undoStack.last()
        val updatedTab = tab.copy(
            content = prev,
            undoStack = tab.undoStack.dropLast(1),
            redoStack = (tab.redoStack + tab.content).takeLast(40),
            cursorIndex = tab.cursorIndex.coerceIn(0, prev.length)
        )
        val newTabs = state.openTabs.toMutableList().apply { this[idx] = updatedTab }
        _uiState.update { it.copy(openTabs = newTabs) }
        scheduleLiveAnalyze(updatedTab)
    }

    fun redoActiveTab() {
        val state = _uiState.value
        val idx = state.activeTabIndex
        val tab = state.openTabs.getOrNull(idx) ?: return
        if (tab.redoStack.isEmpty()) return

        val next = tab.redoStack.last()
        val updatedTab = tab.copy(
            content = next,
            undoStack = (tab.undoStack + tab.content).takeLast(40),
            redoStack = tab.redoStack.dropLast(1),
            cursorIndex = tab.cursorIndex.coerceIn(0, next.length)
        )
        val newTabs = state.openTabs.toMutableList().apply { this[idx] = updatedTab }
        _uiState.update { it.copy(openTabs = newTabs) }
        scheduleLiveAnalyze(updatedTab)
    }

    fun saveActiveTab(showToast: Boolean = true) {
        val state = _uiState.value
        val idx = state.activeTabIndex
        val tab = state.openTabs.getOrNull(idx) ?: return
        viewModelScope.launch {
            val ok = fileRepo.writeFile(tab.filePath, tab.content)
            if (ok) {
                val updated = tab.copy(savedContent = tab.content)
                _uiState.update { s ->
                    val list = s.openTabs.toMutableList()
                    if (idx in list.indices && list[idx].filePath == tab.filePath) {
                        list[idx] = updated
                    }
                    s.copy(openTabs = list)
                }
                state.activeProject?.let { projectRepo.touchProjectModified(it.id) }
                refreshProjects()
                if (showToast) showMessage("Fichier '${tab.fileName}' enregistré")
            }
        }
    }

    suspend fun saveAllOpenTabs() {
        val tabs = _uiState.value.openTabs
        tabs.filter { it.isDirty }.forEach { tab ->
            fileRepo.writeFile(tab.filePath, tab.content)
        }
        _uiState.update { s ->
            s.copy(openTabs = s.openTabs.map { it.copy(savedContent = it.content) })
        }
        _uiState.value.activeProject?.let { projectRepo.touchProjectModified(it.id) }
        refreshProjects()
    }

    fun replaceInActiveTab(search: String, replacement: String, replaceAll: Boolean) {
        if (search.isEmpty()) return
        val tab = _uiState.value.openTabs.getOrNull(_uiState.value.activeTabIndex) ?: return
        val newText = if (replaceAll) {
            tab.content.replace(search, replacement, ignoreCase = false)
        } else {
            val idx = tab.content.indexOf(search)
            if (idx >= 0) tab.content.replaceRange(idx, idx + search.length, replacement) else tab.content
        }
        if (newText != tab.content) {
            updateActiveTabContent(newText, tab.cursorIndex.coerceAtMost(newText.length))
            showMessage("Remplacement effectué")
        } else {
            showMessage("Aucune occurrence trouvée")
        }
    }

    // File Explorer operations
    fun createFile(parentDirPath: String, fileName: String) {
        viewModelScope.launch {
            val created = fileRepo.createFile(parentDirPath, fileName)
            if (created != null) {
                refreshFileTree()
                openFileInEditor(created.absolutePath)
                showMessage("Fichier '${created.name}' créé")
            } else {
                showMessage("Impossible de créer le fichier '$fileName'")
            }
        }
    }

    fun createFolder(parentDirPath: String, folderName: String) {
        viewModelScope.launch {
            val created = fileRepo.createFolder(parentDirPath, folderName)
            if (created != null) {
                refreshFileTree()
                showMessage("Dossier '${created.name}' créé")
            } else {
                showMessage("Impossible de créer le dossier '$folderName'")
            }
        }
    }

    fun renameFileNode(filePath: String, newName: String) {
        viewModelScope.launch {
            val renamed = fileRepo.renameNode(filePath, newName)
            if (renamed != null) {
                _uiState.update { s ->
                    val updatedTabs = s.openTabs.map { tab ->
                        if (tab.filePath == filePath) {
                            tab.copy(
                                filePath = renamed.absolutePath,
                                fileName = renamed.name,
                                language = IdeLanguage.fromFileName(renamed.name)
                            )
                        } else tab
                    }
                    s.copy(openTabs = updatedTabs)
                }
                refreshFileTree()
                showMessage("Renommé en '${renamed.name}'")
            } else {
                showMessage("Échec du renommage")
            }
        }
    }

    fun deleteFileNode(filePath: String) {
        viewModelScope.launch {
            val ok = fileRepo.deleteNode(filePath)
            if (ok) {
                _uiState.update { s ->
                    val filtered = s.openTabs.filterNot { it.filePath == filePath || it.filePath.startsWith("$filePath/") }
                    s.copy(
                        openTabs = filtered,
                        activeTabIndex = s.activeTabIndex.coerceAtMost((filtered.size - 1).coerceAtLeast(0))
                    )
                }
                refreshFileTree()
                showMessage("Élément supprimé")
            }
        }
    }

    fun copyToClipboard(filePath: String, fileName: String, isMove: Boolean) {
        _uiState.update {
            it.copy(
                clipboardOp = ClipboardFileOp(filePath, fileName, isMove),
                snackbarMessage = if (isMove) "Prêt à déplacer '$fileName'" else "Copié : '$fileName'"
            )
        }
    }

    fun pasteClipboardInto(destDirPath: String) {
        val clip = _uiState.value.clipboardOp ?: return
        viewModelScope.launch {
            val result = if (clip.isMove) {
                fileRepo.moveNodeToDir(clip.sourcePath, destDirPath)
            } else {
                fileRepo.copyNodeToDir(clip.sourcePath, destDirPath)
            }
            if (result != null) {
                _uiState.update { it.copy(clipboardOp = if (clip.isMove) null else it.clipboardOp) }
                refreshFileTree()
                showMessage(if (clip.isMove) "Déplacé vers '${result.name}'" else "Collé : '${result.name}'")
            } else {
                showMessage("Opération impossible dans ce dossier")
            }
        }
    }

    fun importExternalFile(uri: Uri, destDirPath: String) {
        viewModelScope.launch {
            val imported = fileRepo.importFromUri(uri, destDirPath)
            if (imported != null) {
                refreshFileTree()
                openFileInEditor(imported.absolutePath)
                showMessage("Fichier importé : ${imported.name}")
            } else {
                showMessage("Échec de l'importation")
            }
        }
    }

    // Modules & Configuration
    fun reloadProjectConfig() {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val cfg = configRepo.loadConfiguration(project)
            _uiState.update { it.copy(projectConfig = cfg) }
        }
    }

    fun applyGeneralAndDependencies(
        appName: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        minSdk: Int,
        targetSdk: Int,
        dependencies: List<DependencyItem>
    ) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val updatedProject = configRepo.applyGeneralAndDependencies(
                project = project,
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                dependencies = dependencies
            )
            projectRepo.updateProjectMetadata(updatedProject)
            val cfg = configRepo.loadConfiguration(updatedProject)
            _uiState.update {
                it.copy(activeProject = updatedProject, projectConfig = cfg)
            }
            refreshProjects()
            refreshFileTree()
            // Reload open gradle/manifest/strings tabs if open
            reloadOpenTabsFromDisk()
            showMessage("Configuration enregistrée dans les fichiers du projet")
        }
    }

    fun addModule(moduleName: String, moduleType: String, packageName: String) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val ok = configRepo.addModule(project, moduleName, moduleType, packageName)
            if (ok) {
                reloadProjectConfig()
                refreshFileTree()
                showMessage("Module ':$moduleName' ajouté")
            } else {
                showMessage("Impossible d'ajouter le module '$moduleName'")
            }
        }
    }

    fun removeModule(moduleName: String) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val ok = configRepo.removeModule(project, moduleName)
            if (ok) {
                reloadProjectConfig()
                refreshFileTree()
                showMessage("Module ':$moduleName' supprimé")
            } else {
                showMessage("Le module principal ne peut pas être supprimé")
            }
        }
    }

    fun updateModuleConfig(module: ModuleConfig) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val ok = configRepo.updateModuleConfig(project, module)
            if (ok) {
                reloadProjectConfig()
                reloadOpenTabsFromDisk()
                showMessage("Configuration de ':${module.name}' mise à jour")
            }
        }
    }

    // Compilation
    fun compileActiveProject(onComplete: (BuildResult) -> Unit) {
        val project = _uiState.value.activeProject ?: return
        if (_uiState.value.isCompiling) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCompiling = true, compileStatusMessage = "Sauvegarde des fichiers...") }
            saveAllOpenTabs()
            val result = buildEngine.compileProject(
                project = project,
                remoteBridgeUrl = settings.value.remoteBuildBridgeUrl,
                onStepUpdate = { step ->
                    _uiState.update { it.copy(compileStatusMessage = step) }
                }
            )
            _uiState.update {
                it.copy(
                    isCompiling = false,
                    compileStatusMessage = "",
                    lastBuildResult = result,
                    liveDiagnostics = result.diagnostics
                )
            }
            refreshStorageStats()
            onComplete(result)
        }
    }

    fun installSdkComponent(componentId: String) {
        viewModelScope.launch {
            settingsRepo.installLocalSdkMetadata(componentId)
            showMessage("Composant SDK installé localement")
        }
    }

    fun clearBuildsCache() {
        viewModelScope.launch {
            val freed = settingsRepo.clearBuildCache()
            refreshStorageStats()
            val kb = freed / 1024
            showMessage("Cache des compilations nettoyé ($kb Ko libérés)")
        }
    }

    private fun scheduleAutoSave(tab: EditorTab) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(900)
            fileRepo.writeFile(tab.filePath, tab.content)
            _uiState.update { s ->
                val tabs = s.openTabs.map {
                    if (it.filePath == tab.filePath && it.content == tab.content) {
                        it.copy(savedContent = tab.content)
                    } else it
                }
                s.copy(openTabs = tabs)
            }
        }
    }

    private fun scheduleLiveAnalyze(tab: EditorTab) {
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            delay(450)
            val fileDiags = CodeAnalyzer.analyzeFile(tab.filePath, tab.fileName, tab.content, tab.language)
            _uiState.update { s ->
                val others = s.liveDiagnostics.filterNot { it.filePath == tab.filePath }
                s.copy(liveDiagnostics = others + fileDiags)
            }
        }
    }

    private suspend fun reloadOpenTabsFromDisk() {
        val current = _uiState.value.openTabs
        val reloaded = current.map { tab ->
            val f = File(tab.filePath)
            if (f.exists()) {
                val disk = fileRepo.readFile(tab.filePath)
                tab.copy(content = disk, savedContent = disk, cursorIndex = tab.cursorIndex.coerceAtMost(disk.length))
            } else tab
        }
        _uiState.update { it.copy(openTabs = reloaded) }
    }

    fun updateSettings(transform: (IdeSettings) -> IdeSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(transform)
        }
    }

    fun updateToolchain(transform: (ToolchainConfig) -> ToolchainConfig) {
        viewModelScope.launch {
            settingsRepo.updateToolchain(transform)
        }
    }

    private fun offsetForLine(text: String, targetLine: Int): Int {
        if (targetLine <= 1) return 0
        var line = 1
        var idx = 0
        while (idx < text.length && line < targetLine) {
            if (text[idx] == '\n') line++
            idx++
        }
        return idx.coerceIn(0, text.length)
    }
}
