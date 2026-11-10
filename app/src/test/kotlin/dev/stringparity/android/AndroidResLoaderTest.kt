package dev.stringparity.android

import dev.stringparity.LocaleStrings
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidResLoaderTest {
    private fun resource(name: String): Path = Path.of(javaClass.getResource("/$name")!!.toURI())

    @Test
    fun `maps folder names to locales`() {
        assertEquals("default", AndroidResLoader.localeOfFolder("values"))
        assertEquals("fr", AndroidResLoader.localeOfFolder("values-fr"))
        assertEquals("fr-CA", AndroidResLoader.localeOfFolder("values-fr-rCA"))
        assertEquals("sr-Latn", AndroidResLoader.localeOfFolder("values-b+sr+Latn"))
        assertEquals("es-419", AndroidResLoader.localeOfFolder("values-b+es+419"))
        assertEquals("es-419", AndroidResLoader.localeOfFolder("values-es-r419"))
        assertEquals("fil", AndroidResLoader.localeOfFolder("values-fil"))
    }

    @Test
    fun `does not treat other qualifiers as locales`() {
        for (folder in listOf("values-night", "values-sw600dp", "values-land", "values-v21", "values-tv", "values-car", "drawable", "layout-fr")) {
            assertNull(AndroidResLoader.localeOfFolder(folder), folder)
        }
        assertNull(AndroidResLoader.localeOfFolder(null))
    }

    @Test
    fun `loads every locale from a res directory, default first`() {
        val locales = AndroidResLoader.load(resource("res-ok"))
        assertEquals(listOf("default", "fr", "fr-CA", "sr-Latn"), locales.map { it.locale })
    }

    @Test
    fun `reads the strings of each locale`() {
        val byLocale = AndroidResLoader.load(resource("res-ok")).associateBy { it.locale }
        assertEquals(3, byLocale.getValue("default").entries.size)
        assertEquals("Bonjour", byLocale.getValue("fr")["hello"]?.value)
        assertEquals(2, byLocale.getValue("fr-CA").entries.size)
        assertEquals("Zdravo", byLocale.getValue("sr-Latn")["hello"]?.value)
        assertEquals(false, byLocale.getValue("default")["brand"]?.translatable)
    }

    @Test
    fun `skips folders without strings xml and folders that are not locales`() {
        val locales = AndroidResLoader.load(resource("res-ok")).map { it.locale }
        assertTrue("de" !in locales, "values-de only has colors.xml")
        assertTrue(locales.none { it == "night" || it.startsWith("sw") })
    }

    @Test
    fun `loads a single strings xml file`() {
        val locales = AndroidResLoader.load(resource("res-ok/values-fr/strings.xml"))
        assertEquals(1, locales.size)
        assertEquals("fr", locales[0].locale)
        assertEquals(listOf("hello"), locales[0].entries.map { it.key })
    }

    @Test
    fun `gives a single file outside a values folder the default locale`() {
        val locales = AndroidResLoader.load(resource("android/basic.xml"))
        assertEquals(LocaleStrings.DEFAULT_LOCALE, locales.single().locale)
    }

    @Test
    fun `returns nothing for a directory with no strings files`() {
        assertEquals(emptyList(), AndroidResLoader.load(resource("res-ok/values-de")))
    }

    @Test
    fun `fails when the path does not exist`() {
        assertFailsWith<StringsParseException> { AndroidResLoader.load(Path.of("/no/such/res")) }
    }

    @Test
    fun `fails with the file name when one strings xml is malformed`() {
        val e = assertFailsWith<StringsParseException> { AndroidResLoader.load(resource("res-broken")) }
        assertTrue(e.message!!.contains("strings.xml"), e.message)
    }
}
