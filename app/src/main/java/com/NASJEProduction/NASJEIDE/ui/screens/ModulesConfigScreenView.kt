package com.NASJEProduction.NASJEIDE.ui.screens

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.NASJEProduction.NASJEIDE.data.ProjectConfigRepository
import com.NASJEProduction.NASJEIDE.model.DependencyItem
import com.NASJEProduction.NASJEIDE.model.ModuleConfig
import com.NASJEProduction.NASJEIDE.model.SdkComponentStatus
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjeIcon
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp

class ModulesConfigScreenView(
    context: Context,
    private val controller: IdeController,
    private val onBack: () -> Unit
) : LinearLayout(context) {

    private var palette = NasjePalette(controller.settings.darkTheme)
    private var selectedTab = 0 // 0=Général, 1=Modules, 2=Dépendances
    private var loadedProjectId: String? = null

    private var draftName = ""
    private var draftPkg = ""
    private var draftVerName = "1.0"
    private var draftVerCode = "1"
    private var draftMinSdk = 24
    private var draftTargetSdk = 35
    private var draftDeps: MutableList<DependencyItem> = mutableListOf()

    private lateinit var tabBar: LinearLayout
    private lateinit var validationBanner: LinearLayout
    private lateinit var tabContentContainer: LinearLayout

    init {
        orientation = VERTICAL
        controller.loadProjectConfig()
        syncDraftFromState()
        buildSkeleton()
        refresh()
    }

    private fun syncDraftFromState() {
        val project = controller.uiState.currentProject ?: return
        loadedProjectId = project.id
        draftName = project.name
        draftPkg = project.packageName
        draftVerName = project.versionName
        draftVerCode = project.versionCode.toString()
        draftMinSdk = project.minSdk
        draftTargetSdk = project.targetSdk
        draftDeps = controller.uiState.dependencies.toMutableList()
    }

    private fun buildSkeleton() {
        removeAllViews()
        palette = NasjePalette(controller.settings.darkTheme)
        setBackgroundColor(palette.bgDeep)

        val applyBtn = UiKit.makeActionButton(
            context = context,
            label = "Appliquer",
            bgColor = palette.blue,
            gradientEndColor = palette.violet,
            icon = NasjeIcon.CHECK,
            radiusDp = 10,
            heightDp = 42,
            padHDp = 12,
            textSizeSp = 12.5f,
            tag = "modules_apply_button"
        ) {
            controller.applyProjectGeneralAndDeps(
                appName = draftName,
                packageName = draftPkg,
                versionName = draftVerName,
                versionCode = draftVerCode.toIntOrNull() ?: 1,
                minSdk = draftMinSdk,
                targetSdk = draftTargetSdk,
                dependencies = draftDeps
            )
        }

        addView(
            UiKit.makeTopBar(
                context = context,
                palette = palette,
                title = "Modules & Configuration",
                subtitle = controller.uiState.currentProject?.name ?: "Projet",
                onBack = onBack,
                rightAction = applyBtn
            )
        )
        addView(UiKit.makeDivider(context, palette))

        // 3 Tabs
        tabBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setBackgroundColor(palette.bgPrimary)
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        }
        addView(tabBar)
        addView(UiKit.makeDivider(context, palette))

        validationBanner = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(6))
            visibility = View.GONE
        }
        addView(validationBanner)

        val scroll = ScrollView(context).apply { isFillViewport = true }
        tabContentContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(24))
        }
        scroll.addView(tabContentContainer)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        if (palette.isDark != controller.settings.darkTheme) {
            buildSkeleton()
        }
        val currentId = controller.uiState.currentProject?.id
        if (currentId != null && currentId != loadedProjectId) {
            controller.loadProjectConfig()
            syncDraftFromState()
        }

        renderTabs()
        renderValidationBanner()
        tabContentContainer.removeAllViews()
        when (selectedTab) {
            0 -> renderGeneralTab()
            1 -> renderModulesTab()
            2 -> renderDependenciesTab()
        }
    }

    private fun renderTabs() {
        tabBar.removeAllViews()
        val titles = listOf("Général", "Modules", "Dépendances")
        titles.forEachIndexed { idx, title ->
            val chip = UiKit.makeChip(
                context = context,
                palette = palette,
                label = title,
                selected = idx == selectedTab,
                accentColor = palette.blue,
                tag = "config_tab_$idx"
            ) {
                selectedTab = idx
                refresh()
            }
            tabBar.addView(chip, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (idx < titles.lastIndex) marginEnd = context.dp(8)
            })
        }
    }

    private fun renderValidationBanner() {
        validationBanner.removeAllViews()
        val errors = controller.uiState.configErrors
        if (errors.isEmpty()) {
            validationBanner.visibility = View.GONE
            return
        }
        validationBanner.visibility = View.VISIBLE
        val card = UiKit.makeCard(
            context, palette,
            borderColor = palette.red,
            radiusDp = 12, padH = 12, padV = 10
        )
        card.setBackgroundDrawable(UiKit.roundedBox(context, UiKit.withAlpha(palette.red, 0.14f), palette.red, 1, 12))
        errors.forEach { err ->
            card.addView(UiKit.makeText(context, "• $err", 12f, palette.red, bold = true))
        }
        validationBanner.addView(card)
    }

    private fun renderGeneralTab() {
        val card = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
        card.addView(UiKit.makeText(context, "Identité de l'application", 15f, palette.cyan, bold = true, displayFont = true))
        card.addView(fieldLabel("Nom de l'application"))
        val nameEt = UiKit.makeInput(context, palette, "Nom", draftName)
        nameEt.addTextChangedListener(SimpleWatcher { draftName = it })
        card.addView(nameEt)

        card.addView(fieldLabel("Package / Application ID"))
        val pkgEt = UiKit.makeInput(context, palette, "com.exemple.app", draftPkg, mono = true)
        pkgEt.addTextChangedListener(SimpleWatcher { draftPkg = it })
        card.addView(pkgEt)

        val verRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, context.dp(8), 0, 0)
        }
        val vNameCol = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(fieldLabel("Version Name"))
            val et = UiKit.makeInput(context, palette, "1.0", draftVerName, mono = true)
            et.addTextChangedListener(SimpleWatcher { draftVerName = it })
            addView(et)
        }
        val vCodeCol = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(fieldLabel("Version Code"))
            val et = UiKit.makeInput(context, palette, "1", draftVerCode, mono = true)
            et.addTextChangedListener(SimpleWatcher { draftVerCode = it })
            addView(et)
        }
        verRow.addView(vNameCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) })
        verRow.addView(vCodeCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(8) })
        card.addView(verRow)
        tabContentContainer.addView(card, blockParams())

        // SDK Target / Min card
        val sdkCard = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
        sdkCard.addView(UiKit.makeText(context, "Niveaux SDK Android", 15f, palette.cyan, bold = true, displayFont = true))
        sdkCard.addView(fieldLabel("Minimum SDK : API $draftMinSdk"))
        sdkCard.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            listOf(21, 24, 26, 28, 30, 33).forEach { api ->
                row.addView(UiKit.makeChip(context, palette, "API $api", api == draftMinSdk, palette.cyan) {
                    draftMinSdk = api
                    refresh()
                }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = context.dp(6)
                })
            }
            addView(row)
        })
        sdkCard.addView(fieldLabel("Target SDK : API $draftTargetSdk"))
        sdkCard.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            listOf(33, 34, 35, 36).forEach { api ->
                row.addView(UiKit.makeChip(context, palette, "API $api", api == draftTargetSdk, palette.violet) {
                    draftTargetSdk = api
                    refresh()
                }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = context.dp(6)
                })
            }
            addView(row)
        })
        tabContentContainer.addView(sdkCard, blockParams())

        // Toolchain Versions & SDK Manager
        val tc = controller.toolchain
        val tcCard = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
        tcCard.addView(UiKit.makeText(context, "Toolchain & Outils de build", 15f, palette.cyan, bold = true, displayFont = true))
        tcCard.addView(makeSpinnerRow("Android Gradle Plugin (AGP)", listOf("8.7.3", "8.6.1", "8.5.2"), tc.agpVersion) { v ->
            controller.updateToolchain { it.copy(agpVersion = v) }
        })
        tcCard.addView(makeSpinnerRow("Version Gradle", listOf("8.10.2", "8.9", "8.7"), tc.gradleVersion) { v ->
            controller.updateToolchain { it.copy(gradleVersion = v) }
        })
        tcCard.addView(makeSpinnerRow("Version JDK", listOf("17", "21", "11"), tc.jdkVersion) { v ->
            controller.updateToolchain { it.copy(jdkVersion = v) }
        })
        tcCard.addView(makeSpinnerRow("Version CMake", listOf("3.22.1", "3.28.0"), tc.cmakeVersion) { v ->
            controller.updateToolchain { it.copy(cmakeVersion = v) }
        })
        tcCard.addView(makeSpinnerRow("Version NDK", listOf("27.1.12297006", "26.3.11579264"), tc.ndkVersion) { v ->
            controller.updateToolchain { it.copy(ndkVersion = v) }
        })

        tcCard.addView(
            UiKit.makeActionButton(
                context,
                "Gestionnaire des SDK",
                palette.surfaceElevated,
                palette.textPrimary,
                palette.blue,
                icon = NasjeIcon.LAYERS,
                heightDp = 46
            ) { showSdkManagerDialog() },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(10)
            }
        )
        tabContentContainer.addView(tcCard, blockParams())
    }

    private fun renderModulesTab() {
        val addBtn = UiKit.makeActionButton(
            context,
            "Ajouter un module",
            palette.blue,
            gradientEndColor = palette.violet,
            icon = NasjeIcon.PLUS,
            heightDp = 48
        ) { showAddModuleDialog() }
        tabContentContainer.addView(addBtn, blockParams())

        val modules = controller.uiState.modules
        val mainModuleName = controller.uiState.currentProject?.moduleName ?: "app"
        modules.forEach { mod ->
            val card = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val info = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(UiKit.makeText(context, ":${mod.name}", 15.5f, palette.textPrimary, bold = true, mono = true))
                addView(UiKit.makeText(context, "${mod.type} • ${mod.packageName}", 12f, palette.textSecondary))
                addView(UiKit.makeText(context, "minSdk ${mod.minSdk} • targetSdk ${mod.targetSdk} • v${mod.versionName}", 11.5f, palette.textMuted))
            }
            row.addView(info, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(UiKit.makeIconButton(context, NasjeIcon.EDIT, palette.cyan, "Modifier module ${mod.name}") {
                showEditModuleDialog(mod)
            })
            if (mod.name != mainModuleName) {
                row.addView(UiKit.makeIconButton(context, NasjeIcon.DELETE, palette.red, "Supprimer module ${mod.name}") {
                    controller.removeModule(mod.name)
                })
            }
            card.addView(row)
            tabContentContainer.addView(card, blockParams())
        }
    }

    private fun renderDependenciesTab() {
        val addBtn = UiKit.makeActionButton(
            context,
            "Ajouter une dépendance",
            palette.blue,
            gradientEndColor = palette.violet,
            icon = NasjeIcon.PLUS,
            heightDp = 48
        ) { showAddDependencyDialog() }
        tabContentContainer.addView(addBtn, blockParams())

        if (draftDeps.isEmpty()) {
            val empty = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
            empty.addView(
                UiKit.makeText(
                    context,
                    "Aucune dépendance externe.\nLe compilateur embarqué compile directement les APIs Android SDK standard sans configuration supplémentaire.",
                    13f,
                    palette.textSecondary
                )
            )
            tabContentContainer.addView(empty, blockParams())
        } else {
            draftDeps.forEachIndexed { idx, dep ->
                val card = UiKit.makeCard(
                    context, palette,
                    borderColor = if (dep.isCompatible) palette.surfaceBorder else palette.amber,
                    radiusDp = 14
                )
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val info = LinearLayout(context).apply {
                    orientation = VERTICAL
                    addView(UiKit.makeText(context, "${dep.group}:${dep.artifact}", 13.5f, palette.textPrimary, bold = true, mono = true))
                    addView(UiKit.makeText(context, "${dep.configuration} • v${dep.version}", 12f, palette.cyan))
                    addView(
                        UiKit.makeText(
                            context,
                            dep.compatibilityMessage,
                            11.5f,
                            if (dep.isCompatible) palette.green else palette.amber
                        )
                    )
                }
                row.addView(info, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(UiKit.makeIconButton(context, NasjeIcon.DELETE, palette.red, "Supprimer dépendance") {
                    draftDeps.removeAt(idx)
                    refresh()
                })
                card.addView(row)
                tabContentContainer.addView(card, blockParams())
            }
        }

        // Popular suggestions
        tabContentContainer.addView(
            UiKit.makeText(context, "Suggestions populaires", 14f, palette.textSecondary, bold = true).apply {
                setPadding(0, context.dp(8), 0, context.dp(8))
            }
        )
        ProjectConfigRepository.POPULAR_DEPENDENCIES.forEach { preset ->
            val already = draftDeps.any { it.group == preset.group && it.artifact == preset.artifact }
            val card = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 12, padH = 12, padV = 10)
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val info = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(UiKit.makeText(context, preset.coordinate, 12.5f, palette.textPrimary, mono = true, bold = true))
                addView(UiKit.makeText(context, preset.configuration, 11f, palette.textMuted))
            }
            row.addView(info, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (already) {
                row.addView(UiKit.makeText(context, "Ajoutée", 12f, palette.green, bold = true))
            } else {
                row.addView(UiKit.makeActionButton(context, "Ajouter", palette.surfaceElevated, palette.cyan, palette.cyan, heightDp = 38, padHDp = 10, textSizeSp = 12f) {
                    val (ok, msg) = controller.checkDepCompatibility(preset, draftTargetSdk)
                    draftDeps.add(preset.copy(isCompatible = ok, compatibilityMessage = msg))
                    refresh()
                })
            }
            card.addView(row)
            tabContentContainer.addView(card, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(8)
            })
        }
    }

    private fun makeSpinnerRow(
        label: String,
        options: List<String>,
        current: String,
        onSelected: (String) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(6), 0, context.dp(6))
        }
        row.addView(
            UiKit.makeText(context, label, 13f, palette.textSecondary),
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val spinner = Spinner(context).apply {
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
            this.adapter = adapter
            val idx = options.indexOf(current).coerceAtLeast(0)
            setSelection(idx, false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val chosen = options[pos]
                    if (chosen != current) onSelected(chosen)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        row.addView(spinner)
        return row
    }

    private fun showAddModuleDialog() {
        val nameEt = UiKit.makeInput(context, palette, "Nom du module (ex: core)", mono = true)
        val pkgEt = UiKit.makeInput(context, palette, "Package (ex: com.nasje.core)", "${draftPkg}.core", mono = true)
        var typeChoice = "android-library"
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(8))
            addView(nameEt, blockParams())
            addView(pkgEt, blockParams())
            val typeSpinner = Spinner(context).apply {
                val items = listOf("android-library", "application")
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        typeChoice = items[pos]
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) = Unit
                }
            }
            addView(typeSpinner)
        }
        AlertDialog.Builder(context)
            .setTitle("Nouveau module")
            .setView(container)
            .setPositiveButton("Créer") { _, _ ->
                val n = nameEt.text.toString().trim()
                val p = pkgEt.text.toString().trim()
                if (n.isNotEmpty() && p.isNotEmpty()) controller.addModule(n, typeChoice, p)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showEditModuleDialog(mod: ModuleConfig) {
        val pkgEt = UiKit.makeInput(context, palette, "Package", mod.packageName, mono = true)
        val minEt = UiKit.makeInput(context, palette, "minSdk", mod.minSdk.toString(), mono = true)
        val targetEt = UiKit.makeInput(context, palette, "targetSdk", mod.targetSdk.toString(), mono = true)
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(8))
            addView(pkgEt, blockParams())
            addView(minEt, blockParams())
            addView(targetEt)
        }
        AlertDialog.Builder(context)
            .setTitle("Configurer :${mod.name}")
            .setView(container)
            .setPositiveButton("Enregistrer") { _, _ ->
                controller.updateModule(
                    mod.copy(
                        packageName = pkgEt.text.toString().trim().ifEmpty { mod.packageName },
                        minSdk = minEt.text.toString().toIntOrNull() ?: mod.minSdk,
                        targetSdk = targetEt.text.toString().toIntOrNull() ?: mod.targetSdk
                    )
                )
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showAddDependencyDialog() {
        val coordEt = UiKit.makeInput(context, palette, "group:artifact:version", mono = true)
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(8))
            addView(coordEt)
        }
        AlertDialog.Builder(context)
            .setTitle("Ajouter une dépendance Maven")
            .setView(container)
            .setPositiveButton("Ajouter") { _, _ ->
                val parts = coordEt.text.toString().trim().split(':')
                if (parts.size == 3) {
                    val raw = DependencyItem(parts[0], parts[1], parts[2], "implementation")
                    val (ok, msg) = controller.checkDepCompatibility(raw, draftTargetSdk)
                    draftDeps.add(raw.copy(isCompatible = ok, compatibilityMessage = msg))
                    refresh()
                } else {
                    controller.postMessage("Format attendu : groupe:artefact:version")
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showSdkManagerDialog() {
        val scroll = ScrollView(context)
        val col = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
        }
        controller.sdkComponents.forEach { comp ->
            val card = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 10, padH = 10, padV = 8)
            card.addView(UiKit.makeText(context, comp.name, 13.5f, palette.textPrimary, bold = true))
            card.addView(UiKit.makeText(context, "${comp.version} • ${comp.status.label}", 11.5f, palette.cyan))
            card.addView(UiKit.makeText(context, comp.description, 11.5f, palette.textSecondary))
            if (comp.status == SdkComponentStatus.AVAILABLE_DOWNLOAD) {
                card.addView(
                    UiKit.makeActionButton(context, "Installer le profil", palette.blue, heightDp = 38, padHDp = 10, textSizeSp = 12f) {
                        controller.installSdkComponent(comp.id)
                    },
                    LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) }
                )
            }
            col.addView(card, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(8)
            })
        }
        scroll.addView(col)
        AlertDialog.Builder(context)
            .setTitle("Gestionnaire des SDK NASJE")
            .setView(scroll)
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun fieldLabel(text: String) = UiKit.makeText(
        context, text, 12f, palette.textSecondary, bold = true
    ).apply { setPadding(0, context.dp(8), 0, context.dp(4)) }

    private fun blockParams() = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = context.dp(12)
    }

    private class SimpleWatcher(private val onChanged: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChanged(s?.toString() ?: "")
    }
}
