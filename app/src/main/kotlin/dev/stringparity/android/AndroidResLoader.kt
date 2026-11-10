package dev.stringparity.android

import dev.stringparity.LocaleStrings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Loads Android strings from a `res` directory or from one `strings.xml` file.
 *
 * In a `res` directory, `values/strings.xml` is the default locale and each
 * `values-<qualifier>/strings.xml` is another locale. Folders without a
 * `strings.xml` are skipped. So are folders whose qualifier is not a locale,
 * such as `values-night` or `values-sw600dp`.
 */
object AndroidResLoader {
    private val legacyQualifier = Regex("([a-z]{2,3})(?:-r([A-Z]{2}|[0-9]{3}))?")
    private val bcp47Qualifier = Regex("b\\+[A-Za-z0-9]+(?:\\+[A-Za-z0-9]+)*")

    // Two and three letter qualifiers that look like languages but are UI modes.
    private val notLanguages = setOf("tv", "car")

    /**
     * Loads [path]. The result is sorted with the default locale first, then by locale tag.
     * A single file gets the locale of its parent `values*` folder, or the default locale
     * if the parent isn't a locale folder.
     */
    fun load(path: Path): List<LocaleStrings> {
        if (!path.exists()) throw StringsParseException("Path does not exist: $path")
        if (path.isRegularFile()) return listOf(loadFile(path, localeOfFolder(path.toAbsolutePath().parent?.name)))

        val found = Files.list(path).use { stream ->
            stream.filter { it.isDirectory() && it.resolve("strings.xml").isRegularFile() }
                .toList()
                .mapNotNull { dir -> localeOfFolder(dir.name)?.let { locale -> locale to dir.resolve("strings.xml") } }
        }
        return found
            .sortedWith(compareBy({ it.first != LocaleStrings.DEFAULT_LOCALE }, { it.first }))
            .map { (locale, file) -> loadFile(file, locale) }
    }

    /**
     * Maps a resource folder name to a locale tag, or null if it isn't a locale folder.
     *
     * `values` is the default, `values-fr` is `fr`, `values-fr-rCA` is `fr-CA`
     * and `values-b+sr+Latn` is `sr-Latn`.
     */
    fun localeOfFolder(folder: String?): String? {
        if (folder == null) return null
        if (folder == "values") return LocaleStrings.DEFAULT_LOCALE
        val qualifier = folder.removePrefix("values-").takeIf { folder.startsWith("values-") } ?: return null
        if (bcp47Qualifier.matches(qualifier)) return qualifier.removePrefix("b+").replace('+', '-')
        val match = legacyQualifier.matchEntire(qualifier) ?: return null
        val (language, region) = match.destructured
        if (language in notLanguages) return null
        return if (region.isEmpty()) language else "$language-$region"
    }

    private fun loadFile(file: Path, locale: String?) =
        LocaleStrings(locale ?: LocaleStrings.DEFAULT_LOCALE, AndroidStringsParser.parse(file))
}
