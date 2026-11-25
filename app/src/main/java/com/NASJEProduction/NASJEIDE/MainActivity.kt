package com.NASJEProduction.NASJEIDE

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.NASJEProduction.NASJEIDE.provider.NasjeFileProvider
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp
import com.NASJEProduction.NASJEIDE.ui.screens.BuildSuccessScreenView
import com.NASJEProduction.NASJEIDE.ui.screens.CreateProjectScreenView
import com.NASJEProduction.NASJEIDE.ui.screens.EditorScreenView
import com.NASJEProduction.NASJEIDE.ui.screens.ErrorsScreenView
import com.NASJEProduction.NASJEIDE.ui.screens.HomeScreenView
import com.NASJEProduction.NASJEIDE.ui.screens.ModulesConfigScreenView
import com.NASJEProduction.NASJEIDE.ui.screens.SettingsScreenView
import java.io.File

enum class IdeRoute {
    HOME,
    CREATE_PROJECT,
    EDITOR,
    MODULES_CONFIG,
    ERRORS,
    BUILD_SUCCESS,
    SETTINGS
}

class MainActivity : Activity() {

    private companion object {
        const val REQ_IMPORT_DOCUMENT = 2001
        const val REQ_EXPORT_APK = 2002
    }

    private lateinit var controller: IdeController
    private val backStack = mutableListOf(IdeRoute.HOME)
    private var pendingImportDir: String? = null

    private lateinit var rootFrame: FrameLayout
    private lateinit var screenHost: FrameLayout
    private lateinit var compileOverlay: FrameLayout
    private lateinit var compileStatusTv: TextView
    private lateinit var snackbarTv: TextView

    private var homeView: HomeScreenView? = null
    private var createView: CreateProjectScreenView? = null
    private var editorView: EditorScreenView? = null
    private var modulesView: ModulesConfigScreenView? = null
    private var errorsView: ErrorsScreenView? = null
    private var buildSuccessView: BuildSuccessScreenView? = null
    private var settingsView: SettingsScreenView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var snackHideRunnable: Runnable? = null

