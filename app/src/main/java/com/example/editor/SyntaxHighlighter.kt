package com.example.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.model.IdeLanguage
import com.example.model.SyntaxThemePreset

data class SyntaxPalette(
    val keyword: Color,
    val typeOrTag: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotationOrAttr: Color,
    val punctuation: Color,
    val defaultText: Color,
    val searchHighlightBg: Color
) {
    companion object {
        fun forPreset(preset: SyntaxThemePreset, darkTheme: Boolean): SyntaxPalette = when {
            !darkTheme -> SyntaxPalette(
                keyword = Color(0xFF7C3AED),
                typeOrTag = Color(0xFF2563EB),
                string = Color(0xFF059669),
                number = Color(0xFFD97706),
                comment = Color(0xFF64748B),
                annotationOrAttr = Color(0xFF0284C7),
                punctuation = Color(0xFF475569),
                defaultText = Color(0xFF0F172A),
                searchHighlightBg = Color(0x55FACC15)
            )
            preset == SyntaxThemePreset.ELECTRIC_NEON -> SyntaxPalette(
                keyword = Color(0xFFC084FC),
                typeOrTag = Color(0xFF38BDF8),
                string = Color(0xFF34D399),
                number = Color(0xFFF472B6),
                comment = Color(0xFF64748B),
                annotationOrAttr = Color(0xFFFBBF24),
                punctuation = Color(0xFF7DD3FC),
                defaultText = Color(0xFFF8FAFC),
                searchHighlightBg = Color(0x663B82F6)
            )
            preset == SyntaxThemePreset.CLASSIC_MONOKAI -> SyntaxPalette(
                keyword = Color(0xFFF92672),
                typeOrTag = Color(0xFF66D9EF),
                string = Color(0xFFE6DB74),
                number = Color(0xFFAE81FF),
                comment = Color(0xFF75715E),
                annotationOrAttr = Color(0xFFA6E22E),
                punctuation = Color(0xFFF8F8F2),
                defaultText = Color(0xFFF8F8F2),
                searchHighlightBg = Color(0x66F59E0B)
            )
            else -> SyntaxPalette( // NASJE_MIDNIGHT
                keyword = Color(0xFFA78BFA),
                typeOrTag = Color(0xFF60A5FA),
                string = Color(0xFF4ADE80),
                number = Color(0xFFFBBF24),
                comment = Color(0xFF64748B),
                annotationOrAttr = Color(0xFF22D3EE),
                punctuation = Color(0xFF94A3B8),
                defaultText = Color(0xFFE2E8F0),
                searchHighlightBg = Color(0x6638BDF8)
            )
        }
    }
}

