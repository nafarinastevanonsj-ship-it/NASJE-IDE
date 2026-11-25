package com.NASJEProduction.NASJEIDE.ui.screens

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.NASJEProduction.NASJEIDE.ui.IdeController
import com.NASJEProduction.NASJEIDE.ui.NasjeIcon
import com.NASJEProduction.NASJEIDE.ui.NasjePalette
import com.NASJEProduction.NASJEIDE.ui.UiKit
import com.NASJEProduction.NASJEIDE.ui.UiKit.dp
import com.NASJEProduction.NASJEIDE.ui.VectorIconDrawable

class BuildSuccessScreenView(
    context: Context,
    private val controller: IdeController,
    private val onInstallApk: (String) -> Unit,
    private val onShareApk: (String) -> Unit,
    private val onDownloadApk: (String, String) -> Unit,
    private val onReturnHome: () -> Unit,
    private val onContinueEditor: () -> Unit
) : LinearLayout(context) {

    private var palette = NasjePalette(controller.settings.darkTheme)
    private lateinit var contentCol: LinearLayout

    init {
        orientation = VERTICAL
        buildSkeleton()
        refresh()
    }

    private fun buildSkeleton() {
        removeAllViews()
        palette = NasjePalette(controller.settings.darkTheme)
        setBackgroundColor(palette.bgDeep)

        val scroll = ScrollView(context).apply { isFillViewport = true }
        contentCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(20), context.dp(28), context.dp(20), context.dp(24))
        }
        scroll.addView(contentCol)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        if (palette.isDark != controller.settings.darkTheme) {
            buildSkeleton()
        }
        contentCol.removeAllViews()
        val result = controller.uiState.lastBuildResult

        // Check badge with subtle pulse animation
        val badgeFrame = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            background = UiKit.roundedBox(context, UiKit.withAlpha(palette.green, 0.16f), palette.green, 2, 44)
        }
        val checkImg = ImageView(context).apply {
            setImageDrawable(VectorIconDrawable(NasjeIcon.CHECK_CIRCLE, palette.green, context.dp(48)))
            contentDescription = "Compilation réussie"
        }
        badgeFrame.addView(checkImg, LayoutParams(context.dp(52), context.dp(52)))
        badgeFrame.scaleX = 0.85f
        badgeFrame.scaleY = 0.85f
        badgeFrame.animate().scaleX(1f).scaleY(1f).setDuration(350L).start()

        contentCol.addView(badgeFrame, LayoutParams(context.dp(88), context.dp(88)).apply {
            bottomMargin = context.dp(16)
        })

        contentCol.addView(
            UiKit.makeText(
                context,
                "Compilation réussie !",
                23f,
                palette.textPrimary,
                bold = true,
                displayFont = true
            ).apply { gravity = Gravity.CENTER }
        )

        if (result == null || result.apkFilePath == null) {
            contentCol.addView(
                UiKit.makeText(
                    context,
                    "Aucun fichier APK actif.",
                    14f,
                    palette.textSecondary
                ).apply { setPadding(0, context.dp(8), 0, context.dp(20)) }
            )
            contentCol.addView(
                UiKit.makeActionButton(context, "Retour à l'éditeur", palette.blue, heightDp = 48) { onContinueEditor() }
            )
            return
        }

        val apkPath = result.apkFilePath
        val apkName = result.apkFileName ?: "app-debug.apk"

        contentCol.addView(
            UiKit.makeText(
                context,
                "${result.projectName} (${result.packageName})",
                13.5f,
                palette.cyan
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, context.dp(4), 0, context.dp(18))
            }
        )

        // Metadata Card
        val infoCard = UiKit.makeCard(context, palette, borderColor = UiKit.withAlpha(palette.green, 0.45f), radiusDp = 16)
        infoCard.addView(infoRow("Fichier APK", apkName))
        infoCard.addView(infoRow("Taille réelle", controller.formatBytes(result.apkSizeBytes)))
        infoCard.addView(infoRow("Durée", "${result.durationMs} ms"))
        infoCard.addView(infoRow("Moteur", result.engineLabel))
        infoCard.addView(infoRow("Version", result.versionName))
        infoCard.addView(infoRow("Emplacement", apkPath))
        contentCol.addView(infoCard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(18)
        })

        // Primary Install Button (Green)
        contentCol.addView(
            UiKit.makeActionButton(
                context = context,
                label = "Installer maintenant",
                bgColor = palette.green,
                gradientEndColor = palette.greenDark,
                icon = NasjeIcon.INSTALL,
                radiusDp = 14,
                heightDp = 52,
                textSizeSp = 15f,
                tag = "install_apk_button"
            ) { onInstallApk(apkPath) },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(10)
            }
        )

        // Share & Download row
        val shareDownloadRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        shareDownloadRow.addView(
            UiKit.makeActionButton(
                context = context,
                label = "Partager l'APK",
                bgColor = palette.surface,
                textColor = palette.textPrimary,
                strokeColor = palette.blue,
                icon = NasjeIcon.SHARE,
                radiusDp = 12,
                heightDp = 48,
                tag = "share_apk_button"
            ) { onShareApk(apkPath) },
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) }
        )
        shareDownloadRow.addView(
            UiKit.makeActionButton(
                context = context,
                label = "Télécharger",
                bgColor = palette.surface,
                textColor = palette.cyan,
                strokeColor = palette.cyan,
                icon = NasjeIcon.DOWNLOAD,
                radiusDp = 12,
                heightDp = 48,
                tag = "download_apk_button"
            ) { onDownloadApk(apkPath, apkName) },
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(6) }
        )
        contentCol.addView(shareDownloadRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(14)
        })

        // Navigation buttons
        contentCol.addView(
            UiKit.makeActionButton(
                context = context,
                label = "Continuer dans l'éditeur",
                bgColor = palette.violet,
                gradientEndColor = palette.blue,
                icon = NasjeIcon.EDIT,
                radiusDp = 12,
                heightDp = 48,
                tag = "continue_editor_button"
            ) { onContinueEditor() },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(8)
            }
        )
        contentCol.addView(
            UiKit.makeActionButton(
                context = context,
                label = "Retour à l'accueil",
                bgColor = palette.surface,
                textColor = palette.textSecondary,
                strokeColor = palette.surfaceBorder,
                radiusDp = 12,
                heightDp = 46,
                tag = "return_home_button"
            ) { onReturnHome() },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun infoRow(label: String, value: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, context.dp(4), 0, context.dp(4))
            addView(UiKit.makeText(context, label, 11.5f, palette.textMuted, bold = true))
            addView(UiKit.makeText(context, value, 13f, palette.textPrimary, mono = true))
        }
    }
}
