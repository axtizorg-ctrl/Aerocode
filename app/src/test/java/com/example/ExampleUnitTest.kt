package com.example

import com.example.data.model.Language
import com.example.ui.editor.detectCodeBlocks
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testLanguageDetection() {
        assertEquals(Language.CPP, Language.detect("main.cpp"))
        assertEquals(Language.C, Language.detect("module.c"))
        assertEquals(Language.PYTHON, Language.detect("script.py"))
        assertEquals(Language.JAVASCRIPT, Language.detect("app.js"))
        assertEquals(Language.HTML, Language.detect("index.html"))
        assertEquals(Language.CSS, Language.detect("styles.css"))
        assertEquals(Language.RUST, Language.detect("main.rs"))
        assertEquals(Language.GO, Language.detect("server.go"))
    }

    @Test
    fun testDetectCodeBlocksFunctionsAndClasses() {
        val jsCode = listOf(
            "function calculateTotal(items) {",
            "    let total = 0;",
            "    return total;",
            "}",
            "",
            "class ShoppingCart {",
            "    constructor() {",
            "        this.items = [];",
            "    }",
            "}"
        )

        val blocks = detectCodeBlocks(jsCode, Language.JAVASCRIPT)
        assertTrue("Should detect function block", blocks.containsKey(0))
        assertEquals(3, blocks[0]?.endLine)
        assertEquals("function", blocks[0]?.label)

        assertTrue("Should detect class block", blocks.containsKey(5))
        assertEquals(9, blocks[5]?.endLine)
        assertEquals("class", blocks[5]?.label)
    }

    @Test
    fun testDetectPythonCodeBlocks() {
        val pyCode = listOf(
            "class UserModel:",
            "    def __init__(self, name):",
            "        self.name = name",
            "",
            "    def get_name(self):",
            "        return self.name",
            "",
            "print('Done')"
        )

        val blocks = detectCodeBlocks(pyCode, Language.PYTHON)
        assertTrue("Should detect python class block", blocks.containsKey(0))
        assertEquals("class", blocks[0]?.label)
        assertTrue("Should detect python method block", blocks.containsKey(1))
        assertEquals("function", blocks[1]?.label)
    }

    @Test
    fun testDetectHtmlBlocks() {
        val htmlCode = listOf(
            "<!DOCTYPE html>",
            "<html>",
            "<head>",
            "    <title>Test</title>",
            "</head>",
            "<body>",
            "    <div class=\"container\">",
            "        <p>Hello</p>",
            "    </div>",
            "</body>",
            "</html>"
        )

        val blocks = detectCodeBlocks(htmlCode, Language.HTML)
        assertTrue("Should detect html container div", blocks.values.any { it.label == "<div>" })
    }
}
