package dev.stringparity.android

import dev.stringparity.StringEntry
import org.w3c.dom.CDATASection
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.inputStream

/** Thrown when a strings.xml file can't be read or isn't valid. The message names the file. */
class StringsParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses one Android `strings.xml` file with the JDK XML parser.
 *
 * Only `<string>` elements directly under `<resources>` are read. Other elements,
 * such as `<plurals>` and `<string-array>`, are ignored for now. Markup inside a
 * string, such as `<b>` or `<xliff:g>`, is dropped and its text is kept.
 */
object AndroidStringsParser {
    /** Parses the file at [path]. */
    fun parse(path: Path): List<StringEntry> {
        val input = try {
            path.inputStream()
        } catch (e: java.io.IOException) {
            throw StringsParseException("Can't read $path: ${e.message}", e)
        }
        return input.use { parseSource(InputSource(it), path.toString()) }
    }

    /** Parses [xml] text. [source] only appears in error messages. */
    fun parse(xml: String, source: String = "strings.xml"): List<StringEntry> =
        parseSource(InputSource(StringReader(xml)), source)

    private fun parseSource(input: InputSource, source: String): List<StringEntry> {
        val root = try {
            newBuilderFactory().newDocumentBuilder().apply {
                // Throw instead of printing "[Fatal Error]" to stderr.
                setErrorHandler(ThrowingErrorHandler)
            }.parse(input).documentElement
        } catch (e: SAXParseException) {
            throw StringsParseException("Malformed XML in $source at line ${e.lineNumber}: ${e.message}", e)
        } catch (e: org.xml.sax.SAXException) {
            throw StringsParseException("Malformed XML in $source: ${e.message}", e)
        } catch (e: java.io.IOException) {
            throw StringsParseException("Can't read $source: ${e.message}", e)
        }

        if (root.tagName != "resources") {
            throw StringsParseException("$source: expected a <resources> root element but found <${root.tagName}>")
        }

        val entries = mutableListOf<StringEntry>()
        var child = root.firstChild
        while (child != null) {
            if (child is Element && child.tagName == "string") {
                val name = child.getAttribute("name")
                if (name.isEmpty()) throw StringsParseException("$source: a <string> element has no name attribute")
                entries += StringEntry(
                    key = name,
                    value = AndroidText.decode(child),
                    translatable = child.getAttribute("translatable") != "false",
                )
            }
            child = child.nextSibling
        }
        return entries
    }

    private fun newBuilderFactory() = DocumentBuilderFactory.newInstance().apply {
        // Internal entities declared in a DOCTYPE still work. External ones are never fetched.
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = true
    }

    private object ThrowingErrorHandler : ErrorHandler {
        override fun warning(e: SAXParseException) {}
        override fun error(e: SAXParseException) = throw e
        override fun fatalError(e: SAXParseException) = throw e
    }
}

/** Applies Android's rules for turning the text of a `<string>` into its runtime value. */
internal object AndroidText {
    /**
     * Decodes the text inside [element].
     *
     * Outside double quotes, runs of whitespace become one space and the ends are trimmed.
     * Inside double quotes, whitespace is kept and the quotes are removed. A backslash
     * starts an escape: `\n`, `\t`, `\'`, `\"`, `\@`, `\?`, `\\` and `\uXXXX`.
     * CDATA text is copied as is.
     */
    fun decode(element: Element): String {
        val state = Decoder()
        state.visit(element)
        return state.result()
    }

    private class Decoder {
        private val out = StringBuilder()
        private var inQuotes = false
        private var pendingSpace = false

        fun result(): String = out.toString()

        fun visit(node: Node) {
            var child = node.firstChild
            while (child != null) {
                when {
                    child is CDATASection -> appendLiteral(child.data)
                    child.nodeType == Node.TEXT_NODE -> text(child.nodeValue)
                    child.nodeType == Node.ELEMENT_NODE -> visit(child)
                }
                child = child.nextSibling
            }
        }

        private fun appendLiteral(s: String) {
            flushSpace()
            out.append(s)
        }

        private fun flushSpace() {
            if (pendingSpace) out.append(' ')
            pendingSpace = false
        }

        private fun text(s: String) {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' -> {
                        i++
                        if (i >= s.length) break // a lone trailing backslash is dropped
                        i = escape(s, i)
                        continue
                    }
                    c == '"' -> inQuotes = !inQuotes
                    c.isWhitespace() && !inQuotes -> if (out.isNotEmpty()) pendingSpace = true
                    else -> appendLiteral(c.toString())
                }
                i++
            }
        }

        /** Handles the escape whose letter is at [i] and returns the index after it. */
        private fun escape(s: String, i: Int): Int {
            when (val c = s[i]) {
                'n' -> appendLiteral("\n")
                't' -> appendLiteral("\t")
                'u' -> {
                    val hex = s.substring(i + 1, minOf(i + 5, s.length))
                    if (hex.length != 4 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        throw StringsParseException("Invalid unicode escape \\u$hex")
                    }
                    appendLiteral(hex.toInt(16).toChar().toString())
                    return i + 5
                }
                // \' \" \@ \? \\ and any other escaped character stand for the character itself.
                else -> appendLiteral(c.toString())
            }
            return i + 1
        }
    }
}
