package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DiagnosticItem
import com.example.model.DiagnosticSeverity
import com.example.ui.IdeUiState
import com.example.ui.theme.GlowCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LanguageBadge
import com.example.ui.theme.NasjeBackdropBrush
import com.example.ui.theme.NasjeBlue
import com.example.ui.theme.NasjeCyan
import com.example.ui.theme.NasjeErrorRed
import com.example.ui.theme.NasjeRunGreen
import com.example.ui.theme.NasjeSurface
import com.example.ui.theme.NasjeSurfaceElevated
import com.example.ui.theme.NasjeTextMuted
import com.example.ui.theme.NasjeTextSecondary
import com.example.ui.theme.NasjeWarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorsScreen(
    state: IdeUiState,
    onBack: () -> Unit,
    onGoToLine: (filePath: String, line: Int) -> Unit
) {
    val diagnostics = state.lastBuildResult?.diagnostics ?: state.liveDiagnostics
    val errors = remember(diagnostics) { diagnostics.filter { it.severity == DiagnosticSeverity.ERROR } }
    val warnings = remember(diagnostics) { diagnostics.filter { it.severity == DiagnosticSeverity.WARNING } }
    val buildLogs = state.lastBuildResult?.buildLogs ?: emptyList()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDiagnostic by remember(diagnostics) {
        mutableStateOf(errors.firstOrNull() ?: warnings.firstOrNull())
    }

    val tabTitles = listOf(
        "Erreurs (${errors.size})",
        "Avertissements (${warnings.size})",
        "Sortie de compilation"
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Diagnostics & Compilation") },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("errors_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = NasjeCyan
                ) {
                    tabTitles.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title, maxLines = 1) },
                            modifier = Modifier.testTag("errors_tab_$idx")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NasjeBackdropBrush)
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            when (selectedTab) {
                0, 1 -> {
                    val list = if (selectedTab == 0) errors else warnings
                    if (list.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = NasjeRunGreen,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (selectedTab == 0) "Aucune erreur détectée" else "Aucun avertissement",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(list, key = { it.id }) { diag ->
                                val isSelected = selectedDiagnostic?.id == diag.id
                                val accent = if (diag.severity == DiagnosticSeverity.ERROR) NasjeErrorRed else NasjeWarningAmber
                                GlowCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("diagnostic_item_${diag.line}"),
                                    backgroundColor = if (isSelected) NasjeSurfaceElevated else NasjeSurface,
                                    onClick = { selectedDiagnostic = diag },
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (diag.severity == DiagnosticSeverity.ERROR) Icons.Filled.Error else Icons.Filled.Warning,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            LanguageBadge(label = diag.typeLabel, accentColor = accent)
                                        }
                                        Text(
                                            text = "${diag.fileName} • Ln ${diag.line}, Col ${diag.column}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NasjeCyan
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = diag.message,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Détails de l'erreur + Aller à la ligne
                    selectedDiagnostic?.let { detail ->
                        GlowCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            backgroundColor = NasjeSurfaceElevated,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Text(
                                text = "Détails de l'erreur",
                                style = MaterialTheme.typography.titleSmall,
                                color = NasjeCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${detail.fileName} (Ligne ${detail.line}, Colonne ${detail.column})",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = detail.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = NasjeTextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onGoToLine(detail.filePath, detail.line) },
                                colors = ButtonDefaults.buttonColors(containerColor = NasjeBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("go_to_line_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Aller à la ligne ${detail.line}")
                            }
                        }
                    }
                }

                // Sortie de compilation
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.lastBuildResult?.let { res ->
                            item {
                                GlowCard(modifier = Modifier.fillMaxWidth(), backgroundColor = NasjeSurfaceElevated) {
                                    Text(
                                        text = "Moteur : ${res.engineLabel}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NasjeCyan
                                    )
                                    Text(
                                        text = "Statut : ${if (res.success) "Succès (${res.apkFileName})" else "Échec"} • Durée : ${res.durationMs} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (res.success) NasjeRunGreen else NasjeErrorRed
                                    )
                                }
                            }
                        }

                        if (buildLogs.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucune sortie de compilation enregistrée. Appuyez sur RUN dans l'éditeur pour lancer une compilation.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NasjeTextMuted
                                )
                            }
                        } else {
                            itemsIndexed(buildLogs) { idx, line ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NasjeSurface, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}. ",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = NasjeTextMuted
                                    )
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
