package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.IdeSettings
import com.example.model.SdkComponentItem
import com.example.model.SdkComponentStatus
import com.example.model.SyntaxThemePreset
import com.example.ui.IdeUiState
import com.example.ui.theme.GlowCard
import com.example.ui.theme.LanguageBadge
import com.example.ui.theme.NasjeBackdropBrush
import com.example.ui.theme.NasjeCyan
import com.example.ui.theme.NasjeLogoMark
import com.example.ui.theme.NasjeRunGreen
import com.example.ui.theme.NasjeSurface
import com.example.ui.theme.NasjeSurfaceElevated
import com.example.ui.theme.NasjeTextSecondary
import com.example.ui.theme.NasjeWarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: IdeUiState,
    settings: IdeSettings,
    sdkComponents: List<SdkComponentItem>,
    onBack: () -> Unit,
    onUpdateSettings: ((IdeSettings) -> IdeSettings) -> Unit,
    onInstallSdkComponent: (String) -> Unit,
    onClearBuildCache: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var bridgeUrlInput by remember(settings.remoteBuildBridgeUrl) {
        mutableStateOf(settings.remoteBuildBridgeUrl)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Paramètres NASJE IDE") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NasjeBackdropBrush)
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Apparence & Éditeur
            item {
                GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurface) {
                    Text("Apparence & Éditeur", style = MaterialTheme.typography.titleMedium, color = NasjeCyan)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Thème sombre professionnel", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Text("Fond bleu-noir et contrastes lumineux", style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                        }
                        Switch(
                            checked = settings.darkTheme,
                            onCheckedChange = { checked -> onUpdateSettings { it.copy(darkTheme = checked) } },
                            modifier = Modifier.testTag("settings_dark_theme_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sauvegarde automatique", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Text("Enregistre automatiquement vos modifications", style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                        }
                        Switch(
                            checked = settings.autoSave,
                            onCheckedChange = { checked -> onUpdateSettings { it.copy(autoSave = checked) } },
                            modifier = Modifier.testTag("settings_autosave_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Taille du texte de l'éditeur : ${settings.editorFontSizeSp} sp", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Slider(
                        value = settings.editorFontSizeSp.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(editorFontSizeSp = v.toInt()) } },
                        valueRange = 11f..22f,
                        steps = 10
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Police de code", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("JetBrains Mono", "Monospace", "Sans Serif").forEach { font ->
                            FilterChip(
                                selected = settings.editorFontFamily == font,
                                onClick = { onUpdateSettings { it.copy(editorFontFamily = font) } },
                                label = { Text(font) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Couleurs syntaxiques", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SyntaxThemePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = settings.syntaxTheme == preset,
                                onClick = { onUpdateSettings { it.copy(syntaxTheme = preset) } },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Langue de l'interface", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.appLanguage == "fr",
                            onClick = { onUpdateSettings { it.copy(appLanguage = "fr") } },
                            label = { Text("Français") }
                        )
                        FilterChip(
                            selected = settings.appLanguage == "en",
                            onClick = { onUpdateSettings { it.copy(appLanguage = "en") } },
                            label = { Text("English") }
                        )
                    }
                }
            }

            // Gestion du stockage
            item {
                GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurface) {
                    Text("Gestion du stockage", style = MaterialTheme.typography.titleMedium, color = NasjeCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Projets enregistrés : ${state.projectsStorageBytes / 1024} Ko",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        "Fichiers APK générés : ${state.buildsStorageBytes / 1024} Ko",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NasjeTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onClearBuildCache,
                        modifier = Modifier.fillMaxWidth().testTag("clear_builds_button")
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = NasjeCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nettoyer le cache des APK générés")
                    }
                }
            }

            // Gestion des SDK & Pont Gradle
            item {
                GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurface) {
                    Text("Gestion des SDK & Compilateurs", style = MaterialTheme.typography.titleMedium, color = NasjeCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Le moteur natif NASJE compile directement les projets Java Android en APK sur l'appareil. Pour Kotlin (kotlinc), NDK C++ et Gradle complet, connectez un Pont Gradle distant (HTTP/HTTPS).",
                        style = MaterialTheme.typography.bodySmall,
                        color = NasjeTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bridgeUrlInput,
                        onValueChange = { bridgeUrlInput = it },
                        label = { Text("URL du Pont Gradle distant (optionnel)") },
                        placeholder = { Text("https://build.mon-serveur.fr") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onUpdateSettings { it.copy(remoteBuildBridgeUrl = bridgeUrlInput.trim()) }
                            onShowMessage("URL du Pont Gradle enregistrée")
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer")
                    }
                }
            }

            items(sdkComponents, key = { it.id }) { comp ->
                GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurfaceElevated) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(comp.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LanguageBadge(
                                label = "${comp.version} • ${comp.status.label}",
                                accentColor = when (comp.status) {
                                    SdkComponentStatus.INSTALLED_BUILT_IN, SdkComponentStatus.INSTALLED_LOCAL -> NasjeRunGreen
                                    SdkComponentStatus.AVAILABLE_DOWNLOAD -> NasjeCyan
                                    SdkComponentStatus.REMOTE_BRIDGE_ONLY -> NasjeWarningAmber
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(comp.description, style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                        }
                        if (comp.status == SdkComponentStatus.AVAILABLE_DOWNLOAD) {
                            IconButton(onClick = { onInstallSdkComponent(comp.id) }) {
                                Icon(Icons.Filled.Download, contentDescription = "Télécharger", tint = NasjeCyan)
                            }
                        } else if (comp.status == SdkComponentStatus.INSTALLED_BUILT_IN || comp.status == SdkComponentStatus.INSTALLED_LOCAL) {
                            Icon(Icons.Filled.Verified, contentDescription = "Installé", tint = NasjeRunGreen)
                        }
                    }
                }
            }

            // À propos de NASJE IDE
            item {
                GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurface) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NasjeLogoMark(size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("NASJE IDE", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("Version 1.0.0 • Build Natif Android", style = MaterialTheme.typography.bodySmall, color = NasjeCyan)
                            Text("Développez. Compilez. Réalisez.", style = MaterialTheme.typography.bodySmall, color = NasjeTextSecondary)
                        }
                    }
                }
            }
        }
    }
}
