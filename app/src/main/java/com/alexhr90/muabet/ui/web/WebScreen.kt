package com.alexhr90.muabet.ui.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.rememberCustomTabOpener

/**
 * The forum's own pages in a WebView (SPEC §3): login, posting, reactions, messages and anything
 * not native yet. It shares cookies with the app's OkHttp client through [CookieManager]. The
 * WebView keeps its own User-Agent: this is the user browsing.
 *
 * In [loginMode] the screen closes by itself once the page has left the login form and the forum
 * marks the visitor as logged in.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(
    url: String,
    onBack: () -> Unit,
    loginMode: Boolean = false,
    onLoggedIn: () -> Unit = {},
) {
    val context = LocalContext.current
    val openTab = rememberCustomTabOpener(context)
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var current by remember { mutableStateOf(url) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val done by rememberUpdatedState(onLoggedIn)
    val externalTab by rememberUpdatedState(openTab)

    BackHandler(enabled = canGoBack) { webView?.goBack() }
    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush() }
    }

    ScreenScaffold(
        title = title.ifBlank { stringResource(if (loginMode) R.string.action_log_in else R.string.web_title) },
        onBack = onBack,
        titleInBar = true,
        actions = {
            IconButton(onClick = { openTab(current) }) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.action_open_in_browser))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.destroy() },
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val target = request.url
                                // Forum pages stay here; everything else opens in a Custom Tab.
                                if (target.scheme == "https" && ForumUrls.isForumHost(target.host)) return false
                                if (target.scheme == "http" || target.scheme == "https") externalTab(target.toString())
                                return true
                            }

                            override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                                current = pageUrl ?: current
                            }

                            override fun onPageFinished(view: WebView, pageUrl: String?) {
                                title = view.title.orEmpty().substringBefore(" | ")
                                canGoBack = view.canGoBack()
                                CookieManager.getInstance().flush()
                                // Logged in once the page has left the form and XenForo marks the visitor as a member.
                                if (loginMode && pageUrl != null && !pageUrl.contains("/login")) {
                                    view.evaluateJavascript("document.documentElement.getAttribute('data-logged-in')") { flag ->
                                        if (flag == "\"true\"") done()
                                    }
                                }
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
            )
            if (progress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
