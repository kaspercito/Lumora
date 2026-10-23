package com.lagradost.cloudstream3.network

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Desktop stand-in for the Android `CloudflareKiller` (which solves Cloudflare challenges with a
 * WebView). Plugins only need the class to exist to load; requests go through untouched, so sites
 * protected by a Cloudflare challenge may still fail, but everything else works.
 */
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        fun parseCookieMap(cookie: String): Map<String, String> =
            cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    fun getCookieHeaders(url: String): Headers = Headers.Builder().build()

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
