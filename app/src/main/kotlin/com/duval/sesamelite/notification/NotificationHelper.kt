package com.duval.sesamelite.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duval.sesamelite.MainActivity
import com.duval.sesamelite.R
import com.duval.sesamelite.crypto.CryptoManager
import com.duval.sesamelite.crypto.DecryptionResult
import com.duval.sesamelite.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NotificationHelper {

    const val CHANNEL_ID = "sesame_arrivals"
    const val EXTRA_ENTRY_ID = "entry_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun sendArrivalNotification(context: Context, entryId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(context).accessCodeDao()
            val entry = dao.getById(entryId) ?: return@launch
            if (entry.isSilenced) return@launch

            val bodyLines = mutableListOf<String>()

            // Resolve code
            if (entry.code != null) {
                val displayCode = when (val r = CryptoManager.decrypt(entry.code)) {
                    is DecryptionResult.Success -> context.getString(R.string.notification_code_body, r.value)
                    is DecryptionResult.LegacyPlainText -> context.getString(R.string.notification_code_body, r.value)
                    is DecryptionResult.KeyUnavailable -> context.getString(R.string.notification_code_key_unavailable)
                    is DecryptionResult.UnknownVersion -> context.getString(R.string.notification_code_unknown_version)
                }
                bodyLines.add(displayCode)
            }

            // Resolve location details
            if (!entry.locationDetails.isNullOrEmpty()) {
                when (val r = CryptoManager.decrypt(entry.locationDetails)) {
                    is DecryptionResult.Success -> if (r.value.isNotEmpty()) bodyLines.add(r.value)
                    is DecryptionResult.LegacyPlainText -> if (r.value.isNotEmpty()) bodyLines.add(r.value)
                    else -> {}
                }
            }

            if (bodyLines.isEmpty()) return@launch

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_ENTRY_ID, entryId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, entryId.hashCode(), tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(entry.label)
                .setContentText(bodyLines.joinToString("\n"))
                .setStyle(NotificationCompat.BigTextStyle().bigText(bodyLines.joinToString("\n")))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context)
                    .notify(entryId.hashCode(), notif)
            }
        }
    }
}
