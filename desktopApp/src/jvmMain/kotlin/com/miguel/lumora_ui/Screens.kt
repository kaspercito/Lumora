package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream4.generated.resources.category_all
import com.lagradost.cloudstream4.generated.resources.load_more
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.lagradost.cloudstream4.generated.resources.close
import com.lagradost.cloudstream4.generated.resources.extension
import com.lagradost.cloudstream4.generated.resources.featured
import com.lagradost.cloudstream4.generated.resources.play_arrow
import com.lagradost.cloudstream4.generated.resources.results_count
import com.lagradost.cloudstream4.generated.resources.view_details
import com.lagradost.cloudstream4.generated.resources.welcome_action
import com.lagradost.cloudstream4.generated.resources.welcome_body
import com.lagradost.cloudstream4.generated.resources.welcome_title
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.nav_search
import com.lagradost.cloudstream4.generated.resources.no_results_found
import com.lagradost.cloudstream4.generated.resources.search_hint
import com.lagradost.cloudstream4.generated.resources.search_prompt
import com.lagradost.cloudstream4.generated.resources.title_home
import com.lagradost.cloudstream4.generated.resources.title_search
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import com.lagradost.cloudstream4.generated.resources.nav_home
import com.lagradost.cloudstream4.generated.resources.source_no_home
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * First built-in source for Windows: the TMDB meta provider that ships in `:library`. Playback
 * sources come from the plugins installed in Settings > Sources.
 */
internal val tmdb: TmdbProvider by lazy {
    // Without this flag TmdbProvider.load() returns null (it expects a subclass to fill the page in)
    object : TmdbProvider() {
        override val useMetaLoadResponse = true
    }
}

internal sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
}

/** Keeps Home from reloading every time the user comes back from a detail page (one entry per source). */
private val homeCache = mutableMapOf<String, List<HomePageList>>()

@Composable
fun HomeScreen(onOpen: (SearchResponse) -> Unit, onAddSources: () -> Unit = {}) {
    var reload by remember { mutableIntStateOf(0) }
    val source = SourceSelection.current
    val state by produceState<LoadState<List<HomePageList>>>(LoadState.Loading, reload, source.name) {
        val cached = homeCache[source.name]
        if (cached != null && reload == 0) {
            value = LoadState.Ready(cached)
            return@produceState
        }
        value = LoadState.Loading
        value = try {
            if (source !== tmdb && !source.hasMainPage) {
                LoadState.Ready(emptyList())
            } else {
                val rows = loadHome(source)
                homeCache[source.name] = rows
                LoadState.Ready(rows)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(Res.string.title_home))
        SourcePicker()
        // Buscador en el Inicio: busca en la fuente elegida arriba
        var homeQuery by remember { mutableStateOf("") }
        HomeSearchField(homeQuery) { homeQuery = it }
        if (homeQuery.isNotBlank()) {
            SearchResultsGrid(source, homeQuery.trim(), onOpen)
            return@Column
        }
        // Like the mobile app: a source offers several categories (e.g. "Fútbol"); pick one to browse it
        val categories = if (source !== tmdb) source.mainPage else emptyList()
        var category by remember(source.name) { mutableStateOf<Int?>(null) }
        if (categories.isNotEmpty()) {
            CategoryPicker(categories.map { it.name }, category) { category = it }
        }
        val chosen = category?.let { categories.getOrNull(it) }
        if (chosen != null) {
            CategoryGrid(source, MainPageRequest(chosen.name, chosen.data, chosen.horizontalImages), onOpen)
            return@Column
        }
        when (val current = state) {
            is LoadState.Loading -> HomeSkeleton()
            is LoadState.Error -> CenteredError(current.message) { homeCache.remove(source.name); reload++ }
            is LoadState.Ready -> if (current.value.isEmpty()) {
                EmptyState(Res.drawable.nav_home, stringResource(Res.string.source_no_home))
            } else {
                val featured = current.value.firstNotNullOfOrNull { row -> row.list.firstOrNull { !it.posterUrl.isNullOrBlank() } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    if (DesktopProviders.isEmpty) item { WelcomeCard(onAddSources) }
                    if (featured != null) item { FeaturedHero(featured, onOpen) }
                    items(current.value) { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle(row.name)
                            ScrollableRow {
                                items(row.list) { PosterCard(it, { onOpen(it) }, Modifier.width(PosterWidth)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 6.dp),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        leadingIcon = { Icon(painterResource(Res.drawable.nav_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }, modifier = Modifier.handCursor()) {
                    Icon(painterResource(Res.drawable.close), contentDescription = null)
                }
            }
        },
        placeholder = { Text(stringResource(Res.string.search_hint)) },
    )
}

/** Resultados de buscar [query] en [source] (usado por el buscador del Inicio). */
@Composable
private fun SearchResultsGrid(source: MainAPI, query: String, onOpen: (SearchResponse) -> Unit) {
    val state by produceState<LoadState<List<SearchResponse>>>(LoadState.Loading, query, source.name) {
        value = LoadState.Loading
        delay(400) // debounce
        value = try {
            LoadState.Ready(withTimeout(50_000) { searchSource(source, query) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }
    when (val current = state) {
        is LoadState.Loading -> PosterGridSkeleton()
        is LoadState.Error -> CenteredError(current.message) { }
        is LoadState.Ready -> if (current.value.isEmpty()) {
            EmptyState(Res.drawable.nav_search, stringResource(Res.string.no_results_found))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(PosterWidth),
                contentPadding = PaddingValues(ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(current.value) { PosterCard(it, { onOpen(it) }) }
            }
        }
    }
}

/** "All" + one chip per category of the selected source. */
@Composable
private fun CategoryPicker(names: List<String>, selected: Int?, onSelect: (Int?) -> Unit) {
    ScrollableRow(
        modifier = Modifier.padding(bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        spacing = 8.dp,
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Res.string.category_all)) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.handCursor(),
            )
        }
        items(names.indices.toList()) { i ->
            FilterChip(
                selected = selected == i,
                onClick = { onSelect(i) },
                label = { Text(names[i]) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.handCursor(),
            )
        }
    }
}

/** One category of a source as a grid, loading more pages on demand. */
@Composable
private fun CategoryGrid(api: MainAPI, request: MainPageRequest, onOpen: (SearchResponse) -> Unit) {
    val results = remember(api.name, request.data) { mutableStateListOf<SearchResponse>() }
    var page by remember(api.name, request.data) { mutableIntStateOf(1) }
    var loading by remember(api.name, request.data) { mutableStateOf(true) }
    var error by remember(api.name, request.data) { mutableStateOf<String?>(null) }
    var hasMore by remember(api.name, request.data) { mutableStateOf(true) }
    var attempt by remember(api.name, request.data) { mutableIntStateOf(0) }

    LaunchedEffect(api.name, request.data, page, attempt) {
        loading = true
        error = null
        try {
            val items = withTimeout(40_000) { api.getMainPage(page, request)?.items.orEmpty().flatMap { it.list } }
            val known = results.map { it.url }.toSet()
            val fresh = items.filter { it.url !in known }
            results.addAll(fresh)
            hasMore = fresh.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.message ?: e::class.simpleName.orEmpty()
            hasMore = false
        }
        loading = false
    }

    when {
        results.isEmpty() && loading -> PosterGridSkeleton()
        results.isEmpty() && error != null -> CenteredError(error.orEmpty()) { attempt++ }
        results.isEmpty() -> EmptyState(Res.drawable.nav_home, stringResource(Res.string.no_results_found))
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(PosterWidth),
            contentPadding = PaddingValues(ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(results.toList()) { PosterCard(it, { onOpen(it) }) }
            if (hasMore || loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        else PillButton(stringResource(Res.string.load_more), onClick = { page++ })
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Box(
            Modifier
                .padding(horizontal = ScreenPadding, vertical = 10.dp)
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .shimmer()
        )
        PosterRowSkeleton()
    }
}

/** Big card at the top of Home: blurred poster as ambient background, sharp poster and title on top. */
@Composable
private fun FeaturedHero(item: SearchResponse, onOpen: (SearchResponse) -> Unit) {
    val score = runCatching { item.score?.toStringNull(0.1, 10) }.getOrNull()
    Box(
        Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(38.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.35f)))
            )
        )
        Row(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                item.posterUrl, item.name,
                Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        stringResource(Res.string.featured),
                        color = Color.White,
                        background = MaterialTheme.colorScheme.primary,
                    )
                    if (score != null) ScoreBadge(score)
                }
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                PillButton(
                    stringResource(Res.string.view_details),
                    onClick = { onOpen(item) },
                    filled = true,
                    icon = Res.drawable.play_arrow,
                )
            }
        }
    }
}

/** Shown on Home until the first source is installed, so it is obvious what to do next. */
@Composable
private fun WelcomeCard(onAddSources: () -> Unit) {
    Panel(Modifier.padding(horizontal = ScreenPadding).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.extension), null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.welcome_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PillButton(stringResource(Res.string.welcome_action), onAddSources, filled = true)
        }
    }
}

