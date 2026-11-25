package com.NASJEProduction.NASJEIDE.ui.screens

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.NASJEProduction.NASJEIDE.model.IdeLanguage
import com.NASJEProduction.NASJEIDE.model.ProjectType
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjeIcon
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp

class CreateProjectScreenView(
    context: Context,
    private val controller: IdeController,
    private val onBack: () -> Unit,
    private val onProjectCreated: () -> Unit
) : LinearLayout(context) {

    private var palette = NasjePalette(controller.settings.darkTheme)
    private var selectedType = ProjectType.ANDROID_JAVA
    private var selectedLanguage = IdeLanguage.JAVA
    private var selectedMinSdk = 24
    private var selectedTargetSdk = 35
    private var packageManuallyEdited = false

    private lateinit var nameInput: EditText
    private lateinit var packageInput: EditText
    private lateinit var moduleInput: EditText
    private lateinit var versionInput: EditText
    private lateinit var templatesContainer: LinearLayout
    private lateinit var langChipsContainer: LinearLayout
    private lateinit var minSdkChipsContainer: LinearLayout
    private lateinit var targetSdkChipsContainer: LinearLayout

    init {
        orientation = VERTICAL
        buildUi()
    }

    private fun buildUi() {
        removeAllViews()
        palette = NasjePalette(controller.settings.darkTheme)
        setBackgroundColor(palette.bgDeep)

        addView(
            UiKit.makeTopBar(
                context = context,
                palette = palette,
                title = "Nouveau Projet",
                subtitle = "Générateur de structure Android réelle",
                onBack = onBack
            )
        )
        addView(UiKit.makeDivider(context, palette))

        val scroll = ScrollView(context).apply { isFillViewport = true }
        val form = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(24))
        }

        // Section 1: Project Type
        form.addView(sectionTitle("1. Modèle de projet"))
        templatesContainer = LinearLayout(context).apply { orientation = VERTICAL }
        renderTemplates()
        form.addView(templatesContainer)

        // Section 2: Identity
        form.addView(sectionTitle("2. Identité de l'application"))
        form.addView(fieldLabel("Nom du projet"))
        nameInput = UiKit.makeInput(context, palette, "Ex: Mon Application", "Mon Application", tag = "create_project_name")
        form.addView(nameInput, fieldParams())

        form.addView(fieldLabel("Nom du package (Application ID)"))
        packageInput = UiKit.makeInput(context, palette, "com.nasje.monapplication", "com.nasje.monapplication", mono = true, tag = "create_project_package")
        form.addView(packageInput, fieldParams())

        var programmaticPkgUpdate = false
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!packageManuallyEdited) {
                    val slug = (s?.toString() ?: "")
                        .lowercase()
                        .replace(Regex("[^a-z0-9]"), "")
                        .ifEmpty { "myapp" }
                    programmaticPkgUpdate = true
                    packageInput.setText("com.nasje.$slug")
                    programmaticPkgUpdate = false
                }
            }
        })
        packageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!programmaticPkgUpdate) packageManuallyEdited = true
            }
        })

        // Module + Version row
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        val modCol = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(fieldLabel("Nom du module"))
            moduleInput = UiKit.makeInput(context, palette, "app", "app", mono = true, tag = "create_project_module")
            addView(moduleInput)
        }
        val verCol = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(fieldLabel("Version"))
            versionInput = UiKit.makeInput(context, palette, "1.0", "1.0", mono = true, tag = "create_project_version")
            addView(versionInput)
        }
        row.addView(modCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) })
        row.addView(verCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(8) })
        form.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(12)
        })

        // Section 3: Primary Language
        form.addView(sectionTitle("3. Langage principal"))
        langChipsContainer = LinearLayout(context).apply { orientation = HORIZONTAL }
        val langScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(langChipsContainer)
        }
        renderLanguageChips()
        form.addView(langScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(14)
        })

        // Section 4: SDKs
        form.addView(sectionTitle("4. Configuration SDK Android"))
        form.addView(fieldLabel("Minimum SDK"))
        minSdkChipsContainer = LinearLayout(context).apply { orientation = HORIZONTAL }
        form.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(minSdkChipsContainer)
        }, fieldParams())

        form.addView(fieldLabel("Target SDK"))
        targetSdkChipsContainer = LinearLayout(context).apply { orientation = HORIZONTAL }
        form.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(targetSdkChipsContainer)
        }, fieldParams())
        renderSdkChips()

        // Submit Button
        var isSubmitting = false
        val submitBtn = UiKit.makeActionButton(
            context = context,
            label = "Créer et ouvrir le projet",
            bgColor = palette.violet,
            gradientEndColor = palette.blue,
            strokeColor = UiKit.withAlpha(palette.cyan, 0.55f),
            icon = NasjeIcon.CHECK,
            radiusDp = 14,
            heightDp = 52,
            textSizeSp = 15f,
            tag = "create_project_submit"
        ) {
            if (isSubmitting) return@makeActionButton
            isSubmitting = true
            val name = nameInput.text.toString().trim().ifEmpty { "Mon Application" }
            val pkg = packageInput.text.toString().trim().ifEmpty { "com.nasje.myapp" }
            val mod = moduleInput.text.toString().trim().ifEmpty { "app" }
            val ver = versionInput.text.toString().trim().ifEmpty { "1.0" }
            controller.createProject(
                name = name,
                packageName = pkg,
                projectType = selectedType,
                primaryLanguage = selectedLanguage,
                moduleName = mod,
                versionName = ver,
                minSdk = selectedMinSdk,
                targetSdk = selectedTargetSdk,
                onError = { isSubmitting = false }
            ) {
                isSubmitting = false
                onProjectCreated()
            }
        }
        form.addView(submitBtn, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(10)
        })

        scroll.addView(form)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderTemplates() {
        templatesContainer.removeAllViews()
        ProjectType.entries.forEach { type ->
            val selected = type == selectedType
            val card = UiKit.makeCard(
                context = context,
                palette = palette,
                elevated = selected,
                borderColor = if (selected) palette.blue else palette.surfaceBorder,
                radiusDp = 12,
                padH = 12,
                padV = 10
            )
            card.isClickable = true
            card.isFocusable = true
            card.tag = "template_${type.name}"
            card.setOnClickListener {
                selectedType = type
                selectedLanguage = type.defaultLanguage
                renderTemplates()
                renderLanguageChips()
            }
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(UiKit.makeLanguageBadge(context, type.defaultLanguage, compact = true))
            val textCol = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(context.dp(12), 0, context.dp(8), 0)
                addView(UiKit.makeText(context, type.displayName, 14f, palette.textPrimary, bold = true))
                addView(UiKit.makeText(context, type.subtitle, 11.5f, palette.textSecondary))
            }
            row.addView(textCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(row)
            templatesContainer.addView(card, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(8)
            })
        }
    }

    private fun renderLanguageChips() {
        langChipsContainer.removeAllViews()
        val langs = listOf(
            IdeLanguage.JAVA,
            IdeLanguage.KOTLIN,
            IdeLanguage.CPP,
            IdeLanguage.C,
            IdeLanguage.HTML,
            IdeLanguage.JAVASCRIPT
        )
        langs.forEach { lang ->
            val chip = UiKit.makeChip(
                context = context,
                palette = palette,
                label = lang.label,
                selected = lang == selectedLanguage,
                accentColor = lang.accentColorInt
            ) {
                selectedLanguage = lang
                renderLanguageChips()
            }
            langChipsContainer.addView(chip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun renderSdkChips() {
        minSdkChipsContainer.removeAllViews()
        listOf(21, 24, 26, 28, 30, 33).forEach { sdk ->
            val chip = UiKit.makeChip(
                context = context,
                palette = palette,
                label = "API $sdk",
                selected = sdk == selectedMinSdk,
                accentColor = palette.cyan
            ) {
                selectedMinSdk = sdk
                if (selectedTargetSdk < selectedMinSdk) selectedTargetSdk = selectedMinSdk
                renderSdkChips()
            }
            minSdkChipsContainer.addView(chip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }

        targetSdkChipsContainer.removeAllViews()
        listOf(33, 34, 35, 36).forEach { sdk ->
            val chip = UiKit.makeChip(
                context = context,
                palette = palette,
                label = "API $sdk",
                selected = sdk == selectedTargetSdk,
                accentColor = palette.violet
            ) {
                selectedTargetSdk = sdk
                if (selectedMinSdk > selectedTargetSdk) selectedMinSdk = selectedTargetSdk
                renderSdkChips()
            }
            targetSdkChipsContainer.addView(chip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun sectionTitle(title: String) = UiKit.makeText(
        context, title, 15f, palette.cyan, bold = true, displayFont = true
    ).apply { setPadding(0, context.dp(8), 0, context.dp(8)) }

    private fun fieldLabel(label: String) = UiKit.makeText(
        context, label, 12f, palette.textSecondary, bold = true
    ).apply { setPadding(0, 0, 0, context.dp(5)) }

    private fun fieldParams() = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = context.dp(12)
    }
}
