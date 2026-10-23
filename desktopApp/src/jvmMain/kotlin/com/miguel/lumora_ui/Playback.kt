package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.lagradost.cloudstream4.generated.resources.play_arrow
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.choose_source
import com.lagradost.cloudstream4.generated.resources.nav_downloads
import com.lagradost.cloudstream4.generated.resources.no_links
import com.lagradost.cloudstream4.generated.resources.pause
import com.lagradost.cloudstream4.generated.resources.resolving_links
import com.lagradost.cloudstream4.generated.resources.resume
import com.lagradost.cloudstream4.generated.resources.vlc_missing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

/** What the user asked to play. Each provider is searched by [title]; season/episode are null for movies. */
data class PlayRequest(
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    /** When set (title opened from a plugin source) links are loaded from this provider directly. */
    val api: MainAPI? = null,
    val data: String? = null,
)

private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** Finds the title in one provider and returns its playback `data`, or null when it has no match. */
private suspend fun findData(api: MainAPI, request: PlayRequest): String? {
    val query = request.title.substringBefore(" · ")
    val wanted = normalize(query)
    val results = api.search(query).orEmpty()
    val match = results.firstOrNull { normalize(it.name) == wanted }
        ?: results.firstOrNull { normalize(it.name).startsWith(wanted) || wanted.startsWith(normalize(it.name)) }
        ?: return null
    return when (val load = api.load(match.url)) {
        is MovieLoadResponse -> load.dataUrl
        is TvSeriesLoadResponse -> load.episodes.pickEpisode(request)
        is AnimeLoadResponse -> load.episodes.values.flatten().pickEpisode(request)
        else -> null
    }
}

private fun List<Episode>.pickEpisode(request: PlayRequest): String? {
    val season = request.season ?: 1
    return firstOrNull { (it.season ?: 1) == season && it.episode == request.episode }?.data
        ?: if (season <= 1) firstOrNull { it.episode == request.episode }?.data else null
}

