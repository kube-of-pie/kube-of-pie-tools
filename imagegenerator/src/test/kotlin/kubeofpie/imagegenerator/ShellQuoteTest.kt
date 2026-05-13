package kubeofpie.imagegenerator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShellQuoteTest {

    @Test
    fun `leaves harmless characters untouched`() {
        assertEquals("hello world", ShellQuote.escape("hello world"))
        assertEquals("3.21", ShellQuote.escape("3.21"))
        assertEquals("", ShellQuote.escape(""))
    }

    @Test
    fun `escapes the four shell-significant double-quoted characters`() {
        assertEquals("a\\\$b", ShellQuote.escape("a\$b"))
        assertEquals("a\\\"b", ShellQuote.escape("a\"b"))
        assertEquals("a\\\\b", ShellQuote.escape("a\\b"))
        assertEquals("a\\`b", ShellQuote.escape("a`b"))
    }

    @Test
    fun `escapes combinations correctly`() {
        assertEquals(
            "p\\$\\\"123\\`\\\\",
            ShellQuote.escape("p\$\"123`\\"),
        )
    }

    @Test
    fun `keeps newlines literal (multi-line values like INTERFACES)`() {
        assertEquals("a\nb", ShellQuote.escape("a\nb"))
    }

    @Test
    fun `escapeOrNull returns null for null input`() {
        assertNull(ShellQuote.escapeOrNull(null))
        assertEquals("ok", ShellQuote.escapeOrNull("ok"))
    }
}
