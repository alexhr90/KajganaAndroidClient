package com.alexhr90.muabet.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alexhr90.muabet.MainActivity
import com.alexhr90.muabet.R
import com.alexhr90.muabet.domain.url.ForumUrls
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alert notifications (SPEC §4 M4): one channel, one notification that says how many alerts and
 * direct messages are unread. It never shows their content (the app only reads the counters).
 */
@Singleton
class Notifications @Inject constructor(@ApplicationContext private val context: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_alerts_description) },
        )
    }

    /** False until the user allows notifications (Android 13+) and keeps them on for the app. */
    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showUnread(alerts: Int, messages: Int) {
        if (!canPost() || (alerts <= 0 && messages <= 0)) return
        val res = context.resources
        val title = when {
            alerts > 0 -> res.getQuantityString(R.plurals.notify_alerts, alerts, alerts)
            else -> res.getQuantityString(R.plurals.notify_messages, messages, messages)
        }
        val text = if (alerts > 0 && messages > 0) res.getQuantityString(R.plurals.notify_messages, messages, messages) else context.getString(R.string.notify_tap)
        val target = if (alerts > 0) ForumUrls.alerts else ForumUrls.conversations
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(target), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 8100, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_muabet)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setNumber(alerts + messages)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_ALERTS, n) }
    }

    fun clearAlerts() {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_ALERTS) }
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
        private const val ID_ALERTS = 1
    }
}
