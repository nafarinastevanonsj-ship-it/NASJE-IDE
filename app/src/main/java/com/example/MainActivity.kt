package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.IdeViewModel
import com.example.ui.screens.BuildSuccessScreen
import com.example.ui.screens.CreateProjectScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ErrorsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ModulesConfigScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NasjeIdeTheme

object NasjeRoutes {
    const val HOME = "home"
    const val CREATE_PROJECT = "create_project"
    const val EDITOR = "editor"
    const val MODULES_CONFIG = "modules_config"
    const val ERRORS = "errors"
    const val BUILD_SUCCESS = "build_success"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NasjeIdeRoot()
        }
    }
}

@Composable
fun NasjeIdeRoot(viewModel: IdeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toolchain by viewModel.toolchain.collectAsStateWithLifecycle()
    val sdkComponents by viewModel.sdkComponents.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    NasjeIdeTheme(darkTheme = settings.darkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = NasjeRoutes.HOME
            ) {
                composable(NasjeRoutes.HOME) {
                    HomeScreen(
                        state = uiState,
                        onNewProjectClick = {
                            navController.navigate(NasjeRoutes.CREATE_PROJECT)
                        },
                        onOpenProject = { project ->
                            viewModel.openProject(project)
                            navController.navigate(NasjeRoutes.EDITOR)
                        },
                        onRenameProject = viewModel::renameProject,
                        onDuplicateProject = viewModel::duplicateProject,
                        onDeleteProject = viewModel::deleteProject,
                        onSearchChange = viewModel::updateProjectSearch,
                        onSortChange = viewModel::updateProjectSort,
                        onToggleShowAll = viewModel::toggleShowAllProjects,
                        onOpenSettings = {
                            navController.navigate(NasjeRoutes.SETTINGS)
                        }
                    )
                }

                composable(NasjeRoutes.CREATE_PROJECT) {
                    CreateProjectScreen(
                        onBack = { navController.popBackStack() },
                        onCreateProject = { name, pkg, type, lang, mod, ver, minSdk, targetSdk ->
                            viewModel.createProject(
                                name = name,
                                packageName = pkg,
                                projectType = type,
                                primaryLanguage = lang,
                                moduleName = mod,
                                versionName = ver,
                                minSdk = minSdk,
                                targetSdk = targetSdk,
                                onCreated = {
                                    navController.navigate(NasjeRoutes.EDITOR) {
                                        popUpTo(NasjeRoutes.HOME)
                                    }
                                }
                            )
                        }
                    )
                }

                composable(NasjeRoutes.EDITOR) {
                    EditorScreen(
                        state = uiState,
                        settings = settings,
                        onSelectTab = viewModel::selectTab,
                        onCloseTab = viewModel::closeTab,
                        onContentChange = viewModel::updateActiveTabContent,
                        onConsumeLineJump = viewModel::consumePendingLineJump,
                        onUndo = viewModel::undoActiveTab,
                        onRedo = viewModel::redoActiveTab,
                        onSave = { viewModel.saveActiveTab() },
                        onRunBuild = {
                            viewModel.compileActiveProject { result ->
                                if (result.success && !result.apkFilePath.isNullOrBlank()) {
                                    navController.navigate(NasjeRoutes.BUILD_SUCCESS)
                                } else {
                                    navController.navigate(NasjeRoutes.ERRORS)
                                }
                            }
                        },
                        onReplaceText = viewModel::replaceInActiveTab,
                        onOpenFile = { node -> viewModel.openFileInEditor(node.absolutePath) },
                        onCreateFile = viewModel::createFile,
                        onCreateFolder = viewModel::createFolder,
                        onRenameNode = viewModel::renameFileNode,
                        onDeleteNode = viewModel::deleteFileNode,
                        onCopyOrMove = viewModel::copyToClipboard,
                        onPasteInto = viewModel::pasteClipboardInto,
                        onImportUri = viewModel::importExternalFile,
                        onRefreshTree = viewModel::refreshFileTree,
                        onOpenModulesConfig = {
                            viewModel.reloadProjectConfig()
                            navController.navigate(NasjeRoutes.MODULES_CONFIG)
                        },
                        onOpenErrors = {
                            navController.navigate(NasjeRoutes.ERRORS)
                        },
                        onOpenSettings = {
                            navController.navigate(NasjeRoutes.SETTINGS)
                        },
                        onNavigateHome = {
                            navController.popBackStack(NasjeRoutes.HOME, inclusive = false)
                        }
                    )
                }

                composable(NasjeRoutes.MODULES_CONFIG) {
                    ModulesConfigScreen(
                        state = uiState,
                        toolchain = toolchain,
                        sdkComponents = sdkComponents,
                        onBack = { navController.popBackStack() },
                        onApplyGeneralAndDeps = viewModel::applyGeneralAndDependencies,
                        onAddModule = viewModel::addModule,
                        onRemoveModule = viewModel::removeModule,
                        onUpdateModule = viewModel::updateModuleConfig,
                        onUpdateToolchain = viewModel::updateToolchain,
                        onInstallSdkComponent = viewModel::installSdkComponent
                    )
                }

                composable(NasjeRoutes.ERRORS) {
                    ErrorsScreen(
                        state = uiState,
                        onBack = { navController.popBackStack() },
                        onGoToLine = { filePath, line ->
                            viewModel.openFileInEditor(filePath, targetLine = line)
                            navController.popBackStack(NasjeRoutes.EDITOR, inclusive = false)
                        }
                    )
                }

                composable(NasjeRoutes.BUILD_SUCCESS) {
                    BuildSuccessScreen(
                        buildResult = uiState.lastBuildResult,
                        onBackToEditor = {
                            navController.popBackStack(NasjeRoutes.EDITOR, inclusive = false)
                        },
                        onBackHome = {
                            navController.popBackStack(NasjeRoutes.HOME, inclusive = false)
                        },
                        onShowMessage = viewModel::showMessage
                    )
                }

                composable(NasjeRoutes.SETTINGS) {
                    SettingsScreen(
                        state = uiState,
                        settings = settings,
                        sdkComponents = sdkComponents,
                        onBack = { navController.popBackStack() },
                        onUpdateSettings = viewModel::updateSettings,
                        onInstallSdkComponent = viewModel::installSdkComponent,
                        onClearBuildCache = viewModel::clearBuildsCache,
                        onShowMessage = viewModel::showMessage
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp)
            )
        }
    }
}
