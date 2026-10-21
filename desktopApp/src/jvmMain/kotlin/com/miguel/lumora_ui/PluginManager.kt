package com.lagradost.cloudstream4

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.zip.ZipFile

data class RemotePlugin(
    val name: String,
    val internalName: String,
    val url: String,
    val language: String?,
    val tvTypes: List<String>,
    val version: Int,
)

/**
 * Downloads CloudStream repositories (repo.json -> plugins.json -> .cs3), converts the Android DEX
 * inside each .cs3 to JVM bytecode and loads it. This is best effort: a plugin that depends on
 * Android APIs that are not available on Windows will fail to load, and the error is shown in the
 * Sources screen instead of crashing the app.
 */
object PluginManager {
    const val DEFAULT_REPO = "https://raw.githubusercontent.com/redblacker8/storm-ext/refs/heads/builds/repo.json"

    val available = mutableStateListOf<RemotePlugin>()

    /** internalName -> "" when loaded OK, otherwise the error message. Absent = not installed. */
    val installed = mutableStateMapOf<String, String>()

    /** Providers coming from loaded plugins (TMDB is not in here). */
    val apis = mutableStateListOf<MainAPI>()

    val status = mutableStateOf("")
    val busy = mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
    private val mapper = ObjectMapper()

    private val baseDir: File by lazy {
        val appData = System.getenv("APPDATA")
        File(if (appData != null) File(appData, "Lumora") else File(System.getProperty("user.home"), ".lumora"))
    }
    private val pluginsDir get() = File(baseDir, "plugins").apply { mkdirs() }
    private val cacheDir get() = File(baseDir, "plugin-cache").apply { mkdirs() }
    private val repoFile get() = File(baseDir, "repo.txt")

    var repoUrl: String
        get() = runCatching { repoFile.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() } ?: DEFAULT_REPO
        set(value) {
            baseDir.mkdirs()
            repoFile.writeText(value.trim())
        }

    private var started = false

    /** Loads every plugin already on disk. Safe to call repeatedly. */
    fun loadInstalledAsync() {
        if (started) return
        started = true
        scope.launch {
            pluginsDir.listFiles { f -> f.extension == "cs3" }?.forEach { loadPlugin(it) }
        }
    }

    fun refreshAsync() {
        scope.launch {
            busy.value = true
            status.value = ""
            try {
                val found = mutableListOf<RemotePlugin>()
                val repo = mapper.readTree(download(repoUrl))
                repo["pluginLists"]?.forEach { listUrl ->
                    mapper.readTree(download(listUrl.asText())).forEach { node ->
                        // status 0 = marked as broken/down by the repo author
                        if (node["status"]?.asInt(1) == 0) return@forEach
                        found.add(
                            RemotePlugin(
                                name = node["name"]?.asText() ?: return@forEach,
                                internalName = node["internalName"]?.asText() ?: return@forEach,
                                url = node["url"]?.asText() ?: return@forEach,
                                language = node["language"]?.asText(),
                                tvTypes = node["tvTypes"]?.map { it.asText() }.orEmpty(),
                                version = node["version"]?.asInt(0) ?: 0,
                            )
                        )
                    }
                }
                available.clear()
                available.addAll(found.sortedBy { it.name.lowercase() })
            } catch (e: Exception) {
                status.value = e.message ?: e.toString()
            } finally {
                busy.value = false
            }
        }
    }

    fun install(plugin: RemotePlugin) {
        scope.launch {
            installed[plugin.internalName] = "…"
            try {
                val file = File(pluginsDir, "${plugin.internalName}.cs3")
                file.writeBytes(download(plugin.url))
                loadPlugin(file)
            } catch (e: Exception) {
                installed[plugin.internalName] = e.message ?: e.toString()
            }
        }
    }

    fun uninstall(plugin: RemotePlugin) {
        val file = File(pluginsDir, "${plugin.internalName}.cs3")
        apis.removeAll { it.sourcePlugin == file.path }
        APIHolder.allProviders.removeAll { it.sourcePlugin == file.path }
        file.delete()
        installed.remove(plugin.internalName)
    }

    private fun download(url: String): ByteArray {
        val response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()} · $url")
        return response.body()
    }

    private fun loadPlugin(file: File) {
        val key = file.nameWithoutExtension
        try {
            val (className, dex) = ZipFile(file).use { zip ->
                val manifest = mapper.readTree(zip.getInputStream(zip.getEntry("manifest.json")).readBytes())
                val dexEntry = zip.getEntry("classes.dex") ?: error("classes.dex")
                manifest["pluginClassName"].asText() to zip.getInputStream(dexEntry).readBytes()
            }
            // Converted jars are cached by name+size so each plugin is only translated once
            val jar = File(cacheDir, "$key-${file.length()}.jar")
            if (!jar.exists()) {
                val dexFile = File(cacheDir, "$key.dex").apply { writeBytes(dex) }
                dexToJar(dexFile, jar)
                dexFile.delete()
            }
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), PluginManager::class.java.classLoader)
            val plugin = loader.loadClass(className).getDeclaredConstructor().newInstance() as BasePlugin
            plugin.filename = file.path
            plugin.load()
            val registered = APIHolder.allProviders.filter { it.sourcePlugin == file.path }
            apis.removeAll { it.sourcePlugin == file.path }
            apis.addAll(registered)
            installed[key] = ""
        } catch (t: Throwable) {
            // NoClassDefFoundError etc. are Errors, not Exceptions: catch everything per plugin
            val cause = generateSequence(t) { it.cause }.last()
            installed[key] = "${cause::class.simpleName}: ${cause.message}"
        }
    }

    /**
     * Calls dex-translator through reflection so a different version of its API can never stop
     * the whole app from compiling; if the call shape is wrong, the error is shown per plugin.
     */
    private fun dexToJar(dex: File, jar: File) {
        val cls = Class.forName("de.femtopedia.dex2jar.Dex2jar")
        val builder = cls.getMethod("from", File::class.java).invoke(null, dex)
        val to = cls.methods.firstOrNull {
            it.name == "to" && it.parameterTypes.size == 1 &&
                (it.parameterTypes[0] == Path::class.java || it.parameterTypes[0] == File::class.java)
        } ?: error("Dex2jar.to(...) not found")
        val arg: Any = if (to.parameterTypes[0] == Path::class.java) jar.toPath() else jar
        to.invoke(builder, arg)
    }
}
