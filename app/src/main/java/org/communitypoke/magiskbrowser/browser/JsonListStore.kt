package org.communitypoke.magiskbrowser.browser

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny JSON list backed by SharedPreferences — used for bookmarks and history.
 * Newest entries first, deduplicated by URL, capped at [maxEntries].
 */
class JsonListStore(
    private val prefs: SharedPreferences,
    private val key: String,
    private val maxEntries: Int,
) {
    data class Entry(val title: String, val url: String, val timestamp: Long)

    fun all(): List<Entry> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                title = o.optString("title"),
                url = o.optString("url"),
                timestamp = o.optLong("timestamp"),
            )
        }
    }

    fun add(title: String, url: String) {
        val entries = all().filterNot { it.url == url }.toMutableList()
        entries.add(0, Entry(title, url, System.currentTimeMillis()))
        save(entries.take(maxEntries))
    }

    fun remove(url: String) = save(all().filterNot { it.url == url })

    fun clear() = save(emptyList())

    private fun save(entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("title", e.title)
                    .put("url", e.url)
                    .put("timestamp", e.timestamp)
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
