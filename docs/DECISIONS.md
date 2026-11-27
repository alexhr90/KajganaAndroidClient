# Decisions

Non-obvious choices, newest last. Each has an id so code and docs can point at it.

## D1 — Name and ids (M0)
Working name **Muabet** (муабет = chat), package `com.alexhr90.muabet`, version prefix `muabet`, APK `Muabet-<tag>.apk`. The spec's open question on the name is unanswered, so the default is used. Renaming later means a new package (a new app on the phone), so decide before v1.0.

## D2 — Open questions answered by their defaults (M0)
The owner did not answer SPEC §7 before M0 started, so: all four languages (en, mk, de, ru); no forums pinned on first launch; painted backdrop on by default with the time-of-day scene, as in Izvor (`BackdropStyle.AUTO`); test phone still unknown (needed for `docs/DEVICE_TEST.md`).

## D3 — What M0 leaves out of the stack (M0)
Room, WorkManager and Paging 3 stay in the version catalog but are not dependencies yet: M0 stores nothing but settings (DataStore), and Room's compiler fails on a module without a database. Room arrives with favourites and read positions in M1, WorkManager with notifications in M4. Thread lists page with a plain "load the next page near the end" instead of Paging 3, which fits a server that only offers numbered pages.

## D4 — Branches and the first commit (M0)
The repo was empty, so `main` was created with the spec only (`SPEC.md`), and the M0 work went to a `claude/**` branch with a pull request into `main`, as SPEC §6 asks. The version stays `0.0.1` until the last commit of M0 bumps it to `0.1.0`, so merging the pull request is what releases v0.1.0.

## D5 — Forum markup assumed for the parsers (M0)
The cloud session cannot reach forum.kajgana.com (the proxy refuses the host), so the parsers follow standard XenForo 2.3 markup plus the classes verified in SPEC §3: `.block--category`, `.node` with `node--unread`, `.node-extra-title`, `.structItem--thread` with `is-unread`, `.structItemContainer-group--sticky`, `.structItem-pageJump`, `article.message--post` with `data-content="post-<id>"`, `.message-userExtras dl`, `.bbWrapper`, `.bbCodeBlock--quote` with `data-quote` and `data-source`, `.bbCodeSpoiler`, `.bbCodeBlock--unfurl`, `.bbMediaWrapper` and `data-s9e-mediaembed`, `.reactionsBar`, `.pageNav-page`, and `data-logged-in` and `data-csrf` on `<html>`. Times come from `data-timestamp`, `data-time` or `datetime`. The fixtures are synthetic pages in that shape. The phone test is the real check; any mismatch becomes a fixture and a fix.

## D6 — Spike 1: the WebView login reaches OkHttp (pending the phone test)
Design: the user logs in on the forum's own `/login/` page in a WebView (its own User-Agent, as a person browsing). `WebViewCookieJar` hands OkHttp the cookies of `CookieManager` for the forum host only, and writes cookies the forum sets back into it. The login screen closes when the page has left `/login` and `<html data-logged-in="true">`. Settings → Session check requests `/account/` with OkHttp and reports the cookie names, the HTTP answer, whether a bot check answered, and the user the forum saw. **Result:** to be filled in from the owner's phone test. If it fails, M1 does not start until the owner decides.

## D7 — Spike 2: parsing live pages (pending the phone test)
Home, a forum's thread list and a thread page parse into `ForumHome`, `ForumPage` and `ThreadPage` (18 JVM tests on synthetic fixtures). **Result on the live forum:** to be filled in from the owner's phone test (forums listed, a thread with 1,000+ pages opened at its last page).

## D8 — Politeness in one interceptor (M0)
`PolitenessInterceptor` applies SPEC §3 to forum requests only: a fair semaphore of 2, a 1-second spacing from `RequestSpacing`, `Backoff` from 2 s doubling to 5 minutes after 429, 5xx and network errors (or `Retry-After` when longer), and the User-Agent `Muabet/<version> (+https://github.com/alexhr90/KajganaAndroidClient)`. `BlockDetector` turns a bot check, 429 or a bare 403 into `Loaded.Blocked`, and the screen offers the page in the WebView; nothing works around it. Conditional requests come with the M1 cache.

## D9 — Session data is never backed up (M0)
The WebView's data folder (`app_webview`, which holds the forum cookies) is excluded from cloud backup and device transfer, so a restored phone starts logged out. Cookie values never leave `CookieManager`: the session check shows names only.

## D10 — The mark (M0)
An original monoline speech bubble on Izvor's grid: 100 units, 7-unit stroke, butt caps, open at the upper right like the family's marks, tail at the lower left, two lines of talk inside. The loader draws the bubble, then types the lines. Nothing is taken from the Kajgana logo; only its orange `#FF6D10` is used as the accent.
