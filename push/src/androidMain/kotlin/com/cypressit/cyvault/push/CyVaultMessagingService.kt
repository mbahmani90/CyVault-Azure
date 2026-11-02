package com.cypressit.cyvault.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Receives the server's pushes. They carry data only, so this builds the notification itself, in
 * the phone's language, even while the app isn't running.
 */
class CyVaultMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != TRANSFER_RECEIVED) return
        val amount = data["amount"] ?: return
        val currency = data["currency"] ?: return
        val from = data["from"] ?: return
        showTransferReceived(data["transferId"].orEmpty(), formatAmount(amount), currency, from)
    }

    // The app sends the current token to the server each time it starts, so a new one needs
    // nothing here: it's sent on the next start.
    override fun onNewToken(token: String) = Unit

    private fun showTransferReceived(transferId: String, amount: String, currency: String, from: String) {
        // Android 13+ shows nothing until the user allows notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel()

        val title = getString(R.string.push_transfer_received_title)
        // On the lock screen, only the title: the amount and the sender stay hidden until unlocked.
        val lockScreen = NotificationCompat.Builder(this, CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_push_notification)
            .setContentTitle(title)
            .build()
        val notification = NotificationCompat.Builder(this, CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_push_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.push_transfer_received_body, amount, currency, from))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(transferId.hashCode(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_TRANSFERS,
            getString(R.string.push_channel_transfers),
            NotificationManager.IMPORTANCE_HIGH,
        )
        // Creating a channel that exists only updates its name.
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // Tapping the notification opens the app on its start screen.
    private fun openApp(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        // Matches the server's PushNotifications.TransferReceivedType.
        const val TRANSFER_RECEIVED = "transfer_received"
        const val CHANNEL_TRANSFERS = "transfers"

        // The server sends a plain decimal ("20.5"); money shows two decimals ("20.50").
        fun formatAmount(amount: String): String =
            runCatching { BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toPlainString() }
                .getOrDefault(amount)
    }
}
