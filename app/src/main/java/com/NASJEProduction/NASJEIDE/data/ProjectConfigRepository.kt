package com.NASJEProduction.NASJEIDE.data

import com.NASJEProduction.NASJEIDE.model.DependencyItem
import com.NASJEProduction.NASJEIDE.model.ModuleConfig
import com.NASJEProduction.NASJEIDE.model.ProjectInfo
import java.io.File

data class LoadedProjectConfiguration(
    val modules: List<ModuleConfig>,
    val dependencies: List<DependencyItem>,
    val configErrors: List<String>
)

class ProjectConfigRepository {

    companion object {
        val POPULAR_DEPENDENCIES = listOf(
            DependencyItem("com.google.code.gson", "gson", "2.11.0", "implementation"),
            DependencyItem("com.squareup.okhttp3", "okhttp", "4.12.0", "implementation"),
            DependencyItem("org.json", "json", "20240303", "implementation"),
            DependencyItem("junit", "junit", "4.13.2", "testImplementation")
        )
    }

    fun loadConfiguration(project: ProjectInfo): LoadedProjectConfiguration {
        val root = File(project.rootPath)
        val moduleNames = parseSettingsModules(root).ifEmpty { listOf(project.moduleName) }
        val modules = moduleNames.map { modName -> parseModuleConfig(root, modName, project) }
        val mainModuleFile = findModuleGradleFile(root, project.moduleName)
        val deps = if (mainModuleFile != null && mainModuleFile.exists()) {
            parseDependencies(mainModuleFile.readText(), project.targetSdk)
        } else {
            emptyList()
        }
        val errors = validateConfig(modules, deps)
        return LoadedProjectConfiguration(modules, deps, errors)
    }

    fun applyGeneralAndDependencies(
        project: ProjectInfo,
        appName: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        minSdk: Int,
        targetSdk: Int,
        dependencies: List<DependencyItem>
    ): ProjectInfo {
        val root = File(project.rootPath)
        val modDir = File(root, project.moduleName)

        val gradleFile = findModuleGradleFile(root, project.moduleName)
        if (gradleFile != null && gradleFile.exists()) {
            var content = gradleFile.readText()
            val isKts = gradleFile.name.endsWith(".kts")
            content = replaceGradleProperty(content, "namespace", packageName)
            content = replaceGradleProperty(content, "applicationId", packageName)
            content = replaceGradleInt(content, "compileSdk", targetSdk)
            content = replaceGradleInt(content, "minSdk", minSdk)
            content = replaceGradleInt(content, "targetSdk", targetSdk)
            content = replaceGradleInt(content, "versionCode", versionCode)
            content = replaceGradleProperty(content, "versionName", versionName)
            content = replaceDependenciesBlock(content, dependencies, isKts)
            gradleFile.writeText(content)
        }

        val stringsFile = File(modDir, "src/main/res/values/strings.xml")
        if (stringsFile.exists()) {
            val xml = stringsFile.readText()
            val updatedXml = xml.replace(
                Regex("<string name=\"app_name\">.*?</string>"),
                "<string name=\"app_name\">${escapeXml(appName)}</string>"
            )
            stringsFile.writeText(updatedXml)
        }

        val manifestFile = File(modDir, "src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val mXml = manifestFile.readText()
            val updatedManifest = if (mXml.contains("package=\"")) {
                mXml.replace(Regex("package=\"[^\"]+\""), "package=\"$packageName\"")
            } else {
                mXml
            }
            manifestFile.writeText(updatedManifest)
        }

        return project.copy(
            name = appName.trim().ifEmpty { project.name },
            packageName = packageName.trim().ifEmpty { project.packageName },
            versionName = versionName.trim().ifEmpty { project.versionName },
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            lastModifiedAt = System.currentTimeMillis()
        )
    }

