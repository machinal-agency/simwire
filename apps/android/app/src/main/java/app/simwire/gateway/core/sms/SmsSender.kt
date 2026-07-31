package app.simwire.gateway.core.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import app.simwire.gateway.core.subscriptionIdForSlot

const val ACTION_SMS_SENT = "app.simwire.gateway.SMS_SENT"
const val ACTION_SMS_DELIVERED = "app.simwire.gateway.SMS_DELIVERED"
const val EXTRA_MESSAGE_ID = "message_id"

class SmsSender(private val context: Context) {

    /**
     * Hands the message to the radio. Terminal results arrive later through
     * the [ACTION_SMS_SENT] / [ACTION_SMS_DELIVERED] broadcasts.
     */
    fun send(messageId: String, recipients: List<String>, text: String, simSlot: Int?) {
        val manager = smsManagerFor(simSlot)
        for (recipient in recipients) {
            val parts = manager.divideMessage(text)
            val sentIntents = ArrayList<PendingIntent?>(parts.size)
            val deliveredIntents = ArrayList<PendingIntent?>(parts.size)
            for (i in parts.indices) {
                val last = i == parts.size - 1
                // Only the last part reports, so multipart messages emit one status.
                sentIntents.add(if (last) statusIntent(ACTION_SMS_SENT, messageId, i) else null)
                deliveredIntents.add(if (last) statusIntent(ACTION_SMS_DELIVERED, messageId, i) else null)
            }
            manager.sendMultipartTextMessage(recipient, null, parts, sentIntents, deliveredIntents)
        }
    }

    private fun statusIntent(action: String, messageId: String, part: Int): PendingIntent {
        val intent = Intent(action)
            .setPackage(context.packageName)
            .putExtra(EXTRA_MESSAGE_ID, messageId)
        val requestCode = (messageId.hashCode() * 31 + part) and 0x0FFFFFFF
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun smsManagerFor(simSlot: Int?): SmsManager {
        val subscriptionId = simSlot?.let { subscriptionIdForSlot(context, it) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (subscriptionId != null) base.createForSubscriptionId(subscriptionId) else base
        } else {
            @Suppress("DEPRECATION")
            if (subscriptionId != null) SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            else SmsManager.getDefault()
        }
    }
}
