package com.simpleandroid.ericardscollector.data

/**
 * Minimal JSON reader/writer.
 *
 * Objects become LinkedHashMap<String, Any?> (key order is preserved, which matters for the
 * "sections" of an ERICard), arrays become ArrayList<Any?>, numbers become Long or Double.
 * It mirrors Python's json module closely, so the data code can stay close to the desktop program
 * and the app needs no third-party JSON library.
 */
class JsonParseException(message: String) : Exception(message)

object Json {
    fun parse(text: String): Any? {
        val parser = Parser(text.removePrefix("\uFEFF"))
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) parser.fail("Extra data")
        return value
    }

    fun stringify(value: Any?, indent: Int = 2): String {
        val out = StringBuilder()
        write(out, value, indent, 0)
        return out.toString()
    }

    // --- Writing ---
    private fun write(out: StringBuilder, value: Any?, indent: Int, level: Int) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long -> out.append(value.toString())
            is Double -> out.append(value.toString())
            is Number -> out.append(value.toString())
            is Map<*, *> -> {
                if (value.isEmpty()) { out.append("{}"); return }
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    newline(out, indent, level + 1)
                    writeString(out, key.toString())
                    out.append(": ")
                    write(out, item, indent, level + 1)
                }
                newline(out, indent, level)
                out.append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) { out.append("[]"); return }
                out.append('[')
                var first = true
                for (item in items) {
                    if (!first) out.append(',')
                    first = false
                    newline(out, indent, level + 1)
                    write(out, item, indent, level + 1)
                }
                newline(out, indent, level)
                out.append(']')
            }
            else -> writeString(out, value.toString())
        }
    }

    private fun newline(out: StringBuilder, indent: Int, level: Int) {
        out.append('\n')
        repeat(indent * level) { out.append(' ') }
    }

    private fun writeString(out: StringBuilder, text: String) {
        out.append('"')
        for (char in text) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
            }
        }
        out.append('"')
    }

    // --- Reading ---
    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd() = pos >= text.length

        fun fail(message: String): Nothing = throw JsonParseException("$message: char $pos")

        fun skipWhitespace() {
            while (pos < text.length && (text[pos] == ' ' || text[pos] == '\n' || text[pos] == '\r' || text[pos] == '\t')) pos++
        }

        fun readValue(): Any? {
            if (atEnd()) fail("Expecting value")
            return when (val char = text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (char == '-' || char.isDigit()) readNumber() else fail("Expecting value")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) fail("Expecting value")
            pos += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWhitespace()
            if (pos < text.length && text[pos] == '}') { pos++; return result }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[pos] != '"') fail("Expecting property name enclosed in double quotes")
                val key = readString()
                skipWhitespace()
                if (atEnd() || text[pos] != ':') fail("Expecting ':' delimiter")
                pos++
                skipWhitespace()
                result[key] = readValue()
                skipWhitespace()
                if (atEnd()) fail("Expecting ',' delimiter")
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return result }
                    else -> fail("Expecting ',' delimiter")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val result = ArrayList<Any?>()
            pos++ // [
            skipWhitespace()
            if (pos < text.length && text[pos] == ']') { pos++; return result }
            while (true) {
                skipWhitespace()
                result.add(readValue())
                skipWhitespace()
                if (atEnd()) fail("Expecting ',' delimiter")
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return result }
                    else -> fail("Expecting ',' delimiter")
                }
            }
        }

        private fun readString(): String {
            pos++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string starting at")
                val char = text[pos]
                when {
                    char == '"' -> { pos++; return out.toString() }
                    char == '\\' -> {
                        pos++
                        if (atEnd()) fail("Unterminated string starting at")
                        when (val escape = text[pos]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 >= text.length) fail("Invalid \\uXXXX escape")
                                val code = text.substring(pos + 1, pos + 5).toIntOrNull(16) ?: fail("Invalid \\uXXXX escape")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("Invalid \\escape '$escape'")
                        }
                        pos++
                    }
                    char < ' ' -> fail("Invalid control character")
                    else -> { out.append(char); pos++ }
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (text[pos] == '-') pos++
            while (pos < text.length && text[pos].isDigit()) pos++
            var isFloat = false
            if (pos < text.length && text[pos] == '.') {
                isFloat = true
                pos++
                while (pos < text.length && text[pos].isDigit()) pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                while (pos < text.length && text[pos].isDigit()) pos++
            }
            val token = text.substring(start, pos)
            if (token == "-") fail("Expecting value")
            return if (isFloat) token.toDouble() else (token.toLongOrNull() ?: token.toDouble())
        }
    }
}
