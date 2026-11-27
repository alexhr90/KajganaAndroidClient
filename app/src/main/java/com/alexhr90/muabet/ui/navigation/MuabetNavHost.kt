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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.alexhr90.muabet.designsystem.barGround
import com.alexhr90.muabet.designsystem.screenGround
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.domain.url.Route
import com.alexhr90.muabet.domain.url.ThreadTarget
import com.alexhr90.muabet.ui.common.LocalOpenLink
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.rememberCustomTabOpener
import com.alexhr90.muabet.ui.forum.ForumScreen
import com.alexhr90.muabet.ui.forums.ForumsScreen
import com.alexhr90.muabet.ui.placeholder.LaterTabScreen
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
    const val WEB = "web?url={url}&login={login}"
    const val SETTINGS = "settings"
    const val SESSION_CHECK = "settings/session"

    val TABS = listOf(FORUMS, NEW, WATCHED, ALERTS)

    fun forum(id: Long, slug: String, title: String) = "forum/$id?slug=${Uri.encode(slug)}&title=${Uri.encode(title)}"
    fun thread(id: Long, slug: String, page: Int = 1, title: String = "") =
        "thread?id=$id&slug=${Uri.encode(slug)}&page=$page&title=${Uri.encode(title)}"
    fun threadAt(url: String) = "thread?at=${Uri.encode(url)}"
    fun web(url: String, login: Boolean = false) = "web?url=${Uri.encode(url)}&login=$login"
}

/** The app-wide view of the session, for the tabs that need a login. */
@HiltViewModel
class AppViewModel @Inject constructor(forum: ForumRepository) : ViewModel() {
    val session = forum.session
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.FORUMS, R.string.tab_forums, Icons.Outlined.Forum, Icons.Rounded.Forum),
    Tab(Routes.NEW, R.string.tab_new, Icons.Outlined.NewReleases, Icons.Rounded.NewReleases),
    Tab(Routes.WATCHED, R.string.tab_watched, Icons.Outlined.Bookmarks, Icons.Rounded.Bookmarks),
    Tab(Routes.ALERTS, R.string.tab_alerts, Icons.Outlined.Notifications, Icons.Rounded.Notifications),
)

/**
 * App navigation (SPEC §4): a bottom bar with Forums, New, Watched and Alerts; search (M3) and
 * the settings gear sit in the top bar. Every other screen stacks above the tabs.
 */
@Composable
fun MuabetNavHost(navController: NavHostController = rememberNavController()) {
    val app: AppViewModel = hiltViewModel()
    val session by app.session.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in Routes.TABS
    val context = LocalContext.current
    val openTab = rememberCustomTabOpener(context)

    val openWeb: (String) -> Unit = remember(navController) { { url -> navController.navigate(Routes.web(url)) } }
    val openLink: (String) -> Unit = remember(navController, openTab) {
        { url ->
            when (val r = ForumUrls.route(url)) {
                Route.Home -> navController.navigate(Routes.FORUMS) { popUpTo(navController.graph.findStartDestination().id); launchSingleTop = true }
                is Route.Forum -> navController.navigate(Routes.forum(r.id, r.slug, ""))
                is Route.Thread -> if (r.target == ThreadTarget.Page) {
                    navController.navigate(Routes.thread(r.id, r.slug, r.page))
                } else {
                    navController.navigate(Routes.threadAt(url))
                }
                is Route.Post -> navController.navigate(Routes.threadAt(ForumUrls.post(r.id)))
                is Route.Category, is Route.Member, is Route.ForumWeb -> navController.navigate(Routes.web(ForumUrls.absolute(url) ?: url))
                is Route.External -> openTab(r.url)
            }
        }
    }

    Scaffold(
        containerColor = screenGround,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(showBar, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    if (!LocalBackdrop.current) Rule()
                    NavigationBar(containerColor = barGround, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
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
            Graph(navController, loggedIn = session?.loggedIn == true, modifier = Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

@Composable
private fun Graph(navController: NavHostController, loggedIn: Boolean, modifier: Modifier) {
    // Forward: the new screen slides in a little while the old one recedes; back reverses it.
    // Between tabs only a fade. Under reduced motion only fades (as in Izvor).
    val reduced = LocalReducedMotion.current
    val shift = { full: Int -> if (reduced) 0 else full / 10 }
    fun NavBackStackEntry.isTab() = destination.route in Routes.TABS
    val back: () -> Unit = { if (!navController.popBackStack()) navController.navigate(Routes.FORUMS) }
    val settings = { navController.navigate(Routes.SETTINGS) }
    val login = { navController.navigate(Routes.web(ForumUrls.login, login = true)) }

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
        composable(Routes.FORUMS) { entry ->
            // Set by the login screen, so the list reloads as the logged-in user.
            val reloadKey by entry.savedStateHandle.getStateFlow(KEY_LOGGED_IN, 0L).collectAsStateWithLifecycle()
            ForumsScreen(
                onForum = { f -> navController.navigate(Routes.forum(f.id, f.slug, f.title)) },
                onSettings = settings,
                onLogin = login,
                reloadKey = reloadKey,
            )
        }
        composable(Routes.NEW) {
            LaterTabScreen(stringResource(R.string.tab_new), Icons.Outlined.NewReleases, ForumUrls.whatsNewPosts, needsLogin = false, loggedIn = loggedIn, onSettings = settings, onLogin = login)
        }
        composable(Routes.WATCHED) {
            LaterTabScreen(stringResource(R.string.tab_watched), Icons.Outlined.Bookmarks, ForumUrls.watchedThreads, needsLogin = true, loggedIn = loggedIn, onSettings = settings, onLogin = login)
        }
        composable(Routes.ALERTS) {
            LaterTabScreen(stringResource(R.string.tab_alerts), Icons.Outlined.Notifications, ForumUrls.alerts, needsLogin = true, loggedIn = loggedIn, onSettings = settings, onLogin = login)
        }
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
                onThread = { t, page -> navController.navigate(Routes.thread(t.id, t.slug, page, t.title)) },
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
        ) {
            ThreadScreen(onBack = back)
        }
        composable(
            Routes.WEB,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = ForumUrls.home },
                navArgument("login") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val url = entry.arguments?.getString("url") ?: ForumUrls.home
            val loginMode = entry.arguments?.getBoolean("login") ?: false
            WebScreen(
                url = url,
                onBack = back,
                loginMode = loginMode,
                onLoggedIn = {
                    runCatching { navController.getBackStackEntry(Routes.FORUMS).savedStateHandle[KEY_LOGGED_IN] = System.currentTimeMillis() }
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_LOGGED_IN, System.currentTimeMillis())
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.SETTINGS) { entry ->
            val reloadKey by entry.savedStateHandle.getStateFlow(KEY_LOGGED_IN, 0L).collectAsStateWithLifecycle()
            SettingsScreen(
                onBack = back,
                onLogin = login,
                onSessionCheck = { navController.navigate(Routes.SESSION_CHECK) },
                reloadKey = reloadKey,
            )
        }
        composable(Routes.SESSION_CHECK) { entry ->
            val reloadKey by entry.savedStateHandle.getStateFlow(KEY_LOGGED_IN, 0L).collectAsStateWithLifecycle()
            SessionCheckScreen(onBack = back, onLogin = login, reloadKey = reloadKey)
        }
    }
}

/** Saved-state key the login screen sets on the screen below it, so that screen reloads. */
private const val KEY_LOGGED_IN = "loggedIn"
