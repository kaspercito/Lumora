package com.lagradost.cloudstream4

import com.lagradost.cloudstream3.MainAPI

/**
 * Fuentes de video de Lumora para Windows: los plugins instalados desde Ajustes > Fuentes
 * (ver [PluginManager]). Tras instalar uno, aparece aquí automáticamente.
 */
object DesktopProviders {
    val installed: List<MainAPI> get() = PluginManager.apis.toList()

    val isEmpty get() = PluginManager.apis.isEmpty()

    /** Carga los plugins ya instalados en disco. Se puede llamar varias veces. */
    fun registerAll() = PluginManager.loadInstalledAsync()
}
