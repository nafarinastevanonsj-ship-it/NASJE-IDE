package com.example.data

import android.content.Context
import com.example.model.IdeLanguage
import com.example.model.ProjectInfo
import com.example.model.ProjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val projectsRoot: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    private val indexFile: File
        get() = File(projectsRoot, "projects_index.json")

    suspend fun listProjects(): List<ProjectInfo> = withContext(Dispatchers.IO) {
        readIndex()
            .filter { File(it.rootPath).exists() }
            .sortedByDescending { it.lastModifiedAt }
    }

    suspend fun getProject(projectId: String): ProjectInfo? = withContext(Dispatchers.IO) {
        readIndex().firstOrNull { it.id == projectId }
    }

    suspend fun createProject(
        name: String,
        packageName: String,
        projectType: ProjectType,
        primaryLanguage: IdeLanguage,
        moduleName: String,
        versionName: String,
        minSdk: Int,
        targetSdk: Int
    ): ProjectInfo = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifEmpty { "Application" }
        val cleanModule = moduleName.trim().lowercase().replace(Regex("[^a-z0-9_]"), "").ifEmpty { "app" }
        val cleanPackage = sanitizePackage(packageName)
        val id = UUID.randomUUID().toString().take(8)
        val folderSlug = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "project" }
        var projectDir = File(projectsRoot, "${folderSlug}_$id")
        while (projectDir.exists()) {
            projectDir = File(projectsRoot, "${folderSlug}_${UUID.randomUUID().toString().take(6)}")
        }
        projectDir.mkdirs()

        val now = System.currentTimeMillis()
        val project = ProjectInfo(
            id = id,
            name = cleanName,
            packageName = cleanPackage,
            rootPath = projectDir.absolutePath,
            projectType = projectType,
            primaryLanguage = primaryLanguage,
            moduleName = cleanModule,
            versionName = versionName.trim().ifEmpty { "1.0" },
            versionCode = 1,
            minSdk = minSdk,
            targetSdk = targetSdk,
            createdAt = now,
            lastModifiedAt = now
        )

        scaffoldProjectFiles(project)

        val current = readIndex().toMutableList()
        current.add(0, project)
        writeIndex(current)
        project
    }

    suspend fun renameProject(projectId: String, newName: String): ProjectInfo? = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@withContext null
        val list = readIndex().toMutableList()
        val idx = list.indexOfFirst { it.id == projectId }
        if (idx == -1) return@withContext null
        val updated = list[idx].copy(name = trimmed, lastModifiedAt = System.currentTimeMillis())
        list[idx] = updated
        writeIndex(list)

        // Also update strings.xml app_name
        val stringsFile = File(updated.rootPath, "${updated.moduleName}/src/main/res/values/strings.xml")
        if (stringsFile.exists()) {
            val content = stringsFile.readText()
            val updatedXml = content.replace(
                Regex("<string name=\"app_name\">.*?</string>"),
                "<string name=\"app_name\">${escapeXml(trimmed)}</string>"
            )
            stringsFile.writeText(updatedXml)
        }
        updated
    }

    suspend fun duplicateProject(projectId: String): ProjectInfo? = withContext(Dispatchers.IO) {
        val list = readIndex().toMutableList()
        val source = list.firstOrNull { it.id == projectId } ?: return@withContext null
        val sourceDir = File(source.rootPath)
        if (!sourceDir.exists()) return@withContext null

        val newId = UUID.randomUUID().toString().take(8)
        val newName = "${source.name} (Copie)"
        val slug = newName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "project" }
        val destDir = File(projectsRoot, "${slug}_$newId")
        sourceDir.copyRecursively(destDir, overwrite = true)

        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = newId,
            name = newName,
            rootPath = destDir.absolutePath,
            createdAt = now,
            lastModifiedAt = now
        )
        list.add(0, copy)
        writeIndex(list)
        copy
    }

    suspend fun deleteProject(projectId: String): Boolean = withContext(Dispatchers.IO) {
        val list = readIndex().toMutableList()
        val target = list.firstOrNull { it.id == projectId } ?: return@withContext false
        File(target.rootPath).deleteRecursively()
        list.removeAll { it.id == projectId }
        writeIndex(list)
        true
    }

    suspend fun updateProjectMetadata(updated: ProjectInfo) = withContext(Dispatchers.IO) {
        val list = readIndex().toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            list[idx] = updated.copy(lastModifiedAt = System.currentTimeMillis())
            writeIndex(list)
        }
    }

    suspend fun touchProjectModified(projectId: String) = withContext(Dispatchers.IO) {
        val list = readIndex().toMutableList()
        val idx = list.indexOfFirst { it.id == projectId }
        if (idx != -1) {
            list[idx] = list[idx].copy(lastModifiedAt = System.currentTimeMillis())
            writeIndex(list)
        }
    }

    private fun scaffoldProjectFiles(project: ProjectInfo) {
        val root = File(project.rootPath)
        val mod = project.moduleName
        val pkgPath = project.packageName.replace('.', '/')
        val useKts = project.projectType == ProjectType.ANDROID_KOTLIN ||
            (project.projectType == ProjectType.ANDROID_CUSTOM && project.primaryLanguage == IdeLanguage.KOTLIN)

        // Root Gradle configuration
        if (useKts) {
            File(root, "settings.gradle.kts").writeText(
                """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                rootProject.name = "${project.name}"
                include(":$mod")
                """.trimIndent() + "\n"
            )
            File(root, "build.gradle.kts").writeText(
                """
                plugins {
                    id("com.android.application") version "8.7.3" apply false
                    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
                }
                """.trimIndent() + "\n"
            )
        } else {
            File(root, "settings.gradle").writeText(
                """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                rootProject.name = "${project.name}"
                include ':$mod'
                """.trimIndent() + "\n"
            )
            File(root, "build.gradle").writeText(
                """
                plugins {
                    id 'com.android.application' version '8.7.3' apply false
                }
                """.trimIndent() + "\n"
            )
        }

        File(root, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            android.nonTransitiveRClass=true
            """.trimIndent() + "\n"
        )

        val gradleWrapperDir = File(root, "gradle/wrapper").apply { mkdirs() }
        File(gradleWrapperDir, "gradle-wrapper.properties").writeText(
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """.trimIndent() + "\n"
        )

        // Module directories
        val moduleDir = File(root, mod).apply { mkdirs() }
        val mainDir = File(moduleDir, "src/main").apply { mkdirs() }
        val resDir = File(mainDir, "res").apply { mkdirs() }
        val valuesDir = File(resDir, "values").apply { mkdirs() }
        val layoutDir = File(resDir, "layout").apply { mkdirs() }
        val drawableDir = File(resDir, "drawable").apply { mkdirs() }
        File(mainDir, "assets").mkdirs()
        File(moduleDir, "libs").mkdirs()

        // AndroidManifest.xml
        File(mainDir, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="${project.packageName}">

                <application
                    android:allowBackup="true"
                    android:label="@string/app_name"
                    android:icon="@drawable/ic_app_emblem"
                    android:supportsRtl="true">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """.trimIndent() + "\n"
        )

        // Resources
        File(valuesDir, "strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">${escapeXml(project.name)}</string>
                <string name="welcome_title">Bienvenue dans ${escapeXml(project.name)}</string>
                <string name="welcome_subtitle">Compilé nativement avec NASJE IDE</string>
                <string name="action_button">Tester l'action</string>
            </resources>
            """.trimIndent() + "\n"
        )

        File(valuesDir, "colors.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="primary_blue">#3B82F6</color>
                <color name="accent_violet">#8B5CF6</color>
                <color name="surface_dark">#0B1120</color>
                <color name="text_light">#F8FAFC</color>
            </resources>
            """.trimIndent() + "\n"
        )

        File(drawableDir, "ic_app_emblem.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="48dp"
                android:height="48dp"
                android:viewportWidth="24"
                android:viewportHeight="24">
                <path
                    android:fillColor="#3B82F6"
                    android:pathData="M12,2L2,7l10,5 10,-5 -10,-5zM2,17l10,5 10,-5M2,12l10,5 10,-5" />
            </vector>
            """.trimIndent() + "\n"
        )

        File(layoutDir, "activity_main.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="24dp"
                android:background="#0B1120">

                <TextView
                    android:id="@+id/title_text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/welcome_title"
                    android:textColor="#F8FAFC"
                    android:textSize="22sp" />

                <TextView
                    android:id="@+id/subtitle_text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/welcome_subtitle"
                    android:textColor="#94A3B8"
                    android:textSize="14sp" />

            </LinearLayout>
            """.trimIndent() + "\n"
        )

        // Language-specific source files and module build.gradle
        when (project.projectType) {
            ProjectType.ANDROID_JAVA -> {
                writeJavaModuleGradle(moduleDir, project, usesCpp = false)
                val javaDir = File(mainDir, "java/$pkgPath").apply { mkdirs() }
                File(javaDir, "MainActivity.java").writeText(javaActivityTemplate(project))
            }

            ProjectType.ANDROID_KOTLIN -> {
                writeKotlinModuleGradle(moduleDir, project)
                val kotlinDir = File(mainDir, "kotlin/$pkgPath").apply { mkdirs() }
                File(kotlinDir, "MainActivity.kt").writeText(kotlinActivityTemplate(project))
            }

            ProjectType.ANDROID_CPP -> {
                writeJavaModuleGradle(moduleDir, project, usesCpp = true)
                val javaDir = File(mainDir, "java/$pkgPath").apply { mkdirs() }
                File(javaDir, "MainActivity.java").writeText(cppBridgeJavaActivityTemplate(project))
                val cppDir = File(mainDir, "cpp").apply { mkdirs() }
                File(cppDir, "CMakeLists.txt").writeText(cmakeTemplate(project.moduleName))
                File(cppDir, "native-lib.cpp").writeText(cppNativeLibTemplate(project.packageName))
            }

            ProjectType.ANDROID_EMPTY -> {
                writeJavaModuleGradle(moduleDir, project, usesCpp = false)
                val javaDir = File(mainDir, "java/$pkgPath").apply { mkdirs() }
                File(javaDir, "MainActivity.java").writeText(emptyJavaActivityTemplate(project))
            }

            ProjectType.ANDROID_CUSTOM -> {
                when (project.primaryLanguage) {
                    IdeLanguage.KOTLIN -> {
                        writeKotlinModuleGradle(moduleDir, project)
                        val kotlinDir = File(mainDir, "kotlin/$pkgPath").apply { mkdirs() }
                        File(kotlinDir, "MainActivity.kt").writeText(kotlinActivityTemplate(project))
                    }
                    IdeLanguage.CPP, IdeLanguage.C -> {
                        writeJavaModuleGradle(moduleDir, project, usesCpp = true)
                        val javaDir = File(mainDir, "java/$pkgPath").apply { mkdirs() }
                        File(javaDir, "MainActivity.java").writeText(cppBridgeJavaActivityTemplate(project))
                        val cppDir = File(mainDir, "cpp").apply { mkdirs() }
                        File(cppDir, "CMakeLists.txt").writeText(cmakeTemplate(project.moduleName))
                        val ext = if (project.primaryLanguage == IdeLanguage.C) "native-lib.c" else "native-lib.cpp"
                        File(cppDir, ext).writeText(cppNativeLibTemplate(project.packageName))
                    }
                    else -> {
                        writeJavaModuleGradle(moduleDir, project, usesCpp = false)
                        val javaDir = File(mainDir, "java/$pkgPath").apply { mkdirs() }
                        File(javaDir, "MainActivity.java").writeText(javaActivityTemplate(project))
                        if (project.primaryLanguage in listOf(IdeLanguage.HTML, IdeLanguage.CSS, IdeLanguage.JAVASCRIPT)) {
                            val assetsDir = File(mainDir, "assets").apply { mkdirs() }
                            File(assetsDir, "index.html").writeText(
                                """
                                <!DOCTYPE html>
                                <html lang="fr">
                                <head>
                                    <meta charset="UTF-8" />
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                                    <title>${project.name}</title>
                                    <link rel="stylesheet" href="style.css" />
                                </head>
                                <body>
                                    <main class="card">
                                        <h1>${project.name}</h1>
                                        <p>Projet Web Android créé avec NASJE IDE.</p>
                                    </main>
                                    <script src="app.js"></script>
                                </body>
                                </html>
                                """.trimIndent() + "\n"
                            )
                            File(assetsDir, "style.css").writeText(
                                """
                                body {
                                    background: #0b1120;
                                    color: #f8fafc;
                                    font-family: sans-serif;
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                    min-height: 100vh;
                                    margin: 0;
                                }
                                .card {
                                    border: 1px solid #3b82f6;
                                    border-radius: 16px;
                                    padding: 24px;
                                }
                                """.trimIndent() + "\n"
                            )
                            File(assetsDir, "app.js").writeText(
                                """
                                console.log("NASJE IDE Web asset initialized for ${project.name}");
                                """.trimIndent() + "\n"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun writeJavaModuleGradle(moduleDir: File, project: ProjectInfo, usesCpp: Boolean) {
        val cmakeBlock = if (usesCpp) {
            """
                externalNativeBuild {
                    cmake {
                        path file('src/main/cpp/CMakeLists.txt')
                        version '3.22.1'
                    }
                }
            """.trimIndent()
        } else ""

        File(moduleDir, "build.gradle").writeText(
            """
            plugins {
                id 'com.android.application'
            }

            android {
                namespace '${project.packageName}'
                compileSdk ${project.targetSdk}

                defaultConfig {
                    applicationId "${project.packageName}"
                    minSdk ${project.minSdk}
                    targetSdk ${project.targetSdk}
                    versionCode ${project.versionCode}
                    versionName "${project.versionName}"
                }

                buildTypes {
                    release {
                        minifyEnabled false
                    }
                }

                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_11
                    targetCompatibility JavaVersion.VERSION_11
                }
                ${if (cmakeBlock.isNotEmpty()) "\n    $cmakeBlock\n" else ""}
            }

            dependencies {
            }
            """.trimIndent() + "\n"
        )
    }

    private fun writeKotlinModuleGradle(moduleDir: File, project: ProjectInfo) {
        File(moduleDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "${project.packageName}"
                compileSdk = ${project.targetSdk}

                defaultConfig {
                    applicationId = "${project.packageName}"
                    minSdk = ${project.minSdk}
                    targetSdk = ${project.targetSdk}
                    versionCode = ${project.versionCode}
                    versionName = "${project.versionName}"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
                kotlinOptions {
                    jvmTarget = "11"
                }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("androidx.appcompat:appcompat:1.7.0")
            }
            """.trimIndent() + "\n"
        )
    }

    private fun javaActivityTemplate(project: ProjectInfo): String = """
        package ${project.packageName};

        import android.app.Activity;
        import android.os.Bundle;
        import android.widget.TextView;
        import android.widget.Toast;

        public class MainActivity extends Activity {

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                TextView title = findViewById(R.id.title_text);
                if (title != null) {
                    title.setText("Bonjour depuis ${project.name}");
                }
                Toast.makeText(this, "Application démarrée", Toast.LENGTH_SHORT).show();
            }
        }
    """.trimIndent() + "\n"

    private fun emptyJavaActivityTemplate(project: ProjectInfo): String = """
        package ${project.packageName};

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);
            }
        }
    """.trimIndent() + "\n"

    private fun kotlinActivityTemplate(project: ProjectInfo): String = """
        package ${project.packageName}

        import android.os.Bundle
        import android.widget.TextView
        import androidx.appcompat.app.AppCompatActivity

        class MainActivity : AppCompatActivity() {

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.activity_main)

                val titleView = findViewById<TextView>(R.id.title_text)
                titleView?.text = "Bienvenue dans ${project.name}"
            }
        }
    """.trimIndent() + "\n"

    private fun cppBridgeJavaActivityTemplate(project: ProjectInfo): String = """
        package ${project.packageName};

        import android.app.Activity;
        import android.os.Bundle;
        import android.widget.TextView;

        public class MainActivity extends Activity {

            static {
                System.loadLibrary("${project.moduleName}");
            }

            public native String stringFromJNI();

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                TextView title = findViewById(R.id.title_text);
                if (title != null) {
                    title.setText(stringFromJNI());
                }
            }
        }
    """.trimIndent() + "\n"

    private fun cmakeTemplate(moduleName: String): String = """
        cmake_minimum_required(VERSION 3.22.1)
        project("$moduleName")

        add_library($moduleName SHARED
            native-lib.cpp)

        find_library(log-lib
            log)

        target_link_libraries($moduleName
            ${'$'}{log-lib})
    """.trimIndent() + "\n"

    private fun cppNativeLibTemplate(packageName: String): String {
        val jniPkg = packageName.replace("_", "_1").replace('.', '_')
        return """
            #include <jni.h>
            #include <string>

            extern "C" JNIEXPORT jstring JNICALL
            Java_${jniPkg}_MainActivity_stringFromJNI(
                    JNIEnv* env,
                    jobject /* this */) {
                std::string hello = "Bonjour depuis C++ NDK !";
                return env->NewStringUTF(hello.c_str());
            }
        """.trimIndent() + "\n"
    }

    private fun sanitizePackage(raw: String): String {
        val parts = raw.trim().lowercase().split('.').map { seg ->
            seg.replace(Regex("[^a-z0-9_]"), "").let {
                if (it.isEmpty() || it.first().isDigit()) "app$it" else it
            }
        }.filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> parts.joinToString(".")
            parts.size == 1 -> "com.nasje.${parts[0]}"
            else -> "com.nasje.myapp"
        }
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun readIndex(): List<ProjectInfo> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                ProjectInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    packageName = obj.getString("packageName"),
                    rootPath = obj.getString("rootPath"),
                    projectType = ProjectType.entries.firstOrNull {
                        it.name == obj.optString("projectType")
                    } ?: ProjectType.ANDROID_JAVA,
                    primaryLanguage = IdeLanguage.entries.firstOrNull {
                        it.name == obj.optString("primaryLanguage")
                    } ?: IdeLanguage.JAVA,
                    moduleName = obj.optString("moduleName", "app"),
                    versionName = obj.optString("versionName", "1.0"),
                    versionCode = obj.optInt("versionCode", 1),
                    minSdk = obj.optInt("minSdk", 24),
                    targetSdk = obj.optInt("targetSdk", 35),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    lastModifiedAt = obj.optLong("lastModifiedAt", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(projects: List<ProjectInfo>) {
        val arr = JSONArray()
        projects.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("packageName", p.packageName)
                    put("rootPath", p.rootPath)
                    put("projectType", p.projectType.name)
                    put("primaryLanguage", p.primaryLanguage.name)
                    put("moduleName", p.moduleName)
                    put("versionName", p.versionName)
                    put("versionCode", p.versionCode)
                    put("minSdk", p.minSdk)
                    put("targetSdk", p.targetSdk)
                    put("createdAt", p.createdAt)
                    put("lastModifiedAt", p.lastModifiedAt)
                }
            )
        }
        indexFile.writeText(arr.toString(2))
    }
}
