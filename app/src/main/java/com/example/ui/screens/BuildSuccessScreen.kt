package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.BuildResult
import com.example.ui.theme.GlowCard
import com.example.ui.theme.GradientPrimaryButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NasjeBackdropBrush
import com.example.ui.theme.NasjeBorderSubtle
import com.example.ui.theme.NasjeCyan
import com.example.ui.theme.NasjeRunGreen
import com.example.ui.theme.NasjeRunGreenDark
import com.example.ui.theme.NasjeSurface
import com.example.ui.theme.NasjeSurfaceElevated
import com.example.ui.theme.NasjeTextMuted
import com.example.ui.theme.NasjeTextSecondary
import java.io.File
import java.util.Locale

@Composable
fun BuildSuccessScreen(
    buildResult: BuildResult?,
    onBackToEditor: () -> Unit,
    onBackHome: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val apkFile = remember(buildResult?.apkFilePath) {
        buildResult?.apkFilePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "success_check_scale"
    )

    // SAF CreateDocument launcher to download/save the generated APK to Downloads or user-picked folder
    val exportApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { destUri ->
        if (destUri != null && apkFile != null) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    apkFile.inputStream().use { input -> input.copyTo(out) }
                }
                onShowMessage("APK enregistré : ${apkFile.name}")
            }.onFailure {
                onShowMessage("Erreur lors de l'enregistrement : ${it.message}")
            }
        }
    }

    fun launchApkInstaller(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            onShowMessage("Autorisez l'installation d'applications depuis NASJE IDE puis réessayez.")
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(installIntent)
        }.onFailure {
            onShowMessage("Impossible d'ouvrir l'installateur Android : ${it.message}")
        }
    }

    fun shareApk(file: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partager l'APK"))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NasjeBackdropBrush)
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (buildResult == null || !buildResult.success || apkFile == null) {
                Text(
                    text = "Aucun APK généré.",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackHome) { Text("Retour à l'accueil") }
                return@Column
            }

            // Large animated green checkmark
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(NasjeRunGreen.copy(alpha = 0.16f))
                    .border(2.dp, NasjeRunGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Compilation réussie",
                    tint = NasjeRunGreen,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Compilation réussie !",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Votre application Android « ${buildResult.projectName} » a été compilée, vérifiée et signée avec succès.",
                style = MaterialTheme.typography.bodyMedium,
                color = NasjeTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // APK Details Card
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NasjeSurfaceElevated,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Android, contentDescription = null, tint = NasjeRunGreen, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildResult.apkFileName ?: apkFile.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "${buildResult.packageName} • v${buildResult.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NasjeCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow(label = "Taille réelle", value = formatBytes(apkFile.length()))
                InfoRow(label = "Durée", value = "${buildResult.durationMs} ms")
                InfoRow(label = "Moteur", value = buildResult.engineLabel)
                InfoRow(label = "Emplacement", value = apkFile.absolutePath, mono = true)

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { shareApk(apkFile) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = NasjeCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Partager l'APK", color = NasjeCyan)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 1. Télécharger l'APK
            GradientPrimaryButton(
                text = "Télécharger l'APK",
                icon = Icons.Filled.Download,
                testTag = "download_apk_button",
                onClick = {
                    exportApkLauncher.launch(apkFile.name)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Installer maintenant
            GradientPrimaryButton(
                text = "Installer maintenant",
                icon = Icons.Filled.InstallMobile,
                gradient = Brush.linearGradient(listOf(NasjeRunGreen, NasjeRunGreenDark)),
                testTag = "install_apk_button",
                onClick = {
                    launchApkInstaller(apkFile)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Retour à l'accueil
            OutlinedButton(
                onClick = onBackHome,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NasjeBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("build_success_home_button")
            ) {
                Icon(Icons.Filled.Home, contentDescription = null, tint = NasjeTextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retour à l'accueil", color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBackToEditor) {
                Text("Continuer dans l'éditeur", color = NasjeCyan)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = NasjeTextMuted)
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
            } else {
                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            },
            color = Color.White
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.FRANCE, "%.2f Mo (%d octets)", bytes / (1024.0 * 1024.0), bytes)
    bytes >= 1024 -> String.format(Locale.FRANCE, "%.1f Ko (%d octets)", bytes / 1024.0, bytes)
    else -> "$bytes octets"
}
