package com.lagradost.cloudstream4

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.lagradost.cloudstream4.generated.resources.extension
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition
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
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream4.compose.LocalFocusOutlineDefault
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.arrow_back
import com.lagradost.cloudstream4.generated.resources.check
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.generated.resources.empty_downloads
import com.lagradost.cloudstream4.generated.resources.nav_downloads
import com.lagradost.cloudstream4.generated.resources.nav_home
import com.lagradost.cloudstream4.generated.resources.nav_library
import com.lagradost.cloudstream4.generated.resources.nav_search
import com.lagradost.cloudstream4.generated.resources.nav_settings
import com.lagradost.cloudstream4.generated.resources.settings_about
import com.lagradost.cloudstream4.generated.resources.settings_sources_group
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

/** A theme the user can pick, with the colors used to draw its little preview. */
private class ThemeOption(
    val mode: CloudStreamThemeMode,
    val name: StringResource,
    val background: Color,
    val card: Color,
    val text: Color,
)

private val selectableThemes = listOf(
    ThemeOption(CloudStreamThemeMode.Dark, Res.string.theme_dark, Color(0xFF111111), Color(0xFF2B2C30), Color(0xFFE9EAEE)),
    ThemeOption(CloudStreamThemeMode.Amoled, Res.string.theme_amoled, Color(0xFF000000), Color(0xFF1A1A1A), Color(0xFFE9EAEE)),
    ThemeOption(CloudStreamThemeMode.Light, Res.string.theme_light, Color(0xFFFFFFFF), Color(0xFFEEEEEE), Color(0xFF202125)),
    ThemeOption(CloudStreamThemeMode.Dracula, Res.string.theme_dracula, Color(0xFF282A36), Color(0xFF44475A), Color(0xFFF8F8F2)),
    ThemeOption(CloudStreamThemeMode.Lavender, Res.string.theme_lavender, Color(0xFFFDF0FB), Color(0xFFEBDDF7), Color(0xFF2D1B47)),
    ThemeOption(CloudStreamThemeMode.SilentBlue, Res.string.theme_silent_blue, Color(0xFF151A30), Color(0xFF3A446A), Color(0xFFE0E1F3)),
)

/** Everything about "where am I" lives here so the window can handle Esc / Alt+← no matter what has focus. */
private class NavState {
    var tab by mutableStateOf(LumoraTab.Home)
    /** Stack of opened detail pages: opening a recommendation pushes, "back" pops. */
    var details by mutableStateOf(listOf<SearchResponse>())
    var showSources by mutableStateOf(false)
    var playRequest by mutableStateOf<PlayRequest?>(null)

    fun selectTab(entry: LumoraTab) {
        tab = entry
        details = emptyList()
        playRequest = null
        showSources = false
    }

    /** Goes one step back; false when already at a root screen. */
    fun back(): Boolean = when {
        playRequest != null -> { playRequest = null; true }
        details.isNotEmpty() -> { details = details.dropLast(1); true }
        showSources -> { showSources = false; true }
        else -> false
    }
}

fun main() = application {
    var themeMode by remember { mutableStateOf(CloudStreamThemeMode.Dark) }
    val nav = remember { NavState() }
    // Loads the plugins already installed on disk (registers only once)
    LaunchedEffect(Unit) {
        installHttpLogging()
        DesktopProviders.registerAll()
    }
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        size = DpSize(1180.dp, 780.dp),
    )
    // Pantalla completa de toda la ventana mientras el reproductor la pide
    LaunchedEffect(PlayerFullscreen.active) {
        windowState.placement =
            if (PlayerFullscreen.active) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.default_icon),
        // A normal desktop window: wide, centered on screen and freely resizable
        state = windowState,
        onPreviewKeyEvent = { event ->
            val goBack = event.type == KeyEventType.KeyDown &&
                (event.key == Key.Escape || (event.isAltPressed && event.key == Key.DirectionLeft))
            if (goBack && PlayerFullscreen.active) {
                // Esc sale primero de la pantalla completa del reproductor
                PlayerFullscreen.active = false
                true
            } else goBack && nav.back()
        },
    ) {
        LaunchedEffect(window) { window.minimumSize = Dimension(760, 520) }
        CloudStreamTheme(mode = themeMode) {
            CompositionLocalProvider(LocalFocusOutlineDefault provides false) {
                LumoraApp(nav = nav, themeMode = themeMode, onThemeModeChange = { themeMode = it })
            }
        }
    }
}

