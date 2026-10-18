package app.lart.mobile.data

import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.Request

enum class DebridService(val label: String, val tokenPage: String) {
    RealDebrid("Real-Debrid", "https://real-debrid.com/apitoken"),
    TorBox("TorBox", "https://torbox.app/settings"),
}

data class DebridAccount(val service: DebridService, val apiKey: String)

/**
 * Turns a torrent stream into a direct HTTPS link through the user's debrid account.
 * Only files the service already has cached play; anything else is removed again
 * so it doesn't fill the account's download slots.
 */
object Debrid {
    class NotCached(service: DebridService) :
        Exception("Not cached on ${service.label}. Pick a source marked Cached.")

    private val videoExtensions = setOf("mkv", "mp4", "avi", "m4v", "mov", "webm", "ts", "m2ts", "wmv", "flv")

    /** Checks the key and returns the account's name, for Settings. */
    suspend fun verify(account: DebridAccount): String = when (account.service) {
        DebridService.RealDebrid -> {
            val user = rdGet(account, "/user")
            user.string("username") ?: user.string("email") ?: "Signed in"
        }
        DebridService.TorBox -> {
            val data = tbGet(account, "/user/me")["data"]?.jsonObject
            data?.string("email") ?: "Signed in"
        }
    }

    suspend fun resolve(account: DebridAccount, stream: Stream): String {
        val hash = stream.infoHash ?: error("Not a torrent")
        return when (account.service) {
            DebridService.RealDebrid -> realDebrid(account, hash.lowercase(), stream)
            DebridService.TorBox -> torBox(account, hash.lowercase(), stream)
        }
    }

    private fun magnet(hash: String, stream: Stream): String {
        val trackers = stream.sources.orEmpty().filter { it.startsWith("tracker:") }.take(10)
            .joinToString("") { "&tr=" + Uri.encode(it.removePrefix("tracker:")) }
        return "magnet:?xt=urn:btih:$hash$trackers"
    }

    private data class TorrentFile(val id: Int, val path: String, val bytes: Long)

    /** The file the addon meant: its index, else its filename, else the largest video. */
    private fun pickFile(files: List<TorrentFile>, stream: Stream, byIndex: (Int) -> TorrentFile?): TorrentFile? {
        val wanted = stream.behaviorHints?.filename
        stream.fileIdx?.let { idx ->
            byIndex(idx)?.let { if (wanted == null || it.path.endsWith(wanted, ignoreCase = true)) return it }
        }
        wanted?.let { name -> files.firstOrNull { it.path.endsWith(name, ignoreCase = true) }?.let { return it } }
        return files.filter { it.path.substringAfterLast('.').lowercase() in videoExtensions }.maxByOrNull { it.bytes }
            ?: files.maxByOrNull { it.bytes }
    }

    // ---- Real-Debrid: https://api.real-debrid.com/ ----

    private const val RD = "https://api.real-debrid.com/rest/1.0"

    private fun rdRequest(account: DebridAccount, path: String) =
        Request.Builder().url(RD + path).header("Authorization", "Bearer ${account.apiKey}")

    private suspend fun rdGet(account: DebridAccount, path: String): JsonObject =
        Net.json.parseToJsonElement(Net.call(rdRequest(account, path).build())).jsonObject

