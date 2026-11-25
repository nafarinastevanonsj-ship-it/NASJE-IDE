package com.NASJEProduction.NASJEIDE.ui

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.NASJEProduction.NASJEIDE.compiler.BuildEngine
import com.NASJEProduction.NASJEIDE.data.FileManagerRepository
import com.NASJEProduction.NASJEIDE.data.ProjectConfigRepository
import com.NASJEProduction.NASJEIDE.data.ProjectRepository
import com.NASJEProduction.NASJEIDE.data.SettingsRepository
import com.NASJEProduction.NASJEIDE.editor.CodeAnalyzer
import com.NASJEProduction.NASJEIDE.model.BuildResult
import com.NASJEProduction.NASJEIDE.model.DependencyItem
import com.NASJEProduction.NASJEIDE.model.DiagnosticItem
import com.NASJEProduction.NASJEIDE.model.DiagnosticSeverity
import com.NASJEProduction.NASJEIDE.model.EditorTab
import com.NASJEProduction.NASJEIDE.model.FileNode
import com.NASJEProduction.NASJEIDE.model.IdeLanguage
import com.NASJEProduction.NASJEIDE.model.IdeSettings
import com.NASJEProduction.NASJEIDE.model.ModuleConfig
import com.NASJEProduction.NASJEIDE.model.ProjectInfo
import com.NASJEProduction.NASJEIDE.model.ProjectSortOrder
import com.NASJEProduction.NASJEIDE.model.ProjectType
import com.NASJEProduction.NASJEIDE.model.SdkComponentItem
import com.NASJEProduction.NASJEIDE.model.ToolchainConfig
import java.io.File
import java.util.concurrent.Executors

data class ClipboardFileOp(
    val sourcePath: String,
    val sourceName: String,
    val isMove: Boolean
)

data class IdeUiState(
    val projects: List<ProjectInfo> = emptyList(),
    val projectSearchQuery: String = "",
    val projectSortOrder: ProjectSortOrder = ProjectSortOrder.RECENT,
    val showAllProjects: Boolean = false,
    val currentProject: ProjectInfo? = null,
    val fileTree: FileNode? = null,
    val fileSearchQuery: String = "",
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabIndex: Int = -1,
    val activeFileDiagnostics: List<DiagnosticItem> = emptyList(),
    val projectDiagnostics: List<DiagnosticItem> = emptyList(),
    val modules: List<ModuleConfig> = emptyList(),
    val dependencies: List<DependencyItem> = emptyList(),
    val configErrors: List<String> = emptyList(),
    val clipboardOp: ClipboardFileOp? = null,
    val isCompiling: Boolean = false,
    val compileStatusMessage: String = "",
    val compileLogs: List<String> = emptyList(),
    val lastBuildResult: BuildResult? = null,
    val snackbarMessage: String? = null,
    val projectsStorageBytes: Long = 0L,
    val buildsStorageBytes: Long = 0L
) {
    val activeTab: EditorTab? get() = openTabs.getOrNull(activeTabIndex)
    val errorCount: Int get() = projectDiagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount: Int get() = projectDiagnostics.count { it.severity == DiagnosticSeverity.WARNING }
}

/**
 * Pure Android SDK state & background-task controller (zero ViewModel / kotlinx.coroutines).
 */
class IdeController(private val context: Context) {

    private val projectRepo = ProjectRepository(context)
    private val fileRepo = FileManagerRepository(context)
    private val configRepo = ProjectConfigRepository()
    private val settingsRepo = SettingsRepository(context)
    private val buildEngine = BuildEngine(context, configRepo)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()

    var uiState: IdeUiState = IdeUiState()
        private set

    var settings: IdeSettings = settingsRepo.getSettings()
        private set

    var toolchain: ToolchainConfig = settingsRepo.getToolchain()
        private set

    var sdkComponents: List<SdkComponentItem> = emptyList()
        private set

    private var installedSdkIds: Set<String> = settingsRepo.getInstalledSdkIds()

    private val listeners = mutableListOf<() -> Unit>()
    private var autoSaveRunnable: Runnable? = null
    private var analyzeRunnable: Runnable? = null

