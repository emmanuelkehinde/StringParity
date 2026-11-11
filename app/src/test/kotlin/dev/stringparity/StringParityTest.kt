package dev.stringparity

import com.github.ajalt.clikt.testing.test
import java.nio.file.Path
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
        assertTrue(result.stdout.contains("No missing keys."), result.stdout)
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
}