    private suspend fun rdPost(account: DebridAccount, path: String, form: Map<String, String>): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return Net.call(rdRequest(account, path).post(body).build())
    }

    private suspend fun realDebrid(account: DebridAccount, hash: String, stream: Stream): String {
        val added = Net.json.parseToJsonElement(rdPost(account, "/torrents/addMagnet", mapOf("magnet" to magnet(hash, stream))))
            .jsonObject
        val id = added.string("id") ?: error("Real-Debrid didn't accept the torrent")
        try {
            // Wait for the magnet to turn into a file list.
            var info = rdGet(account, "/torrents/info/$id")
            for (attempt in 0 until 20) {
                if (info.string("status") != "magnet_conversion") break
                delay(1_000)
                info = rdGet(account, "/torrents/info/$id")
            }
            val files = info["files"]?.jsonArray.orEmpty().map { it.jsonObject }.map {
                TorrentFile(
                    id = it["id"]?.jsonPrimitive?.intOrNull ?: 0,
                    path = it.string("path").orEmpty(),
                    bytes = it["bytes"]?.jsonPrimitive?.longOrNull ?: 0,
                )
            }
            // Real-Debrid numbers files from 1 in torrent order, so Stremio's fileIdx is id - 1.
            val file = pickFile(files, stream) { idx -> files.firstOrNull { it.id == idx + 1 } }
                ?: error("Real-Debrid found no files in this torrent")
            if (info.string("status") == "waiting_files_selection") {
                rdPost(account, "/torrents/selectFiles/$id", mapOf("files" to file.id.toString()))
            }
            // Cached torrents finish at once; give the status a few seconds to settle.
            for (attempt in 0 until 6) {
                info = rdGet(account, "/torrents/info/$id")
                if (info.string("status") == "downloaded") break
                delay(1_000)
            }
            if (info.string("status") != "downloaded") throw NotCached(DebridService.RealDebrid)
            val links = info["links"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
            val link = links.singleOrNull() ?: run {
                // Several files selected earlier: links follow the selected files in order.
                val selected = info["files"]?.jsonArray.orEmpty().map { it.jsonObject }
                    .filter { it["selected"]?.jsonPrimitive?.intOrNull == 1 }
                val position = selected.indexOfFirst { it["id"]?.jsonPrimitive?.intOrNull == file.id }
                links.getOrNull(position) ?: links.firstOrNull()
            } ?: error("Real-Debrid returned no link")
            val unrestricted = Net.json.parseToJsonElement(rdPost(account, "/unrestrict/link", mapOf("link" to link))).jsonObject
            return unrestricted.string("download") ?: error("Real-Debrid returned no download link")
        } catch (e: Exception) {
            if (e is NotCached) runCatching { Net.call(rdRequest(account, "/torrents/delete/$id").delete().build()) }
            throw e
        }
    }

    // ---- TorBox: https://api.torbox.app/docs ----

    private const val TB = "https://api.torbox.app/v1/api"

    private fun tbRequest(account: DebridAccount, path: String) =
        Request.Builder().url(TB + path).header("Authorization", "Bearer ${account.apiKey}")

    private suspend fun tbGet(account: DebridAccount, path: String): JsonObject =
        Net.json.parseToJsonElement(Net.call(tbRequest(account, path).build())).jsonObject

    private suspend fun torBox(account: DebridAccount, hash: String, stream: Stream): String {
        val cached = tbGet(account, "/torrents/checkcached?hash=$hash&format=list")["data"]
        if (cached !is JsonArray || cached.isEmpty()) throw NotCached(DebridService.TorBox)

        val form = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("magnet", magnet(hash, stream)).build()
        val created = Net.json.parseToJsonElement(Net.call(tbRequest(account, "/torrents/createtorrent").post(form).build()))
            .jsonObject
        val torrentId = created["data"]?.jsonObject?.get("torrent_id")?.jsonPrimitive?.contentOrNull
            ?: error(created.string("detail") ?: "TorBox didn't accept the torrent")

        var torrent: JsonObject? = null
        for (attempt in 0 until 10) {
            val current = tbGet(account, "/torrents/mylist?id=$torrentId&bypass_cache=true")["data"] as? JsonObject
            torrent = current
            val finished = current?.get("download_finished")?.jsonPrimitive?.booleanOrNull == true ||
                current?.get("download_present")?.jsonPrimitive?.booleanOrNull == true
            if (finished) break
            delay(1_000)
        }
        val files = torrent?.get("files")?.jsonArray.orEmpty().map { it.jsonObject }.map {
            TorrentFile(
                id = it["id"]?.jsonPrimitive?.intOrNull ?: 0,
                path = it.string("name") ?: it.string("short_name").orEmpty(),
                bytes = it["size"]?.jsonPrimitive?.longOrNull ?: 0,
            )
        }
        // TorBox's file ids aren't promised to follow torrent order, so the filename decides first.
        val file = pickFile(files, stream) { idx -> files.getOrNull(idx) } ?: error("TorBox found no files in this torrent")
        val link = tbGet(
            account,
            "/torrents/requestdl?token=${Uri.encode(account.apiKey)}&torrent_id=$torrentId&file_id=${file.id}",
        )
        return link["data"]?.jsonPrimitive?.contentOrNull ?: error(link.string("detail") ?: "TorBox returned no link")
    }
}
