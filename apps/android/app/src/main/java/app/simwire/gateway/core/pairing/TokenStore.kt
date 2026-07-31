package app.simwire.gateway.core.pairing

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("simwire", Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString()
            .also { prefs.edit { putString(KEY_DEVICE_ID, it) } }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    var clientName: String?
        get() = prefs.getString(KEY_CLIENT, null)
        set(value) = prefs.edit { putString(KEY_CLIENT, value) }

    val isPaired: Boolean get() = token != null

    fun clear() = prefs.edit { remove(KEY_TOKEN); remove(KEY_CLIENT) }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TOKEN = "token"
        const val KEY_CLIENT = "client_name"
    }
}
