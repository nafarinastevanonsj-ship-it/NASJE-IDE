package com.NASJEProduction.NASJEIDE.compiler

import com.NASJEProduction.NASJEIDE.model.DiagnosticItem
import com.NASJEProduction.NASJEIDE.model.DiagnosticSeverity
import com.NASJEProduction.NASJEIDE.model.ProjectInfo
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure Java HttpURLConnection client connecting NASJE IDE to an optional external Gradle/NDK/Kotlin
 * Build Bridge over HTTP/HTTPS (zero OkHttp / kotlinx.coroutines dependencies).
 */
class RemoteBuildClient {

    fun buildViaRemoteBridge(
        bridgeUrl: String,
        project: ProjectInfo,
        outputApkFile: File,
        onLog: (String) -> Unit
    ): Pair<Boolean, List<DiagnosticItem>> {
        val cleanUrl = bridgeUrl.trim().trimEnd('/')
        if (cleanUrl.isEmpty()) {
            return false to emptyList()
        }
        val zipFile = File.createTempFile("nasje_src_${project.id}", ".zip")
        var conn: HttpURLConnection? = null
        try {
            onLog("Préparation de l'archive projet pour le Pont Gradle ($cleanUrl)...")
            zipDirectory(File(project.rootPath), zipFile)

            val boundary = "----NasjeBoundary${System.currentTimeMillis()}"
            val url = URL("$cleanUrl/api/build")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            onLog("Envoi vers le serveur de compilation Gradle/Android SDK...")
            DataOutputStream(conn.outputStream).use { out ->
                writeFormField(out, boundary, "projectId", project.id)
                writeFormField(out, boundary, "moduleName", project.moduleName)
                writeFileField(out, boundary, "projectZip", "${project.id}.zip", zipFile)
                out.writeBytes("--$boundary--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            val contentType = conn.contentType.orEmpty()
            if (code !in 200..299) {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                onLog("Erreur HTTP $code du Pont Gradle : $errText")
                return false to listOf(
                    DiagnosticItem(
                        id = UUID.randomUUID().toString(),
                        severity = DiagnosticSeverity.ERROR,
                        typeLabel = "Pont Gradle",
                        filePath = "${project.rootPath}/${project.moduleName}/build.gradle",
                        fileName = "build.gradle",
                        line = 1,
                        column = 1,
                        message = "Échec du Pont Gradle (HTTP $code)",
                        details = errText.ifBlank { "Le serveur de build a répondu avec le code $code." }
                    )
                )
            }

            if (contentType.contains("application/vnd.android.package-archive") ||
                contentType.contains("application/octet-stream")
            ) {
                outputApkFile.parentFile?.mkdirs()
                conn.inputStream.use { input ->
                    FileOutputStream(outputApkFile).use { output -> input.copyTo(output) }
                }
                onLog("APK reçu depuis le Pont Gradle (${outputApkFile.length()} octets)")
                return (outputApkFile.exists() && outputApkFile.length() > 0) to emptyList()
            } else {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = runCatching { JSONObject(jsonStr) }.getOrNull()
                val msg = obj?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: jsonStr.ifBlank { "Réponse invalide du serveur de build." }
                onLog(msg)
                return false to listOf(
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
        } catch (e: Exception) {
            onLog("Connexion au Pont Gradle impossible : ${e.message}")
            return false to listOf(
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
            conn?.disconnect()
            zipFile.delete()
        }
    }

    private fun writeFormField(out: DataOutputStream, boundary: String, name: String, value: String) {
        out.writeBytes("--$boundary\r\n")
        out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        out.write(value.toByteArray(Charsets.UTF_8))
        out.writeBytes("\r\n")
    }

    private fun writeFileField(
        out: DataOutputStream,
        boundary: String,
        fieldName: String,
        fileName: String,
        file: File
    ) {
        out.writeBytes("--$boundary\r\n")
        out.writeBytes("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n")
        out.writeBytes("Content-Type: application/zip\r\n\r\n")
        file.inputStream().use { it.copyTo(out) }
        out.writeBytes("\r\n")
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
