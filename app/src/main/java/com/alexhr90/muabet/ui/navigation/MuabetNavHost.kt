package com.alexhr90.muabet.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.designsystem.LocalBackdrop
import com.alexhr90.muabet.designsystem.LocalReducedMotion
import com.alexhr90.muabet.designsystem.MuabetMotion
import com.alexhr90.muabet.designsystem.Rule
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.Tone
import com.alexhr90.muabet.designsystem.barGround
import com.alexhr90.muabet.designsystem.color
import com.alexhr90.muabet.designsystem.screenGround
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.domain.url.Route
import com.alexhr90.muabet.domain.url.ThreadTarget
import com.alexhr90.muabet.ui.common.LocalOpenLink
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.rememberExternalOpener
import com.alexhr90.muabet.ui.compose.ComposeScreen
import com.alexhr90.muabet.ui.compose.ComposeTarget
import com.alexhr90.muabet.ui.member.MemberScreen
import com.alexhr90.muabet.ui.messages.BookmarksScreen
import com.alexhr90.muabet.ui.messages.ConversationScreen
import com.alexhr90.muabet.ui.messages.ConversationsScreen
import com.alexhr90.muabet.ui.forum.ForumScreen
import com.alexhr90.muabet.ui.forum.OpenThread
import com.alexhr90.muabet.ui.forums.ForumsScreen
import com.alexhr90.muabet.ui.library.HiddenForumsScreen
import com.alexhr90.muabet.ui.library.MutesScreen
import com.alexhr90.muabet.ui.library.SavedScreen
import com.alexhr90.muabet.ui.lists.AlertsScreen
import com.alexhr90.muabet.ui.lists.NewScreen
import com.alexhr90.muabet.ui.lists.WatchedScreen
import com.alexhr90.muabet.ui.search.SearchScreen
import com.alexhr90.muabet.ui.settings.SessionCheckScreen
import com.alexhr90.muabet.ui.settings.SettingsScreen
import com.alexhr90.muabet.ui.thread.ThreadScreen
import com.alexhr90.muabet.ui.web.WebScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

object Routes {
    const val FORUMS = "forums"
    const val NEW = "new"
    const val WATCHED = "watched"
    const val ALERTS = "alerts"
    const val FORUM = "forum/{id}?slug={slug}&title={title}"
    const val THREAD = "thread?id={id}&slug={slug}&page={page}&title={title}&at={at}"
    const val WEB = "web?url={url}&login={login}&compose={compose}"
    const val SEARCH = "search?thread={thread}&title={title}&url={url}"
    const val SETTINGS = "settings"
    const val SESSION_CHECK = "settings/session"
    const val HIDDEN = "settings/hidden"
    const val MUTES = "settings/mutes"
    const val SAVED = "settings/saved"
    const val COMPOSE = "compose?kind={kind}&id={id}&slug={slug}&title={title}&post={post}&to={to}"
    const val MESSAGES = "messages"
    const val CONVERSATION = "messages/{id}?slug={slug}&title={title}"
    const val MEMBER = "member/{id}?slug={slug}"
    const val BOOKMARKS = "bookmarks"

    val TABS = listOf(FORUMS, NEW, WATCHED, ALERTS)

    fun forum(id: Long, slug: String, title: String) = "forum/$id?slug=${Uri.encode(slug)}&title=${Uri.encode(title)}"

    /** [page] 0 resumes where the user stopped. */
    fun thread(id: Long, slug: String, page: Int = 0, title: String = "") =
        "thread?id=$id&slug=${Uri.encode(slug)}&page=$page&title=${Uri.encode(title)}"
    fun threadAt(url: String) = "thread?at=${Uri.encode(url)}"
    fun web(url: String, login: Boolean = false, compose: Boolean = false) = "web?url=${Uri.encode(url)}&login=$login&compose=$compose"
    fun search(threadId: Long = 0, title: String = "", url: String = "") = "search?thread=$threadId&title=${Uri.encode(title)}&url=${Uri.encode(url)}"
    fun compose(t: ComposeTarget) =
        "compose?kind=${t.kind.name}&id=${t.id}&slug=${Uri.encode(t.slug)}&title=${Uri.encode(t.title)}&post=${t.post}&to=${Uri.encode(t.to)}"
    fun conversation(id: Long, slug: String, title: String = "") = "messages/$id?slug=${Uri.encode(slug)}&title=${Uri.encode(title)}"
    fun member(id: Long, slug: String) = "member/$id?slug=${Uri.encode(slug)}"
}

