package dev.stringparity.android

import dev.stringparity.StringEntry
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidStringsParserTest {
    private fun fixture(name: String): Path =
        Path.of(javaClass.getResource("/android/$name")!!.toURI())

    private fun values(name: String): Map<String, String> =
        AndroidStringsParser.parse(fixture(name)).associate { it.key to it.value }

    @Test
    fun `reads string elements in file order`() {
        val entries = AndroidStringsParser.parse(fixture("basic.xml"))
        assertEquals(
            listOf(
                StringEntry("app_name", "StringParity"),
                StringEntry("greeting", "Hello, world"),
                StringEntry("empty", ""),
            ),
            entries,
        )
    }

    @Test
    fun `decodes backslash escapes`() {
        val v = values("escapes.xml")
        assertEquals("Line one\nLine two", v["newline"])
        assertEquals("Name\tValue", v["tab"])
        assertEquals("Don't panic", v["apostrophe"])
        assertEquals("Say \"hi\"", v["quote"])
        assertEquals("@home", v["at_sign"])
        assertEquals("?attr", v["question"])
        assertEquals("C:\\temp", v["backslash"])
        assertEquals("Caf\u00e9 \u20AC", v["unicode"])
    }

    @Test
    fun `strips surrounding double quotes and keeps the whitespace inside`() {
        val v = values("escapes.xml")
        assertEquals("  keep   these  spaces  ", v["quoted"])
        assertEquals("Don't panic", v["quoted_apostrophe"])
    }

    @Test
    fun `collapses unquoted whitespace and trims the ends`() {
        assertEquals("spread over several lines", values("escapes.xml")["collapse"])
    }

    @Test
    fun `expands XML entities and character references`() {
        assertEquals("Fish & Chips <3 \u00a9 Acme", values("entities-cdata.xml")["entities"])
    }

    @Test
    fun `copies CDATA without unescaping it`() {
        val v = values("entities-cdata.xml")
        assertEquals("<b>Bold</b> & \\n literal", v["cdata"])
        assertEquals("Click <here> now", v["mixed"])
    }

    @Test
    fun `keeps the text inside inline markup`() {
        assertEquals("Hello bold and %1\$s", values("entities-cdata.xml")["markup"])
    }

    @Test
    fun `marks translatable false instead of dropping the string`() {
        val byKey = AndroidStringsParser.parse(fixture("translatable.xml")).associateBy { it.key }
        assertEquals(false, byKey.getValue("app_name").translatable)
        assertEquals(true, byKey.getValue("title").translatable)
        assertEquals(true, byKey.getValue("plain").translatable)
        assertEquals("StringParity", byKey.getValue("app_name").value)
    }

    @Test
    fun `ignores plurals, arrays and other resource types without failing`() {
        val entries = AndroidStringsParser.parse(fixture("other-elements.xml"))
        assertEquals(listOf("first", "second"), entries.map { it.key })
    }

    @Test
    fun `fails with the file name and line on malformed XML`() {
        val e = assertFailsWith<StringsParseException> { AndroidStringsParser.parse(fixture("malformed.xml")) }
        assertTrue(e.message!!.contains("malformed.xml"), e.message)
        assertTrue(e.message!!.contains("line"), e.message)
    }

    @Test
    fun `fails on an empty file`() {
        assertFailsWith<StringsParseException> { AndroidStringsParser.parse("", "empty.xml") }
    }

    @Test
    fun `fails when the root element is not resources`() {
        val e = assertFailsWith<StringsParseException> { AndroidStringsParser.parse(fixture("wrong-root.xml")) }
        assertTrue(e.message!!.contains("<resources>"), e.message)
    }

    @Test
    fun `fails when a string has no name`() {
        assertFailsWith<StringsParseException> { AndroidStringsParser.parse(fixture("no-name.xml")) }
    }

    @Test
    fun `fails on a bad unicode escape`() {
        val xml = """<resources><string name="a">bad \u12G4</string></resources>"""
        assertFailsWith<StringsParseException> { AndroidStringsParser.parse(xml) }
    }

    @Test
    fun `fails with a clear error when the file is missing`() {
        val e = assertFailsWith<StringsParseException> { AndroidStringsParser.parse(Path.of("/no/such/strings.xml")) }
        assertTrue(e.message!!.contains("/no/such/strings.xml"))
    }
}
