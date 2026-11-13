package com.prosatfinder

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TranslationCompletenessTest {
    private val res = File("src/main/res")

    private fun keys(dir: File, onlyTranslatable: Boolean): Set<String> {
        val result = HashSet<String>()
        dir.listFiles { f -> f.extension == "xml" }?.forEach { f ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(f).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                if (onlyTranslatable && e.getAttribute("translatable") == "false") continue
                result += e.getAttribute("name")
            }
        }
        return result
    }

    private val expected = listOf(
        "values-tr", "values-b+azb", "values-az", "values-b+az+Arab", "values-fa", "values-de",
        "values-ru", "values-ar", "values-fr", "values-es", "values-it", "values-pt", "values-zh",
        "values-ja", "values-ko", "values-hi", "values-ur"
    )

    @Test fun everyLanguageFolderExists() {
        val missing = expected.filter { !File(res, it).isDirectory }
        assertTrue("Eksik dil klasörleri: $missing", missing.isEmpty())
    }

    @Test fun noMissingKeys() {
        val base = keys(File(res, "values"), true)
        val problems = expected.mapNotNull { d ->
            val dir = File(res, d)
            if (!dir.isDirectory) null
            else (base - keys(dir, false)).takeIf { it.isNotEmpty() }?.let { "$d: $it" }
        }
        assertTrue("Eksik çeviriler: $problems", problems.isEmpty())
    }
}
