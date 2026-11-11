package dev.stringparity.ios

import dev.stringparity.LocaleStrings
import dev.stringparity.StringEntry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.nio.file.Path
import kotlin.io.path.readText

/** Thrown when a String Catalog can't be read or isn't valid. The message names the file. */
class CatalogParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The strings of one iOS String Catalog.
 *
 * @property sourceLanguage the catalog's `sourceLanguage`, such as `en`
 * @property locales one entry per locale, with the source language as [LocaleStrings.DEFAULT_LOCALE]
 *   first and the others sorted by tag
 * @property variationKeys keys skipped in at least one locale because they use plural or device
 *   variations instead of a plain string
 */
data class StringCatalog(
    val sourceLanguage: String,
    val locales: List<LocaleStrings>,
    val variationKeys: Set<String> = emptySet(),
)

/**
 * Parses an iOS String Catalog (`.xcstrings`), which is JSON.
 *
 * Each key's `localizations` map holds one object per locale. A `stringUnit` gives the value.
 * A localization with only `variations` (plurals or devices) is skipped and its key is listed in
 * [StringCatalog.variationKeys], because the Android parser ignores `<plurals>` too.
 *
 * The source language becomes [LocaleStrings.DEFAULT_LOCALE] so it lines up with Android's
 * `values` folder. A key with no source localization uses the key as its source value, as Xcode does.
 * `"shouldTranslate": false` maps to [StringEntry.translatable] false.
 */
object XcstringsParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses the file at [path]. */
    fun parse(path: Path): StringCatalog {
        val text = try {
            path.readText()
        } catch (e: java.io.IOException) {
            throw CatalogParseException("Can't read $path: ${e.message}", e)
        }
        return parse(text, path.toString())
    }

    /** Parses [text]. [source] only appears in error messages. */
    fun parse(text: String, source: String = "Localizable.xcstrings"): StringCatalog {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw CatalogParseException("Malformed JSON in $source: ${e.message}", e)
        }
        if (root !is JsonObject) throw CatalogParseException("$source: expected a JSON object at the top level")

        val sourceLanguage = root.string("sourceLanguage", source)
            ?: throw CatalogParseException("$source: missing \"sourceLanguage\"")
        val sourceTag = normalizeTag(sourceLanguage)
        val strings = root["strings"]?.let { it as? JsonObject ?: throw CatalogParseException("$source: \"strings\" is not an object") }
            ?: JsonObject(emptyMap())

        val byLocale = linkedMapOf<String, MutableList<StringEntry>>(sourceTag to mutableListOf())
        val variationKeys = linkedSetOf<String>()

        for ((key, value) in strings) {
            val entry = value as? JsonObject ?: throw CatalogParseException("$source: the entry for \"$key\" is not an object")
            val translatable = (entry["shouldTranslate"] as? JsonPrimitive)?.booleanOrNull ?: true
            val localizations = entry["localizations"]?.let {
                it as? JsonObject ?: throw CatalogParseException("$source: \"localizations\" of \"$key\" is not an object")
            } ?: JsonObject(emptyMap())

            var hasSource = false
            for ((tag, localization) in localizations) {
                val locale = normalizeTag(tag)
                if (locale == sourceTag) hasSource = true
                val loc = localization as? JsonObject
                    ?: throw CatalogParseException("$source: localization \"$tag\" of \"$key\" is not an object")
                val unit = loc["stringUnit"] as? JsonObject
                if (unit == null) {
                    if (loc.containsKey("variations")) variationKeys += key
                    continue
                }
                val text = unit.string("value", source)
                    ?: throw CatalogParseException("$source: stringUnit for \"$key\" in \"$tag\" has no value")
                byLocale.getOrPut(locale) { mutableListOf() } += StringEntry(key, text, translatable)
            }
            if (!hasSource) byLocale.getValue(sourceTag) += StringEntry(key, key, translatable)
        }

        val locales = byLocale
            .map { (tag, entries) -> LocaleStrings(if (tag == sourceTag) LocaleStrings.DEFAULT_LOCALE else tag, entries) }
            .sortedWith(compareBy({ it.locale != LocaleStrings.DEFAULT_LOCALE }, { it.locale }))
        return StringCatalog(sourceTag, locales, variationKeys)
    }

    /** Uses `-` between subtags, so `pt_BR` and `pt-BR` are the same locale. */
    private fun normalizeTag(tag: String) = tag.replace('_', '-')

    private fun JsonObject.string(name: String, source: String): String? {
        val value: JsonElement = this[name] ?: return null
        if (value !is JsonPrimitive || !value.isString) throw CatalogParseException("$source: \"$name\" is not a string")
        return value.content
    }
}
