package com.NASJEProduction.NASJEIDE.ui.screens

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.NASJEProduction.NASJEIDE.model.DiagnosticItem
import com.NASJEProduction.NASJEIDE.model.DiagnosticSeverity
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjeIcon
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp

class ErrorsScreenView(
    context: Context,
    private val controller: IdeController,
    private val onBack: () -> Unit,
    private val onNavigateToDiagnostic: (filePath: String, line: Int) -> Unit
) : LinearLayout(context) {

    private var palette = NasjePalette(controller.settings.darkTheme)
    private var selectedTab = 0 // 0=Erreurs, 1=Avertissements, 2=Sortie de compilation
    private var selectedDiagId: String? = null

    private lateinit var tabBar: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var detailsContainer: LinearLayout

    init {
        orientation = VERTICAL
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
                title = "Diagnostics & Compilation",
                subtitle = controller.uiState.currentProject?.name ?: "Projet",
                onBack = onBack
            )
        )
        addView(UiKit.makeDivider(context, palette))

        tabBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setBackgroundColor(palette.bgPrimary)
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        }
        addView(tabBar)
        addView(UiKit.makeDivider(context, palette))

        val scroll = ScrollView(context).apply { isFillViewport = true }
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(14), context.dp(14), context.dp(14), context.dp(24))
        }
        listContainer = LinearLayout(context).apply { orientation = VERTICAL }
        detailsContainer = LinearLayout(context).apply { orientation = VERTICAL }
        body.addView(listContainer)
        body.addView(detailsContainer)
        scroll.addView(body)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        if (palette.isDark != controller.settings.darkTheme) {
            buildSkeleton()
        }
        val diags = controller.uiState.projectDiagnostics
        val errors = diags.filter { it.severity == DiagnosticSeverity.ERROR }
        val warnings = diags.filter { it.severity == DiagnosticSeverity.WARNING }
        val logs = controller.uiState.compileLogs

        tabBar.removeAllViews()
        val tabs = listOf(
            "Erreurs (${errors.size})",
            "Avert. (${warnings.size})",
            "Sortie"
        )
        tabs.forEachIndexed { idx, label ->
            val accent = when (idx) {
                0 -> palette.red
                1 -> palette.amber
                else -> palette.cyan
            }
            tabBar.addView(
                UiKit.makeChip(context, palette, label, idx == selectedTab, accent, tag = "errors_tab_$idx") {
                    selectedTab = idx
                    refresh()
                },
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < tabs.lastIndex) marginEnd = context.dp(6)
                }
            )
        }

        listContainer.removeAllViews()
        detailsContainer.removeAllViews()

        if (selectedTab == 2) {
            val card = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 12)
            card.addView(UiKit.makeText(context, "Journal de compilation", 14.5f, palette.cyan, bold = true, displayFont = true))
            if (logs.isEmpty()) {
                card.addView(
                    UiKit.makeText(context, "Aucune sortie de compilation disponible. Lancez RUN pour compiler.", 13f, palette.textSecondary)
                        .apply { setPadding(0, context.dp(8), 0, 0) }
                )
            } else {
                logs.forEach { line ->
                    card.addView(
                        UiKit.makeText(context, line, 12f, palette.textPrimary, mono = true)
                            .apply { setPadding(0, context.dp(4), 0, context.dp(2)) }
                    )
                }
            }
            listContainer.addView(card)
            return
        }

        val items = if (selectedTab == 0) errors else warnings
        if (items.isEmpty()) {
            val okCard = UiKit.makeCard(context, palette, borderColor = palette.green, radiusDp = 14)
            okCard.gravity = Gravity.CENTER
            okCard.addView(
                UiKit.makeText(
                    context,
                    if (selectedTab == 0) "Aucune erreur détectée !" else "Aucun avertissement détecté.",
                    15f,
                    palette.green,
                    bold = true
                )
            )
            listContainer.addView(okCard)
            return
        }

        val selectedItem = items.firstOrNull { it.id == selectedDiagId } ?: items.first()
        selectedDiagId = selectedItem.id

        items.forEach { diag ->
            val isSelected = diag.id == selectedItem.id
            val sevColor = if (diag.severity == DiagnosticSeverity.ERROR) palette.red else palette.amber
            val card = UiKit.makeCard(
                context = context,
                palette = palette,
                elevated = isSelected,
                borderColor = if (isSelected) sevColor else palette.surfaceBorder,
                radiusDp = 12,
                padH = 12,
                padV = 10
            )
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener {
                selectedDiagId = diag.id
                refresh()
            }
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                android.widget.ImageView(context).apply {
                    setImageDrawable(
                        com.NASJEProduction.NASJEIDE.ui.VectorIconDrawable(
                            if (diag.severity == DiagnosticSeverity.ERROR) NasjeIcon.ERROR else NasjeIcon.WARNING,
                            sevColor,
                            context.dp(20)
                        )
                    )
                },
                LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(10) }
            )
            val col = LinearLayout(context).apply { orientation = VERTICAL }
            col.addView(UiKit.makeText(context, diag.typeLabel, 12f, sevColor, bold = true))
            col.addView(UiKit.makeText(context, diag.message, 13.5f, palette.textPrimary, bold = true))
            col.addView(UiKit.makeText(context, "${diag.fileName} • Ligne ${diag.line}, Col ${diag.column}", 11.5f, palette.textSecondary, mono = true))
            row.addView(col, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(row)
            listContainer.addView(card, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(8)
            })
        }

        // Detail card
        val detailCard = UiKit.makeCard(context, palette, borderColor = palette.blue, radiusDp = 14)
        detailCard.addView(UiKit.makeText(context, "Détails de l'erreur", 14.5f, palette.cyan, bold = true, displayFont = true))
        detailCard.addView(
            UiKit.makeText(context, selectedItem.details, 13f, palette.textPrimary)
                .apply { setPadding(0, context.dp(8), 0, context.dp(12)) }
        )
        detailCard.addView(
            UiKit.makeActionButton(
                context = context,
                label = "Aller à la ligne ${selectedItem.line}",
                bgColor = palette.blue,
                gradientEndColor = palette.violet,
                icon = NasjeIcon.EDIT,
                heightDp = 46,
                tag = "go_to_error_line"
            ) {
                onNavigateToDiagnostic(selectedItem.filePath, selectedItem.line)
            }
        )
        detailsContainer.addView(detailCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(8)
        })
    }
}
