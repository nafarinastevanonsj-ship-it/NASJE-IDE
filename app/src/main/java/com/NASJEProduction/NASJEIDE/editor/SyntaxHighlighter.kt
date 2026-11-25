package com.NASJEProduction.NASJEIDE.editor

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.NASJEProduction.NASJEIDE.model.IdeLanguage
import com.NASJEProduction.NASJEIDE.model.SyntaxThemePreset

data class SyntaxPalette(
    val keyword: Int,
    val typeOrTag: Int,
    val string: Int,
    val number: Int,
    val comment: Int,
    val annotationOrAttr: Int,
    val punctuation: Int,
    val defaultText: Int,
    val searchHighlightBg: Int
) {
    companion object {
        fun forPreset(preset: SyntaxThemePreset, darkTheme: Boolean): SyntaxPalette = when {
            !darkTheme -> SyntaxPalette(
                keyword = 0xFF7C3AED.toInt(),
                typeOrTag = 0xFF2563EB.toInt(),
                string = 0xFF059669.toInt(),
                number = 0xFFD97706.toInt(),
                comment = 0xFF64748B.toInt(),
                annotationOrAttr = 0xFF0284C7.toInt(),
                punctuation = 0xFF475569.toInt(),
                defaultText = 0xFF0F172A.toInt(),
                searchHighlightBg = 0x55FACC15.toInt()
            )
            preset == SyntaxThemePreset.ELECTRIC_NEON -> SyntaxPalette(
                keyword = 0xFFC084FC.toInt(),
                typeOrTag = 0xFF38BDF8.toInt(),
                string = 0xFF34D399.toInt(),
                number = 0xFFF472B6.toInt(),
                comment = 0xFF64748B.toInt(),
                annotationOrAttr = 0xFFFBBF24.toInt(),
                punctuation = 0xFF7DD3FC.toInt(),
                defaultText = 0xFFF8FAFC.toInt(),
                searchHighlightBg = 0x663B82F6.toInt()
            )
            preset == SyntaxThemePreset.CLASSIC_MONOKAI -> SyntaxPalette(
                keyword = 0xFFF92672.toInt(),
                typeOrTag = 0xFF66D9EF.toInt(),
                string = 0xFFE6DB74.toInt(),
                number = 0xFFAE81FF.toInt(),
                comment = 0xFF75715E.toInt(),
                annotationOrAttr = 0xFFA6E22E.toInt(),
                punctuation = 0xFFF8F8F2.toInt(),
                defaultText = 0xFFF8F8F2.toInt(),
                searchHighlightBg = 0x66F59E0B.toInt()
            )
            else -> SyntaxPalette(
                keyword = 0xFFA78BFA.toInt(),
                typeOrTag = 0xFF60A5FA.toInt(),
                string = 0xFF4ADE80.toInt(),
                number = 0xFFFBBF24.toInt(),
                comment = 0xFF64748B.toInt(),
                annotationOrAttr = 0xFF22D3EE.toInt(),
                punctuation = 0xFF94A3B8.toInt(),
                defaultText = 0xFFE2E8F0.toInt(),
                searchHighlightBg = 0x6638BDF8.toInt()
            )
        }
    }
}

/**
 * Pure Android SDK Spannable syntax highlighter supporting 14 languages + live search highlights.
 */
object SyntaxHighlighter {

    private class NasjeSyntaxSpan(val span: Any)

    private val javaKeywords = setOf(
        "package", "import", "public", "private", "protected", "class", "interface", "enum",
        "extends", "implements", "static", "final", "void", "return", "if", "else", "for",
        "while", "do", "switch", "case", "default", "break", "continue", "new", "this",
        "super", "try", "catch", "finally", "throw", "throws", "boolean", "int", "long",
        "double", "float", "char", "byte", "short", "true", "false", "null", "abstract"
    )

