package com.lagradost.cloudstream4

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.empty_library
import com.lagradost.cloudstream4.generated.resources.nav_library
import com.lagradost.cloudstream4.generated.resources.title_library
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** A saved title: just what is needed to draw its poster and reopen its detail page. */
data class SavedItem(val url: String, val name: String, val posterUrl: String?)

/**
 * Favorites, stored as a small tab-separated file (one title per line) so it survives restarts:
 * `%APPDATA%\Lumora\library.tsv` on Windows, `~/.lumora/library.tsv` elsewhere.
 */
object LibraryStore {
    val items = mutableStateListOf<SavedItem>()

    private val file: File by lazy {
        val appData = System.getenv("APPDATA")
        val dir = if (appData != null) File(appData, "Lumora") else File(System.getProperty("user.home"), ".lumora")
        File(dir, "library.tsv")
    }

    init {
        runCatching {
            if (file.exists()) {
                file.readLines().forEach { line ->
                    val p = line.split('\t')
                    if (p.size >= 2 && p[0].isNotBlank()) {
                        items.add(SavedItem(p[0], p[1], p.getOrNull(2)?.takeIf { it.isNotBlank() }))
                    }
                }
            }
        }
    }

    fun contains(url: String) = items.any { it.url == url }

    fun toggle(item: SearchResponse) {
        val existing = items.indexOfFirst { it.url == item.url }
        if (existing >= 0) items.removeAt(existing)
        else items.add(0, SavedItem(item.url, item.name, item.posterUrl))
        save()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            fun clean(s: String?) = (s ?: "").replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
            file.writeText(items.joinToString("\n") { "${clean(it.url)}\t${clean(it.name)}\t${clean(it.posterUrl)}" })
        }
    }
}

private fun SavedItem.toSearchResponse(): SearchResponse =
    tmdb.newMovieSearchResponse(name, url, TvType.Movie, false) { posterUrl = this@toSearchResponse.posterUrl }

@Composable
fun LibraryScreen(onOpen: (SearchResponse) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(Res.string.title_library))
        if (LibraryStore.items.isEmpty()) {
            EmptyState(Res.drawable.nav_library, stringResource(Res.string.empty_library))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                contentPadding = PaddingValues(ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(LibraryStore.items.toList()) { saved ->
                    val response = saved.toSearchResponse()
                    PosterCard(response, { onOpen(response) })
                }
            }
        }
    }
}
