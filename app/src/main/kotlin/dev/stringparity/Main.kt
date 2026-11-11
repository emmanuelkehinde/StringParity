package dev.stringparity

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import dev.stringparity.android.AndroidResLoader
import dev.stringparity.android.StringsParseException
import dev.stringparity.ios.CatalogParseException
import dev.stringparity.ios.StringCatalog
import dev.stringparity.ios.XcstringsParser
import java.nio.file.Path

class StringParity : CliktCommand(name = "stringparity") {
    override fun help(context: Context) =
        "Compare Android strings.xml files with an iOS String Catalog (.xcstrings) and report differences."

    val android by option("--android", help = "Path to the Android res directory or a strings.xml file")
        .path(mustExist = true)

    val ios by option("--ios", help = "Path to the iOS .xcstrings String Catalog")
        .path(mustExist = true, canBeDir = false)

    override fun run() {
        if (android == null && ios == null) throw UsageError("Give --android, --ios or both.")
        android?.let { printCounts("Android strings per locale:", loadAndroid(it)) }
        ios?.let { path ->
            val catalog = loadIos(path)
            printCounts("iOS strings per locale (source language ${catalog.sourceLanguage}):", catalog.locales)
            if (catalog.variationKeys.isNotEmpty()) {
                val n = catalog.variationKeys.size
                echo("  Skipped $n ${if (n == 1) "key" else "keys"} with plural or device variations")
            }
        }
    }

    private fun loadAndroid(path: Path): List<LocaleStrings> {
        val locales = try {
            AndroidResLoader.load(path)
        } catch (e: StringsParseException) {
            throw CliktError(e.message)
        }
        if (locales.isEmpty()) throw CliktError("No strings.xml files found in $path")
        return locales
    }

    private fun loadIos(path: Path): StringCatalog = try {
        XcstringsParser.parse(path)
    } catch (e: CatalogParseException) {
        throw CliktError(e.message)
    }

    private fun printCounts(title: String, locales: List<LocaleStrings>) {
        echo(title)
        for (locale in locales) {
            val skipped = locale.entries.count { !it.translatable }
            val note = if (skipped > 0) " ($skipped not translatable)" else ""
            echo("  ${locale.locale}: ${locale.entries.size}$note")
        }
    }
}

fun main(args: Array<String>) = StringParity().main(args)
