# Decisions

Non-obvious choices, newest last. Each has an id so code and docs can point at it.

## D1 — Name and ids (M0)
Working name **Muabet** (муабет = chat), package `com.alexhr90.muabet`, version prefix `muabet`, APK `Muabet-<tag>.apk`. The spec's open question on the name is unanswered, so the default is used. Renaming later means a new package (a new app on the phone), so decide before v1.0.

## D2 — Open questions answered by their defaults (M0)
The owner did not answer SPEC §7 before M0 started, so: all four languages (en, mk, de, ru); no forums pinned on first launch; painted backdrop on by default with the time-of-day scene, as in Izvor (`BackdropStyle.AUTO`); test phone still unknown (needed for `docs/DEVICE_TEST.md`).

## D3 — What M0 leaves out of the stack (M0, updated in v1.0.0)
M0 stored nothing but settings (DataStore). Since v1.0.0 Room (`data.local`, `muabet.db`, schema exported to `app/schemas`) and WorkManager (with `HiltWorkerFactory`) are dependencies; Paging 3 stays in the catalog only. Thread lists page with a plain "load the next page near the end" instead of Paging 3, which fits a server that only offers numbered pages.

## D4 — Branches and the first commit (M0)
The repo was empty, so `main` was created with the spec only (`SPEC.md`), and the M0 work went to a `claude/**` branch with a pull request into `main`, as SPEC §6 asks. The version stays `0.0.1` until the last commit of M0 bumps it to `0.1.0`, so merging the pull request is what releases v0.1.0.

## D5 — Forum markup assumed for the parsers (M0)
The cloud session cannot reach forum.kajgana.com (the proxy refuses the host), so the parsers follow standard XenForo 2.3 markup plus the classes verified in SPEC §3: `.block--category`, `.node` with `node--unread`, `.node-extra-title`, `.structItem--thread` with `is-unread`, `.structItemContainer-group--sticky`, `.structItem-pageJump`, `article.message--post` with `data-content="post-<id>"`, `.message-userExtras dl`, `.bbWrapper`, `.bbCodeBlock--quote` with `data-quote` and `data-source`, `.bbCodeSpoiler`, `.bbCodeBlock--unfurl`, `.bbMediaWrapper` and `data-s9e-mediaembed`, `.reactionsBar`, `.pageNav-page`, and `data-logged-in` and `data-csrf` on `<html>`. Times come from `data-timestamp`, `data-time` or `datetime`. The fixtures are synthetic pages in that shape. The phone test is the real check; any mismatch becomes a fixture and a fix.

## D6 — Spike 1: the WebView login reaches OkHttp (passed)
Design: the user logs in on the forum's own `/login/` page in a WebView (its own User-Agent, as a person browsing). `WebViewCookieJar` hands OkHttp the cookies of `CookieManager` for the forum host only, and writes cookies the forum sets back into it. The login screen closes when the page has left `/login` and `<html data-logged-in="true">`. Settings → Session check requests `/account/` with OkHttp and reports the cookie names, the HTTP answer, whether a bot check answered, and the user the forum saw. **Result (owner's phone test of v0.1.0):** passed. Settings → Session check showed "The login works in the app": HTTP 200, no bot check or block, about 0.5 s, and "Stay logged in" kept (`xf_user` present). The forum saw the owner's account (the name is not recorded here).

## D7 — Spike 2: parsing live pages (passed)
Home, a forum's thread list and a thread page parse into `ForumHome`, `ForumPage` and `ThreadPage` (18 JVM tests on synthetic fixtures). **Result on the live forum:** the owner's test of v0.1.0 listed the forums and read threads. Two findings were fixed in v1.0.0: members' uploaded images did not show (see D11), and posts needed clearer separation (posts now sit on their own cards).

## D8 — Politeness in one interceptor (M0)
`PolitenessInterceptor` applies SPEC §3 to forum requests only: a fair semaphore of 2, a 1-second spacing from `RequestSpacing`, `Backoff` from 2 s doubling to 5 minutes after 429, 5xx and network errors (or `Retry-After` when longer), and the User-Agent `Muabet/<version> (+https://github.com/alexhr90/KajganaAndroidClient)`. `BlockDetector` turns a bot check, 429 or a bare 403 into `Loaded.Blocked`, and the screen offers the page in the WebView; nothing works around it. Conditional requests come with the M1 cache.

