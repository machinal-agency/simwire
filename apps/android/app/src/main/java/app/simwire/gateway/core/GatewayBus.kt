package app.simwire.gateway.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import app.simwire.gateway.core.pairing.TokenStore
import app.simwire.gateway.core.protocol.DeviceFrame
import app.simwire.gateway.core.protocol.DeviceInfo
import app.simwire.gateway.core.protocol.SimSlot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class JournalKind { IN, OUT, SYS }

data class JournalEntry(val time: String, val kind: JournalKind, val text: String)

data class GatewayStatus(
    val running: Boolean = false,
    val clientName: String? = null,
    val lanAddress: String? = null,
)

/** Single in-process hub: server, receivers and UI all talk through here. */
object GatewayBus {
    /** Frames waiting to be written to the connected client's socket. */
    val outbound = MutableSharedFlow<DeviceFrame>(extraBufferCapacity = 64)

    private val _status = MutableStateFlow(GatewayStatus())
    val status: StateFlow<GatewayStatus> = _status.asStateFlow()

    private val _journal = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journal: StateFlow<List<JournalEntry>> = _journal.asStateFlow()

    fun setRunning(running: Boolean, lanAddress: String? = null) {
        _status.value = _status.value.copy(running = running, lanAddress = lanAddress)
        if (!running) _status.value = _status.value.copy(clientName = null)
    }

    fun setClient(name: String?) {
        _status.value = _status.value.copy(clientName = name)
    }

    fun log(kind: JournalKind, text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _journal.value = (_journal.value + JournalEntry(time, kind, text)).takeLast(200)
    }
}

fun buildDeviceInfo(context: Context): DeviceInfo {
    val store = TokenStore(context)
    return DeviceInfo(
        id = store.deviceId,
        name = Build.MODEL,
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = Build.VERSION.RELEASE,
        simSlots = readSimSlots(context),
    )
}

@SuppressLint("MissingPermission")
fun readSimSlots(context: Context): List<SimSlot> = try {
    val sm = context.getSystemService(SubscriptionManager::class.java)
    sm.activeSubscriptionInfoList.orEmpty().map { info ->
        SimSlot(
            index = info.simSlotIndex,
            carrier = info.carrierName?.toString(),
            phoneNumber = null,
        )
    }
} catch (_: SecurityException) {
    emptyList()
}

fun subscriptionIdForSlot(context: Context, slotIndex: Int): Int? = try {
    val sm = context.getSystemService(SubscriptionManager::class.java)
    @SuppressLint("MissingPermission")
    val subs = sm.activeSubscriptionInfoList.orEmpty()
    subs.firstOrNull { it.simSlotIndex == slotIndex }?.subscriptionId
} catch (_: SecurityException) {
    null
}
