# PLAN.md — Muabet

Milestones from `SPEC.md` §4. After v0.1.0 the owner asked for M1 to M4 in one go (v1.0.0), tested as one
complete app. Tick items as they land and keep this file in step with the code.

## M0, v0.1.0: foundation and spikes (done)

- [x] Repo layout copied from Izvor: `:core` (pure JVM) and `:app`, version catalog, Gradle 8.14.3 wrapper, JDK 17.
- [x] `muabet.versionName` in `gradle.properties`, `versionCode` derived (MAJOR*10000 + MINOR*100 + PATCH).
- [x] Shared debug key `config/debug.keystore`; release signing from secrets, debug key otherwise.
- [x] Three workflows from the `project-repo-setup` skill: `ci.yml` (CI Gate), `build-apk.yml` (opt-in `[apk]`), `release.yml` (version bump).
- [x] Design system copied from Izvor, renamed, recoloured to Kajgana orange; original speech-bubble mark, launcher icon, logo loader and launch intro.
- [x] Docs: `SPEC.md`, `PLAN.md`, `AGENTS.md`, `README.md`, `docs/DECISIONS.md`, `docs/DEVICE_TEST.md`, `docs/ASSETS.md`.
- [x] Spike 2: `HomeParser`, `ForumParser`, `ThreadParser`, `BbContent`, `SessionParser`, URL routes and politeness rules in `:core`, with synthetic fixtures and JVM tests.
- [x] Spike 1 code: login on the forum's page in a WebView, OkHttp sharing its cookies (`WebViewCookieJar`), and Settings → Session check, which makes one authenticated request and shows the result (cookie names only).
- [x] Native screens to prove the parsers on live pages: Forums (collapsible categories), a forum's thread list (pinned row, infinite scroll, pull to refresh), a thread page by page with the page jump sheet.
- [x] New, Watched and Alerts tabs open the forum's own pages until M2; guests are asked to log in for Watched and Alerts.
- [x] CI green on the pull request, preview APK built.
- [x] Owner's phone test: the app installs and lists the live forums; the session check passes after logging in.
- [x] Spike results written into `docs/DECISIONS.md` (D6, D7): spike 1 passed.

## M1, v0.2.0: reading (in v1.0.0)

- [x] Room database (`data.local`): favourites, hidden forums, read positions, mutes, saved pages, the widget's watched list.
- [x] Forums tab: pinned favourites on top, reorderable (long-press: move up or down); hide forums with undo; categories remember their folded state.
- [x] Thread: posts on their own cards, the member card on a name (profile, message, posts), collapsed quotes, reaction icons with the total and the reaction names, tap to enlarge and zoom images, load images on tap setting, members-only attachments load with the forum's cookies.
- [x] Navigation: swipe between pages (pager), remembered position per thread (page and first post on screen), Back keeps the scroll position.
- [x] A 1,000+ page thread opens at any page (page jump sheet, last-page label on a row); neighbouring pages load when swiped to.

## M2, v0.3.0: account (in v1.0.0)

- [x] Session-expired handling: a page that comes back as guest while `xf_user` exists shows a bar with Log in above the tabs.
- [x] `ListParser` for `/whats-new/posts/`, `/watched/threads`, `/account/alerts` and search results; native New, Watched and Alerts tabs with endless scroll; unread threads open at the first unread post; alerts badge on the tab.
- [x] Reply, quote, new thread, react, watch, bookmark and direct messages through the WebView, returning to the native thread at the new post.

## M3, v0.4.0: comfort (in v1.0.0)

- [x] Search across the forum (all or titles only), within a thread, and a member's posts.
- [x] `domain.filter`: mute threads and keywords (muted posts fold to one line); hidden forums stay out of New.
- [x] Text size, row density, offline saved threads (chosen pages, size capped, shown when offline with a note).

## M4, v1.0.0: background and polish

- [x] Alert notifications through WorkManager (adds `POST_NOTIFICATIONS`), off by default, 15 minutes to 3 hours apart, quiet hours; Settings says that polled alerts arrive late.
- [x] Home-screen widget for watched threads; forum links open in the app through "Open with" and the share sheet.
- [x] Backup and restore of favourites, hidden forums, mutes and settings (JSON file); accessibility pass; all four languages complete.
- [x] CI green with the preview APK; owner's phone test of v1.0.0 (`docs/DEVICE_TEST.md`).

## v1.1.0: fully native (owner's feedback on v1.0.0)

"YouTube videos and other embedded elements show as 'Part of this post opens on the forum', and replying opens the web wrapper. The app should feel native and seamless."

- [x] Embeds recognised from every markup the forum uses (XenForo media BB code, s9e, iframes, video tags, unfurls) in `MediaSites`; videos play in place, music in a small player, social posts open in their app; tables, boxes and files native; anything else rendered in place from its HTML (D15).
- [x] Native compose screen: reply, quote, new thread, edit, direct message and reply, BB code tools, photo upload, drafts (D16).
- [x] Native reactions, bookmarks, watch, delete and report; native direct messages, member profiles and bookmarks.
- [ ] Owner's phone test of v1.1.0 (`docs/DEVICE_TEST.md`).
