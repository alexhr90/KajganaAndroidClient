# AGENTS.md — Muabet

Instructions for AI coding agents (Claude Code and others) and contributors working on **Muabet** (`com.alexhr90.muabet`), a personal, unofficial Android client for forum.kajgana.com. **Keep this file accurate: update it whenever structure, commands, conventions or decisions change.**

Read, in order: `SPEC.md` (what and why), `PLAN.md` (milestones and tasks), `docs/DECISIONS.md` (decision log), `docs/DEVICE_TEST.md` (what the owner checks on the phone).

## Current status

**v1.1.0 (fully native) is implemented and waiting for the owner's phone test** (`docs/DEVICE_TEST.md`). v1.0.0 (M1 to M4) was released; the owner's feedback on it asked for embeds to play in the app and for writing without the web wrapper (D15, D16). The owner wants each release finished and polished, pushed once and tested as a complete app.

## Hard rules

1. **Never build or compile the Android app in a cloud or sandbox session.** No `./gradlew assemble…`, no `:app` tasks, no emulators, no SDK installs. GitHub Actions builds (see "GitHub Actions minutes"). Pure-JVM `:core` tests are fine in the cloud (see Testing).
2. **Always create or update `README.md` and `AGENTS.md`** to reflect your changes, in the same change set as the code.
3. **Branches:** `main` is the default branch. Work on `claude/**` or `feature/**`, open a pull request into `main`, merge, delete the branch. **Build and release only after the final commit and push:** bump `muabet.versionName` in `gradle.properties` as the last commit of the work; merging it releases `v<versionName>`.
4. No backend, accounts of its own, analytics, ads or crash reporters. All data stays on the phone.
5. Do not add permissions, hosts or dependencies outside `SPEC.md` §2 without asking the owner. Permissions: `INTERNET` and `POST_NOTIFICATIONS` (asked for only when the user turns notifications on). Nothing else.
6. **Be a polite client** (`SPEC.md` §3, D8): every forum request goes through `PolitenessInterceptor` (2 at once, 1 per second, back-off, User-Agent `Muabet/<version> (+https://github.com/alexhr90/KajganaAndroidClient)`). **Never work around a bot check or block**: show the page in the WebView and tell the owner.
7. **The repo is public.** Fixtures are synthetic pages modelled on XenForo markup with invented names and text. Never commit real posts, usernames, cookies, page copies, screenshots or the Kajgana logo.
8. **Session:** the user logs in on the forum's own page in the WebView. The app never sees or stores the password and never logs, stores or exports cookie values (names only, as in the session check). `app_webview` and `muabet.db` stay excluded in `backup_rules.xml` and `data_extraction_rules.xml`.
9. Parsing never leaves a blank screen: a failed parse is `Loaded.Failed` (offers "Open on the forum"); an unknown block is `Block.Unknown` carrying its HTML, drawn in place by `HtmlBlock`. Nothing in a post may send the reader to the forum's web page.
10. Every user-visible string goes in `tools/strings.py` (en, mk, de, ru in one tuple), then `python3 tools/strings.py` regenerates `res/values*/strings.xml`; never edit those files by hand. Same format arguments in every language; Russian plurals need `one`, `few`, `many` and `other`. Design voice: sentence case, no emoji, no exclamation marks.
11. Never commit secrets, keystores (except the shared non-secret `config/debug.keystore`), `local.properties` or `release.jks`.
12. Icons and graphics must be original or openly licensed, recorded in `docs/ASSETS.md`.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt · DataStore · Navigation Compose · Coroutines/Flow · OkHttp · Jsoup · kotlinx.serialization · Coil · WebView · `androidx.browser` (Custom Tabs) · Gradle Kotlin DSL with a version catalog (Gradle 8.14.3, JDK 17). Room 2.8.2 (KSP, schema in `app/schemas`) and WorkManager 2.10.2 with `hilt-work`; Paging 3 is in the catalog only (D3). minSdk 26, compile and target SDK 36. Versions: `gradle/libs.versions.toml`, copied from Izvor (AGP 8.13.0, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Compose BOM 2025.10.01, Hilt 2.57.2, Navigation 2.9.5, OkHttp 4.12.0, Jsoup 1.18.1, Coil 2.7.0). Bump versions together and keep KSP matched to Kotlin.

