package dev.stringparity.ios

import dev.stringparity.LocaleStrings
import dev.stringparity.StringEntry
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XcstringsParserTest {
    private fun fixture(name: String): Path =
        Path.of(javaClass.getResource("/ios/$name")!!.toURI())

    private fun StringCatalog.locale(tag: String) = locales.single { it.locale == tag }

    @Test
    fun `reads every locale, source language first as default`() {
        val catalog = XcstringsParser.parse(fixture("basic.xcstrings"))
        assertEquals("en", catalog.sourceLanguage)
        assertEquals(listOf(LocaleStrings.DEFAULT_LOCALE, "fr", "pt-BR"), catalog.locales.map { it.locale })
    }

    @Test
    fun `reads string unit values`() {
        val catalog = XcstringsParser.parse(fixture("basic.xcstrings"))
        assertEquals("Hello", catalog.locale("default")["hello"]?.value)
        assertEquals("Au revoir", catalog.locale("fr")["bye"]?.value)
        assertEquals("Olá", catalog.locale("pt-BR")["hello"]?.value)
        assertEquals(1, catalog.locale("pt-BR").entries.size)
    }

    @Test
    fun `uses the key as the source value when the source localization is missing`() {
        val catalog = XcstringsParser.parse(fixture("basic.xcstrings"))
        assertEquals(StringEntry("Welcome back", "Welcome back"), catalog.locale("default")["Welcome back"])
        assertNull(catalog.locale("fr")["Welcome back"])
    }

    @Test
    fun `respects the source language`() {
        val catalog = XcstringsParser.parse(fixture("special.xcstrings"))
        assertEquals("de", catalog.sourceLanguage)
        assertEquals("Titel", catalog.locale("default")["title"]?.value)
        assertEquals("Title", catalog.locale("en")["title"]?.value)
    }

    @Test
    fun `maps shouldTranslate false to not translatable`() {
        val catalog = XcstringsParser.parse(fixture("special.xcstrings"))
        assertEquals(false, catalog.locale("default")["brand"]?.translatable)
        assertEquals(true, catalog.locale("default")["title"]?.translatable)
    }

    @Test
    fun `skips plural variations and lists their keys`() {
        val catalog = XcstringsParser.parse(fixture("special.xcstrings"))
        assertEquals(setOf("items_count"), catalog.variationKeys)
        assertTrue(catalog.locales.none { it["items_count"] != null })
    }

    @Test
    fun `reads a catalog with no strings`() {
        val catalog = XcstringsParser.parse(fixture("empty.xcstrings"))
        assertEquals(listOf(LocaleStrings(LocaleStrings.DEFAULT_LOCALE, emptyList())), catalog.locales)
    }

    @Test
    fun `fails with the file name on malformed json`() {
        val e = assertFailsWith<CatalogParseException> { XcstringsParser.parse(fixture("malformed.xcstrings")) }
        assertTrue(e.message!!.contains("malformed.xcstrings"), e.message)
    }

    @Test
    fun `fails when sourceLanguage is missing`() {
        val e = assertFailsWith<CatalogParseException> { XcstringsParser.parse(fixture("no-source.xcstrings")) }
        assertTrue(e.message!!.contains("sourceLanguage"), e.message)
    }

    @Test
    fun `fails when the top level is not an object`() {
        assertFailsWith<CatalogParseException> { XcstringsParser.parse("[]") }
    }

    @Test
    fun `fails when a string unit has no value`() {
        val text = """{"sourceLanguage":"en","strings":{"k":{"localizations":{"en":{"stringUnit":{"state":"new"}}}}}}"""
        assertFailsWith<CatalogParseException> { XcstringsParser.parse(text) }
    }
}
