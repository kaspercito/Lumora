package com.lagradost.cloudstream4

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The "site" the user is browsing, like the provider picker of the mobile app. TMDB is always there;
 * every installed plugin adds one more.
 */
object SourceSelection {
    private var selectedName by mutableStateOf<String?>(null)

    /** Every source that can be browsed: TMDB first, then the loaded plugins. */
    val all: List<MainAPI> get() = listOf<MainAPI>(tmdb) + PluginManager.apis.toList()

    var current: MainAPI
        get() = all.firstOrNull { it.name == selectedName } ?: tmdb
        set(value) { selectedName = value.name }

    fun forItem(item: SearchResponse): MainAPI = all.firstOrNull { it.name == item.apiName } ?: tmdb
}

internal fun MainAPI.label(): String = if (this === tmdb) "TMDB" else name

/** Row of chips to pick the source. Hidden when TMDB is the only one. */
@Composable
fun SourcePicker() {
    val sources = SourceSelection.all
    if (sources.size <= 1) return
    val current = SourceSelection.current
    ScrollableRow(
        modifier = Modifier.padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        spacing = 8.dp,
    ) {
        items(sources) { api ->
            FilterChip(
                selected = api.name == current.name,
                onClick = { SourceSelection.current = api },
                label = { Text(api.label()) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.handCursor(),
            )
        }
    }
}

/** Home rows of any source. A row that fails is skipped; only if all fail is the error reported. */
internal suspend fun loadHome(api: MainAPI): List<HomePageList> = coroutineScope {
    val requests = if (api === tmdb) listOf(MainPageRequest("", "", false))
    else api.mainPage.take(10).map { MainPageRequest(it.name, it.data, it.horizontalImages) }
    var firstError: Throwable? = null
    val rows = requests.map { request ->
        async {
            try {
                withTimeout(40_000) { api.getMainPage(1, request)?.items.orEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (firstError == null) firstError = e
                emptyList()
            }
        }
    }.awaitAll().flatten().filter { it.list.isNotEmpty() }
    if (rows.isEmpty()) firstError?.let { throw it }
    rows
}

/**
 * Busca en una fuente probando todas las vías que ofrece un plugin. Antes solo se usaba
 * `search(query, 1)` y, si devolvía una lista VACÍA (no null), nunca se probaba `search(query)`;
 * muchos plugins solo implementan una de las dos, por eso salía "Sin resultados".
 */
internal suspend fun searchSource(api: MainAPI, query: String): List<SearchResponse> {
    val q = query.trim().replace(Regex("\\s+"), " ")
    // Enlace pegado (copiado del navegador): se abre directamente con el plugin, sin pasar por la búsqueda
    if (api !== tmdb && q.startsWith("http", ignoreCase = true)) {
        val loaded = try { api.load(q) } catch (e: CancellationException) { throw e } catch (_: Throwable) { null }
        return listOf(
            api.newMovieSearchResponse(loaded?.name ?: q, q, TvType.Movie, false) {
                posterUrl = loaded?.posterUrl
            }
        )
    }
    var firstError: Throwable? = null
    suspend fun attempt(block: suspend () -> List<SearchResponse>?): List<SearchResponse> =
        try {
            block().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: NotImplementedError) {
            // El plugin simplemente no implementa esta variante: no es un error real
            emptyList()
        } catch (e: Throwable) {
            System.err.println("[Lumora] búsqueda en ${api.name} falló: ${e::class.simpleName}: ${e.message}")
            if (firstError == null) firstError = e
            emptyList()
        }

    val results = attempt { api.search(q, 1)?.items }
        .ifEmpty { attempt { api.search(q) } }
        .ifEmpty { attempt { api.quickSearch(q) } }
        .ifEmpty {
            // Último intento: sin acentos y con mayúsculas/espacios normalizados
            val plain = java.text.Normalizer.normalize(q, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
            if (plain != q) attempt { api.search(plain, 1)?.items }.ifEmpty { attempt { api.search(plain) } }
            else emptyList()
        }
        .ifEmpty {
            // La ruta de búsqueda de la página puede estar bloqueada (HTTP 403) aunque el Inicio funcione:
            // se busca entre los títulos de las categorías del Inicio del plugin
            if (api !== tmdb) searchViaHome(api, q) else emptyList()
        }
    System.err.println("[Lumora] búsqueda '$q' en ${api.name}: ${results.size} resultados")
    // Si todo vino vacío y algo falló, mostrar el error real en vez de "Sin resultados"
    if (results.isEmpty()) firstError?.let { throw it }
    return results
}


private fun looseKey(s: String) =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }

/** Índice en memoria de los títulos ya vistos en el catálogo de cada plugin (se llena al buscar). */
private val catalogIndex = HashMap<String, LinkedHashMap<String, SearchResponse>>()
private val catalogComplete = HashSet<String>()
private const val CATALOG_MAX_PAGES = 24
private const val CATALOG_BATCH = 4

/**
 * Alternativa cuando la búsqueda del plugin no devuelve nada (p. ej. la página bloquea /search con 403):
 * recorre hasta [CATALOG_MAX_PAGES] páginas de cada categoría del Inicio, guarda los títulos en un índice
 * y filtra por el texto (sin importar espacios, mayúsculas ni acentos: "visionquest" encuentra "Vision Quest").
 * Se detiene en cuanto aparece una coincidencia; las búsquedas siguientes usan el índice ya guardado.
 */
private suspend fun searchViaHome(api: MainAPI, query: String): List<SearchResponse> = coroutineScope {
    val wanted = looseKey(query)
    if (wanted.isEmpty()) return@coroutineScope emptyList()
    val index = synchronized(catalogIndex) { catalogIndex.getOrPut(api.name) { LinkedHashMap() } }
    fun matches(): List<SearchResponse> =
        synchronized(index) { index.values.filter { looseKey(it.name).contains(wanted) } }

    matches().takeIf { it.isNotEmpty() }?.let {
        System.err.println("[Lumora] búsqueda alternativa en ${api.name}: ${it.size} resultados (índice)")
        return@coroutineScope it
    }

    if (synchronized(catalogComplete) { api.name !in catalogComplete }) {
        val gate = Semaphore(8)
        var stoppedEarly = false
        val finished = withTimeoutOrNull(35_000) {
            api.mainPage.take(10).map { category ->
                async {
                    var start = 1
                    while (start <= CATALOG_MAX_PAGES && !stoppedEarly) {
                        val batch = (start until start + CATALOG_BATCH).map { page ->
                            async {
                                gate.withPermit {
                                    try {
                                        api.getMainPage(page, MainPageRequest(category.name, category.data, category.horizontalImages))
                                            ?.items.orEmpty().flatMap { it.list }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Throwable) {
                                        emptyList()
                                    }
                                }
                            }
                        }.awaitAll()
                        var added = 0
                        synchronized(index) {
                            batch.flatten().forEach { if (index.putIfAbsent(it.url, it) == null) added++ }
                        }
                        // Sin títulos nuevos = se acabaron las páginas de esta categoría
                        if (added == 0) break
                        if (matches().isNotEmpty()) { stoppedEarly = true; break }
                        start += CATALOG_BATCH
                    }
                }
            }.awaitAll()
            true
        }
        if (finished == true && !stoppedEarly) synchronized(catalogComplete) { catalogComplete.add(api.name) }
    }
    val result = matches()
    System.err.println("[Lumora] búsqueda alternativa en ${api.name}: ${result.size} resultados (índice de ${synchronized(index) { index.size }} títulos)")
    result
}