## Design language

Izvor's design system with Kajgana orange as the only change (`SPEC.md` §5). `designsystem/` is a renamed copy of Izvor's: `ScreenScaffold`, `PageTitle`/`pageTitle` (accent full stop), `NavigationRow`, `ValueRow`, `SwitchRow`, `SectionCard`, `SectionHeader`, `StatusPill`, `InfoRow`, `PrimaryPill`, `OutlinedPill`, `ChoiceDialog`, `EmptyState`, `LoadingState`, `NoteCard`, the glass helpers (`glassColor`, `glassColorStrong`, `glassBorder`, `floatingGround`), `LogoLoader` (never a Material spinner), `MuabetPullToRefresh`, `MuabetSnackbarHost`, `MuabetBackdrop` and `LaunchIntro`. Accent: `#FF6D10` on charcoal `#101112` in dark mode, `#A34300` on paper `#F5F6F0` in light mode (the bright orange fails contrast on paper). Warning amber and negative coral sit close to orange: always pair them with an icon or label. Open rows with 1 dp `Rule`s, pills of `PillHeight`, no shadows or gradients on controls, Material Rounded and Outlined icons, 48 dp targets, `LocalReducedMotion` respected everywhere.

Muabet's own pieces live in `designsystem/ForumComponents.kt`: `ForumRow` and `ThreadRow` (long-press menus, compact density from `LocalCompactRows`), `PostItem` (a glass card per post: avatar and name open the member card, a "New" pill, ⋮ for the post menu, React and Quote under the body), `MutedPostItem`, `Avatar`, `MemberSheet`, `ActionSheet` with `SheetAction`, `PostImage` (`LocalImagesOnTap`, loading and error states), `QuoteBlock` (collapsed), `ReactionSummaryRow`, `PageJumpSheet`, `UnreadDot`; media, tables, boxes, files and link cards in `designsystem/MediaBlocks.kt`. Top and bottom bars use `barGround`, which is opaque enough that text never shows through. The mark (`designsystem/Brand.kt`, `res/drawable/ic_launcher_foreground.xml`) is an original speech bubble (D10); never use the Kajgana logo.

## Architecture

