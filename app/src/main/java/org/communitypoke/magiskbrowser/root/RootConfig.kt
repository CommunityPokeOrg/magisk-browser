package org.communitypoke.magiskbrowser.root

/**
 * Model of the Magisk module's boot-time config file
 * (/data/adb/magisk-browser/browser.conf), a flat `key=value` file consumed by
 * the module's service.sh. Pure Kotlin so it can be unit-tested on the JVM.
 */
class RootConfig private constructor(private val values: LinkedHashMap<String, String>) {

    operator fun get(key: String): String = values[key] ?: ""

    fun set(key: String, value: String) {
        values[key] = value
    }

    fun isEnabled(key: String): Boolean = this[key] == "1"

    fun serialize(): String = buildString {
        appendLine("# Wolfy Browser root configuration")
        appendLine("# Consumed by the magisk-browser module's service.sh at boot.")
        appendLine("# Keys are documented in docs/ROOT-SETTINGS.md.")
        for ((k, v) in values) {
            append(k).append('=').appendLine(v)
        }
    }

    companion object {
        const val MODULE_ID = "magisk-browser"
        const val CONF_DIR = "/data/adb/magisk-browser"
        const val CONF_PATH = "$CONF_DIR/browser.conf"
        const val HOSTS_BLOCKLIST_PATH = "$CONF_DIR/hosts.block"
        const val PACKAGE_NAME = "org.communitypoke.magiskbrowser"

        const val KEY_SET_DEFAULT_BROWSER = "set_default_browser"
        const val KEY_HOSTS_BLOCKLIST = "hosts_blocklist"
        const val KEY_GLOBAL_HTTP_PROXY = "global_http_proxy"
        const val KEY_WEBVIEW_PROVIDER = "webview_provider"

        val DEFAULTS: LinkedHashMap<String, String> = linkedMapOf(
            KEY_SET_DEFAULT_BROWSER to "0",
            KEY_HOSTS_BLOCKLIST to "0",
            KEY_GLOBAL_HTTP_PROXY to "",
            KEY_WEBVIEW_PROVIDER to "",
        )

        fun defaults(): RootConfig = RootConfig(LinkedHashMap(DEFAULTS))

        fun parse(text: String): RootConfig {
            val map = LinkedHashMap(DEFAULTS)
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim()
                if (key.isNotEmpty()) map[key] = value
            }
            return RootConfig(map)
        }
    }
}
