# Kajgana Android Client: project specs

Copied from the owner's project specs document (27 November 2026). Changes to the plan go to
`docs/DECISIONS.md`; this file stays the original brief.

## 1. Overview

Build a personal, unofficial Android client for [forum.kajgana.com](https://forum.kajgana.com/) that makes reading and navigating it better than the mobile website. Repository: [alexhr90/KajganaAndroidClient](https://github.com/alexhr90/KajganaAndroidClient), empty as of this spec and public for now to use free GitHub Actions minutes.

**Working name (to confirm):** Muabet (муабет = chat, as in the forum's „Општи муабети“). Package `com.alexhr90.muabet`, version property prefix `muabet`, APK name `Muabet`.

**Goals**

1. Open straight into the forum without a browser.
2. Reach favourite forums, new posts and watched threads in one tap.
3. Make threads with 1,000+ pages comfortable to read.
4. Match the family design of Izvor, with Kajgana orange as the accent.
5. Show no news widget, trending block, statistics or online list.

**Non-goals**

- No backend, accounts of its own, analytics, ads or crash reporters.
- No admin cooperation: no API key and no OAuth client.
- No moderation tools or account settings in the app; these use the forum's own pages in a WebView. (v1.1.0 made posting native at the owner's request: `docs/DECISIONS.md` D16.)
- No bulk downloading or archiving of the forum.
- No Play Store release, iOS or tablet-specific layouts.

## 2. Repository setup and tech stack

Copy [Izvor](https://github.com/alexhr90/Izvor)'s project layout and toolchain; its `gradle/libs.versions.toml` is the starting point.

| Item | Value |
|---|---|
| Platform | Android only, minSdk 26, compile and target SDK 36 |
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM + Clean Architecture, pure-JVM `:core` and Android `:app` |
| DI, database, settings | Hilt, Room, DataStore |
| Async and background | Coroutines, Flow, WorkManager, Paging 3 |
| Network and parsing | OkHttp, Jsoup, kotlinx.serialization |
| Images and web | Coil, WebView, androidx.browser |
| Build | Gradle Kotlin DSL, version catalog, Gradle 8.14.3, JDK 17 |
| Versions | AGP 8.13.0, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Compose BOM 2025.10.01, Hilt 2.57.2, Room 2.8.2, Navigation 2.9.5, WorkManager 2.10.2, OkHttp 4.12.0, Jsoup 1.18.1, Coil 2.7.0 |
| UI languages | English, Macedonian, German, Russian |
| Permissions | `INTERNET`, `POST_NOTIFICATIONS` (from M4). Ask before adding any other |

**Files to create:** `README.md`, `AGENTS.md`, `SPEC.md` (this document), `PLAN.md`, `docs/DECISIONS.md`, `docs/DEVICE_TEST.md`, `docs/ASSETS.md`, `.gitignore`, `config/debug.keystore`, three workflows (section 6).

**Where this differs from Izvor:** derive `versionCode` from `muabet.versionName`, and follow the branch rules in section 6. Izvor's older notes say otherwise on both.

## 3. Architecture and forum integration

The app is a hybrid: native Compose screens for reading, and the forum's own pages in a WebView for login, posting, reactions and anything not yet native.

```text
:core (pure Kotlin/JVM, unit tested)
  domain.model    Category, Forum, ThreadSummary, Post, Alert, Page
  domain.parse    HomeParser, ForumParser, ThreadParser, ListParser, BbContent
  domain.url      routes, ids, pagination, link recognition
  domain.filter   hidden forums, muted threads and keywords
  domain.net      throttle and back-off rules
:app
  designsystem    copied from Izvor, renamed and recoloured
  data.remote     OkHttp client, WebView cookie bridge
  data.local      Room: favourites, read positions, mutes, saved threads, cache
  data.repo       Forum, Thread, Session, Settings repositories
  ui              forums, forum, thread, newposts, watched, alerts, search, web, settings
  work, notify    alert polling and notifications
  widget          watched threads
```

**Rules for the integration**

- **Session:** the user logs in on the forum's page in the WebView. OkHttp reads the same cookies through `CookieManager`. The app never sees or stores the password, and never logs or exports cookies.
- **Parsing:** server HTML only, parsed with Jsoup. Post bodies become a block model (text, quote, image, link, embed, spoiler). An unknown block or failed parse offers “Open on the forum”, never a blank screen.
- **Politeness:** User-Agent `Muabet/<version> (+https://github.com/alexhr90/KajganaAndroidClient)`, at most 2 concurrent requests and 1 per second, conditional requests where supported, back-off on errors. Never work around a bot check or block.
- **Links:** forum links open natively, others in a Custom Tab.
- **Fixtures:** synthetic pages modelled on real markup. The repo is public, so never commit real posts, usernames, cookies or page copies.

**What is known about the forum (checked 27 November 2026, as a guest)**

| Topic | Finding |
|---|---|
| Software | XenForo; its stylesheet suggests version 2.3 |
| Home | 6 categories, 49 top-level forums, many with subforums |
| Forum URL | `/forums/<slug>.<id>/` |
| Thread and post URL | `/threads/<slug>.<id>/`, `.../post-<id>` |
| Thread size | 15 posts per page; one thread had 1,046 pages |
| Post fields | avatar, name, role, join date, post count, reaction points, age, location, time, number, body, reactions |
| Lists | `/whats-new/posts/`, RSS at `/forums/-/index.rss` |
| Style pages | `/misc/style`; light or dark through `/misc/style-variation?variation=alternate&t=<csrf token>` |
| Cookies | `xf_session`, `xf_user`, `xf_csrf` ([cookie page](https://forum.kajgana.com/help/cookies)) |
| Verified CSS classes | `.node`, `.node-title`, `.node-extra`, `.node-meta`, `.p-body-sidebar` |

**To verify in Phase 0 (standard XenForo, not checked here):** `/page-<n>`, `/unread`, `/watched/threads`, `/account/alerts`, `/direct-messages/`, `/account/bookmarks`, post and thread list selectors, search form, any bot protection, and `robots.txt` (the fetch failed).

## 4. Features by milestone

Ship in five milestones; each ends with a release the owner tests on his phone.

### M0, v0.1.0: foundation and spikes

- Scaffold both modules, design system, workflows, debug key and docs.
- Spike 1: WebView login, then an authenticated OkHttp request with the shared cookies.
- Spike 2: parse home, a forum and a thread into models, with synthetic fixtures.
- **Done when:** CI is green, the app installs and lists the live forums, and both spike results are in `docs/DECISIONS.md`. If spike 1 fails, stop and ask the owner.

### M1, v0.2.0: reading

- **Forums tab:** pinned favourites on top, reorderable; collapsible categories; hide forums; compact two-line rows (name, latest thread on one line); unread shown by weight and accent dot. No forum icons or thread counts.
- **Thread list:** pinned threads collapsed into one row, infinite scroll, pull to refresh.
- **Thread:** one-line post header (details on tap), collapsed quotes, reactions as icons with counts, tap to enlarge images, option to load images on tap.
- **Navigation:** swipe between pages, page jump sheet, first and last page, remembered position per thread.
- **Done when:** a 1,000+ page thread opens at any page, and Back returns to the same scroll position.

### M2, v0.3.0: account

- Login, logout and session-expired handling.
- **New**, **Watched** and **Alerts** tabs; jump to first unread.
- Reply, quote, new thread, react, direct messages and bookmarks through the WebView.
- **Done when:** the owner can log in, read unread posts, reply and see the reply natively.

### M3, v0.4.0: comfort

- Search across the forum and within a thread.
- Mute threads and keywords; ignored forums stay out of New.
- Text size, row density, theme (system, light, dark), offline saved threads (chosen pages, size capped).
- **Done when:** a saved thread opens in aeroplane mode and muted items never appear.

### M4, v1.0.0: background and polish

- Alert notifications through WorkManager, off by default, at least 15 minutes apart, with quiet hours.
- Home screen widget for watched threads, deep links for forum URLs through the share sheet and “Open with”.
- Backup and restore of favourites, mutes and settings; accessibility pass; all four languages complete.
- **Done when:** every item in `docs/DEVICE_TEST.md` passes on the owner's phone.

**Navigation:** bottom bar with Forums, New, Watched and Alerts. Search and the settings gear sit in the top bar. Guests see Forums and New; the other tabs ask them to log in.

## 5. Design language: Izvor with Kajgana orange

Take [Izvor](https://github.com/alexhr90/Izvor) as the reference and change only the accent from green to Kajgana orange. Start from a copy of `app/src/main/java/com/alexhr90/izvor/designsystem`.

**Colours.** Keep Izvor's neutrals, warning and negative tones. Replace every lime and olive value, including containers and backdrop scenes. `#FF6D10` is sampled from the Kajgana logo.

| Role | Dark | Light |
|---|---|---|
| Background (unchanged) | `#101112` | `#F5F6F0` |
| Accent, `primary` | `#FF6D10` | `#A34300` |
| `onPrimary` | `#101112` | `#F5F6F0` |
| `primaryContainer` | `#3A2008` | `#FFE0C9` |
| `onPrimaryContainer` | `#FF6D10` | `#421B00` |
| `inversePrimary` | `#A34300` | `#FF6D10` |

The accent pairs reach at least 4.9:1 contrast. The bright orange is only 2.6:1 on paper, so light mode uses the darker one. Warning amber and negative coral sit close to orange, so always pair them with an icon or label.

**Carried over from Izvor**

- Open rows with 1 dp rules, pill buttons of one height, no shadows or gradients on controls, Material Rounded and Outlined icons.
- `ScreenScaffold`, `PageTitle` with the accent full stop, the shared row, pill, dialog and empty-state components.
- Glass containers, painted backdrop (optional, lime scenes recoloured), edge-to-edge bars.
- A logo loader instead of spinners, branded pull to refresh and snackbar, a launch intro under 1 second.
- 4 dp spacing scale, system sans with Cyrillic coverage, reduced-motion support, 48 dp touch targets, text up to 200 %.
- Sentence case, no emoji, no exclamation marks in interface text.

**New for this app**

- An original mark: a monoline speech bubble on Izvor's grid (100 units, 7-unit stroke). Never copy the Kajgana logo or name it as the app's own; add “unofficial” in About and the README.
- Components: `ForumRow`, `ThreadRow`, `PostItem`, `QuoteBlock`, `ReactionSummary`, `PageJumpSheet`, `UnreadDot`.

## 6. CI, releases and GitHub Actions minutes

Use the owner's standard setup; the `project-repo-setup` skill has the workflow templates (`APP` = `Muabet`, `appid` = `muabet`). Keep minutes low even while the repo is public.

- `main` is the default branch. Work on `claude/**` or `feature/**`, merge into `main`, delete the branch. Turn on automatic deletion of head branches.
- **Never build or compile the Android app in a cloud session.** GitHub Actions builds. Pure-JVM `:core` tests are fine in the cloud.
- Branch builds are opt-in: only when the last commit message of a push contains `[apk]`, or through Run workflow. Push once with `[apk]` when work is finished, read the log, and push one fix with `[apk]` only if it failed.
- `ci.yml`: pushes to `main` and pull requests; tests and lint in one job named **CI Gate**; `paths-ignore` for `**.md`, `docs/**`, `LICENSE*`, `.gitignore`; `concurrency` with cancel-in-progress.
- `build-apk.yml`: debug APK as a `preview/<branch>` pre-release, signed with `config/debug.keystore`, app id suffix `.debug`.
- `release.yml`: runs on a `muabet.versionName` change on `main`, a `v*` tag or Run workflow; a first job skips if `v<versionName>` exists. No release on ordinary pushes.
- Every job has `timeout-minutes`; artifacts are uploaded only on failure with `retention-days: 3`; APKs ship through GitHub Releases.
- `versionName` is `MAJOR.MINOR.PATCH` in `gradle.properties`; `versionCode = MAJOR*10000 + MINOR*100 + PATCH`. Bump it as the last commit of the work, so the release follows the final push.
- Commits touching only docs or CI carry `[skip ci]`.
- Release signing is optional: secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD`. Never commit that key.
- Copy the “GitHub Actions minutes” section into `AGENTS.md` and `README.md`.

## 7. Constraints, risks and open questions

| Risk | Handling |
|---|---|
| [Terms](https://forum.kajgana.com/help/terms/) do not mention third-party apps or scraping, but treat unauthorised use of any part of the portal as infringement | Personal use, polite requests, unofficial naming. The owner accepts that the admins may object or block the app |
| The public repo and its releases are visible to anyone, including the admins | No forum content or branding in the repo; consider going private after v1.0 |
| Native screens show no forum ads or tracking | Revisit before sharing the app with anyone |
| A forum or style update breaks parsing | Parsers isolated in `:core`, fixture tests, WebView fallback |
| Login cookies do not work outside the WebView | Spike 1 in M0, before other work |
| A bot check blocks plain requests | Do not bypass it; show the page in the WebView and ask the owner |
| Polled notifications arrive late | State this in Settings |
| Other people's posts are cached on the phone | Size-capped cache, no export of post content |

**Open questions, with the default to use if unanswered**

- [ ] App name: Muabet?
- [ ] Are German and Russian needed, or only English and Macedonian? Default: all four, as in Izvor.
- [ ] Which forums should be pinned on first launch? Default: none.
- [ ] Painted backdrop on or off by default? Default: as in Izvor.
- [ ] Test phone and Android version? Needed for `docs/DEVICE_TEST.md`.

## 8. Instructions for the new Claude project

Attach this document to the new project and start with this message:

```text
Build the Android app described in the attached spec in
https://github.com/alexhr90/KajganaAndroidClient (empty, public).

1. Read the spec fully. Study https://github.com/alexhr90/Izvor for
   structure, tech stack, design system, UI/UX and workflows.
2. Ask me the open questions in section 7. Use the defaults if I don't answer.
3. Commit the spec as SPEC.md, then create PLAN.md, AGENTS.md, README.md
   and docs/ before writing app code.
4. Implement M0 and stop if the login cookie spike fails.
   Continue one milestone at a time and wait for my phone test.

Rules:
- Never build or compile the Android app in the cloud. Use GitHub Actions.
- Follow the project-repo-setup skill and section 6 of the spec.
- Update README.md and AGENTS.md with every change.
- Bump the version and release only after the final commit and push.
- Never commit real forum content, usernames, cookies or secrets.
```