/** The app-wide view of the session, for the tabs that need a login and the expired-session bar. */
@HiltViewModel
class AppViewModel @Inject constructor(forum: ForumRepository) : ViewModel() {
    val session = forum.session
    val expired = forum.sessionExpired
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.FORUMS, R.string.tab_forums, Icons.Outlined.Forum, Icons.Rounded.Forum),
    Tab(Routes.NEW, R.string.tab_new, Icons.Outlined.NewReleases, Icons.Rounded.NewReleases),
    Tab(Routes.WATCHED, R.string.tab_watched, Icons.Outlined.Bookmarks, Icons.Rounded.Bookmarks),
    Tab(Routes.ALERTS, R.string.tab_alerts, Icons.Outlined.Notifications, Icons.Rounded.Notifications),
)

/**
 * App navigation (SPEC §4): a bottom bar with Forums, New, Watched and Alerts; search and the
 * settings gear sit in the top bar. Every other screen stacks above the tabs. [incomingLink] is a
 * forum address from outside the app (Open with, share, widget, notification).
 */
@Composable
fun MuabetNavHost(
    incomingLink: String? = null,
    onLinkHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val app: AppViewModel = hiltViewModel()
    val session by app.session.collectAsStateWithLifecycle()
    val expired by app.expired.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in Routes.TABS
    val context = LocalContext.current
    // Other sites open in their own app when one is installed (YouTube, Instagram…), else a Custom Tab.
    val openTab = rememberExternalOpener(context)

    fun goTab(r: String) = navController.navigate(r) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val openWeb: (String) -> Unit = remember(navController) { { url -> navController.navigate(Routes.web(url)) } }
    val openLink: (String) -> Unit = remember(navController, openTab) {
        { url ->
            val abs = ForumUrls.absolute(url) ?: url
            when {
                // The account lists have native tabs.
                sameList(abs, ForumUrls.alerts) -> goTab(Routes.ALERTS)
                sameList(abs, ForumUrls.watchedThreads) -> goTab(Routes.WATCHED)
                sameList(abs, ForumUrls.whatsNewPosts) -> goTab(Routes.NEW)
                else -> when (val r = ForumUrls.route(abs)) {
                    Route.Home -> goTab(Routes.FORUMS)
                    is Route.Forum -> navController.navigate(Routes.forum(r.id, r.slug, ""))
                    is Route.Thread -> if (r.target == ThreadTarget.Page) {
                        navController.navigate(Routes.thread(r.id, r.slug, r.page))
                    } else {
                        navController.navigate(Routes.threadAt(abs))
                    }
                    is Route.Post -> navController.navigate(Routes.threadAt(ForumUrls.post(r.id)))
                    is Route.Member -> navController.navigate(Routes.member(r.id, r.slug))
                    Route.Conversations -> navController.navigate(Routes.MESSAGES)
                    is Route.Conversation -> navController.navigate(Routes.conversation(r.id, r.slug))
                    Route.Bookmarks -> navController.navigate(Routes.BOOKMARKS)
                    is Route.Category, is Route.ForumWeb -> navController.navigate(Routes.web(abs))
                    is Route.External -> openTab(r.url)
                }
            }
        }
    }
    LaunchedEffect(incomingLink) {
        if (incomingLink != null) {
            openLink(incomingLink)
            onLinkHandled()
        }
    }
    val login = { navController.navigate(Routes.web(ForumUrls.login, login = true)) }

    Scaffold(
        containerColor = screenGround,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(showBar, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.background(barGround)) {
                    if (expired) ExpiredBar(onLogin = login)
                    if (!LocalBackdrop.current) Rule()
                    NavigationBar(containerColor = barGround, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            val badge = if (tab.route == Routes.ALERTS) session?.alertsUnread ?: 0 else 0
                            NavigationBarItem(
                                selected = selected,
                                onClick = { if (!selected) goTab(tab.route) },
                                icon = {
                                    val icon = if (selected) tab.selectedIcon else tab.icon
                                    if (badge > 0) {
                                        val label = stringResource(R.string.cd_unread_alerts, badge)
                                        BadgedBox(badge = { Badge { Text(if (badge > 99) "99+" else badge.toString()) } }, modifier = Modifier.semantics { contentDescription = label }) {
                                            Icon(icon, contentDescription = null)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = null)
                                    }
                                },
                                label = { Text(stringResource(tab.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalOpenLink provides openLink, LocalOpenWeb provides openWeb) {
            Graph(navController, login = login, openLink = openLink, modifier = Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

/** True when [url] is the account list [list], with or without a trailing slash or a page. */
private fun sameList(url: String, list: String): Boolean {
    val a = url.substringBefore('?').trimEnd('/')
    val b = list.trimEnd('/')
    return a == b || (a.startsWith("$b/") && !a.removePrefix("$b/").contains('/') && b == ForumUrls.whatsNewPosts.trimEnd('/'))
}

/** The forum ended the session (SPEC §4 M2): say so above the tabs, with Log in. */
@Composable
private fun ExpiredBar(onLogin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Space.l, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.GppMaybe, contentDescription = null, tint = Tone.Warning.color())
        Spacer(Modifier.width(Space.m))
        Text(stringResource(R.string.session_expired), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onLogin) { Text(stringResource(R.string.action_log_in)) }
    }
}

@Composable
private fun Graph(navController: NavHostController, login: () -> Unit, openLink: (String) -> Unit, modifier: Modifier) {
    // Forward: the new screen slides in a little while the old one recedes; back reverses it.
    // Between tabs only a fade. Under reduced motion only fades (as in Izvor).
    val reduced = LocalReducedMotion.current
    val shift = { full: Int -> if (reduced) 0 else full / 10 }
    fun NavBackStackEntry.isTab() = destination.route in Routes.TABS
    val back: () -> Unit = { if (!navController.popBackStack()) navController.navigate(Routes.FORUMS) }
    val settings = { navController.navigate(Routes.SETTINGS) }
    val search = { navController.navigate(Routes.search()) }
    val openThread: OpenThread = { t, page ->
        if (page == 0 && t.unread) navController.navigate(Routes.threadAt(ForumUrls.threadUnread(t.slug, t.id)))
        else navController.navigate(Routes.thread(t.id, t.slug, page, t.title))
    }
    // Writing (reply, quote, edit, new thread, messages) happens on the native compose screen.
    val compose: (ComposeTarget) -> Unit = { t -> navController.navigate(Routes.compose(t)) }
    val profile: (Long, String) -> Unit = { id, slug -> navController.navigate(Routes.member(id, slug)) }

    NavHost(
        navController = navController,
        startDestination = Routes.FORUMS,
        modifier = modifier,
        enterTransition = {
            if (initialState.isTab() && targetState.isTab()) fadeIn(tween(MuabetMotion.NAVIGATION))
            else fadeIn(tween(MuabetMotion.NAVIGATION)) + slideInHorizontally(tween(MuabetMotion.NAVIGATION)) { shift(it) }
        },
        exitTransition = {
            if (initialState.isTab() && targetState.isTab()) fadeOut(tween(MuabetMotion.EXIT))
            else fadeOut(tween(MuabetMotion.EXIT)) + scaleOut(tween(MuabetMotion.EXIT), targetScale = if (reduced) 1f else 0.97f)
        },
        popEnterTransition = {
            if (initialState.isTab() && targetState.isTab()) fadeIn(tween(MuabetMotion.NAVIGATION))
            else fadeIn(tween(MuabetMotion.NAVIGATION)) + scaleIn(tween(MuabetMotion.NAVIGATION), initialScale = if (reduced) 1f else 0.97f)
        },
        popExitTransition = {
            if (initialState.isTab() && targetState.isTab()) fadeOut(tween(MuabetMotion.EXIT))
            else fadeOut(tween(MuabetMotion.EXIT)) + slideOutHorizontally(tween(MuabetMotion.EXIT)) { shift(it) }
        },
    ) {
        composable(Routes.FORUMS) {
            ForumsScreen(
                onForum = { f -> navController.navigate(Routes.forum(f.id, f.slug, f.title)) },
                onSettings = settings,
                onSearch = search,
                onLogin = login,
            )
        }
        composable(Routes.NEW) { NewScreen(onThread = openThread, onSettings = settings, onSearch = search) }
        composable(Routes.WATCHED) { WatchedScreen(onThread = openThread, onSettings = settings, onSearch = search, onLogin = login) }
        composable(Routes.ALERTS) { AlertsScreen(onSettings = settings, onLogin = login, onMessages = { navController.navigate(Routes.MESSAGES) }) }
        composable(
            Routes.FORUM,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("slug") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ForumScreen(
                onBack = back,
                onForum = { f -> navController.navigate(Routes.forum(f.id, f.slug, f.title)) },
                onThread = openThread,
                onNewThread = compose,
            )
        }
        composable(
            Routes.THREAD,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("slug") { type = NavType.StringType; defaultValue = "" },
                navArgument("page") { type = NavType.IntType; defaultValue = 1 },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("at") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val openAt by entry.savedStateHandle.getStateFlow<String?>(KEY_OPEN_AT, null).collectAsStateWithLifecycle()
            ThreadScreen(
                onBack = back,
                onCompose = compose,
                onProfile = profile,
                onSearchInThread = { id, title -> navController.navigate(Routes.search(id, title)) },
                onSearchUrl = { url -> navController.navigate(Routes.search(url = url)) },
                openAtRequest = openAt,
                onOpenAtHandled = { entry.savedStateHandle[KEY_OPEN_AT] = null },
            )
        }
        composable(
            Routes.WEB,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = ForumUrls.home },
                navArgument("login") { type = NavType.BoolType; defaultValue = false },
                navArgument("compose") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val url = entry.arguments?.getString("url") ?: ForumUrls.home
            val loginMode = entry.arguments?.getBoolean("login") ?: false
            val composeMode = entry.arguments?.getBoolean("compose") ?: false
            WebScreen(
                url = url,
                onBack = back,
                loginMode = loginMode,
                // The forum's pages note the new session as soon as the next page loads.
                onLoggedIn = { navController.popBackStack() },
                onNativeTarget = if (composeMode) {
                    { target ->
                        navController.popBackStack()
                        val top = navController.currentBackStackEntry
                        if (top != null && top.destination.route == Routes.THREAD) top.savedStateHandle[KEY_OPEN_AT] = target else openLink(target)
                    }
                } else {
                    null
                },
            )
        }
        composable(
            Routes.SEARCH,
            arguments = listOf(
                navArgument("thread") { type = NavType.LongType; defaultValue = 0L },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            SearchScreen(onBack = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onLogin = login,
                onMessages = { navController.navigate(Routes.MESSAGES) },
                onBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                onSessionCheck = { navController.navigate(Routes.SESSION_CHECK) },
                onHidden = { navController.navigate(Routes.HIDDEN) },
                onMutes = { navController.navigate(Routes.MUTES) },
                onSaved = { navController.navigate(Routes.SAVED) },
            )
        }
        composable(Routes.SESSION_CHECK) { SessionCheckScreen(onBack = back, onLogin = login) }
        composable(Routes.HIDDEN) { HiddenForumsScreen(onBack = back) }
        composable(Routes.MUTES) { MutesScreen(onBack = back) }
        composable(
            Routes.COMPOSE,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType; defaultValue = "Reply" },
                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                navArgument("slug") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("post") { type = NavType.LongType; defaultValue = 0L },
                navArgument("to") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ComposeScreen(
                onBack = back,
                onLogin = login,
                onDone = { target ->
                    navController.popBackStack()
                    val top = navController.currentBackStackEntry
                    val r = target?.let { ForumUrls.route(it) }
                    when {
                        target == null -> Unit
                        top?.destination?.route == Routes.THREAD && (r is Route.Thread || r is Route.Post) -> top.savedStateHandle[KEY_OPEN_AT] = target
                        top?.destination?.route == Routes.CONVERSATION && r is Route.Conversation &&
                            top.arguments?.getLong("id") == r.id -> top.savedStateHandle[KEY_REFRESH] = true
                        else -> openLink(target)
                    }
                },
            )
        }
        composable(Routes.MESSAGES) {
            ConversationsScreen(
                onBack = back,
                onOpen = { c -> navController.navigate(Routes.conversation(c.id, c.slug, c.title)) },
                onNew = { compose(ComposeTarget(com.alexhr90.muabet.ui.compose.ComposeKind.NewMessage)) },
                onLogin = login,
            )
        }
        composable(
            Routes.CONVERSATION,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("slug") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val refresh by entry.savedStateHandle.getStateFlow(KEY_REFRESH, false).collectAsStateWithLifecycle()
            ConversationScreen(
                onBack = back,
                onReply = { id, slug, title -> compose(ComposeTarget(com.alexhr90.muabet.ui.compose.ComposeKind.Message, id, slug, title)) },
                onProfile = profile,
                refreshRequest = refresh,
                onRefreshHandled = { entry.savedStateHandle[KEY_REFRESH] = false },
            )
        }
        composable(
            Routes.MEMBER,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("slug") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            MemberScreen(
                onBack = back,
                onMessage = { name -> compose(ComposeTarget(com.alexhr90.muabet.ui.compose.ComposeKind.NewMessage, to = name)) },
                onPosts = { id -> navController.navigate(Routes.search(url = ForumUrls.postsBy(id))) },
            )
        }
        composable(Routes.BOOKMARKS) { BookmarksScreen(onBack = back, onLogin = login) }
        composable(Routes.SAVED) {
            SavedScreen(onBack = back, onOpen = { id, slug, page, title -> navController.navigate(Routes.thread(id, slug, page, title)) })
        }
    }
}

/** Saved-state key: a thread link the screen below a forum form should jump to. */
private const val KEY_OPEN_AT = "openAt"

/** Saved-state key: the conversation below the compose screen should reload to the newest message. */
private const val KEY_REFRESH = "refresh"
