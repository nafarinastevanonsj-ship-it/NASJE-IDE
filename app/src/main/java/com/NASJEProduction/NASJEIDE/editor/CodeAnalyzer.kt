package com.NASJEProduction.NASJEIDE.editor

import com.NASJEProduction.NASJEIDE.model.DiagnosticItem
import com.NASJEProduction.NASJEIDE.model.DiagnosticSeverity
import com.NASJEProduction.NASJEIDE.model.IdeLanguage
import java.io.File
import java.util.UUID

object CodeAnalyzer {

    fun analyzeProject(projectRootPath: String): List<DiagnosticItem> {
        val root = File(projectRootPath)
        if (!root.exists()) return emptyList()
        val diagnostics = mutableListOf<DiagnosticItem>()
        root.walkTopDown()
            .filter { it.isFile && !it.absolutePath.contains("/build/") }
            .forEach { file ->
                val lang = IdeLanguage.fromFileName(file.name)
                diagnostics.addAll(analyzeFile(file.absolutePath, file.name, file.readText(), lang))
            }
        return diagnostics
    }

    fun analyzeFile(
        filePath: String,
        fileName: String,
        content: String,
        language: IdeLanguage
    ): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()
        when (language) {
            IdeLanguage.JAVA -> analyzeJava(filePath, fileName, content, items)
            IdeLanguage.KOTLIN -> analyzeKotlin(filePath, fileName, content, items)
            IdeLanguage.CPP, IdeLanguage.C -> {
                if (fileName.equals("CMakeLists.txt", ignoreCase = true)) {
                    analyzeCMake(filePath, fileName, content, items)
                } else {
                    analyzeCpp(filePath, fileName, content, items)
                }
            }
            IdeLanguage.XML -> analyzeXml(filePath, fileName, content, items)
            IdeLanguage.GRADLE, IdeLanguage.KOTLIN_DSL -> analyzeGradle(filePath, fileName, content, items)
            IdeLanguage.JSON -> analyzeJson(filePath, fileName, content, items)
            IdeLanguage.JAVASCRIPT -> checkBalancedDelimiters(filePath, fileName, content, "JavaScript", items)
            else -> Unit
        }
        return items
    }

    private fun analyzeJava(
        filePath: String,
        fileName: String,
        content: String,
        out: MutableList<DiagnosticItem>
    ) {
        checkBalancedDelimiters(filePath, fileName, content, "Java", out)
        val lines = content.lines()
        val hasPackage = lines.any { it.trim().startsWith("package ") }
        if (!hasPackage && content.isNotBlank()) {
            out.add(
                diag(
                    DiagnosticSeverity.WARNING,
                    "Structure Java",
                    filePath,
                    fileName,
                    1,
                    1,
                    "Déclaration 'package' absente",
                    "Les classes Android doivent déclarer leur package en première ligne (ex: package com.exemple.app;)."
                )
            )
        }

        val hasTypeDecl = Regex("""\b(class|interface|enum)\s+[A-Za-z_][A-Za-z0-9_]*""").containsMatchIn(content)
        if (!hasTypeDecl && content.isNotBlank()) {
            out.add(
                diag(
                    DiagnosticSeverity.ERROR,
                    "Classe introuvable",
                    filePath,
                    fileName,
                    1,
                    1,
                    "Aucune classe ou interface déclarée dans $fileName",
                    "Chaque fichier source .java doit contenir au moins une déclaration class, interface ou enum."
                )
            )
        }

        var inBlockComment = false
        lines.forEachIndexed { idx, rawLine ->
            val lineNum = idx + 1
            var line = rawLine.trim()
            if (line.contains("/*")) inBlockComment = true
            if (inBlockComment) {
                if (line.contains("*/")) inBlockComment = false
                return@forEachIndexed
            }
            val commentIdx = line.indexOf("//")
            if (commentIdx >= 0) line = line.substring(0, commentIdx).trim()
            if (line.isEmpty() || line.startsWith("@")) return@forEachIndexed

            val endsProperly = line.endsWith(";") || line.endsWith("{") || line.endsWith("}") ||
                line.endsWith(",") || line.endsWith("(") || line.endsWith(":") ||
                line.startsWith("if") || line.startsWith("else") || line.startsWith("for") ||
                line.startsWith("while") || line.startsWith("try") || line.startsWith("catch") ||
                line.startsWith("public ") || line.startsWith("private ") || line.startsWith("protected ")

            val looksLikeStatement = line.startsWith("package ") ||
                line.startsWith("import ") ||
                line.startsWith("return ") ||
                line == "return" ||
                line.startsWith("break") ||
                line.startsWith("continue") ||
                line.startsWith("throw ") ||
                (line.contains("=") && !line.contains("==")) ||
                (line.endsWith(")") && !line.startsWith("if") && !line.startsWith("for") && !line.startsWith("while") && !line.startsWith("switch") && !line.startsWith("catch") && !line.contains("class "))

            if (looksLikeStatement && !endsProperly) {
                out.add(
                    diag(
                        DiagnosticSeverity.ERROR,
                        "Point-virgule manquant",
                        filePath,
                        fileName,
                        lineNum,
                        rawLine.length + 1,
                        "';' attendu en fin d'instruction",
                        "Ligne $lineNum: \"$line\" doit se terminer par un point-virgule ';'."
                    )
                )
            }
        }
    }

    private fun analyzeKotlin(
        filePath: String,
        fileName: String,
        content: String,
        out: MutableList<DiagnosticItem>
    ) {
        checkBalancedDelimiters(filePath, fileName, content, "Kotlin", out)
        val lines = content.lines()
        lines.forEachIndexed { idx, raw ->
            val trimmed = raw.trim()
            if (trimmed.startsWith("fun ") && !trimmed.contains("(")) {
                out.add(
                    diag(
                        DiagnosticSeverity.ERROR,
                        "Syntaxe Kotlin",
                        filePath,
                        fileName,
                        idx + 1,
                        1,
                        "Parenthèses '()' manquantes dans la déclaration fun",
                        "Une fonction Kotlin doit déclarer ses paramètres entre parenthèses, ex: fun maFonction()."
                    )
                )
            }
            if ((trimmed.startsWith("val ") || trimmed.startsWith("var ")) &&
                !trimmed.contains("=") && !trimmed.contains(":")
            ) {
                out.add(
                    diag(
                        DiagnosticSeverity.ERROR,
                        "Propriété Kotlin invalide",
                        filePath,
                        fileName,
                        idx + 1,
                        1,
                        "Type ou initialisation '=' manquant pour val/var",
                        "Déclarez un type explicite (: Type) ou assignez une valeur (= ...)."
                    )
                )
            }
        }
    }

    private fun analyzeCpp(
        filePath: String,
        fileName: String,
        content: String,
        out: MutableList<DiagnosticItem>
    ) {
        checkBalancedDelimiters(filePath, fileName, content, "C/C++", out)
        val lines = content.lines()
        lines.forEachIndexed { idx, raw ->
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("/*") || line.endsWith("*/")) {
                return@forEachIndexed
            }
            if (line.startsWith("return ") && !line.endsWith(";")) {
                out.add(
                    diag(
                        DiagnosticSeverity.ERROR,
                        "Erreur de compilation C++",
                        filePath,
                        fileName,
                        idx + 1,
                        raw.length + 1,
                        "Point-virgule ';' manquant après return",
                        "Instruction C++ incomplète à la ligne ${idx + 1}: \"$line\"."
                    )
                )
            }
        }
    }

    private fun analyzeCMake(
        filePath: String,
        fileName: String,
        content: String,
        out: MutableList<DiagnosticItem>
    ) {
        if (!content.contains("cmake_minimum_required")) {
            out.add(
                diag(
                    DiagnosticSeverity.WARNING,
                    "CMake",
                    filePath,
                    fileName,
                    1,
                    1,
                    "cmake_minimum_required manquant",
                    "Spécifiez cmake_minimum_required(VERSION 3.22.1) pour le build NDK."
                )
            )
        }
        if (!content.contains("add_library")) {
            out.add(
                diag(
                    DiagnosticSeverity.ERROR,
                    "CMake",
                    filePath,
                    fileName,
                    1,
                    1,
                    "Aucune cible add_library définie dans CMakeLists.txt",
                    "Ajoutez add_library(<nom> SHARED <sources...>) pour compiler votre bibliothèque native."
                )
            )
        }
    }

    private fun analyzeXml(
        filePath: String,
        fileName: String,
        content: String,
        out: MutableList<DiagnosticItem>
    ) {
        if (content.isBlank()) return
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(content.reader())
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                event = parser.next()
            }
        } catch (e: org.xmlpull.v1.XmlPullParserException) {
            val line = e.lineNumber.coerceAtLeast(1)
            val col = e.columnNumber.coerceAtLeast(1)
            out.add(
                diag(
                    DiagnosticSeverity.ERROR,
                    "Erreur XML",
                    filePath,
                    fileName,
                    line,
                    col,
                    e.message?.substringBefore("\n") ?: "Balise XML mal formée",
                    "L'analyseur XML PullParser a détecté une erreur de syntaxe ligne $line, colonne $col: ${e.message}"
                )
            )
        } catch (_: Exception) {
        }

        if (fileName == "AndroidManifest.xml" && !content.contains("<application")) {
            out.add(
                diag(
                    DiagnosticSeverity.ERROR,
                    "Manifest Android",
                    filePath,
                    fileName,
                    1,
                    1,
                    "Balise <application> manquante dans AndroidManifest.xml",
                    "Un AndroidManifest.xml valide doit contenir un élément <application>."
                )
            )
        }
    }

    private fun analyzeGradle(
        filePath: String,
        fileName: String,
        content: String,
        out: MutableList<DiagnosticItem>
    ) {
        checkBalancedDelimiters(filePath, fileName, content, "Gradle", out)
        val minSdk = Regex("""minSdk\s*=?\s*(\d+)""").find(content)?.groupValues?.get(1)?.toIntOrNull()
        val targetSdk = Regex("""targetSdk\s*=?\s*(\d+)""").find(content)?.groupValues?.get(1)?.toIntOrNull()
        if (minSdk != null && targetSdk != null && minSdk > targetSdk) {
            val lineNum = content.lines().indexOfFirst { it.contains("minSdk") }.let { if (it >= 0) it + 1 else 1 }
            out.add(
                diag(
                    DiagnosticSeverity.ERROR,
                    "Erreur Gradle",
                    filePath,
                    fileName,
                    lineNum,
                    1,
                    "minSdk ($minSdk) supérieur à targetSdk ($targetSdk)",
                    "La version minSdk doit être inférieure ou égale à targetSdk."
                )
            )
        }
    }

    private fun analyzeJson(
        filePath: String,
        fileName: String,
        content: String,
        out: MutableList<DiagnosticItem>
    ) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        checkBalancedDelimiters(filePath, fileName, content, "JSON", out)
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            out.add(
                diag(
                    DiagnosticSeverity.ERROR,
                    "Erreur JSON",
                    filePath,
                    fileName,
                    1,
                    1,
                    "Un document JSON doit commencer par '{' ou '['",
                    "Caractère racine inattendu dans $fileName."
                )
            )
        }
    }

    private fun checkBalancedDelimiters(
        filePath: String,
        fileName: String,
        content: String,
        langLabel: String,
        out: MutableList<DiagnosticItem>
    ) {
        data class OpenToken(val ch: Char, val line: Int, val col: Int)
        val stack = ArrayDeque<OpenToken>()
        var line = 1
        var col = 1
        var i = 0
        var inString: Char? = null
        var inLineComment = false
        var inBlockComment = false

        while (i < content.length) {
            val c = content[i]
            val next = if (i + 1 < content.length) content[i + 1] else '\u0000'

            if (c == '\n') {
                if (inString != null) {
                    out.add(
                        diag(
                            DiagnosticSeverity.ERROR,
                            "Chaîne non fermée ($langLabel)",
                            filePath,
                            fileName,
                            line,
                            col,
                            "Littéral de chaîne non fermé avant la fin de ligne",
                            "Ajoutez le délimiteur $inString manquant sur la ligne $line."
                        )
                    )
                    inString = null
                }
                inLineComment = false
                line++
                col = 1
                i++
                continue
            }

            if (inLineComment) {
                i++; col++; continue
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false
                    i += 2; col += 2
                } else {
                    i++; col++
                }
                continue
            }
            if (inString != null) {
                if (c == '\\') {
                    i += 2; col += 2; continue
                }
                if (c == inString) inString = null
                i++; col++; continue
            }

            if (c == '/' && next == '/') {
                inLineComment = true; i += 2; col += 2; continue
            }
            if (c == '/' && next == '*') {
                inBlockComment = true; i += 2; col += 2; continue
            }
            if (c == '"' || c == '\'') {
                inString = c; i++; col++; continue
            }

            when (c) {
                '{', '(', '[' -> stack.addLast(OpenToken(c, line, col))
                '}', ')', ']' -> {
                    val expected = when (c) {
                        '}' -> '{'
                        ')' -> '('
                        else -> '['
                    }
                    if (stack.isEmpty() || stack.last().ch != expected) {
                        out.add(
                            diag(
                                DiagnosticSeverity.ERROR,
                                "Syntaxe $langLabel",
                                filePath,
                                fileName,
                                line,
                                col,
                                "Délimiteur '$c' inattendu",
                                "Aucun bloc ouvrant correspondant pour '$c' à la ligne $line, colonne $col."
                            )
                        )
                    } else {
                        stack.removeLast()
                    }
                }
            }
            i++
            col++
        }

        if (inBlockComment) {
            out.add(
                diag(
                    DiagnosticSeverity.ERROR,
                    "Commentaire non fermé",
                    filePath,
                    fileName,
                    line,
                    col,
                    "Commentaire bloc /* ... */ non fermé",
                    "Ajoutez */ pour fermer le bloc de commentaire."
                )
            )
        }

        while (stack.isNotEmpty()) {
            val open = stack.removeLast()
            val closing = when (open.ch) {
                '{' -> '}'
                '(' -> ')'
                else -> ']'
            }
            out.add(
                diag(
                    DiagnosticSeverity.ERROR,
                    "Bloc non fermé ($langLabel)",
                    filePath,
                    fileName,
                    open.line,
                    open.col,
                    "'$closing' manquant pour fermer '${open.ch}'",
                    "Le bloc ouvert à la ligne ${open.line}, colonne ${open.col} n'est jamais fermé."
                )
            )
        }
    }

    private fun diag(
        severity: DiagnosticSeverity,
        typeLabel: String,
        filePath: String,
        fileName: String,
        line: Int,
        column: Int,
        message: String,
        details: String
    ) = DiagnosticItem(
        id = UUID.randomUUID().toString(),
        severity = severity,
        typeLabel = typeLabel,
        filePath = filePath,
        fileName = fileName,
        line = line,
        column = column,
        message = message,
        details = details
    )
}
