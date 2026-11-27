// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * The texts of the interface (F19, `AGENTS.md` §4, §11): every string of every module exists in
 * English (default) and in French, with the same placeholders; the French follows the typographic
 * rules; no visible text is written in the Kotlin code.
 *
 * The test reads the source tree (the working directory of a Gradle test is the `app` module), so
 * it covers the modules without needing them on the classpath.
 */
class StringResourcesTest {
    /** One resource of a `strings.xml`: its texts (a plural has one per quantity) and whether it is translated. */
    private data class Entry(val name: String, val texts: List<String>, val translatable: Boolean)

    private val moduleDirs: List<File> =
        listOf(File("."), *File("../feature").listFiles { file -> file.isDirectory }.orEmpty())
            .filter { File(it, "src/main/res/values/strings.xml").exists() }

    private fun load(module: File, folder: String): Map<String, Entry> {
        val file = File(module, "src/main/res/$folder/strings.xml")
        if (!file.exists()) return emptyMap()
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val entries = mutableListOf<Entry>()
        val children = root.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index) as? Element ?: continue
            val texts = if (node.tagName == "plurals") {
                val items = node.getElementsByTagName("item")
                List(items.length) { items.item(it).textContent }
            } else {
                listOf(node.textContent)
            }
            entries += Entry(node.getAttribute("name"), texts, node.getAttribute("translatable") != "false")
        }
        return entries.associateBy { it.name }
    }

    private fun forEachModule(
        check: (module: String, english: Map<String, Entry>, french: Map<String, Entry>) -> Unit,
    ) {
        assertThat(moduleDirs).isNotEmpty()
        moduleDirs.forEach { dir ->
            check(dir.canonicalFile.name, load(dir, "values"), load(dir, "values-fr"))
        }
    }

    @Test
    fun `every module that has texts is found`() {
        val names = moduleDirs.map { it.canonicalFile.name }

        assertThat(names).containsAtLeast("app", "library", "reader", "settings", "about")
    }

    @Test
    fun `every translatable English string exists in French, and no French string is extra`() {
        forEachModule { module, english, french ->
            val missing = english.values.filter { it.translatable && it.name !in french }.map { it.name }
            val extra = french.keys - english.keys
            val untranslatedButPresent = french.keys.filter { english[it]?.translatable == false }

            assertWithMessage("$module: strings missing in French").that(missing).isEmpty()
            assertWithMessage("$module: French strings without English").that(extra).isEmpty()
            assertWithMessage("$module: translatable=false but translated").that(untranslatedButPresent).isEmpty()
        }
    }

    @Test
    fun `a translation keeps the placeholders of the English text`() {
        forEachModule { module, english, french ->
            french.values.forEach { translation ->
                val original = english[translation.name] ?: return@forEach
                assertWithMessage("$module: placeholders of ${translation.name}")
                    .that(placeholders(translation.texts))
                    .isEqualTo(placeholders(original.texts))
            }
        }
    }

    @Test
    fun `no string is empty`() {
        forEachModule { module, english, french ->
            (english.values + french.values).forEach { entry ->
                assertWithMessage("$module: ${entry.name} is empty").that(entry.texts.all { it.isNotBlank() }).isTrue()
            }
        }
    }

    @Test
    fun `French puts a no-break space before the high punctuation and inside the guillemets`() {
        forEachModule { module, _, french ->
            french.values.forEach { entry ->
                entry.texts.forEach { text ->
                    assertWithMessage("$module: ${entry.name} has a plain space before : ; ? ! or »")
                        .that(HIGH_PUNCTUATION_AFTER_SPACE.containsMatchIn(text))
                        .isFalse()
                    assertWithMessage("$module: ${entry.name} has no space before : ; ? ! or »")
                        .that(HIGH_PUNCTUATION_GLUED.containsMatchIn(text))
                        .isFalse()
                    assertWithMessage("$module: ${entry.name} has no space after «")
                        .that(GUILLEMET_OPEN_GLUED.containsMatchIn(text))
                        .isFalse()
                }
            }
        }
    }

    @Test
    fun `texts use typographic quotes and apostrophes`() {
        forEachModule { module, english, french ->
            (english.values.filter { it.translatable } + french.values).forEach { entry ->
                entry.texts.forEach { text ->
                    assertWithMessage("$module: ${entry.name} has a straight quote").that(text).doesNotContain("\"")
                    assertWithMessage("$module: ${entry.name} has a straight apostrophe").that(text).doesNotContain("'")
                    assertWithMessage("$module: ${entry.name} has three dots").that(text).doesNotContain("...")
                }
            }
        }
    }

    @Test
    fun `no visible text is written in the Kotlin code`() {
        val offenders = moduleDirs
            .flatMap { File(it, "src/main").walkTopDown().filter { file -> file.extension == "kt" }.toList() }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> !line.isCommentLine() && LITERAL_TEXT.containsMatchIn(line) }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }

        assertWithMessage("visible texts that are not string resources").that(offenders).isEmpty()
    }

    private fun String.isCommentLine(): Boolean =
        trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

    private fun placeholders(texts: List<String>): Set<String> =
        texts.flatMap { PLACEHOLDER.findAll(it).map { match -> match.value } }.toSet()

    private companion object {
        /** `%d`, `%s`, `%1$s`, `%1$,d`, `%.1f`. */
        val PLACEHOLDER = Regex("""%(?:\d+\$)?[,0-9.\-]*[sdf]""")

        /** A plain space before `:`, `;`, `?`, `!` or `»` (a no-break space is expected). */
        val HIGH_PUNCTUATION_AFTER_SPACE = Regex(""" [:;?!»]""")

        /** `:`, `;`, `?` or `!` right after a letter or a digit, except in an address or an ellipsis. */
        val HIGH_PUNCTUATION_GLUED = Regex("""[\p{L}\d)][:;?!](?!/)""")

        /** `«` followed by a character other than a no-break space. */
        val GUILLEMET_OPEN_GLUED = Regex("""«[^  ]""")

        /** `Text("…")`, `text = "…"`, `contentDescription = "…"` with a letter in a literal. */
        val LITERAL_TEXT =
            Regex(
                """(\bText\(\s*|\b(text|contentDescription|label|title|placeholder|stateDescription) = )"[^"]*\p{L}""",
            )
    }
}
