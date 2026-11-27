# AGENTS.md — Muabet

Instructions for AI coding agents (Claude Code and others) and contributors working on **Muabet** (`com.alexhr90.muabet`), a personal, unofficial Android client for forum.kajgana.com. **Keep this file accurate: update it whenever structure, commands, conventions or decisions change.**

Read, in order: `SPEC.md` (what and why), `PLAN.md` (milestones and tasks), `docs/DECISIONS.md` (decision log), `docs/DEVICE_TEST.md` (what the owner checks on the phone).

## Current status

**M0 (v0.1.0, foundation and spikes) is implemented and waiting for the owner's phone test.** `:core` holds the forum models, URL routes, HTML parsers and politeness rules with JVM tests; `:app` holds the Compose UI, the WebView login, the cookie bridge and the design system. Spike 1 (the WebView login reaching OkHttp) is checked on the phone through Settings → Session check; its result goes into `docs/DECISIONS.md` D6. **If spike 1 fails, stop and ask the owner before M1.** Work one milestone at a time and wait for the phone test between them.

## Hard rules

1. **Never build or compile the Android app in a cloud or sandbox session.** No `./gradlew assemble…`, no `:app` tasks, no emulators, no SDK installs. GitHub Actions builds (see "GitHub Actions minutes"). Pure-JVM `:core` tests are fine in the cloud (see Testing).
2. **Always create or update `README.md` and `AGENTS.md`** to reflect your changes, in the same change set as the code.
3. **Branches:** `main` is the default branch. Work on `claude/**` or `feature/**`, open a pull request into `main`, merge, delete the branch. **Build and release only after the final commit and push:** bump `muabet.versionName` in `gradle.properties` as the last commit of the work; merging it releases `v<versionName>`.
4. No backend, accounts of its own, analytics, ads or crash reporters. All data stays on the phone.
5. Do not add permissions, hosts or dependencies outside `SPEC.md` §2 without asking the owner. Permissions: `INTERNET` now, `POST_NOTIFICATIONS` from M4.
6. **Be a polite client** (`SPEC.md` §3, D8): every forum request goes through `PolitenessInterceptor` (2 at once, 1 per second, back-off, User-Agent `Muabet/<version> (+https://github.com/alexhr90/KajganaAndroidClient)`). **Never work around a bot check or block**: show the page in the WebView and tell the owner.
7. **The repo is public.** Fixtures are synthetic pages modelled on XenForo markup with invented names and text. Never commit real posts, usernames, cookies, page copies, screenshots or the Kajgana logo.
8. **Session:** the user logs in on the forum's own page in the WebView. The app never sees or stores the password and never logs, stores or exports cookie values (names only, as in the session check). `app_webview` stays excluded in `backup_rules.xml` and `data_extraction_rules.xml`.
9. Parsing never leaves a blank screen: a failed parse is `Loaded.Failed`, an unknown block is `Block.Unknown`, and both offer "Open on the forum".
10. Every user-visible string goes in `res/values/strings.xml` **and** `values-mk`, `values-de`, `values-ru`, with the same format arguments; Russian plurals need `one`, `few`, `many` and `other`. Design voice: sentence case, no emoji, no exclamation marks.
11. Never commit secrets, keystores (except the shared non-secret `config/debug.keystore`), `local.properties` or `release.jks`.
12. Icons and graphics must be original or openly licensed, recorded in `docs/ASSETS.md`.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt · DataStore · Navigation Compose · Coroutines/Flow · OkHttp · Jsoup · kotlinx.serialization · Coil · WebView · `androidx.browser` (Custom Tabs) · Gradle Kotlin DSL with a version catalog (Gradle 8.14.3, JDK 17). Room (M1), WorkManager (M4) and Paging 3 are in the catalog but not yet dependencies (D3). minSdk 26, compile and target SDK 36. Versions: `gradle/libs.versions.toml`, copied from Izvor (AGP 8.13.0, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Compose BOM 2025.10.01, Hilt 2.57.2, Navigation 2.9.5, OkHttp 4.12.0, Jsoup 1.18.1, Coil 2.7.0). Bump versions together and keep KSP matched to Kotlin.

## Design language

