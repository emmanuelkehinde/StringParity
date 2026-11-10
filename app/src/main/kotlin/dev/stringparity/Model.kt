package dev.stringparity

/**
 * One string resource.
 *
 * @property key the resource name, such as `app_name`
 * @property value the text after Android's unescaping rules have been applied
 * @property translatable false when the source marks it `translatable="false"`
 */
data class StringEntry(
    val key: String,
    val value: String,
    val translatable: Boolean = true,
)

/**
 * All strings for one locale of one platform.
 *
 * @property locale a BCP 47 style tag such as `fr` or `fr-CA`, or [DEFAULT_LOCALE] for the default resources
 * @property entries the strings in file order
 */
data class LocaleStrings(
    val locale: String,
    val entries: List<StringEntry>,
) {
    /** Returns the entry for [key], or null. If a key appears twice, the last one wins. */
    operator fun get(key: String): StringEntry? = entries.lastOrNull { it.key == key }

    companion object {
        const val DEFAULT_LOCALE = "default"
    }
}