/** Resolves links with every installed provider, then lets the user pick one and plays it. */
@Composable
fun PlaybackScreen(request: PlayRequest, onClose: () -> Unit) {
    var chosen by remember(request) { mutableStateOf<ExtractorLink?>(null) }

    val links by produceState<LoadState<List<ExtractorLink>>>(LoadState.Loading, request) {
        value = LoadState.Loading
        value = try {
            val found = mutableListOf<ExtractorLink>()
            val direct = request.api
            val directData = request.data
            if (direct != null && directData != null) {
                try {
                    withTimeoutOrNull(60_000) {
                        direct.loadLinks(directData, false, { }, { synchronized(found) { found.add(it) } })
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (found.isEmpty()) throw e
                }
            } else DesktopProviders.installed.forEach { api ->
                // A broken provider must not take the others down; also cap each one at 45 s
                try {
                    withTimeoutOrNull(45_000) {
                        val data = findData(api, request) ?: return@withTimeoutOrNull null
                        api.loadLinks(data, false, { }, { synchronized(found) { found.add(it) } })
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
            }
            LoadState.Ready(synchronized(found) { found.sortedByDescending { it.quality } })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    val link = chosen
    if (link != null) {
        PlayerScreen(request.title, link, onBack = { chosen = null })
        return
    }

    CenteredContent(maxWidth = 760.dp) {
        ScreenHeader(title = request.title, onBack = onClose)
        when (val current = links) {
            is LoadState.Loading -> CenteredLoading(stringResource(Res.string.resolving_links))
            is LoadState.Error -> CenteredError(current.message, onClose)
            is LoadState.Ready -> if (current.value.isEmpty()) {
                EmptyState(Res.drawable.nav_downloads, stringResource(Res.string.no_links))
            } else {
                SectionTitle(stringResource(Res.string.choose_source))
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = ScreenPadding, vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current.value) { l ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { chosen = l }
                                .handCursor()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(Res.drawable.play_arrow), null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Text(
                                l.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (l.quality > 0) Pill("${l.quality}p") else if (l.url.contains(".m3u8")) Pill("Auto")
                        }
                    }
                }
            }
        }
    }
}

/**
 * VLC es una librería nativa (vlcj solo la envuelve). Primero se busca el VLC portable que viaja
 * DENTRO de la app (resources/windows-x64/vlc, ver build.gradle.kts); solo si no está se cae al
 * VLC instalado en el sistema.
 */
private val bundledVlcDir: java.io.File? by lazy {
    val res = System.getProperty("compose.application.resources.dir") ?: return@lazy null
    java.io.File(res, "vlc").takeIf { java.io.File(it, "libvlc.dll").exists() }
}

private val vlcAvailable: Boolean by lazy {
    runCatching {
        bundledVlcDir?.let { dir ->
            // libvlc.dll y libvlccore.dll se cargan desde aquí; libvlc busca plugins\ junto a ellos
            System.setProperty("jna.library.path", dir.absolutePath)
            com.sun.jna.NativeLibrary.addSearchPath("libvlc", dir.absolutePath)
            com.sun.jna.NativeLibrary.addSearchPath("libvlccore", dir.absolutePath)
        }
        NativeDiscovery().discover()
    }.getOrDefault(false)
}

/** Pantalla completa de toda la app mientras se reproduce (la lee Main.kt). */
object PlayerFullscreen {
    var active by mutableStateOf(false)
}

/**
 * Reproductor incrustado en la ventana de Lumora (VLC dibuja dentro de un SwingPanel).
 * Doble clic o F = pantalla completa · Esc = salir · Espacio = pausa · ← → = ±10 s.
 */
@Composable
private fun PlayerScreen(title: String, link: ExtractorLink, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (!PlayerFullscreen.active) ScreenHeader(title = title, onBack = onBack)
        if (!vlcAvailable) {
            EmptyState(Res.drawable.nav_downloads, stringResource(Res.string.vlc_missing))
            return@Column
        }

        val component = remember { EmbeddedMediaPlayerComponent() }
        val proxy = remember(link) { runCatching { HlsProxy(playbackHeaders(link)) }.getOrNull() }
        var paused by remember { mutableStateOf(false) }
        var position by remember { mutableFloatStateOf(0f) }
        var seeking by remember { mutableStateOf(false) }
        var failed by remember(link) { mutableStateOf(false) }

        DisposableEffect(component, proxy) {
            val surface = component.videoSurfaceComponent()
            val mouse = object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) PlayerFullscreen.active = !PlayerFullscreen.active
                }
            }
            val keys = object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    val player = component.mediaPlayer()
                    when (e.keyCode) {
                        java.awt.event.KeyEvent.VK_F, java.awt.event.KeyEvent.VK_F11 ->
                            PlayerFullscreen.active = !PlayerFullscreen.active
                        java.awt.event.KeyEvent.VK_ESCAPE -> PlayerFullscreen.active = false
                        java.awt.event.KeyEvent.VK_SPACE -> {
                            paused = !paused
                            player.controls().setPause(paused)
                        }
                        java.awt.event.KeyEvent.VK_RIGHT -> player.controls().skipTime(10_000)
                        java.awt.event.KeyEvent.VK_LEFT -> player.controls().skipTime(-10_000)
                    }
                }
            }
            surface.addMouseListener(mouse)
            surface.addKeyListener(keys)
            onDispose {
                PlayerFullscreen.active = false
                surface.removeMouseListener(mouse)
                surface.removeKeyListener(keys)
                Thread {
                    runCatching { proxy?.close() }
                    runCatching { component.release() }
                }.start()
            }
        }

        LaunchedEffect(component, link) {
            // Con el proxy local VLC no necesita opciones: el proxy ya envía todas las cabeceras
            val options: List<String> = if (proxy != null) emptyList() else buildList {
                if (link.referer.isNotBlank()) add(":http-referrer=${link.referer}")
                link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }
                    ?.let { add(":http-user-agent=${it.value}") }
            }
            val playUrl = proxy?.proxied(link.url) ?: link.url
            println("[Lumora] reproduciendo: ${link.url} (referer='${link.referer}', opciones=$options)")
            component.mediaPlayer().events().addMediaPlayerEventListener(object : uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: uk.co.caprica.vlcj.player.base.MediaPlayer) {
                    println("[Lumora] VLC: playing")
                    failed = false
                }
                override fun error(mediaPlayer: uk.co.caprica.vlcj.player.base.MediaPlayer) {
                    println("[Lumora] VLC: ERROR al abrir ${link.url}")
                    failed = true
                }
            })
            component.mediaPlayer().media().play(playUrl, *options.toTypedArray())
        }
        LaunchedEffect(component) {
            while (true) {
                if (!seeking) position = component.mediaPlayer().status().position().coerceIn(0f, 1f)
                delay(500)
            }
        }

        SwingPanel(factory = { component }, modifier = Modifier.weight(1f).fillMaxWidth())
        if (failed && !PlayerFullscreen.active) {
            Text(
                "VLC no pudo abrir este enlace. Vuelve atrás y prueba otra fuente.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
            )
        }
        if (!PlayerFullscreen.active) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton(
                    text = stringResource(if (paused) Res.string.resume else Res.string.pause),
                    filled = true,
                    onClick = {
                        paused = !paused
                        component.mediaPlayer().controls().setPause(paused)
                    },
                )
                Slider(
                    value = position,
                    onValueChange = { seeking = true; position = it },
                    onValueChangeFinished = {
                        component.mediaPlayer().controls().setPosition(position)
                        seeking = false
                    },
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = "Pantalla completa",
                    filled = false,
                    onClick = { PlayerFullscreen.active = true },
                )
            }
        }
    }
}
