package com.lagradost.cloudstream3.utils

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData

/**
 * Lumora - preparación del primer arranque.
 *
 *  Añade los repositorios de extensiones definidos en gradle.properties
 *     (lumora.defaultRepos). Se hace una sola vez, para que si alguien borra un
 *     repositorio no vuelva a aparecer.
 */
object LumoraBootstrap {
    private const val REPOS_SEEDED_KEY = "lumora_repos_seeded_v1"

    fun run(context: Context) {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            seedRepositories(prefs)
        } catch (t: Throwable) {
            // Nunca debe tumbar la app
            t.printStackTrace()
        }
    }

    private fun seedRepositories(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(REPOS_SEEDED_KEY, false)) return

        val wanted = BuildConfig.DEFAULT_REPOS
            .split(";")
            .mapNotNull { entry ->
                val parts = entry.split("|", limit = 2).map { it.trim() }
                val url = parts.lastOrNull()?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                val name = if (parts.size == 2 && parts[0].isNotBlank()) parts[0] else url
                RepositoryData(name, url)
            }

        if (wanted.isNotEmpty()) {
            val current = getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
            setKey(REPOSITORIES_KEY, (current + wanted).distinctBy { it.url }.toTypedArray())
        }
        prefs.edit { putBoolean(REPOS_SEEDED_KEY, true) }
    }
}
