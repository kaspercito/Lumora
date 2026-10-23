package com.lagradost.cloudstream4

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.databind.JsonNode
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
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Properties
import java.util.zip.ZipFile

data class RemotePlugin(
    val name: String,
    val internalName: String,
    val url: String,
    val language: String?,
    val tvTypes: List<String>,
    val version: Int,
    /** Repository this plugin came from (as typed by the user). */
    val repo: String = "",
)

/**
 * Downloads CloudStream repositories (repo.json -> plugins.json -> .cs3), converts the Android DEX
 * inside each .cs3 to JVM bytecode and loads it. This is best effort: a plugin that depends on
 * Android APIs that are not available on Windows will fail to load, and the error is shown in the
 * Sources screen instead of crashing the app.
 *
 * Repositories can be typed as: a direct `repo.json` link, a `github.com/user/repo` link (the usual
 * `builds`/`master`/`main` branches are tried), a GitHub `blob` link, a `cloudstreamrepo://` link
 * or a short code. A direct `plugins.json` list is accepted too.
 */
object PluginManager {
    const val DEFAULT_REPO = "https://raw.githubusercontent.com/redblacker8/storm-ext/refs/heads/builds/repo.json"

    private const val USER_AGENT = "Lumora/1.0 (Windows; +https://github.com/kaspercito/Lumora)"

    /** Plugins offered by the configured repositories. */
    val available = mutableStateListOf<RemotePlugin>()

    /** internalName -> "" when loaded OK, "…" while installing, otherwise the error message. Absent = not installed. */
    val installed = mutableStateMapOf<String, String>()

    /** internalName -> version of the .cs3 that is on disk (0 when unknown). Used to offer updates. */
    val installedVersions = mutableStateMapOf<String, Int>()

    /** Providers coming from loaded plugins (TMDB is not in here). */
    val apis = mutableStateListOf<MainAPI>()

    /** Repositories the user added, in order. */
    val repos = mutableStateListOf<String>()

