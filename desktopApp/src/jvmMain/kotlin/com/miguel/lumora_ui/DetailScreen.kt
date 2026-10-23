package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.add_to_library
import com.lagradost.cloudstream4.generated.resources.arrow_back
import com.lagradost.cloudstream4.generated.resources.back
import com.lagradost.cloudstream4.generated.resources.bookmark
import com.lagradost.cloudstream4.generated.resources.bookmark_border
import com.lagradost.cloudstream4.generated.resources.cast
import com.lagradost.cloudstream4.generated.resources.episode_n
import com.lagradost.cloudstream4.generated.resources.episodes
import com.lagradost.cloudstream4.generated.resources.info
import com.lagradost.cloudstream4.generated.resources.minutes_n
import com.lagradost.cloudstream4.generated.resources.no_providers
import com.lagradost.cloudstream4.generated.resources.play
import com.lagradost.cloudstream4.generated.resources.play_arrow
import com.lagradost.cloudstream4.generated.resources.recommendations
import com.lagradost.cloudstream4.generated.resources.remove_from_library
import com.lagradost.cloudstream4.generated.resources.season_n
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val DetailMaxWidth = 1040.dp

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
            val loaded = SourceSelection.forItem(item).load(item.url)
            if (loaded != null) LoadState.Ready(loaded) else LoadState.Error("—")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    when (val current = state) {
        is LoadState.Loading -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = item.name, onBack = onBack)
            DetailSkeleton()
        }
        is LoadState.Error -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = item.name, onBack = onBack)
            CenteredError(current.message) { reload++ }
        }
        is LoadState.Ready -> Box(Modifier.fillMaxSize()) {
            DetailContent(item, current.value, onOpen, onPlay)
            // Floating back button over the backdrop
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(18.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .handCursor(),
            ) {
                Icon(
                    painterResource(Res.drawable.arrow_back),
                    contentDescription = stringResource(Res.string.back),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)).shimmer())
        Box(Modifier.width(320.dp).height(26.dp).clip(RoundedCornerShape(8.dp)).shimmer())
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
    }
}

/** Centers a lazy item and keeps it at a readable width. */
@Composable
private fun Constrained(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = DetailMaxWidth).fillMaxWidth(), content = content)
    }
}

