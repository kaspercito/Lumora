package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.error_loading
import com.lagradost.cloudstream4.generated.resources.loading
import com.lagradost.cloudstream4.generated.resources.no_results_found
import com.lagradost.cloudstream4.generated.resources.retry
import com.lagradost.cloudstream4.generated.resources.search_hint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * First built-in source for Windows. Plugins (.cs3) are Android dex and cannot be loaded on the
 * JVM, so for now the desktop app uses the TMDB meta provider that already ships in `:library`.
 */
internal val tmdb by lazy { TmdbProvider() }

internal sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
}

/** Keeps Home from reloading every time the user comes back from a detail page. */
private var homeCache: List<HomePageList>? = null

@Composable
fun HomeScreen(onOpen: (SearchResponse) -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<LoadState<List<HomePageList>>>(LoadState.Loading, reload) {
        val cached = homeCache
        if (cached != null && reload == 0) {
            value = LoadState.Ready(cached)
            return@produceState
        }
        value = LoadState.Loading
        value = try {
            val page = tmdb.getMainPage(1, MainPageRequest("", "", false))
            val rows = page.items.filter { it.list.isNotEmpty() }
            homeCache = rows
            LoadState.Ready(rows)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState.Error(e.message ?: e.toString())
        }
    }

    when (val current = state) {
        is LoadState.Loading -> CenteredLoading()
        is LoadState.Error -> CenteredError(current.message) { reload++ }
        is LoadState.Ready -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(current.value) { row ->
                Column {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(row.list) { PosterCard(it, { onOpen(it) }, Modifier.width(120.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(onOpen: (SearchResponse) -> Unit) {
    var query by remember { mutableStateOf("") }
    val state by produceState<LoadState<List<SearchResponse>>?>(null, query) {
        if (query.isBlank()) {
            value = null
            return@produceState
        }
        delay(400) // debounce: produceState restarts (and cancels this) on every keystroke
        value = LoadState.Loading
        value = try {
            LoadState.Ready(tmdb.search(query, 1)?.items.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState.Error(e.message ?: e.toString())
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.search_hint)) },
        )
        when (val current = state) {
            null -> Unit
            is LoadState.Loading -> CenteredLoading()
            is LoadState.Error -> CenteredError(current.message) { query = query.trim() + " " }
            is LoadState.Ready -> if (current.value.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.no_results_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(current.value) { PosterCard(it, { onOpen(it) }) }
                }
            }
        }
    }
}

@Composable
fun PosterCard(item: SearchResponse, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
internal fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                stringResource(Res.string.loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
internal fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(Res.string.error_loading, message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WhiteButton(stringResource(Res.string.retry), Modifier.padding(top = 16.dp), onRetry)
        }
    }
}
