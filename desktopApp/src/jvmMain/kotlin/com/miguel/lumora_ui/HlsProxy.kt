package com.lagradost.cloudstream4

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Mini-proxy local (127.0.0.1) para VLC. VLC solo sabe enviar Referer y User-Agent, pero muchas CDN
 * piden más cabeceras (Origin, etc.). Este proxy descarga cada URL con TODAS las cabeceras del enlace
 * y reescribe las listas .m3u8 para que los segmentos también pasen por aquí.
 */
class HlsProxy(private val headers: Map<String, String>) : AutoCloseable {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    val port: Int get() = server.address.port

    init {
        server.executor = Executors.newCachedThreadPool { r -> Thread(r, "hls-proxy").apply { isDaemon = true } }
        server.createContext("/") { ex ->
            try {
                handle(ex)
            } catch (e: Throwable) {
                println("[Lumora] proxy error: ${e::class.simpleName}: ${e.message}")
                runCatching { ex.sendResponseHeaders(502, -1) }
            } finally {
                ex.close()
            }
        }
        server.start()
    }

    fun proxied(url: String): String = "http://127.0.0.1:$port/p?u=" + URLEncoder.encode(url, "UTF-8")

    private fun handle(ex: HttpExchange) {
        val raw = ex.requestURI.rawQuery.orEmpty().substringAfter("u=", "")
        val target = URLDecoder.decode(raw, "UTF-8")
        val builder = Request.Builder().url(target)
        headers.forEach { (k, v) -> builder.header(k, v) }
        ex.requestHeaders.getFirst("Range")?.let { builder.header("Range", it) }

        client.newCall(builder.build()).execute().use { resp ->
            val contentType = resp.header("Content-Type").orEmpty()
            println("[Lumora] proxy ${resp.code} $contentType ${target.take(120)}")
            val body = resp.body
            val path = target.substringBefore('?')
            val maybePlaylist = contentType.contains("mpegurl", true) ||
                path.endsWith(".m3u8", true) || path.endsWith(".txt", true)

            if (resp.isSuccessful && maybePlaylist) {
                val text = body.string()
                if (text.trimStart().startsWith("#EXTM3U")) {
                    val out = rewrite(keepBestVariant(text), resp.request.url.toString()).toByteArray(Charsets.UTF_8)
                    ex.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
                    ex.sendResponseHeaders(200, out.size.toLong())
                    ex.responseBody.write(out)
                } else {
                    println("[Lumora] proxy: la respuesta NO es una lista HLS: ${text.take(160)}")
                    val out = text.toByteArray(Charsets.UTF_8)
                    ex.sendResponseHeaders(200, out.size.toLong())
                    ex.responseBody.write(out)
                }
                return
            }

            // Algunas CDN disfrazan los segmentos de video como imagen (PNG/JPG falso delante del MPEG-TS).
            // VLC no los entiende: se recorta lo que haya antes del primer paquete TS válido.
            if (resp.isSuccessful && contentType.startsWith("image/", true) && ex.requestHeaders.getFirst("Range") == null) {
                val data = stripFakeImageHeader(body.bytes())
                ex.responseHeaders.add("Content-Type", "video/mp2t")
                ex.sendResponseHeaders(200, data.size.toLong())
                ex.responseBody.write(data)
                return
            }

            if (contentType.isNotBlank()) ex.responseHeaders.add("Content-Type", contentType)
            resp.header("Content-Range")?.let { ex.responseHeaders.add("Content-Range", it) }
            resp.header("Accept-Ranges")?.let { ex.responseHeaders.add("Accept-Ranges", it) }
            val length = body.contentLength()
            ex.sendResponseHeaders(resp.code, if (length >= 0) length else 0)
            body.byteStream().copyTo(ex.responseBody)
        }
    }

    private fun rewrite(text: String, base: String): String {
        val uriAttr = Regex("URI=\"([^\"]+)\"")
        return text.lineSequence().joinToString("\n") { line ->
            val t = line.trim()
            when {
                t.isEmpty() -> line
                t.startsWith("#") -> uriAttr.replace(line) { m ->
                    "URI=\"" + proxied(resolve(base, m.groupValues[1])) + "\""
                }
                else -> proxied(resolve(base, t))
            }
        }
    }

    /**
     * Si es una lista maestra con varias calidades, deja solo la mayor (por altura y, si no hay,
     * por ancho de banda) para que VLC reproduzca siempre la mejor y no se quede en la más baja.
     */
    private fun keepBestVariant(text: String): String {
        val lines = text.lines()
        val height = Regex("RESOLUTION=\\d+x(\\d+)")
        val bandwidth = Regex("[:,]BANDWIDTH=(\\d+)")
        val variants = lines.withIndex()
            .filter { it.value.startsWith("#EXT-X-STREAM-INF") }
            .map { (i, l) ->
                val h = height.find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val bw = bandwidth.find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                Triple(i, h * 1_000_000_000L + bw, "${h}p/${bw / 1000}kbps")
            }
        if (variants.isEmpty()) return text
        println("[Lumora] proxy: calidades en la lista: ${variants.joinToString { it.third }}")
        if (variants.size == 1) return text
        val best = variants.maxBy { it.second }
        println("[Lumora] proxy: se usa la mejor: ${best.third}")
        val drop = variants.filter { it !== best }.flatMap { listOf(it.first, it.first + 1) }.toSet()
        return lines.filterIndexed { i, _ -> i !in drop }.joinToString("\n")
    }

    /** Busca tres paquetes MPEG-TS seguidos (byte 0x47 cada 188 bytes) y descarta todo lo anterior. */
    private fun stripFakeImageHeader(bytes: ByteArray): ByteArray {
        val limit = minOf(bytes.size - 376, 32_768)
        for (i in 0 until limit) {
            if (bytes[i] == 0x47.toByte() && bytes[i + 188] == 0x47.toByte() && bytes[i + 376] == 0x47.toByte()) {
                return if (i == 0) bytes else bytes.copyOfRange(i, bytes.size)
            }
        }
        return bytes
    }

    private fun resolve(base: String, ref: String): String =
        base.toHttpUrlOrNull()?.resolve(ref)?.toString() ?: ref

    override fun close() {
        runCatching { server.stop(0) }
        runCatching { client.dispatcher.executorService.shutdown() }
    }
}

/** Cabeceras completas con las que se debe pedir un enlace (las del extractor + Referer/Origin/UA). */
internal fun playbackHeaders(link: com.lagradost.cloudstream3.utils.ExtractorLink): Map<String, String> {
    val result = linkedMapOf<String, String>()
    link.headers.forEach { (k, v) -> result[k] = v }
    fun has(name: String) = result.keys.any { it.equals(name, true) }
    if (link.referer.isNotBlank() && !has("Referer")) result["Referer"] = link.referer
    if (!has("Origin")) {
        val origin = (link.referer.takeIf { it.isNotBlank() } ?: link.url).toHttpUrlOrNull()
            ?.let { "${it.scheme}://${it.host}" }
        if (origin != null) result["Origin"] = origin
    }
    if (!has("User-Agent")) {
        result["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
    }
    return result
}
