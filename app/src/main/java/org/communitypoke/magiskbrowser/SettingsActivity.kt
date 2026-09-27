package org.communitypoke.magiskbrowser

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.communitypoke.magiskbrowser.browser.JsonListStore
import org.communitypoke.magiskbrowser.root.RootConfig
import org.communitypoke.magiskbrowser.root.RootShell

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            wireDataSection()
            wireRootSection()
            findPreference<Preference>("about_version")?.summary = "v${BuildConfig.VERSION_NAME}"
            findPreference<Preference>("about_repo")?.setOnPreferenceClickListener {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/CommunityPokeOrg/magisk-browser"),
                    )
                )
                true
            }
        }

        private fun wireDataSection() {
            findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
                JsonListStore(
                    requireContext().getSharedPreferences("history", Context.MODE_PRIVATE),
                    "entries", 300,
                ).clear()
                toast("History cleared")
                true
            }
            findPreference<Preference>("clear_cookies")?.setOnPreferenceClickListener {
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                toast("Cookies cleared")
                true
            }
            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                WebView(requireContext()).clearCache(true)
                toast("WebView cache cleared")
                true
            }
        }

        // ------------------------------------------------------------ Root section

        private fun wireRootSection() {
            val status = findPreference<Preference>("root_status")
            val privStatus = findPreference<Preference>("root_privapp_status")

            // su checks spawn a process — keep them off the main thread.
            Thread {
                val rootAvailable = RootShell.isAvailable()
                val confExists = rootAvailable &&
                    RootShell.exec("test -f ${RootConfig.CONF_PATH}").ok
                val wss = context?.checkSelfPermission(
                    android.Manifest.permission.WRITE_SECURE_SETTINGS
                ) == PackageManager.PERMISSION_GRANTED

                activity?.runOnUiThread {
                    status?.summary = when {
                        !rootAvailable -> "Not available (su not found or denied)"
                        !confExists -> "su works, but the Magisk module is not installed"
                        else -> "su available — module installed at ${RootConfig.CONF_DIR}"
                    }
                    privStatus?.summary = if (wss) {
                        "WRITE_SECURE_SETTINGS granted (priv-app whitelist active)"
                    } else {
                        "Not granted — install the Magisk module or use su"
                    }
                }
            }.start()

            // Root rows stay visible even without su (documentation value);
            // actions re-check root at call time.
            findPreference<Preference>("root_edit_config")?.setOnPreferenceClickListener {
                editRemoteFile(RootConfig.CONF_PATH, "browser.conf")
                true
            }
            findPreference<Preference>("root_edit_hosts")?.setOnPreferenceClickListener {
                editRemoteFile(RootConfig.HOSTS_BLOCKLIST_PATH, "hosts.block")
                true
            }

            bindRootToggle("root_set_default_browser", RootConfig.KEY_SET_DEFAULT_BROWSER)
            bindRootToggle("root_hosts_blocklist", RootConfig.KEY_HOSTS_BLOCKLIST)
            bindRootText("root_global_http_proxy", RootConfig.KEY_GLOBAL_HTTP_PROXY)
            bindRootText("root_webview_provider", RootConfig.KEY_WEBVIEW_PROVIDER)
        }

        /** Loads the conf via su, applies [mutate], writes it back. */
        private fun updateConf(mutate: (RootConfig) -> Unit, onDone: (Boolean) -> Unit) {
            Thread {
                val ok = RootShell.isAvailable()
                var saved = false
                if (ok) {
                    val conf = RootShell.readFile(RootConfig.CONF_PATH)
                        ?.let(RootConfig::parse) ?: RootConfig.defaults()
                    mutate(conf)
                    saved = RootShell.writeFile(RootConfig.CONF_PATH, conf.serialize())
                }
                activity?.runOnUiThread { onDone(ok && saved) }
            }.start()
        }

        private fun bindRootToggle(prefKey: String, confKey: String) {
            findPreference<SwitchPreferenceCompat>(prefKey)?.setOnPreferenceChangeListener { pref, newValue ->
                val enabled = newValue as Boolean
                updateConf({ it.set(confKey, if (enabled) "1" else "0") }) { ok ->
                    if (!ok) {
                        (pref as SwitchPreferenceCompat).isChecked = !enabled
                        toast("Failed to write browser.conf (root/module missing?)")
                    } else {
                        toast("Saved — applies on next boot")
                    }
                }
                true
            }
        }

        private fun bindRootText(prefKey: String, confKey: String) {
            findPreference<EditTextPreference>(prefKey)?.setOnPreferenceChangeListener { _, newValue ->
                val value = (newValue as? String)?.trim().orEmpty()
                updateConf({ it.set(confKey, value) }) { ok ->
                    toast(if (ok) "Saved — applies on next boot" else "Failed to write browser.conf")
                }
                true
            }
        }

        private fun editRemoteFile(path: String, title: String) {
            Thread {
                val root = RootShell.isAvailable()
                val text = if (root) RootShell.readFile(path) else null
                activity?.runOnUiThread {
                    if (!root || text == null) {
                        toast("Need su + installed module to edit $title")
                        return@runOnUiThread
                    }
                    val edit = EditText(requireContext()).apply {
                        setText(text)
                        minLines = 10
                        setHorizontallyScrolling(true)
                    }
                    AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setView(edit)
                        .setPositiveButton("Save") { _, _ ->
                            Thread {
                                val ok = RootShell.writeFile(path, edit.text.toString())
                                activity?.runOnUiThread {
                                    toast(if (ok) "Saved" else "Write failed")
                                }
                            }.start()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }.start()
        }

        private fun toast(msg: String) =
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