@Composable
private fun DetailContent(
    item: SearchResponse,
    load: LoadResponse,
    onOpen: (SearchResponse) -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val api = SourceSelection.forItem(item)
    val fromPlugin = api !== tmdb
    val episodes: List<Episode> = when (load) {
        is TvSeriesLoadResponse -> load.episodes
        is AnimeLoadResponse -> load.episodes.values.flatten()
        else -> emptyList()
    }
    val movieData: String? = when (load) {
        is MovieLoadResponse -> load.dataUrl
        is LiveStreamLoadResponse -> load.dataUrl
        else -> null
    }
    val bySeason = episodes.groupBy { it.season ?: 1 }.toSortedMap()
    var selectedSeason by remember(load) { mutableIntStateOf(bySeason.keys.firstOrNull() ?: 1) }
    val isMovie = movieData != null
    // Titles from TMDB are searched in the installed plugins; titles from a plugin play directly
    val canPlay = fromPlugin || !DesktopProviders.isEmpty
    val saved = LibraryStore.contains(item.url)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { DetailHero(item, load, api, isMovie, movieData, canPlay, saved, fromPlugin, onPlay) }

        if ((isMovie || episodes.isNotEmpty()) && !canPlay) {
            item { Constrained { NoProvidersNote() } }
        }

        val tags = load.tags.orEmpty()
        if (tags.isNotEmpty()) {
            item {
                Constrained {
                    ScrollableRow { items(tags) { tag -> Pill(tag) } }
                }
            }
        }

        load.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            item {
                Constrained {
                    Text(
                        plot,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                    )
                }
            }
        }

        val cast = load.actors.orEmpty()
        if (cast.isNotEmpty()) {
            item {
                Constrained {
                    SectionTitle(stringResource(Res.string.cast))
                    Spacer(Modifier.height(8.dp))
                    ScrollableRow(spacing = 16.dp) {
                        items(cast) { data ->
                            Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                PosterImage(
                                    url = data.actor.image,
                                    title = data.actor.name,
                                    modifier = Modifier.size(80.dp).clip(CircleShape),
                                    letterSize = 28,
                                )
                                Text(
                                    data.actor.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (episodes.isNotEmpty()) {
            item {
                Constrained {
                    SectionTitle(stringResource(Res.string.episodes))
                    if (bySeason.size > 1) {
                        ScrollableRow(Modifier.padding(vertical = 8.dp), spacing = 8.dp) {
                            items(bySeason.keys.toList()) { season ->
                                FilterChip(
                                    selected = season == selectedSeason,
                                    onClick = { selectedSeason = season },
                                    label = { Text(stringResource(Res.string.season_n, season)) },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.handCursor(),
                                )
                            }
                        }
                    }
                }
            }
            items(bySeason[selectedSeason].orEmpty()) { ep ->
                Constrained {
                    EpisodeRow(ep, enabled = canPlay) {
                        onPlay(
                            PlayRequest(
                                "${load.name} · ${ep.name ?: ""}", ep.season, ep.episode,
                                api = api.takeIf { fromPlugin }, data = ep.data.takeIf { fromPlugin },
                            )
                        )
                    }
                }
            }
        }

        val related = load.recommendations.orEmpty()
        if (related.isNotEmpty()) {
            item {
                Constrained {
                    SectionTitle(stringResource(Res.string.recommendations))
                    Spacer(Modifier.height(8.dp))
                    ScrollableRow {
                        items(related) { PosterCard(it, { onOpen(it) }, Modifier.width(PosterWidth)) }
                    }
                }
            }
        }
    }
}

/** Backdrop with the poster, title, facts and the main buttons laid over it. */
@Composable
private fun DetailHero(
    item: SearchResponse,
    load: LoadResponse,
    api: com.lagradost.cloudstream3.MainAPI,
    isMovie: Boolean,
    movieData: String?,
    canPlay: Boolean,
    saved: Boolean,
    fromPlugin: Boolean,
    onPlay: (PlayRequest) -> Unit,
) {
    val minutesLabel = load.duration?.let { stringResource(Res.string.minutes_n, it) }
    val scoreLabel = runCatching { load.score?.toStringNull(0.1, 10) }.getOrNull()
    val facts = listOfNotNull(load.year?.toString(), minutesLabel, load.contentRating)
    val backdrop = load.backgroundPosterUrl
    val background = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxWidth().height(400.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
        // Real backdrops are shown sharp; when only the poster exists it is blurred so it looks intentional
        (backdrop ?: load.posterUrl)?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (backdrop == null) Modifier.blur(34.dp) else Modifier),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.25f),
                    0.45f to Color.Black.copy(alpha = 0.35f),
                    1f to background,
                )
            )
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.widthIn(max = DetailMaxWidth).fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                PosterImage(
                    load.posterUrl, load.name,
                    Modifier
                        .width(170.dp)
                        .aspectRatio(2f / 3f)
                        .shadow(18.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)),
                    letterSize = 56,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        load.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (scoreLabel != null || facts.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (scoreLabel != null) ScoreBadge(scoreLabel)
                            facts.forEach { Pill(it) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isMovie && canPlay) {
                            PillButton(
                                stringResource(Res.string.play),
                                onClick = {
                                    onPlay(PlayRequest(load.name, api = api.takeIf { fromPlugin }, data = movieData.takeIf { fromPlugin }))
                                },
                                filled = true,
                                icon = Res.drawable.play_arrow,
                            )
                        }
                        PillButton(
                            stringResource(if (saved) Res.string.remove_from_library else Res.string.add_to_library),
                            onClick = { LibraryStore.toggle(item) },
                            icon = if (saved) Res.drawable.bookmark else Res.drawable.bookmark_border,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (enabled) Modifier.clickable(onClick = onClick).handCursor() else Modifier)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
            PosterImage(ep.posterUrl, ep.name ?: "", Modifier.fillMaxSize(), letterSize = 26)
            if (enabled) {
                Box(
                    Modifier.align(Alignment.Center).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.play_arrow), null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val number = ep.episode?.let { stringResource(Res.string.episode_n, it) }
            Text(
                listOfNotNull(number, ep.name).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
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
    Row(
        Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.info), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            stringResource(Res.string.no_providers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
