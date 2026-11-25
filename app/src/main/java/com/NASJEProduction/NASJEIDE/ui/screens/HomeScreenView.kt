package com.NASJEProduction.NASJEIDE.ui.screens

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import com.NASJEProduction.NASJEIDE.R
import com.NASJEProduction.NASJEIDE.model.ProjectInfo
import com.NASJEProduction.NASJEIDE.model.ProjectSortOrder
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjeIcon
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeScreenView(
    private val context: Context,
    private val controller: IdeController,
    private val onNewProjectClick: () -> Unit,
    private val onOpenProject: (ProjectInfo) -> Unit,
    private val onOpenSettings: () -> Unit
) : FrameLayout(context) {

    private var palette = NasjePalette(controller.settings.darkTheme)
    private var showSearch = false
    private lateinit var rootCol: LinearLayout
    private lateinit var searchBarContainer: LinearLayout
    private lateinit var projectListContainer: LinearLayout
    private lateinit var projectCountText: TextView
    private lateinit var bottomCountText: TextView
    private lateinit var showAllButton: TextView

    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.FRENCH)
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.FRENCH)

    init {
        buildSkeleton()
        refresh()
    }

    private fun buildSkeleton() {
        removeAllViews()
        palette = NasjePalette(controller.settings.darkTheme)
        setBackgroundColor(palette.bgDeep)

        rootCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
        }

        val contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(16))
        }

        // Top Header with Logo, Title, Slogan, Search toggle
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(2), 0, context.dp(14))

            val logoFrame = FrameLayout(context).apply {
                background = UiKit.roundedBox(context, palette.surface, palette.blue, 1, 12)
                setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.img_nasje_logo)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Logo NASJE IDE"
                }, LayoutParams(context.dp(46), context.dp(46)))
            }
            addView(logoFrame, LinearLayout.LayoutParams(context.dp(50), context.dp(50)))

            val titleCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(12), 0, context.dp(8), 0)
                addView(
                    UiKit.makeText(
                        context,
                        "NASJE IDE",
                        21f,
                        palette.textPrimary,
                        bold = true,
                        displayFont = true,
                        maxLines = 1
                    )
                )
                addView(
                    UiKit.makeText(
                        context,
                        "Développez. Compilez. Réalisez.",
                        12.5f,
                        palette.cyan,
                        maxLines = 1
                    )
                )
            }
            addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(
                UiKit.makeIconButton(
                    context = context,
                    icon = if (showSearch) NasjeIcon.CLOSE else NasjeIcon.SEARCH,
                    tint = palette.textPrimary,
                    contentDesc = "Rechercher un projet",
                    bgColor = palette.surface,
                    borderColor = palette.surfaceBorder,
                    tag = "home_search_toggle"
                ) {
                    showSearch = !showSearch
                    if (!showSearch) controller.setProjectSearchQuery("")
                    buildSkeleton()
                    refresh()
                }
            )
        }
        contentCol.addView(headerRow)

        // Collapsible Search Bar
        searchBarContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = if (showSearch) View.VISIBLE else View.GONE
            setPadding(0, 0, 0, context.dp(12))
            val input = UiKit.makeInput(
                context,
                palette,
                hint = "Rechercher par nom ou package...",
                initialValue = controller.uiState.projectSearchQuery,
                tag = "home_search_input"
            )
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    controller.setProjectSearchQuery(s?.toString() ?: "")
                }
            })
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        contentCol.addView(searchBarContainer)

        // Hero Card with illustration + "+ Nouveau Projet" gradient CTA
        val heroCard = UiKit.makeCard(
            context = context,
            palette = palette,
            elevated = false,
            borderColor = UiKit.withAlpha(palette.blue, 0.55f),
            radiusDp = 18,
            padH = 0,
            padV = 0
        )
        heroCard.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.img_hero_ide)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Illustration environnement de développement mobile"
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(140))
        )
        val heroBody = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(16))

            addView(
                UiKit.makeText(
                    context,
                    "Environnement Android Natif Complet",
                    15f,
                    palette.textPrimary,
                    bold = true,
                    displayFont = true
                )
            )
            addView(
                UiKit.makeText(
                    context,
                    "Créez vos applications Java, Kotlin ou C++, éditez avec coloration syntaxique et générez directement des APK signés.",
                    12.5f,
                    palette.textSecondary
                ).apply { setPadding(0, context.dp(4), 0, context.dp(14)) }
            )
            addView(
                UiKit.makeActionButton(
                    context = context,
                    label = "+ Nouveau Projet",
                    bgColor = palette.violet,
                    gradientEndColor = palette.blue,
                    strokeColor = UiKit.withAlpha(palette.cyan, 0.6f),
                    icon = NasjeIcon.PLUS,
                    radiusDp = 14,
                    heightDp = 50,
                    textSizeSp = 15f,
                    tag = "new_project_button"
                ) { onNewProjectClick() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        heroCard.addView(heroBody)
        contentCol.addView(heroCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(18)
        })

        // "Mes Projets" Section Header with Sort & Show All
        val sectionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, context.dp(10))

            addView(
                UiKit.makeText(
                    context,
                    "Mes Projets",
                    17f,
                    palette.textPrimary,
                    bold = true,
                    displayFont = true
                )
            )
            projectCountText = UiKit.makeText(
                context,
                "",
                12f,
                palette.cyan,
                bold = true
            ).apply {
                setPadding(context.dp(8), 0, context.dp(8), 0)
            }
            addView(projectCountText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            showAllButton = UiKit.makeText(
                context,
                "Tout afficher",
                12.5f,
                palette.blue,
                bold = true
            ).apply {
                isClickable = true
                isFocusable = true
                setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
                setOnClickListener { controller.toggleShowAllProjects() }
            }
            addView(showAllButton)

            val sortBtn = UiKit.makeActionButton(
                context = context,
                label = controller.uiState.projectSortOrder.label,
                bgColor = palette.surface,
                textColor = palette.textSecondary,
                strokeColor = palette.surfaceBorder,
                icon = NasjeIcon.SORT,
                radiusDp = 10,
                heightDp = 40,
                padHDp = 10,
                textSizeSp = 11.5f,
                tag = "home_sort_button"
            ) {}
            sortBtn.setOnClickListener { anchor ->
                val menu = PopupMenu(context, anchor)
                ProjectSortOrder.entries.forEachIndexed { idx, order ->
                    menu.menu.add(0, idx, idx, order.label)
                }
                menu.setOnMenuItemClickListener { item ->
                    val chosen = ProjectSortOrder.entries.getOrNull(item.itemId)
                    if (chosen != null) controller.setProjectSortOrder(chosen)
                    true
                }
                menu.show()
            }
            addView(sortBtn)
        }
        contentCol.addView(sectionRow)

        projectListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentCol.addView(projectListContainer)

        scroll.addView(contentCol)
        rootCol.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom Bar with Settings Button
        rootCol.addView(UiKit.makeDivider(context, palette))
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.topBarBg)
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))

            bottomCountText = UiKit.makeText(
                context,
                "0 projet enregistré",
                12.5f,
                palette.textSecondary
            )
            addView(bottomCountText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(
                UiKit.makeActionButton(
                    context = context,
                    label = "Paramètres",
                    bgColor = palette.surface,
                    textColor = palette.textPrimary,
                    strokeColor = palette.surfaceBorder,
                    icon = NasjeIcon.SETTINGS,
                    radiusDp = 12,
                    heightDp = 48,
                    padHDp = 14,
                    textSizeSp = 13f,
                    tag = "settings_button"
                ) { onOpenSettings() }
            )
        }
        rootCol.addView(bottomBar)
        addView(rootCol, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun refresh() {
        val state = controller.uiState
        if (palette.isDark != controller.settings.darkTheme) {
            buildSkeleton()
        }
        val allCount = state.projects.size
        val visible = controller.filteredProjects()

        projectCountText.text = if (allCount > 0) "($allCount)" else ""
        bottomCountText.text = if (allCount == 1) "1 projet enregistré" else "$allCount projets enregistrés"
        showAllButton.visibility = if (allCount > 5 && state.projectSearchQuery.isBlank()) View.VISIBLE else View.GONE
        showAllButton.text = if (state.showAllProjects) "Réduire" else "Tout afficher"

        projectListContainer.removeAllViews()
        if (visible.isEmpty()) {
            val emptyCard = UiKit.makeCard(context, palette, borderColor = palette.surfaceBorder, radiusDp = 14)
            emptyCard.gravity = Gravity.CENTER
            emptyCard.addView(
                UiKit.makeText(
                    context,
                    if (state.projectSearchQuery.isBlank()) {
                        "Aucun projet pour le moment.\nAppuyez sur « + Nouveau Projet » pour commencer."
                    } else {
                        "Aucun projet ne correspond à « ${state.projectSearchQuery} »."
                    },
                    13.5f,
                    palette.textSecondary
                ).apply { gravity = Gravity.CENTER }
            )
            projectListContainer.addView(emptyCard)
            return
        }

        visible.forEach { project ->
            projectListContainer.addView(
                buildProjectCard(project),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = context.dp(10)
                }
            )
        }
    }

    private fun buildProjectCard(project: ProjectInfo): View {
        val card = UiKit.makeCard(
            context = context,
            palette = palette,
            borderColor = UiKit.withAlpha(project.primaryLanguage.accentColorInt, 0.45f),
            radiusDp = 14,
            padH = 12,
            padV = 12
        )
        card.isClickable = true
        card.isFocusable = true
        card.tag = "project_card_${project.id}"
        card.setOnClickListener { onOpenProject(project) }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(UiKit.makeLanguageBadge(context, project.primaryLanguage))

        val infoCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), 0, context.dp(8), 0)
            addView(
                UiKit.makeText(
                    context,
                    project.name,
                    15f,
                    palette.textPrimary,
                    bold = true,
                    maxLines = 1
                )
            )
            addView(
                UiKit.makeText(
                    context,
                    "${project.packageName} • ${project.primaryLanguage.label}",
                    12f,
                    palette.textSecondary,
                    maxLines = 1
                )
            )
            val date = Date(project.lastModifiedAt)
            addView(
                UiKit.makeText(
                    context,
                    "Modifié le ${dateFormatter.format(date)} à ${timeFormatter.format(date)}",
                    11f,
                    palette.textMuted,
                    maxLines = 1
                ).apply { setPadding(0, context.dp(2), 0, 0) }
            )
        }
        row.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val moreBtn = UiKit.makeIconButton(
            context = context,
            icon = NasjeIcon.MORE,
            tint = palette.textSecondary,
            contentDesc = "Options du projet ${project.name}",
            sizeDp = 48,
            tag = "project_menu_${project.id}"
        ) {}
        moreBtn.setOnClickListener { anchor ->
            val popup = PopupMenu(context, anchor)
            popup.menu.add(0, 1, 1, "Ouvrir")
            popup.menu.add(0, 2, 2, "Renommer")
            popup.menu.add(0, 3, 3, "Dupliquer")
            popup.menu.add(0, 4, 4, "Supprimer")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onOpenProject(project)
                    2 -> showRenameDialog(project)
                    3 -> controller.duplicateProject(project.id)
                    4 -> showDeleteConfirmDialog(project)
                }
                true
            }
            popup.show()
        }
        row.addView(moreBtn)
        card.addView(row)
        return card
    }

    private fun showRenameDialog(project: ProjectInfo) {
        val input = UiKit.makeInput(context, palette, "Nouveau nom du projet", project.name)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(4))
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle("Renommer le projet")
            .setView(container)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    controller.renameProject(project.id, newName)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showDeleteConfirmDialog(project: ProjectInfo) {
        AlertDialog.Builder(context)
            .setTitle("Supprimer le projet ?")
            .setMessage("Voulez-vous vraiment supprimer « ${project.name} » et tous ses fichiers ? Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                controller.deleteProject(project.id)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
