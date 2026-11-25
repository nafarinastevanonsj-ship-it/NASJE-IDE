package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.compiler.BuildEngine
import com.example.data.FileManagerRepository
import com.example.data.ProjectConfigRepository
import com.example.data.ProjectRepository
import com.example.model.DiagnosticSeverity
import com.example.model.IdeLanguage
import com.example.model.ProjectType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appNameIsNasjeIde() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("NASJE IDE", context.getString(R.string.app_name))
    }

    @Test
    fun projectCreationScaffoldsAndCompilesRealJavaApk() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val projectRepo = ProjectRepository(context)
            val configRepo = ProjectConfigRepository()
            val buildEngine = BuildEngine(context, configRepo)

            val project = projectRepo.createProject(
                name = "TestApp",
                packageName = "com.nasje.testapp",
                projectType = ProjectType.ANDROID_JAVA,
                primaryLanguage = IdeLanguage.JAVA,
                moduleName = "app",
                versionName = "1.0",
                minSdk = 24,
                targetSdk = 35
            )

            val mainActivity = File(project.rootPath, "app/src/main/java/com/nasje/testapp/MainActivity.java")
            assertTrue(mainActivity.exists())
            assertFalse(File(project.rootPath, "app/src/main/cpp/CMakeLists.txt").exists())

            val result = buildEngine.compileProject(project, remoteBridgeUrl = "") {}
            assertTrue("Build logs: ${result.buildLogs}", result.success)
            assertNotNull(result.apkFilePath)

            val apkFile = File(result.apkFilePath!!)
            assertTrue(apkFile.exists())
            assertTrue(apkFile.length() > 1024)

            ZipFile(apkFile).use { zip ->
                assertNotNull(zip.getEntry("AndroidManifest.xml"))
                assertNotNull(zip.getEntry("resources.arsc"))
                assertNotNull(zip.getEntry("META-INF/MANIFEST.MF"))
                assertNotNull(zip.getEntry("META-INF/NASJE.SF"))
                assertNotNull(zip.getEntry("META-INF/NASJE.RSA"))
                val dexEntry = zip.getEntry("classes.dex")
                assertNotNull(dexEntry)
                val dexBytes = zip.getInputStream(dexEntry).readBytes()
                val magic = String(dexBytes, 0, 7, Charsets.US_ASCII)
                assertEquals("dex\n035", magic)
            }
            val rawApk = apkFile.readBytes()
            assertTrue(String(rawApk, Charsets.US_ASCII).contains("APK Sig Block 42"))
        }
    }

    @Test
    fun kotlinProjectWithoutBridgeDoesNotFakeApk() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val projectRepo = ProjectRepository(context)
            val buildEngine = BuildEngine(context, ProjectConfigRepository())

            val project = projectRepo.createProject(
                name = "KotlinApp",
                packageName = "com.nasje.ktapp",
                projectType = ProjectType.ANDROID_KOTLIN,
                primaryLanguage = IdeLanguage.KOTLIN,
                moduleName = "app",
                versionName = "1.0",
                minSdk = 24,
                targetSdk = 35
            )

            val result = buildEngine.compileProject(project, remoteBridgeUrl = "") {}
            assertFalse(result.success)
            assertNull(result.apkFilePath)
            assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.ERROR })
        }
    }

    @Test
    fun fileManagerAndConfigApplyPersistRealEdits() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val projectRepo = ProjectRepository(context)
            val fileRepo = FileManagerRepository(context)
            val configRepo = ProjectConfigRepository()

            val project = projectRepo.createProject(
                name = "ConfigApp",
                packageName = "com.nasje.cfg",
                projectType = ProjectType.ANDROID_JAVA,
                primaryLanguage = IdeLanguage.JAVA,
                moduleName = "app",
                versionName = "1.0",
                minSdk = 24,
                targetSdk = 35
            )

            val newFile = fileRepo.createFile("${project.rootPath}/app/src/main/java/com/nasje/cfg", "Helper.java")
            assertNotNull(newFile)
            assertTrue(newFile!!.exists())

            val updated = configRepo.applyGeneralAndDependencies(
                project = project,
                appName = "ConfigAppPro",
                packageName = "com.nasje.cfgpro",
                versionName = "2.1",
                versionCode = 7,
                minSdk = 26,
                targetSdk = 35,
                dependencies = emptyList()
            )
            assertEquals("2.1", updated.versionName)
            val gradleText = File(project.rootPath, "app/build.gradle").readText()
            assertTrue(gradleText.contains("versionCode 7"))
            assertTrue(gradleText.contains("minSdk 26"))
            assertTrue(gradleText.contains("com.nasje.cfgpro"))
        }
    }
}
