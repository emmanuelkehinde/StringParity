package dev.stringparity

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import java.nio.file.Path
import kotlin.io.path.readText

/** Thrown when a mapping file can't be read or isn't valid. The message names the file. */
class MappingParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Android keys that name the same string as an iOS key.
 *
 * @property androidToIos iOS key for each Android key that is named differently
 */
data class KeyMapping(val androidToIos: Map<String, String>) {
    /** Returns [android] with each mapped key renamed to its iOS key. Other keys are unchanged. */
    fun apply(android: List<LocaleStrings>): List<LocaleStrings> = android.map { locale ->
        locale.copy(entries = locale.entries.map { it.copy(key = androidToIos[it.key] ?: it.key) })
    }

    companion object {
        val EMPTY = KeyMapping(emptyMap())

        /**
         * Reads a mapping file. It has one top-level `keys` entry, which maps each Android key
         * to the iOS key for the same string:
         *
         * ```yaml
         * keys:
         *   login_title: Log in
         * ```
         */
        fun parse(path: Path): KeyMapping {
            val root = try {
                Load(LoadSettings.builder().build()).loadFromString(path.readText())
            } catch (e: YamlEngineException) {
                throw MappingParseException("Invalid mapping file $path: ${e.message?.lineSequence()?.firstOrNull()}", e)
            } catch (e: java.io.IOException) {
                throw MappingParseException("Can't read mapping file $path: ${e.message}", e)
            }
            fun invalid(reason: String): Nothing = throw MappingParseException("Invalid mapping file $path: $reason")

            val top = root as? Map<*, *> ?: invalid("expected a top-level map with a 'keys' entry")
            val unknown = top.keys.filter { it != "keys" }
            if (unknown.isNotEmpty()) invalid("unknown top-level entry '${unknown.first()}', only 'keys' is allowed")
            val keys = top["keys"] as? Map<*, *> ?: invalid("'keys' must map Android keys to iOS keys")

            val result = linkedMapOf<String, String>()
            for ((k, v) in keys) {
                if (k !is String || v !is String || k.isEmpty() || v.isEmpty()) {
                    invalid("entry '$k' must map an Android key to an iOS key, both as text")
                }
                result[k] = v
            }
            val duplicate = result.values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
            if (duplicate != null) invalid("iOS key '${duplicate.key}' is the target of more than one Android key")
            return KeyMapping(result)
        }
    }
}