```
:core  (pure Kotlin/JVM, no Android types)
  domain.model   SessionInfo (with alert and message badges), PageInfo, ForumHome, Category, Forum,
                 SubForum, LatestThread, ForumPage, ThreadSummary, ThreadList, ThreadPage, Post
                 (with PostAbilities), PostAuthor, ReactionSummary, Reaction, Alert, AlertList,
                 SearchResult, SearchPage, ConversationSummary, ConversationList, ConversationPage,
                 MemberProfile, FormResult, Block (Paragraph, Quote, Image, Link, Embed with
                 EmbedKind, Spoiler, Code, ListBlock, Table, Box, File, Unknown with html), Run
  domain.parse   HomeParser (+ NodeParser), ForumParser, ThreadRowParser, ThreadParser (abilities,
                 attachments as images, files or videos), BbContent, MediaSites (embed recognition),
                 ListParser (New, Watched, Alerts, search, bookmarks), ConversationParser,
                 MemberParser, XfJson (form answers, reaction picker), BbCode (edit form and editor
                 HTML to BB code), FormFields, SessionParser, parseNextUrl, ParseException
  domain.filter  MuteRules (muted threads and keywords, hidden forums), PostText (plain text of a post)
  domain.url     ForumUrls (builders incl. add-reply, post-thread, quote, edit, delete, report,
                 react, bookmark, watch, direct messages, attachment upload, search, member,
                 route(), preferHttps), Route (incl. Member, Conversations, Conversation,
                 Bookmarks), ThreadTarget
  domain.net     Politeness, RequestSpacing, Backoff, BlockDetector, BlockKind
:app
  designsystem   Izvor's components recoloured, Brand, ForumComponents, Reading (LocalCompactRows,
                 LocalImagesOnTap)
  data.remote    ForumHttp.kt: WebViewCookieJar, PolitenessInterceptor, ForumFetcher (get, getJson,
                 send with multipart Upload), FetchResult
  data.post      PostingRepository (Sent, EditSource, AttachTo, Attached), DraftStore (DataStore
                 "drafts", cleared on send and logout)
  data.local     Room: MuabetDatabase, entities, LibraryDao, ReadingDao, SavedDao, WatchedCacheDao
  data.repo      ForumRepository (Loaded<T>, session and sessionExpired flows, saved-page fallback),
                 LibraryRepository, ReadingRepository, SavedRepository, SessionRepository
  data.settings  SettingsRepository (DataStore: appearance, reading, notifications, quiet hours,
                 saved cap, alert counters)
  data.backup    BackupManager (JSON export and restore through the system file picker)
  notify         Notifications (one channel, counts only)
  work           SyncWorker (badge check, widget list), WorkScheduler
  widget         WatchedWidget (RemoteViews), WatchedWidgetService
  di             AppModule (OkHttp, Room and DAOs)
  ui             navigation (MuabetNavHost, Routes, AppViewModel, expired-session bar), common
                 (incl. rememberExternalOpener, downloadFile), forums, forum, thread (pager, reaction
                 picker, delete and report dialogs), compose (ComposeScreen, ComposeViewModel,
                 ComposeTarget), messages (conversations, conversation, bookmarks), member, lists
                 (New, Watched, Alerts), search, library (hidden forums, mutes, saved threads), web
                 (login and account pages), settings
```

Pure logic belongs in `:core` so it can be unit tested on the JVM. `ui` depends on `data.repo`; screens get `UiState<T>` and render it with `LoadedContent`.

## Behaviour notes (keep in sync with the code)