    fun addModule(
        project: ProjectInfo,
        moduleName: String,
        moduleType: String,
        packageName: String
    ): Boolean {
        val cleanName = moduleName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (cleanName.isEmpty()) return false
        val root = File(project.rootPath)
        val modDir = File(root, cleanName)
        if (modDir.exists()) return false

        val srcDir = File(modDir, "src/main/java/${packageName.replace('.', '/')}").apply { mkdirs() }
        File(modDir, "src/main/res/values").mkdirs()
        File(modDir, "src/main/AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageName" />
            """.trimIndent() + "\n"
        )
        val helperName = cleanName.replaceFirstChar { it.uppercase() } + "Module"
        File(srcDir, "$helperName.java").writeText(
            """
            package $packageName;

            public class $helperName {
                public static String getModuleName() {
                    return "$cleanName";
                }
            }
            """.trimIndent() + "\n"
        )

        val pluginId = if (moduleType == "android-library") "com.android.library" else "com.android.application"
        val useKts = File(root, "settings.gradle.kts").exists()
        if (useKts) {
            File(modDir, "build.gradle.kts").writeText(
                """
                plugins {
                    id("$pluginId")
                }

                android {
                    namespace = "$packageName"
                    compileSdk = ${project.targetSdk}
                    defaultConfig {
                        minSdk = ${project.minSdk}
                    }
                }
                """.trimIndent() + "\n"
            )
            val settings = File(root, "settings.gradle.kts")
            val text = settings.readText()
            if (!text.contains(":$cleanName")) {
                settings.writeText(text.trimEnd() + "\ninclude(\":$cleanName\")\n")
            }
        } else {
            File(modDir, "build.gradle").writeText(
                """
                plugins {
                    id '$pluginId'
                }

                android {
                    namespace '$packageName'
                    compileSdk ${project.targetSdk}
                    defaultConfig {
                        minSdk ${project.minSdk}
                    }
                }
                """.trimIndent() + "\n"
            )
            val settings = File(root, "settings.gradle")
            val text = if (settings.exists()) settings.readText() else ""
            if (!text.contains(":$cleanName")) {
                settings.writeText(text.trimEnd() + "\ninclude ':$cleanName'\n")
            }
        }
        return true
    }

    fun removeModule(project: ProjectInfo, moduleName: String): Boolean {
        if (moduleName == project.moduleName) return false
        val root = File(project.rootPath)
        File(root, moduleName).deleteRecursively()
        listOf("settings.gradle.kts", "settings.gradle").forEach { name ->
            val f = File(root, name)
            if (f.exists()) {
                val lines = f.readLines().filterNot { it.contains(":$moduleName") }
                f.writeText(lines.joinToString("\n") + "\n")
            }
        }
        return true
    }

    fun updateModuleConfig(project: ProjectInfo, module: ModuleConfig): Boolean {
        val root = File(project.rootPath)
        val gradleFile = findModuleGradleFile(root, module.name) ?: return false
        if (!gradleFile.exists()) return false
        var text = gradleFile.readText()
        text = replaceGradleProperty(text, "namespace", module.packageName)
        text = replaceGradleInt(text, "minSdk", module.minSdk)
        text = replaceGradleInt(text, "targetSdk", module.targetSdk)
        text = replaceGradleInt(text, "compileSdk", module.targetSdk)
        text = replaceGradleProperty(text, "versionName", module.versionName)
        text = replaceGradleInt(text, "versionCode", module.versionCode)
        gradleFile.writeText(text)
        return true
    }

    fun checkCompatibility(dep: DependencyItem, targetSdk: Int): Pair<Boolean, String> {
        if (dep.group == "androidx.core" && dep.artifact == "core-ktx" && targetSdk < 34) {
            return false to "core-ktx ${dep.version} recommande compileSdk/targetSdk >= 34"
        }
        if (dep.version.isBlank()) {
            return false to "Numéro de version manquant"
        }
        return true to "Compatible avec API $targetSdk"
    }

    private fun parseSettingsModules(root: File): List<String> {
        val settings = listOf("settings.gradle.kts", "settings.gradle")
            .map { File(root, it) }
            .firstOrNull { it.exists() } ?: return emptyList()
        val regex = Regex(""":([a-zA-Z0-9_-]+)""")
        return regex.findAll(settings.readText()).map { it.groupValues[1] }.distinct().toList()
    }

    private fun parseModuleConfig(root: File, modName: String, project: ProjectInfo): ModuleConfig {
        val gradle = findModuleGradleFile(root, modName)
        val text = if (gradle != null && gradle.exists()) gradle.readText() else ""
        val type = when {
            text.contains("com.android.library") -> "android-library"
            File(root, "$modName/src/main/cpp/CMakeLists.txt").exists() -> "native-cpp"
            else -> "application"
        }
        val pkg = Regex("""namespace\s*=?\s*['"]([^'"]+)['"]""").find(text)?.groupValues?.get(1)
            ?: project.packageName
        val minSdk = Regex("""minSdk\s*=?\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: project.minSdk
        val targetSdk = Regex("""targetSdk\s*=?\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: project.targetSdk
        val versionName = Regex("""versionName\s*=?\s*['"]([^'"]+)['"]""").find(text)?.groupValues?.get(1)
            ?: project.versionName
        val versionCode = Regex("""versionCode\s*=?\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: project.versionCode
        return ModuleConfig(
            name = modName,
            type = type,
            packageName = pkg,
            minSdk = minSdk,
            targetSdk = targetSdk,
            versionName = versionName,
            versionCode = versionCode,
            usesCpp = File(root, "$modName/src/main/cpp/CMakeLists.txt").exists()
        )
    }

    private fun parseDependencies(gradleText: String, targetSdk: Int): List<DependencyItem> {
        val regex = Regex("""(implementation|api|testImplementation|androidTestImplementation)\s*\(?\s*['"]([^:'"]+):([^:'"]+):([^'"]+)['"]\s*\)?""")
        return regex.findAll(gradleText).map { m ->
            val item = DependencyItem(
                group = m.groupValues[2],
                artifact = m.groupValues[3],
                version = m.groupValues[4],
                configuration = m.groupValues[1]
            )
            val (ok, msg) = checkCompatibility(item, targetSdk)
            item.copy(isCompatible = ok, compatibilityMessage = msg)
        }.toList()
    }

    private fun validateConfig(modules: List<ModuleConfig>, deps: List<DependencyItem>): List<String> {
        val errs = mutableListOf<String>()
        modules.forEach { mod ->
            if (mod.minSdk > mod.targetSdk) {
                errs.add("Module '${mod.name}': minSdk (${mod.minSdk}) ne peut pas dépasser targetSdk (${mod.targetSdk}).")
            }
            if (!mod.packageName.contains('.')) {
                errs.add("Module '${mod.name}': package name invalide (${mod.packageName}).")
            }
        }
        deps.filter { !it.isCompatible }.forEach { dep ->
            errs.add("Dépendance ${dep.coordinate}: ${dep.compatibilityMessage}")
        }
        return errs
    }

    private fun findModuleGradleFile(root: File, moduleName: String): File? {
        val kts = File(root, "$moduleName/build.gradle.kts")
        if (kts.exists()) return kts
        val groovy = File(root, "$moduleName/build.gradle")
        return if (groovy.exists()) groovy else null
    }

    private fun replaceGradleProperty(text: String, key: String, value: String): String {
        val regex = Regex("""($key\s*=?\s*)['"][^'"]*['"]""")
        return if (regex.containsMatchIn(text)) {
            text.replace(regex) { m -> "${m.groupValues[1]}\"$value\"" }
        } else {
            text
        }
    }

    private fun replaceGradleInt(text: String, key: String, value: Int): String {
        val regex = Regex("""($key\s*=?\s*)\d+""")
        return if (regex.containsMatchIn(text)) {
            text.replace(regex) { m -> "${m.groupValues[1]}$value" }
        } else {
            text
        }
    }

    private fun replaceDependenciesBlock(text: String, deps: List<DependencyItem>, isKts: Boolean): String {
        val lines = deps.joinToString("\n") { dep ->
            if (isKts) {
                "    ${dep.configuration}(\"${dep.coordinate}\")"
            } else {
                "    ${dep.configuration} '${dep.coordinate}'"
            }
        }
        val blockRegex = Regex("""dependencies\s*\{[\s\S]*?\}""")
        val newBlock = if (lines.isNotEmpty()) {
            "dependencies {\n$lines\n}"
        } else {
            "dependencies {\n}"
        }
        return if (blockRegex.containsMatchIn(text)) {
            text.replace(blockRegex, newBlock)
        } else {
            text.trimEnd() + "\n\n$newBlock\n"
        }
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
