package com.brentvdb.hatv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * Home Assistant for Android TV / Google TV.
 *
 * Opens on an area picker (Main House / Upstairs House / Irrigation). Choosing an area
 * shows that area's dashboard full-screen.
 *
 * Remote controls on a dashboard:
 *  - Arrow keys move a highlight between tiles, tabs and buttons (tvnav.js)
 *  - OK / Select activates the highlighted item
 *  - Back closes pop-ups, then goes back within the area, then returns to the area picker
 *  - Long-press Back, or the Menu key, opens Settings
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var picker: View
    private lateinit var prefs: Prefs
    private val areaCards = mutableListOf<View>()
    private var navScript = ""
    private val handler = Handler(Looper.getMainLooper())

    private var currentArea: Area? = null
    private var lastAreaIndex = 0
    private var loadedConfig = ""
    private var startScriptInstalled = false
    private var clearHistoryOnLoad = false
    private var showingError = false
    private var lastBackPress = 0L
    private var backLongPressed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        navScript = assets.open("tvnav.js").bufferedReader().use { it.readText() }

        web = WebView(this).apply {
            setBackgroundColor(BG)
            isFocusable = true
            isFocusableInTouchMode = true
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.userAgentString = settings.userAgentString + " HomeAssistantTV/1.1"
            webChromeClient = WebChromeClient()
            webViewClient = DashboardClient()
            addJavascriptInterface(Bridge(), "HATV")
        }
        picker = buildPicker()

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(BG)
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(picker, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val config = prefs.pageConfig
        if (config != loadedConfig) {
            if (loadedConfig.isNotEmpty() && startScriptInstalled) {
                // The start-up script is baked into this WebView; rebuild it with the new settings.
                recreate()
                return
            }
            loadedConfig = config
            applyZoom()
            installStartScript()
        }
        if (picker.visibility == View.VISIBLE) areaCards.getOrNull(lastAreaIndex)?.requestFocus()
        else web.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ---- Area picker -------------------------------------------------------

    private fun buildPicker(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
        }
        column.addView(TextView(this).apply {
            text = "Home Assistant"
            textSize = 40f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
        })
        column.addView(TextView(this).apply {
            text = "Choose your area"
            textSize = 22f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(36))
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        Prefs.AREAS.forEachIndexed { index, area ->
            val card = areaCard(area) { openArea(index) }
            areaCards.add(card)
            row.addView(card, LinearLayout.LayoutParams(dp(250), dp(170)).apply {
                leftMargin = dp(14); rightMargin = dp(14)
            })
        }
        column.addView(row)

        column.addView(TextView(this).apply {
            text = "⚙  Settings"
            textSize = 18f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = focusFrame(SURFACE, 3)
            setPadding(dp(22), dp(10), dp(22), dp(10))
            setOnClickListener { openSettings() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(40) })

        column.addView(TextView(this).apply {
            text = "On a dashboard: arrows move · OK selects · Back returns here · long-press Back = Settings"
            textSize = 15f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        })
        return column
    }

    private fun areaCard(area: Area, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = focusFrame(SURFACE, 5)
            addView(TextView(context).apply {
                text = area.title
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = area.subtitle
                textSize = 16f
                setTextColor(MUTED)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), 0)
            })
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, has ->
                v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(120).start()
            }
        }

    private fun openArea(index: Int) {
        val area = Prefs.AREAS[index]
        lastAreaIndex = index
        currentArea = area
        showingError = false
        pageTyping = false
        clearHistoryOnLoad = true
        picker.visibility = View.GONE
        web.visibility = View.VISIBLE
        web.loadUrl(prefs.areaUrl(area))
        web.requestFocus()
    }

    private fun showPicker() {
        handler.removeCallbacksAndMessages(null)
        currentArea = null
        showingError = false
        pageTyping = false
        web.loadUrl("about:blank") // stops camera streams etc. while the picker is showing
        web.visibility = View.GONE
        picker.visibility = View.VISIBLE
        areaCards.getOrNull(lastAreaIndex)?.requestFocus()
    }

    // ---- Remote keys -------------------------------------------------------

    /** True while a text box on the page has focus (reported by tvnav.js). */
    @Volatile private var pageTyping = false

    /** Small bridge the page script uses to talk to the app. */
    private inner class Bridge {
        @JavascriptInterface
        fun typing(isTyping: Boolean) { pageTyping = isTyping }

        @JavascriptInterface
        fun showKeyboard() {
            runOnUiThread {
                web.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    /**
     * On a dashboard, the arrow keys and OK are sent straight to the page's navigation
     * script, so they work no matter which view Android thinks has focus. While a text box
     * is focused (e.g. the sign-in page), keys go to the page normally so typing works.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (web.visibility == View.VISIBLE && currentArea != null && !showingError && !pageTyping) {
            val key = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> "up"
                KeyEvent.KEYCODE_DPAD_DOWN -> "down"
                KeyEvent.KEYCODE_DPAD_LEFT -> "left"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "ok"
                else -> null
            }
            if (key != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    web.evaluateJavascript("(window.__hatvKey ? window.__hatvKey('$key') : 'not-loaded')") { r ->
                        if (r?.contains("not-loaded") == true) {
                            // The page script isn't there yet (still loading); inject it now.
                            web.evaluateJavascript(navScript, null)
                        }
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

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
                if (currentArea != null) web.reload(); return true
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

    /** Back: close a pop-up, then go back within the area, then return to the area picker, then exit. */
    private fun handleBack() {
        if (picker.visibility == View.VISIBLE) { exitWithDoublePress(); return }
        if (showingError) { showPicker(); return }
        web.evaluateJavascript("(window.__hatvBack ? window.__hatvBack() : 'none')") { result ->
            when (result?.trim('"')) {
                "closed", "back" -> Unit
                else -> showPicker()
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

    private fun installStartScript() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && !startScriptInstalled) {
            // Installed once per WebView; if settings change, the activity is recreated.
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
            val area = currentArea ?: return
            if (showingError || url == "about:blank") return
            if (clearHistoryOnLoad) {
                // Each area starts with a fresh history, so Back never jumps into another area.
                view.clearHistory()
                clearHistoryOnLoad = false
            }
            // Older WebViews without document-start scripts: apply settings now, and if that
            // just signed us in on the login page, reload into the dashboard.
            if (!startScriptInstalled) {
                view.evaluateJavascript(startScript() + "(window.__hatvTokenSet?'set':'')") { r ->
                    if (r?.contains("set") == true && url.contains("/auth/")) view.loadUrl(prefs.areaUrl(area))
                }
            }
            view.evaluateJavascript("window.__hatvRoot=" + JSONObject.quote(prefs.areaPath(area)) + ";", null)
            view.evaluateJavascript(navScript, null)
            // Make sure the remote's keys go to the dashboard, not the (hidden) area picker.
            if (picker.visibility != View.VISIBLE) view.requestFocus(View.FOCUS_DOWN)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame || currentArea == null) return
            showError(view, error.description?.toString() ?: "Connection error")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Keep Home Assistant inside the app; ignore links to other sites.
            val target = request.url.toString()
            return !target.startsWith(prefs.origin)
        }
    }

    private fun showError(view: WebView, reason: String) {
        val area = currentArea ?: return
        showingError = true
        val safeReason = reason.replace("<", "&lt;")
        val html = """
            <html><body style="background:#0B1220;color:#E6EDF5;font-family:sans-serif;
              display:flex;align-items:center;justify-content:center;height:100vh;margin:0">
            <div style="text-align:center;max-width:70%">
              <h1 style="color:#F2B94B">Can't reach Home Assistant</h1>
              <p style="font-size:22px">${prefs.areaUrl(area)}</p>
              <p style="color:#93A4BA">$safeReason</p>
              <p style="font-size:22px">Retrying in 10 seconds… Press <b>Back</b> for the area menu,
              long-press <b>Back</b> for Settings.</p>
            </div></body></html>
        """.trimIndent()
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (currentArea == area) {
                showingError = false
                web.loadUrl(prefs.areaUrl(area))
            }
        }, 10_000)
    }

    // ---- Styling helpers ---------------------------------------------------

    /** Card background with a thick teal frame when focused, so the remote's position is obvious. */
    private fun focusFrame(fill: Int, focusStrokeDp: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(fill, ACCENT, focusStrokeDp))
        addState(intArrayOf(), rounded(fill, BORDER, 1))
    }

    private fun rounded(fill: Int, stroke: Int, strokeDp: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(16).toFloat()
        setStroke(dp(strokeDp), stroke)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private val BG = Color.parseColor("#0B1220")
        private val SURFACE = Color.parseColor("#142033")
        private val BORDER = Color.parseColor("#24334A")
        private val ACCENT = Color.parseColor("#22D3C5")
        private val TEXT = Color.parseColor("#E6EDF5")
        private val MUTED = Color.parseColor("#93A4BA")
    }
}
