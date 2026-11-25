package com.example.compiler

import com.example.model.DiagnosticItem
import com.example.model.DiagnosticSeverity
import com.example.model.ProjectInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Connects NASJE IDE to an external headless Gradle/NDK/Kotlin Build Bridge over HTTPS/HTTP
 * when compiling projects requiring desktop toolchains (kotlinc, NDK clang++, CMake, Gradle).
 */
class RemoteBuildClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun buildViaRemoteBridge(
        bridgeUrl: String,
        project: ProjectInfo,
        outputApkFile: File,
        onLog: (String) -> Unit
    ): Pair<Boolean, List<DiagnosticItem>> = withContext(Dispatchers.IO) {
        val cleanUrl = bridgeUrl.trim().trimEnd('/')
        if (cleanUrl.isEmpty()) {
            return@withContext false to emptyList()
        }
        val zipFile = File.createTempFile("nasje_src_${project.id}", ".zip")
        try {
            onLog("Préparation de l'archive projet pour le Pont Gradle ($cleanUrl)...")
            zipDirectory(File(project.rootPath), zipFile)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("projectId", project.id)
                .addFormDataPart("moduleName", project.moduleName)
                .addFormDataPart(
                    "projectZip",
                    "${project.id}.zip",
                    zipFile.asRequestBody("application/zip".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$cleanUrl/api/build")
                .post(requestBody)
                .build()

            onLog("Envoi vers le serveur de compilation Gradle/Android SDK...")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errText = response.body?.string().orEmpty()
                    onLog("Erreur HTTP ${response.code} du Pont Gradle : $errText")
                    return@withContext false to listOf(
                        DiagnosticItem(
                            id = UUID.randomUUID().toString(),
                            severity = DiagnosticSeverity.ERROR,
                            typeLabel = "Pont Gradle",
                            filePath = "${project.rootPath}/${project.moduleName}/build.gradle",
                            fileName = "build.gradle",
                            line = 1,
                            column = 1,
                            message = "Échec du Pont Gradle (HTTP ${response.code})",
                            details = errText.ifBlank { "Le serveur de build a répondu avec le code ${response.code}." }
                        )
                    )
                }

                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.contains("application/vnd.android.package-archive") ||
                    contentType.contains("application/octet-stream")
                ) {
                    outputApkFile.parentFile?.mkdirs()
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(outputApkFile).use { output -> input.copyTo(output) }
                    }
                    onLog("APK reçu depuis le Pont Gradle (${outputApkFile.length()} octets)")
                    return@withContext (outputApkFile.exists() && outputApkFile.length() > 0) to emptyList()
                } else {
                    val jsonStr = response.body?.string().orEmpty()
                    val obj = runCatching { JSONObject(jsonStr) }.getOrNull()
                    val msg = obj?.optString("error") ?: jsonStr.ifBlank { "Réponse invalide du serveur de build." }
                    onLog(msg)
                    return@withContext false to listOf(
                        DiagnosticItem(
                            id = UUID.randomUUID().toString(),
                            severity = DiagnosticSeverity.ERROR,
                            typeLabel = "Compilation Gradle",
                            filePath = "${project.rootPath}/${project.moduleName}/build.gradle",
                            fileName = "build.gradle",
                            line = 1,
                            column = 1,
                            message = msg,
                            details = jsonStr
                        )
                    )
                }
            }
        } catch (e: Exception) {
            onLog("Connexion au Pont Gradle impossible : ${e.message}")
            return@withContext false to listOf(
                DiagnosticItem(
                    id = UUID.randomUUID().toString(),
                    severity = DiagnosticSeverity.ERROR,
                    typeLabel = "Connexion Pont Gradle",
                    filePath = "${project.rootPath}/${project.moduleName}/build.gradle",
                    fileName = "build.gradle",
                    line = 1,
                    column = 1,
                    message = "Pont Gradle inaccessible : ${e.message ?: "Erreur réseau"}",
                    details = "Vérifiez l'URL du Pont Gradle dans Paramètres > Gestion des SDK ($cleanUrl)."
                )
            )
        } finally {
            zipFile.delete()
        }
    }

    private fun zipDirectory(sourceDir: File, destZip: File) {
        ZipOutputStream(FileOutputStream(destZip)).use { zos ->
            sourceDir.walkTopDown()
                .filter { it.isFile && !it.absolutePath.contains("/build/") }
                .forEach { file ->
                    val entryName = file.relativeTo(sourceDir).path.replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
        }
    }
}
