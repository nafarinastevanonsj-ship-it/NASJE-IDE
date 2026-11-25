package com.NASJEProduction.NASJEIDE.model

enum class ProjectType(
    val displayName: String,
    val subtitle: String,
    val defaultLanguage: IdeLanguage
) {
    ANDROID_JAVA(
        displayName = "Projet Android Java",
        subtitle = "Application Android native en Java avec SDK & compilation APK intégrée",
        defaultLanguage = IdeLanguage.JAVA
    ),
    ANDROID_KOTLIN(
        displayName = "Projet Android Kotlin",
        subtitle = "Application Android moderne en Kotlin & Gradle Kotlin DSL",
        defaultLanguage = IdeLanguage.KOTLIN
    ),
    ANDROID_CPP(
        displayName = "Projet Android C++",
        subtitle = "Application Android hybride avec NDK, JNI et CMakeLists.txt",
        defaultLanguage = IdeLanguage.CPP
    ),
    ANDROID_EMPTY(
        displayName = "Projet Android vide",
        subtitle = "Structure Android minimale prête à personnaliser",
        defaultLanguage = IdeLanguage.JAVA
    ),
    ANDROID_CUSTOM(
        displayName = "Projet personnalisé",
        subtitle = "Configuration flexible Java/Kotlin/Web/C++ selon vos choix",
        defaultLanguage = IdeLanguage.JAVA
    )
}

enum class IdeLanguage(
    val label: String,
    val extensions: List<String>,
    val accentHex: Long
) {
    JAVA("Java", listOf("java"), 0xFFF97316),
    KOTLIN("Kotlin", listOf("kt"), 0xFFA855F7),
    CPP("C++", listOf("cpp", "cc", "cxx", "h", "hpp"), 0xFF38BDF8),
    C("C", listOf("c"), 0xFF0EA5E9),
    XML("XML", listOf("xml"), 0xFF10B981),
    GRADLE("Gradle", listOf("gradle"), 0xFF22D3EE),
    KOTLIN_DSL("Kotlin DSL", listOf("kts"), 0xFFC084FC),
    HTML("HTML", listOf("html", "htm"), 0xFFFB7185),
    CSS("CSS", listOf("css"), 0xFF60A5FA),
    JAVASCRIPT("JavaScript", listOf("js"), 0xFFFACC15),
    JSON("JSON", listOf("json"), 0xFF34D399),
    PROPERTIES("Properties", listOf("properties"), 0xFF94A3B8),
    MARKDOWN("Markdown", listOf("md"), 0xFFCBD5E1),
    TEXT("Texte", listOf("txt"), 0xFF94A3B8);

    val accentColorInt: Int get() = accentHex.toInt()

    companion object {
        fun fromFileName(fileName: String): IdeLanguage {
            val lower = fileName.lowercase()
            if (lower == "cmakelists.txt") return CPP
            val ext = lower.substringAfterLast('.', "")
            return entries.firstOrNull { ext in it.extensions } ?: TEXT
        }
    }
}

enum class ProjectSortOrder(val label: String) {
    RECENT("Plus récents"),
    NAME("Nom (A-Z)"),
    LANGUAGE("Langage")
}

data class ProjectInfo(
    val id: String,
    val name: String,
    val packageName: String,
    val rootPath: String,
    val projectType: ProjectType,
    val primaryLanguage: IdeLanguage,
    val moduleName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val createdAt: Long,
    val lastModifiedAt: Long
)

data class FileNode(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val children: List<FileNode> = emptyList(),
    val language: IdeLanguage = IdeLanguage.fromFileName(name)
)

data class EditorTab(
    val filePath: String,
    val relativePath: String,
    val fileName: String,
    val language: IdeLanguage,
    val content: String,
    val savedContent: String,
    val cursorIndex: Int = 0,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList(),
    val pendingLineJump: Int? = null
) {
    val isDirty: Boolean get() = content != savedContent
}

data class ModuleConfig(
    val name: String,
    val type: String, // "application", "android-library", "native-cpp"
    val packageName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val versionName: String,
    val versionCode: Int,
    val usesCpp: Boolean = false
)

data class DependencyItem(
    val group: String,
    val artifact: String,
    val version: String,
    val configuration: String = "implementation",
    val isCompatible: Boolean = true,
    val compatibilityMessage: String = "Compatible avec le SDK cible"
) {
    val coordinate: String get() = "$group:$artifact:$version"
}

data class SdkComponentItem(
    val id: String,
    val name: String,
    val category: String,
    val version: String,
    val sizeLabel: String,
    val status: SdkComponentStatus,
    val description: String
)

enum class SdkComponentStatus(val label: String) {
    INSTALLED_BUILT_IN("Intégré & Prêt"),
    INSTALLED_LOCAL("Installé"),
    AVAILABLE_DOWNLOAD("Téléchargeable"),
    REMOTE_BRIDGE_ONLY("Pont Gradle requis")
}

data class ToolchainConfig(
    val androidSdkApi: Int = 35,
    val agpVersion: String = "8.7.3",
    val gradleVersion: String = "8.10.2",
    val jdkVersion: String = "17",
    val ndkVersion: String = "27.1.12297006",
    val cmakeVersion: String = "3.22.1",
    val remoteBuildBridgeUrl: String = ""
)

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class DiagnosticItem(
    val id: String,
    val severity: DiagnosticSeverity,
    val typeLabel: String,
    val filePath: String,
    val fileName: String,
    val line: Int,
    val column: Int,
    val message: String,
    val details: String
)

data class BuildResult(
    val success: Boolean,
    val projectId: String,
    val projectName: String,
    val apkFilePath: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val packageName: String,
    val versionName: String,
    val durationMs: Long,
    val diagnostics: List<DiagnosticItem>,
    val buildLogs: List<String>,
    val engineLabel: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SyntaxThemePreset(val label: String) {
    NASJE_MIDNIGHT("NASJE Midnight"),
    ELECTRIC_NEON("Electric Neon"),
    CLASSIC_MONOKAI("Monokai Pro")
}

data class IdeSettings(
    val darkTheme: Boolean = true,
    val editorFontSizeSp: Int = 14,
    val editorFontFamily: String = "JetBrains Mono",
    val syntaxTheme: SyntaxThemePreset = SyntaxThemePreset.NASJE_MIDNIGHT,
    val autoSave: Boolean = true,
    val appLanguage: String = "fr",
    val remoteBuildBridgeUrl: String = ""
)
