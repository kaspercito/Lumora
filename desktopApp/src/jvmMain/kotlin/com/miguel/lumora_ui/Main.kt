package com.lagradost.cloudstream4

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream4.compose.LocalFocusOutlineDefault
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.check
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.generated.resources.empty_downloads
import com.lagradost.cloudstream4.generated.resources.nav_downloads
import com.lagradost.cloudstream4.generated.resources.nav_home
import com.lagradost.cloudstream4.generated.resources.nav_library
import com.lagradost.cloudstream4.generated.resources.nav_search
import com.lagradost.cloudstream4.generated.resources.nav_settings
import com.lagradost.cloudstream4.generated.resources.settings_about
import com.lagradost.cloudstream4.generated.resources.settings_appearance
import com.lagradost.cloudstream4.generated.resources.settings_version
import com.lagradost.cloudstream4.generated.resources.sources_summary
import com.lagradost.cloudstream4.generated.resources.sources_title
import com.lagradost.cloudstream4.generated.resources.theme_amoled
import com.lagradost.cloudstream4.generated.resources.theme_dark
import com.lagradost.cloudstream4.generated.resources.theme_dracula
import com.lagradost.cloudstream4.generated.resources.theme_lavender
import com.lagradost.cloudstream4.generated.resources.theme_light
import com.lagradost.cloudstream4.generated.resources.theme_silent_blue
import com.lagradost.cloudstream4.generated.resources.title_downloads
import com.lagradost.cloudstream4.generated.resources.title_home
import com.lagradost.cloudstream4.generated.resources.title_library
import com.lagradost.cloudstream4.generated.resources.title_search
import com.lagradost.cloudstream4.generated.resources.title_settings
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import com.mihon.presentation.settings.widget.PreferenceGroupHeader
import com.mihon.presentation.settings.widget.TextPreferenceWidget
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension

/** Same five destinations as the bottom bar of the mobile app. */
private enum class LumoraTab(val title: StringResource, val icon: DrawableResource) {
    Home(Res.string.title_home, Res.drawable.nav_home),
    Search(Res.string.title_search, Res.drawable.nav_search),
    Library(Res.string.title_library, Res.drawable.nav_library),
    Downloads(Res.string.title_downloads, Res.drawable.nav_downloads),
    Settings(Res.string.title_settings, Res.drawable.nav_settings),
}

private val selectableThemes = listOf(
    CloudStreamThemeMode.Dark to Res.string.theme_dark,
    CloudStreamThemeMode.Amoled to Res.string.theme_amoled,
    CloudStreamThemeMode.Light to Res.string.theme_light,
    CloudStreamThemeMode.Dracula to Res.string.theme_dracula,
    CloudStreamThemeMode.Lavender to Res.string.theme_lavender,
    CloudStreamThemeMode.SilentBlue to Res.string.theme_silent_blue,
)

fun main() = application {
    var themeMode by remember { mutableStateOf(CloudStreamThemeMode.Dark) }
    // Loads the plugins already installed on disk (registers only once)
    LaunchedEffect(Unit) { DesktopProviders.registerAll() }

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.default_icon),
        // Phone-like proportions by default, but freely resizable down to a sane minimum
        state = rememberWindowState(width = 520.dp, height = 880.dp),
    ) {
        LaunchedEffect(window) { window.minimumSize = Dimension(380, 560) }
        CloudStreamTheme(mode = themeMode) {
            CompositionLocalProvider(LocalFocusOutlineDefault provides false) {
                LumoraApp(themeMode = themeMode, onThemeModeChange = { themeMode = it })
            }
        }
    }
}

@Composable
private fun LumoraApp(
    themeMode: CloudStreamThemeMode,
    onThemeModeChange: (CloudStreamThemeMode) -> Unit,
) {
    var tab by remember { mutableStateOf(LumoraTab.Home) }
    // Stack of opened detail pages: opening a recommendation pushes, "back" pops
    var details by remember { mutableStateOf(listOf<SearchResponse>()) }
    var showSources by remember { mutableStateOf(false) }
    var playRequest by remember { mutableStateOf<PlayRequest?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                LumoraTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = {
                            tab = entry
                            details = emptyList()
                            playRequest = null
                            showSources = false
                        },
                        icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                        label = { Text(stringResource(entry.title)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val open = { item: SearchResponse -> details = details + item }
            val request = playRequest
            when {
                request != null -> PlaybackScreen(request, onClose = { playRequest = null })
                details.isNotEmpty() -> DetailScreen(
                    item = details.last(),
                    onBack = { details = details.dropLast(1) },
                    onOpen = open,
                    onPlay = { playRequest = it },
                )
                else -> when (tab) {
                    LumoraTab.Home -> HomeScreen(open)
                    LumoraTab.Search -> SearchScreen(open)
                    LumoraTab.Library -> LibraryScreen(open)
                    LumoraTab.Downloads -> Column(Modifier.fillMaxSize()) {
                        ScreenHeader(stringResource(Res.string.title_downloads))
                        EmptyState(Res.drawable.nav_downloads, stringResource(Res.string.empty_downloads))
                    }
                    LumoraTab.Settings ->
                        if (showSources) SourcesScreen { showSources = false }
                        else SettingsScreen(themeMode, onThemeModeChange) { showSources = true }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: CloudStreamThemeMode,
    onThemeModeChange: (CloudStreamThemeMode) -> Unit,
    onOpenSources: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(Res.string.title_settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PreferenceGroupHeader(stringResource(Res.string.settings_appearance))
            selectableThemes.forEach { (mode, name) ->
                TextPreferenceWidget(
                    title = stringResource(name),
                    widget = if (mode == themeMode) {
                        {
                            Icon(
                                painterResource(Res.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                    onPreferenceClick = { onThemeModeChange(mode) },
                )
            }
            PreferenceGroupHeader(stringResource(Res.string.sources_title))
            TextPreferenceWidget(
                title = stringResource(Res.string.sources_title),
                subtitle = stringResource(Res.string.sources_summary, PluginManager.apis.size),
                onPreferenceClick = onOpenSources,
            )
            PreferenceGroupHeader(stringResource(Res.string.settings_about))
            TextPreferenceWidget(
                title = stringResource(Res.string.app_name),
                subtitle = stringResource(Res.string.settings_version),
            )
        }
    }
}
