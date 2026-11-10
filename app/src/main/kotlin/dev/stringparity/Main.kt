package dev.stringparity

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import dev.stringparity.android.AndroidResLoader
import dev.stringparity.android.StringsParseException

class StringParity : CliktCommand(name = "stringparity") {
    override fun help(context: Context) =
        "Compare Android strings.xml files with an iOS String Catalog (.xcstrings) and report differences."

    val android by option("--android", help = "Path to the Android res directory or a strings.xml file")
        .path(mustExist = true)
        .required()

    val ios by option("--ios", help = "Path to the iOS .xcstrings String Catalog")
        .path(mustExist = true, canBeDir = false)
        .required()

    override fun run() {
        val locales = try {
            AndroidResLoader.load(android)
        } catch (e: StringsParseException) {
            throw CliktError(e.message)
        }
        if (locales.isEmpty()) throw CliktError("No strings.xml files found in $android")

        echo("Android strings per locale:")
        for (locale in locales) {
            val skipped = locale.entries.count { !it.translatable }
            val note = if (skipped > 0) " ($skipped not translatable)" else ""
            echo("  ${locale.locale}: ${locale.entries.size}$note")
        }
        echo("iOS: $ios")
        echo("Comparison isn't implemented yet.")
    }
}

fun main(args: Array<String>) = StringParity().main(args)