@Composable
private fun LumoraApp(
    nav: NavState,
    themeMode: CloudStreamThemeMode,
    onThemeModeChange: (CloudStreamThemeMode) -> Unit,
) {
    // Wide windows get a side bar like any desktop app; narrow ones keep the phone-style bottom bar
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 720.dp
        val expanded = maxWidth >= 980.dp
        Row(Modifier.fillMaxSize()) {
            if (wide && !PlayerFullscreen.active) {
                Sidebar(nav, expanded)
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                bottomBar = {
                    if (!wide && !PlayerFullscreen.active) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            LumoraTab.entries.forEach { entry ->
                                NavigationBarItem(
                                    selected = nav.tab == entry,
                                    onClick = { nav.selectTab(entry) },
                                    icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                                    label = { Text(stringResource(entry.title)) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val open = { item: SearchResponse -> nav.details = nav.details + item }
                    val request = nav.playRequest
                    when {
                        request != null -> PlaybackScreen(request, onClose = { nav.playRequest = null })
                        nav.details.isNotEmpty() -> DetailScreen(
                            item = nav.details.last(),
                            onBack = { nav.back() },
                            onOpen = open,
                            onPlay = { nav.playRequest = it },
                        )
                        else -> when (nav.tab) {
                            LumoraTab.Home -> HomeScreen(open, onAddSources = {
                                nav.selectTab(LumoraTab.Settings)
                                nav.showSources = true
                            })
                            LumoraTab.Search -> SearchScreen(open)
                            LumoraTab.Library -> LibraryScreen(open)
                            LumoraTab.Downloads -> Column(Modifier.fillMaxSize()) {
                                ScreenHeader(stringResource(Res.string.title_downloads))
                                EmptyState(Res.drawable.nav_downloads, stringResource(Res.string.empty_downloads))
                            }
                            LumoraTab.Settings ->
                                if (nav.showSources) SourcesScreen { nav.showSources = false }
                                else SettingsScreen(themeMode, onThemeModeChange) { nav.showSources = true }
                        }
                    }
                }
            }
        }
    }
}

/** Left navigation: logo, the main destinations and Settings pinned to the bottom. */
@Composable
private fun Sidebar(nav: NavState, expanded: Boolean) {
    Column(
        Modifier
            .width(if (expanded) 232.dp else 84.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = if (expanded) 14.dp else 10.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (expanded) 8.dp else 0.dp, bottom = 18.dp),
            horizontalArrangement = if (expanded) Arrangement.spacedBy(12.dp) else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(Res.drawable.default_icon),
                contentDescription = stringResource(Res.string.app_name),
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)),
            )
            if (expanded) {
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        LumoraTab.entries.filter { it != LumoraTab.Settings }.forEach { entry ->
            SidebarItem(entry, selected = nav.tab == entry, expanded = expanded) { nav.selectTab(entry) }
        }
        Spacer(Modifier.weight(1f))
        SidebarItem(LumoraTab.Settings, selected = nav.tab == LumoraTab.Settings, expanded = expanded) {
            nav.selectTab(LumoraTab.Settings)
        }
    }
}

@Composable
private fun SidebarItem(entry: LumoraTab, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val background = when {
        selected -> scheme.primary.copy(alpha = 0.18f)
        hovered -> scheme.onBackground.copy(alpha = 0.07f)
        else -> Color.Transparent
    }
    val tint = if (selected) scheme.primary else scheme.onSurfaceVariant
    val label = stringResource(entry.title)
    val shape = RoundedCornerShape(14.dp)
    if (expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(background)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .handCursor()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(entry.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                label,
                color = if (selected) scheme.onBackground else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(background)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .handCursor()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(painterResource(entry.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    themeMode: CloudStreamThemeMode,
    onThemeModeChange: (CloudStreamThemeMode) -> Unit,
    onOpenSources: () -> Unit,
) {
    CenteredContent(maxWidth = 820.dp) {
        ScreenHeader(stringResource(Res.string.title_settings))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            SettingsGroup(stringResource(Res.string.settings_appearance)) {
                FlowRow(
                    Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    selectableThemes.forEach { option ->
                        ThemeTile(option, selected = option.mode == themeMode) { onThemeModeChange(option.mode) }
                    }
                }
            }
            SettingsGroup(stringResource(Res.string.settings_sources_group)) {
                SettingsRow(
                    icon = Res.drawable.extension,
                    title = stringResource(Res.string.sources_title),
                    subtitle = stringResource(Res.string.sources_summary, PluginManager.apis.size),
                    onClick = onOpenSources,
                )
            }
            SettingsGroup(stringResource(Res.string.settings_about)) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painterResource(Res.drawable.default_icon),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Column {
                        Text(
                            stringResource(Res.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(Res.string.settings_version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Little preview of a theme: its background, a card and a text line, plus the accent color. */
@Composable
private fun ThemeTile(option: ThemeOption, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(158.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .handCursor(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(shape)
                .background(option.background)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.width(60.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(option.text.copy(alpha = 0.85f)))
                Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(7.dp)).background(option.card))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(scheme.primary),
            )
            if (selected) {
                Icon(
                    painterResource(Res.drawable.check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).padding(2.dp),
                )
            }
        }
        Text(
            stringResource(option.name),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Titled group of settings on a rounded card. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Panel(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsRow(icon: DrawableResource, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .handCursor()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(Res.drawable.arrow_back),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).rotate(180f),
        )
    }
}