- **Links** (`LocalOpenLink`): `ForumUrls.route()` sends forum, thread and post links to native screens (unread, latest and post links go through `threadAt`, which follows the forum's redirect); the alerts, watched and new-posts addresses switch to their tabs; other forum pages go to the WebView, other sites to a Custom Tab. Links from outside (VIEW, SEND, widget, notification) arrive through `MainActivity` and the same function.
- **Login:** `Routes.web(ForumUrls.login, login = true)`; the WebView screen closes once the page left `/login` and `<html data-logged-in="true">`; every list reloads when the session changes. "Stay logged in" must be ticked for `xf_user`.
- **Session state:** every parsed page updates `ForumRepository.session`; a guest page while `xf_user` exists sets `sessionExpired`, shown as a bar above the tabs.
- **Logout:** clears all WebView cookies and storage, the widget's list and the alerts notification; the session is guest.
- **Thread:** `HorizontalPager` keyed by thread id, one page per screen, only pages near the one on screen kept in memory. Route `thread?id&slug&page&title&at`: `page=0` resumes the remembered position, `at` opens any thread link. Opening an unread thread from a list goes through `/unread`. The first post on screen is saved per thread (debounced). Offline, a saved page stands in with a note.
- **Writing (D16):** `Routes.compose(ComposeTarget)` with kind Reply (with `post` for a quote), Thread, Edit, Message or NewMessage. On success the screen pops and hands the forum's redirect to the thread below through the saved-state key `openAt`, to the conversation below through `refresh`, or to `openLink`. Quick actions (react, bookmark, watch, delete, report) live in `ThreadViewModel`, update the post at once and roll back on failure; refusals show the forum's own words. A post whose action bar could not be read gets react, quote and bookmark when logged in.
- **Media (D15):** `designsystem/MediaBlocks.kt`: `EmbedBlock` (players in a WebView with the forum as base URL, full screen through `onShowCustomView`, new windows sent to the site's app), `VideoFileEmbed` (VideoView with cookies), `TableBlock`, `BoxBlock`, `FileBlock`, `LinkCard`, `HtmlBlock` (auto-height transparent WebView for unknown markup). `MainActivity` handles orientation and screen size changes itself so a playing video survives rotation.
- **External links:** `rememberExternalOpener` tries the site's own app first (Android 11+), then a Custom Tab.
- **Lists:** `EndlessViewModel` loads the first page once the session is known, more near the end (`nearEnd`), and reloads on login or logout. Mute rules apply to every thread list; hidden forums also leave New.
- **Alerts:** opening the tab zeroes the badge and the stored counters and clears the notification.
- **Notifications and widget:** `WorkScheduler` keeps one periodic `SyncWorker` while notifications are on or a widget is placed (D13).
- **Images:** Coil with its own OkHttp client that shares the forum cookies (forum host only), the User-Agent, a 150 MB disk cache, cache headers ignored (D11).

## Testing

`:core` tests and fixtures live in `core/src/test`. Add a synthetic fixture plus a test for every markup change or parser bug.

After changing strings, run `python3 tools/strings.py` (cloud-safe).

Running `:core` tests without the Android Gradle Plugin (cloud-safe): create a throwaway Gradle build outside the repo whose `settings.gradle.kts` includes only `:core` (`project(":core").projectDir = file("<repo>/core")`), loads the catalog with `versionCatalogs { create("libs") { from(files("<repo>/gradle/libs.versions.toml")) } }`, and whose root `build.gradle.kts` applies only the Kotlin JVM and serialization plugins with `apply false`. Run `gradle :core:test --max-workers=1` (Maven Central behind the proxy may answer 429; wait and retry). Do not run `:app` tasks in the cloud. The cloud cannot reach forum.kajgana.com either (D5).

## CI and releases

- `ci.yml`: unit tests and `lintDebug` in one job named "CI Gate" on pushes to `main` and on pull requests; skips docs-only changes.
- `build-apk.yml`: debug APK as pre-release `preview/<branch>` when the last commit message of a push contains `[apk]`, or on Run workflow; installs next to the release build as `com.alexhr90.muabet.debug`; signed with `config/debug.keystore`.
- `release.yml`: when `main` gets a new `muabet.versionName` (or a `v*` tag, or Run workflow) it releases `v<versionName>` once, after tests and `assembleRelease`, signed with `release.jks` from the `SIGNING_*` secrets or the debug key, and deletes the preview pre-releases.

## Conventions

- Kotlin official style; no wildcard imports; immutable data classes and sealed types.
- State in ViewModels as `StateFlow`; screens collect with `collectAsStateWithLifecycle`.
- Commits: conventional style (`feat:`, `fix:`, `docs:`, `chore:`, `test:`), small and focused.
- Post text uses `TextDirection.Content` and the system font (Latin and Cyrillic).

## When you finish a piece of work

- Update `README.md`, this file and the checklist in `PLAN.md`.
- Add what the owner should verify to `docs/DEVICE_TEST.md`; record decisions in `docs/DECISIONS.md`.
- Push once with `[apk]`, read the run, fix with one more `[apk]` push only if it failed. Bump the version as the last commit.

## GitHub Actions minutes

The account is on GitHub's free plan (2,000 Actions minutes a month for private repos, 500 MB of
artifact storage), so the workflows build as little as possible. Keep these rules when adding or
changing workflows:

- Never build in the cloud session; GitHub Actions builds. Branch pushes do not build: a push builds
  only when its last commit message contains `[apk]`, or with **Run workflow**.
- Agents: finish the work, then push once with `[apk]` in the last commit message to have it compiled.
  Read the run's log; if it failed, fix it and push again with `[apk]`. Do not add `[apk]` to
  intermediate pushes.
- CI on `main` and pull requests runs tests and lint in one "CI Gate" job, skips docs-only changes
  and cancels superseded runs. Every job has `timeout-minutes`; artifacts use `retention-days: 3`.
- Releases: bump `muabet.versionName` in `gradle.properties` (MAJOR.MINOR.PATCH) as the last commit of
  the work; merging it to `main` releases `v<version>`. Other pushes never release.
- Commits that only touch CI or docs carry `[skip ci]`. Merged branches are deleted.
