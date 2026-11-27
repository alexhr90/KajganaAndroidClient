package com.alexhr90.muabet.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alexhr90.muabet.data.local.WatchedCacheDao
import com.alexhr90.muabet.data.local.WatchedCacheEntity
import com.alexhr90.muabet.data.remote.FetchResult
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.data.settings.AlertState
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.domain.parse.SessionParser
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.notify.Notifications
import com.alexhr90.muabet.widget.WatchedWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime

/**
 * The background check (SPEC §4 M4), at most every 15 minutes: one small forum page for the
 * unread alerts and messages badges (reading the badge does not mark anything as read), and the
 * Watched list only while the widget is on the home screen.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val forum: ForumRepository,
    private val settings: SettingsRepository,
    private val notifications: Notifications,
    private val watched: WatchedCacheDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        if (s.notifications) checkAlerts(s.isQuiet(LocalTime.now().hour))
        if (WatchedWidget.isPlaced(applicationContext)) refreshWatched()
        return Result.success()
    }

    private suspend fun checkAlerts(quiet: Boolean) {
        // In quiet hours nothing is fetched; the counters stay, so the news arrives afterwards.
        if (quiet) return
        val r = forum.raw(BADGE_PAGE) as? FetchResult.Page ?: return
        val info = runCatching { SessionParser.parse(r.html, r.url) }.getOrNull() ?: return
        forum.noteSession(info)
        if (!info.loggedIn) return
        val before = settings.alertState()
        val more = info.alertsUnread > before.lastAlerts || info.conversationsUnread > before.lastConversations
        if (more) notifications.showUnread(info.alertsUnread, info.conversationsUnread)
        if (info.alertsUnread == 0 && info.conversationsUnread == 0) notifications.clearAlerts()
        settings.setAlertState(AlertState(info.alertsUnread, info.conversationsUnread))
    }

    private suspend fun refreshWatched() {
        val list = (forum.threadList(ForumUrls.watchedThreads) as? Loaded.Ok)?.value ?: return
        watched.replace(
            list.threads.take(WIDGET_ROWS).mapIndexed { i, t ->
                WatchedCacheEntity(t.id, t.slug, t.title, t.unread, t.lastPostAt, t.lastPoster, i)
            },
        )
        WatchedWidget.refreshAll(applicationContext)
    }

    companion object {
        /** A small page that carries the navigation badges. */
        val BADGE_PAGE = "${ForumUrls.BASE}/help/"
        const val WIDGET_ROWS = 20
    }
}
