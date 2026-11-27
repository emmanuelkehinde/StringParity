package dev.stringparity

import com.github.ajalt.clikt.testing.test
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringParityTest {
    @Test
    fun `fails when neither platform is given`() {
        val result = StringParity().test("")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("--android"), result.stderr)
        assertTrue(result.stderr.contains("--ios"), result.stderr)
    }

    private fun resource(name: String) = Path.of(javaClass.getResource("/$name")!!.toURI()).pathString

    @Test
    fun `prints the string count per locale for a res directory`() {
        val result = StringParity().test("--android ${resource("res-ok")}")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("default: 3 (1 not translatable)"), result.stdout)
        assertTrue(result.stdout.contains("fr: 1"), result.stdout)
        assertTrue(result.stdout.contains("fr-CA: 2"), result.stdout)
        assertTrue(result.stdout.contains("sr-Latn: 1"), result.stdout)
    }

    @Test
    fun `accepts a single strings xml file`() {
        val result = StringParity().test("--android ${resource("android/basic.xml")}")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("default: 3"), result.stdout)
    }

    @Test
    fun `fails with a clear message on malformed xml`() {
        val result = StringParity().test("--android ${resource("android/malformed.xml")}")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed.xml"), result.stderr)
    }

    @Test
    fun `fails when the res directory has no strings files`() {
        val result = StringParity().test("--android ${resource("res-ok/values-de")}")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("No strings.xml"), result.stderr)
    }

    @Test
    fun `prints the string count per locale for a string catalog`() {
        val result = StringParity().test("--ios ${resource("ios/basic.xcstrings")}")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("source language en"), result.stdout)
        assertTrue(result.stdout.contains("default: 3"), result.stdout)
        assertTrue(result.stdout.contains("fr: 2"), result.stdout)
        assertTrue(result.stdout.contains("pt-BR: 1"), result.stdout)
    }

    @Test
    fun `notes skipped variations and strings that are not translatable`() {
        val result = StringParity().test("--ios ${resource("ios/special.xcstrings")}")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("default: 2 (1 not translatable)"), result.stdout)
        assertTrue(result.stdout.contains("Skipped 1 key with plural or device variations"), result.stdout)
    }

    @Test
    fun `fails with a clear message on a malformed catalog`() {
        val result = StringParity().test("--ios ${resource("ios/malformed.xcstrings")}")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed.xcstrings"), result.stderr)
    }

    @Test
    fun `exits with zero when both platforms have the same keys`() {
        val result = StringParity().test("--android ${resource("res-ok")} --ios ${resource("ios/matches-res-ok.xcstrings")}")
        assertEquals(0, result.statusCode, result.output)
        assertTrue(result.stdout.contains("Android strings per locale:"), result.stdout)
        assertTrue(result.stdout.contains("No missing keys or placeholder mismatches."), result.stdout)
    }

    @Test
    fun `reports missing keys and locales and exits with one`() {
        val result = StringParity().test("--android ${resource("res-ok")} --ios ${resource("ios/basic.xcstrings")}")
        assertEquals(1, result.statusCode, result.output)
        val out = result.stdout
        assertTrue(out.contains("Missing on Android (1):\n  Welcome back"), out)
        assertTrue(out.contains("Locale fr-CA is missing on iOS (2 strings on Android)"), out)
        assertTrue(out.contains("Locale sr-Latn is missing on iOS (1 string on Android)"), out)
        assertTrue(out.contains("Locale pt-BR is missing on Android (1 string on iOS)"), out)
        assertTrue(out.contains("Locale fr:\n  Missing on Android (1):\n    bye"), out)
        assertTrue(out.contains("Found 5 missing items."), out)
    }

    @Test
    fun `exits with zero when placeholders match across platforms`() {
        val result = StringParity().test("--android ${resource("res-placeholders")} --ios ${resource("ios/placeholders-ok.xcstrings")}")
        assertEquals(0, result.statusCode, result.output)
        assertTrue(result.stdout.contains("No missing keys or placeholder mismatches."), result.stdout)
    }

    @Test
    fun `reports placeholder mismatches and exits with one`() {
        val result = StringParity().test("--android ${resource("res-placeholders")} --ios ${resource("ios/placeholders-bad.xcstrings")}")
        assertEquals(1, result.statusCode, result.output)
        val expected = """
            Placeholder mismatches (3):
              price in default, types differ: Android %.2f, iOS %lld
              greeting in fr, types differ: Android %1${'$'}s %2${'$'}d, iOS %@ %@
              price in fr, types differ: Android %.2f, iOS %lld
            Placeholders that differ from the default (1):
              greeting in fr on iOS, types differ: %@ %@, default %@ %d
            Found 4 placeholder mismatches.
        """.trimIndent()
        assertTrue(result.stdout.endsWith(expected + "\n"), result.stdout)
    }

    @Test
    fun `reports renamed keys as missing without a mapping`() {
        val result = StringParity().test("--android ${resource("res-mapped")} --ios ${resource("ios/mapped.xcstrings")}")
        assertEquals(1, result.statusCode, result.output)
        assertTrue(result.stdout.contains("Missing on iOS (1):\n  login_title"), result.stdout)
        assertTrue(result.stdout.contains("Missing on Android (1):\n  Log in"), result.stdout)
    }

    @Test
    fun `treats mapped keys as the same string`() {
        val result = StringParity().test(
            "--android ${resource("res-mapped")} --ios ${resource("ios/mapped.xcstrings")} --mapping ${resource("mapping/valid.yaml")}",
        )
        assertEquals(0, result.statusCode, result.output)
        assertTrue(result.stdout.contains("No missing keys or placeholder mismatches."), result.stdout)
    }

    @Test
    fun `fails with a clear message on an invalid mapping file`() {
        val result = StringParity().test(
            "--android ${resource("res-mapped")} --ios ${resource("ios/mapped.xcstrings")} --mapping ${resource("mapping/no-keys.yaml")}",
        )
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("no-keys.yaml"), result.stderr)
    }

    @Test
    fun `mapping needs both platforms`() {
        val result = StringParity().test("--android ${resource("res-mapped")} --mapping ${resource("mapping/valid.yaml")}")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("--mapping needs both"), result.stderr)
    }

    private fun parseJson(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun JsonObject.strings(name: String) = getValue(name).jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `prints an empty json report when both platforms match`() {
        val result = StringParity().test(
            "--android ${resource("res-ok")} --ios ${resource("ios/matches-res-ok.xcstrings")} --format json",
        )
        assertEquals(0, result.statusCode, result.output)
        assertEquals("", result.stderr)
        val json = parseJson(result.stdout)
        assertEquals(
            listOf("missingKeys", "missingLocales", "localeGaps", "placeholderMismatches", "placeholderDrift", "summary"),
            json.keys.toList(),
        )
        for (group in listOf("missingKeys", "missingLocales")) {
            val platforms = json.getValue(group).jsonObject
            assertEquals(listOf("android", "ios"), platforms.keys.toList())
            assertTrue(platforms.values.all { (it as JsonArray).isEmpty() }, result.stdout)
        }
        assertEquals(emptyList(), json.getValue("localeGaps").jsonArray)
        assertEquals(emptyList(), json.getValue("placeholderMismatches").jsonArray)
        assertEquals(emptyList(), json.getValue("placeholderDrift").jsonArray)
        val summary = json.getValue("summary").jsonObject
        assertEquals(listOf("missing", "placeholderMismatches", "problems"), summary.keys.toList())
        assertTrue(summary.values.all { it.jsonPrimitive.int == 0 }, result.stdout)
    }

    @Test
    fun `reports missing keys and locales as json and exits with one`() {
        val result = StringParity().test(
            "--android ${resource("res-ok")} --ios ${resource("ios/basic.xcstrings")} --format json",
        )
        assertEquals(1, result.statusCode, result.output)
        val json = parseJson(result.stdout)

        val missingKeys = json.getValue("missingKeys").jsonObject
        assertEquals(listOf("Welcome back"), missingKeys.strings("android"))
        assertEquals(emptyList(), missingKeys.strings("ios"))

        val missingLocales = json.getValue("missingLocales").jsonObject
        fun locales(platform: String) = missingLocales.getValue(platform).jsonArray.map {
            it.jsonObject.getValue("locale").jsonPrimitive.content to it.jsonObject.getValue("strings").jsonPrimitive.int
        }
        assertEquals(listOf("pt-BR" to 1), locales("android"))
        assertEquals(listOf("fr-CA" to 2, "sr-Latn" to 1), locales("ios"))

        val gap = json.getValue("localeGaps").jsonArray.single().jsonObject
        assertEquals("fr", gap.getValue("locale").jsonPrimitive.content)
        assertEquals(listOf("bye"), gap.strings("missingOnAndroid"))
        assertEquals(emptyList(), gap.strings("missingOnIos"))

        val summary = json.getValue("summary").jsonObject
        assertEquals(5, summary.getValue("missing").jsonPrimitive.int)
        assertEquals(0, summary.getValue("placeholderMismatches").jsonPrimitive.int)
        assertEquals(5, summary.getValue("problems").jsonPrimitive.int)
    }

    @Test
    fun `reports placeholder mismatches as json`() {
        val result = StringParity().test(
            "--android ${resource("res-placeholders")} --ios ${resource("ios/placeholders-bad.xcstrings")} --format json",
        )
        assertEquals(1, result.statusCode, result.output)
        val json = parseJson(result.stdout)

        val mismatches = json.getValue("placeholderMismatches").jsonArray.map { it.jsonObject }
        assertEquals(listOf("price", "greeting", "price"), mismatches.map { it.getValue("key").jsonPrimitive.content })
        assertEquals(listOf("default", "fr", "fr"), mismatches.map { it.getValue("locale").jsonPrimitive.content })
        val greeting = mismatches[1]
        assertEquals("types differ", greeting.getValue("reason").jsonPrimitive.content)
        assertEquals(listOf("%1${'$'}s", "%2${'$'}d"), greeting.strings("android"))
        assertEquals(listOf("%@", "%@"), greeting.strings("ios"))

        val drift = json.getValue("placeholderDrift").jsonArray.single().jsonObject
        assertEquals("greeting", drift.getValue("key").jsonPrimitive.content)
        assertEquals("ios", drift.getValue("platform").jsonPrimitive.content)
        assertEquals(listOf("%@", "%@"), drift.strings("translated"))
        assertEquals(listOf("%@", "%d"), drift.strings("default"))

        assertEquals(4, json.getValue("summary").jsonObject.getValue("placeholderMismatches").jsonPrimitive.int)
    }

    @Test
    fun `json output is the same on every run`() {
        val args = "--android ${resource("res-ok")} --ios ${resource("ios/basic.xcstrings")} --format json"
        assertEquals(StringParity().test(args).stdout, StringParity().test(args).stdout)
    }

    @Test
    fun `json format needs both platforms`() {
        val result = StringParity().test("--android ${resource("res-ok")} --format json")
        assertEquals(1, result.statusCode)
        assertEquals("", result.stdout)
        assertTrue(result.stderr.contains("--format json needs both"), result.stderr)
    }

    @Test
    fun `json format sends input errors to stderr`() {
        val result = StringParity().test(
            "--android ${resource("res-ok")} --ios ${resource("ios/malformed.xcstrings")} --format json",
        )
        assertEquals(1, result.statusCode)
        assertEquals("", result.stdout)
        assertTrue(result.stderr.contains("malformed.xcstrings"), result.stderr)
    }

    @Test
    fun `rejects an unknown format`() {
        val result = StringParity().test("--android ${resource("res-ok")} --format xml")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("--format"), result.stderr)
    }
}
