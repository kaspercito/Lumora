package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.add_to_library
import com.lagradost.cloudstream4.generated.resources.cast
import com.lagradost.cloudstream4.generated.resources.episode_n
import com.lagradost.cloudstream4.generated.resources.episodes
import com.lagradost.cloudstream4.generated.resources.minutes_n
import com.lagradost.cloudstream4.generated.resources.no_providers
import com.lagradost.cloudstream4.generated.resources.play
import com.lagradost.cloudstream4.generated.resources.recommendations
import com.lagradost.cloudstream4.generated.resources.remove_from_library
import com.lagradost.cloudstream4.generated.resources.season_n
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

/** Detail page of a movie or series. */
@Composable
fun DetailScreen(
    item: SearchResponse,
    onBack: () -> Unit,
    onOpen: (SearchResponse) -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<LoadState<LoadResponse>>(LoadState.Loading, item.url, reload) {
        value = LoadState.Loading
        value = try {
            val loaded = tmdb.load(item.url)
            if (loaded != null) LoadState.Ready(loaded) else LoadState.Error("—")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = item.name, onBack = onBack)
        when (val current = state) {
            is LoadState.Loading -> CenteredLoading()
            is LoadState.Error -> CenteredError(current.message) { reload++ }
            is LoadState.Ready -> DetailContent(item, current.value, onOpen, onPlay)
        }
    }
}

@Composable
private fun DetailContent(
    item: SearchResponse,
    load: LoadResponse,
    onOpen: (SearchResponse) -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val episodes: List<Episode> = (load as? TvSeriesLoadResponse)?.episodes.orEmpty()
    val bySeason = episodes.groupBy { it.season ?: 1 }.toSortedMap()
    val minutesLabel = load.duration?.let { stringResource(Res.string.minutes_n, it) }
    val scoreLabel = runCatching { load.score?.toStringNull(0.1, 10) }.getOrNull()?.let { "★ $it" }
    val meta = listOfNotNull(load.year?.toString(), minutesLabel, scoreLabel, load.contentRating)
        .joinToString("  ·  ")
    val isMovie = load is MovieLoadResponse
    val canPlay = !DesktopProviders.isEmpty
    val saved = LibraryStore.contains(item.url)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Backdrop, the same wide image the mobile detail page opens with
        (load.backgroundPosterUrl ?: load.posterUrl)?.let { backdrop ->
            item {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(horizontal = ScreenPadding)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        item {
            Row(Modifier.padding(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = load.posterUrl,
                    contentDescription = load.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(load.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isMovie && canPlay) {
                            WhiteButton(stringResource(Res.string.play)) { onPlay(PlayRequest(load.name)) }
                        }
                        BlackButton(
                            stringResource(if (saved) Res.string.remove_from_library else Res.string.add_to_library)
                        ) { LibraryStore.toggle(item) }
                    }
                }
            }
        }
        if ((isMovie || episodes.isNotEmpty()) && !canPlay) {
            item { NoProvidersNote() }
        }
        val tags = load.tags.orEmpty()
        if (tags.isNotEmpty()) {
            item {
                ScrollableRow(contentPadding = PaddingValues(horizontal = ScreenPadding)) {
                    items(tags) { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                }
            }
        }
        load.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            item {
                Text(
                    plot,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        }

        val cast = load.actors.orEmpty()
        if (cast.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.cast)) }
            item {
                ScrollableRow {
                    items(cast) { data ->
                        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = data.actor.image,
                                contentDescription = data.actor.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(36.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Text(
                                data.actor.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        if (episodes.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.episodes)) }
            bySeason.forEach { (season, list) ->
                item {
                    Text(
                        stringResource(Res.string.season_n, season),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                    )
                }
                items(list) { ep ->
                    EpisodeRow(ep, enabled = canPlay) {
                        onPlay(PlayRequest("${load.name} · ${ep.name ?: ""}", ep.season, ep.episode))
                    }
                }
            }
        }

        val related = load.recommendations.orEmpty()
        if (related.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.recommendations)) }
            item {
                ScrollableRow {
                    items(related) { PosterCard(it, { onOpen(it) }, Modifier.width(128.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = ep.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val number = ep.episode?.let { stringResource(Res.string.episode_n, it) }
            Text(
                listOfNotNull(number, ep.name).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ep.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NoProvidersNote() {
    Text(
        stringResource(Res.string.no_providers),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = ScreenPadding),
    )
}
