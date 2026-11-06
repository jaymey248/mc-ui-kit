package dev.jamie.uikit.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.jamie.uikit.core.Kit
import dev.jamie.uikit.core.guard
import dev.jamie.uikit.core.log
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Persists every [SettingHost] registered as a [Store] to `config/<mod id>/<file>.json`.
 *
 * - Writes are debounced (500 ms) and atomic (temp file + move) on a background thread.
 * - Unknown keys are preserved, so downgrading/upgrading the mod never loses settings.
 * - Each file carries a `_version`; [migrations] run in order before values are applied.
 */
object Config {
    val dir: Path by lazy { FabricLoader.getInstance().configDir.resolve(Kit.modId) }
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val io = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "${Kit.displayName}-Config").apply { isDaemon = true } }
    private val stores = mutableMapOf<String, Store>()
    @Volatile private var dirty = false
    private var loaded = false

    /** This mod's config format; set it before [load] when [migrations] have steps up to it. */
    var version = 1
    /** file → (fromVersion → transform). */
    val migrations = mutableMapOf<String, MutableMap<Int, (JsonObject) -> Unit>>()

    /** A named group of hosts written to one file. Each host gets its own JSON object keyed by [id]. */
    class Store(val file: String) {
        val hosts = linkedMapOf<String, SettingHost>()
        var raw = JsonObject()
    }

    fun store(file: String): Store = stores.getOrPut(file) { Store(file) }

    fun bind(file: String, id: String, host: SettingHost) {
        val s = store(file)
        s.hosts[id] = host
        if (loaded) apply(s.raw.getAsJsonObject(id), host)
    }

    fun markDirty() {
        if (!loaded) return
        if (io.isShutdown) { saveNow(); return } // late changes during shutdown are written synchronously
        if (!dirty) {
            dirty = true
            try {
                io.schedule({ if (dirty) saveNow() }, 500, TimeUnit.MILLISECONDS)
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                saveNow()
            }
        }
    }

    fun load() {
        Files.createDirectories(dir)
        for (s in stores.values) {
            val path = dir.resolve(s.file + ".json")
            s.raw = if (Files.exists(path)) {
                try {
                    JsonParser.parseString(Files.readString(path)).asJsonObject
                } catch (t: Throwable) {
                    log.error("Corrupt config ${s.file}.json — backing up and starting fresh", t)
                    Files.copy(path, dir.resolve(s.file + ".broken.json"), StandardCopyOption.REPLACE_EXISTING)
                    JsonObject()
                }
            } else JsonObject()
            migrate(s)
            for ((id, host) in s.hosts) apply(s.raw.getAsJsonObject(id), host)
        }
        loaded = true
    }

    private fun migrate(s: Store) {
        var v = s.raw.get("_version")?.asInt ?: version
        val steps = migrations[s.file] ?: return
        while (v < version) {
            steps[v]?.invoke(s.raw)
            v++
        }
    }

    private fun apply(obj: JsonObject?, host: SettingHost) {
        if (obj == null) return
        for (setting in host.settings) {
            if (!setting.persistent) continue
            val j = obj.get(setting.key) ?: continue
            guard("config ${setting.key}") { setting.fromJson(j) }
        }
    }

    @Synchronized
    fun saveNow() {
        dirty = false
        for (s in stores.values) guard("save ${s.file}") {
            val out = s.raw.deepCopy()
            out.addProperty("_version", version)
            for ((id, host) in s.hosts) {
                val obj = out.getAsJsonObject(id) ?: JsonObject().also { out.add(id, it) }
                for (setting in host.settings) if (setting.persistent) obj.add(setting.key, setting.toJson())
            }
            s.raw = out
            val path = dir.resolve(s.file + ".json")
            val tmp = dir.resolve(s.file + ".json.tmp")
            Files.writeString(tmp, gson.toJson(out))
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    /** Whole config as one JSON string (for export / sharing). */
    fun export(): String {
        saveNow()
        val all = JsonObject()
        stores.values.forEach { all.add(it.file, it.raw) }
        return gson.toJson(all)
    }

    fun import(json: String) {
        val all = JsonParser.parseString(json).asJsonObject
        for ((file, obj) in all.entrySet()) {
            val s = stores[file] ?: continue
            s.raw = obj.asJsonObject
            for ((id, host) in s.hosts) apply(s.raw.getAsJsonObject(id), host)
        }
        saveNow()
    }

    fun shutdown() {
        if (loaded) saveNow()
        io.shutdown()
    }
}
