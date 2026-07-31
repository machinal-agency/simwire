package app.simwire.gateway.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import app.simwire.gateway.core.GatewayBus
import app.simwire.gateway.core.JournalKind
import app.simwire.gateway.core.protocol.IncomingFrame
import app.simwire.gateway.core.queue.GatewayDb
import app.simwire.gateway.core.queue.IncomingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/** Static receiver: fires even when the app process was dead. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val from = messages.first().displayOriginatingAddress ?: "unknown"
        val text = messages.joinToString("") { it.displayMessageBody ?: "" }
        val subId = intent.getIntExtra("subscription", -1)
        val slot = slotForSubscription(context, subId)
        val receivedAt = System.currentTimeMillis()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = GatewayDb.get(context).dao()
                val rowId = dao.insertIncoming(
                    IncomingEntity(
                        sender = from,
                        text = text,
                        simSlot = slot,
                        receivedAt = receivedAt,
                        forwarded = false,
                    ),
                )
                GatewayBus.log(JournalKind.IN, "$from  $text")
                val frame = IncomingFrame(
                    from = from,
                    text = text,
                    simSlot = slot,
                    receivedAt = Instant.ofEpochMilli(receivedAt).toString(),
                )
                // Delivered live if a client is connected; otherwise it stays
                // buffered in Room and replays on the next hello.ack.
                if (GatewayBus.outbound.tryEmit(frame) && GatewayBus.status.value.clientName != null) {
                    dao.markForwarded(rowId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun slotForSubscription(context: Context, subscriptionId: Int): Int = try {
        if (subscriptionId < 0) 0
        else context.getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList.orEmpty()
            .firstOrNull { it.subscriptionId == subscriptionId }?.simSlotIndex ?: 0
    } catch (_: SecurityException) {
        0
    }
}
