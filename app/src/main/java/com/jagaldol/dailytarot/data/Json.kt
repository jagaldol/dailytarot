package com.jagaldol.dailytarot.data

/**
 * Minimal strict JSON reader/writer for the bundled catalog and user backups. Kept dependency-free
 * so the same code runs in JVM unit tests (android.jar's org.json is only a stub there).
 * Objects become [Map], arrays [List], numbers [Long] or [Double].
 */
internal object Json {
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWhitespace()
        require(parser.atEnd) { "Unexpected trailing JSON at ${parser.pos}" }
        return value
    }

    fun write(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Double -> {
                require(value.isFinite()) { "JSON numbers must be finite" }
                out.append(value.toString())
            }
            is String -> writeString(out, value)
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) out.append(',')
                    writeString(out, key as String)
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("Unsupported JSON value: ${value::class}")
        }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char < ' ') {
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        var pos = 0
        val atEnd get() = pos == text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos] in " \t\r\n") pos++
        }

        fun value(): Any? {
            skipWhitespace()
            require(!atEnd) { "Unexpected end of JSON" }
            return when (text[pos]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') return result.also { pos++ }
            while (true) {
                skipWhitespace()
                require(peek() == '"') { "Expected key at $pos" }
                val key = string()
                require(key !in result) { "Duplicate key $key" }
                skipWhitespace()
                expect(':')
                result[key] = value()
                skipWhitespace()
                if (peek() == ',') pos++ else return result.also { expect('}') }
            }
        }

        private fun array(): List<Any?> {
            pos++
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') return result.also { pos++ }
            while (true) {
                result += value()
                skipWhitespace()
                if (peek() == ',') pos++ else return result.also { expect(']') }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(!atEnd) { "Unterminated string" }
                when (val char = text[pos++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(!atEnd) { "Bad escape" }
                        when (val escaped = text[pos++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(pos + 4 <= text.length) { "Bad unicode escape" }
                                out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape \\$escaped")
                        }
                    }
                    else -> {
                        require(char >= ' ') { "Control character in string" }
                        out.append(char)
                    }
                }
            }
        }

        private fun number(): Any {
            val start = pos
            while (pos < text.length && text[pos] in "+-0123456789.eE") pos++
            val raw = text.substring(start, pos)
            require(raw.isNotEmpty()) { "Unexpected character at $start" }
            return raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun literal(word: String, value: Any?): Any? {
            require(text.startsWith(word, pos)) { "Unexpected token at $pos" }
            pos += word.length
            return value
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun expect(char: Char) {
            require(peek() == char) { "Expected '$char' at $pos" }
            pos++
        }
    }
}
