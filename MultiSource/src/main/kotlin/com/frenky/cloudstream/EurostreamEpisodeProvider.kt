package com.frenky.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * Eurostream catalogue adapter: recognizes actual seasons and episodes from
 * the public listing, and delegates playable links to CloudStream's registered
 * extractors. It does not implement DRM or access-control circumvention.
 *
 * A listing link is NOT a guaranteed playable media stream. Never claim
 * success unless an extractor actually emits an ExtractorLink.
 */
class EurostreamEpisodeProvider(private val source: Source) : MainAPI() {
    override var mainUrl: String = source.fallbackUrl
    override var name: String = source.displayName
    override var lang: String = "it"
    override val supportedTypes = setOf(TvType.TvSeries)
    override val hasMainPage = true

    private val catalogue = ConfigurableSourceProvider(source)
    private val seasonRegex = Regex("""(?i)\bstagione\s*([0-9]{1,2})\b""")
    private val episodeRegex = Regex("""(?i)\b([0-9]{1,2})\s*[x×]\s*([0-9]{1,3})\b""")
    private val simpleEpisodeRegex = Regex("""(?i)\bepisodi[oa]?\s*(?:n\.?\s*)?([0-9]{1,3})\b""")
    private val supportedLabels = Regex(
        """(?i)turbovid|deltabit|streamtape|mixdrop|dood|voe|vixcloud|vidoza|supervideo|filemoon|upstream|uqload|vidmoly|streamwish|mp4upload"""
    )

    private data class EpisodeId(val season: Int, val number: Int)
    private data class VideoPage(val name: String, val url: String)
    private data class EpisodeListing(
        val id: EpisodeId,
        val videoPages: LinkedHashMap<String, VideoPage> = linkedMapOf()
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
        catalogue.getMainPage(page, request)

    override suspend fun search(query: String): List<SearchResponse> {
        mainUrl = DomainRegistry.resolve(source.id, source.fallbackUrl)
        return catalogue.search(query)
    }

    private fun publicHttps(raw: String): Boolean = try {
        val uri = URI(raw)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        uri.scheme.equals("https", true) && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            host != "localhost" && !host.endsWith(".local") &&
            !host.endsWith(".localhost") &&
            !host.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) &&
            !host.contains(":")
    } catch (_: Exception) { false }

