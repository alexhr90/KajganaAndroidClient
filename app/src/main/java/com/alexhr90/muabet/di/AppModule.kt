package com.alexhr90.muabet.di

import com.alexhr90.muabet.BuildConfig
import com.alexhr90.muabet.data.remote.PolitenessInterceptor
import com.alexhr90.muabet.data.remote.WebViewCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}
