package com.NASJEProduction.NASJEIDE.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Pure Android SDK ContentProvider replacing androidx.core.content.FileProvider
 * for sharing and installing compiled APKs stored under context.filesDir/builds/.
 */
class NasjeFileProvider : ContentProvider() {

    companion object {
        fun getUriForFile(context: Context, file: File): Uri {
            val filesRoot = context.filesDir.canonicalFile
            val canonicalTarget = file.canonicalFile
            val relPath = canonicalTarget.relativeTo(filesRoot).path.replace(File.separatorChar, '/')
            return Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.fileprovider")
                .path(relPath)
                .build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = resolveSafeFile(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols, 1)
        val row = cols.map { col ->
            when (col) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }.toTypedArray()
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String {
        val name = uri.lastPathSegment?.lowercase() ?: ""
        return when {
            name.endsWith(".apk") -> "application/vnd.android.package-archive"
            name.endsWith(".zip") -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = resolveSafeFile(uri) ?: throw FileNotFoundException("Fichier introuvable: $uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun resolveSafeFile(uri: Uri): File? {
        val ctx = context ?: return null
        val rel = uri.path?.trimStart('/') ?: return null
        val root = ctx.filesDir.canonicalFile
        val target = File(root, rel).canonicalFile
        if (!target.path.startsWith(root.path + File.separator) || !target.exists() || !target.isFile) {
            return null
        }
        return target
    }
}