    private suspend fun html(url: String): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:119.0) Gecko/119.0 Firefox/119.0")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Accept-Language", "it-IT,it;q=0.9,en;q=0.6")
            }
            if (connection.responseCode != 200) return@withContext ""
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (total < 1_500_000) {
                    val count = input.read(buf, 0, minOf(buf.size, 1_500_000 - total))
                    if (count <= 0) break
                    out.write(buf, 0, count)
                    total += count
                }
                out.toString("UTF-8")
            }
        } catch (_: Exception) {
            ""
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Text and anchors are encountered in document order, so a line such as
     * "3×31 Episodio 31 – DeltaBit – Turbovid" groups both host links in one
     * episode. This avoids the common bug of reading entire parent <p> text
     * (which may contain dozens of episodes) and assigning every link to the
     * last episode of the season.
     */
    private fun parseEpisodes(document: org.jsoup.nodes.Document): List<EpisodeListing> {
        val root: Element = document.selectFirst(
            ".entry-content, .post-content, .post-entry, article .content, article"
        ) ?: document.body()
        val found = LinkedHashMap<EpisodeId, EpisodeListing>()
        var seasonHint = 1
        var active: EpisodeId? = null

        fun walk(node: Node) {
            if (node is Element && node.normalName() in setOf("script", "style", "noscript", "nav", "footer"))
                return
            if (node is TextNode) {
                val value = node.text()
                seasonRegex.findAll(value).lastOrNull()?.let {
                    seasonHint = it.groupValues[1].toIntOrNull() ?: seasonHint
                    active = null
                }
                episodeRegex.findAll(value).lastOrNull()?.let {
                    val season = it.groupValues[1].toIntOrNull() ?: seasonHint
                    val episode = it.groupValues[2].toIntOrNull() ?: 0
                    if (season in 1..99 && episode in 1..999) {
                        active = EpisodeId(season, episode)
                    }
                }
                // Some posts label a season once and write "Episodio 01" on
                // each subsequent line, without repeating 1×01.
                if (!episodeRegex.containsMatchIn(value)) {
                    simpleEpisodeRegex.findAll(value).lastOrNull()?.let {
                        val episode = it.groupValues[1].toIntOrNull() ?: 0
                        if (episode in 1..999 && seasonHint in 1..99)
                            active = EpisodeId(seasonHint, episode)
                    }
                }
            } else if (node is Element && node.normalName() == "a") {
                val id = active
                if (id != null && found.size < 1000) {
                    val href = node.absUrl("href")
                    val label = node.text().trim().ifBlank { node.attr("title") }
                    val host = runCatching { URI(href).host?.lowercase(Locale.ROOT) }.getOrNull()
                    val knownHost = host == "clicka.cc" ||
                        host?.endsWith(".clicka.cc") == true
                    if (publicHttps(href) && (supportedLabels.containsMatchIn(label) || knownHost)) {
                        val item = found.getOrPut(id) { EpisodeListing(id) }
                        if (item.videoPages.size < 10) {
                            item.videoPages.putIfAbsent(href, VideoPage(label.ifBlank { "Server" }, href))
                        }
                    }
                }
            }
            node.childNodes().forEach(::walk)
        }

        walk(root)
        return found.values
            .filter { it.videoPages.isNotEmpty() }
            .sortedWith(compareBy<EpisodeListing> { it.id.season }.thenBy { it.id.number })
    }

    override suspend fun load(url: String): LoadResponse {
        val currentDomain = DomainRegistry.resolve(source.id, source.fallbackUrl)
        val itemHost = runCatching { URI(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.") }.getOrNull()
        val officialHost = runCatching { URI(currentDomain).host?.lowercase(Locale.ROOT)?.removePrefix("www.") }.getOrNull()
        if (!publicHttps(url) || itemHost != officialHost)
            throw IllegalArgumentException("Scheda fuori dal dominio Eurostream")

        val document = Jsoup.parse(html(url), url)
        val title = document.selectFirst("h1")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Serie TV"
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("meta[name=description]")?.attr("content")
            ?: ""
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?.takeIf { publicHttps(it) }
        val listings = parseEpisodes(document)
        val episodes = listings.map { listing ->
            val links = JSONArray()
            listing.videoPages.values.forEach { link ->
                links.put(JSONObject().put("name", link.name).put("url", link.url))
            }
            val data = JSONObject()
                .put("origin", url)
                .put("players", links)
                .toString()
            newEpisode(data, initializer = {
                name = "Episodio " + listing.id.number
                season = listing.id.season
                episode = listing.id.number
            }, fix = false)
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = poster
            plot = description.take(800) + if (episodes.isEmpty()) {
                "\n\nNessun episodio ricavabile da questa pagina: controlla il sito."
            } else {
                "\n\n" + episodes.size + " episodi elencati. La riproduzione dipende dai server esterni disponibili."
            }
        }
    }

    /**
     * Try CloudStream's standard extractors for each public player URL. Some
     * shorteners and hosts will not resolve; this must return false in those
     * cases, rather than offering an HTML page as if it were an MP4 file.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return false
        val origin = json.optString("origin").takeIf(::publicHttps) ?: return false
        val players = json.optJSONArray("players") ?: return false
        var emitted = 0
        val seen = HashSet<String>()
        for (i in 0 until minOf(players.length(), 10)) {
            val player = players.optJSONObject(i) ?: continue
            val url = player.optString("url")
            if (!publicHttps(url) || !seen.add(url)) continue
            val path = runCatching { URI(url).path.lowercase(Locale.ROOT) }.getOrNull() ?: continue
            val label = player.optString("name", "Server")
            if (path.endsWith(".mp4") || path.endsWith(".m3u8")) {
                callback(newExtractorLink(
                    source = name,
                    name = label,
                    url = url,
                    type = if (path.endsWith(".m3u8")) ExtractorLinkType.M3U8
                        else ExtractorLinkType.VIDEO
                ) {
                    referer = origin
                    quality = Qualities.Unknown.value
                })
                emitted++
                continue
            }
            try {
                loadExtractor(url, origin, subtitleCallback) { video ->
                    if (publicHttps(video.url)) {
                        callback(video)
                        emitted++
                    }
                }
            } catch (_: Exception) {
                // Try the next mirror; do not turn a failed extraction into
                // a fake stream that the player cannot open.
            }
        }
        return emitted > 0
    }
}
