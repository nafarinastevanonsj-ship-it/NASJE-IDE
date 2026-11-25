package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.IdeSettings
import com.example.model.SdkComponentItem
import com.example.model.SdkComponentStatus
import com.example.model.SyntaxThemePreset
import com.example.model.ToolchainConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.nasjeDataStore by preferencesDataStore(name = "nasje_ide_prefs")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val FONT_SIZE_SP = intPreferencesKey("editor_font_size_sp")
        val FONT_FAMILY = stringPreferencesKey("editor_font_family")
        val SYNTAX_THEME = stringPreferencesKey("syntax_theme")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val REMOTE_BRIDGE_URL = stringPreferencesKey("remote_bridge_url")

        val SDK_API = intPreferencesKey("toolchain_sdk_api")
        val AGP_VERSION = stringPreferencesKey("toolchain_agp")
        val GRADLE_VERSION = stringPreferencesKey("toolchain_gradle")
        val JDK_VERSION = stringPreferencesKey("toolchain_jdk")
        val NDK_VERSION = stringPreferencesKey("toolchain_ndk")
        val CMAKE_VERSION = stringPreferencesKey("toolchain_cmake")
        val INSTALLED_SDK_IDS = stringSetPreferencesKey("installed_sdk_ids")
    }

    val settingsFlow: Flow<IdeSettings> = context.nasjeDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.SYNTAX_THEME] ?: SyntaxThemePreset.NASJE_MIDNIGHT.name
        val themePreset = SyntaxThemePreset.entries.firstOrNull { it.name == themeStr }
            ?: SyntaxThemePreset.NASJE_MIDNIGHT
        IdeSettings(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            editorFontSizeSp = prefs[Keys.FONT_SIZE_SP] ?: 14,
            editorFontFamily = prefs[Keys.FONT_FAMILY] ?: "JetBrains Mono",
            syntaxTheme = themePreset,
            autoSave = prefs[Keys.AUTO_SAVE] ?: true,
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: "fr",
            remoteBuildBridgeUrl = prefs[Keys.REMOTE_BRIDGE_URL] ?: ""
        )
    }

    val toolchainFlow: Flow<ToolchainConfig> = context.nasjeDataStore.data.map { prefs ->
        ToolchainConfig(
            androidSdkApi = prefs[Keys.SDK_API] ?: 35,
            agpVersion = prefs[Keys.AGP_VERSION] ?: "8.7.3",
            gradleVersion = prefs[Keys.GRADLE_VERSION] ?: "8.10.2",
            jdkVersion = prefs[Keys.JDK_VERSION] ?: "17",
            ndkVersion = prefs[Keys.NDK_VERSION] ?: "27.1.12297006",
            cmakeVersion = prefs[Keys.CMAKE_VERSION] ?: "3.22.1",
            remoteBuildBridgeUrl = prefs[Keys.REMOTE_BRIDGE_URL] ?: ""
        )
    }

    val installedSdkIdsFlow: Flow<Set<String>> = context.nasjeDataStore.data.map { prefs ->
        prefs[Keys.INSTALLED_SDK_IDS] ?: setOf("sdk_api_35", "engine_java_dex", "engine_axml_arsc", "engine_apksig")
    }

    suspend fun updateSettings(transform: (IdeSettings) -> IdeSettings) {
        context.nasjeDataStore.edit { prefs ->
            val current = IdeSettings(
                darkTheme = prefs[Keys.DARK_THEME] ?: true,
                editorFontSizeSp = prefs[Keys.FONT_SIZE_SP] ?: 14,
                editorFontFamily = prefs[Keys.FONT_FAMILY] ?: "JetBrains Mono",
                syntaxTheme = SyntaxThemePreset.entries.firstOrNull {
                    it.name == prefs[Keys.SYNTAX_THEME]
                } ?: SyntaxThemePreset.NASJE_MIDNIGHT,
                autoSave = prefs[Keys.AUTO_SAVE] ?: true,
                appLanguage = prefs[Keys.APP_LANGUAGE] ?: "fr",
                remoteBuildBridgeUrl = prefs[Keys.REMOTE_BRIDGE_URL] ?: ""
            )
            val updated = transform(current)
            prefs[Keys.DARK_THEME] = updated.darkTheme
            prefs[Keys.FONT_SIZE_SP] = updated.editorFontSizeSp
            prefs[Keys.FONT_FAMILY] = updated.editorFontFamily
            prefs[Keys.SYNTAX_THEME] = updated.syntaxTheme.name
            prefs[Keys.AUTO_SAVE] = updated.autoSave
            prefs[Keys.APP_LANGUAGE] = updated.appLanguage
            prefs[Keys.REMOTE_BRIDGE_URL] = updated.remoteBuildBridgeUrl
        }
    }

    suspend fun updateToolchain(transform: (ToolchainConfig) -> ToolchainConfig) {
        context.nasjeDataStore.edit { prefs ->
            val current = ToolchainConfig(
                androidSdkApi = prefs[Keys.SDK_API] ?: 35,
                agpVersion = prefs[Keys.AGP_VERSION] ?: "8.7.3",
                gradleVersion = prefs[Keys.GRADLE_VERSION] ?: "8.10.2",
                jdkVersion = prefs[Keys.JDK_VERSION] ?: "17",
                ndkVersion = prefs[Keys.NDK_VERSION] ?: "27.1.12297006",
                cmakeVersion = prefs[Keys.CMAKE_VERSION] ?: "3.22.1",
                remoteBuildBridgeUrl = prefs[Keys.REMOTE_BRIDGE_URL] ?: ""
            )
            val updated = transform(current)
            prefs[Keys.SDK_API] = updated.androidSdkApi
            prefs[Keys.AGP_VERSION] = updated.agpVersion
            prefs[Keys.GRADLE_VERSION] = updated.gradleVersion
            prefs[Keys.JDK_VERSION] = updated.jdkVersion
            prefs[Keys.NDK_VERSION] = updated.ndkVersion
            prefs[Keys.CMAKE_VERSION] = updated.cmakeVersion
            prefs[Keys.REMOTE_BRIDGE_URL] = updated.remoteBuildBridgeUrl
        }
    }

    suspend fun installLocalSdkMetadata(componentId: String): Boolean = withContext(Dispatchers.IO) {
        val sdkDir = File(context.filesDir, "sdks/$componentId")
        if (!sdkDir.exists()) sdkDir.mkdirs()
        val manifestFile = File(sdkDir, "package.xml")
        manifestFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <sdk-package id="$componentId" installed-at="${System.currentTimeMillis()}" />
            """.trimIndent()
        )
        context.nasjeDataStore.edit { prefs ->
            val current = prefs[Keys.INSTALLED_SDK_IDS] ?: setOf("sdk_api_35", "engine_java_dex", "engine_axml_arsc", "engine_apksig")
            prefs[Keys.INSTALLED_SDK_IDS] = current + componentId
        }
        true
    }

    fun getAvailableSdkComponents(installedIds: Set<String>, bridgeUrl: String): List<SdkComponentItem> {
        val bridgeActive = bridgeUrl.isNotBlank()
        return listOf(
            SdkComponentItem(
                id = "engine_java_dex",
                name = "Moteur Java & Dalvik DEX",
                category = "Compilateur embarqué",
                version = "DEX 035 / Java 11",
                sizeLabel = "Intégré",
                status = SdkComponentStatus.INSTALLED_BUILT_IN,
                description = "Compile les sources Java Android (.java) en bytecode Dalvik classes.dex directement sur l'appareil."
            ),
            SdkComponentItem(
                id = "engine_axml_arsc",
                name = "Compilateur AXML & Resources.arsc",
                category = "Build Tools embarqué",
                version = "AOSP Binary v2",
                sizeLabel = "Intégré",
                status = SdkComponentStatus.INSTALLED_BUILT_IN,
                description = "Encode AndroidManifest.xml, les layouts XML et les valeurs (strings/colors) au format binaire Android."
            ),
            SdkComponentItem(
                id = "engine_apksig",
                name = "Signataire APK v1 + v2 (RSA-2048)",
                category = "Sécurité & Packaging",
                version = "JAR + APK Sig v2",
                sizeLabel = "Intégré",
                status = SdkComponentStatus.INSTALLED_BUILT_IN,
                description = "Aligne et signe cryptographiquement les paquets APK pour installation directe sur Android."
            ),
            SdkComponentItem(
                id = "sdk_api_35",
                name = "Android SDK Platform API 35",
                category = "Android SDK",
                version = "API 35 (Android 15)",
                sizeLabel = "Profil local",
                status = if ("sdk_api_35" in installedIds) SdkComponentStatus.INSTALLED_LOCAL else SdkComponentStatus.AVAILABLE_DOWNLOAD,
                description = "Définitions d'API Android 15 (android.app, android.widget, android.os, android.view) pour l'analyseur."
            ),
            SdkComponentItem(
                id = "sdk_api_36",
                name = "Android SDK Platform API 36",
                category = "Android SDK",
                version = "API 36 (Android 16)",
                sizeLabel = "Profil local",
                status = if ("sdk_api_36" in installedIds) SdkComponentStatus.INSTALLED_LOCAL else SdkComponentStatus.AVAILABLE_DOWNLOAD,
                description = "Définitions d'API Android 16 et validation du Manifeste cible."
            ),
            SdkComponentItem(
                id = "toolchain_kotlinc",
                name = "Compilateur Kotlin (kotlinc) & Gradle",
                category = "Toolchain Externe",
                version = "Kotlin 2.1 / Gradle 8.10",
                sizeLabel = if (bridgeActive) "Pont actif" else "Pont requis",
                status = if (bridgeActive) SdkComponentStatus.INSTALLED_LOCAL else SdkComponentStatus.REMOTE_BRIDGE_ONLY,
                description = "La compilation Kotlin complète et Gradle nécessitent un Pont Gradle distant (HTTP/HTTPS) configuré."
            ),
            SdkComponentItem(
                id = "toolchain_ndk_cmake",
                name = "Android NDK & CMake (C/C++)",
                category = "Toolchain Native",
                version = "NDK r27 / CMake 3.22",
                sizeLabel = if (bridgeActive) "Pont actif" else "Pont requis",
                status = if (bridgeActive) SdkComponentStatus.INSTALLED_LOCAL else SdkComponentStatus.REMOTE_BRIDGE_ONLY,
                description = "L'analyse C/C++ et CMakeLists.txt est locale ; la compilation binaire .so NDK utilise le Pont Gradle."
            )
        )
    }

    fun calculateStorageUsageBytes(): Pair<Long, Long> {
        val projectsDir = File(context.filesDir, "projects")
        val buildsDir = File(context.filesDir, "builds")
        return dirSize(projectsDir) to dirSize(buildsDir)
    }

    suspend fun clearBuildCache(): Long = withContext(Dispatchers.IO) {
        val buildsDir = File(context.filesDir, "builds")
        val freed = dirSize(buildsDir)
        if (buildsDir.exists()) {
            buildsDir.deleteRecursively()
        }
        freed
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }
}