## D9 — Session data is never backed up (M0)
The WebView's data folder (`app_webview`, which holds the forum cookies) is excluded from cloud backup and device transfer, so a restored phone starts logged out. Cookie values never leave `CookieManager`: the session check shows names only.

## D10 — The mark (M0)
An original monoline speech bubble on Izvor's grid: 100 units, 7-unit stroke, butt caps, open at the upper right like the family's marks, tail at the lower left, two lines of talk inside. The loader draws the bubble, then types the lines. Nothing is taken from the Kajgana logo; only its orange `#FF6D10` is used as the accent.

## D11 — Images: forum cookies for attachments, cleartext for other hosts (v1.0.0)
Members' uploads are XenForo attachments, shown to members only, so Coil's own OkHttp client now uses `WebViewCookieJar` too (the jar answers for the forum host only; other image hosts never get the cookies). `BbContent` prefers the image proxy `src` over `data-url`, upgrades forum `http://` links to https, and a failed image shows a tappable line instead of a gap. Posts link images on old `http://` hosts, so `network_security_config.xml` allows cleartext in general but never for kajgana.com.

## D12 — Account lists and forms (v1.0.0)
New, Watched and Alerts parse the forum's own pages (`ListParser`), with the next page from `.pageNav-jump--next`. Opening Alerts marks them read on the forum, as on the website. Posting stayed on the forum's own forms in the WebView in v1.0.0; v1.1.0 replaced that with native posting (D16).

## D13 — Background checks read a badge, not the alerts (v1.0.0)
Notifications are off by default. When on, one periodic `SyncWorker` (15 minutes to 3 hours, network required) loads `/help/`, a small page that carries the alerts and direct messages badges, so checking never marks anything read and never stores alert text. It notifies only when a count grew, nothing is fetched in quiet hours, and the notification shows counts only. The same worker refreshes the widget's watched list, and only while a widget is placed.

## D14 — Local data (v1.0.0)
Room holds favourites, hidden forums, read positions (the newest 2,000), muted threads and keywords, saved pages (gzip forum HTML, parsed again when read, capped by Settings → Storage) and the widget's watched titles. `muabet.db` is excluded from cloud backup and device transfer like the WebView's data; the user's own backup (Settings → Backup) is a JSON file with favourites, hidden forums, mutes and settings only, never cookies, posts or saved pages. Logging out also clears the widget's list and the alerts notification.

## D15 — Everything in a post shows in the app (v1.1.0)
The owner's test of v1.0.0 found embeds ("Part of this post opens on the forum") everywhere. `BbContent` now collects every address an embed's markup carries (`src`, a lazy `data-src` or `data-s9e-mediaembed-src`, links, `data-url`, a `style` `url()`), and `MediaSites` turns the forum's site id and media key, or any of those addresses, into an `Embed` with a kind: YouTube, video, video file, audio, social or link. Videos show their thumbnail and play in place in a small WebView that loads the site's own player in a page whose address is the forum's (players check where they are embedded); full screen comes from the player. Video files use the system player with the session's cookies. Music loads a short player on tap. Social posts open in the site's app (`FLAG_ACTIVITY_REQUIRE_NON_BROWSER` on Android 11+), with "Show here" when the site has a player. Tables, titled boxes and attached files have native views; anything still unknown keeps its HTML and is drawn in place by a transparent WebView in the app's colours, as tall as its content, so no post sends the reader to the forum any more. External links in general open in their app first, then in a Custom Tab.

## D16 — Native posting with the forum's own endpoints (v1.1.0)
The owner asked for writing without the web wrapper. `PostingRepository` sends the same requests the forum's pages send with JavaScript: the form's fields, `_xfToken` from `<html data-csrf>`, `_xfResponseType=json` and `X-Requested-With`, to `add-reply`, `post-thread`, `posts/<id>/edit|delete|report|react|bookmark|quote`, `threads/<id>/watch` and the direct message endpoints. `XfJson` reads the answer: a redirect to the new post, the forum's own error words, or a security error, after which the token is read from a fresh page and the request is sent once more. Edits read the forum's edit form and send back every field it holds (title, prefix and other settings) with the new text, so nothing else changes; the editor's HTML is turned back into BB code when the form has no BB code field. Photos come from the system photo picker (no permission), are scaled to 2,048 px, and upload to `attachments/upload` with a hash that the message then carries. Drafts live in their own DataStore file on the phone, are cleared when sent and on logout, and are never backed up. Login and account settings stay on the forum's own pages in the WebView, so the app still never sees the password.
