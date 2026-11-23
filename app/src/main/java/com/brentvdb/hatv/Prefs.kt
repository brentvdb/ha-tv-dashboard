package com.brentvdb.hatv

import android.content.Context

/** App settings, stored locally on the TV. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hatv", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) = sp.edit().putString(KEY_SERVER, v.trim().trimEnd('/')).apply()

    var dashboardPath: String
        get() = sp.getString(KEY_PATH, DEFAULT_PATH) ?: DEFAULT_PATH
        set(v) {
            val p = v.trim().ifEmpty { DEFAULT_PATH }
            sp.edit().putString(KEY_PATH, if (p.startsWith("/")) p else "/$p").apply()
        }

    /** Optional Home Assistant long-lived access token for automatic login. */
    var accessToken: String
        get() = sp.getString(KEY_TOKEN, "") ?: ""
        set(v) = sp.edit().putString(KEY_TOKEN, v.trim()).apply()

    /** Page zoom in percent; 100 = the TV's normal size. */
    var zoomPercent: Int
        get() = sp.getInt(KEY_ZOOM, 100)
        set(v) = sp.edit().putInt(KEY_ZOOM, v.coerceIn(50, 250)).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_SCREEN, true)
        set(v) = sp.edit().putBoolean(KEY_SCREEN, v).apply()

    val dashboardUrl: String get() = serverUrl + dashboardPath

    /** Scheme + host + port only, e.g. http://192.168.0.29 */
    val origin: String
        get() = try {
            val u = java.net.URL(serverUrl)
            val port = if (u.port == -1 || u.port == u.defaultPort) "" else ":${u.port}"
            "${u.protocol}://${u.host}$port"
        } catch (e: Exception) {
            serverUrl
        }

    companion object {
        const val DEFAULT_SERVER = "http://192.168.0.29"
        const val DEFAULT_PATH = "/tv-dashboard/home"
        private const val KEY_SERVER = "server_url"
        private const val KEY_PATH = "dashboard_path"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_ZOOM = "zoom_percent"
        private const val KEY_SCREEN = "keep_screen_on"
    }
}
