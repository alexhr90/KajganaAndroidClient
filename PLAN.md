# PLAN.md — Muabet

Milestones from `SPEC.md` §4. Each ends with a release the owner tests on the phone; the next one
starts only after that test. Tick items as they land and keep this file in step with the code.

## M0, v0.1.0: foundation and spikes (implemented, waiting for the phone test)

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
- [ ] CI green on the pull request, preview APK built.
- [ ] Owner's phone test: the app installs and lists the live forums; the session check passes after logging in.
- [ ] Spike results written into `docs/DECISIONS.md` (D6, D7). If spike 1 fails, stop and ask the owner.

## M1, v0.2.0: reading

- [ ] Room database (`data.local`): favourites, hidden forums, read positions.
- [ ] Forums tab: pinned favourites on top, reorderable; hide forums; categories remember their folded state.
- [ ] Thread: details on tap (done in M0), collapsed quotes (done in M0), reaction icons with counts per type, tap to enlarge images (basic viewer in M0), load images on tap setting.
- [ ] Navigation: swipe between pages, remembered position per thread (page and post), Back keeps the scroll position.
- [ ] Done when a 1,000+ page thread opens at any page and Back returns to the same scroll position.

## M2, v0.3.0: account

- [ ] Session-expired handling (a page that comes back as guest while `xf_user` exists).
- [ ] `ListParser` for `/whats-new/posts/`, `/watched/threads` and `/account/alerts`; native New, Watched and Alerts tabs; jump to first unread.
- [ ] Reply, quote, new thread, react, direct messages and bookmarks through the WebView, returning to the native thread.

## M3, v0.4.0: comfort

- [ ] Search across the forum and within a thread.
- [ ] `domain.filter`: mute threads and keywords; ignored forums stay out of New.
- [ ] Text size, row density, offline saved threads (chosen pages, size capped). Theme setting exists since M0.

## M4, v1.0.0: background and polish

- [ ] Alert notifications through WorkManager (adds `POST_NOTIFICATIONS`), off by default, at least 15 minutes apart, quiet hours; Settings says that polled alerts arrive late.
- [ ] Home-screen widget for watched threads; deep links for forum URLs through the share sheet and “Open with”.
- [ ] Backup and restore of favourites, mutes and settings; accessibility pass; all four languages complete.
