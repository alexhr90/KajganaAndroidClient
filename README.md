# Muabet

A personal, **unofficial** Android client for [forum.kajgana.com](https://forum.kajgana.com/), made to read and navigate the forum more comfortably than its mobile website. Muabet (муабет) means chat. It is not made, endorsed or supported by the forum or its owners, has no backend, analytics or ads, and never sees your password: you log in on the forum's own page inside the app.

## Status

**v0.1.0 (M0, foundation and spikes)**, waiting for the first phone test. See `PLAN.md` for the milestones and `docs/DEVICE_TEST.md` for what to check.

What works in M0:

- **Forums:** the live categories and forums in compact two-line rows; unread forums bolder with an orange dot; categories fold on tap.
- **Forum:** subforums, pinned threads folded into one row, the thread list with infinite scroll and pull to refresh, a "… p." shortcut to a thread's last page.
- **Thread:** page by page, with first, previous, a page jump sheet (slider or number), next and last, so a 1,000+ page thread opens anywhere. Posts have a one-line header (details on tap), collapsed quotes, spoilers, images (tap to enlarge), link previews, embeds, code, lists and reactions. Anything not shown natively offers "Open on the forum".
- **Login:** on the forum's page in a WebView; the app's own requests share its cookies. Settings → **Session check** shows whether that works (cookie names only).
- **New, Watched, Alerts:** open the forum's own pages until the native lists arrive in v0.3.
- **Settings:** theme, motion, painted backdrop, log in and out, about.
- English, Macedonian, German and Russian.

## Design

Izvor's design language with Kajgana orange (`#FF6D10` on charcoal, `#A34300` on paper) as the accent: open rows with thin rules, pill buttons, glass containers over an optional painted backdrop, a logo loader instead of spinners, and a short launch intro. The mark is an original monoline speech bubble.

## Install

Download `Muabet-v<version>.apk` from [Releases](https://github.com/alexhr90/KajganaAndroidClient/releases). Preview builds (`Muabet-<commit>.apk` in a `preview/…` pre-release) install next to it as a separate app.

## Build

The app builds on GitHub Actions (JDK 17, Gradle 8.14.3). Locally: `./gradlew assembleDebug` with the Android SDK installed. `:core` is plain Kotlin and its tests run anywhere (`AGENTS.md` → Testing).

Project layout: `:core` (forum models, URL routes, HTML parsers, politeness rules, JVM tests with synthetic fixtures) and `:app` (Compose UI, WebView login, OkHttp cookie bridge, design system). Details in `AGENTS.md`.

## Releases

Bumping `muabet.versionName` in `gradle.properties` on `main` publishes `v<version>` with the APK. Release signing uses the `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` and `SIGNING_STORE_PASSWORD` secrets when they exist, the shared debug key otherwise.

## GitHub Actions minutes

Test builds are on demand: put `[apk]` in the last commit message of a push, or start the preview
build from the Actions tab with **Run workflow**. CI on `main` and pull requests skips docs-only
changes. A release is published only when the version in `gradle.properties` changes on `main`.

## Privacy

Muabet talks only to forum.kajgana.com (and image hosts that posts link to). Forum cookies stay in the WebView's store, are never shown, logged or backed up, and logging out clears them. Pages are requested politely: at most two at a time, one per second, with an honest User-Agent, and the app never works around a bot check.
