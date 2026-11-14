package dev.stringparity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals("No missing keys or placeholder mismatches.", report.toText())
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

    private fun values(tag: String, vararg pairs: Pair<String, String>) =
        LocaleStrings(tag, pairs.map { StringEntry(it.first, it.second) })

    private fun p(position: Int, type: PlaceholderType, text: String) = Placeholder(position, type, text)

    @Test
    fun `accepts placeholders that match across platforms`() {
        val android = listOf(values("default", "greet" to "Hi %1\$s, %2\$d new"), values("fr", "greet" to "%2\$d nouveaux, %1\$s"))
        val ios = listOf(values("default", "greet" to "Hi %@, %ld new"), values("fr", "greet" to "%2\$ld nouveaux, %1\$@"))
        assertEquals(0, ParityChecker.compare(android, ios).problemCount)
    }

    @Test
    fun `reports placeholder mismatches in the default locale and per locale`() {
        val android = listOf(values("default", "greet" to "Hi %s", "total" to "%d items"), values("fr", "greet" to "Salut %s", "total" to "%d articles"))
        val ios = listOf(values("default", "greet" to "Hi %@ %@", "total" to "%lld items"), values("fr", "greet" to "Salut %@ %@", "total" to "%@ articles"))
        val report = ParityChecker.compare(android, ios)
        assertEquals(
            listOf(
                PlaceholderMismatch("greet", "default", "count differs", listOf(p(1, PlaceholderType.STRING, "%s")), listOf(p(1, PlaceholderType.STRING, "%@"), p(2, PlaceholderType.STRING, "%@"))),
                PlaceholderMismatch("greet", "fr", "count differs", listOf(p(1, PlaceholderType.STRING, "%s")), listOf(p(1, PlaceholderType.STRING, "%@"), p(2, PlaceholderType.STRING, "%@"))),
                PlaceholderMismatch("total", "fr", "types differ", listOf(p(1, PlaceholderType.INTEGER, "%d")), listOf(p(1, PlaceholderType.STRING, "%@"))),
            ),
            report.placeholderMismatches,
        )
        assertEquals(listOf("total" to Platform.IOS), report.placeholderDrift.map { it.key to it.platform })
        assertEquals(4, report.problemCount)
    }

    @Test
    fun `reports a translation whose placeholders differ from its own default`() {
        val android = listOf(values("default", "n" to "%d left"), values("de", "n" to "%s übrig"), values("ja", "n" to "残り%d"))
        val report = ParityChecker.compare(android, listOf(values("default", "n" to "%d left")))
        assertEquals(
            listOf(PlaceholderDrift("n", "de", Platform.ANDROID, "types differ", listOf(p(1, PlaceholderType.STRING, "%s")), listOf(p(1, PlaceholderType.INTEGER, "%d")))),
            report.placeholderDrift,
        )
        assertEquals(emptyList(), report.placeholderMismatches)
    }

    @Test
    fun `does not compare placeholders of keys that are missing or not translatable`() {
        val android = listOf(
            LocaleStrings("default", listOf(StringEntry("a", "%d"), StringEntry("fmt", "%s", translatable = false))),
            values("fr", "fmt" to "%d"),
        )
        val ios = listOf(values("default", "b" to "%@", "fmt" to "%d"), values("fr", "b" to "%d"))
        val report = ParityChecker.compare(android, ios)
        assertEquals(emptyList(), report.placeholderMismatches)
        assertEquals(listOf("b" to "fr"), report.placeholderDrift.map { it.key to it.locale })
    }

    @Test
    fun `formats placeholder problems as text`() {
        val android = listOf(values("default", "greet" to "Hi %1\$s"), values("fr", "greet" to "Salut %1\$s"))
        val ios = listOf(values("default", "greet" to "Hi %1\$@ %2\$d"), values("fr", "greet" to "Salut"))
        val expected = """
            Placeholder mismatches (2):
              greet in default, count differs: Android %1${'$'}s, iOS %1${'$'}@ %2${'$'}d
              greet in fr, count differs: Android %1${'$'}s, iOS none
            Placeholders that differ from the default (1):
              greet in fr on iOS, count differs: none, default %1${'$'}@ %2${'$'}d
            Found 3 placeholder mismatches.
        """.trimIndent()
        assertEquals(expected, ParityChecker.compare(android, ios).toText())
    }

    @Test
    fun `counts missing keys and placeholder mismatches separately in the summary`() {
        val report = ParityChecker.compare(listOf(values("default", "a" to "%d", "b" to "B")), listOf(values("default", "a" to "%@")))
        assertTrue(report.toText().endsWith("Found 1 missing item and 1 placeholder mismatch."), report.toText())
    }
}
