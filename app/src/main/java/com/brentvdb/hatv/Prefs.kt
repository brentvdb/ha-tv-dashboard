package com.brentvdb.hatv

import android.content.Context

/** One choice on the start screen. */
data class Area(val key: String, val title: String, val subtitle: String, val defaultPath: String)

/** App settings, stored locally on the TV. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hatv", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) = sp.edit().putString(KEY_SERVER, v.trim().trimEnd('/')).apply()

    /** Dashboard page opened for an area, e.g. /main-house/home */
    fun areaPath(area: Area): String = sp.getString("path_${area.key}", area.defaultPath) ?: area.defaultPath

    fun setAreaPath(area: Area, value: String) {
        val p = value.trim().ifEmpty { area.defaultPath }
        sp.edit().putString("path_${area.key}", if (p.startsWith("/")) p else "/$p").apply()
    }

    fun areaUrl(area: Area): String = serverUrl + areaPath(area)

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

    /** Scheme + host + port only, e.g. http://192.168.0.29 */
    val origin: String
        get() = try {
            val u = java.net.URL(serverUrl)
            val port = if (u.port == -1 || u.port == u.defaultPort) "" else ":${u.port}"
            "${u.protocol}://${u.host}$port"
        } catch (e: Exception) {
            serverUrl
        }

    /** Everything that, when changed, requires the dashboard view to be rebuilt. */
    val pageConfig: String
        get() = listOf(serverUrl, accessToken, zoomPercent).joinToString("|")

    companion object {
        const val DEFAULT_SERVER = "http://192.168.0.29"
        private const val KEY_SERVER = "server_url"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_ZOOM = "zoom_percent"
        private const val KEY_SCREEN = "keep_screen_on"

        val AREAS = listOf(
            Area("main", "Main House", "Downstairs", "/main-house/home"),
            Area("upstairs", "Upstairs House", "The flat", "/upstairs-house/home"),
            Area("irrigation", "Irrigation", "Garden stations & schedule", "/irrigation-control/control"),
        )
    }
}
