package com.NASJEProduction.NASJEIDE.ui.screens

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import com.NASJEProduction.NASJEIDE.editor.SyntaxHighlighter
import com.NASJEProduction.NASJEIDE.editor.SyntaxPalette
import com.NASJEProduction.NASJEIDE.model.DiagnosticSeverity
import com.NASJEProduction.NASJEIDE.ui.FontKit
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjeIcon
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp

class EditorScreenView(
    context: Context,
    private val controller: IdeController,
    private val onNavigateHome: () -> Unit,
    private val onOpenModules: () -> Unit,
    private val onOpenErrors: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onRunBuild: () -> Unit,
    private val onRequestImportIntoDir: (String) -> Unit
) : FrameLayout(context) {

    private var palette = NasjePalette(controller.settings.darkTheme)
    private var explorerVisible = false
    private var findReplaceVisible = false
    private var findQuery = ""
    private var replaceQuery = ""
    private var lastLoadedFilePath: String? = null
    private var lastLoadedTextVersion: String? = null
    private var suppressTextWatcher = false

    private lateinit var mainCol: LinearLayout
    private lateinit var projectTitleView: TextView
    private lateinit var activeFileSubView: TextView
    private lateinit var tabsRow: LinearLayout
    private lateinit var findReplacePanel: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var replaceInput: EditText

    private lateinit var emptyStateView: LinearLayout
    private lateinit var editorScrollView: ScrollView
    private lateinit var gutterView: LineNumberGutterView
    private lateinit var codeEditText: CursorAwareEditText

    private lateinit var cursorStatusText: TextView
    private lateinit var langBadgeText: TextView
    private lateinit var diagBadgeBtn: LinearLayout
    private lateinit var diagBadgeText: TextView

    private lateinit var explorerOverlay: FileExplorerOverlay

    private class CursorAwareEditText(context: Context) : EditText(context) {
        var onCursorMoved: ((Int) -> Unit)? = null

        override fun onSelectionChanged(selStart: Int, selEnd: Int) {
            super.onSelectionChanged(selStart, selEnd)
            onCursorMoved?.invoke(selStart)
        }
    }

    private class LineNumberGutterView(
        context: Context,
        private val palette: NasjePalette
    ) : View(context) {
        private var lineCount = 1
        private var lineHeightPx = context.dp(20)
        private var topPadPx = context.dp(10)
        private var diagSeverityByLine: Map<Int, DiagnosticSeverity> = emptyMap()
        private var linkedEditText: EditText? = null

        private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = FontKit.mono(context)
            textAlign = android.graphics.Paint.Align.RIGHT
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics
            )
            color = palette.textMuted
        }
        private val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }

        fun bindEditor(editText: EditText) {
            linkedEditText = editText
        }

        fun updateData(
            lines: Int,
            fontSizeSp: Float,
            lineH: Int,
            topPad: Int,
            diags: Map<Int, DiagnosticSeverity>
        ) {
            lineCount = lines.coerceAtLeast(1)
            lineHeightPx = lineH.coerceAtLeast(context.dp(16))
            topPadPx = topPad
            diagSeverityByLine = diags
            textPaint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                (fontSizeSp - 1f).coerceAtLeast(10f),
                context.resources.displayMetrics
            )
            requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val digits = lineCount.toString().length.coerceAtLeast(2)
            val charW = textPaint.measureText("0")
            val desiredW = (context.dp(18) + digits * charW).toInt().coerceAtLeast(context.dp(44))
            val editorH = linkedEditText?.height ?: 0
            val contentH = topPadPx + lineCount * lineHeightPx + context.dp(120)
            val desiredH = maxOf(editorH, contentH, MeasureSpec.getSize(heightMeasureSpec))
            setMeasuredDimension(resolveSize(desiredW, widthMeasureSpec), resolveSize(desiredH, heightMeasureSpec))
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val rightX = width - context.dp(8).toFloat()
            val dotX = context.dp(6).toFloat()
            val dotRadius = context.dp(2.5f.toInt()).toFloat().coerceAtLeast(3f)
            val layout = linkedEditText?.layout
            val fm = textPaint.fontMetrics

            for (i in 0 until lineCount) {
                val lineNum = i + 1
                val baseline = if (layout != null && i < layout.lineCount) {
                    (topPadPx + layout.getLineBaseline(i)).toFloat()
                } else {
                    (topPadPx + i * lineHeightPx - fm.ascent).toFloat()
                }

                val sev = diagSeverityByLine[lineNum]
                if (sev != null) {
                    val c = if (sev == DiagnosticSeverity.ERROR) palette.red else palette.amber
                    dotPaint.color = c
                    textPaint.color = c
                    canvas.drawCircle(dotX, baseline + fm.ascent * 0.35f, dotRadius, dotPaint)
                } else {
                    textPaint.color = palette.textMuted
                }
                canvas.drawText(lineNum.toString(), rightX, baseline, textPaint)
            }
        }
    }

    init {
        buildSkeleton()
        refresh()
    }

    private fun buildSkeleton() {
        removeAllViews()
        palette = NasjePalette(controller.settings.darkTheme)
        setBackgroundColor(palette.bgDeep)

        mainCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // Top Bar: Menu, Explorer, Title, Green RUN, Save, More
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.topBarBg)
            setPadding(context.dp(4), context.dp(6), context.dp(6), context.dp(6))

            val menuBtn = UiKit.makeIconButton(
                context, NasjeIcon.MENU, palette.textPrimary, "Menu principal", sizeDp = 48, tag = "editor_menu_button"
            ) {}
            menuBtn.setOnClickListener { anchor ->
                val menu = PopupMenu(context, anchor)
                menu.menu.add(0, 1, 1, "Accueil (Mes Projets)")
                menu.menu.add(0, 2, 2, "Modules & Configuration")
                menu.menu.add(0, 3, 3, "Erreurs & Logs")
                menu.menu.add(0, 4, 4, "Paramètres")
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            controller.saveAllOpenFiles()
                            onNavigateHome()
                        }
                        2 -> onOpenModules()
                        3 -> onOpenErrors()
                        4 -> onOpenSettings()
                    }
                    true
                }
                menu.show()
            }
            addView(menuBtn)

            addView(
                UiKit.makeIconButton(
                    context,
                    NasjeIcon.FOLDER,
                    palette.cyan,
                    "Explorateur de fichiers",
                    sizeDp = 48,
                    tag = "editor_explorer_button"
                ) {
                    explorerVisible = !explorerVisible
                    updateExplorerVisibility()
                }
            )

            val titleCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(4), 0, context.dp(6), 0)
                projectTitleView = UiKit.makeText(context, "NASJE IDE", 14.5f, palette.textPrimary, bold = true, displayFont = true, maxLines = 1)
                activeFileSubView = UiKit.makeText(context, "", 11f, palette.textSecondary, maxLines = 1)
                addView(projectTitleView)
                addView(activeFileSubView)
            }
            addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(
                UiKit.makeActionButton(
                    context = context,
                    label = "RUN",
                    bgColor = palette.green,
                    gradientEndColor = palette.greenDark,
                    icon = NasjeIcon.PLAY,
                    radiusDp = 11,
                    heightDp = 40,
                    padHDp = 12,
                    textSizeSp = 12.5f,
                    tag = "editor_run_button"
                ) { onRunBuild() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = context.dp(4)
                }
            )

            addView(
                UiKit.makeIconButton(
                    context,
                    NasjeIcon.SAVE,
                    palette.textPrimary,
                    "Sauvegarder le fichier",
                    sizeDp = 48,
                    tag = "editor_save_button"
                ) { controller.saveActiveFile(showToast = true) }
            )

            val moreBtn = UiKit.makeIconButton(
                context, NasjeIcon.MORE, palette.textPrimary, "Actions supplémentaires", sizeDp = 48, tag = "editor_more_button"
            ) {}
            moreBtn.setOnClickListener { anchor ->
                val menu = PopupMenu(context, anchor)
                menu.menu.add(0, 1, 1, "Annuler (Undo)")
                menu.menu.add(0, 2, 2, "Rétablir (Redo)")
                menu.menu.add(0, 3, 3, "Rechercher / Remplacer")
                menu.menu.add(0, 4, 4, "Modules & Configuration")
                menu.menu.add(0, 5, 5, "Erreurs & Logs")
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> controller.undo()
                        2 -> controller.redo()
                        3 -> {
                            findReplaceVisible = !findReplaceVisible
                            findReplacePanel.visibility = if (findReplaceVisible) View.VISIBLE else View.GONE
                            if (findReplaceVisible) findInput.requestFocus()
                        }
                        4 -> onOpenModules()
                        5 -> onOpenErrors()
                    }
                    true
                }
                menu.show()
            }
            addView(moreBtn)
        }
        mainCol.addView(topBar)
        mainCol.addView(UiKit.makeDivider(context, palette))

        // Open Tabs Bar
        tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
        }
        val tabsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(palette.bgPrimary)
            addView(tabsRow)
        }
        mainCol.addView(tabsScroll)
        mainCol.addView(UiKit.makeDivider(context, palette))

        // Collapsible Find & Replace Bar
        findReplacePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.surface)
            setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
            visibility = if (findReplaceVisible) View.VISIBLE else View.GONE

            val findRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                findInput = UiKit.makeInput(context, palette, "Rechercher...", findQuery, mono = true)
                findInput.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        findQuery = s?.toString() ?: ""
                        applySyntaxToCurrentEditable()
                    }
                })
                addView(findInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(6)
                })
                addView(
                    UiKit.makeActionButton(context, "Suivant", palette.blue, heightDp = 42, padHDp = 10, textSizeSp = 12f) {
                        jumpToNextMatch()
                    }
                )
                addView(
                    UiKit.makeIconButton(context, NasjeIcon.CLOSE, palette.textSecondary, "Fermer recherche", sizeDp = 42, iconDp = 16) {
                        findReplaceVisible = false
                        findReplacePanel.visibility = View.GONE
                        findQuery = ""
                        applySyntaxToCurrentEditable()
                    }
                )
            }
            addView(findRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(6)
            })

            val repRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                replaceInput = UiKit.makeInput(context, palette, "Remplacer par...", replaceQuery, mono = true)
                replaceInput.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        replaceQuery = s?.toString() ?: ""
                    }
                })
                addView(replaceInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(6)
                })
                addView(
                    UiKit.makeActionButton(context, "1×", palette.surfaceElevated, palette.textPrimary, palette.surfaceBorder, heightDp = 42, padHDp = 12, textSizeSp = 12f) {
                        val pos = controller.replaceOne(findQuery, replaceQuery, codeEditText.selectionStart)
                        if (pos != null) {
                            codeEditText.setSelection(pos.coerceIn(0, codeEditText.text.length))
                        }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = context.dp(4)
                    }
                )
                addView(
                    UiKit.makeActionButton(context, "Tout", palette.violet, heightDp = 42, padHDp = 12, textSizeSp = 12f) {
                        controller.replaceAll(findQuery, replaceQuery)
                    }
                )
            }
            addView(repRow)
        }
        mainCol.addView(findReplacePanel)

        // Editor Workspace Area
        val workspaceFrame = FrameLayout(context).apply {
            setBackgroundColor(palette.editorBg)
        }

        emptyStateView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(24))
            visibility = View.GONE

            addView(
                UiKit.makeText(
                    context,
                    "Aucun fichier ouvert",
                    16f,
                    palette.textPrimary,
                    bold = true,
                    displayFont = true
                )
            )
            addView(
                UiKit.makeText(
                    context,
                    "Ouvrez un fichier depuis l'explorateur pour commencer à coder.",
                    13f,
                    palette.textSecondary
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, context.dp(6), 0, context.dp(16))
                }
            )
            addView(
                UiKit.makeActionButton(
                    context,
                    "Ouvrir l'explorateur",
                    palette.blue,
                    icon = NasjeIcon.FOLDER,
                    heightDp = 48
                ) {
                    explorerVisible = true
                    updateExplorerVisibility()
                }
            )
        }
        workspaceFrame.addView(emptyStateView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Synchronized Gutter + HorizontalScrollView(CodeEditText) inside vertical ScrollView
        editorScrollView = ScrollView(context).apply {
            isFillViewport = true
        }
        val editorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        gutterView = LineNumberGutterView(context, palette).apply {
            setBackgroundColor(palette.gutterBg)
        }
        editorRow.addView(gutterView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        editorRow.addView(View(context).apply {
            setBackgroundColor(palette.divider)
        }, LinearLayout.LayoutParams(context.dp(1), ViewGroup.LayoutParams.MATCH_PARENT))

        val codeHScroll = HorizontalScrollView(context).apply {
            isFillViewport = true
        }
        codeEditText = CursorAwareEditText(context).apply {
            tag = "code_editor_input"
            background = null
            gravity = Gravity.TOP or Gravity.START
            setHorizontallyScrolling(true)
            setPadding(context.dp(10), context.dp(10), context.dp(24), context.dp(120))
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextColor(palette.textPrimary)
        }
        applyEditorTypography()
        gutterView.bindEditor(codeEditText)

        codeEditText.addTextChangedListener(object : TextWatcher {
            private var insertedNewlineAt = -1

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressTextWatcher && count == 1 && before == 0 && s != null && start < s.length && s[start] == '\n') {
                    insertedNewlineAt = start
                } else {
                    insertedNewlineAt = -1
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (suppressTextWatcher || s == null) return
                suppressTextWatcher = true
                try {
                    if (insertedNewlineAt >= 0) {
                        val beforeNl = s.substring(0, insertedNewlineAt)
                        val prevLine = beforeNl.substringAfterLast('\n')
                        val indent = prevLine.takeWhile { it == ' ' || it == '\t' }
                        val extra = if (prevLine.trimEnd().endsWith("{") || prevLine.trimEnd().endsWith(":")) "    " else ""
                        val fullIndent = indent + extra
                        if (fullIndent.isNotEmpty()) {
                            s.insert(insertedNewlineAt + 1, fullIndent)
                        }
                        insertedNewlineAt = -1
                    }
                    val tab = controller.uiState.activeTab
                    if (tab != null) {
                        val paletteSyntax = SyntaxPalette.forPreset(
                            controller.settings.syntaxTheme,
                            controller.settings.darkTheme
                        )
                        SyntaxHighlighter.applyHighlighting(s, tab.language, paletteSyntax, findQuery)
                        val textStr = s.toString()
                        lastLoadedTextVersion = textStr
                        controller.updateActiveEditorContent(textStr, codeEditText.selectionStart)
                        rebuildGutter(textStr)
                    }
                } finally {
                    suppressTextWatcher = false
                }
            }
        })

        codeHScroll.addView(codeEditText, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        editorRow.addView(codeHScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        editorScrollView.addView(editorRow)
        workspaceFrame.addView(editorScrollView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        mainCol.addView(workspaceFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom Status Bar
        mainCol.addView(UiKit.makeDivider(context, palette))
        val statusBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.topBarBg)
            setPadding(context.dp(10), context.dp(5), context.dp(10), context.dp(5))

            cursorStatusText = UiKit.makeText(context, "Ln 1, Col 1", 11.5f, palette.textSecondary, mono = true)
            addView(cursorStatusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(
                UiKit.makeText(context, "UTF-8", 11f, palette.textMuted, mono = true).apply {
                    setPadding(0, 0, context.dp(10), 0)
                }
            )

            langBadgeText = UiKit.makeText(context, "Texte", 11f, palette.cyan, bold = true).apply {
                setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
                background = UiKit.roundedBox(context, UiKit.withAlpha(palette.cyan, 0.14f), radiusDp = 6)
            }
            addView(langBadgeText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })

            diagBadgeBtn = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                tag = "editor_diagnostics_badge"
                setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
                setOnClickListener { onOpenErrors() }
                diagBadgeText = UiKit.makeText(context, "OK", 11f, palette.green, bold = true)
                addView(diagBadgeText)
            }
            addView(diagBadgeBtn)
        }
        mainCol.addView(statusBar)

        addView(mainCol, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        codeEditText.onCursorMoved = { cursorPos ->
            updateCursorStatus(cursorPos)
        }

        explorerOverlay = FileExplorerOverlay(
            context = context,
            controller = controller,
            onClose = {
                explorerVisible = false
                updateExplorerVisibility()
            },
            onFileSelected = { path ->
                controller.openFile(path)
                explorerVisible = false
                updateExplorerVisibility()
            },
            onRequestImportIntoDir = onRequestImportIntoDir
        )
        addView(explorerOverlay, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        updateExplorerVisibility()
    }

    private fun updateExplorerVisibility() {
        explorerOverlay.visibility = if (explorerVisible) View.VISIBLE else View.GONE
        if (explorerVisible) {
            explorerOverlay.refresh()
        }
    }

    fun isExplorerOpen(): Boolean = explorerVisible

    fun closeExplorer() {
        explorerVisible = false
        updateExplorerVisibility()
    }

    fun refresh() {
        if (palette.isDark != controller.settings.darkTheme) {
            buildSkeleton()
        }
        val state = controller.uiState
        val project = state.currentProject
        val activeTab = state.activeTab

        projectTitleView.text = project?.name ?: "NASJE IDE"
        activeFileSubView.text = activeTab?.relativePath ?: "Aucun fichier ouvert"

        applyEditorTypography()
        renderTabs()

        if (activeTab == null) {
            emptyStateView.visibility = View.VISIBLE
            editorScrollView.visibility = View.GONE
            lastLoadedFilePath = null
            lastLoadedTextVersion = null
            langBadgeText.text = "—"
            cursorStatusText.text = "Ln 1, Col 1"
        } else {
            emptyStateView.visibility = View.GONE
            editorScrollView.visibility = View.VISIBLE
            langBadgeText.text = activeTab.language.label

            val shouldReload = activeTab.filePath != lastLoadedFilePath ||
                activeTab.content != lastLoadedTextVersion

            if (shouldReload) {
                suppressTextWatcher = true
                try {
                    codeEditText.setText(activeTab.content)
                    applySyntaxToCurrentEditable()
                    val safeCursor = activeTab.cursorIndex.coerceIn(0, codeEditText.text.length)
                    codeEditText.setSelection(safeCursor)
                    lastLoadedFilePath = activeTab.filePath
                    lastLoadedTextVersion = activeTab.content
                    codeEditText.post {
                        rebuildGutter(codeEditText.text?.toString() ?: "")
                    }
                } finally {
                    suppressTextWatcher = false
                }
            }

            rebuildGutter(codeEditText.text.toString())
            updateCursorStatus(codeEditText.selectionStart)

            val jumpLine = activeTab.pendingLineJump
            if (jumpLine != null) {
                val tabIndex = state.activeTabIndex
                codeEditText.post {
                    jumpToLine(jumpLine)
                    controller.consumePendingLineJump(tabIndex)
                }
            }
        }

        val errCount = state.errorCount
        val warnCount = state.warningCount
        when {
            errCount > 0 -> {
                diagBadgeBtn.background = UiKit.roundedBox(context, UiKit.withAlpha(palette.red, 0.16f), UiKit.withAlpha(palette.red, 0.5f), 1, 6)
                diagBadgeText.setTextColor(palette.red)
                diagBadgeText.text = "$errCount err • $warnCount av"
            }
            warnCount > 0 -> {
                diagBadgeBtn.background = UiKit.roundedBox(context, UiKit.withAlpha(palette.amber, 0.16f), UiKit.withAlpha(palette.amber, 0.5f), 1, 6)
                diagBadgeText.setTextColor(palette.amber)
                diagBadgeText.text = "$warnCount av"
            }
            else -> {
                diagBadgeBtn.background = UiKit.roundedBox(context, UiKit.withAlpha(palette.green, 0.16f), UiKit.withAlpha(palette.green, 0.5f), 1, 6)
                diagBadgeText.setTextColor(palette.green)
                diagBadgeText.text = "0 err"
            }
        }

        if (explorerVisible) {
            explorerOverlay.refresh()
        }
    }

    private fun renderTabs() {
        tabsRow.removeAllViews()
        val state = controller.uiState
        state.openTabs.forEachIndexed { idx, tab ->
            val selected = idx == state.activeTabIndex
            val tabChip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(10), context.dp(6), context.dp(6), context.dp(6))
                background = UiKit.roundedBox(
                    context = context,
                    fillColor = if (selected) palette.surfaceElevated else palette.surface,
                    strokeColor = if (selected) palette.blue else palette.surfaceBorder,
                    strokeDp = 1,
                    radiusDp = 8
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { controller.selectTab(idx) }

                addView(
                    View(context).apply {
                        background = UiKit.roundedBox(context, tab.language.accentColorInt, radiusDp = 4)
                    },
                    LinearLayout.LayoutParams(context.dp(7), context.dp(7)).apply { marginEnd = context.dp(6) }
                )

                val nameLabel = if (tab.isDirty) "${tab.fileName} •" else tab.fileName
                addView(
                    UiKit.makeText(
                        context,
                        nameLabel,
                        12f,
                        if (selected) palette.textPrimary else palette.textSecondary,
                        bold = selected,
                        maxLines = 1
                    )
                )

                val closeBtn = TextView(context).apply {
                    text = "×"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(palette.textMuted)
                    setPadding(context.dp(8), 0, context.dp(4), 0)
                    setOnClickListener { controller.closeTab(idx) }
                }
                addView(closeBtn)
            }
            tabsRow.addView(tabChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(6)
            })
        }
    }

    private fun applyEditorTypography() {
        val s = controller.settings
        val fontSize = s.editorFontSizeSp.toFloat()
        codeEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
        codeEditText.typeface = FontKit.editorTypeface(context, s.editorFontFamily)
        codeEditText.setTextColor(palette.textPrimary)
    }

    private fun applySyntaxToCurrentEditable() {
        val tab = controller.uiState.activeTab ?: return
        val editable = codeEditText.text ?: return
        val syntaxPalette = SyntaxPalette.forPreset(controller.settings.syntaxTheme, controller.settings.darkTheme)
        SyntaxHighlighter.applyHighlighting(editable, tab.language, syntaxPalette, findQuery)
    }

    private fun rebuildGutter(text: String) {
        val lineCount = text.count { it == '\n' } + 1
        val diagsByLine = controller.uiState.activeFileDiagnostics
            .groupBy { it.line }
            .mapValues { (_, list) ->
                if (list.any { it.severity == DiagnosticSeverity.ERROR }) DiagnosticSeverity.ERROR
                else DiagnosticSeverity.WARNING
            }
        val fontSize = controller.settings.editorFontSizeSp.toFloat()
        val lineH = codeEditText.lineHeight.takeIf { it > 0 } ?: context.dp(20)
        gutterView.updateData(
            lines = lineCount,
            fontSizeSp = fontSize,
            lineH = lineH,
            topPad = codeEditText.paddingTop,
            diags = diagsByLine
        )
    }

    private fun updateCursorStatus(cursorIndex: Int) {
        if (!::cursorStatusText.isInitialized) return
        val content = codeEditText.text?.toString() ?: ""
        val clamped = cursorIndex.coerceIn(0, content.length)
        val before = content.substring(0, clamped)
        val line = before.count { it == '\n' } + 1
        val col = clamped - before.lastIndexOf('\n')
        cursorStatusText.text = "Ln $line, Col $col"
    }

    private fun jumpToLine(targetLine: Int) {
        val text = codeEditText.text?.toString() ?: return
        val lines = text.lines()
        val safeLine = targetLine.coerceIn(1, lines.size.coerceAtLeast(1))
        var offset = 0
        for (i in 0 until safeLine - 1) {
            offset += lines[i].length + 1
        }
        codeEditText.requestFocus()
        codeEditText.setSelection(offset.coerceIn(0, text.length))
        val layout = codeEditText.layout
        if (layout != null && safeLine - 1 < layout.lineCount) {
            val y = layout.getLineTop(safeLine - 1)
            editorScrollView.smoothScrollTo(0, (y - context.dp(40)).coerceAtLeast(0))
        }
    }

    private fun jumpToNextMatch() {
        if (findQuery.isBlank()) return
        val text = codeEditText.text?.toString() ?: return
        val start = codeEditText.selectionEnd.coerceIn(0, text.length)
        val idx = text.indexOf(findQuery, startIndex = start, ignoreCase = true)
            .let { if (it >= 0) it else text.indexOf(findQuery, ignoreCase = true) }
        if (idx >= 0) {
            codeEditText.requestFocus()
            codeEditText.setSelection(idx, (idx + findQuery.length).coerceAtMost(text.length))
            val layout = codeEditText.layout
            if (layout != null) {
                val line = layout.getLineForOffset(idx)
                editorScrollView.smoothScrollTo(0, (layout.getLineTop(line) - context.dp(40)).coerceAtLeast(0))
            }
        } else {
            controller.postMessage("Aucune occurrence trouvée")
        }
    }
}
