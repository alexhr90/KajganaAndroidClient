package com.alexhr90.muabet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.alexhr90.muabet.MainActivity
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * The home-screen widget for watched threads (SPEC §4 M4), on plain RemoteViews. The list comes
 * from the last Watched list the app or the background check loaded; rows open the thread at the
 * first unread post.
 */
@AndroidEntryPoint
class WatchedWidget : AppWidgetProvider() {
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var settings: SettingsRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, it)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    override fun onEnabled(context: Context) {
        reschedule()
        runCatching { scheduler.runOnce() }
    }

    override fun onDisabled(context: Context) = reschedule()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) runCatching { scheduler.runOnce() }
    }

    private fun reschedule() {
        runCatching { scheduler.sync(runBlocking { settings.current() }) }
    }

    companion object {
        const val ACTION_REFRESH = "com.alexhr90.muabet.widget.REFRESH"

        fun isPlaced(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            return manager.getAppWidgetIds(ComponentName(context, WatchedWidget::class.java)).isNotEmpty()
        }

        /** Redraws every placed widget; cheap when none is placed. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, WatchedWidget::class.java))
            if (ids.isEmpty()) return
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun views(context: Context, widgetId: Int): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_watched)
            val list = Intent(context, WatchedWidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            list.data = Uri.parse(list.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.widget_list, list)
            v.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val open = Intent(Intent.ACTION_VIEW, Uri.parse(ForumUrls.watchedThreads), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val openPending = PendingIntent.getActivity(context, 7100, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.widget_title, openPending)
            v.setOnClickPendingIntent(R.id.widget_empty, openPending)

            val refresh = Intent(context, WatchedWidget::class.java).setAction(ACTION_REFRESH)
            v.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 7102, refresh, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

            // Each row fills in its thread's address; the template must stay mutable for that.
            val template = Intent(Intent.ACTION_VIEW, null, context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            v.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(context, 7103, template, mutable or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }
    }
}
