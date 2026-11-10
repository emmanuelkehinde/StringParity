package dev.stringparity

import com.github.ajalt.clikt.testing.test
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringParityTest {
    @Test
    fun `fails when required options are missing`() {
        val result = StringParity().test("")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("--android"))
    }

    private fun resource(name: String) = Path.of(javaClass.getResource("/$name")!!.toURI()).pathString

    @Test
    fun `prints the string count per locale for a res directory`() {
        val catalog = createTempFile(suffix = ".xcstrings")
        val result = StringParity().test("--android ${resource("res-ok")} --ios ${catalog.pathString}")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("default: 3 (1 not translatable)"), result.stdout)
        assertTrue(result.stdout.contains("fr: 1"), result.stdout)
        assertTrue(result.stdout.contains("fr-CA: 2"), result.stdout)
        assertTrue(result.stdout.contains("sr-Latn: 1"), result.stdout)
    }

    @Test
    fun `accepts a single strings xml file`() {
        val catalog = createTempFile(suffix = ".xcstrings")
        val result = StringParity().test("--android ${resource("android/basic.xml")} --ios ${catalog.pathString}")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("default: 3"), result.stdout)
    }

    @Test
    fun `fails with a clear message on malformed xml`() {
        val catalog = createTempFile(suffix = ".xcstrings")
        val result = StringParity().test("--android ${resource("android/malformed.xml")} --ios ${catalog.pathString}")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed.xml"), result.stderr)
    }

    @Test
    fun `fails when the res directory has no strings files`() {
        val catalog = createTempFile(suffix = ".xcstrings")
        val result = StringParity().test("--android ${resource("res-ok/values-de")} --ios ${catalog.pathString}")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("No strings.xml"), result.stderr)
    }
}
