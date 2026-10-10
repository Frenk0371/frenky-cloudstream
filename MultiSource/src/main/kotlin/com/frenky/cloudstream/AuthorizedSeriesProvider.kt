package com.frenky.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/** Plays only direct HTTPS media URLs listed by the authorized-media publisher.
 * Supports true seasons/episodes in CloudStream, MP4, HLS and subtitles.
 * Does not extract third-party embeds or circumvent access controls.
 */
class AuthorizedSeriesProvider : MainAPI() {
    override var mainUrl = "https://github.com/Frenk0371/frenky-cloudstream"
    override var name = "Frenky - Episodi autorizzati"
    override var lang = "it"
    override val supportedTypes = setOf(TvType.TvSeries)
    override val hasMainPage = true

    private val registry = "https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/main/config/authorized-series.json"
    private data class Video(val url: String, val type: ExtractorLinkType, val quality: Int)
    private data class Track(val label: String, val url: String)
    private data class Ep(val name: String, val season: Int, val number: Int,
                          val videos: List<Video>, val subtitles: List<Track>)
    private data class Show(val id: String, val title: String, val plot: String,
                            val episodes: List<Ep>)

    @Volatile private var cache: List<Show>? = null
    @Volatile private var cachedUntil = 0L

    private fun valid(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return uri.scheme.equals("https", true) && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) && host != "localhost" &&
            !host.endsWith(".local") && !host.endsWith(".localhost") &&
            !host.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) &&
            !host.contains(":")
    }

    private fun parse(text: String): List<Show> {
        val arr = JSONObject(text).optJSONArray("series") ?: return emptyList()
        val list = mutableListOf<Show>()
        for (i in 0 until minOf(arr.length(), 50)) {
            val s = arr.optJSONObject(i) ?: continue
            val id = s.optString("id")
            val title = s.optString("title")
            if (!id.matches(Regex("""[a-zA-Z0-9_-]{1,40}""")) || title.isBlank()) continue
            val eps = s.optJSONArray("episodes") ?: continue
            val items = mutableListOf<Ep>()
            for (j in 0 until minOf(eps.length(), 250)) {
                val e = eps.optJSONObject(j) ?: continue
                val videos = mutableListOf<Video>()
                val urls = e.optJSONArray("streams") ?: continue
                for (k in 0 until minOf(urls.length(), 10)) {
                    val stream = urls.optJSONObject(k) ?: continue
                    val url = stream.optString("url")
                    if (!valid(url)) continue
                    val type = when (stream.optString("type").lowercase(Locale.ROOT)) {
                        "mp4" -> ExtractorLinkType.VIDEO
                        "hls", "m3u8" -> ExtractorLinkType.M3U8
                        else -> continue
                    }
                    val suffix = if (type == ExtractorLinkType.M3U8) ".m3u8" else ".mp4"
                    if (!URI(url).path.lowercase(Locale.ROOT).endsWith(suffix)) continue
                    val q = stream.optInt("quality", Qualities.Unknown.value)
                    videos.add(Video(url, type, if (q in 144..2160) q else Qualities.Unknown.value))
                }
                if (videos.isEmpty()) continue
                val tracks = mutableListOf<Track>()
                val captions = e.optJSONArray("subtitles")
                if (captions != null) for (k in 0 until minOf(captions.length(), 10)) {
                    val cap = captions.optJSONObject(k) ?: continue
                    val url = cap.optString("url")
                    if (valid(url) && URI(url).path.lowercase(Locale.ROOT)
                        .let { it.endsWith(".srt") || it.endsWith(".vtt") }) {
                        tracks.add(Track(cap.optString("language", "Italiano"), url))
                    }
                }
                val season = e.optInt("season", 1).coerceIn(1, 99)
                val number = e.optInt("episode", j + 1).coerceIn(1, 999)
                items.add(Ep(e.optString("title").ifBlank { "Episodio " + number },
                    season, number, videos, tracks))
            }
            if (items.isNotEmpty()) list.add(Show(id, title, s.optString("description"), items))
        }
        return list
    }

    private suspend fun catalogue(): List<Show> {
        val now = System.currentTimeMillis()
        cache?.let { if (now < cachedUntil) return it }
        val shows = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(registry).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6500
                    readTimeout = 6500
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode != 200) return@withContext emptyList()
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText().take(250_000)
                }
                parse(body)
            } catch (_: Exception) {
                emptyList()
            } finally {
                conn?.disconnect()
            }
        }
        cache = shows
        cachedUntil = now + if (shows.isEmpty()) 20_000L else 120_000L
        return shows
    }

    private fun key(show: Show) = mainUrl + "/tree/main/config?series=" + show.id
    private fun result(show: Show): SearchResponse =
        newTvSeriesSearchResponse(show.title, key(show), TvType.TvSeries) {}

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
        newHomePageResponse("Episodi disponibili",
            if (page == 1) catalogue().map(::result) else emptyList(), false)

    override suspend fun search(query: String): List<SearchResponse> {
        val words = query.lowercase(Locale.ROOT).trim().split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
        return if (words.isEmpty()) emptyList() else catalogue().filter { show ->
            val text = (show.title + " " + show.plot).lowercase(Locale.ROOT)
            words.all { text.contains(it) }
        }.map(::result)
    }

    override suspend fun load(url: String): LoadResponse {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host != "github.com" ||
            uri.path != "/Frenk0371/frenky-cloudstream/tree/main/config")
            throw IllegalArgumentException("URL serie non valido")
        val id = Regex("""(?:^|&)series=([a-zA-Z0-9_-]+)""")
            .find(uri.query.orEmpty())?.groupValues?.get(1)
        val show = catalogue().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Serie non presente")
        val episodes = show.episodes.sortedWith(compareBy<Ep> { it.season }.thenBy { it.number })
            .map { ep ->
                newEpisode(ep.videos.first().url) {
                    name = ep.name
                    season = ep.season
                    episode = ep.number
                }
            }
        return newTvSeriesLoadResponse(show.title, url, TvType.TvSeries, episodes) {
            plot = show.plot
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episode = catalogue().asSequence().flatMap { it.episodes.asSequence() }
            .firstOrNull { ep -> ep.videos.first().url == data } ?: return false
        var count = 0
        for (stream in episode.videos) {
            if (!valid(stream.url)) continue
            val label = if (stream.type == ExtractorLinkType.M3U8) "HLS" else "MP4"
            val resolution = if (stream.quality == Qualities.Unknown.value) "Auto"
                else stream.quality.toString() + "p"
            callback(newExtractorLink(name, label + " - " + resolution,
                stream.url, type = stream.type) {
                quality = stream.quality
            })
            count++
        }
        for (sub in episode.subtitles) subtitleCallback(SubtitleFile(sub.label, sub.url))
        return count > 0
    }
}
