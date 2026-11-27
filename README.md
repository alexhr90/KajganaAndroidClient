# Muabet

A personal, **unofficial** Android client for [forum.kajgana.com](https://forum.kajgana.com/), made to read and navigate the forum more comfortably than its mobile website. Muabet (муабет) means chat. It is not made, endorsed or supported by the forum or its owners, has no backend, analytics or ads, and never sees your password: you log in on the forum's own page inside the app.

## Status

**v1.1.0**, the native release: everything in a post shows in the app and all writing happens in the app. See `PLAN.md` for the milestones and `docs/DEVICE_TEST.md` for what to check.

- **Forums:** the live categories and forums in compact rows; pin favourites to the top and reorder them, hide forums (with Undo), fold categories (remembered).
- **Forum:** subforums, pinned threads folded into one row, infinite scroll, pull to refresh, a "… p." shortcut to the last page, New thread. Long-press a thread for first or last page, share, copy link, mute.
- **Thread:** swipe between pages; the page bar and a jump sheet open any page of a 1,000+ page thread; the position (page and post) is remembered per thread. Each post is a card: the name opens the member card (profile, message, posts); under the post, React and Quote; the menu also bookmarks, edits, deletes, reports, copies and shares. Collapsed quotes, spoilers, members' images (tap to zoom, or load on tap), link previews with picture and description, tables, titled boxes, attached files (downloaded with the session), code, lists and reactions.
- **Media in place:** YouTube, Vimeo, Dailymotion and Streamable play inside the post (full screen from the player), Spotify and SoundCloud in a small player, video attachments in the system player; posts from X, Instagram, TikTok, Facebook and Reddit open in their own app (or show in place). Anything else the forum draws is rendered in place in the app's colours.
- **New, Watched, Alerts:** native lists with endless scroll; unread threads open at the first unread post; an unread badge on Alerts.
- **Writing, native:** reply, quote, new thread, edit, and direct messages on one compose screen with BB code tools (bold, italic, link, image, quote, spoiler, code, list) and photos from the photo picker (no permission). Drafts are kept until sent. Reactions, bookmarks, watching, delete and report are one tap, with the forum's own words when it says no.
- **Direct messages and profiles:** the conversations list, conversations, New message, member profiles and your bookmarks are native screens.
- **Search:** the whole forum (all or titles only), one thread, or a member's posts.
- **Mutes:** mute threads and words; muted threads leave every list, posts with a muted word fold to one line.
- **Offline:** save chosen pages of a thread; they open without a connection.
- **Notifications** (off by default): new alerts and direct messages, checked every 15 minutes to 3 hours, with quiet hours.
- **Widget** with your watched threads; forum links open in the app through "Open with" and the share sheet.
- **Settings:** account (messages, bookmarks, account page, log in and out), text size, row density, theme, motion, backdrop, lists, saved pages storage, backup and restore of your lists and settings, session check.
- English, Macedonian, German and Russian.

## Design

Izvor's design language with Kajgana orange (`#FF6D10` on charcoal, `#A34300` on paper) as the accent: open rows with thin rules, pill buttons, glass containers over an optional painted backdrop, a logo loader instead of spinners, and a short launch intro. The mark is an original monoline speech bubble.

## Install

Download `Muabet-v<version>.apk` from [Releases](https://github.com/alexhr90/KajganaAndroidClient/releases). Preview builds (`Muabet-<commit>.apk` in a `preview/…` pre-release) install next to it as a separate app.

## Build

The app builds on GitHub Actions (JDK 17, Gradle 8.14.3). Locally: `./gradlew assembleDebug` with the Android SDK installed. `:core` is plain Kotlin and its tests run anywhere (`AGENTS.md` → Testing).

Project layout: `:core` (forum models, URL routes, HTML parsers, mute rules, politeness rules, JVM tests with synthetic fixtures) and `:app` (Compose UI, WebView login, native posting, OkHttp cookie bridge, Room, WorkManager, widget, design system). Details in `AGENTS.md`.

Strings live in `tools/strings.py` for all four languages; run `python3 tools/strings.py` to regenerate the `strings.xml` files.

## Releases

Bumping `muabet.versionName` in `gradle.properties` on `main` publishes `v<version>` with the APK. Release signing uses the `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` and `SIGNING_STORE_PASSWORD` secrets when they exist, the shared debug key otherwise.

## GitHub Actions minutes

Test builds are on demand: put `[apk]` in the last commit message of a push, or start the preview
build from the Actions tab with **Run workflow**. CI on `main` and pull requests skips docs-only
changes. A release is published only when the version in `gradle.properties` changes on `main`.

## Privacy

Muabet talks only to forum.kajgana.com (and image hosts that posts link to). Forum cookies stay in the WebView's store, are never shown, logged or backed up, and logging out clears them. Permissions: Internet, and notifications only if you turn them on. Everything the app keeps (pins, mutes, read positions, saved pages) stays on the phone and is left out of cloud backups; Settings → Backup writes a file with your lists and settings only. Pages are requested politely: at most two at a time, one per second, with an honest User-Agent, and the app never works around a bot check.
