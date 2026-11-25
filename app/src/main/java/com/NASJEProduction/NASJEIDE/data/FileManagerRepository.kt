package com.NASJEProduction.NASJEIDE.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.NASJEProduction.NASJEIDE.model.FileNode
import com.NASJEProduction.NASJEIDE.model.IdeLanguage
import java.io.File

class FileManagerRepository(private val context: Context) {

    fun buildFileTree(projectRootPath: String): FileNode? {
        val rootDir = File(projectRootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) return null
        return buildNode(rootDir, rootDir)
    }

    private fun buildNode(root: File, file: File): FileNode {
        val relative = if (file.absolutePath == root.absolutePath) "" else {
            file.relativeTo(root).path.replace(File.separatorChar, '/')
        }
        val children = if (file.isDirectory) {
            (file.listFiles() ?: emptyArray())
                .filter { !it.name.startsWith(".nasje") && it.name != "build" }
                .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                .map { buildNode(root, it) }
        } else {
            emptyList()
        }
        return FileNode(
            name = file.name,
            relativePath = relative,
            absolutePath = file.absolutePath,
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            children = children,
            language = IdeLanguage.fromFileName(file.name)
        )
    }

    fun readFile(filePath: String): String {
        val file = File(filePath)
        return if (file.exists() && file.isFile) file.readText(Charsets.UTF_8) else ""
    }

    fun writeFile(filePath: String, content: String): Boolean {
        return try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun createFile(parentDirPath: String, fileName: String, initialContent: String = ""): File? {
        val clean = fileName.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return null
        val target = File(parentDirPath, clean)
        if (target.exists()) return null
        target.parentFile?.mkdirs()
        val body = initialContent.ifEmpty { defaultContentForFile(clean) }
        target.writeText(body, Charsets.UTF_8)
        return target
    }

    fun createFolder(parentDirPath: String, folderName: String): File? {
        val clean = folderName.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return null
        val target = File(parentDirPath, clean)
        if (target.exists()) return null
        return if (target.mkdirs()) target else null
    }

    fun renameNode(filePath: String, newName: String): File? {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return null
        val src = File(filePath)
        if (!src.exists()) return null
        val dest = File(src.parentFile, clean)
        if (dest.exists()) return null
        return if (src.renameTo(dest)) dest else null
    }

    fun deleteNode(filePath: String): Boolean {
        val target = File(filePath)
        if (!target.exists()) return false
        return target.deleteRecursively()
    }

    fun copyNodeToDir(sourcePath: String, destDirPath: String): File? {
        val src = File(sourcePath)
        val destDir = File(destDirPath)
        if (!src.exists() || !destDir.isDirectory) return null
        var target = File(destDir, src.name)
        var counter = 1
        while (target.exists()) {
            val base = src.nameWithoutExtension
            val ext = if (src.extension.isNotEmpty()) ".${src.extension}" else ""
            target = File(destDir, "${base}_copie$counter$ext")
            counter++
        }
        if (src.isDirectory) {
            src.copyRecursively(target, overwrite = false)
        } else {
            src.copyTo(target, overwrite = false)
        }
        return target
    }

    fun moveNodeToDir(sourcePath: String, destDirPath: String): File? {
        val src = File(sourcePath)
        val destDir = File(destDirPath)
        if (!src.exists() || !destDir.isDirectory) return null
        if (destDir.absolutePath.startsWith(src.absolutePath)) return null
        val target = File(destDir, src.name)
        if (target.exists()) return null
        return if (src.renameTo(target)) {
            target
        } else {
            val copied = copyNodeToDir(sourcePath, destDirPath)
            if (copied != null) src.deleteRecursively()
            copied
        }
    }

    fun importFromUri(uri: Uri, destDirPath: String): File? {
        return try {
            val fileName = resolveFileName(uri) ?: "imported_${System.currentTimeMillis()}.txt"
            val destDir = File(destDirPath).apply { mkdirs() }
            val destFile = File(destDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destFile
        } catch (_: Exception) {
            null
        }
    }

    fun searchFiles(rootNode: FileNode?, query: String): List<FileNode> {
        if (rootNode == null || query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val results = mutableListOf<FileNode>()
        fun walk(node: FileNode) {
            if (node.relativePath.isNotEmpty() && node.name.lowercase().contains(q)) {
                results.add(node)
            }
            node.children.forEach { walk(it) }
        }
        walk(rootNode)
        return results
    }

    fun findPrimarySourceFile(projectRootPath: String, moduleName: String): File? {
        val root = File(projectRootPath)
        val candidates = listOf(
            File(root, "$moduleName/src/main/java"),
            File(root, "$moduleName/src/main/kotlin"),
            File(root, "$moduleName/src/main/cpp")
        )
        for (dir in candidates) {
            if (dir.exists()) {
                val found = dir.walkTopDown().firstOrNull {
                    it.isFile && (it.name.endsWith(".java") || it.name.endsWith(".kt") || it.name.endsWith(".cpp"))
                }
                if (found != null) return found
            }
        }
        val manifest = File(root, "$moduleName/src/main/AndroidManifest.xml")
        return if (manifest.exists()) manifest else root.walkTopDown().firstOrNull { it.isFile }
    }

    private fun resolveFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun defaultContentForFile(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        return when (IdeLanguage.fromFileName(fileName)) {
            IdeLanguage.JAVA -> "public class $base {\n    \n}\n"
            IdeLanguage.KOTLIN -> "class $base {\n    \n}\n"
            IdeLanguage.XML -> "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"
            IdeLanguage.CPP -> "#include <string>\n\nint computeValue() {\n    return 42;\n}\n"
            IdeLanguage.C -> "#include <stdio.h>\n\nint compute_value(void) {\n    return 42;\n}\n"
            IdeLanguage.JSON -> "{\n  \"name\": \"$base\"\n}\n"
            IdeLanguage.HTML -> "<!DOCTYPE html>\n<html>\n<body>\n</body>\n</html>\n"
            IdeLanguage.CSS -> "/* $fileName */\n"
            IdeLanguage.JAVASCRIPT -> "// $fileName\n"
            else -> ""
        }
    }
}
