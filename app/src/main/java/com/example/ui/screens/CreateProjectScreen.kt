package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.IdeLanguage
import com.example.model.ProjectType
import com.example.ui.theme.GlowCard
import com.example.ui.theme.GradientPrimaryButton
import com.example.ui.theme.NasjeBackdropBrush
import com.example.ui.theme.NasjeBlue
import com.example.ui.theme.NasjeBorderSubtle
import com.example.ui.theme.NasjeCyan
import com.example.ui.theme.NasjeGlowBorderGradient
import com.example.ui.theme.NasjeRunGreen
import com.example.ui.theme.NasjeSurface
import com.example.ui.theme.NasjeSurfaceElevated
import com.example.ui.theme.NasjeTextSecondary
import com.example.ui.theme.NasjeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onBack: () -> Unit,
    onCreateProject: (
        name: String,
        packageName: String,
        projectType: ProjectType,
        primaryLanguage: IdeLanguage,
        moduleName: String,
        versionName: String,
        minSdk: Int,
        targetSdk: Int
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(ProjectType.ANDROID_JAVA) }
    var projectName by remember { mutableStateOf("MonApplication") }
    var packageName by remember { mutableStateOf("com.nasje.monapplication") }
    var packageEditedManually by remember { mutableStateOf(false) }
    var primaryLanguage by remember { mutableStateOf(IdeLanguage.JAVA) }
    var moduleName by remember { mutableStateOf("app") }
    var versionName by remember { mutableStateOf("1.0") }
    var minSdk by remember { mutableIntStateOf(24) }
    var targetSdk by remember { mutableIntStateOf(35) }

    val availableLanguages = listOf(
        IdeLanguage.JAVA,
        IdeLanguage.KOTLIN,
        IdeLanguage.CPP,
        IdeLanguage.C,
        IdeLanguage.HTML
    )
    val minSdkOptions = listOf(21, 24, 26, 28, 30)
    val targetSdkOptions = listOf(33, 34, 35, 36)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Nouveau Projet Android") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, NasjeBorderSubtle)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    GradientPrimaryButton(
                        text = "Créer le projet",
                        icon = Icons.Filled.CreateNewFolder,
                        enabled = projectName.isNotBlank() && packageName.contains('.') && minSdk <= targetSdk,
                        testTag = "create_project_submit",
                        onClick = {
                            onCreateProject(
                                projectName,
                                packageName,
                                selectedType,
                                primaryLanguage,
                                moduleName,
                                versionName,
                                minSdk,
                                targetSdk
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NasjeBackdropBrush)
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "1. Modèle de projet",
                    style = MaterialTheme.typography.titleMedium,
                    color = NasjeCyan
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProjectType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        val icon = when (type) {
                            ProjectType.ANDROID_JAVA -> Icons.Filled.Android
                            ProjectType.ANDROID_KOTLIN -> Icons.Filled.Code
                            ProjectType.ANDROID_CPP -> Icons.Filled.Memory
                            ProjectType.ANDROID_EMPTY -> Icons.Filled.Layers
                            ProjectType.ANDROID_CUSTOM -> Icons.Filled.Tune
                        }
                        GlowCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("template_${type.name}"),
                            glowColor = if (isSelected) {
                                Brush.linearGradient(listOf(NasjeCyan, NasjeViolet))
                            } else {
                                Brush.linearGradient(listOf(NasjeBorderSubtle, NasjeBorderSubtle))
                            },
                            backgroundColor = if (isSelected) NasjeSurfaceElevated else NasjeSurface,
                            onClick = {
                                selectedType = type
                                primaryLanguage = type.defaultLanguage
                            },
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) NasjeCyan else NasjeTextSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = type.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = type.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NasjeTextSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Sélectionné",
                                        tint = NasjeRunGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "2. Paramètres du projet",
                    style = MaterialTheme.typography.titleMedium,
                    color = NasjeCyan
                )
            }

            item {
                GlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = NasjeGlowBorderGradient,
                    backgroundColor = NasjeSurface
                ) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = {
                            projectName = it
                            if (!packageEditedManually) {
                                val slug = it.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "myapp" }
                                packageName = "com.nasje.$slug"
                            }
                        },
                        label = { Text("Nom du projet") },
                        singleLine = true,
                        colors = ideFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_project_name")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = packageName,
                        onValueChange = {
                            packageEditedManually = true
                            packageName = it
                        },
                        label = { Text("Nom du package (Application ID)") },
                        singleLine = true,
                        colors = ideFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_package_name")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = moduleName,
                            onValueChange = { moduleName = it },
                            label = { Text("Nom du module") },
                            singleLine = true,
                            colors = ideFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_module_name")
                        )
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("Version") },
                            singleLine = true,
                            colors = ideFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_version_name")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Langage principal",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableLanguages.forEach { lang ->
                            FilterChip(
                                selected = primaryLanguage == lang,
                                onClick = { primaryLanguage = lang },
                                label = { Text(lang.label) },
                                enabled = selectedType == ProjectType.ANDROID_CUSTOM || lang == selectedType.defaultLanguage,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NasjeBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = NasjeCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Minimum SDK : API $minSdk",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        minSdkOptions.forEach { api ->
                            FilterChip(
                                selected = minSdk == api,
                                onClick = { minSdk = api },
                                label = { Text("$api") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Target SDK : API $targetSdk",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        targetSdkOptions.forEach { api ->
                            FilterChip(
                                selected = targetSdk == api,
                                onClick = { targetSdk = api },
                                label = { Text("$api") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ideFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NasjeBlue,
    unfocusedBorderColor = NasjeBorderSubtle,
    focusedLabelColor = NasjeCyan,
    focusedContainerColor = NasjeSurfaceElevated.copy(alpha = 0.4f),
    unfocusedContainerColor = NasjeSurfaceElevated.copy(alpha = 0.25f)
)
