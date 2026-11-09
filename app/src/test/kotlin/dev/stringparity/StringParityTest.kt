package dev.stringparity

import com.github.ajalt.clikt.testing.test
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

    @Test
    fun `accepts existing android and ios paths`() {
        val strings = createTempFile(suffix = ".xml")
        val catalog = createTempFile(suffix = ".xcstrings")
        val result = StringParity().test("--android ${strings.pathString} --ios ${catalog.pathString}")
        assertEquals(0, result.statusCode)
    }
}
