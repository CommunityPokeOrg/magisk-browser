package org.communitypoke.magiskbrowser.browser

import android.webkit.WebView

/**
 * One browser tab. The WebView is created lazily on first activation so a
 * session with many tabs stays cheap.
 *
 * Incognito here means: no history entries, no bookmark adds. Cookies and the
 * HTTP cache are still shared with normal tabs (Android WebView has a single
 * process-wide cookie/cache store — see docs/LIMITATIONS.md).
 */
class BrowserTab(val id: Long, val incognito: Boolean) {
    var webView: WebView? = null
    var lastUrl: String? = null
    var title: String? = null

    fun describe(): String {
        val base = title?.takeIf { it.isNotBlank() } ?: lastUrl ?: "New tab"
        return if (incognito) "🕶 $base" else base
    }
}
