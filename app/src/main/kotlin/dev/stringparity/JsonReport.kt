package dev.stringparity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val prettyJson = Json { prettyPrint = true }

/**
 * Formats [report] as one JSON object with the same findings as [toText].
 *
 * Fields always appear in the same order and lists are never left out, so an empty list
 * means nothing was found. Keys and locales are sorted. Placeholder lists keep the order
 * of [ParityReport.placeholderMismatches] and [ParityReport.placeholderDrift], which is the
 * default locale first, then locales and keys in sorted order.
 */
fun ParityReport.toJson(): String = prettyJson.encodeToString(JsonObject.serializer(), toJsonObject())

/** Builds the object that [toJson] prints. */
fun ParityReport.toJsonObject(): JsonObject = buildJsonObject {
    fun strings(list: List<String>) = JsonArray(list.sorted().map(::JsonPrimitive))
    fun placeholders(list: List<Placeholder>) = JsonArray(list.map { JsonPrimitive(it.text) })
    fun locales(counts: Map<String, Int>) = buildJsonArray {
        for ((tag, count) in counts.toSortedMap()) {
            add(buildJsonObject {
                put("locale", tag)
                put("strings", count)
            })
        }
    }

    put("missingKeys", buildJsonObject {
        put("android", strings(missingOnAndroid))
        put("ios", strings(missingOnIos))
    })
    put("missingLocales", buildJsonObject {
        put("android", locales(onlyOnIos))
        put("ios", locales(onlyOnAndroid))
    })
    put("localeGaps", buildJsonArray {
        for (gap in gaps.sortedBy { it.locale }) {
            add(buildJsonObject {
                put("locale", gap.locale)
                put("missingOnAndroid", strings(gap.missingOnAndroid))
                put("missingOnIos", strings(gap.missingOnIos))
            })
        }
    })
    put("placeholderMismatches", buildJsonArray {
        for (m in placeholderMismatches) {
            add(buildJsonObject {
                put("key", m.key)
                put("locale", m.locale)
                put("reason", m.reason)
                put("android", placeholders(m.android))
                put("ios", placeholders(m.ios))
            })
        }
    })
    put("placeholderDrift", buildJsonArray {
        for (d in placeholderDrift) {
            add(buildJsonObject {
                put("key", d.key)
                put("locale", d.locale)
                put("platform", d.platform.name.lowercase())
                put("reason", d.reason)
                put("translated", placeholders(d.translated))
                put("default", placeholders(d.default))
            })
        }
    })
    put("summary", buildJsonObject {
        put("missing", missingCount)
        put("placeholderMismatches", placeholderCount)
        put("problems", problemCount)
    })
}
