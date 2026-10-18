package app.lart.mobile.data

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Talks the Stremio addon protocol to any installed addon. */
object AddonClient {
    /** Used before sign-in, and kept as the metadata source if an account has none. */
    val defaults = listOf(
        "https://v3-cinemeta.strem.io/manifest.json",
        "https://opensubtitles-v3.strem.io/manifest.json",
    )

    suspend fun manifest(transportUrl: String): Addon {
        val manifest = Net.json.decodeFromString(Manifest.serializer(), Net.get(transportUrl))
        return Addon(transportUrl, manifest)
    }

    private fun path(addon: Addon, resource: String, type: String, id: String, extra: Map<String, String> = emptyMap()): String {
        val extras = if (extra.isEmpty()) "" else "/" + extra.entries.joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }
        return "${addon.baseUrl}/$resource/$type/${Uri.encode(id)}$extras.json"
    }

    private suspend fun fetch(url: String): JsonObject = Net.json.parseToJsonElement(Net.get(url)).jsonObject

    suspend fun catalog(addon: Addon, catalog: CatalogDef, extra: Map<String, String> = emptyMap()): List<Meta> {
        val body = fetch(path(addon, "catalog", catalog.type, catalog.id, extra))
        return body["metas"]?.jsonArray?.mapNotNull {
            runCatching { Net.json.decodeFromJsonElement(Meta.serializer(), it) }.getOrNull()
        } ?: emptyList()
    }

    /** Full metadata from the first addon that has it. */
    suspend fun meta(addons: List<Addon>, type: String, id: String): Meta? {
        for (addon in addons.filter { it.manifest.supports("meta", type, id) }) {
            val meta = runCatching {
                fetch(path(addon, "meta", type, id))["meta"]?.let { Net.json.decodeFromJsonElement(Meta.serializer(), it) }
            }.getOrNull()
            if (meta != null) return meta
        }
        return null
    }

    data class AddonStreams(val addon: Addon, val streams: List<Stream>, val error: String? = null)

    /** Streams from every addon that offers them, in parallel; a slow addon times out alone. */
    suspend fun streams(addons: List<Addon>, type: String, id: String): List<AddonStreams> = coroutineScope {
        addons.filter { it.manifest.supports("stream", type, id) }.map { addon ->
            async {
                val result = withTimeoutOrNull(20_000) {
                    runCatching {
                        fetch(path(addon, "stream", type, id))["streams"]?.jsonArray?.mapNotNull {
                            runCatching { Net.json.decodeFromJsonElement(Stream.serializer(), it) }.getOrNull()
                        } ?: emptyList()
                    }
                }
                when {
                    result == null -> AddonStreams(addon, emptyList(), "Timed out")
                    result.isFailure -> AddonStreams(addon, emptyList(), result.exceptionOrNull()?.message)
                    else -> AddonStreams(addon, result.getOrThrow())
                }
            }
        }.awaitAll()
    }

    suspend fun subtitles(addons: List<Addon>, type: String, id: String): List<Subtitle> = coroutineScope {
        addons.filter { it.manifest.supports("subtitles", type, id) }.map { addon ->
            async {
                withTimeoutOrNull(15_000) {
                    runCatching {
                        fetch(path(addon, "subtitles", type, id))["subtitles"]?.jsonArray?.mapNotNull {
                            runCatching { Net.json.decodeFromJsonElement(Subtitle.serializer(), it) }.getOrNull()
                        } ?: emptyList()
                    }.getOrNull()
                } ?: emptyList()
            }
        }.awaitAll().flatten()
    }
}
