package app.lart.mobile.data

/** A stream ready to hand to the player: where it came from and the URL to open. */
data class Ready(val stream: Stream, val url: String)

data class RankedStream(val addon: Addon, val stream: Stream)

/** Finding, ranking and resolving streams, shared by the streams list and the player. */
object Playback {
    suspend fun ranked(addons: List<Addon>, type: String, id: String, debrid: DebridAccount?): List<RankedStream> {
        val all = AddonClient.streams(addons, type, id).flatMap { group -> group.streams.map { RankedStream(group.addon, it) } }
        val order = Ranking.rank(all.map { it.stream }) { it.playable(debrid != null) }
        // Rank by stream, then put each back with its addon (streams can repeat across addons).
        val pool = all.toMutableList()
        return order.mapNotNull { stream -> pool.firstOrNull { it.stream === stream }?.also { pool.remove(it) } }
    }

    suspend fun resolve(stream: Stream, debrid: DebridAccount?): Ready = when {
        stream.direct -> Ready(stream, stream.url!!)
        stream.infoHash != null && debrid != null -> Ready(stream, Debrid.resolve(debrid, stream))
        stream.infoHash != null -> error("Torrents need a debrid account. Add one in Settings.")
        else -> error("Lart can't play this kind of stream yet.")
    }

    /**
     * The best stream that actually opens: tries the top few playable ones in rank
     * order, so one uncached torrent doesn't stop auto-play.
     */
    suspend fun best(ranked: List<RankedStream>, debrid: DebridAccount?, attempts: Int = 3): Ready? {
        for (candidate in ranked.filter { it.stream.playable(debrid != null) }.take(attempts)) {
            runCatching { resolve(candidate.stream, debrid) }.getOrNull()?.let { return it }
        }
        return null
    }
}
