package com.alexhr90.muabet

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.io.File

@HiltAndroidApp
class MuabetApp : Application(), ImageLoaderFactory {

    /**
     * Avatars and post images: decoded at display size, a 150 MB LRU disk cache (SPEC §7:
     * size-capped), the honest User-Agent. Images use their own client without forum cookies.
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", BuildConfig.USER_AGENT).build()) }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .diskCache { DiskCache.Builder().directory(File(cacheDir, IMAGE_CACHE_DIR)).maxSizeBytes(150L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }

    companion object {
        const val IMAGE_CACHE_DIR = "image_cache"
    }
}