    init {
        refreshSdkComponents()
        refreshProjects()
        refreshStorageStats()
    }

    fun addListener(listener: () -> Unit) {
        if (listener !in listeners) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.toList().forEach { it() }
        } else {
            mainHandler.post { listeners.toList().forEach { it() } }
        }
    }

    private fun updateState(transform: (IdeUiState) -> IdeUiState) {
        uiState = transform(uiState)
        notifyChanged()
    }

    fun refreshSdkComponents() {
        installedSdkIds = settingsRepo.getInstalledSdkIds()
        sdkComponents = settingsRepo.getAvailableSdkComponents(installedSdkIds, settings.remoteBuildBridgeUrl)
    }

    fun refreshProjects() {
        val loaded = projectRepo.listProjects()
        updateState { it.copy(projects = loaded) }
    }

    fun setProjectSearchQuery(q: String) {
        updateState { it.copy(projectSearchQuery = q) }
    }

    fun setProjectSortOrder(order: ProjectSortOrder) {
        updateState { it.copy(projectSortOrder = order) }
    }

    fun toggleShowAllProjects() {
        updateState { it.copy(showAllProjects = !it.showAllProjects) }
    }

    fun filteredProjects(): List<ProjectInfo> {
        val s = uiState
        val filtered = s.projects.filter {
            s.projectSearchQuery.isBlank() ||
                it.name.contains(s.projectSearchQuery, ignoreCase = true) ||
                it.packageName.contains(s.projectSearchQuery, ignoreCase = true)
        }
        val sorted = when (s.projectSortOrder) {
            ProjectSortOrder.RECENT -> filtered.sortedByDescending { it.lastModifiedAt }
            ProjectSortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
            ProjectSortOrder.LANGUAGE -> filtered.sortedBy { it.primaryLanguage.label }
        }
        return if (s.showAllProjects || s.projectSearchQuery.isNotBlank()) sorted else sorted.take(5)
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
        onError: (String) -> Unit = {},
        onCreated: (ProjectInfo) -> Unit
    ) {
        executor.execute {
            try {
                val project = projectRepo.createProject(
                    name = name,
                    packageName = packageName,
                    projectType = projectType,
                    primaryLanguage = primaryLanguage,
                    moduleName = moduleName,
                    versionName = versionName,
                    minSdk = minSdk,
                    targetSdk = targetSdk
                )
                mainHandler.post {
                    refreshProjects()
                    refreshStorageStats()
                    openProjectInternal(project)
                    onCreated(project)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    val msg = "Erreur lors de la création : ${e.message ?: "inconnue"}"
                    postMessage(msg)
                    onError(msg)
                }
            }
        }
    }

    fun openProject(projectId: String, onReady: () -> Unit) {
        val project = projectRepo.getProject(projectId) ?: return
        openProjectInternal(project)
        onReady()
    }

    private fun openProjectInternal(project: ProjectInfo) {
        val tree = fileRepo.buildFileTree(project.rootPath)
        val primary = fileRepo.findPrimarySourceFile(project.rootPath, project.moduleName)
        val cfg = configRepo.loadConfiguration(project)
        val diags = CodeAnalyzer.analyzeProject(project.rootPath)

        val tabs = if (primary != null && primary.exists()) {
            val content = fileRepo.readFile(primary.absolutePath)
            val rel = primary.relativeTo(File(project.rootPath)).path.replace(File.separatorChar, '/')
            listOf(
                EditorTab(
                    filePath = primary.absolutePath,
                    relativePath = rel,
                    fileName = primary.name,
                    language = IdeLanguage.fromFileName(primary.name),
                    content = content,
                    savedContent = content
                )
            )
        } else emptyList()

        val activeDiags = if (tabs.isNotEmpty()) {
            diags.filter { it.filePath == tabs[0].filePath }
        } else emptyList()

        updateState {
            it.copy(
                currentProject = project,
                fileTree = tree,
                openTabs = tabs,
                activeTabIndex = if (tabs.isNotEmpty()) 0 else -1,
                projectDiagnostics = diags,
                activeFileDiagnostics = activeDiags,
                modules = cfg.modules,
                dependencies = cfg.dependencies,
                configErrors = cfg.configErrors,
                clipboardOp = null
            )
        }
    }

    fun renameProject(projectId: String, newName: String) {
        val updated = projectRepo.renameProject(projectId, newName)
        refreshProjects()
        if (updated != null && uiState.currentProject?.id == projectId) {
            updateState { it.copy(currentProject = updated) }
            refreshFileTree()
        }
    }

    fun duplicateProject(projectId: String) {
        executor.execute {
            projectRepo.duplicateProject(projectId)
            mainHandler.post {
                refreshProjects()
                refreshStorageStats()
                postMessage("Projet dupliqué avec succès")
            }
        }
    }

    fun deleteProject(projectId: String) {
        executor.execute {
            projectRepo.deleteProject(projectId)
            mainHandler.post {
                refreshProjects()
                refreshStorageStats()
                if (uiState.currentProject?.id == projectId) {
                    updateState {
                        it.copy(currentProject = null, openTabs = emptyList(), activeTabIndex = -1)
                    }
                }
                postMessage("Projet supprimé")
            }
        }
    }

    fun setFileSearchQuery(q: String) {
        updateState { it.copy(fileSearchQuery = q) }
    }

    fun refreshFileTree() {
        val project = uiState.currentProject ?: return
        val tree = fileRepo.buildFileTree(project.rootPath)
        updateState { it.copy(fileTree = tree) }
    }

    fun openFile(filePath: String, lineToJump: Int? = null) {
        val project = uiState.currentProject ?: return
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return

        val existingIdx = uiState.openTabs.indexOfFirst { it.filePath == filePath }
        if (existingIdx >= 0) {
            val updated = uiState.openTabs.toMutableList()
            if (lineToJump != null) {
                updated[existingIdx] = updated[existingIdx].copy(pendingLineJump = lineToJump)
            }
            val activeTab = updated[existingIdx]
            val activeDiags = CodeAnalyzer.analyzeFile(
                activeTab.filePath,
                activeTab.fileName,
                activeTab.content,
                activeTab.language
            )
            updateState {
                it.copy(
                    openTabs = updated,
                    activeTabIndex = existingIdx,
                    activeFileDiagnostics = activeDiags
                )
            }
            return
        }

        val content = fileRepo.readFile(filePath)
        val rel = runCatching { file.relativeTo(File(project.rootPath)).path.replace(File.separatorChar, '/') }
            .getOrDefault(file.name)
        val tab = EditorTab(
            filePath = filePath,
            relativePath = rel,
            fileName = file.name,
            language = IdeLanguage.fromFileName(file.name),
            content = content,
            savedContent = content,
            pendingLineJump = lineToJump
        )
        val updatedTabs = uiState.openTabs + tab
        val activeDiags = CodeAnalyzer.analyzeFile(tab.filePath, tab.fileName, tab.content, tab.language)
        updateState {
            it.copy(
                openTabs = updatedTabs,
                activeTabIndex = updatedTabs.lastIndex,
                activeFileDiagnostics = activeDiags
            )
        }
    }

    fun consumePendingLineJump(tabIndex: Int) {
        val tabs = uiState.openTabs.toMutableList()
        val tab = tabs.getOrNull(tabIndex) ?: return
        if (tab.pendingLineJump == null) return
        tabs[tabIndex] = tab.copy(pendingLineJump = null)
        uiState = uiState.copy(openTabs = tabs)
    }

    fun selectTab(index: Int) {
        if (index !in uiState.openTabs.indices) return
        val tab = uiState.openTabs[index]
        val diags = CodeAnalyzer.analyzeFile(tab.filePath, tab.fileName, tab.content, tab.language)
        updateState {
            it.copy(activeTabIndex = index, activeFileDiagnostics = diags)
        }
    }

    fun closeTab(index: Int) {
        val tabs = uiState.openTabs.toMutableList()
        if (index !in tabs.indices) return
        val target = tabs[index]
        if (target.isDirty) {
            fileRepo.writeFile(target.filePath, target.content)
        }
        tabs.removeAt(index)
        val newActive = when {
            tabs.isEmpty() -> -1
            index <= uiState.activeTabIndex -> (uiState.activeTabIndex - 1).coerceAtLeast(0)
            else -> uiState.activeTabIndex
        }
        val activeDiags = if (newActive in tabs.indices) {
            val t = tabs[newActive]
            CodeAnalyzer.analyzeFile(t.filePath, t.fileName, t.content, t.language)
        } else emptyList()
        updateState {
            it.copy(openTabs = tabs, activeTabIndex = newActive, activeFileDiagnostics = activeDiags)
        }
    }

    fun updateActiveEditorContent(newContent: String, cursorIndex: Int) {
        val idx = uiState.activeTabIndex
        val current = uiState.openTabs.getOrNull(idx) ?: return
        if (current.content == newContent) {
            val tabs = uiState.openTabs.toMutableList()
            tabs[idx] = current.copy(cursorIndex = cursorIndex)
            uiState = uiState.copy(openTabs = tabs)
            return
        }

        val updatedUndo = (current.undoStack + current.content).takeLast(40)
        val updatedTab = current.copy(
            content = newContent,
            cursorIndex = cursorIndex,
            undoStack = updatedUndo,
            redoStack = emptyList()
        )
        val tabs = uiState.openTabs.toMutableList()
        tabs[idx] = updatedTab
        // Update state immediately without forcing full Editor EditText reset
        uiState = uiState.copy(openTabs = tabs)

        scheduleAutoSave()
        scheduleLiveAnalyze()
    }

    fun undo() {
        val idx = uiState.activeTabIndex
        val current = uiState.openTabs.getOrNull(idx) ?: return
        if (current.undoStack.isEmpty()) return
        val prev = current.undoStack.last()
        val tabs = uiState.openTabs.toMutableList()
        tabs[idx] = current.copy(
            content = prev,
            undoStack = current.undoStack.dropLast(1),
            redoStack = (current.redoStack + current.content).takeLast(40)
        )
        updateState { it.copy(openTabs = tabs) }
        runImmediateAnalyze()
    }

    fun redo() {
        val idx = uiState.activeTabIndex
        val current = uiState.openTabs.getOrNull(idx) ?: return
        if (current.redoStack.isEmpty()) return
        val next = current.redoStack.last()
        val tabs = uiState.openTabs.toMutableList()
        tabs[idx] = current.copy(
            content = next,
            undoStack = (current.undoStack + current.content).takeLast(40),
            redoStack = current.redoStack.dropLast(1)
        )
        updateState { it.copy(openTabs = tabs) }
        runImmediateAnalyze()
    }

    fun replaceOne(find: String, replacement: String, fromIndex: Int): Int? {
        if (find.isEmpty()) return null
        val tab = uiState.activeTab ?: return null
        val matchIdx = tab.content.indexOf(find, startIndex = fromIndex.coerceAtLeast(0), ignoreCase = true)
            .let { if (it >= 0) it else tab.content.indexOf(find, ignoreCase = true) }
        if (matchIdx < 0) {
            postMessage("Aucune occurrence trouvée")
            return null
        }
        val newText = tab.content.replaceRange(matchIdx, matchIdx + find.length, replacement)
        val newCursor = matchIdx + replacement.length
        updateActiveEditorContent(newText, newCursor)
        notifyChanged()
        return newCursor
    }

    fun replaceAll(find: String, replacement: String) {
        if (find.isEmpty()) return
        val tab = uiState.activeTab ?: return
        val regex = Regex(Regex.escape(find), RegexOption.IGNORE_CASE)
        val count = regex.findAll(tab.content).count()
        if (count == 0) {
            postMessage("Aucune occurrence trouvée")
            return
        }
        val newText = regex.replace(tab.content, Regex.escapeReplacement(replacement))
        updateActiveEditorContent(newText, tab.cursorIndex.coerceAtMost(newText.length))
        notifyChanged()
        postMessage("$count occurrence(s) remplacée(s)")
    }

    fun saveActiveFile(showToast: Boolean = true) {
        val idx = uiState.activeTabIndex
        val tab = uiState.openTabs.getOrNull(idx) ?: return
        fileRepo.writeFile(tab.filePath, tab.content)
        uiState.currentProject?.let { projectRepo.touchProjectModified(it.id) }
        val tabs = uiState.openTabs.toMutableList()
        tabs[idx] = tab.copy(savedContent = tab.content)
        updateState {
            it.copy(
                openTabs = tabs,
                snackbarMessage = if (showToast) "${tab.fileName} sauvegardé" else it.snackbarMessage
            )
        }
        refreshProjects()
    }

    fun saveAllOpenFiles() {
        val tabs = uiState.openTabs.map { tab ->
            if (tab.isDirty) {
                fileRepo.writeFile(tab.filePath, tab.content)
                tab.copy(savedContent = tab.content)
            } else tab
        }
        uiState.currentProject?.let { projectRepo.touchProjectModified(it.id) }
        updateState { it.copy(openTabs = tabs) }
        refreshProjects()
    }

    fun createNewFile(parentDirPath: String, fileName: String) {
        val created = fileRepo.createFile(parentDirPath, fileName)
        if (created == null) {
            postMessage("Impossible de créer '$fileName' (existe déjà ou nom invalide)")
            return
        }
        refreshFileTree()
        openFile(created.absolutePath)
        postMessage("Fichier '${created.name}' créé")
    }

    fun createNewFolder(parentDirPath: String, folderName: String) {
        val created = fileRepo.createFolder(parentDirPath, folderName)
        if (created == null) {
            postMessage("Impossible de créer le dossier '$folderName'")
            return
        }
        refreshFileTree()
        postMessage("Dossier '${created.name}' créé")
    }

    fun renameFileOrFolder(path: String, newName: String) {
        val oldFile = File(path)
        val renamed = fileRepo.renameNode(path, newName)
        if (renamed == null) {
            postMessage("Renommage impossible")
            return
        }
        val projectRoot = uiState.currentProject?.rootPath ?: ""
        val updatedTabs = uiState.openTabs.map { tab ->
            if (tab.filePath == oldFile.absolutePath) {
                val rel = runCatching { renamed.relativeTo(File(projectRoot)).path.replace(File.separatorChar, '/') }
                    .getOrDefault(renamed.name)
                tab.copy(
                    filePath = renamed.absolutePath,
                    relativePath = rel,
                    fileName = renamed.name,
                    language = IdeLanguage.fromFileName(renamed.name)
                )
            } else tab
        }
        updateState { it.copy(openTabs = updatedTabs) }
        refreshFileTree()
        postMessage("Renommé en '${renamed.name}'")
    }

    fun deleteFileOrFolder(path: String) {
        val ok = fileRepo.deleteNode(path)
        if (!ok) {
            postMessage("Suppression impossible")
            return
        }
        val tabs = uiState.openTabs.filterNot { it.filePath == path || it.filePath.startsWith("$path/") }
        val newActive = if (tabs.isEmpty()) -1 else uiState.activeTabIndex.coerceAtMost(tabs.lastIndex)
        updateState { it.copy(openTabs = tabs, activeTabIndex = newActive) }
        refreshFileTree()
        postMessage("Élément supprimé")
    }

    fun setClipboardCopy(node: FileNode) {
        updateState {
            it.copy(
                clipboardOp = ClipboardFileOp(node.absolutePath, node.name, isMove = false),
                snackbarMessage = "'${node.name}' copié — choisissez un dossier puis Coller"
            )
        }
    }

    fun setClipboardMove(node: FileNode) {
        updateState {
            it.copy(
                clipboardOp = ClipboardFileOp(node.absolutePath, node.name, isMove = true),
                snackbarMessage = "'${node.name}' prêt à déplacer — choisissez la destination"
            )
        }
    }

    fun clearClipboard() {
        updateState { it.copy(clipboardOp = null) }
    }

    fun pasteClipboardInto(destDirPath: String) {
        val op = uiState.clipboardOp ?: return
        val result = if (op.isMove) {
            fileRepo.moveNodeToDir(op.sourcePath, destDirPath)
        } else {
            fileRepo.copyNodeToDir(op.sourcePath, destDirPath)
        }
        if (result == null) {
            postMessage("Impossible de coller ici")
            return
        }
        if (op.isMove) {
            val remainingTabs = uiState.openTabs.filterNot {
                it.filePath == op.sourcePath || it.filePath.startsWith("${op.sourcePath}/")
            }
            val newIdx = if (remainingTabs.isEmpty()) -1 else uiState.activeTabIndex.coerceAtMost(remainingTabs.lastIndex)
            updateState { it.copy(openTabs = remainingTabs, activeTabIndex = newIdx, clipboardOp = null) }
        } else {
            updateState { it.copy(clipboardOp = null) }
        }
        refreshFileTree()
        postMessage(if (op.isMove) "Déplacé vers '${result.name}'" else "Copié : '${result.name}'")
    }

    fun importDocumentUri(uri: Uri, destDirPath: String) {
        executor.execute {
            val imported = fileRepo.importFromUri(uri, destDirPath)
            mainHandler.post {
                if (imported != null) {
                    refreshFileTree()
                    openFile(imported.absolutePath)
                    postMessage("Importé : ${imported.name}")
                } else {
                    postMessage("Échec de l'importation")
                }
            }
        }
    }

    fun loadProjectConfig() {
        val project = uiState.currentProject ?: return
        val loaded = configRepo.loadConfiguration(project)
        updateState {
            it.copy(
                modules = loaded.modules,
                dependencies = loaded.dependencies,
                configErrors = loaded.configErrors
            )
        }
    }

    fun applyProjectGeneralAndDeps(
        appName: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        minSdk: Int,
        targetSdk: Int,
        dependencies: List<DependencyItem>
    ) {
        val project = uiState.currentProject ?: return
        val updated = configRepo.applyGeneralAndDependencies(
            project = project,
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            dependencies = dependencies
        )
        projectRepo.updateProjectMetadata(updated)
        val reloaded = configRepo.loadConfiguration(updated)
        updateState {
            it.copy(
                currentProject = updated,
                modules = reloaded.modules,
                dependencies = reloaded.dependencies,
                configErrors = reloaded.configErrors,
                snackbarMessage = "Configuration appliquée au projet"
            )
        }
        refreshProjects()
        refreshFileTree()
        reloadOpenTabsFromDisk()
    }

    fun addModule(name: String, type: String, packageName: String) {
        val project = uiState.currentProject ?: return
        val ok = configRepo.addModule(project, name, type, packageName)
        if (!ok) {
            postMessage("Impossible de créer le module '$name'")
            return
        }
        loadProjectConfig()
        refreshFileTree()
        postMessage("Module ':$name' ajouté")
    }

    fun removeModule(moduleName: String) {
        val project = uiState.currentProject ?: return
        val ok = configRepo.removeModule(project, moduleName)
        if (!ok) {
            postMessage("Le module principal ne peut pas être supprimé")
            return
        }
        loadProjectConfig()
        refreshFileTree()
        postMessage("Module ':$moduleName' supprimé")
    }

    fun updateModule(module: ModuleConfig) {
        val project = uiState.currentProject ?: return
        configRepo.updateModuleConfig(project, module)
        loadProjectConfig()
        reloadOpenTabsFromDisk()
        postMessage("Module ':${module.name}' mis à jour")
    }

    fun checkDepCompatibility(dep: DependencyItem, targetSdk: Int): Pair<Boolean, String> {
        return configRepo.checkCompatibility(dep, targetSdk)
    }

    fun runCompileProject(onFinished: (BuildResult) -> Unit) {
        val project = uiState.currentProject ?: return
        if (uiState.isCompiling) return
        saveAllOpenFiles()
        updateState {
            it.copy(
                isCompiling = true,
                compileStatusMessage = "Préparation du build...",
                compileLogs = emptyList()
            )
        }
        executor.execute {
            val result = buildEngine.compileProject(
                project = project,
                remoteBridgeUrl = settings.remoteBuildBridgeUrl
            ) { step ->
                mainHandler.post {
                    updateState { s ->
                        s.copy(
                            compileStatusMessage = step,
                            compileLogs = s.compileLogs + step
                        )
                    }
                }
            }
            mainHandler.post {
                refreshStorageStats()
                updateState { s ->
                    s.copy(
                        isCompiling = false,
                        lastBuildResult = result,
                        projectDiagnostics = result.diagnostics,
                        compileLogs = result.buildLogs,
                        activeFileDiagnostics = s.activeTab?.let { tab ->
                            result.diagnostics.filter { it.filePath == tab.filePath }
                        } ?: emptyList()
                    )
                }
                onFinished(result)
            }
        }
    }

    fun updateSettings(transform: (IdeSettings) -> IdeSettings) {
        settings = settingsRepo.updateSettings(transform)
        refreshSdkComponents()
        notifyChanged()
    }

    fun updateToolchain(transform: (ToolchainConfig) -> ToolchainConfig) {
        toolchain = settingsRepo.updateToolchain(transform)
        if (toolchain.remoteBuildBridgeUrl != settings.remoteBuildBridgeUrl) {
            settings = settingsRepo.updateSettings { it.copy(remoteBuildBridgeUrl = toolchain.remoteBuildBridgeUrl) }
        }
        refreshSdkComponents()
        notifyChanged()
    }

    fun installSdkComponent(componentId: String) {
        settingsRepo.installLocalSdkMetadata(componentId)
        refreshSdkComponents()
        postMessage("Composant SDK enregistré localement")
    }

    fun clearBuildCache() {
        val freed = settingsRepo.clearBuildCache()
        refreshStorageStats()
        postMessage("Cache APK nettoyé (${formatBytes(freed)} libérés)")
    }

    fun refreshStorageStats() {
        val (pBytes, bBytes) = settingsRepo.calculateStorageUsageBytes()
        updateState { it.copy(projectsStorageBytes = pBytes, buildsStorageBytes = bBytes) }
    }

    fun exportApkToUri(destUri: Uri): Boolean {
        val apkPath = uiState.lastBuildResult?.apkFilePath ?: return false
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return false
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                apkFile.inputStream().use { input -> input.copyTo(out) }
            }
            postMessage("APK enregistré : ${apkFile.name}")
            true
        } catch (e: Exception) {
            postMessage("Erreur export APK : ${e.message}")
            false
        }
    }

    fun postMessage(message: String) {
        updateState { it.copy(snackbarMessage = message) }
    }

    fun clearPendingMessage() {
        if (uiState.snackbarMessage != null) {
            uiState = uiState.copy(snackbarMessage = null)
        }
    }

    private fun scheduleAutoSave() {
        autoSaveRunnable?.let { mainHandler.removeCallbacks(it) }
        if (!settings.autoSave) return
        val runnable = Runnable {
            saveActiveFile(showToast = false)
        }
        autoSaveRunnable = runnable
        mainHandler.postDelayed(runnable, 900L)
    }

    private fun scheduleLiveAnalyze() {
        analyzeRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { runImmediateAnalyze() }
        analyzeRunnable = runnable
        mainHandler.postDelayed(runnable, 450L)
    }

    private fun runImmediateAnalyze() {
        val tab = uiState.activeTab ?: return
        val fileDiags = CodeAnalyzer.analyzeFile(tab.filePath, tab.fileName, tab.content, tab.language)
        val merged = uiState.projectDiagnostics.filter { it.filePath != tab.filePath } + fileDiags
        updateState {
            it.copy(
                activeFileDiagnostics = fileDiags,
                projectDiagnostics = merged
            )
        }
    }

    private fun reloadOpenTabsFromDisk() {
        val updated = uiState.openTabs.map { tab ->
            val f = File(tab.filePath)
            if (f.exists() && !tab.isDirty) {
                val text = f.readText()
                tab.copy(content = text, savedContent = text)
            } else tab
        }
        updateState { it.copy(openTabs = updated) }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes o"
            bytes < 1024L * 1024L -> String.format("%.1f Ko", bytes / 1024.0)
            else -> String.format("%.2f Mo", bytes / (1024.0 * 1024.0))
        }
    }
}
