package com.example

import com.example.compiler.JavaToDexCompiler
import com.example.compiler.ResourcesArscCompiler
import com.example.data.ProjectConfigRepository
import com.example.editor.CodeAnalyzer
import com.example.model.DiagnosticSeverity
import com.example.model.IdeLanguage
import com.example.model.ProjectInfo
import com.example.model.ProjectType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory

class ExampleUnitTest {

    @Test
    fun resourcesArscCompilerGeneratesValidBinaryResourceTable() {
        val tempRes = createTempDirectory("nasje_res_test").toFile()
        File(tempRes, "values").mkdirs()
        File(tempRes, "layout").mkdirs()
        File(tempRes, "values/strings.xml").writeText(
            """<resources><string name="app_name">Demo</string></resources>"""
        )
        File(tempRes, "values/colors.xml").writeText(
            """<resources><color name="primary">#3B82F6</color></resources>"""
        )
        File(tempRes, "layout/activity_main.xml").writeText(
            """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"><TextView android:id="@+id/title_text"/></LinearLayout>"""
        )

        val compiled = ResourcesArscCompiler.compile(tempRes, "com.nasje.demo")
        assertTrue(compiled.arscBytes.size > 100)
        val type = ByteBuffer.wrap(compiled.arscBytes, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(0x0002.toShort(), type)
        assertTrue(compiled.resourceIds.containsKey("string/app_name"))
        assertTrue(compiled.resourceIds.containsKey("layout/activity_main"))
        assertTrue(compiled.resourceIds.containsKey("id/title_text"))
        tempRes.deleteRecursively()
    }

    @Test
    fun javaToDexCompilerEmitsValidDex035HeaderAndClass() {
        val tempDir = createTempDirectory("nasje_java_test").toFile()
        val javaFile = File(tempDir, "MainActivity.java")
        javaFile.writeText(
            """
            package com.nasje.demo;
            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;
            import android.widget.Toast;

            public class MainActivity extends Activity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.activity_main);
                    TextView tv = findViewById(R.id.title_text);
                    tv.setText("Hello NASJE");
                    Toast.makeText(this, "OK", Toast.LENGTH_SHORT).show();
                }
            }
            """.trimIndent()
        )

        val out = JavaToDexCompiler.compileJavaSources(
            javaFiles = listOf(javaFile),
            packageName = "com.nasje.demo",
            resourceIds = mapOf("layout/activity_main" to 0x7f030000, "id/title_text" to 0x7f050000)
        )
        assertEquals("dex\n035", String(out.dexBytes, 0, 7, Charsets.US_ASCII))
        assertTrue(out.compiledClasses.contains("com.nasje.demo.MainActivity"))
        tempDir.deleteRecursively()
    }

    @Test
    fun codeAnalyzerDetectsSyntaxErrorsAccurately() {
        val javaDiags = CodeAnalyzer.analyzeFile(
            filePath = "/tmp/MainActivity.java",
            fileName = "MainActivity.java",
            content = "package com.a;\npublic class MainActivity {\n  int x = 5\n}\n",
            language = IdeLanguage.JAVA
        )
        assertTrue(javaDiags.any { it.severity == DiagnosticSeverity.ERROR && it.line == 3 })

        val jsonDiags = CodeAnalyzer.analyzeFile(
            filePath = "/tmp/data.json",
            fileName = "data.json",
            content = "{ invalid json ",
            language = IdeLanguage.JSON
        )
        assertTrue(jsonDiags.any { it.severity == DiagnosticSeverity.ERROR })

        val validKotlin = CodeAnalyzer.analyzeFile(
            filePath = "/tmp/Main.kt",
            fileName = "Main.kt",
            content = "package com.a\nclass Main {\n  fun hello() { val x = 1 }\n}\n",
            language = IdeLanguage.KOTLIN
        )
        assertFalse(validKotlin.any { it.severity == DiagnosticSeverity.ERROR })
    }

    @Test
    fun configRepositoryPersistsGeneralSettingsAndModules() {
        runBlocking {
            val tempRoot = createTempDirectory("nasje_cfg_test").toFile()
            val appDir = File(tempRoot, "app/src/main/res/values").apply { mkdirs() }
            File(tempRoot, "settings.gradle").writeText("rootProject.name = \"Cfg\"\ninclude ':app'\n")
            File(tempRoot, "app/build.gradle").writeText(
                """
                android {
                    namespace 'com.nasje.old'
                    compileSdk 34
                    defaultConfig {
                        applicationId "com.nasje.old"
                        minSdk 24
                        targetSdk 34
                        versionCode 1
                        versionName "1.0"
                    }
                }
                dependencies {
                }
                """.trimIndent()
            )
            File(appDir, "strings.xml").writeText("<resources><string name=\"app_name\">Old</string></resources>")
            File(tempRoot, "app/src/main/AndroidManifest.xml").writeText("<manifest package=\"com.nasje.old\"><application/></manifest>")

            val project = ProjectInfo(
                id = "test",
                name = "Old",
                packageName = "com.nasje.old",
                rootPath = tempRoot.absolutePath,
                projectType = ProjectType.ANDROID_JAVA,
                primaryLanguage = IdeLanguage.JAVA,
                moduleName = "app",
                versionName = "1.0",
                versionCode = 1,
                minSdk = 24,
                targetSdk = 34,
                createdAt = 0L,
                lastModifiedAt = 0L
            )
            val repo = ProjectConfigRepository()
            val updated = repo.applyGeneralAndDependencies(
                project = project,
                appName = "NewApp",
                packageName = "com.nasje.newapp",
                versionName = "2.0",
                versionCode = 4,
                minSdk = 26,
                targetSdk = 35,
                dependencies = emptyList()
            )
            assertEquals("NewApp", updated.name)
            assertTrue(File(tempRoot, "app/build.gradle").readText().contains("com.nasje.newapp"))
            assertTrue(repo.addModule(updated, "core", "android-library", "com.nasje.core"))
            assertTrue(File(tempRoot, "settings.gradle").readText().contains(":core"))
            tempRoot.deleteRecursively()
        }
    }
}
