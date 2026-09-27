package org.communitypoke.magiskbrowser

import org.communitypoke.magiskbrowser.root.RootConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootConfigTest {

    @Test
    fun `defaults are all disabled and empty`() {
        val conf = RootConfig.defaults()
        assertFalse(conf.isEnabled(RootConfig.KEY_SET_DEFAULT_BROWSER))
        assertFalse(conf.isEnabled(RootConfig.KEY_HOSTS_BLOCKLIST))
        assertEquals("", conf[RootConfig.KEY_GLOBAL_HTTP_PROXY])
        assertEquals("", conf[RootConfig.KEY_WEBVIEW_PROVIDER])
    }

    @Test
    fun `parse reads key=value and ignores comments and blank lines`() {
        val conf = RootConfig.parse(
            """
            # comment
            set_default_browser=1
            global_http_proxy=127.0.0.1:8080

            broken-line-without-equals
            =nokey
            webview_provider = com.google.android.webview
            """.trimIndent()
        )
        assertTrue(conf.isEnabled(RootConfig.KEY_SET_DEFAULT_BROWSER))
        assertEquals("127.0.0.1:8080", conf[RootConfig.KEY_GLOBAL_HTTP_PROXY])
        assertEquals("com.google.android.webview", conf[RootConfig.KEY_WEBVIEW_PROVIDER])
        assertEquals("0", conf[RootConfig.KEY_HOSTS_BLOCKLIST])
    }

    @Test
    fun `values may contain equals signs`() {
        val conf = RootConfig.parse("global_http_proxy=user:pass@host:8080?x=y")
        assertEquals("user:pass@host:8080?x=y", conf[RootConfig.KEY_GLOBAL_HTTP_PROXY])
    }

    @Test
    fun `serialize round-trips through parse`() {
        val conf = RootConfig.defaults()
        conf.set(RootConfig.KEY_SET_DEFAULT_BROWSER, "1")
        conf.set(RootConfig.KEY_GLOBAL_HTTP_PROXY, "10.0.0.2:3128")
        val reparsed = RootConfig.parse(conf.serialize())
        assertTrue(reparsed.isEnabled(RootConfig.KEY_SET_DEFAULT_BROWSER))
        assertEquals("10.0.0.2:3128", reparsed[RootConfig.KEY_GLOBAL_HTTP_PROXY])
    }

    @Test
    fun `serialize output is valid shell-conf format`() {
        val text = RootConfig.defaults().serialize()
        val nonCommentLines = text.lines().filter {
            it.isNotBlank() && !it.trim().startsWith("#")
        }
        assertTrue(nonCommentLines.isNotEmpty())
        nonCommentLines.forEach { line ->
            assertTrue("line has key=value: $line", line.contains('='))
            assertFalse("no leading whitespace: $line", line.startsWith(" ") || line.startsWith("\t"))
        }
    }
}
