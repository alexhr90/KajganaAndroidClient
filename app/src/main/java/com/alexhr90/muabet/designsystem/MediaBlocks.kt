package com.alexhr90.muabet.designsystem

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.alexhr90.muabet.R
import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.EmbedKind
import com.alexhr90.muabet.domain.url.ForumUrls
import java.net.URI

// Embedded media, tables, boxes, files and link cards, all shown in the post (v1.1): nothing in a
// post sends the reader to the forum's web page any more.

/** An embed: a video plays in place, music plays in a small player, a social post opens in its app. */
@Composable
fun EmbedBlock(block: Block.Embed, actions: PostActions) {
    when (block.kind) {
        EmbedKind.YouTube, EmbedKind.Video -> if (block.frameUrl != null) VideoEmbed(block, actions) else SiteCard(block, actions)
        EmbedKind.VideoFile -> VideoFileEmbed(block, actions)
        EmbedKind.Audio -> if (block.frameUrl != null) AudioEmbed(block, actions) else SiteCard(block, actions)
        EmbedKind.Social, EmbedKind.Link -> SiteCard(block, actions)
    }
}

/** The site's own name for an embed: YouTube, Vimeo, Spotify, X… */
@Composable
fun siteName(site: String?, url: String): String = when (site?.lowercase()) {
    "youtube" -> "YouTube"
    "vimeo" -> "Vimeo"
    "dailymotion" -> "Dailymotion"
    "streamable" -> "Streamable"
    "spotify" -> "Spotify"
    "soundcloud" -> "SoundCloud"
    "x", "twitter" -> "X"
    "instagram" -> "Instagram"
    "tiktok" -> "TikTok"
    "facebook" -> "Facebook"
    "reddit" -> "Reddit"
    "twitch" -> "Twitch"
    "imgur" -> "Imgur"
    "video", null, "" -> hostOf(url)
    else -> site.replaceFirstChar { it.uppercase() }
}

/**
 * A video from a site: its thumbnail (or a dark frame) with a play button; a tap plays it right
 * there in the site's own player, full screen from the player's button.
 */
@Composable
private fun VideoEmbed(block: Block.Embed, actions: PostActions) {
    var playing by remember(block.frameUrl) { mutableStateOf(false) }
    val name = siteName(block.site, block.url)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black),
        ) {
            if (playing) {
                FramePlayer(block.frameUrl!!, Modifier.fillMaxSize(), onExternal = actions.onLink, background = Color.Black)
            } else {
                if (block.thumbnail != null) {
                    AsyncImage(model = block.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                val play = stringResource(R.string.cd_play, name)
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(role = Role.Button, onClickLabel = play) { playing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = play, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
            }
        }
        EmbedFooter(name, onOpen = { actions.onLink(block.url) })
    }
}

/** Under a player: the site and "Open in the app". */
@Composable
private fun EmbedFooter(name: String, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onOpen) {
            Text(stringResource(R.string.embed_open_in, name), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(Space.xs))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** Music (Spotify, SoundCloud…): a card; a tap loads the site's small player in place. */
@Composable
private fun AudioEmbed(block: Block.Embed, actions: PostActions) {
    var playing by remember(block.frameUrl) { mutableStateOf(false) }
    val name = siteName(block.site, block.url)
    if (!playing) {
        MediaCard(
            title = stringResource(R.string.embed_listen_here),
            subtitle = name,
            icon = Icons.Outlined.Audiotrack,
            onClick = { playing = true },
            trailing = {
                IconButton(onClick = { actions.onLink(block.url) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.embed_open_in, name))
                }
            },
        )
        return
    }
    val compact = block.url.contains("/track/") || block.url.contains("/episode/") || block.site == "soundcloud"
    val height = when {
        block.site == "soundcloud" -> 166.dp
        compact -> 152.dp
        else -> 352.dp
    }
    Column(Modifier.fillMaxWidth()) {
        FramePlayer(
            block.frameUrl!!,
            Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.small),
            onExternal = actions.onLink,
            background = Color.Transparent,
        )
        EmbedFooter(name, onOpen = { actions.onLink(block.url) })
    }
}

/**
 * A post on a social site or another page: a card that opens it in the site's app (or the
 * browser). When the site has an embed player, "Show here" plays it in place.
 */
@Composable
private fun SiteCard(block: Block.Embed, actions: PostActions) {
    var shown by remember(block.url) { mutableStateOf(false) }
    val name = siteName(block.site, block.url)
    val frame = block.frameUrl
    if (shown && frame != null) {
        Column(Modifier.fillMaxWidth()) {
            FramePlayer(frame, Modifier.fillMaxWidth().height(560.dp).clip(MaterialTheme.shapes.small), onExternal = actions.onLink, background = Color.Transparent)
            EmbedFooter(name, onOpen = { actions.onLink(block.url) })
        }
        return
    }
    MediaCard(
        title = if (block.kind == EmbedKind.Social) stringResource(R.string.embed_post_on, name) else name,
        subtitle = shortUrl(block.url),
        icon = if (block.kind == EmbedKind.Social) Icons.Outlined.Public else Icons.Rounded.PlayArrow,
        onClick = { actions.onLink(block.url) },
        trailing = if (frame != null) {
            { TextButton(onClick = { shown = true }) { Text(stringResource(R.string.embed_show_here)) } }
        } else {
            null
        },
    )
}

