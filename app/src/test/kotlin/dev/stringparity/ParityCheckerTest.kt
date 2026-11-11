package dev.stringparity

import kotlin.test.Test
import kotlin.test.assertEquals

class ParityCheckerTest {
    private fun locale(tag: String, vararg keys: String) =
        LocaleStrings(tag, keys.map { StringEntry(it, it.uppercase()) })

    private fun default(vararg keys: String) = locale(LocaleStrings.DEFAULT_LOCALE, *keys)

    @Test
    fun `reports nothing when both platforms match`() {
        val android = listOf(default("a", "b"), locale("fr", "a"))
        val ios = listOf(default("b", "a"), locale("fr", "a"))
        val report = ParityChecker.compare(android, ios)
        assertEquals(0, report.problemCount)
        assertEquals("No missing keys.", report.toText())
    }

    @Test
    fun `reports default keys missing on either platform`() {
        val report = ParityChecker.compare(listOf(default("a", "b", "c")), listOf(default("a", "d")))
        assertEquals(listOf("b", "c"), report.missingOnIos)
        assertEquals(listOf("d"), report.missingOnAndroid)
        assertEquals(3, report.problemCount)
    }

    @Test
    fun `reports keys translated on one platform and missing on the other per locale`() {
        val android = listOf(default("a", "b", "c"), locale("fr", "a", "b"))
        val ios = listOf(default("a", "b", "c"), locale("fr", "a", "c"))
        val report = ParityChecker.compare(android, ios)
        assertEquals(listOf(LocaleGap("fr", missingOnIos = listOf("b"), missingOnAndroid = listOf("c"))), report.gaps)
    }

    @Test
    fun `does not report a key that neither platform translates`() {
        val android = listOf(default("a", "b"), locale("fr", "a"))
        val ios = listOf(default("a", "b"), locale("fr", "a"))
        assertEquals(emptyList(), ParityChecker.compare(android, ios).gaps)
    }

    @Test
    fun `reports locales that only one platform has`() {
        val android = listOf(default("a"), locale("de", "a"))
        val ios = listOf(default("a"), locale("ja", "a"))
        val report = ParityChecker.compare(android, ios)
        assertEquals(mapOf("de" to 1), report.onlyOnAndroid)
        assertEquals(mapOf("ja" to 1), report.onlyOnIos)
        assertEquals(2, report.problemCount)
    }

    @Test
    fun `leaves out keys that are not translatable where they exist`() {
        val android = listOf(LocaleStrings(LocaleStrings.DEFAULT_LOCALE, listOf(StringEntry("a", "A"), StringEntry("api_key", "x", translatable = false))))
        val ios = listOf(LocaleStrings(LocaleStrings.DEFAULT_LOCALE, listOf(StringEntry("a", "A"), StringEntry("brand", "Acme", translatable = false))))
        assertEquals(0, ParityChecker.compare(android, ios).problemCount)
    }

    @Test
    fun `does not compare a not translatable key per locale`() {
        val android = listOf(default("a", "brand"), locale("fr", "a", "brand"))
        val ios = listOf(
            LocaleStrings(LocaleStrings.DEFAULT_LOCALE, listOf(StringEntry("a", "A"), StringEntry("brand", "Acme", translatable = false))),
            locale("fr", "a"),
        )
        assertEquals(0, ParityChecker.compare(android, ios).problemCount)
    }

    @Test
    fun `compares an Android folder for the iOS source language with the iOS source strings`() {
        val android = listOf(default("a", "b"), locale("en", "a"))
        val ios = listOf(default("a", "b"))
        val report = ParityChecker.compare(android, ios, iosSourceLanguage = "en")
        assertEquals(emptyMap(), report.onlyOnAndroid)
        assertEquals(listOf(LocaleGap("en", missingOnIos = emptyList(), missingOnAndroid = listOf("b"))), report.gaps)
    }

    @Test
    fun `formats the report as text`() {
        val android = listOf(default("a", "b"), locale("fr", "a"), locale("de", "a"))
        val ios = listOf(default("a", "c"), locale("fr"))
        val text = ParityChecker.compare(android, ios).toText()
        val expected = """
            Missing on iOS (1):
              b
            Missing on Android (1):
              c
            Locale de is missing on iOS (1 string on Android)
            Locale fr:
              Missing on iOS (1):
                a
            Found 4 missing items.
        """.trimIndent()
        assertEquals(expected, text)
    }
}
