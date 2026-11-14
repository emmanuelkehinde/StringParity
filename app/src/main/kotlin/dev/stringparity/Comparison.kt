package dev.stringparity

/**
 * Keys that one locale has on one platform and not on the other.
 *
 * @property locale the locale tag, such as `fr`
 * @property missingOnIos keys translated on Android and missing from the iOS catalog
 * @property missingOnAndroid keys translated in the iOS catalog and missing on Android
 */
data class LocaleGap(
    val locale: String,
    val missingOnIos: List<String>,
    val missingOnAndroid: List<String>,
)

/** The platform a string file belongs to. */
enum class Platform(val label: String) {
    ANDROID("Android"),
    IOS("iOS"),
}

/**
 * A key whose placeholders differ between Android and iOS in one locale.
 *
 * @property key the string key
 * @property locale the locale tag, or [LocaleStrings.DEFAULT_LOCALE]
 * @property reason why they differ, such as `count differs`
 * @property android the placeholders in the Android value
 * @property ios the placeholders in the iOS value
 */
data class PlaceholderMismatch(
    val key: String,
    val locale: String,
    val reason: String,
    val android: List<Placeholder>,
    val ios: List<Placeholder>,
)

/**
 * A translation whose placeholders differ from the default value on the same platform.
 *
 * @property key the string key
 * @property locale the locale tag of the translation
 * @property platform the platform both values come from
 * @property reason why they differ, such as `types differ`
 * @property translated the placeholders in the translation
 * @property default the placeholders in the default value
 */
data class PlaceholderDrift(
    val key: String,
    val locale: String,
    val platform: Platform,
    val reason: String,
    val translated: List<Placeholder>,
    val default: List<Placeholder>,
)

/**
 * The result of comparing Android strings with an iOS String Catalog.
 *
 * @property missingOnIos default-locale keys that only Android has
 * @property missingOnAndroid default-locale keys that only iOS has
 * @property onlyOnAndroid locales that only Android has, with their string counts
 * @property onlyOnIos locales that only iOS has, with their string counts
 * @property gaps locales on both platforms whose translated keys differ
 * @property placeholderMismatches keys whose placeholders differ between the platforms
 * @property placeholderDrift translations whose placeholders differ from their platform's default
 */
data class ParityReport(
    val missingOnIos: List<String>,
    val missingOnAndroid: List<String>,
    val onlyOnAndroid: Map<String, Int>,
    val onlyOnIos: Map<String, Int>,
    val gaps: List<LocaleGap>,
    val placeholderMismatches: List<PlaceholderMismatch> = emptyList(),
    val placeholderDrift: List<PlaceholderDrift> = emptyList(),
) {
    /** Each missing key and each locale that one platform lacks counts as one item. */
    val missingCount: Int
        get() = missingOnIos.size + missingOnAndroid.size + onlyOnAndroid.size + onlyOnIos.size +
            gaps.sumOf { it.missingOnIos.size + it.missingOnAndroid.size }

    /** Each placeholder mismatch and each drifted translation counts as one item. */
    val placeholderCount: Int
        get() = placeholderMismatches.size + placeholderDrift.size

    /** All items that make the check fail. */
    val problemCount: Int
        get() = missingCount + placeholderCount
}

/** Compares the keys and placeholders of both platforms, in the default locale and per locale. */
object ParityChecker {
    /**
     * Compares [android] with [ios].
     *
     * Keys that are not translatable on the platform that has them are left out, because
     * those are often platform-only values such as API keys.
     *
     * Per locale, only keys that both default locales have and that are translatable on both
     * are compared. A key missing from a locale on both platforms is not reported.
     *
     * The iOS source language is listed as [LocaleStrings.DEFAULT_LOCALE]. If Android also has
     * a folder for that language, such as `values-en`, it is compared with the iOS source strings.
     *
     * Placeholders are compared for each shared key in the default locale and in each locale
     * where both platforms translate it. Each translation is also compared with the default value
     * on its own platform, for every translatable default key.
     */
    fun compare(android: List<LocaleStrings>, ios: List<LocaleStrings>, iosSourceLanguage: String? = null): ParityReport {
        val androidDefault = android.defaultLocale()
        val iosDefault = ios.defaultLocale()
        val androidKeys = androidDefault.translatableKeys()
        val iosKeys = iosDefault.translatableKeys()
        val shared = androidKeys intersect iosKeys

        val androidByTag = android.filter { it.locale != LocaleStrings.DEFAULT_LOCALE }.associateBy { it.locale }
        val iosByTag = ios.filter { it.locale != LocaleStrings.DEFAULT_LOCALE }.associateBy { it.locale }
        fun iosLocale(tag: String) = iosByTag[tag] ?: iosDefault.takeIf { tag == iosSourceLanguage }

        val onlyOnAndroid = sortedMapOf<String, Int>()
        val onlyOnIos = sortedMapOf<String, Int>()
        val gaps = mutableListOf<LocaleGap>()
        val mismatches = mutableListOf<PlaceholderMismatch>()
        fun comparePlaceholders(tag: String, keys: Collection<String>, a: LocaleStrings, i: LocaleStrings) {
            for (key in keys.sorted()) {
                val aPlaceholders = Placeholders.extract(a[key]!!.value)
                val iPlaceholders = Placeholders.extract(i[key]!!.value)
                val reason = Placeholders.mismatch(aPlaceholders, iPlaceholders) ?: continue
                mismatches += PlaceholderMismatch(key, tag, reason, aPlaceholders, iPlaceholders)
            }
        }
        comparePlaceholders(LocaleStrings.DEFAULT_LOCALE, shared, androidDefault, iosDefault)

        for (tag in (androidByTag.keys + iosByTag.keys).sorted()) {
            val a = androidByTag[tag]
            val i = iosLocale(tag)
            when {
                a == null -> onlyOnIos[tag] = i!!.entries.size
                i == null -> onlyOnAndroid[tag] = a.entries.size
                else -> {
                    val aKeys = a.entries.map { it.key }.toSet() intersect shared
                    val iKeys = i.entries.map { it.key }.toSet() intersect shared
                    val gap = LocaleGap(tag, (aKeys - iKeys).sorted(), (iKeys - aKeys).sorted())
                    if (gap.missingOnIos.isNotEmpty() || gap.missingOnAndroid.isNotEmpty()) gaps += gap
                    comparePlaceholders(tag, aKeys intersect iKeys, a, i)
                }
            }
        }

        return ParityReport(
            missingOnIos = (androidKeys - iosDefault.allKeys()).sorted(),
            missingOnAndroid = (iosKeys - androidDefault.allKeys()).sorted(),
            onlyOnAndroid = onlyOnAndroid,
            onlyOnIos = onlyOnIos,
            gaps = gaps,
            placeholderMismatches = mismatches,
            placeholderDrift = drift(Platform.ANDROID, androidDefault, androidByTag.values) +
                drift(Platform.IOS, iosDefault, iosByTag.values),
        )
    }

