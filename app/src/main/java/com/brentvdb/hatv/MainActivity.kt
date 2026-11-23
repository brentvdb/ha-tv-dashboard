package com.brentvdb.hatv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * Full-screen Home Assistant dashboard for Android TV / Google TV.
 *
 * Remote controls:
 *  - Arrow keys move a highlight between tiles, tabs and buttons (tvnav.js)
 *  - OK / Select activates the highlighted item
 *  - Back closes pop-ups, then goes back, then (press twice) exits
 *  - Long-press Back, or the Menu key, opens Settings
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var prefs: Prefs
    private var navScript = ""
    private val handler = Handler(Looper.getMainLooper())
    private var loadedConfig = ""
    private var lastBackPress = 0L
    private var backLongPressed = false
    private var showingError = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        navScript = assets.open("tvnav.js").bufferedReader().use { it.readText() }

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1220"))
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.userAgentString = settings.userAgentString + " HomeAssistantTV/1.0"
            webChromeClient = WebChromeClient()
            webViewClient = DashboardClient()
        }
        setContentView(web)
        hideSystemUi()

        if (savedInstanceState == null && prefs.serverUrl.isBlank()) {
            openSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // (Re)load whenever the settings that affect the page have changed.
        val config = listOf(prefs.dashboardUrl, prefs.accessToken, prefs.zoomPercent).joinToString("|")
        if (config != loadedConfig) {
            if (loadedConfig.isNotEmpty() && startScriptInstalled) {
                // The start-up script is baked into this WebView; rebuild it with the new settings.
                recreate()
                return
            }
            loadedConfig = config
            applyZoom()
            installStartScript()
            showingError = false
            web.loadUrl(prefs.dashboardUrl)
        }
        web.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ---- Remote keys -------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (event.repeatCount == 0) {
                    event.startTracking()
                    backLongPressed = false
                }
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                openSettings(); return true
            }
            KeyEvent.KEYCODE_PROG_RED -> {
                web.reload(); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backLongPressed = true
            openSettings()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!backLongPressed && event.isTracking && !event.isCanceled) handleBack()
            backLongPressed = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /** Back: close an open pop-up first, then go back a page, then exit on a double press. */
    private fun handleBack() {
        if (showingError) { exitWithDoublePress(); return }
        web.evaluateJavascript("(window.__hatvBack ? window.__hatvBack() : 'none')") { result ->
            when (result?.trim('"')) {
                "closed", "back" -> Unit
                else -> if (web.canGoBack()) web.goBack() else exitWithDoublePress()
            }
        }
    }

    private fun exitWithDoublePress() {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) finish()
        else {
            lastBackPress = now
            Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Page setup --------------------------------------------------------

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun applyZoom() {
        val zoom = prefs.zoomPercent
        if (zoom == 100) {
            web.settings.useWideViewPort = true
            web.setInitialScale(0)
        } else {
            web.settings.useWideViewPort = false
            web.setInitialScale(zoom)
        }
    }

    /**
     * Script that runs before Home Assistant's own code on every page:
     * hides the sidebar, and (if a token is set) signs in automatically.
     */
    private fun startScript(): String {
        val sb = StringBuilder("(function(){try{")
        sb.append("localStorage.setItem('dockedSidebar','\"always_hidden\"');")
        val token = prefs.accessToken
        if (token.isNotEmpty()) {
            val tokens = JSONObject()
                .put("access_token", token)
                .put("token_type", "Bearer")
                .put("expires_in", 1800)
                .put("hassUrl", prefs.origin)
                .put("clientId", prefs.origin + "/")
                .put("expires", 4102444800000L) // year 2100: never refresh
                .put("refresh_token", "")
                .toString()
            sb.append("var t=").append(JSONObject.quote(tokens)).append(";")
            sb.append("var cur=localStorage.getItem('hassTokens');")
            sb.append("if(cur!==t){localStorage.setItem('hassTokens',t);window.__hatvTokenSet=true;}")
        }
        sb.append("}catch(e){}})();")
        return sb.toString()
    }

    private var startScriptInstalled = false

    private fun installStartScript() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && !startScriptInstalled) {
            // Installed once per WebView; if settings change, the activity is recreated by SettingsActivity.
            WebViewCompat.addDocumentStartJavaScript(web, startScript(), setOf(prefs.origin))
            startScriptInstalled = true
        }
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private inner class DashboardClient : WebViewClient() {

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (showingError) return
            // Older WebViews without document-start scripts: apply settings now, and if that
            // just signed us in on the login page, reload into the dashboard.
            if (!startScriptInstalled) {
                view.evaluateJavascript(startScript() + "(window.__hatvTokenSet?'set':'')") { r ->
                    if (r?.contains("set") == true && url.contains("/auth/")) view.loadUrl(prefs.dashboardUrl)
                }
            }
            view.evaluateJavascript(navScript, null)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            showError(view, error.description?.toString() ?: "Connection error")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Keep Home Assistant inside the app; ignore links to other sites.
            val target = request.url.toString()
            return !target.startsWith(prefs.origin)
        }
    }

    private fun showError(view: WebView, reason: String) {
        showingError = true
        val safeReason = reason.replace("<", "&lt;")
        val html = """
            <html><body style="background:#0B1220;color:#E6EDF5;font-family:sans-serif;
              display:flex;align-items:center;justify-content:center;height:100vh;margin:0">
            <div style="text-align:center;max-width:70%">
              <h1 style="color:#F2B94B">Can't reach Home Assistant</h1>
              <p style="font-size:22px">${prefs.dashboardUrl}</p>
              <p style="color:#93A4BA">$safeReason</p>
              <p style="font-size:22px">Retrying in 10 seconds… Long-press <b>Back</b> for Settings.</p>
            </div></body></html>
        """.trimIndent()
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            showingError = false
            web.loadUrl(prefs.dashboardUrl)
        }, 10_000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }
}
