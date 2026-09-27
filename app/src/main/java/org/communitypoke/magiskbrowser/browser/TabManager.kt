package org.communitypoke.magiskbrowser.browser

import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Owns the tab list and the single FrameLayout the active tab's WebView is
 * attached to. WebViews are kept per-tab so switching preserves page state.
 */
class TabManager(
    private val container: FrameLayout,
    private val webViewFactory: (BrowserTab) -> WebView,
    private val onActiveChanged: () -> Unit,
) {
    companion object {
        const val MAX_TABS = 12
    }

    private var nextId = 1L
    val tabs = mutableListOf<BrowserTab>()
    var activeId: Long = -1L
        private set

    fun active(): BrowserTab? = tabs.firstOrNull { it.id == activeId }

    fun newTab(incognito: Boolean = false, activate: Boolean = true): BrowserTab? {
        if (tabs.size >= MAX_TABS) return null
        val tab = BrowserTab(nextId++, incognito)
        tabs.add(tab)
        if (activate) activate(tab.id)
        return tab
    }

    fun activate(id: Long) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        activeId = id
        if (tab.webView == null) tab.webView = webViewFactory(tab)
        container.removeAllViews()
        val wv = tab.webView
        if (wv != null) {
            (wv.parent as? FrameLayout)?.removeView(wv)
            container.addView(wv)
        }
        onActiveChanged()
    }

    fun closeTab(id: Long): Boolean {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return false
        val tab = tabs.removeAt(index)
        tab.webView?.let { (it.parent as? FrameLayout)?.removeView(it); it.destroy() }
        if (tab.id == activeId) {
            activeId = -1
            val next = tabs.getOrNull(index) ?: tabs.lastOrNull()
            if (next != null) activate(next.id) else onActiveChanged()
        }
        return true
    }

    fun destroyAll() {
        tabs.forEach { it.webView?.destroy() }
        tabs.clear()
        activeId = -1
    }
}