    private fun drift(platform: Platform, default: LocaleStrings, locales: Collection<LocaleStrings>): List<PlaceholderDrift> {
        val keys = default.translatableKeys()
        val result = mutableListOf<PlaceholderDrift>()
        for (locale in locales.sortedBy { it.locale }) {
            for (key in (locale.allKeys() intersect keys).sorted()) {
                val defaultPlaceholders = Placeholders.extract(default[key]!!.value)
                val translated = Placeholders.extract(locale[key]!!.value)
                val reason = Placeholders.mismatch(translated, defaultPlaceholders) ?: continue
                result += PlaceholderDrift(key, locale.locale, platform, reason, translated, defaultPlaceholders)
            }
        }
        return result
    }

    private fun List<LocaleStrings>.defaultLocale() =
        firstOrNull { it.locale == LocaleStrings.DEFAULT_LOCALE } ?: LocaleStrings(LocaleStrings.DEFAULT_LOCALE, emptyList())

    private fun LocaleStrings.allKeys() = entries.map { it.key }.toSet()

    // A duplicate key follows LocaleStrings.get: the last entry decides.
    private fun LocaleStrings.translatableKeys() = allKeys().filter { this[it]!!.translatable }.toSet()
}

/** Formats [report] as plain text, one key per line. */
fun ParityReport.toText(): String = buildString {
    fun keys(title: String, list: List<String>, indent: String) {
        if (list.isEmpty()) return
        appendLine("$indent$title (${list.size}):")
        list.forEach { appendLine("$indent  $it") }
    }
    fun strings(n: Int) = if (n == 1) "1 string" else "$n strings"

    keys("Missing on iOS", missingOnIos, "")
    keys("Missing on Android", missingOnAndroid, "")
    for ((tag, count) in onlyOnAndroid) appendLine("Locale $tag is missing on iOS (${strings(count)} on Android)")
    for ((tag, count) in onlyOnIos) appendLine("Locale $tag is missing on Android (${strings(count)} on iOS)")
    for (gap in gaps) {
        appendLine("Locale ${gap.locale}:")
        keys("Missing on iOS", gap.missingOnIos, "  ")
        keys("Missing on Android", gap.missingOnAndroid, "  ")
    }

    fun texts(list: List<Placeholder>) = if (list.isEmpty()) "none" else list.joinToString(" ") { it.text }
    if (placeholderMismatches.isNotEmpty()) {
        appendLine("Placeholder mismatches (${placeholderMismatches.size}):")
        for (m in placeholderMismatches) {
            appendLine("  ${m.key} in ${m.locale}, ${m.reason}: Android ${texts(m.android)}, iOS ${texts(m.ios)}")
        }
    }
    if (placeholderDrift.isNotEmpty()) {
        appendLine("Placeholders that differ from the default (${placeholderDrift.size}):")
        for (d in placeholderDrift) {
            appendLine("  ${d.key} in ${d.locale} on ${d.platform.label}, ${d.reason}: ${texts(d.translated)}, default ${texts(d.default)}")
        }
    }

    val parts = buildList {
        if (missingCount > 0) add("$missingCount missing ${if (missingCount == 1) "item" else "items"}")
        if (placeholderCount > 0) add("$placeholderCount placeholder ${if (placeholderCount == 1) "mismatch" else "mismatches"}")
    }
    append(if (parts.isEmpty()) "No missing keys or placeholder mismatches." else "Found ${parts.joinToString(" and ")}.")
}
