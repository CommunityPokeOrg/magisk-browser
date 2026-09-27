package org.communitypoke.magiskbrowser.browser

import java.net.URLEncoder

/**
 * Turns omnibox text into a navigable URL or a search URL.
 * Pure Kotlin — covered by JVM unit tests, no Android imports.
 */
object UrlInput {

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
    private val HOST_LIKE = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|\\[[0-9a-fA-F:]+\\]|[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+)(:\\d+)?([/?#].*)?$"
    )

    fun resolve(input: String, searchTemplate: String): String {
        val q = input.trim()
        if (q.isEmpty()) return "about:blank"
        if (q.startsWith("about:")) return q
        if (SCHEME.matches(q)) return q
        if (!q.contains(' ') && HOST_LIKE.matches(q)) {
            // Numeric hosts and localhost almost always run plain HTTP.
            val scheme = if (q.first().isDigit() || q.startsWith("localhost") || q.startsWith("[")) "http" else "https"
            return "$scheme://$q"
        }
        return searchTemplate.replace("%s", URLEncoder.encode(q, "UTF-8"))
    }

    fun searchTemplate(engine: String): String = when (engine) {
        "google" -> "https://www.google.com/search?q=%s"
        "brave" -> "https://search.brave.com/search?q=%s"
        "bing" -> "https://www.bing.com/search?q=%s"
        else -> "https://duckduckgo.com/?q=%s"
    }
}
