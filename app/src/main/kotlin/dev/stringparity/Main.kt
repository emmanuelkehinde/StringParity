package dev.stringparity

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path

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
        echo("Android: $android")
        echo("iOS: $ios")
        echo("Comparison isn't implemented yet.")
    }
}

fun main(args: Array<String>) = StringParity().main(args)
