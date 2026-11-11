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

/**
 * The result of comparing Android strings with an iOS String Catalog.
 *
 * @property missingOnIos default-locale keys that only Android has
 * @property missingOnAndroid default-locale keys that only iOS has
 * @property onlyOnAndroid locales that only Android has, with their string counts
 * @property onlyOnIos locales that only iOS has, with their string counts
 * @property gaps locales on both platforms whose translated keys differ
 */
data class ParityReport(
    val missingOnIos: List<String>,
    val missingOnAndroid: List<String>,
    val onlyOnAndroid: Map<String, Int>,
    val onlyOnIos: Map<String, Int>,
    val gaps: List<LocaleGap>,
) {
    /** Each missing key and each locale that one platform lacks counts as one problem. */
    val problemCount: Int
        get() = missingOnIos.size + missingOnAndroid.size + onlyOnAndroid.size + onlyOnIos.size +
            gaps.sumOf { it.missingOnIos.size + it.missingOnAndroid.size }
}

/** Compares the keys of both platforms, in the default locale and per locale. */
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
                }
            }
        }

        return ParityReport(
            missingOnIos = (androidKeys - iosDefault.allKeys()).sorted(),
            missingOnAndroid = (iosKeys - androidDefault.allKeys()).sorted(),
            onlyOnAndroid = onlyOnAndroid,
            onlyOnIos = onlyOnIos,
            gaps = gaps,
        )
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
    val n = problemCount
    append(if (n == 0) "No missing keys." else "Found $n missing ${if (n == 1) "item" else "items"}.")
}
