package com.NASJEProduction.NASJEIDE

import com.NASJEProduction.NASJEIDE.compiler.JavaToDexCompiler
import com.NASJEProduction.NASJEIDE.compiler.ResourcesArscCompiler
import com.NASJEProduction.NASJEIDE.data.ProjectConfigRepository
import com.NASJEProduction.NASJEIDE.editor.CodeAnalyzer
import com.NASJEProduction.NASJEIDE.model.DependencyItem
import com.NASJEProduction.NASJEIDE.model.DiagnosticSeverity
import com.NASJEProduction.NASJEIDE.model.IdeLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NasjeUnitTest {

    @Test
    fun resourcesArscCompiler_buildsValidTable() {
        val tempResDir = Files.createTempDirectory("nasje_res_test").toFile()
        try {
            val valuesDir = File(tempResDir, "values").apply { mkdirs() }
            File(valuesDir, "strings.xml").writeText(
                """
                <resources>
                    <string name="app_name">TestApp</string>
                </resources>
                """.trimIndent()
            )
            File(tempResDir, "layout").apply { mkdirs() }.let {
                File(it, "activity_main.xml").writeText("<LinearLayout />")
            }

            val compiled = ResourcesArscCompiler.compile(tempResDir, "com.nasje.testapp")
            assertTrue("ARSC bytes should not be empty", compiled.arscBytes.size > 16)
            assertEquals(0x02.toByte(), compiled.arscBytes[0])
            assertEquals(0x00.toByte(), compiled.arscBytes[1])
            assertTrue(compiled.resourceIds.containsKey("string/app_name"))
            assertTrue(compiled.resourceIds.containsKey("layout/activity_main"))
        } finally {
            tempResDir.deleteRecursively()
        }
    }

    @Test
    fun javaToDexCompiler_producesValidDexMagic() {
        val tempDir = Files.createTempDirectory("nasje_dex_test").toFile()
        try {
            val javaFile = File(tempDir, "MainActivity.java")
            javaFile.writeText(
                """
                package com.nasje.demo;
                import android.app.Activity;
                import android.os.Bundle;
                public class MainActivity extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);
                    }
                }
                """.trimIndent()
            )
            val result = JavaToDexCompiler.compileJavaSources(
                javaFiles = listOf(javaFile),
                packageName = "com.nasje.demo",
                resourceIds = mapOf("layout/activity_main" to 0x7f020000)
            )
            val magic = String(result.dexBytes.sliceArray(0 until 8), Charsets.US_ASCII)
            assertEquals("dex\n035\u0000", magic)
            assertTrue(result.dexBytes.size > 112)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun codeAnalyzer_detectsErrorsAndValidJava() {
        val badJava = """
            package com.nasje.bad;
            public class Broken {
                public void test() {
                    int x = 10
                }
            }
        """.trimIndent()
        val diags = CodeAnalyzer.analyzeFile("Broken.java", "Broken.java", badJava, IdeLanguage.JAVA)
        assertTrue(diags.any { it.severity == DiagnosticSeverity.ERROR })

        val goodJava = """
            package com.nasje.good;
            public class Valid {
                public void test() {
                    int x = 10;
                }
            }
        """.trimIndent()
        val cleanDiags = CodeAnalyzer.analyzeFile("Valid.java", "Valid.java", goodJava, IdeLanguage.JAVA)
        assertEquals(0, cleanDiags.count { it.severity == DiagnosticSeverity.ERROR })
    }

    @Test
    fun projectConfigRepository_validatesDependencies() {
        val repo = ProjectConfigRepository()
        val depOk = DependencyItem("com.google.code.gson", "gson", "2.11.0")
        assertTrue(repo.checkCompatibility(depOk, 35).first)

        val depBad = DependencyItem("com.example", "missing-ver", "")
        assertFalse(repo.checkCompatibility(depBad, 35).first)
    }
}
