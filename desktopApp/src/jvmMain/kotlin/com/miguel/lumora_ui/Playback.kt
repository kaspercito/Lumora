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
import com.lagradost.cloudstream4.compose.WhiteButton
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
data class PlayRequest(val title: String, val season: Int? = null, val episode: Int? = null)

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
            DesktopProviders.installed.forEach { api ->
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

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = request.title, onBack = onClose)
        when (val current = links) {
            is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.resolving_links), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                        val quality = if (l.quality > 0) " · ${l.quality}p" else ""
                        Text(
                            text = "${l.name}$quality",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { chosen = l }
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** VLC is a native library; vlcj only wraps it, so a VLC 64-bit install is required. */
private val vlcAvailable: Boolean by lazy { runCatching { NativeDiscovery().discover() }.getOrDefault(false) }

@Composable
private fun PlayerScreen(title: String, link: ExtractorLink, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = title, onBack = onBack)
        if (!vlcAvailable) {
            EmptyState(Res.drawable.nav_downloads, stringResource(Res.string.vlc_missing))
            return@Column
        }

        val component = remember { EmbeddedMediaPlayerComponent() }
        var paused by remember { mutableStateOf(false) }
        var position by remember { mutableFloatStateOf(0f) }
        var seeking by remember { mutableStateOf(false) }

        DisposableEffect(component) { onDispose { component.release() } }

        LaunchedEffect(component, link) {
            // VLC only understands these two headers as options
            val options = buildList {
                if (link.referer.isNotBlank()) add(":http-referrer=${link.referer}")
                link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }
                    ?.let { add(":http-user-agent=${it.value}") }
            }
            component.mediaPlayer().media().play(link.url, *options.toTypedArray())
        }
        LaunchedEffect(component) {
            while (true) {
                if (!seeking) position = component.mediaPlayer().status().position().coerceIn(0f, 1f)
                delay(500)
            }
        }

        SwingPanel(factory = { component }, modifier = Modifier.weight(1f).fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhiteButton(
                text = stringResource(if (paused) Res.string.resume else Res.string.pause),
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
        }
    }
}
