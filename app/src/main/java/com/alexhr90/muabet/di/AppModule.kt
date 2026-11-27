package com.alexhr90.muabet.di

import android.content.Context
import androidx.room.Room
import com.alexhr90.muabet.BuildConfig
import com.alexhr90.muabet.data.local.LibraryDao
import com.alexhr90.muabet.data.local.MuabetDatabase
import com.alexhr90.muabet.data.local.ReadingDao
import com.alexhr90.muabet.data.local.SavedDao
import com.alexhr90.muabet.data.local.WatchedCacheDao
import com.alexhr90.muabet.data.remote.PolitenessInterceptor
import com.alexhr90.muabet.data.remote.WebViewCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun cookieJar(): WebViewCookieJar = WebViewCookieJar()

    /** One client for the forum: shared WebView cookies, politeness rules and the honest User-Agent. */
    @Provides
    @Singleton
    fun okHttp(cookies: WebViewCookieJar): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(PolitenessInterceptor(BuildConfig.USER_AGENT))
        .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MuabetDatabase =
        Room.databaseBuilder(context, MuabetDatabase::class.java, MuabetDatabase.NAME).build()

    @Provides fun libraryDao(db: MuabetDatabase): LibraryDao = db.library()
    @Provides fun readingDao(db: MuabetDatabase): ReadingDao = db.reading()
    @Provides fun savedDao(db: MuabetDatabase): SavedDao = db.saved()
    @Provides fun watchedDao(db: MuabetDatabase): WatchedCacheDao = db.watched()
}