@Composable
fun SearchScreen(onOpen: (SearchResponse) -> Unit) {
    var query by remember { mutableStateOf("") }
    val source = SourceSelection.current
    val state by produceState<LoadState<List<SearchResponse>>?>(null, query, source.name) {
        if (query.isBlank()) {
            value = null
            return@produceState
        }
        delay(400) // debounce: produceState restarts (and cancels this) on every keystroke
        value = LoadState.Loading
        value = try {
            LoadState.Ready(withTimeout(50_000) { searchSource(source, query) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }
    val count = (state as? LoadState.Ready<List<SearchResponse>>)?.value?.size

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(Res.string.title_search),
            subtitle = count?.takeIf { it > 0 }?.let { stringResource(Res.string.results_count, it) },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            leadingIcon = { Icon(painterResource(Res.drawable.nav_search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.handCursor()) {
                        Icon(painterResource(Res.drawable.close), contentDescription = null)
                    }
                }
            },
            placeholder = { Text(stringResource(Res.string.search_hint)) },
        )
        SourcePicker()
        when (val current = state) {
            null -> EmptyState(Res.drawable.nav_search, stringResource(Res.string.search_prompt))
            is LoadState.Loading -> PosterGridSkeleton()
            is LoadState.Error -> CenteredError(current.message) { query = query.trim() + " " }
            is LoadState.Ready -> if (current.value.isEmpty()) {
                EmptyState(Res.drawable.nav_search, stringResource(Res.string.no_results_found))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(PosterWidth),
                    contentPadding = PaddingValues(ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(current.value) { PosterCard(it, { onOpen(it) }) }
                }
            }
        }
    }
}

/** Loading placeholder for poster grids (search). */
@Composable
fun PosterGridSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PosterWidth),
        contentPadding = PaddingValues(ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) {
        gridItems((0 until 12).toList()) { PosterSkeleton() }
    }
}
