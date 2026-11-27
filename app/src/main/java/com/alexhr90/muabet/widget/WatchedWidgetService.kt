package com.alexhr90.muabet.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.local.WatchedCacheDao
import com.alexhr90.muabet.data.local.WatchedCacheEntity
import com.alexhr90.muabet.domain.url.ForumUrls
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Feeds the widget's list from the cached Watched threads. */
@AndroidEntryPoint
class WatchedWidgetService : RemoteViewsService() {
    @Inject lateinit var watched: WatchedCacheDao

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext, watched)

    private class Factory(private val context: Context, private val dao: WatchedCacheDao) : RemoteViewsFactory {
        private var rows: List<WatchedCacheEntity> = emptyList()

        override fun onCreate() = Unit
        override fun onDestroy() { rows = emptyList() }

        /** Runs on a binder thread, so blocking is allowed here. */
        override fun onDataSetChanged() {
            rows = runCatching { runBlocking { dao.top(20) } }.getOrDefault(emptyList())
        }

        override fun getCount() = rows.size
        override fun getViewTypeCount() = 1
        override fun hasStableIds() = true
        override fun getItemId(position: Int) = rows.getOrNull(position)?.threadId ?: position.toLong()
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_watched_item)
            val row = rows.getOrNull(position) ?: return v
            val title = if (row.unread) "● ${row.title}" else row.title
            v.setTextViewText(R.id.widget_item_title, title)
            val ago = row.lastPostAt?.let {
                DateUtils.getRelativeTimeSpanString(it * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
            }
            v.setTextViewText(R.id.widget_item_meta, listOfNotNull(row.lastPoster, ago).joinToString(" · "))
            val target = if (row.unread) ForumUrls.threadUnread(row.slug, row.threadId) else ForumUrls.threadLatest(row.slug, row.threadId)
            v.setOnClickFillInIntent(R.id.widget_item, Intent().setData(Uri.parse(target)))
            return v
        }
    }
}
