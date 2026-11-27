# Device test

What the owner checks on the phone before each milestone counts as done. Test phone and Android
version: **not given yet** (SPEC §7). Install the preview APK (`Muabet-<sha>.apk`, app id
`com.alexhr90.muabet.debug`) from the branch's `preview/…` pre-release, or the release APK.

## M0, v0.1.0

1. Install and open. The launch intro (bubble, two lines, name) plays once and is under a second; a tap skips it.
2. Forums lists the live categories and forums. Unread forums are bolder with an orange dot. Tapping a category header folds it.
3. Open a forum: threads load, pinned threads sit in one "Pinned threads" row, scrolling to the end loads the next page, pull to refresh works.
4. Open a long thread (1,000+ pages) from its "… p." label: it opens at the last page. Use the page bar (first, previous, the page number for the jump sheet, next, last) and jump to page 500.
5. Posts show the author, time and number in one line; tapping it shows the role and details. Quotes are collapsed with "Show all"; spoilers open on tap; images load and enlarge on tap; YouTube embeds open in a Custom Tab; forum links open natively.
6. Settings → Log in. Log in on the forum's page **with "Stay logged in" ticked**. The screen closes by itself.
7. Settings → Session check. Expected: "The login works in the app", forum cookies include `xf_user`, "Seen as" is your forum name. Write the result (pass or fail and what it showed) into `docs/DECISIONS.md` D6, or send it to Claude.
8. Watched and Alerts: as a guest they ask you to log in; logged in they offer the forum's page.
9. Settings: theme (system, light, dark), motion, backdrop (none, time of day, dawn, day, dusk, night, aurora, ember) apply at once. About says the app is unofficial.
10. Languages: switch the phone (or the app's language in Android settings) to Macedonian, German and Russian; no English leftovers.
11. Settings → Log out, then Session check: "Seen as" is Guest.
12. If any page shows "This page cannot be shown here yet" or "The forum asks for a check", note which page; "Open on the forum" must show it.
