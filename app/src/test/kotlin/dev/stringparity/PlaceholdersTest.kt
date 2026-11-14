package dev.stringparity

import dev.stringparity.PlaceholderType.FLOAT
import dev.stringparity.PlaceholderType.INTEGER
import dev.stringparity.PlaceholderType.STRING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceholdersTest {
    private fun signature(value: String) = Placeholders.extract(value).map { it.position to it.type }

    @Test
    fun `reads Android placeholders`() {
        assertEquals(
            listOf(Placeholder(1, STRING, "%s"), Placeholder(2, INTEGER, "%d"), Placeholder(3, FLOAT, "%f"), Placeholder(4, FLOAT, "%.2f")),
            Placeholders.extract("%s has %d items at %f or %.2f"),
        )
        assertEquals(listOf(2 to INTEGER, 1 to STRING), signature("%2\$d by %1\$s"))
    }

    @Test
    fun `reads iOS placeholders`() {
        assertEquals(
            listOf(1 to STRING, 2 to INTEGER, 3 to INTEGER, 4 to INTEGER, 5 to INTEGER, 6 to FLOAT),
            signature("%@ %d %ld %lld %u %f"),
        )
        assertEquals(listOf(Placeholder(1, STRING, "%1$@")), Placeholders.extract("Hi %1\$@"))
    }

    @Test
    fun `skips literal percent signs`() {
        assertEquals(emptyList(), signature("100%% sure"))
        assertEquals(listOf(1 to INTEGER), signature("%d%%"))
        assertEquals(listOf(1 to STRING), signature("%%%s"))
    }

    @Test
    fun `leaves a percent sign that starts no placeholder as text`() {
        assertEquals(emptyList(), signature("50% off"))
        assertEquals(emptyList(), signature("100% sure"))
        assertEquals(emptyList(), signature("Save 20%"))
    }

    @Test
    fun `numbers placeholders without an index in order among themselves`() {
        assertEquals(listOf(2 to STRING, 1 to INTEGER), signature("%2\$s %d"))
    }

    @Test
    fun `reads flags width and precision`() {
        assertEquals(listOf(1 to INTEGER, 2 to FLOAT, 3 to INTEGER), signature("%05d %-8.3f %+d"))
    }

    @Test
    fun `treats string and object placeholders as the same type`() {
        assertNull(Placeholders.mismatch(Placeholders.extract("%s"), Placeholders.extract("%@")))
        assertNull(Placeholders.mismatch(Placeholders.extract("%1\$s %2\$d"), Placeholders.extract("%@ %ld")))
        assertNull(Placeholders.mismatch(Placeholders.extract("%d"), Placeholders.extract("%lld")))
    }

    @Test
    fun `matches reordered placeholders by position`() {
        assertNull(Placeholders.mismatch(Placeholders.extract("%1\$s %2\$d"), Placeholders.extract("%2\$ld %1\$@")))
    }

    @Test
    fun `counts a position used twice once`() {
        assertNull(Placeholders.mismatch(Placeholders.extract("%1\$s and %1\$s"), Placeholders.extract("%@")))
    }

    @Test
    fun `explains why placeholders differ`() {
        assertEquals("count differs", Placeholders.mismatch(Placeholders.extract("%s"), Placeholders.extract("%@ %d")))
        assertEquals("types differ", Placeholders.mismatch(Placeholders.extract("%d"), Placeholders.extract("%@")))
        assertEquals("types differ", Placeholders.mismatch(Placeholders.extract("%d"), Placeholders.extract("%f")))
        assertEquals("positions differ", Placeholders.mismatch(Placeholders.extract("%2\$s"), Placeholders.extract("%@")))
        assertEquals("count differs", Placeholders.mismatch(Placeholders.extract("Hello"), Placeholders.extract("Hello %@")))
    }
}