Izvor's design system with Kajgana orange as the only change (`SPEC.md` §5). `designsystem/` is a renamed copy of Izvor's: `ScreenScaffold`, `PageTitle`/`pageTitle` (accent full stop), `NavigationRow`, `ValueRow`, `SwitchRow`, `SectionCard`, `SectionHeader`, `StatusPill`, `InfoRow`, `PrimaryPill`, `OutlinedPill`, `ChoiceDialog`, `EmptyState`, `LoadingState`, `NoteCard`, the glass helpers (`glassColor`, `glassColorStrong`, `glassBorder`, `floatingGround`), `LogoLoader` (never a Material spinner), `MuabetPullToRefresh`, `MuabetSnackbarHost`, `MuabetBackdrop` and `LaunchIntro`. Accent: `#FF6D10` on charcoal `#101112` in dark mode, `#A34300` on paper `#F5F6F0` in light mode (the bright orange fails contrast on paper). Warning amber and negative coral sit close to orange: always pair them with an icon or label. Open rows with 1 dp `Rule`s, pills of `PillHeight`, no shadows or gradients on controls, Material Rounded and Outlined icons, 48 dp targets, `LocalReducedMotion` respected everywhere.

Muabet's own pieces live in `designsystem/ForumComponents.kt`: `ForumRow` (two compact lines, no icons or counts), `ThreadRow`, `PostItem` (one-line header, details on tap, body as blocks), `QuoteBlock` (collapsed), `ReactionSummaryRow`, `PageJumpSheet`, `UnreadDot`. The mark (`designsystem/Brand.kt`, `res/drawable/ic_launcher_foreground.xml`) is an original speech bubble (D10); never use the Kajgana logo.

## Architecture

```
:core  (pure Kotlin/JVM, no Android types)
  domain.model   SessionInfo, PageInfo, ForumHome, Category, Forum, SubForum, LatestThread, ForumPage,
                 ThreadSummary, ThreadPage, Post, PostAuthor, ReactionSummary, Alert, Block, Run
  domain.parse   HomeParser (+ NodeParser), ForumParser, ThreadRowParser, ThreadParser, BbContent,
                 SessionParser, parseCount, ParseException
  domain.url     ForumUrls (builders, route(), slugAndId, pageOf), Route, ThreadTarget
  domain.net     Politeness, RequestSpacing, Backoff, BlockDetector, BlockKind
:app
  designsystem   Izvor's components recoloured, Brand (bubble mark), ForumComponents
  data.remote    ForumHttp.kt: WebViewCookieJar, PolitenessInterceptor, ForumFetcher, FetchResult
  data.repo      ForumRepository (Loaded<T>, session StateFlow), SessionRepository (spike 1 check, logout)
  data.settings  SettingsRepository (DataStore: theme, motion, backdrop)
  di             AppModule (OkHttp with the cookie jar and the interceptor)
  ui             navigation (MuabetNavHost, Routes, AppViewModel), common (UiState, LoadedContent,
                 LocalOpenLink, LocalOpenWeb, Custom Tabs, relativeTime), forums, forum, thread, web,
                 settings (SettingsScreen, SessionCheckScreen), placeholder (New, Watched, Alerts until M2)
```

Pure logic belongs in `:core` so it can be unit tested on the JVM. `ui` depends on `data.repo`; screens get `UiState<T>` and render it with `LoadedContent`.

## Behaviour notes (keep in sync with the code)

- **Links** (`LocalOpenLink`): `ForumUrls.route()` sends forum, thread and post links to native screens (unread, latest and post links go through `threadAt`, which follows the forum's redirect), other forum pages to the WebView, other sites to a Custom Tab.
- **Login:** `Routes.web(ForumUrls.login, login = true)`; the WebView screen closes once the page left `/login` and `<html data-logged-in="true">`, and sets a saved-state key so the screen below reloads. "Stay logged in" must be ticked for `xf_user` (the session check shows whether it was).
- **Session state:** every parsed page updates `ForumRepository.session`; tabs that need a login read it through `AppViewModel`.
- **Logout:** clears all WebView cookies and storage, then the session is guest.
- **Thread pages:** one page at a time (15 posts), the page in `SavedStateHandle`; the bottom bar has first, previous, the page jump sheet, next and last; a forum row's "… p." label opens the last page.
- **Forum lists:** pinned threads fold into one row; the next page loads when five rows from the end; threads already shown are not repeated.
- **Images:** Coil with its own OkHttp client (no forum cookies), the User-Agent and a 150 MB disk cache.

## Testing

`:core` tests and fixtures live in `core/src/test`. Add a synthetic fixture plus a test for every markup change or parser bug.

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
