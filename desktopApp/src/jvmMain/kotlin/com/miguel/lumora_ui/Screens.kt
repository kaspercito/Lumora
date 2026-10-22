package com.lagradost.cloudstream4

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.lagradost.cloudstream3.MainPageRequest
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * First built-in source for Windows: the TMDB meta provider that ships in `:library`. Playback
 * sources come from the plugins installed in Settings > Sources.
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
        } catch (e: Throwable) {
            LoadState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(Res.string.title_home))
        when (val current = state) {
            is LoadState.Loading -> CenteredLoading()
            is LoadState.Error -> CenteredError(current.message) { reload++ }
            is LoadState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(current.value) { row ->
                    Column {
                        SectionTitle(row.name)
                        ScrollableRow {
                            items(row.list) { PosterCard(it, { onOpen(it) }, Modifier.width(128.dp)) }
                        }
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
        } catch (e: Throwable) {
            LoadState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(Res.string.title_search))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(painterResource(Res.drawable.nav_search), contentDescription = null) },
            placeholder = { Text(stringResource(Res.string.search_hint)) },
        )
        when (val current = state) {
            null -> EmptyState(Res.drawable.nav_search, stringResource(Res.string.search_prompt))
            is LoadState.Loading -> CenteredLoading()
            is LoadState.Error -> CenteredError(current.message) { query = query.trim() + " " }
            is LoadState.Ready -> if (current.value.isEmpty()) {
                EmptyState(Res.drawable.nav_search, stringResource(Res.string.no_results_found))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(128.dp),
                    contentPadding = PaddingValues(ScreenPadding),
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
