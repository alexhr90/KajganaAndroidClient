package com.alexhr90.muabet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.alexhr90.muabet.data.remote.WebViewCookieJar
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.notify.Notifications
import com.alexhr90.muabet.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MuabetApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var cookies: WebViewCookieJar

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        // The background check follows the notification settings.
        appScope.launch {
            settings.settings
                .map { Triple(it.notifications, it.notifyIntervalMin, it) }
                .distinctUntilChanged { a, b -> a.first == b.first && a.second == b.second }
                .collect { (_, _, s) -> runCatching { scheduler.sync(s) } }
        }
    }

    /**
     * Avatars and post images: decoded at display size, a 150 MB LRU disk cache (SPEC §7:
     * size-capped), the honest User-Agent. Images on the forum itself (attachments, the image
     * proxy) carry the forum's cookies, because the forum shows attachments to members only;
     * images on other sites never get them (the cookie jar only answers for the forum host).
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", BuildConfig.USER_AGENT).build()) }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .diskCache { DiskCache.Builder().directory(File(cacheDir, IMAGE_CACHE_DIR)).maxSizeBytes(150L * 1024 * 1024).build() }
            .crossfade(true)
            // Some image hosts send no-store headers; the cache is size-capped either way.
            .respectCacheHeaders(false)
            .build()
    }

    companion object {
        const val IMAGE_CACHE_DIR = "image_cache"
    }
}
