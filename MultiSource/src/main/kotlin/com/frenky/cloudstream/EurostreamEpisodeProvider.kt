package com.frenky.cloudstream

import android.util.Log
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
        val candidates = document.select(
            ".entry-content, .post-content, .post-entry, article .content, article, main"
        )
        // A nested content fragment can include only season 1. Prefer the
        // smallest candidate with the highest number of season headings.
        val root: Element = candidates
            .sortedWith(compareByDescending<Element> {
                seasonRegex.findAll(it.text()).map { m -> m.groupValues[1] }
                    .distinct().count()
            }.thenBy { it.text().length })
            .firstOrNull() ?: document.body()
        val found = LinkedHashMap<EpisodeId, EpisodeListing>()
        var seasonHint = 1
        var active: EpisodeId? = null

        fun walk(node: Node) {
            if (node is Element && node.normalName() in setOf("script", "style", "noscript", "nav", "footer"))
                return
            if (node is Element && node.normalName() in
                setOf("h2", "h3", "h4", "h5", "summary", "button") &&
                seasonRegex.containsMatchIn(node.text())) {
                // Detect headings even when the number is in a separate span.
                seasonRegex.findAll(node.text()).lastOrNull()?.let {
                    seasonHint = it.groupValues[1].toIntOrNull() ?: seasonHint
                    active = null
                }
            }
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
                        found.getOrPut(active!!) { EpisodeListing(active!!) }
                    }
                }
                // Some posts label a season once and write "Episodio 01" on
                // each subsequent line, without repeating 1×01.
                if (!episodeRegex.containsMatchIn(value)) {
                    simpleEpisodeRegex.findAll(value).lastOrNull()?.let {
                        val episode = it.groupValues[1].toIntOrNull() ?: 0
                        if (episode in 1..999 && seasonHint in 1..99) {
                            active = EpisodeId(seasonHint, episode)
                            found.getOrPut(active!!) { EpisodeListing(active!!) }
                        }
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
        // Keep indexed episodes with no recognized server: otherwise an entire
        // season may disappear simply because an external host changed.
        return found.values
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
        val seasonCount = listings.map { it.id.season }.distinct().size
        val linkedCount = listings.count { it.videoPages.isNotEmpty() }
        val episodes = listings.map { listing ->
            val servers = listing.videoPages.values.map { it.name.trim() }
                .filter { it.isNotBlank() }.distinct().take(6)
            val intermediary = listing.videoPages.values.any { page ->
                val host = runCatching { URI(page.url).host?.lowercase(Locale.ROOT) }
                    .getOrNull().orEmpty()
                host == "clicka.cc" || host.endsWith(".clicka.cc") ||
                    host == "safego.cc"
            }
            val episodeDetails = if (listing.videoPages.isEmpty()) {
                "Nessun server video riconosciuto. Puoi verificare la pagina " +
                    "originale premendo l'icona del globo nella scheda della serie."
            } else {
                "Server elencati: " + servers.joinToString(", ") + "." +
                    if (intermediary) {
                        " Alcuni link passano da pagine intermedie o verifiche " +
                            "interattive: CloudStream potrebbe non poterli riprodurre." +
                            " Usa il globo nella scheda della serie per aprire il sito " +
                            "nel browser e selezionare questa puntata."
                    } else {
                        " CloudStream prova i lettori supportati. Se non trova " +
                            "un video, usa il globo nella scheda per aprire il sito."
                    }
            }
            val links = JSONArray()
            listing.videoPages.values.forEach { link ->
                links.put(JSONObject().put("name", link.name).put("url", link.url))
            }
            val data = JSONObject()
                .put("origin", url)
                .put("players", links)
                .toString()
            newEpisode(data, initializer = {
                name = "Episodio " + listing.id.number +
                    if (listing.videoPages.isEmpty()) " (nessun server individuato)" else ""
                season = listing.id.season
                episode = listing.id.number
                this.description = episodeDetails
            }, fix = false)
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = poster
            plot = description.take(800) + if (episodes.isEmpty()) {
                "\n\nNessun episodio individuato nella pagina pubblica."
            } else {
                "\n\nCatalogo: " + episodes.size + " episodi in " + seasonCount +
                    " stagioni; " + linkedCount + " episodi con server elencati." +
                    " Alcuni server richiedono verifiche interattive o non sono" +
                    " compatibili con CloudStream. L'elenco non garantisce la riproduzione." +
                    " Se il lettore mostra Nessun link trovato, tocca il globo in " +
                    "alto a destra per aprire la pagina della serie nel browser."
            }
        }
    }

    /**
     * Try CloudStream's standard extractors for each public player URL. Some
     * shorteners and hosts will not resolve; this must return false in those
     * cases, rather than offering an HTML page as if it were an MP4 file.
     */

    private data class PublicMedia(
        val url: String,
        val format: ExtractorLinkType
    )

    /**
     * Only ordinary HTML5 video sources are candidates. This does not execute
     * JavaScript, submit forms, impersonate a user or pass human challenges.
     */
    private fun mediaFormat(url: String, typeHint: String = ""): ExtractorLinkType? {
        val path = runCatching { URI(url).path.lowercase(Locale.ROOT) }
            .getOrNull().orEmpty()
        val mime = typeHint.substringBefore(';').lowercase(Locale.ROOT).trim()
        return when {
            path.endsWith(".m3u8") || mime in
                setOf("application/x-mpegurl", "application/vnd.apple.mpegurl") ->
                ExtractorLinkType.M3U8
            path.endsWith(".mpd") || mime == "application/dash+xml" ->
                ExtractorLinkType.DASH
            path.endsWith(".mp4") || mime == "video/mp4" ->
                ExtractorLinkType.VIDEO
            else -> null
        }
    }

    private fun parseHtml5Media(pageUrl: String, documentHtml: String): List<PublicMedia> {
        // Never treat a verification or login form as an accessible video.
        if (listOf("cf-turnstile", "g-recaptcha", "hcaptcha", "imhuman",
                "name=\"captcha\"", "name='captcha'").any {
                documentHtml.contains(it, ignoreCase = true)
            }) return emptyList()

        val document = Jsoup.parse(documentHtml, pageUrl)
        val found = LinkedHashMap<String, PublicMedia>()
        for (node in document.select("video[src], video source[src]")) {
            val media = node.absUrl("src")
            if (!publicHttps(media)) continue
            val type = mediaFormat(media, node.attr("type")) ?: continue
            found.putIfAbsent(media, PublicMedia(media, type))
            if (found.size == 4) return found.values.toList()
        }
        // Also read plain, directly declared media in common player scripts.
        // This is only text parsing: no packed JS, eval, protected requests,
        // forms or interactive verification are performed.
        val plainFile = Regex("""(?i)(?:\bfile\b|\bsrc\b)\s*:\s*['"](https://[^'"\s<>]+)['"]""")
        for (script in document.select("script")) {
            val data = script.data()
            if (!data.contains("jwplayer", ignoreCase = true) &&
                !data.contains("sources", ignoreCase = true) &&
                !data.contains("file:", ignoreCase = true)) continue
            for (candidate in plainFile.findAll(data).take(8)) {
                val media = candidate.groupValues[1].replace("&amp;", "&")
                if (!publicHttps(media)) continue
                val type = mediaFormat(media) ?: continue
                found.putIfAbsent(media, PublicMedia(media, type))
                if (found.size == 4) return found.values.toList()
            }
        }
        return found.values.toList()
    }

    /**
     * Follow only ordinary HTTP redirects (up to three); do not follow HTML
     * interstitials, execute scripts, submit forms, or answer challenges.
     */
    private suspend fun publicHttpDestination(url: String): String = withContext(Dispatchers.IO) {
        var current = url
        val visited = mutableSetOf<String>()
        for (hop in 0 until 3) {
            if (!publicHttps(current) || !visited.add(current)) break
            if (runCatching { URI(current).host?.endsWith("safego.cc") == true }
                    .getOrDefault(false)) break
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                }
                val status = connection.responseCode
                if (status !in setOf(301, 302, 303, 307, 308)) break
                val location = connection.getHeaderField("Location") ?: break
                val next = URI(current).resolve(location).toString()
                if (!publicHttps(next) || next in visited) break
                current = next
            } catch (_: Exception) {
                break
            } finally {
                connection?.disconnect()
            }
        }
        current
    }

    private fun isIntermediary(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }
            .getOrNull().orEmpty()
        return host == "clicka.cc" || host.endsWith(".clicka.cc") ||
            host == "safego.cc" || host.endsWith(".safego.cc")
    }

    private suspend fun openlyAvailableHtml5Media(pageUrl: String): List<PublicMedia> {
        // These are interstitial/verification pages, not video players.
        if (isIntermediary(pageUrl)) return emptyList()
        val body = html(pageUrl)
        if (body.isBlank() || (!body.contains("<video", ignoreCase = true) &&
                !body.contains("jwplayer", ignoreCase = true) &&
                !body.contains("sources:", ignoreCase = true))) return emptyList()
        return parseHtml5Media(pageUrl, body)
    }

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
            val label = player.optString("name", "Server")
            val directFormat = mediaFormat(url)
            if (directFormat != null) {
                callback(newExtractorLink(
                    source = name,
                    name = label,
                    url = url,
                    type = directFormat
                ) {
                    referer = origin
                    quality = Qualities.Unknown.value
                })
                emitted++
                continue
            }
            val before = emitted
            // Some known URL shorteners return a plain HTTP 301/302/307.
            // A CAPTCHA or JavaScript landing page never yields a video URL.
            val destination = if (isIntermediary(url)) publicHttpDestination(url) else url
            val candidates = linkedSetOf(url, destination)
            for (candidate in candidates) {
                if (emitted != before || isIntermediary(candidate)) continue
                try {
                    loadExtractor(candidate, origin, subtitleCallback) { video ->
                        if (publicHttps(video.url) && seen.add(video.url)) {
                            callback(video)
                            emitted++
                        }
                    }
                } catch (error: Exception) {
                    val host = runCatching { URI(candidate).host }.getOrNull().orEmpty()
                    Log.w("FrenkyCloudStream", "Eurostream: extractor failure for " +
                        host + " (" + error.javaClass.simpleName + ")")
                }
            }
            if (emitted == before) {
                val host = runCatching { URI(destination).host }.getOrNull().orEmpty()
                Log.i("FrenkyCloudStream", "Eurostream: no supported extractor for " +
                    host + " (possibly an interactive verification page)")
            }
            // A public, plain HTML5 video element may be playable even when
            // CloudStream has no named extractor for its host.
            // Never interpret iframes, script-generated URLs, or challenges as streams.
            if (emitted == before) {
                try {
                    for (media in openlyAvailableHtml5Media(destination)) {
                        if (!seen.add(media.url)) continue
                        callback(newExtractorLink(
                            source = name,
                            name = label + " HTML5",
                            url = media.url,
                            type = media.format
                        ) {
                            referer = destination
                            quality = Qualities.Unknown.value
                        })
                        emitted++
                    }
                } catch (error: Exception) {
                    val host = runCatching { URI(destination).host }.getOrNull().orEmpty()
                    Log.w("FrenkyCloudStream", "Eurostream: HTML5 parse error at " +
                        host + " (" + error.javaClass.simpleName + ")")
                }
            }
        }
        if (emitted == 0) {
            Log.i("FrenkyCloudStream", "Eurostream: no playable media from " +
                players.length() + " listed servers")
        }
        return emitted > 0
    }
}
