package io.github.daedalus1215.iris

import android.content.Context
import androidx.core.content.edit

/** What the app remembers between launches. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("iris", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(SERVER_URL, null) ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = prefs.edit { putString(SERVER_URL, value) }

    var token: String
        get() = prefs.getString(TOKEN, null).orEmpty()
        set(value) = prefs.edit { putString(TOKEN, value) }

    var selectedDeviceId: String?
        get() = prefs.getString(SELECTED_DEVICE, null)
        set(value) = prefs.edit { putString(SELECTED_DEVICE, value) }

    /** Arrow buttons in place of the touchpad. */
    var arrowButtons: Boolean
        get() = prefs.getBoolean(ARROW_BUTTONS, false)
        set(value) = prefs.edit { putBoolean(ARROW_BUTTONS, value) }

    private companion object {
        const val SERVER_URL = "server_url"
        const val TOKEN = "token"
        const val SELECTED_DEVICE = "selected_device"
        const val ARROW_BUTTONS = "arrow_buttons"
    }
}
