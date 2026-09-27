package org.communitypoke.magiskbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.communitypoke.magiskbrowser.browser.BrowserTab
import org.communitypoke.magiskbrowser.browser.JsonListStore
import org.communitypoke.magiskbrowser.browser.TabManager
import org.communitypoke.magiskbrowser.browser.UrlInput

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var webContainer: FrameLayout
    private lateinit var tabs: TabManager
    private lateinit var history: JsonListStore
    private lateinit var bookmarks: JsonListStore

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileChooserCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileChooserCallback = null
        }

    private val requestGeoPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants.values.any { it }
            geoCallback?.invoke(geoOrigin, granted, false)
            geoCallback = null
            geoOrigin = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        history = JsonListStore(getSharedPreferences("history", MODE_PRIVATE), "entries", 300)
        bookmarks = JsonListStore(getSharedPreferences("bookmarks", MODE_PRIVATE), "entries", 500)

        urlBar = findViewById(R.id.url_bar)
        progress = findViewById(R.id.progress)
        webContainer = findViewById(R.id.web_container)

        urlBar.setOnEditorActionListener { v, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_DONE -> {
                    navigate(v.text.toString())
                    true
                }
                else -> false
            }
        }
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            activeWebView()?.let { if (it.canGoBack()) it.goBack() }
        }
        findViewById<ImageButton>(R.id.btn_forward).setOnClickListener {
            activeWebView()?.let { if (it.canGoForward()) it.goForward() }
        }
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu() }

        tabs = TabManager(webContainer, ::createWebView, ::onActiveTabChanged)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = activeWebView()
                if (wv != null && wv.canGoBack()) wv.goBack() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (!handleIntent(intent)) {
            tabs.newTab()?.webView?.loadUrl("about:blank")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        tabs.destroyAll()
        super.onDestroy()
    }

    private fun activeWebView(): WebView? = tabs.active()?.webView

    private fun handleIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (intent.action != Intent.ACTION_VIEW) return false
        val tab = tabs.newTab() ?: tabs.active() ?: return false
        tab.webView?.loadUrl(uri.toString())
        return true
    }

    private fun navigate(input: String) {
        val template = UrlInput.searchTemplate(prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo")
        activeWebView()?.loadUrl(UrlInput.resolve(input, template))
        urlBar.clearFocus()
    }

    private fun onActiveTabChanged() {
        val tab = tabs.active()
        if (tab != null) {
            urlBar.setText(tab.lastUrl ?: "")
            if (tab.lastUrl == null) urlBar.text.clear()
        } else {
            // Last tab was closed — open a fresh one.
            tabs.newTab()?.webView?.loadUrl("about:blank")
        }
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab): WebView {
        val wv = WebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        applySettings(wv)
        wv.webViewClient = BrowserClient(tab)
        wv.webChromeClient = BrowserChromeClient(tab)
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }
        return wv
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION") // file-URL guards are explicit for API 26–29, where they still matter
    private fun applySettings(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = prefs.getBoolean("javascript_enabled", true)
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)

            // Secure-by-default surface restrictions.
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            blockNetworkImage = prefs.getBoolean("block_images", false)

            val customUa = prefs.getString("custom_user_agent", "")?.trim().orEmpty()
            userAgentString = when {
                customUa.isNotEmpty() -> customUa
                prefs.getBoolean("desktop_mode", false) -> DESKTOP_UA
                else -> userAgentString
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(this, true)
            }
            val forceDark = prefs.getBoolean("force_dark", false)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, forceDark)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    this,
                    if (forceDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_AUTO,
                )
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // First-party cookies only — privacy default, documented.
            setAcceptThirdPartyCookies(wv, false)
        }
    }

    private inner class BrowserClient(private val tab: BrowserTab) : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val url = request.url
            val scheme = url.scheme?.lowercase() ?: return true
            return when {
                scheme == "http" || scheme == "https" || url.toString() == "about:blank" -> false
                scheme == "intent" -> {
                    try {
                        Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME).let {
                            it.addCategory(Intent.CATEGORY_BROWSABLE)
                            it.component = null
                            it.selector = null
                            startActivity(it)
                        }
                    } catch (_: Exception) {
                        toast("No app can handle that link")
                    }
                    true
                }
                scheme in EXTERNAL_SCHEMES -> {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: Exception) {
                        toast("No app can handle $scheme links")
                    }
                    true
                }
                else -> true // data:, javascript:, file:, etc. — not allowed top-level
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            progress.visibility = View.VISIBLE
            if (tabs.activeId == tab.id) urlBar.setText(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.lastUrl = url
            if (tabs.activeId == tab.id) {
                urlBar.setText(url)
                progress.visibility = View.GONE
            }
            if (!tab.incognito && isRecordable(url)) {
                history.add(view.title ?: url, url)
            }
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError,
        ) {
            // Never silently proceed — secure default. Show an inert error page.
            handler.cancel()
            val escaped = error.url?.replace("<", "&lt;") ?: ""
            view.loadDataWithBaseURL(
                null,
                "<html><body style=\"font-family:sans-serif;padding:2em\">" +
                    "<h2>Certificate error</h2>" +
                    "<p>$escaped could not be verified (${error.primaryError}). " +
                    "This browser never bypasses TLS errors.</p>" +
                    "</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }

        private fun isRecordable(url: String): Boolean =
            url.startsWith("http://") || url.startsWith("https://")
    }

    private inner class BrowserChromeClient(private val tab: BrowserTab) : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (tabs.activeId != tab.id) return
            progress.progress = newProgress
            progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            tab.title = title
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Location request")
                .setMessage("$origin wants your location")
                .setPositiveButton("Allow") { _, _ ->
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        callback.invoke(origin, true, false)
                    } else {
                        geoCallback = callback
                        geoOrigin = origin
                        requestGeoPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                }
                .setNegativeButton("Deny") { _, _ -> callback.invoke(origin, false, false) }
                .show()
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback
            return try {
                pickFile.launch(fileChooserParams.createIntent())
                true
            } catch (_: Exception) {
                fileChooserCallback = null
                false
            }
        }
    }

    // ------------------------------------------------------------------ Downloads

    private fun enqueueDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimeType),
                )
            }
            getSystemService(DownloadManager::class.java).enqueue(request)
            toast("Download started")
        } catch (e: Exception) {
            toast("Download failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ Menus

    private fun showMenu() {
        val items = arrayOf(
            "New tab",
            "New incognito tab",
            "Switch tabs (${tabs.tabs.size})",
            "Add bookmark",
            "Bookmarks",
            "History",
            "Reload",
            "Close current tab",
            "Settings",
            "About",
        )
        AlertDialog.Builder(this)
            .setTitle(tabs.active()?.describe() ?: getString(R.string.app_name))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> newTabOrWarn(incognito = false)
                    1 -> newTabOrWarn(incognito = true)
                    2 -> showTabsDialog()
                    3 -> addBookmark()
                    4 -> showEntryList("Bookmarks", bookmarks, ::openUrl)
                    5 -> showEntryList("History", history, ::openUrl)
                    6 -> activeWebView()?.reload()
                    7 -> tabs.active()?.let { tabs.closeTab(it.id) }
                    8 -> startActivity(Intent(this, SettingsActivity::class.java))
                    9 -> showAbout()
                }
            }
            .show()
    }

    private fun newTabOrWarn(incognito: Boolean) {
        if (tabs.newTab(incognito) == null) {
            toast("Tab limit reached (${TabManager.MAX_TABS})")
        }
    }

    private fun showTabsDialog() {
        val names = tabs.tabs.map { it.describe() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tabs")
            .setItems(names) { d, which -> tabs.activate(tabs.tabs[which].id); d.dismiss() }
            .show()
    }

    private fun addBookmark() {
        val tab = tabs.active() ?: return
        val url = tab.lastUrl ?: return toast("Nothing to bookmark")
        bookmarks.add(tab.title ?: url, url)
        toast("Bookmarked")
    }

    private fun showEntryList(
        title: String,
        store: JsonListStore,
        onPick: (String) -> Unit,
    ) {
        val entries = store.all()
        if (entries.isEmpty()) return toast("No $title yet")
        val labels = entries.map { "${it.title.ifBlank { it.url }}" }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("$title — long-press to delete")
            .setItems(labels) { d, which -> onPick(entries[which].url); d.dismiss() }
            .setNegativeButton("Clear all") { _, _ -> store.clear(); toast("$title cleared") }
            .show()
        dialog.listView.setOnItemLongClickListener { _, _, pos, _ ->
            store.remove(entries[pos].url)
            dialog.dismiss()
            true
        }
    }

    private fun openUrl(url: String) = activeWebView()?.loadUrl(url)

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(
                "v${BuildConfig.VERSION_NAME}\n\n" +
                    "Native WebView browser with optional Magisk root integration.\n" +
                    "github.com/CommunityPokeOrg/magisk-browser"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private val EXTERNAL_SCHEMES = setOf("tel", "mailto", "sms", "geo", "market")
    }
}
