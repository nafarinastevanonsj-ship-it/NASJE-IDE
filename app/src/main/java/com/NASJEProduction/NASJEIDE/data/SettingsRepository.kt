package com.NASJEProduction.NASJEIDE.data

import android.content.Context
import android.content.SharedPreferences
import com.NASJEProduction.NASJEIDE.model.IdeSettings
import com.NASJEProduction.NASJEIDE.model.SdkComponentItem
import com.NASJEProduction.NASJEIDE.model.SdkComponentStatus
import com.NASJEProduction.NASJEIDE.model.SyntaxThemePreset
import com.NASJEProduction.NASJEIDE.model.ToolchainConfig
import java.io.File

/**
 * Pure Android SDK SharedPreferences-backed SettingsRepository (zero androidx.datastore / kotlinx.coroutines).
 */
class SettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nasje_ide_prefs", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_FONT_SIZE_SP = "editor_font_size_sp"
        const val KEY_FONT_FAMILY = "editor_font_family"
        const val KEY_SYNTAX_THEME = "syntax_theme"
        const val KEY_AUTO_SAVE = "auto_save"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_REMOTE_BRIDGE_URL = "remote_bridge_url"

        const val KEY_SDK_API = "toolchain_sdk_api"
        const val KEY_AGP_VERSION = "toolchain_agp"
        const val KEY_GRADLE_VERSION = "toolchain_gradle"
        const val KEY_JDK_VERSION = "toolchain_jdk"
        const val KEY_NDK_VERSION = "toolchain_ndk"
        const val KEY_CMAKE_VERSION = "toolchain_cmake"
        const val KEY_INSTALLED_SDK_IDS = "installed_sdk_ids"

        val DEFAULT_SDK_IDS = setOf("sdk_api_35", "engine_java_dex", "engine_axml_arsc", "engine_apksig")
    }

    fun getSettings(): IdeSettings {
        val themeStr = prefs.getString(KEY_SYNTAX_THEME, SyntaxThemePreset.NASJE_MIDNIGHT.name)
        val themePreset = SyntaxThemePreset.entries.firstOrNull { it.name == themeStr }
            ?: SyntaxThemePreset.NASJE_MIDNIGHT
        return IdeSettings(
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, true),
            editorFontSizeSp = prefs.getInt(KEY_FONT_SIZE_SP, 14),
            editorFontFamily = prefs.getString(KEY_FONT_FAMILY, "JetBrains Mono") ?: "JetBrains Mono",
            syntaxTheme = themePreset,
            autoSave = prefs.getBoolean(KEY_AUTO_SAVE, true),
            appLanguage = prefs.getString(KEY_APP_LANGUAGE, "fr") ?: "fr",
            remoteBuildBridgeUrl = prefs.getString(KEY_REMOTE_BRIDGE_URL, "") ?: ""
        )
    }

    fun updateSettings(transform: (IdeSettings) -> IdeSettings): IdeSettings {
        val updated = transform(getSettings())
        prefs.edit()
            .putBoolean(KEY_DARK_THEME, updated.darkTheme)
            .putInt(KEY_FONT_SIZE_SP, updated.editorFontSizeSp)
            .putString(KEY_FONT_FAMILY, updated.editorFontFamily)
            .putString(KEY_SYNTAX_THEME, updated.syntaxTheme.name)
            .putBoolean(KEY_AUTO_SAVE, updated.autoSave)
            .putString(KEY_APP_LANGUAGE, updated.appLanguage)
            .putString(KEY_REMOTE_BRIDGE_URL, updated.remoteBuildBridgeUrl)
            .apply()
        return updated
    }

    fun getToolchain(): ToolchainConfig {
        return ToolchainConfig(
            androidSdkApi = prefs.getInt(KEY_SDK_API, 35),
            agpVersion = prefs.getString(KEY_AGP_VERSION, "8.7.3") ?: "8.7.3",
            gradleVersion = prefs.getString(KEY_GRADLE_VERSION, "8.10.2") ?: "8.10.2",
            jdkVersion = prefs.getString(KEY_JDK_VERSION, "17") ?: "17",
            ndkVersion = prefs.getString(KEY_NDK_VERSION, "27.1.12297006") ?: "27.1.12297006",
            cmakeVersion = prefs.getString(KEY_CMAKE_VERSION, "3.22.1") ?: "3.22.1",
            remoteBuildBridgeUrl = prefs.getString(KEY_REMOTE_BRIDGE_URL, "") ?: ""
        )
    }

    fun updateToolchain(transform: (ToolchainConfig) -> ToolchainConfig): ToolchainConfig {
        val updated = transform(getToolchain())
        prefs.edit()
            .putInt(KEY_SDK_API, updated.androidSdkApi)
            .putString(KEY_AGP_VERSION, updated.agpVersion)
            .putString(KEY_GRADLE_VERSION, updated.gradleVersion)
            .putString(KEY_JDK_VERSION, updated.jdkVersion)
            .putString(KEY_NDK_VERSION, updated.ndkVersion)
            .putString(KEY_CMAKE_VERSION, updated.cmakeVersion)
            .putString(KEY_REMOTE_BRIDGE_URL, updated.remoteBuildBridgeUrl)
            .apply()
        return updated
    }

    fun getInstalledSdkIds(): Set<String> {
        return prefs.getStringSet(KEY_INSTALLED_SDK_IDS, DEFAULT_SDK_IDS)?.toSet() ?: DEFAULT_SDK_IDS
    }

    fun installLocalSdkMetadata(componentId: String): Boolean {
        val sdkDir = File(context.filesDir, "sdks/$componentId")
        if (!sdkDir.exists()) sdkDir.mkdirs()
        val manifestFile = File(sdkDir, "package.xml")
        manifestFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <sdk-package id="$componentId" installed-at="${System.currentTimeMillis()}" />
            """.trimIndent()
        )
        val current = getInstalledSdkIds().toMutableSet()
        current.add(componentId)
        prefs.edit().putStringSet(KEY_INSTALLED_SDK_IDS, current).apply()
        return true
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

    fun clearBuildCache(): Long {
        val buildsDir = File(context.filesDir, "builds")
        val freed = dirSize(buildsDir)
        if (buildsDir.exists()) {
            buildsDir.deleteRecursively()
        }
        return freed
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }
}
