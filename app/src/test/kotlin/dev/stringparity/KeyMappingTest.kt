package dev.stringparity

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeyMappingTest {
    private fun resource(name: String) = Path.of(javaClass.getResource("/mapping/$name")!!.toURI())

    private fun failure(name: String) = assertFailsWith<MappingParseException> { KeyMapping.parse(resource(name)) }.message!!

    @Test
    fun `reads Android to iOS keys`() {
        assertEquals(mapOf("login_title" to "Log in"), KeyMapping.parse(resource("valid.yaml")).androidToIos)
    }

    @Test
    fun `renames only mapped keys in every locale`() {
        val mapping = KeyMapping(mapOf("a" to "A"))
        val android = listOf(
            LocaleStrings("default", listOf(StringEntry("a", "x"), StringEntry("b", "y", translatable = false))),
            LocaleStrings("fr", listOf(StringEntry("a", "z"))),
        )
        val renamed = mapping.apply(android)
        assertEquals(listOf("A", "b"), renamed[0].entries.map { it.key })
        assertEquals(false, renamed[0].entries[1].translatable)
        assertEquals("A", renamed[1].entries.single().key)
        assertEquals("z", renamed[1].entries.single().value)
    }

    @Test
    fun `rejects an empty file`() {
        assertTrue(failure("empty.yaml").contains("empty.yaml"))
    }

    @Test
    fun `rejects a file without a keys entry`() {
        assertTrue(failure("no-keys.yaml").contains("unknown top-level entry 'mappings'"))
    }

    @Test
    fun `rejects keys that is not a map`() {
        assertTrue(failure("not-a-map.yaml").contains("'keys' must map"))
    }

    @Test
    fun `rejects a value that is not text`() {
        assertTrue(failure("bad-value.yaml").contains("entry 'login_title'"))
    }

    @Test
    fun `rejects two Android keys that map to one iOS key`() {
        assertTrue(failure("duplicate-target.yaml").contains("iOS key 'Log in'"))
    }

    @Test
    fun `rejects malformed yaml`() {
        val message = failure("malformed.yaml")
        assertTrue(message.startsWith("Invalid mapping file"), message)
        assertTrue(message.contains("malformed.yaml"), message)
    }
}
