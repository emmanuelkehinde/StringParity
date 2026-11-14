package dev.stringparity

/** The kind of argument a placeholder takes. iOS `%@` and Android `%s` are both [STRING]. */
enum class PlaceholderType {
    STRING,
    INTEGER,
    FLOAT,
}

/**
 * One format placeholder in a string value.
 *
 * @property position the 1-based argument index: the explicit index in `%2$d`, or the order of
 *   appearance among placeholders without one
 * @property type the kind of argument it takes
 * @property text the placeholder as written, such as `%1$@` or `%.2f`
 */
data class Placeholder(
    val position: Int,
    val type: PlaceholderType,
    val text: String,
)

/** Finds the format placeholders in Android and iOS string values and compares them. */
object Placeholders {
    // Argument index, flags, width, precision, length modifier, conversion. A space or `'` flag is
    // not accepted, so plain text such as "50% off" is not read as a placeholder.
    private val pattern = Regex("""%(?:%|(?:(\d+)\$)?[-+#0]*(?:\d+)?(?:\.\d+)?(?:hh|h|ll|l|q|z|t|j|L)?([sS@diuxXofFeEgGaA]))""")

    /**
     * Returns the placeholders in [value] in order of appearance.
     *
     * `%%` is a literal percent sign and is skipped. Placeholders without an index are numbered
     * in order among themselves, as Java's formatter does, so `%2$s %d` uses arguments 2 and 1.
     * A `%` that does not start a known placeholder is left as text.
     */
    fun extract(value: String): List<Placeholder> {
        var next = 1
        return pattern.findAll(value).mapNotNull { match ->
            val conversion = match.groupValues[2]
            if (conversion.isEmpty()) return@mapNotNull null
            val position = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt() ?: next++
            Placeholder(position, typeOf(conversion.single()), match.value)
        }.toList()
    }

    /**
     * Returns why [first] and [second] don't match, or null when they take the same arguments.
     *
     * Two lists match when they use the same argument positions with the same types. The same
     * position used twice counts once, so `%1$s and %1$s` matches `%@`.
     */
    fun mismatch(first: List<Placeholder>, second: List<Placeholder>): String? {
        val a = first.map { it.position to it.type }.toSet()
        val b = second.map { it.position to it.type }.toSet()
        if (a == b) return null
        val aPositions = a.map { it.first }.toSet()
        val bPositions = b.map { it.first }.toSet()
        return when {
            aPositions.size != bPositions.size -> "count differs"
            aPositions != bPositions -> "positions differ"
            else -> "types differ"
        }
    }

    private fun typeOf(conversion: Char) = when (conversion) {
        's', 'S', '@' -> PlaceholderType.STRING
        'd', 'i', 'u', 'x', 'X', 'o' -> PlaceholderType.INTEGER
        else -> PlaceholderType.FLOAT
    }
}