    /** Human readable result of the last refresh; empty when everything went fine. */
    val status = mutableStateOf("")
    val busy = mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(15))
        .build()
    private val mapper = ObjectMapper()

    private val baseDir: File by lazy {
        val appData = System.getenv("APPDATA")
        if (appData != null) File(appData, "Lumora") else File(System.getProperty("user.home"), ".lumora")
    }
    private val pluginsDir get() = File(baseDir, "plugins").apply { mkdirs() }
    private val cacheDir get() = File(baseDir, "plugin-cache").apply { mkdirs() }
    private val reposFile get() = File(baseDir, "repos.txt")
    private val legacyRepoFile get() = File(baseDir, "repo.txt")
    private val versionsFile get() = File(baseDir, "plugin-versions.properties")

    init {
        runCatching {
            val saved = when {
                reposFile.exists() -> reposFile.readLines()
                legacyRepoFile.exists() -> legacyRepoFile.readLines()
                else -> listOf(DEFAULT_REPO)
            }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            repos.addAll(saved)
        }.onFailure { if (repos.isEmpty()) repos.add(DEFAULT_REPO) }
        runCatching {
            val props = Properties()
            if (versionsFile.exists()) versionsFile.inputStream().use { props.load(it) }
            props.forEach { (k, v) -> installedVersions[k.toString()] = v.toString().toIntOrNull() ?: 0 }
        }
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

    // ---- Repositories --------------------------------------------------------------------------

    /** Adds a repository and downloads its list right away. Returns false if it was already there. */
    fun addRepo(input: String): Boolean {
        val clean = input.trim()
        if (clean.isBlank() || repos.any { it.equals(clean, ignoreCase = true) }) return false
        repos.add(clean)
        saveRepos()
        refreshAsync()
        return true
    }

    fun removeRepo(repo: String) {
        repos.remove(repo)
        saveRepos()
        available.removeAll { it.repo == repo }
    }

    private fun saveRepos() {
        runCatching {
            baseDir.mkdirs()
            reposFile.writeText(repos.joinToString("\n"))
        }
    }

    /** Downloads the plugin list of every repository. A failing repository does not hide the others. */
    fun refreshAsync() {
        if (busy.value) return
        scope.launch {
            busy.value = true
            status.value = ""
            try {
                val found = LinkedHashMap<String, RemotePlugin>()
                val problems = mutableListOf<String>()
                for (repo in repos.toList()) {
                    try {
                        for (plugin in loadRepo(repo)) {
                            val previous = found[plugin.internalName]
                            if (previous == null || plugin.version > previous.version) found[plugin.internalName] = plugin
                        }
                    } catch (e: Exception) {
                        problems.add("$repo\n   ${friendly(e)}")
                    }
                }
                available.clear()
                available.addAll(found.values.sortedBy { it.name.lowercase() })
                status.value = problems.joinToString("\n\n")
            } finally {
                busy.value = false
            }
        }
    }

    /** All the URLs worth trying for what the user typed, most likely first. */
    private fun candidateUrls(input: String): List<String> {
        var u = input.trim().trim('"', '\'', '<', '>')
        if (u.startsWith("cloudstreamrepo://", ignoreCase = true)) u = u.substring("cloudstreamrepo://".length)
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) {
            // "github.com/user/repo" or "raw.githubusercontent.com/..." without scheme vs. a bare short code
            u = if (Regex("^[\\w.-]+\\.[A-Za-z]{2,}(/|$)").containsMatchIn(u)) "https://$u" else "https://cutt.ly/$u"
        }
        Regex("^https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+)/blob/(.+)$", RegexOption.IGNORE_CASE).find(u)?.let {
            val (user, repo, rest) = it.destructured
            return listOf("https://raw.githubusercontent.com/$user/$repo/$rest")
        }
        Regex("^https?://(?:www\\.)?github\\.com/([^/]+)/([^/#?]+?)(?:\\.git)?/?(?:[#?].*)?$", RegexOption.IGNORE_CASE).find(u)?.let {
            val (user, repo) = it.destructured
            return listOf("builds", "master", "main").map { branch ->
                "https://raw.githubusercontent.com/$user/$repo/$branch/repo.json"
            }
        }
        return listOf(u)
    }

    private fun loadRepo(input: String): List<RemotePlugin> {
        var lastError: Exception? = null
        for (url in candidateUrls(input)) {
            try {
                val root = mapper.readTree(text(download(url)))
                if (root.isArray) return parsePlugins(root, input)
                val lists = root["pluginLists"]
                    ?.takeIf { it.isArray && it.size() > 0 }
                    ?: error("El archivo no es un repo.json válido (falta \"pluginLists\").")
                val result = mutableListOf<RemotePlugin>()
                var failure: Exception? = null
                lists.forEach { listUrl ->
                    try {
                        result += parsePlugins(mapper.readTree(text(download(resolve(url, listUrl.asText())))), input)
                    } catch (e: Exception) {
                        if (failure == null) failure = e
                    }
                }
                if (result.isEmpty()) failure?.let { throw it }
                return result
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: error("Dirección de repositorio vacía.")
    }

    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http://", true) || ref.startsWith("https://", true)) ref
        else URI.create(base).resolve(ref).toString()

    private fun parsePlugins(node: JsonNode, repo: String): List<RemotePlugin> {
        if (!node.isArray) error("La lista de plugins no tiene el formato esperado.")
        return node.mapNotNull { n ->
            // status 0 = marked as broken/down by the repo author
            if (n["status"]?.asInt(1) == 0) return@mapNotNull null
            RemotePlugin(
                name = n["name"]?.asText() ?: return@mapNotNull null,
                internalName = n["internalName"]?.asText() ?: return@mapNotNull null,
                url = n["url"]?.asText() ?: return@mapNotNull null,
                language = n["language"]?.asText()?.takeIf { it.isNotBlank() && it != "null" },
                tvTypes = n["tvTypes"]?.map { it.asText() }.orEmpty(),
                version = n["version"]?.asInt(0) ?: 0,
                repo = repo,
            )
        }
    }

    private fun text(bytes: ByteArray) = String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")

    /** Turns network exceptions into something a person can act on. */
    private fun friendly(e: Throwable): String {
        val cause = generateSequence(e) { it.cause }.last()
        return when {
            e is HttpTimeoutException || cause is HttpTimeoutException -> "Tiempo de espera agotado. Revisa tu conexión."
            cause is java.net.UnknownHostException -> "No se encontró el servidor (${cause.message}). ¿Hay internet?"
            cause is javax.net.ssl.SSLException -> "Error de conexión segura (${cause.message})."
            cause is java.net.ConnectException -> "No se pudo conectar. Revisa tu conexión o el firewall."
            cause is com.fasterxml.jackson.core.JsonProcessingException -> "El archivo descargado no es JSON válido."
            else -> e.message ?: e.toString()
        }
    }

    // ---- Install / remove ----------------------------------------------------------------------

    fun install(plugin: RemotePlugin) {
        scope.launch {
            installed[plugin.internalName] = "…"
            try {
                val bytes = download(plugin.url)
                // A .cs3 is a zip: reject HTML error pages and other junk before they replace a good file
                require(bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                    "El archivo descargado no es un plugin .cs3 válido"
                }
                val file = File(pluginsDir, "${plugin.internalName}.cs3")
                val part = File(pluginsDir, "${plugin.internalName}.cs3.part")
                part.writeBytes(bytes)
                Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                loadPlugin(file)
                if (installed[plugin.internalName] == "") {
                    installedVersions[plugin.internalName] = plugin.version
                    saveVersions()
                }
            } catch (e: Exception) {
                installed[plugin.internalName] = friendly(e)
            }
        }
    }

    /** Removes an installed plugin that is not (or no longer) listed by any repository. */
    fun uninstallByName(internalName: String) = uninstall(
        RemotePlugin(internalName, internalName, "", null, emptyList(), 0)
    )

    fun uninstall(plugin: RemotePlugin) {
        val file = File(pluginsDir, "${plugin.internalName}.cs3")
        apis.removeAll { it.sourcePlugin == file.path }
        APIHolder.allProviders.removeAll { it.sourcePlugin == file.path }
        file.delete()
        // The loaded jar stays open on Windows until exit, so deleting it may fail: that is harmless
        cacheDir.listFiles { f -> f.name.startsWith("${plugin.internalName}-") }?.forEach { runCatching { it.delete() } }
        installed.remove(plugin.internalName)
        installedVersions.remove(plugin.internalName)
        saveVersions()
    }

    private fun saveVersions() {
        runCatching {
            val props = Properties()
            installedVersions.forEach { (k, v) -> props.setProperty(k, v.toString()) }
            baseDir.mkdirs()
            versionsFile.outputStream().use { props.store(it, "Lumora plugin versions") }
        }
    }

    private fun download(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(90))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, application/octet-stream, */*")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            val hint = if (response.statusCode() == 404) " (no existe, revisa la dirección)" else ""
            error("HTTP ${response.statusCode()}$hint · $url")
        }
        return response.body()
    }

    private fun loadPlugin(file: File) {
        val key = file.nameWithoutExtension
        try {
            val (className, dex) = ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: error("manifest.json")
                val manifest = mapper.readTree(zip.getInputStream(manifestEntry).readBytes())
                val dexEntry = zip.getEntry("classes.dex") ?: error("classes.dex")
                manifest["pluginClassName"].asText() to zip.getInputStream(dexEntry).readBytes()
            }
            // Converted jars are cached by name+size+date so each plugin is only translated once
            // "-v2": conversions made before the name repair below are discarded
            val jar = File(cacheDir, "$key-v2-${file.length()}-${file.lastModified()}.jar")
            if (!jar.exists()) {
                val dexFile = File(cacheDir, "$key.dex").apply { writeBytes(dex) }
                dexToJar(dexFile, jar)
                fixMangledCalls(jar)
                dexFile.delete()
                // Old conversions of this same plugin are no longer needed
                cacheDir.listFiles { f -> f.name.startsWith("$key-") && f != jar }?.forEach { runCatching { it.delete() } }
            }
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), PluginManager::class.java.classLoader)
            val plugin = loader.loadClass(className).getDeclaredConstructor().newInstance() as BasePlugin
            plugin.filename = file.path
            // Plugins built for Android extend `Plugin` and register their providers in load(context);
            // calling only load() (as before) left them "installed" but with no sources at all.
            if (plugin is com.lagradost.cloudstream3.plugins.Plugin) plugin.load(android.content.Context())
            else plugin.load()
            val registered = APIHolder.allProviders.filter { it.sourcePlugin == file.path }
            apis.removeAll { it.sourcePlugin == file.path }
            apis.addAll(registered)
            installed[key] = if (registered.isEmpty()) "el plugin no registró ninguna fuente" else ""
        } catch (t: Throwable) {
            // NoClassDefFoundError etc. are Errors, not Exceptions: catch everything per plugin
            val cause = generateSequence(t) { it.cause }.last()
            val msg = cause.message.orEmpty()
            installed[key] = if (cause is NoClassDefFoundError || cause is ClassNotFoundException) {
                if (msg.startsWith("android") || msg.contains("androidx"))
                    "usa funciones de Android no disponibles en Windows ($msg)"
                else "falta una clase: $msg"
            } else {
                "${cause::class.simpleName}: $msg"
            }
        }
    }

    /**
     * dex2jar turns the "-" of Kotlin's mangled names into "_" (`Result.constructor-impl` becomes
     * `constructor_impl`), so every call a plugin makes into the Kotlin stdlib for those functions
     * throws NoSuchMethodError. This rewrites such calls back to the real name, found by looking
     * at the methods the stdlib class actually has.
     */
    private fun fixMangledCalls(jar: File) {
        val cache = HashMap<String, Map<String, String>>()
        fun table(owner: String): Map<String, String> = cache.getOrPut(owner) {
            if (!owner.startsWith("kotlin/") && !owner.startsWith("kotlinx/")) return@getOrPut emptyMap()
            runCatching {
                val cls = Class.forName(owner.replace('/', '.'), false, PluginManager::class.java.classLoader)
                val map = HashMap<String, String>()
                cls.declaredMethods.map { it.name }.filter { '-' in it }.forEach { map[it.replace('-', '_')] = it }
                map
            }.getOrDefault(emptyMap())
        }

        fun rewrite(bytes: ByteArray): ByteArray = try {
            val reader = org.objectweb.asm.ClassReader(bytes)
            val writer = org.objectweb.asm.ClassWriter(0)
            reader.accept(object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, writer) {
                override fun visitMethod(
                    access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?,
                ): org.objectweb.asm.MethodVisitor? {
                    val next = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
                    return object : org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9, next) {
                        override fun visitMethodInsn(opcode: Int, owner: String, mname: String, desc: String, isInterface: Boolean) {
                            super.visitMethodInsn(opcode, owner, table(owner)[mname] ?: mname, desc, isInterface)
                        }
                    }
                }
            }, 0)
            writer.toByteArray()
        } catch (_: Throwable) {
            bytes
        }

        val tmp = File(jar.parentFile, jar.name + ".fix")
        ZipFile(jar).use { zin ->
            java.util.zip.ZipOutputStream(tmp.outputStream().buffered()).use { out ->
                for (entry in zin.entries()) {
                    val data = zin.getInputStream(entry).readBytes()
                    out.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    out.write(if (entry.name.endsWith(".class")) rewrite(data) else data)
                    out.closeEntry()
                }
            }
        }
        Files.move(tmp.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Calls dex-translator through reflection so a different version of its API can never stop
     * the whole app from compiling. The class has lived under different packages across forks, so
     * the known names are tried in order; if none exists the error lists what the jar really has.
     */
    private fun dexToJar(dex: File, jar: File) {
        val names = listOf(
            "com.googlecode.d2j.dex.Dex2jar",
            "de.femtopedia.dex2jar.Dex2jar",
            "com.googlecode.dex2jar.Dex2jar",
        )
        val cls = names.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: error("No encontré la clase Dex2jar. Candidatas en el jar: ${scanForDex2jar()}")
        val from = cls.methods.firstOrNull {
            it.name == "from" && java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size == 1 && it.parameterTypes[0] == File::class.java
        } ?: cls.methods.firstOrNull {
            it.name == "from" && java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size == 1 && it.parameterTypes[0] == Path::class.java
        } ?: error("${cls.name}.from(File|Path) no existe")
        val builder = from.invoke(null, if (from.parameterTypes[0] == File::class.java) dex else dex.toPath())
        val to = cls.methods.firstOrNull {
            it.name == "to" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Path::class.java
        } ?: cls.methods.firstOrNull {
            it.name == "to" && it.parameterTypes.size == 1 && it.parameterTypes[0] == File::class.java
        } ?: error("${cls.name}.to(Path|File) no existe")
        to.invoke(builder, if (to.parameterTypes[0] == Path::class.java) jar.toPath() else jar)
    }

    /** Diagnostic only: names of classes called Dex2jar* found in the app's jars. */
    private fun scanForDex2jar(): String = runCatching {
        System.getProperty("java.class.path").orEmpty().split(File.pathSeparator)
            .filter { it.endsWith(".jar") && File(it).exists() }
            .flatMap { path ->
                ZipFile(path).use { zip ->
                    zip.entries().asSequence().map { it.name }
                        .filter { it.endsWith(".class") && it.substringAfterLast('/').startsWith("Dex2jar") }
                        .map { it.removeSuffix(".class").replace('/', '.') }.toList()
                }
            }.joinToString().ifBlank { "ninguna" }
    }.getOrDefault("?")
}