    private val stateListener: () -> Unit = {
        onControllerChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = IdeController(applicationContext)

        rootFrame = FrameLayout(this)
        screenHost = FrameLayout(this)
        rootFrame.addView(
            screenHost,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        setupCompileOverlay()
        setupSnackbar()
        setupEdgeToEdgeInsets()
        setContentView(rootFrame)

        controller.addListener(stateListener)
        renderCurrentRoute()
        applyWindowBarColors()
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.removeListener(stateListener)
    }

    private fun setupEdgeToEdgeInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            rootFrame.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            rootFrame.fitsSystemWindows = true
        }
    }

    @Suppress("DEPRECATION")
    private fun applyWindowBarColors() {
        val palette = NasjePalette(controller.settings.darkTheme)
        window.statusBarColor = palette.topBarBg
        window.navigationBarColor = palette.topBarBg
        rootFrame.setBackgroundColor(palette.bgDeep)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = window.decorView.systemUiVisibility
            flags = if (palette.isDark) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun setupCompileOverlay() {
        compileOverlay = FrameLayout(this).apply {
            setBackgroundColor(0xB8050914.toInt())
            isClickable = true
            isFocusable = true
            visibility = View.GONE

            val palette = NasjePalette(true)
            val card = UiKit.makeCard(this@MainActivity, palette, borderColor = palette.blue, radiusDp = 18, padH = 22, padV = 22)
            card.gravity = Gravity.CENTER_HORIZONTAL

            card.addView(ProgressBar(this@MainActivity), LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                bottomMargin = dp(14)
            })
            card.addView(
                UiKit.makeText(
                    this@MainActivity,
                    "Compilation APK en cours...",
                    16f,
                    palette.textPrimary,
                    bold = true,
                    displayFont = true
                ).apply { gravity = Gravity.CENTER }
            )
            compileStatusTv = UiKit.makeText(
                this@MainActivity,
                "Initialisation...",
                12.5f,
                palette.cyan
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }
            card.addView(compileStatusTv)

            addView(
                card,
                FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            )
        }
        rootFrame.addView(
            compileOverlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun setupSnackbar() {
        snackbarTv = UiKit.makeText(
            this,
            "",
            13f,
            0xFFF8FAFC.toInt(),
            bold = true
        ).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            background = UiKit.roundedBox(this@MainActivity, 0xFF16223D.toInt(), 0xFF38BDF8.toInt(), 1, 12)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            elevation = 8f
        }
        rootFrame.addView(
            snackbarTv,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(72)
                marginStart = dp(16)
                marginEnd = dp(16)
            }
        )
    }

    private fun currentRoute(): IdeRoute = backStack.lastOrNull() ?: IdeRoute.HOME

    private fun navigateTo(route: IdeRoute, replaceCurrent: Boolean = false) {
        if (replaceCurrent && backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
        }
        if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
        renderCurrentRoute()
    }

    private fun popToHome() {
        backStack.clear()
        backStack.add(IdeRoute.HOME)
        renderCurrentRoute()
    }

    private fun navigateBack() {
        val current = currentRoute()
        if (current == IdeRoute.EDITOR && editorView?.isExplorerOpen() == true) {
            editorView?.closeExplorer()
            return
        }
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            renderCurrentRoute()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        navigateBack()
    }

    private fun renderCurrentRoute() {
        screenHost.removeAllViews()
        val view: View = when (currentRoute()) {
            IdeRoute.HOME -> {
                val v = homeView ?: HomeScreenView(
                    context = this,
                    controller = controller,
                    onNewProjectClick = { navigateTo(IdeRoute.CREATE_PROJECT) },
                    onOpenProject = { project ->
                        controller.openProject(project.id) {
                            navigateTo(IdeRoute.EDITOR)
                        }
                    },
                    onOpenSettings = { navigateTo(IdeRoute.SETTINGS) }
                ).also { homeView = it }
                v.refresh()
                v
            }

            IdeRoute.CREATE_PROJECT -> {
                createView = CreateProjectScreenView(
                    context = this,
                    controller = controller,
                    onBack = { navigateBack() },
                    onProjectCreated = { navigateTo(IdeRoute.EDITOR, replaceCurrent = true) }
                )
                createView!!
            }

            IdeRoute.EDITOR -> {
                val v = editorView ?: EditorScreenView(
                    context = this,
                    controller = controller,
                    onNavigateHome = { popToHome() },
                    onOpenModules = { navigateTo(IdeRoute.MODULES_CONFIG) },
                    onOpenErrors = { navigateTo(IdeRoute.ERRORS) },
                    onOpenSettings = { navigateTo(IdeRoute.SETTINGS) },
                    onRunBuild = { triggerBuild() },
                    onRequestImportIntoDir = { dirPath ->
                        pendingImportDir = dirPath
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQ_IMPORT_DOCUMENT)
                    }
                ).also { editorView = it }
                v.refresh()
                v
            }

            IdeRoute.MODULES_CONFIG -> {
                val v = modulesView ?: ModulesConfigScreenView(
                    context = this,
                    controller = controller,
                    onBack = { navigateBack() }
                ).also { modulesView = it }
                v.refresh()
                v
            }

            IdeRoute.ERRORS -> {
                val v = errorsView ?: ErrorsScreenView(
                    context = this,
                    controller = controller,
                    onBack = { navigateBack() },
                    onNavigateToDiagnostic = { filePath, line ->
                        controller.openFile(filePath, lineToJump = line)
                        navigateBack()
                    }
                ).also { errorsView = it }
                v.refresh()
                v
            }

            IdeRoute.BUILD_SUCCESS -> {
                val v = buildSuccessView ?: BuildSuccessScreenView(
                    context = this,
                    controller = controller,
                    onInstallApk = { path -> installApk(path) },
                    onShareApk = { path -> shareApk(path) },
                    onDownloadApk = { _, fileName ->
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/vnd.android.package-archive"
                            putExtra(Intent.EXTRA_TITLE, fileName)
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQ_EXPORT_APK)
                    },
                    onReturnHome = { popToHome() },
                    onContinueEditor = { navigateBack() }
                ).also { buildSuccessView = it }
                v.refresh()
                v
            }

            IdeRoute.SETTINGS -> {
                val v = settingsView ?: SettingsScreenView(
                    context = this,
                    controller = controller,
                    onBack = { navigateBack() }
                ).also { settingsView = it }
                v.refresh()
                v
            }
        }
        screenHost.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun onControllerChanged() {
        applyWindowBarColors()
        val state = controller.uiState
        if (state.isCompiling) {
            compileOverlay.visibility = View.VISIBLE
            compileStatusTv.text = state.compileStatusMessage
        } else {
            compileOverlay.visibility = View.GONE
        }

        state.snackbarMessage?.let { msg ->
            showFloatingMessage(msg)
            controller.clearPendingMessage()
        }

        when (currentRoute()) {
            IdeRoute.HOME -> homeView?.refresh()
            IdeRoute.EDITOR -> editorView?.refresh()
            IdeRoute.MODULES_CONFIG -> modulesView?.refresh()
            IdeRoute.ERRORS -> errorsView?.refresh()
            IdeRoute.BUILD_SUCCESS -> buildSuccessView?.refresh()
            IdeRoute.SETTINGS -> settingsView?.refresh()
            IdeRoute.CREATE_PROJECT -> Unit
        }
    }

    private fun showFloatingMessage(message: String) {
        snackHideRunnable?.let { handler.removeCallbacks(it) }
        snackbarTv.text = message
        snackbarTv.visibility = View.VISIBLE
        val runnable = Runnable { snackbarTv.visibility = View.GONE }
        snackHideRunnable = runnable
        handler.postDelayed(runnable, 2800L)
    }

    private fun triggerBuild() {
        controller.runCompileProject { result ->
            if (result.success) {
                navigateTo(IdeRoute.BUILD_SUCCESS)
            } else {
                navigateTo(IdeRoute.ERRORS)
            }
        }
    }

    private fun installApk(apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) {
            controller.postMessage("Fichier APK introuvable")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(permIntent)
            controller.postMessage("Autorisez l'installation d'apps inconnues puis réessayez")
            return
        }
        val uri = NasjeFileProvider.getUriForFile(this, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }.onFailure {
            controller.postMessage("Aucun installateur APK disponible : ${it.message}")
        }
    }

    private fun shareApk(apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) {
            controller.postMessage("Fichier APK introuvable")
            return
        }
        val uri = NasjeFileProvider.getUriForFile(this, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Partager l'APK compilé"))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_IMPORT_DOCUMENT -> {
                val targetDir = pendingImportDir ?: controller.uiState.currentProject?.rootPath
                pendingImportDir = null
                if (targetDir != null) {
                    controller.importDocumentUri(uri, targetDir)
                }
            }
            REQ_EXPORT_APK -> {
                controller.exportApkToUri(uri)
            }
        }
    }
}
