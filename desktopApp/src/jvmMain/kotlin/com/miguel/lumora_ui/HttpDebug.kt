package com.lagradost.cloudstream4

import com.lagradost.cloudstream3.app

/**
 * Escribe en la consola cada petición HTTP que hacen los plugins (método, URL, código, tiempo).
 * Sirve para ver POR QUÉ una búsqueda de un plugin devuelve 0 resultados (URL pedida, 403, redirección...).
 */
internal fun installHttpLogging() {
    runCatching {
        app.baseClient = app.baseClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val start = System.currentTimeMillis()
                try {
                    val response = chain.proceed(request)
                    val url = request.url.toString()
                    // Las imágenes/posters no interesan
                    if (!url.contains(Regex("\\.(jpe?g|png|webp|gif|svg|ico|css|woff2?)(\\?|$)", RegexOption.IGNORE_CASE))) {
                        println(
                            "[Lumora] http ${response.code} ${request.method} ${url.take(160)} " +
                                "(${System.currentTimeMillis() - start} ms, ${response.header("Content-Type").orEmpty().substringBefore(';')})"
                        )
                    }
                    response
                } catch (e: Throwable) {
                    println("[Lumora] http FALLÓ ${request.method} ${request.url.toString().take(160)}: ${e::class.simpleName}: ${e.message}")
                    throw e
                }
            }
            .build()
    }.onFailure { println("[Lumora] no se pudo activar el log HTTP: ${it.message}") }
}
