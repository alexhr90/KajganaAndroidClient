package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.EmbedKind
import java.net.URI
import java.net.URLEncoder

/**
 * Recognises embedded media from whatever the forum's markup carries: XenForo's own media BB code
 * (`data-media-site-id` and `data-media-key`), s9e media embeds (`data-s9e-mediaembed`), plain
 * iframes and video tags, or just a link. Each site gets the address its app opens, the player
 * frame to play it in place, and a thumbnail when the site offers one without an API call.
 */
object MediaSites {

    /**
     * The embed for [site] (the forum's site id, if any), [key] (the forum's media key) and every
     * address found in the markup, or null when none of them is media. [frames] are the player
     * addresses of the markup's iframes: an unknown player there is still played in place.
     */
    fun classify(site: String?, key: String?, candidates: List<String>, frames: List<String> = emptyList()): Block? {
        val s = site?.lowercase()?.trim()?.ifEmpty { null }
        val urls = candidates.map { it.trim() }.filter { it.isNotEmpty() }.map(::https).distinct()
        fromKey(s, key)?.let { return it }
        urls.firstNotNullOfOrNull { fromUrl(it, frame = it) }?.let { return it }
        val frame = frames.map { https(it.trim()) }.firstOrNull { isFrame(it) } ?: return null
        val page = urls.firstOrNull { it != frame && it.startsWith("http") } ?: frame
        return Block.Embed(site = s ?: hostLabel(page), url = page, kind = EmbedKind.Video, frameUrl = frame)
    }

    /** The media a plain link points to (a YouTube watch page, an mp4…), or null. */
    fun fromLink(url: String): Block? = fromUrl(https(url.trim()), frame = "")

    /** True for an address that is a media file the system player can play. */
    fun isVideoFile(url: String): Boolean = Regex("\\.(mp4|webm|m4v|mov|3gp)(\\?.*)?$", RegexOption.IGNORE_CASE).containsMatchIn(path(url))

