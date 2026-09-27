package org.communitypoke.magiskbrowser

import org.communitypoke.magiskbrowser.browser.UrlInput
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlInputTest {

    private val tpl = "https://duckduckgo.com/?q=%s"

    @Test
    fun `empty input yields about-blank`() {
        assertEquals("about:blank", UrlInput.resolve("   ", tpl))
    }

    @Test
    fun `full urls pass through`() {
        assertEquals("https://example.com/a?b=c", UrlInput.resolve("https://example.com/a?b=c", tpl))
        assertEquals("http://example.com", UrlInput.resolve("http://example.com", tpl))
    }

    @Test
    fun `bare domains get https`() {
        assertEquals("https://example.com", UrlInput.resolve("example.com", tpl))
        assertEquals("https://sub.example.co.uk/path", UrlInput.resolve("sub.example.co.uk/path", tpl))
    }

    @Test
    fun `localhost and ips get http`() {
        assertEquals("http://localhost:8080", UrlInput.resolve("localhost:8080", tpl))
        assertEquals("http://127.0.0.1:3000/x", UrlInput.resolve("127.0.0.1:3000/x", tpl))
    }

    @Test
    fun `queries become searches`() {
        assertEquals(
            "https://duckduckgo.com/?q=hello+world",
            UrlInput.resolve("hello world", tpl),
        )
        assertEquals(
            "https://duckduckgo.com/?q=what+is+android",
            UrlInput.resolve("what is android", tpl),
        )
    }

    @Test
    fun `engine templates`() {
        assertEquals("https://www.google.com/search?q=%s", UrlInput.searchTemplate("google"))
        assertEquals("https://search.brave.com/search?q=%s", UrlInput.searchTemplate("brave"))
        assertEquals("https://duckduckgo.com/?q=%s", UrlInput.searchTemplate("unknown-value"))
    }
}
