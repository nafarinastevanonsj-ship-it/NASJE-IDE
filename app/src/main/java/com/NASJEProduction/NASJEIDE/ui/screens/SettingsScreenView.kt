package com.NASJEProduction.NASJEIDE.ui.screens

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import com.NASJEProduction.NASJEIDE.model.SdkComponentStatus
import com.NASJEProduction.NASJEIDE.model.SyntaxThemePreset
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjeIcon
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp

class SettingsScreenView(
    context: Context,
    private val controller: IdeController,
    private val onBack: () -> Unit
) : LinearLayout(context) {

    private var palette = NasjePalette(controller.settings.darkTheme)
    private lateinit var contentCol: LinearLayout

    init {
        orientation = VERTICAL
        controller.refreshStorageStats()
        buildSkeleton()
        refresh()
    }

    private fun buildSkeleton() {
        removeAllViews()
        palette = NasjePalette(controller.settings.darkTheme)
        setBackgroundColor(palette.bgDeep)

        addView(
            UiKit.makeTopBar(
                context = context,
                palette = palette,
                title = "Paramètres NASJE IDE",
                subtitle = "Éditeur, stockage & SDK embarqué",
                onBack = onBack
            )
        )
        addView(UiKit.makeDivider(context, palette))

        val scroll = ScrollView(context).apply { isFillViewport = true }
        contentCol = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(24))
        }
        scroll.addView(contentCol)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        if (palette.isDark != controller.settings.darkTheme) {
            buildSkeleton()
        }
        contentCol.removeAllViews()
        val s = controller.settings
        val state = controller.uiState

        // 1. Appearance & Behavior Card
        val appCard = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
        appCard.addView(sectionTitle("Interface & Éditeur"))

        appCard.addView(makeSwitchRow("Thème sombre professionnel", s.darkTheme) { checked ->
            controller.updateSettings { it.copy(darkTheme = checked) }
        })
        appCard.addView(makeSwitchRow("Sauvegarde automatique", s.autoSave) { checked ->
            controller.updateSettings { it.copy(autoSave = checked) }
        })

        // Font size SeekBar (11..22sp)
        val sizeLabel = UiKit.makeText(context, "Taille de police : ${s.editorFontSizeSp} sp", 13f, palette.textPrimary, bold = true)
        appCard.addView(sizeLabel.apply { setPadding(0, context.dp(10), 0, context.dp(4)) })
        val seekBar = SeekBar(context).apply {
            max = 11 // 11 + 11 = 22sp
            progress = (s.editorFontSizeSp - 11).coerceIn(0, 11)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    val spVal = 11 + value
                    sizeLabel.text = "Taille de police : $spVal sp"
                    if (fromUser) {
                        controller.updateSettings { it.copy(editorFontSizeSp = spVal) }
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
        appCard.addView(seekBar)

        // Font Family Chips
        appCard.addView(subLabel("Police de l'éditeur"))
        val fontRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf("JetBrains Mono", "Monospace", "Sans Serif").forEach { fam ->
            fontRow.addView(
                UiKit.makeChip(context, palette, fam, s.editorFontFamily == fam, palette.cyan) {
                    controller.updateSettings { it.copy(editorFontFamily = fam) }
                },
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) }
            )
        }
        appCard.addView(fontRow)

        // Syntax Theme Chips
        appCard.addView(subLabel("Coloration syntaxique"))
        val themeRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        SyntaxThemePreset.entries.forEach { preset ->
            themeRow.addView(
                UiKit.makeChip(context, palette, preset.label, s.syntaxTheme == preset, palette.violet) {
                    controller.updateSettings { it.copy(syntaxTheme = preset) }
                },
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) }
            )
        }
        appCard.addView(themeRow)

        // Language Chips
        appCard.addView(subLabel("Langue de l'interface"))
        val langRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf("fr" to "Français", "en" to "English").forEach { (code, label) ->
            langRow.addView(
                UiKit.makeChip(context, palette, label, s.appLanguage == code, palette.blue) {
                    controller.updateSettings { it.copy(appLanguage = code) }
                },
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) }
            )
        }
        appCard.addView(langRow)
        contentCol.addView(appCard, blockParams())

        // 2. Storage & Cache Card
        val storageCard = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
        storageCard.addView(sectionTitle("Stockage & Cache APK"))
        storageCard.addView(
            UiKit.makeText(
                context,
                "Projets enregistrés : ${controller.formatBytes(state.projectsStorageBytes)}\nAPK compilés (cache) : ${controller.formatBytes(state.buildsStorageBytes)}",
                13f,
                palette.textSecondary
            ).apply { setPadding(0, context.dp(4), 0, context.dp(12)) }
        )
        storageCard.addView(
            UiKit.makeActionButton(
                context = context,
                label = "Nettoyer le cache des APK générés",
                bgColor = palette.surfaceElevated,
                textColor = palette.red,
                strokeColor = UiKit.withAlpha(palette.red, 0.5f),
                icon = NasjeIcon.DELETE,
                heightDp = 46,
                tag = "clear_cache_button"
            ) { controller.clearBuildCache() }
        )
        contentCol.addView(storageCard, blockParams())

        // 3. Remote Gradle Bridge Card
        val bridgeCard = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
        bridgeCard.addView(sectionTitle("Pont Gradle Distant (Kotlin / NDK / AAR)"))
        bridgeCard.addView(
            UiKit.makeText(
                context,
                "Optionnel : URL HTTP/HTTPS d'un serveur Gradle pour compiler les projets Kotlin, C++ NDK ou à dépendances Maven.",
                12f,
                palette.textSecondary
            ).apply { setPadding(0, context.dp(4), 0, context.dp(8)) }
        )
        val urlInput = UiKit.makeInput(context, palette, "https://mon-pont-gradle.local:8080", s.remoteBuildBridgeUrl, mono = true)
        bridgeCard.addView(urlInput, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(10)
        })
        bridgeCard.addView(
            UiKit.makeActionButton(context, "Enregistrer l'URL du pont", palette.blue, icon = NasjeIcon.SAVE, heightDp = 46) {
                val url = urlInput.text.toString().trim()
                controller.updateToolchain { it.copy(remoteBuildBridgeUrl = url) }
                controller.postMessage(if (url.isEmpty()) "Pont Gradle désactivé" else "Pont Gradle enregistré")
            }
        )
        contentCol.addView(bridgeCard, blockParams())

        // 4. SDK Components Card
        val sdkCard = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
        sdkCard.addView(sectionTitle("Gestion des SDK"))
        controller.sdkComponents.forEach { comp ->
            val itemBox = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(0, context.dp(8), 0, context.dp(8))
            }
            val topRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topRow.addView(
                UiKit.makeText(context, comp.name, 13.5f, palette.textPrimary, bold = true),
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            val badgeColor = when (comp.status) {
                SdkComponentStatus.INSTALLED_BUILT_IN, SdkComponentStatus.INSTALLED_LOCAL -> palette.green
                SdkComponentStatus.AVAILABLE_DOWNLOAD -> palette.cyan
                SdkComponentStatus.REMOTE_BRIDGE_ONLY -> palette.amber
            }
            topRow.addView(
                UiKit.makeText(context, comp.status.label, 11f, badgeColor, bold = true)
            )
            itemBox.addView(topRow)
            itemBox.addView(UiKit.makeText(context, "${comp.version} • ${comp.sizeLabel}", 11.5f, palette.textMuted))
            itemBox.addView(UiKit.makeText(context, comp.description, 12f, palette.textSecondary))
            if (comp.status == SdkComponentStatus.AVAILABLE_DOWNLOAD) {
                itemBox.addView(
                    UiKit.makeActionButton(context, "Installer", palette.blue, heightDp = 38, padHDp = 12, textSizeSp = 12f) {
                        controller.installSdkComponent(comp.id)
                    },
                    LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = context.dp(6)
                    }
                )
            }
            sdkCard.addView(itemBox)
            sdkCard.addView(UiKit.makeDivider(context, palette))
        }
        contentCol.addView(sdkCard, blockParams())

        // 5. About Card
        val aboutCard = UiKit.makeCard(context, palette, borderColor = UiKit.withAlpha(palette.violet, 0.45f), radiusDp = 14)
        aboutCard.addView(sectionTitle("À propos de NASJE IDE"))
        aboutCard.addView(
            UiKit.makeText(
                context,
                "NASJE IDE v1.0 (com.NASJEProduction.NASJEIDE)\nEnvironnement Android natif autonome : éditeur multi-langages, gestionnaire de modules, compilateur Java/Dalvik DEX + AXML + ARSC et signataire APK v1/v2 intégré.",
                12.5f,
                palette.textSecondary
            ).apply { setPadding(0, context.dp(4), 0, 0) }
        )
        contentCol.addView(aboutCard, blockParams())
    }

    private fun makeSwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(6), 0, context.dp(6))
            addView(
                UiKit.makeText(context, label, 13.5f, palette.textPrimary),
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(Switch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
            })
        }
    }

    private fun sectionTitle(title: String) = UiKit.makeText(
        context, title, 15.5f, palette.cyan, bold = true, displayFont = true
    ).apply { setPadding(0, 0, 0, context.dp(6)) }

    private fun subLabel(title: String) = UiKit.makeText(
        context, title, 12.5f, palette.textSecondary, bold = true
    ).apply { setPadding(0, context.dp(12), 0, context.dp(6)) }

    private fun blockParams() = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = context.dp(14)
    }
}
