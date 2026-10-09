package com.example.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.data.model.IdeTheme
import com.example.data.model.Language
import java.util.regex.Pattern

/**
 * High-performance mobile syntax highlighter using token regex with IdeTheme colors.
 */
object SyntaxHighlighter {

    private val KEYWORDS_C_CPP = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
        "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
        "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
        "switch", "typedef", "union", "unsigned", "void", "volatile", "while", "class",
        "namespace", "template", "typename", "using", "public", "private", "protected",
        "virtual", "override", "nullptr", "bool", "true", "false", "new", "delete",
        "try", "catch", "throw", "constexpr", "static_cast", "dynamic_cast", "const_cast"
    )

    private val KEYWORDS_PYTHON = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
        "del", "elif", "else", "except", "False", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass",
        "raise", "return", "True", "try", "while", "with", "yield", "self"
    )

    private val KEYWORDS_JS_TS = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default",
        "delete", "do", "else", "export", "extends", "finally", "for", "function", "if",
        "import", "in", "instanceof", "new", "return", "super", "switch", "this", "throw",
        "try", "typeof", "var", "void", "while", "with", "yield", "let", "static", "enum",
        "await", "implements", "interface", "package", "private", "protected", "public",
        "null", "true", "false", "undefined", "type", "as", "any", "string", "number", "boolean",
        "document", "window", "console", "parseInt", "parseFloat", "Math", "JSON", "Promise"
    )

    private val KEYWORDS_JAVA = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
    )

    private val KEYWORDS_KOTLIN = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
        "get", "import", "init", "param", "property", "receiver", "set", "setparam", "where",
        "data", "sealed", "open", "override", "private", "protected", "public", "internal",
        "suspend", "tailrec", "operator", "infix", "inline"
    )

    private val KEYWORDS_RUST = setOf(
        "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
        "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
        "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
        "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while"
    )

    private val KEYWORDS_GO = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer", "else",
        "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
        "map", "package", "range", "return", "select", "struct", "switch", "type", "var"
    )

    private val KEYWORDS_CSS = setOf(
        "display", "flex", "grid", "position", "relative", "absolute", "fixed", "sticky",
        "margin", "padding", "width", "height", "max-width", "min-width", "color",
        "background", "background-color", "border", "border-radius", "font-family",
        "font-size", "font-weight", "line-height", "text-align", "justify-content",
        "align-items", "box-shadow", "transition", "transform", "cursor", "overflow",
        "none", "block", "inline-block", "center", "pointer", "solid", "auto", "important"
    )

    private val NUMBER_PATTERN = Pattern.compile("\\b(\\d+(\\.\\d+)?([eE][+-]?\\d+)?(px|rem|em|%|vh|vw|s|ms)?|0x[0-9a-fA-F]+|#[0-9a-fA-F]{3,8})\\b")
    private val STRING_PATTERN = Pattern.compile("(\"[^\"]*\"|'[^']*'|`[^`]*`)")
    private val LINE_COMMENT_SLASH = Pattern.compile("//.*")
    private val LINE_COMMENT_HASH = Pattern.compile("#.*")
    private val BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->")
    private val HTML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z0-9_-]+|/?>")
    private val HTML_ATTR_PATTERN = Pattern.compile("\\b([a-zA-Z_-]+)(?=\\=)")
    private val PREPROCESSOR_PATTERN = Pattern.compile("^\\s*#[a-zA-Z_]+.*", Pattern.MULTILINE)

    fun highlight(code: String, language: Language, theme: IdeTheme): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")

        val builder = buildAnnotatedString {
            append(code)

            val keywords = when (language) {
                Language.CPP, Language.C -> KEYWORDS_C_CPP
                Language.PYTHON -> KEYWORDS_PYTHON
                Language.JAVASCRIPT, Language.TYPESCRIPT -> KEYWORDS_JS_TS
                Language.JAVA -> KEYWORDS_JAVA
                Language.KOTLIN -> KEYWORDS_KOTLIN
                Language.RUST -> KEYWORDS_RUST
                Language.GO -> KEYWORDS_GO
                Language.CSS -> KEYWORDS_CSS
                Language.SHELL -> KEYWORDS_PYTHON
                Language.PHP -> KEYWORDS_C_CPP
                else -> emptySet()
            }

            // HTML Tag & Attribute Highlighting
            if (language == Language.HTML) {
                val tagMatcher = HTML_TAG_PATTERN.matcher(code)
                while (tagMatcher.find()) {
                    addStyle(
                        SpanStyle(color = theme.keywordColor, fontWeight = FontWeight.Bold),
                        tagMatcher.start(),
                        tagMatcher.end()
                    )
                }
                val attrMatcher = HTML_ATTR_PATTERN.matcher(code)
                while (attrMatcher.find()) {
                    addStyle(
                        SpanStyle(color = theme.typeColor),
                        attrMatcher.start(),
                        attrMatcher.end()
                    )
                }
            }

            // Preprocessor directives for C/C++
            if (language == Language.CPP || language == Language.C) {
                val preprocessorMatcher = PREPROCESSOR_PATTERN.matcher(code)
                while (preprocessorMatcher.find()) {
                    addStyle(
                        SpanStyle(color = theme.keywordColor, fontWeight = FontWeight.Bold),
                        preprocessorMatcher.start(),
                        preprocessorMatcher.end()
                    )
                }
            }

            // Word token search for keywords and functions
            val wordPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b")
            val wordMatcher = wordPattern.matcher(code)
            while (wordMatcher.find()) {
                val word = wordMatcher.group(1)
                val start = wordMatcher.start()
                val end = wordMatcher.end()

                if (keywords.contains(word)) {
                    addStyle(
                        SpanStyle(color = theme.keywordColor, fontWeight = FontWeight.Bold),
                        start,
                        end
                    )
                } else if (end < code.length && code[end] == '(') {
                    addStyle(
                        SpanStyle(color = theme.functionColor),
                        start,
                        end
                    )
                } else if (word != null && word.isNotEmpty() && word[0].isUpperCase()) {
                    addStyle(
                        SpanStyle(color = theme.typeColor),
                        start,
                        end
                    )
                }
            }

            // Numbers
            val numMatcher = NUMBER_PATTERN.matcher(code)
            while (numMatcher.find()) {
                addStyle(
                    SpanStyle(color = theme.numberColor),
                    numMatcher.start(),
                    numMatcher.end()
                )
            }

            // Strings
            val strMatcher = STRING_PATTERN.matcher(code)
            while (strMatcher.find()) {
                addStyle(
                    SpanStyle(color = theme.stringColor),
                    strMatcher.start(),
                    strMatcher.end()
                )
            }

            // Comments (apply last to override tokens within comments)
            val commentPattern = if (language == Language.PYTHON || language == Language.SHELL) {
                LINE_COMMENT_HASH
            } else {
                LINE_COMMENT_SLASH
            }
            val commentMatcher = commentPattern.matcher(code)
            while (commentMatcher.find()) {
                addStyle(
                    SpanStyle(color = theme.commentColor),
                    commentMatcher.start(),
                    commentMatcher.end()
                )
            }

            // Block & HTML Comments
            val blockCommentMatcher = BLOCK_COMMENT_PATTERN.matcher(code)
            while (blockCommentMatcher.find()) {
                addStyle(
                    SpanStyle(color = theme.commentColor),
                    blockCommentMatcher.start(),
                    blockCommentMatcher.end()
                )
            }
        }

        return builder
    }
}
