package com.prosatfinder

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Çeviri eksiklerini yakalar: `gradle testDebugUnitTest` */
class StringsCompletenessTest {
    private val res = File("src/main/res")

    private fun parse(f: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)

    private fun strings(f: File): Map<String, String> {
        val nodes = parse(f).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private fun tags(): List<String> {
        val doc = parse(File(res, "values/language_names.xml"))
        val arrays = doc.getElementsByTagName("string-array")
        for (i in 0 until arrays.length) {
            val a = arrays.item(i)
            if (a.attributes.getNamedItem("name").nodeValue == "language_tags") {
                val items = (a as org.w3c.dom.Element).getElementsByTagName("item")
                return (0 until items.length).map { items.item(it).textContent }
            }
        }
        fail("language_tags bulunamadı"); return emptyList()
    }

    private fun folderFor(tag: String) = when {
        tag == "en" -> "values"
        Regex("[a-z]{2}").matches(tag) -> "values-$tag"
        else -> "values-b+" + tag.replace('-', '+')
    }

    @Test fun everyDeclaredLanguageHasCompleteStrings() {
        val base = strings(File(res, "values/strings.xml"))
        val problems = mutableListOf<String>()
        for (tag in tags()) {
            val f = File(res, "${folderFor(tag)}/strings.xml")
            if (!f.exists()) { problems += "$tag: ${f.path} yok"; continue }
            val s = strings(f)
            (base.keys - s.keys).forEach { problems += "$tag: eksik anahtar $it" }
            (s.keys - base.keys).forEach { problems += "$tag: fazla anahtar $it" }
            s.forEach { (k, v) ->
                if (v.isBlank()) problems += "$tag: boş değer $k"
                val ph = Regex("%\\d\\$[sd]")
                if (base[k] != null && ph.findAll(base[k]!!).map { it.value }.sorted().toList() !=
                    ph.findAll(v).map { it.value }.sorted().toList()) problems += "$tag: yer tutucu uyuşmuyor $k"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun localesConfigMatchesLanguageList() {
        val doc = parse(File(res, "xml/locales_config.xml")).getElementsByTagName("locale")
        val inConfig = (0 until doc.length).map { doc.item(it).attributes.getNamedItem("android:name").nodeValue }.toSet()
        assertEquals(tags().toSet(), inConfig)
    }
}
