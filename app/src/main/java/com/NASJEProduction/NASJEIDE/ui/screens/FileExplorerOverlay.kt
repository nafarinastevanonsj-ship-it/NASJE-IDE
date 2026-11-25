package com.NASJEProduction.NASJEIDE.ui.screens

import android.app.AlertDialog
import android.content.Context
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
import com.NASJEProduction.NASJEIDE.data.FileManagerRepository
import com.NASJEProduction.NASJEIDE.model.FileNode
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjeIcon
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp
import com.NASJEProduction.NASJEIDE.ui.VectorIconDrawable

class FileExplorerOverlay(
    context: Context,
    private val controller: IdeController,
    private val onClose: () -> Unit,
    private val onFileSelected: (String) -> Unit,
    private val onRequestImportIntoDir: (String) -> Unit
) : FrameLayout(context) {

    private val fileRepo = FileManagerRepository(context)
    private var palette = NasjePalette(controller.settings.darkTheme)
    private val expandedDirs = mutableSetOf<String>()

    private lateinit var drawerCard: LinearLayout
    private lateinit var treeContainer: LinearLayout
    private lateinit var clipboardBanner: LinearLayout
    private lateinit var clipboardText: android.widget.TextView
    private lateinit var pasteRootBtn: View

    init {
        setBackgroundColor(0x99000000.toInt())
        isClickable = true
        isFocusable = true
        setOnClickListener { onClose() }
        buildUi()
    }

    private fun buildUi() {
        removeAllViews()
        palette = NasjePalette(controller.settings.darkTheme)

        drawerCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.bgPrimary)
            isClickable = true
            isFocusable = true
        }

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.topBarBg)
            setPadding(context.dp(12), context.dp(10), context.dp(8), context.dp(10))

            addView(
                UiKit.makeText(
                    context,
                    "Explorateur",
                    15.5f,
                    palette.textPrimary,
                    bold = true,
                    displayFont = true
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                UiKit.makeIconButton(context, NasjeIcon.FILE, palette.cyan, "Nouveau fichier", sizeDp = 42, iconDp = 18) {
                    val rootPath = controller.uiState.currentProject?.rootPath ?: return@makeIconButton
                    showCreateFileDialog(rootPath)
                }
            )
            addView(
                UiKit.makeIconButton(context, NasjeIcon.FOLDER, palette.blue, "Nouveau dossier", sizeDp = 42, iconDp = 18) {
                    val rootPath = controller.uiState.currentProject?.rootPath ?: return@makeIconButton
                    showCreateFolderDialog(rootPath)
                }
            )
            addView(
                UiKit.makeIconButton(context, NasjeIcon.REFRESH, palette.textSecondary, "Actualiser", sizeDp = 42, iconDp = 18) {
                    controller.refreshFileTree()
                }
            )
            addView(
                UiKit.makeIconButton(context, NasjeIcon.CLOSE, palette.textSecondary, "Fermer l'explorateur", sizeDp = 42, iconDp = 18) {
                    onClose()
                }
            )
        }
        drawerCard.addView(header)
        drawerCard.addView(UiKit.makeDivider(context, palette))

        // Search input
        val searchWrap = LinearLayout(context).apply {
            setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
            val searchInput = UiKit.makeInput(
                context, palette, "Filtrer les fichiers...", controller.uiState.fileSearchQuery, tag = "explorer_search"
            )
            searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    controller.setFileSearchQuery(s?.toString() ?: "")
                }
            })
            addView(searchInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        drawerCard.addView(searchWrap)

        // Clipboard banner
        clipboardBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiKit.withAlpha(palette.blue, 0.16f))
            setPadding(context.dp(10), context.dp(6), context.dp(8), context.dp(6))
            visibility = View.GONE

            clipboardText = UiKit.makeText(context, "", 11.5f, palette.cyan, bold = true, maxLines = 1)
            addView(clipboardText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            pasteRootBtn = UiKit.makeText(context, "Coller", 11.5f, palette.green, bold = true).apply {
                setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
                setOnClickListener {
                    val rootPath = controller.uiState.currentProject?.rootPath ?: return@setOnClickListener
                    controller.pasteClipboardInto(rootPath)
                }
            }
            addView(pasteRootBtn)
            addView(
                UiKit.makeIconButton(context, NasjeIcon.CLOSE, palette.textSecondary, "Annuler presse-papiers", sizeDp = 36, iconDp = 15) {
                    controller.clearClipboard()
                }
            )
        }
        drawerCard.addView(clipboardBanner)

        // Tree scroll view
        val scroll = ScrollView(context)
        treeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(16))
        }
        scroll.addView(treeContainer)
        drawerCard.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val widthPx = (resources.displayMetrics.widthPixels * 0.82f).toInt().coerceAtMost(context.dp(340))
        addView(drawerCard, LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
    }

    fun refresh() {
        if (palette.isDark != controller.settings.darkTheme) {
            buildUi()
        }
        val state = controller.uiState
        val rootNode = state.fileTree

        if (expandedDirs.isEmpty() && rootNode != null) {
            expandedDirs.add(rootNode.absolutePath)
            rootNode.children.filter { it.isDirectory }.forEach { child ->
                expandedDirs.add(child.absolutePath)
                child.children.filter { it.isDirectory }.forEach { sub -> expandedDirs.add(sub.absolutePath) }
            }
        }

        val clip = state.clipboardOp
        if (clip != null) {
            clipboardBanner.visibility = View.VISIBLE
            clipboardText.text = (if (clip.isMove) "Déplacer : " else "Copier : ") + clip.sourceName
        } else {
            clipboardBanner.visibility = View.GONE
        }

        treeContainer.removeAllViews()
        if (rootNode == null) {
            treeContainer.addView(UiKit.makeText(context, "Aucun dossier ouvert", 13f, palette.textMuted))
            return
        }

        if (state.fileSearchQuery.isNotBlank()) {
            val matches = fileRepo.searchFiles(rootNode, state.fileSearchQuery)
            if (matches.isEmpty()) {
                treeContainer.addView(
                    UiKit.makeText(context, "Aucun résultat", 13f, palette.textMuted).apply {
                        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                    }
                )
            } else {
                matches.forEach { node ->
                    treeContainer.addView(buildNodeRow(node, depth = 0, forceFlat = true))
                }
            }
        } else {
            renderTreeRecursive(rootNode, depth = 0)
        }
    }

    private fun renderTreeRecursive(node: FileNode, depth: Int) {
        treeContainer.addView(buildNodeRow(node, depth, forceFlat = false))
        if (node.isDirectory && node.absolutePath in expandedDirs) {
            node.children.forEach { child ->
                renderTreeRecursive(child, depth + 1)
            }
        }
    }

    private fun buildNodeRow(node: FileNode, depth: Int, forceFlat: Boolean): View {
        val isActive = controller.uiState.activeTab?.filePath == node.absolutePath
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(44)
            setPadding(context.dp(8 + depth * 14), context.dp(4), context.dp(4), context.dp(4))
            if (isActive) {
                background = UiKit.roundedBox(context, UiKit.withAlpha(palette.blue, 0.20f), palette.blue, 1, 8)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (node.isDirectory) {
                    if (node.absolutePath in expandedDirs) expandedDirs.remove(node.absolutePath)
                    else expandedDirs.add(node.absolutePath)
                    refresh()
                } else {
                    onFileSelected(node.absolutePath)
                }
            }
        }

        if (node.isDirectory) {
            val expanded = node.absolutePath in expandedDirs
            row.addView(
                ImageView(context).apply {
                    setImageDrawable(
                        VectorIconDrawable(
                            if (expanded) NasjeIcon.CHEVRON_DOWN else NasjeIcon.CHEVRON_RIGHT,
                            palette.textMuted,
                            context.dp(14)
                        )
                    )
                },
                LinearLayout.LayoutParams(context.dp(16), context.dp(16)).apply { marginEnd = context.dp(4) }
            )
            row.addView(
                ImageView(context).apply {
                    setImageDrawable(VectorIconDrawable(NasjeIcon.FOLDER, palette.cyan, context.dp(18)))
                },
                LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(8) }
            )
        } else {
            val dot = View(context).apply {
                background = UiKit.roundedBox(context, node.language.accentColorInt, radiusDp = 4)
            }
            row.addView(dot, LinearLayout.LayoutParams(context.dp(8), context.dp(8)).apply {
                marginStart = context.dp(6)
                marginEnd = context.dp(10)
            })
        }

        val labelText = if (forceFlat) node.relativePath else node.name
        row.addView(
            UiKit.makeText(
                context,
                labelText,
                13f,
                if (isActive) palette.textPrimary else if (node.isDirectory) palette.textPrimary else palette.textSecondary,
                bold = node.isDirectory || isActive,
                maxLines = 1
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val moreBtn = UiKit.makeIconButton(
            context = context,
            icon = NasjeIcon.MORE,
            tint = palette.textMuted,
            contentDesc = "Actions sur ${node.name}",
            sizeDp = 40,
            iconDp = 16
        ) {}
        moreBtn.setOnClickListener { anchor ->
            val menu = PopupMenu(context, anchor)
            if (node.isDirectory) {
                menu.menu.add(0, 1, 1, "Nouveau fichier")
                menu.menu.add(0, 2, 2, "Nouveau dossier")
                menu.menu.add(0, 3, 3, "Importer un fichier...")
                if (controller.uiState.clipboardOp != null) {
                    menu.menu.add(0, 4, 4, "Coller ici")
                }
            }
            if (node.relativePath.isNotEmpty()) {
                menu.menu.add(0, 5, 5, "Renommer")
                menu.menu.add(0, 6, 6, "Copier")
                menu.menu.add(0, 7, 7, "Déplacer")
                menu.menu.add(0, 8, 8, "Supprimer")
            }
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showCreateFileDialog(node.absolutePath)
                    2 -> showCreateFolderDialog(node.absolutePath)
                    3 -> onRequestImportIntoDir(node.absolutePath)
                    4 -> controller.pasteClipboardInto(node.absolutePath)
                    5 -> showRenameNodeDialog(node)
                    6 -> controller.setClipboardCopy(node)
                    7 -> controller.setClipboardMove(node)
                    8 -> showDeleteConfirmDialog(node)
                }
                true
            }
            menu.show()
        }
        row.addView(moreBtn)
        return row
    }

    private fun showCreateFileDialog(parentDir: String) {
        val input = UiKit.makeInput(context, palette, "Ex: Helper.java, config.xml", mono = true)
        val wrap = dialogPad(input)
        AlertDialog.Builder(context)
            .setTitle("Nouveau fichier")
            .setView(wrap)
            .setPositiveButton("Créer") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    expandedDirs.add(parentDir)
                    controller.createNewFile(parentDir, name)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showCreateFolderDialog(parentDir: String) {
        val input = UiKit.makeInput(context, palette, "Nom du dossier", mono = true)
        val wrap = dialogPad(input)
        AlertDialog.Builder(context)
            .setTitle("Nouveau dossier")
            .setView(wrap)
            .setPositiveButton("Créer") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    expandedDirs.add(parentDir)
                    controller.createNewFolder(parentDir, name)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showRenameNodeDialog(node: FileNode) {
        val input = UiKit.makeInput(context, palette, "Nouveau nom", node.name, mono = true)
        val wrap = dialogPad(input)
        AlertDialog.Builder(context)
            .setTitle("Renommer")
            .setView(wrap)
            .setPositiveButton("Valider") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) controller.renameFileOrFolder(node.absolutePath, name)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showDeleteConfirmDialog(node: FileNode) {
        AlertDialog.Builder(context)
            .setTitle("Supprimer « ${node.name} » ?")
            .setMessage("Cette opération supprimera définitivement cet élément du disque.")
            .setPositiveButton("Supprimer") { _, _ ->
                controller.deleteFileOrFolder(node.absolutePath)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun dialogPad(child: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(4))
        addView(child)
    }
}