    /** The YouTube video id in [url], if it is a YouTube address. */
    fun youtubeId(url: String): String? = youtube.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }

    fun youtube(id: String, start: String? = null): Block.Embed = Block.Embed(
        site = "youtube",
        url = "https://www.youtube.com/watch?v=$id" + (start?.let { "&t=$it" } ?: ""),
        kind = EmbedKind.YouTube,
        mediaId = id,
        frameUrl = "https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1&rel=0" + (start?.let { "&start=$it" } ?: ""),
        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    )

    private val youtube = listOf(
        Regex("youtube(?:-nocookie)?\\.com/(?:embed|shorts|live|v)/([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/watch\\?(?:.*&)?v=([A-Za-z0-9_-]{11})"),
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
        Regex("ytimg\\.com/vi(?:_webp)?/([A-Za-z0-9_-]{11})/"),
    )

    private fun fromKey(site: String?, key: String?): Block? {
        val k = key?.trim()?.ifEmpty { null } ?: return null
        return when (site) {
            "youtube" -> {
                val id = k.substringBefore(':').takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return null
                youtube(id, Regex(":t=(\\d+)").find(k)?.groupValues?.get(1))
            }
            "vimeo" -> k.takeIf { it.all(Char::isDigit) }?.let(::vimeo)
            "dailymotion" -> dailymotion(k)
            "streamable" -> streamable(k)
            "twitter", "x" -> k.takeIf { it.all(Char::isDigit) }?.let { social("x", "https://x.com/i/status/$it", null) }
            "instagram" -> social("instagram", "https://www.instagram.com/p/$k/", "https://www.instagram.com/p/$k/embed")
            "tiktok" -> k.takeIf { it.all(Char::isDigit) }?.let { social("tiktok", "https://www.tiktok.com/@/video/$it", "https://www.tiktok.com/embed/v2/$it") }
            "spotify" -> {
                // XenForo keys look like "track/ID" or "track:ID".
                val m = Regex("(track|album|playlist|episode|show|artist)[:/]([A-Za-z0-9]+)").find(k) ?: return null
                spotify(m.groupValues[1], m.groupValues[2])
            }
            "soundcloud" -> soundcloud("https://soundcloud.com/" + k.trimStart('/'))
            "imgur" -> Block.Image(url = "https://i.imgur.com/$k.jpg", alt = null, link = "https://imgur.com/$k")
            "giphy" -> Block.Image(url = "https://media.giphy.com/media/$k/giphy.gif", alt = null, link = "https://giphy.com/gifs/$k")
            "reddit" -> social("reddit", "https://www.reddit.com/" + k.trimStart('/'), null)
            "facebook" -> null // Keys are not addresses; the frame or link in the markup decides.
            else -> null
        }
    }

    private fun fromUrl(url: String, frame: String): Block? {
        youtubeId(url)?.let { id -> return youtube(id, Regex("[?&#](?:t|start)=(\\d+)").find(url)?.groupValues?.get(1)) }
        if (isVideoFile(url)) return Block.Embed(site = "video", url = url, kind = EmbedKind.VideoFile)
        Regex("i\\.imgur\\.com/([A-Za-z0-9]+)\\.gifv").find(url)?.let {
            return Block.Embed(site = "imgur", url = url, kind = EmbedKind.VideoFile, frameUrl = null).copy(url = "https://i.imgur.com/${it.groupValues[1]}.mp4")
        }
        Regex("(?:player\\.)?vimeo\\.com/(?:video/)?(\\d+)").find(url)?.let { return vimeo(it.groupValues[1]) }
        Regex("dailymotion\\.com/(?:embed/)?video/([A-Za-z0-9]+)").find(url)?.let { return dailymotion(it.groupValues[1]) }
        Regex("dai\\.ly/([A-Za-z0-9]+)").find(url)?.let { return dailymotion(it.groupValues[1]) }
        Regex("streamable\\.com/(?:e/|o/)?([A-Za-z0-9]+)").find(url)?.let { return streamable(it.groupValues[1]) }
        Regex("open\\.spotify\\.com/(?:embed/)?(?:intl-[a-z]+/)?(track|album|playlist|episode|show|artist)/([A-Za-z0-9]+)").find(url)?.let {
            return spotify(it.groupValues[1], it.groupValues[2])
        }
        if (url.contains("w.soundcloud.com/player")) return Block.Embed(site = "soundcloud", url = queryUrl(url) ?: url, kind = EmbedKind.Audio, frameUrl = url)
        if (Regex("//(?:www\\.|m\\.)?soundcloud\\.com/").containsMatchIn(url)) return soundcloud(url)
        Regex("(?:twitter|x)\\.com/[^/]+/status(?:es)?/(\\d+)").find(url)?.let { return social("x", "https://x.com/i/status/${it.groupValues[1]}", frame.takeIf(::isFrame)) }
        Regex("s9e\\.github\\.io/iframe/\\d+/twitter[^#]*#(\\d+)").find(url)?.let { return social("x", "https://x.com/i/status/${it.groupValues[1]}", url) }
        Regex("instagram\\.com/(?:[^/]+/)?(p|reel|tv)/([A-Za-z0-9_-]+)").find(url)?.let {
            val page = "https://www.instagram.com/${it.groupValues[1]}/${it.groupValues[2]}/"
            return social("instagram", page, "${page}embed")
        }
        Regex("tiktok\\.com/(?:@[^/]+/video/|embed/(?:v2/)?)(\\d+)").find(url)?.let {
            return social("tiktok", if (url.contains("/embed")) "https://www.tiktok.com/@/video/${it.groupValues[1]}" else url, "https://www.tiktok.com/embed/v2/${it.groupValues[1]}")
        }
        if (Regex("//(?:www\\.|m\\.|web\\.)?facebook\\.com/|//fb\\.watch/").containsMatchIn(url)) {
            val page = queryParam(url, "href") ?: url
            return social("facebook", page, frame.takeIf { isFrame(it) && it.contains("facebook.com/plugins") })
        }
        if (Regex("//(?:www\\.|old\\.|embed\\.)?(?:reddit|redditmedia)\\.com/").containsMatchIn(url)) {
            return social("reddit", url.replace("embed.reddit.com", "www.reddit.com").replace("redditmedia.com", "reddit.com"), frame.takeIf(::isFrame))
        }
        Regex("giphy\\.com/(?:gifs|embed)/(?:[^/]*-)?([A-Za-z0-9]+)").find(url)?.let {
            return Block.Image(url = "https://media.giphy.com/media/${it.groupValues[1]}/giphy.gif", alt = null, link = url)
        }
        if (Regex("//(?:www\\.)?twitch\\.tv/").containsMatchIn(url)) return social("twitch", url, null)
        return null
    }

    private fun vimeo(id: String) = Block.Embed(site = "vimeo", url = "https://vimeo.com/$id", kind = EmbedKind.Video, mediaId = id, frameUrl = "https://player.vimeo.com/video/$id?autoplay=1")

    private fun dailymotion(id: String) = Block.Embed(
        site = "dailymotion",
        url = "https://www.dailymotion.com/video/$id",
        kind = EmbedKind.Video,
        mediaId = id,
        frameUrl = "https://www.dailymotion.com/embed/video/$id?autoplay=1",
        thumbnail = "https://www.dailymotion.com/thumbnail/video/$id",
    )

    private fun streamable(id: String) = Block.Embed(site = "streamable", url = "https://streamable.com/$id", kind = EmbedKind.Video, mediaId = id, frameUrl = "https://streamable.com/e/$id?autoplay=1")

    private fun spotify(type: String, id: String) = Block.Embed(
        site = "spotify",
        url = "https://open.spotify.com/$type/$id",
        kind = EmbedKind.Audio,
        mediaId = id,
        frameUrl = "https://open.spotify.com/embed/$type/$id",
    )

    private fun soundcloud(page: String) = Block.Embed(
        site = "soundcloud",
        url = page,
        kind = EmbedKind.Audio,
        frameUrl = "https://w.soundcloud.com/player/?url=" + URLEncoder.encode(page, "UTF-8") + "&visual=false&show_comments=false",
    )

    private fun social(site: String, page: String, frame: String?) = Block.Embed(site = site, url = page, kind = EmbedKind.Social, frameUrl = frame)

    private fun isFrame(url: String): Boolean = url.startsWith("http") && !isVideoFile(url)

    private fun https(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://", ignoreCase = true) && !url.contains("localhost") -> "https://" + url.substring(7)
        else -> url
    }

    private fun path(url: String): String = runCatching { URI(url).path }.getOrNull() ?: url

    private fun hostLabel(url: String): String? = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()

    private fun queryParam(url: String, name: String): String? =
        Regex("[?&]$name=([^&#]+)").find(url)?.groupValues?.get(1)?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    private fun queryUrl(url: String): String? = queryParam(url, "url")
}