/** A video file (an attachment, an mp4 link): the system player in place, full screen on demand. */
@Composable
private fun VideoFileEmbed(block: Block.Embed, actions: PostActions) {
    var playing by remember(block.url) { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (playing && !fullscreen) {
                VideoFilePlayer(block.url, startAt = position, onPosition = { position = it }, modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = { fullscreen = true },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) { Icon(Icons.Outlined.Fullscreen, contentDescription = stringResource(R.string.cd_fullscreen), tint = Color.White) }
            } else {
                val play = stringResource(R.string.cd_play, hostOf(block.url))
                Box(
                    Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = play) { playing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = play, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
        }
    }
    if (fullscreen) {
        FullscreenDialog(onDismiss = { fullscreen = false }) {
            VideoFilePlayer(block.url, startAt = position, onPosition = { position = it }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun VideoFilePlayer(url: String, startAt: Int, onPosition: (Int) -> Unit, modifier: Modifier) {
    val report by rememberUpdatedState(onPosition)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FrameLayout(ctx).apply {
                val video = VideoView(ctx)
                addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
                val controller = MediaController(ctx)
                controller.setAnchorView(video)
                video.setMediaController(controller)
                // Forum attachments need the session; other hosts get no cookies.
                val headers = HashMap<String, String>()
                val host = runCatching { URI(url).host }.getOrNull()
                if (ForumUrls.isForumHost(host)) CookieManager.getInstance().getCookie(url)?.let { headers["Cookie"] = it }
                video.setVideoURI(Uri.parse(url), headers)
                video.setOnPreparedListener { mp ->
                    if (startAt > 0) video.seekTo(startAt)
                    mp.setOnVideoSizeChangedListener { _, _, _ -> video.requestLayout() }
                    video.start()
                }
                tag = video
            }
        },
        onRelease = { layout ->
            (layout.tag as? VideoView)?.let { v ->
                report(v.currentPosition)
                v.stopPlayback()
            }
        },
    )
}

/** A full-screen black dialog (video files); turns the phone sideways while it is open. */
@Composable
private fun FullscreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (before != null) activity?.requestedOrientation = before }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * A site's player (YouTube, Vimeo, Spotify…) in a small WebView, as the forum embeds it. The page
 * around it is the forum's address, so players that check where they are embedded work. Links the
 * player opens go to the site's app; its full-screen button fills the screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FramePlayer(frameUrl: String, modifier: Modifier, onExternal: (String) -> Unit, background: Color) {
    val external by rememberUpdatedState(onExternal)
    var custom by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    val bg = background.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(if (background == Color.Transparent) AndroidColor.TRANSPARENT else bg)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.setSupportMultipleWindows(true)
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Only the player page itself loads here; anything the user follows opens outside.
                        if (!request.isForMainFrame) return false
                        external(request.url.toString())
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                        custom = view to callback
                    }

                    override fun onHideCustomView() {
                        custom = null
                    }

                    // "Watch on YouTube" and similar open a new window: send its address outside.
                    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                        val catcher = WebView(view.context)
                        catcher.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                                external(request.url.toString())
                                v.destroy()
                                return true
                            }
                        }
                        (resultMsg.obj as? WebView.WebViewTransport)?.webView = catcher
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                loadDataWithBaseURL(ForumUrls.home, frameHtml(frameUrl, cssColor(background)), "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
    custom?.let { (view, callback) ->
        FullscreenDialog(onDismiss = { callback.onCustomViewHidden(); custom = null }) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        (view.parent as? ViewGroup)?.removeView(view)
                        addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                onRelease = { it.removeAllViews() },
            )
        }
    }
}

private fun frameHtml(src: String, background: String): String =
    """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>""" +
        """html,body{margin:0;padding:0;height:100%;background:$background;overflow:hidden}""" +
        """iframe{position:fixed;top:0;left:0;width:100%;height:100%;border:0}</style></head><body>""" +
        """<iframe src="${attr(src)}" allow="autoplay; encrypted-media; fullscreen; picture-in-picture; clipboard-write" """ +
        """allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>"""

private fun attr(s: String): String = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

private fun cssColor(c: Color): String =
    if (c.alpha == 0f) "transparent" else "rgb(${(c.red * 255).toInt()},${(c.green * 255).toInt()},${(c.blue * 255).toInt()})"

