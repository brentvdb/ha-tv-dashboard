package com.brentvdb.hatv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Settings screen, laid out for a TV remote (large text, clear focus highlight). */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(32), dp(64), dp(32))
        }

        column.addView(TextView(this).apply {
            text = "Home Assistant TV – Settings"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#22D3C5"))
            setPadding(0, 0, 0, dp(16))
        })

        val server = field(column, "Home Assistant address", prefs.serverUrl,
            "e.g. http://192.168.0.29  (include :8123 if your server uses it)", InputType.TYPE_TEXT_VARIATION_URI)
        val areaFields = Prefs.AREAS.map { area ->
            area to field(column, "${area.title} – dashboard page", prefs.areaPath(area),
                "e.g. ${area.defaultPath}", InputType.TYPE_TEXT_VARIATION_URI)
        }
        val token = field(column, "Access token (optional – automatic sign-in)", prefs.accessToken,
            "Leave empty to sign in once with your username and password", InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val zoom = field(column, "Zoom %", prefs.zoomPercent.toString(),
            "100 = normal. Try 125–150 to make tiles bigger", InputType.TYPE_CLASS_NUMBER)

        val keepOn = CheckBox(this).apply {
            text = "Keep the screen on while the dashboard is open"
            textSize = 20f
            isChecked = prefs.keepScreenOn
            setTextColor(Color.parseColor("#E6EDF5"))
            background = focusBackground()
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        column.addView(keepOn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        buttons.addView(button("Save") {
            prefs.serverUrl = server.text.toString().ifBlank { Prefs.DEFAULT_SERVER }
            areaFields.forEach { (area, edit) -> prefs.setAreaPath(area, edit.text.toString()) }
            prefs.accessToken = token.text.toString()
            prefs.zoomPercent = zoom.text.toString().toIntOrNull() ?: 100
            prefs.keepScreenOn = keepOn.isChecked
            finish()
        })
        buttons.addView(button("Sign out / clear data") {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            prefs.accessToken = ""
            token.setText("")
            Toast.makeText(this, "Signed out. You'll be asked to sign in again.", Toast.LENGTH_LONG).show()
        })
        buttons.addView(button("Cancel") { finish() })
        column.addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(24) })

        column.addView(TextView(this).apply {
            text = "Remote: arrows move · OK selects · Back closes / goes back · long-press Back = Settings"
            textSize = 16f
            setTextColor(Color.parseColor("#93A4BA"))
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1220"))
            addView(column)
        })
        server.requestFocus()
    }

    private fun field(parent: LinearLayout, label: String, value: String, hint: String, variation: Int): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 20f
            setTextColor(Color.parseColor("#E6EDF5"))
            setPadding(0, dp(16), 0, dp(4))
        })
        val edit = EditText(this).apply {
            setText(value)
            this.hint = hint
            textSize = 20f
            isSingleLine = true
            inputType = if (variation == InputType.TYPE_CLASS_NUMBER) variation
                        else InputType.TYPE_CLASS_TEXT or variation
            setTextColor(Color.parseColor("#E6EDF5"))
            setHintTextColor(Color.parseColor("#5D6B80"))
            background = focusBackground()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        parent.addView(edit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return edit
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 20f
        isAllCaps = false
        setTextColor(Color.parseColor("#0B1220"))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded("#22D3C5", "#FFFFFF", 4))
            addState(intArrayOf(), rounded("#7FA9B5", "#7FA9B5", 0))
        }
        setPadding(dp(24), dp(12), dp(24), dp(12))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(16) }
    }

    /** Surface with a thick teal border when focused, so the remote's position is obvious. */
    private fun focusBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded("#142033", "#22D3C5", 4))
        addState(intArrayOf(), rounded("#142033", "#24334A", 1))
    }

    private fun rounded(fill: String, stroke: String, strokeDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        cornerRadius = dp(10).toFloat()
        setStroke(dp(strokeDp.coerceAtLeast(0)), Color.parseColor(stroke))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
