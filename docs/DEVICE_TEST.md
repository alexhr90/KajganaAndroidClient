# Device test

What the owner checks on the phone before a release counts as done. Test phone and Android
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

## v1.0.0 (M1 to M4)

Reading
1. Bars: scroll a forum, a thread and Settings under the top bar and the tab bar; text never shows through them, with and without the painted backdrop.
2. Forums: long-press a forum to pin it; pinned forums sit on top and move up or down from the same menu. Hide one: it leaves Forums and New, the bar offers Undo, and Settings → Hidden forums brings it back. Folded categories stay folded after a restart.
3. Thread: swipe left and right between pages; the page bar follows. Open a 1,000+ page thread, jump to page 500, scroll down, go Back and open it again from the list: it returns to the same page and post.
4. Posts sit on separate cards. Tapping the avatar or name opens the member card (details, Open profile, Message, Posts); the ⋮ menu quotes, reacts, bookmarks, copies text or link, shares and opens on the forum.
5. Images uploaded by members show (logged in). Tap one: it opens full screen, pinch or double-tap to zoom. Settings → Load images on tap shows a placeholder until tapped. A broken image says so and opens on tap.
6. Settings → Text size and Row density apply at once.

Account
7. New lists new posts (also as a guest), Watched and Alerts list yours when logged in; scrolling to the end loads more; pull to refresh works. An unread thread opens at its first unread post.
8. The Alerts tab shows a badge with the unread count; opening Alerts clears it.
9. Reply from the thread (top bar or the end of the last page): the forum's form opens; after posting, the app returns to the thread at your post. Try quote, react and watch the same way, and New thread from a forum's top bar.
10. Log out on the forum's website in another browser, or wait for the session to end: a bar above the tabs says you were logged out, with Log in.

Comfort
11. Search from Forums, New or Watched (all or titles only); Search in this thread from the thread's ⋮ menu; a member's Posts from the member card.
12. Mute a thread (long-press in a list, or the thread's ⋮ menu): it disappears from every list, with Undo. Add a muted word in Settings → Mutes: threads with it in the title disappear, posts with it fold to one line that opens on tap.
13. Thread ⋮ → Save for offline: save this page or the next ten. Turn on airplane mode and open those pages: they show with a "Saved copy" note. Settings → Saved threads lists them with their size and deletes them.

Background and polish
14. Settings → Notifications: turning it on asks for the permission (Android 13+). Set 15 minutes, get an alert from another account or wait for one: a notification with the count arrives within about the interval (Android may delay it), and tapping it opens Alerts. Nothing arrives in quiet hours.
15. Add the Muabet widget to the home screen: it lists watched threads (unread with a dot), a row opens the thread at the first unread post, the refresh button updates it.
16. Open a forum.kajgana.com link from another app ("Open with Muabet") and share a forum link to Muabet: both open natively.
17. Settings → Backup: export lists to a file, delete a pin and a mute, import the file: they come back.
18. TalkBack: tabs, rows, post menus and the page bar read sensible labels; every control is reachable.
19. Languages: Macedonian, German and Russian show no English leftovers on the new screens.

## v1.1.0 (fully native)

Content
1. Open threads with YouTube links and embeds: each shows its thumbnail; tap plays it inside the post; the player's full-screen button fills the screen sideways; Back leaves full screen. "Open in YouTube" opens the YouTube app.
2. Other media: a Vimeo, Dailymotion or Streamable video plays in place; Spotify and SoundCloud show "Listen here" and a small player; an X, Instagram or TikTok post opens in its app (or "Show here").
3. A video attachment plays in place; the full-screen button continues where it was. A PDF or other file attachment downloads with a notification.
4. Tables, "Info" or hidden-content boxes and link previews (with picture and description) look native. No post anywhere shows "Part of this post opens on the forum" any more; if something still looks wrong, note the thread.

Writing
5. Reply (top bar, end of the last page): the compose screen opens with the keyboard. Use bold, a link and a spoiler from the tool row, add a photo, send: the thread jumps to your post with the photo.
6. Quote (under a post, or its menu): the quote arrives filled in; send.
7. Leave the compose screen without sending, come back to the same reply: the draft is still there.
8. Edit one of your posts from its menu: the text arrives as BB code; change it and send; the thread title (if it is the first post) is unchanged.
9. React from the button under a post: pick a reaction; tap your reaction again to take it back. Bookmark and remove the bookmark from the menu; Watch and Unwatch from the thread's menu.
10. New thread from a forum's top bar: title and text; it opens the new thread.
11. Delete one of your posts (with a reason), and report a post: both confirm in the message bar.
12. Try sending an empty reply, and a reply in airplane mode: the screen explains, nothing is lost.

Messages and profiles
13. Alerts → the envelope (with a badge when unread) or Settings → Direct messages: the conversations list; open one, read, Reply; New message to a member.
14. Tap a member's name → Open profile: the profile is native (picture, title, figures, Message, Posts).
15. Settings → Bookmarks lists your bookmarks natively.
16. Log out: drafts are gone afterwards.