/**
 * Forum markup the app has no native view for, rendered in place by a small transparent WebView
 * in the app's colours and as tall as its content. Links open in the app.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun HtmlBlock(html: String, onLink: (String) -> Unit) {
    val link by rememberUpdatedState(onLink)
    val density = LocalDensity.current
    var heightPx by remember(html) { mutableIntStateOf(0) }
    val text = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val page = remember(html, text, accent, muted) { htmlPage(html, cssColor(text), cssColor(accent), cssColor(muted)) }
    val height: Dp = with(density) { heightPx.toDp() }.coerceIn(24.dp, 4000.dp)
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(height),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mediaPlaybackRequiresUserGesture = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun height(cssPx: Int) {
                            post { heightPx = (cssPx * resources.displayMetrics.density).toInt() }
                        }
                    },
                    "MuabetFrame",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        link(request.url.toString())
                        return true
                    }
                }
                tag = page
                loadDataWithBaseURL(ForumUrls.home, page, "text/html", "utf-8", null)
            }
        },
        update = { view ->
            if (view.tag != page) {
                view.tag = page
                view.loadDataWithBaseURL(ForumUrls.home, page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun htmlPage(body: String, text: String, accent: String, muted: String): String =
    """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>""" +
        """html,body{margin:0;padding:0;background:transparent;color:$text;font:16px/1.45 sans-serif;word-wrap:break-word}""" +
        """a{color:$accent}img,video,iframe{max-width:100%;height:auto;border:0}iframe{width:100%;min-height:200px}""" +
        """table{border-collapse:collapse;max-width:100%}td,th{border:1px solid $muted;padding:4px 6px}""" +
        """blockquote{margin:0;padding-left:12px;border-left:2px solid $accent;color:$muted}</style></head><body>""" +
        body +
        """<script>(function(){document.querySelectorAll('[data-s9e-mediaembed-src]').forEach(function(f){if(!f.getAttribute('src'))f.src=f.getAttribute('data-s9e-mediaembed-src')});""" +
        """document.querySelectorAll('img[data-src]').forEach(function(i){if(!i.getAttribute('src'))i.src=i.getAttribute('data-src')});""" +
        """function h(){MuabetFrame.height(Math.ceil(document.documentElement.scrollHeight))}""" +
        """if(window.ResizeObserver)new ResizeObserver(h).observe(document.body);window.addEventListener('load',h);setTimeout(h,500);h()})()</script>""" +
        """</body></html>"""

/** A table: rows of cells on a grid that scrolls sideways when it is wider than the post. */
@Composable
fun TableBlock(block: Block.Table, actions: PostActions) {
    val columns = block.rows.maxOf { it.size }
    // Column widths from the longest text in each column, within reason.
    val widths = remember(block) {
        (0 until columns).map { c ->
            val longest = block.rows.maxOf { row -> row.getOrNull(c)?.let { cellLength(it.blocks) } ?: 0 }
            (longest * 8).coerceIn(72, 260).dp
        }
    }
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.glass(MaterialTheme.shapes.small)) {
            block.rows.forEach { row ->
                Row {
                    (0 until columns).forEach { c ->
                        val cell = row.getOrNull(c)
                        Box(
                            Modifier
                                .width(widths[c])
                                .border(0.5.dp, line)
                                .then(if (cell?.header == true) Modifier.background(glassColorStrong) else Modifier)
                                .padding(Space.s),
                        ) {
                            if (cell != null) BlockList(cell.blocks, actions)
                        }
                    }
                }
            }
        }
    }
}

private fun cellLength(blocks: List<Block>): Int = blocks.sumOf { b ->
    when (b) {
        is Block.Paragraph -> b.runs.sumOf { it.text.length }
        is Block.Image -> 20
        else -> 12
    }
}

/** A titled box the forum draws around content ("Info", hidden content, an add-on's block). */
@Composable
fun BoxBlock(block: Block.Box, actions: PostActions) {
    Column(
        Modifier.fillMaxWidth().glass(MaterialTheme.shapes.small).padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (block.title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s))
                Text(block.title!!, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        BlockList(block.blocks, actions)
    }
}

/** An attached file: its name and size; a tap downloads it with the session. */
@Composable
fun FileBlock(block: Block.File, actions: PostActions) {
    MediaCard(
        title = block.name,
        subtitle = block.meta ?: hostOf(block.url),
        icon = Icons.Outlined.InsertDriveFile,
        onClick = { actions.onDownload(block.url, block.name) },
        trailing = { Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.cd_download), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = Space.m)) },
    )
}

/** A link preview: the page's picture, title, description and site. */
@Composable
fun LinkCard(block: Block.Link, actions: PostActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.small)
            .clickable(role = Role.Button) { actions.onLink(block.url) }
            .heightIn(min = 56.dp)
            .padding(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (block.image != null && !LocalImagesOnTap.current) {
            AsyncImage(
                model = block.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small).background(glassColorStrong),
            )
        } else {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(block.title ?: shortUrl(block.url), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
            block.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(hostOf(block.url), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.small)
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 56.dp)
            .padding(start = Space.m, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Spacer(Modifier.width(Space.m))
    }
}

internal fun hostOf(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

private fun shortUrl(url: String): String = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").let {
    if (it.length > 48) it.take(47) + "…" else it
}