class CodeSyntaxVisualTransformation(
    private val language: IdeLanguage,
    private val palette: SyntaxPalette,
    private val searchQuery: String = ""
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = SyntaxHighlighter.highlight(
            code = text.text,
            language = language,
            palette = palette,
            searchQuery = searchQuery
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

object SyntaxHighlighter {

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

    fun highlight(
        code: String,
        language: IdeLanguage,
        palette: SyntaxPalette,
        searchQuery: String = ""
    ): AnnotatedString = buildAnnotatedString {
        append(code)
        addStyle(SpanStyle(color = palette.defaultText), 0, code.length)

        when (language) {
            IdeLanguage.XML, IdeLanguage.HTML -> highlightMarkup(code, palette)
            IdeLanguage.JSON -> highlightJson(code, palette)
            IdeLanguage.CSS -> highlightCss(code, palette)
            IdeLanguage.PROPERTIES -> highlightProperties(code, palette)
            IdeLanguage.MARKDOWN -> highlightMarkdown(code, palette)
            IdeLanguage.JAVA -> highlightCodeLike(code, javaKeywords, palette, allowHashPreprocessor = false)
            IdeLanguage.KOTLIN -> highlightCodeLike(code, kotlinKeywords, palette, allowHashPreprocessor = false)
            IdeLanguage.CPP, IdeLanguage.C -> highlightCodeLike(code, cppKeywords, palette, allowHashPreprocessor = true)
            IdeLanguage.JAVASCRIPT -> highlightCodeLike(code, jsKeywords, palette, allowHashPreprocessor = false)
            IdeLanguage.GRADLE, IdeLanguage.KOTLIN_DSL -> highlightCodeLike(code, gradleKeywords + kotlinKeywords, palette, allowHashPreprocessor = false)
            IdeLanguage.TEXT -> Unit
        }

        if (searchQuery.isNotBlank()) {
            var idx = code.indexOf(searchQuery, ignoreCase = true)
            while (idx >= 0) {
                addStyle(
                    SpanStyle(background = palette.searchHighlightBg, fontWeight = FontWeight.Bold),
                    idx,
                    idx + searchQuery.length
                )
                idx = code.indexOf(searchQuery, idx + searchQuery.length, ignoreCase = true)
            }
        }
    }

    private fun AnnotatedString.Builder.highlightCodeLike(
        code: String,
        keywords: Set<String>,
        palette: SyntaxPalette,
        allowHashPreprocessor: Boolean
    ) {
        val len = code.length
        var i = 0
        while (i < len) {
            val ch = code[i]
            // Line comment //
            if (ch == '/' && i + 1 < len && code[i + 1] == '/') {
                val end = code.indexOf('\n', i).let { if (it == -1) len else it }
                addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), i, end)
                i = end
                continue
            }
            // Block comment /* ... */
            if (ch == '/' && i + 1 < len && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), i, end)
                i = end
                continue
            }
            // Preprocessor #include / #define or CMake # comment
            if (ch == '#') {
                if (allowHashPreprocessor) {
                    val end = code.indexOf('\n', i).let { if (it == -1) len else it }
                    val line = code.substring(i, end)
                    if (line.startsWith("#include") || line.startsWith("#define") || line.startsWith("#if") || line.startsWith("#end") || line.startsWith("#pragma")) {
                        addStyle(SpanStyle(color = palette.annotationOrAttr, fontWeight = FontWeight.Medium), i, end)
                    } else {
                        addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), i, end)
                    }
                    i = end
                    continue
                }
            }
            // Strings "..." or '...'
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
                addStyle(SpanStyle(color = palette.string), i, j)
                i = j
                continue
            }
            // Annotations @Override
            if (ch == '@') {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                addStyle(SpanStyle(color = palette.annotationOrAttr), i, j)
                i = j
                continue
            }
            // Numbers
            if (ch.isDigit()) {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                addStyle(SpanStyle(color = palette.number), i, j)
                i = j
                continue
            }
            // Identifiers & Keywords
            if (ch.isLetter() || ch == '_') {
                var j = i + 1
                while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                when {
                    word in keywords -> {
                        addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), i, j)
                    }
                    word.first().isUpperCase() -> {
                        addStyle(SpanStyle(color = palette.typeOrTag), i, j)
                    }
                }
                i = j
                continue
            }
            if (ch in "{}()[];.,:+-*/=<>!&|") {
                addStyle(SpanStyle(color = palette.punctuation), i, i + 1)
            }
            i++
        }
    }

    private fun AnnotatedString.Builder.highlightMarkup(code: String, palette: SyntaxPalette) {
        val commentRegex = Regex("""<!--[\s\S]*?-->""")
        val tagNameRegex = Regex("""</?\s*([a-zA-Z0-9_.:-]+)""")
        val attrRegex = Regex("""\s([a-zA-Z0-9_.:-]+)\s*=""")
        val stringRegex = Regex(""""[^"\n]*"""")

        tagNameRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            addStyle(SpanStyle(color = palette.typeOrTag, fontWeight = FontWeight.SemiBold), g.range.first, g.range.last + 1)
        }
        attrRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            addStyle(SpanStyle(color = palette.annotationOrAttr), g.range.first, g.range.last + 1)
        }
        stringRegex.findAll(code).forEach { m ->
            addStyle(SpanStyle(color = palette.string), m.range.first, m.range.last + 1)
        }
        commentRegex.findAll(code).forEach { m ->
            addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightJson(code: String, palette: SyntaxPalette) {
        val keyRegex = Regex(""""([^"\n]+)"\s*:""")
        val stringValRegex = Regex(""":\s*("[^"\n]*")""")
        val numberBoolRegex = Regex("""\b(true|false|null|-?\d+(\.\d+)?)\b""")

        stringValRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            addStyle(SpanStyle(color = palette.string), g.range.first, g.range.last + 1)
        }
        keyRegex.findAll(code).forEach { m ->
            addStyle(SpanStyle(color = palette.annotationOrAttr), m.range.first, m.range.last)
        }
        numberBoolRegex.findAll(code).forEach { m ->
            addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightCss(code: String, palette: SyntaxPalette) {
        val commentRegex = Regex("""/\*[\s\S]*?\*/""")
        val selectorRegex = Regex("""([.#]?[a-zA-Z0-9_-]+)\s*\{""")
        val propRegex = Regex("""([a-z-]+)\s*:""")
        val colorOrNumRegex = Regex("""#[0-9a-fA-F]{3,8}|\b\d+(px|dp|sp|em|rem|%|vh|vw)?\b""")

        selectorRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            addStyle(SpanStyle(color = palette.typeOrTag, fontWeight = FontWeight.SemiBold), g.range.first, g.range.last + 1)
        }
        propRegex.findAll(code).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            addStyle(SpanStyle(color = palette.annotationOrAttr), g.range.first, g.range.last + 1)
        }
        colorOrNumRegex.findAll(code).forEach { m ->
            addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
        }
        commentRegex.findAll(code).forEach { m ->
            addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightProperties(code: String, palette: SyntaxPalette) {
        var offset = 0
        code.lines().forEach { line ->
            val end = offset + line.length
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") || trimmed.startsWith("!") -> {
                    addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), offset, end)
                }
                line.contains('=') -> {
                    val eq = line.indexOf('=')
                    addStyle(SpanStyle(color = palette.annotationOrAttr), offset, offset + eq)
                    addStyle(SpanStyle(color = palette.string), offset + eq + 1, end)
                }
            }
            offset = end + 1
        }
    }

    private fun AnnotatedString.Builder.highlightMarkdown(code: String, palette: SyntaxPalette) {
        var offset = 0
        code.lines().forEach { line ->
            val end = offset + line.length
            if (line.trimStart().startsWith("#")) {
                addStyle(SpanStyle(color = palette.typeOrTag, fontWeight = FontWeight.Bold), offset, end)
            }
            offset = end + 1
        }
    }
}
