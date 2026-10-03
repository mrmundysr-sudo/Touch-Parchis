package com.touchdevelopment.touchparchis.data

/**
 * Minimal pure-Kotlin JSON reader. Kept dependency-free (no org.json) so the same
 * parsing code runs in the Android app and in JVM unit tests.
 */
object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWhitespace()
        val value = p.readValue()
        p.skipWhitespace()
        require(p.atEnd()) { "trailing content after JSON value" }
        return value
    }

    private class Parser(private val s: String) {
        private var i = 0
        fun atEnd() = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun readValue(): Any? {
            skipWhitespace()
            require(i < s.length) { "unexpected end of JSON" }
            return when (s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { i++; return map }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                map[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> { i++ }
                    '}' -> { i++; return map }
                    else -> error("expected ',' or '}' at $i")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') { i++; return list }
            while (true) {
                list += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> { i++ }
                    ']' -> { i++; return list }
                    else -> error("expected ',' or ']' at $i")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> error("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val token = s.substring(start, i)
            return token.toDouble()
        }

        private fun readBoolean(): Boolean =
            if (s.startsWith("true", i)) { i += 4; true }
            else if (s.startsWith("false", i)) { i += 5; false }
            else error("bad literal at $i")

        private fun readNull(): Any? { i += 4; return null }

        private fun peek(): Char = s[i]
        private fun expect(c: Char) { require(s[i] == c) { "expected '$c' at $i" }; i++ }
    }

    // Convenience accessors for parsed structures.
    @Suppress("UNCHECKED_CAST")
    fun obj(v: Any?): Map<String, Any?> = v as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun arr(v: Any?): List<Any?> = v as List<Any?>

    fun num(v: Any?): Double = (v as Number).toDouble()
    fun int(v: Any?): Int = (v as Number).toInt()
    fun str(v: Any?): String = v as String
    fun bool(v: Any?): Boolean = v as Boolean
}