    private val kotlinKeywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var", "return",
        "if", "else", "when", "for", "while", "do", "in", "is", "as", "override", "open",
        "private", "public", "protected", "internal", "companion", "data", "sealed", "enum",
        "suspend", "true", "false", "null", "this", "super", "try", "catch", "finally", "throw"
    )

    private val cppKeywords = setOf(
        "include", "define", "ifdef", "ifndef", "endif", "extern", "namespace", "using",
        "class", "struct", "enum", "public", "private", "protected", "virtual", "override",
        "const", "static", "inline", "void", "int", "long", "float", "double", "char", "bool",
        "auto", "return", "if", "else", "for", "while", "switch", "case", "break", "nullptr",
        "true", "false", "new", "delete", "template", "typename", "sizeof", "typedef"
    )

    private val jsKeywords = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "switch",
        "case", "break", "class", "extends", "import", "export", "from", "default", "async",
        "await", "try", "catch", "throw", "new", "this", "true", "false", "null", "undefined"
    )

    private val gradleKeywords = setOf(
        "plugins", "android", "defaultConfig", "buildTypes", "release", "debug", "dependencies",
        "implementation", "api", "testImplementation", "androidTestImplementation", "compileOptions",
        "kotlinOptions", "externalNativeBuild", "cmake", "include", "pluginManagement",
        "dependencyResolutionManagement", "repositories", "google", "mavenCentral", "id", "val",
        "var", "true", "false", "apply", "namespace", "compileSdk", "minSdk", "targetSdk",
        "applicationId", "versionCode", "versionName", "minifyEnabled", "isMinifyEnabled"
    )

    fun applyHighlighting(
        editable: Editable,
        language: IdeLanguage,
        palette: SyntaxPalette,
        searchQuery: String = ""
    ) {
        // Clear previously applied syntax spans cleanly without touching IME composition spans
        val oldFg = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        oldFg.forEach { editable.removeSpan(it) }
        val oldBg = editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)
        oldBg.forEach { editable.removeSpan(it) }
        val oldStyle = editable.getSpans(0, editable.length, StyleSpan::class.java)
        oldStyle.forEach { editable.removeSpan(it) }

        val code = editable.toString()
        if (code.isEmpty()) return

        when (language) {
            IdeLanguage.XML, IdeLanguage.HTML -> highlightMarkup(editable, code, palette)
            IdeLanguage.JSON -> highlightJson(editable, code, palette)
            IdeLanguage.CSS -> highlightCss(editable, code, palette)
            IdeLanguage.PROPERTIES -> highlightProperties(editable, code, palette)
            IdeLanguage.MARKDOWN -> highlightMarkdown(editable, code, palette)
            IdeLanguage.JAVA -> highlightCodeLike(editable, code, javaKeywords, palette, allowHashPreprocessor = false)
            IdeLanguage.KOTLIN -> highlightCodeLike(editable, code, kotlinKeywords, palette, allowHashPreprocessor = false)
            IdeLanguage.CPP, IdeLanguage.C -> highlightCodeLike(editable, code, cppKeywords, palette, allowHashPreprocessor = true)
            IdeLanguage.JAVASCRIPT -> highlightCodeLike(editable, code, jsKeywords, palette, allowHashPreprocessor = false)
            IdeLanguage.GRADLE, IdeLanguage.KOTLIN_DSL -> highlightCodeLike(editable, code, gradleKeywords + kotlinKeywords, palette, allowHashPreprocessor = false)
            IdeLanguage.TEXT -> Unit
        }

        if (searchQuery.isNotBlank()) {
            var idx = code.indexOf(searchQuery, ignoreCase = true)
            while (idx >= 0) {
                val end = (idx + searchQuery.length).coerceAtMost(code.length)
                editable.setSpan(
                    BackgroundColorSpan(palette.searchHighlightBg),
                    idx,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                editable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    idx,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                idx = code.indexOf(searchQuery, end, ignoreCase = true)
            }
        }
    }

    private fun setColor(e: Spannable, start: Int, end: Int, color: Int, bold: Boolean = false, italic: Boolean = false) {
        if (start >= end || start < 0 || end > e.length) return
        e.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold && italic) {
            e.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (bold) {
            e.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (italic) {
            e.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun highlightCodeLike(
        e: Spannable,
        code: String,
        keywords: Set<String>,
        palette: SyntaxPalette,
        allowHashPreprocessor: Boolean
    ) {
        val len = code.length
        var i = 0
        while (i < len) {
            val ch = code[i]
            if (ch == '/' && i + 1 < len && code[i + 1] == '/') {
                val end = code.indexOf('\n', i).let { if (it == -1) len else it }
                setColor(e, i, end, palette.comment, italic = true)
                i = end
                continue
            }
            if (ch == '/' && i + 1 < len && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                setColor(e, i, end, palette.comment, italic = true)
                i = end
                continue
            }
            if (ch == '#' && allowHashPreprocessor) {
                val end = code.indexOf('\n', i).let { if (it == -1) len else it }
                val line = code.substring(i, end)
                if (line.startsWith("#include") || line.startsWith("#define") || line.startsWith("#if") || line.startsWith("#end") || line.startsWith("#pragma")) {
                    setColor(e, i, end, palette.annotationOrAttr, bold = true)
                } else {
                    setColor(e, i, end, palette.comment, italic = true)
                }
                i = end
                continue
            }
            if (ch == '"' || ch == '\'') {
                val quote = ch
                var j = i + 1
                while (j < len) {
                    if (code[j] == '\\' && j + 1 < len) {
                        j += 2
                    } else if (code[j] == quote || code[j] == '\n') {
                        if (code[j] == quote) j++
                        break
                    } else {
                        j++
                    }
                }
                setColor(e, i, j, palette.string)
                i = j
                continue
            }
            if (ch == '@') {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                setColor(e, i, j, palette.annotationOrAttr)
                i = j
                continue
            }
            if (ch.isDigit()) {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                setColor(e, i, j, palette.number)
                i = j
                continue
            }
            if (ch.isLetter() || ch == '_') {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                when {
                    word in keywords -> setColor(e, i, j, palette.keyword, bold = true)
                    word.first().isUpperCase() -> setColor(e, i, j, palette.typeOrTag)
                }
                i = j
                continue
            }
            if (ch in "{}()[];.,:+-*/=<>!&|") {
                setColor(e, i, i + 1, palette.punctuation)
            }
            i++
        }
    }

    private fun highlightMarkup(e: Spannable, code: String, palette: SyntaxPalette) {
        val commentRegex = Regex("""<!--[\s\S]*?-->""")
        val tagNameRegex = Regex("""</?\s*([a-zA-Z0-9_.:-]+)""")
        val attrRegex = Regex("""\s([a-zA-Z0-9_.:-]+)\s*=""")
        val stringRegex = Regex(""""[^"\n]*"""")

        tagNameRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            setColor(e, g.range.first, g.range.last + 1, palette.typeOrTag, bold = true)
        }
        attrRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            setColor(e, g.range.first, g.range.last + 1, palette.annotationOrAttr)
        }
        stringRegex.findAll(code).forEach { m ->
            setColor(e, m.range.first, m.range.last + 1, palette.string)
        }
        commentRegex.findAll(code).forEach { m ->
            setColor(e, m.range.first, m.range.last + 1, palette.comment, italic = true)
        }
    }

    private fun highlightJson(e: Spannable, code: String, palette: SyntaxPalette) {
        val keyRegex = Regex(""""([^"\n]+)"\s*:""")
        val stringValRegex = Regex(""":\s*("[^"\n]*")""")
        val numberBoolRegex = Regex("""\b(true|false|null|-?\d+(\.\d+)?)\b""")

        stringValRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            setColor(e, g.range.first, g.range.last + 1, palette.string)
        }
        keyRegex.findAll(code).forEach { m ->
            setColor(e, m.range.first, m.range.last, palette.annotationOrAttr)
        }
        numberBoolRegex.findAll(code).forEach { m ->
            setColor(e, m.range.first, m.range.last + 1, palette.number)
        }
    }

    private fun highlightCss(e: Spannable, code: String, palette: SyntaxPalette) {
        val commentRegex = Regex("""/\*[\s\S]*?\*/""")
        val selectorRegex = Regex("""([.#]?[a-zA-Z0-9_-]+)\s*\{""")
        val propRegex = Regex("""([a-z-]+)\s*:""")
        val colorOrNumRegex = Regex("""#[0-9a-fA-F]{3,8}|\b\d+(px|dp|sp|em|rem|%|vh|vw)?\b""")

        selectorRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            setColor(e, g.range.first, g.range.last + 1, palette.typeOrTag, bold = true)
        }
        propRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            setColor(e, g.range.first, g.range.last + 1, palette.annotationOrAttr)
        }
        colorOrNumRegex.findAll(code).forEach { m ->
            setColor(e, m.range.first, m.range.last + 1, palette.number)
        }
        commentRegex.findAll(code).forEach { m ->
            setColor(e, m.range.first, m.range.last + 1, palette.comment, italic = true)
        }
    }

    private fun highlightProperties(e: Spannable, code: String, palette: SyntaxPalette) {
        var offset = 0
        code.lines().forEach { line ->
            val end = offset + line.length
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") || trimmed.startsWith("!") -> {
                    setColor(e, offset, end, palette.comment, italic = true)
                }
                line.contains('=') -> {
                    val eq = line.indexOf('=')
                    setColor(e, offset, offset + eq, palette.annotationOrAttr)
                    setColor(e, offset + eq + 1, end, palette.string)
                }
            }
            offset = end + 1
        }
    }

    private fun highlightMarkdown(e: Spannable, code: String, palette: SyntaxPalette) {
        var offset = 0
        code.lines().forEach { line ->
            val end = offset + line.length
            if (line.trimStart().startsWith("#")) {
                setColor(e, offset, end, palette.typeOrTag, bold = true)
            }
            offset = end + 1
        }
    }
}
