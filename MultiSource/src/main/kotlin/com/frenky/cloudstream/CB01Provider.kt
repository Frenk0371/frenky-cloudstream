package com.frenky.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** CB01 catalogue and optional standard publicly accessible embedded players. */
class CB01Provider(private val source: Source) : MainAPI() {
    private val catalogue = ConfigurableSourceProvider(source)
    override var mainUrl: String = source.fallbackUrl
    override var name: String = source.displayName
    override var lang: String = "it"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
        catalogue.getMainPage(page, request)

    override suspend fun search(query: String): List<SearchResponse> = catalogue.search(query)

    private fun safeHttps(raw: String): Boolean = try {
        val uri = URI(raw)
        uri.scheme.equals("https", true) && uri.host != null &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            uri.host != "localhost" && !uri.host.endsWith(".local") &&
            !uri.host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))
    } catch (_: Exception) { false }

    private suspend fun html(url: String): String = withContext(Dispatchers.IO) {
        if (!safeHttps(url)) return@withContext ""
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            if (conn.responseCode != 200) return@withContext ""
            conn.inputStream.bufferedReader().use { it.readText().take(1_000_000) }
        } catch (_: Exception) { "" }
        finally { conn?.disconnect() }
    }

    private fun official(url: String): Boolean = try {
        safeHttps(url) && URI(url).host.equals(URI(mainUrl).host, true)
    } catch (_: Exception) { false }

    // Accept old saved CB01 cards only when their legacy host is known.
    // Keep the original full path: never redirect arbitrary hosts.
    private fun canonical(raw: String): String? = try {
        if (!safeHttps(raw)) null else {
            val uri = URI(raw)
            val host = uri.host.lowercase().removePrefix("www.")
            val current = URI(mainUrl).host.lowercase().removePrefix("www.")
            when (host) {
                current -> raw
                "cbin99.mobi" -> URI("https", null, current, -1,
                    uri.rawPath, uri.rawQuery, null).toASCIIString()
                else -> null
            }
        }
    } catch (_: Exception) { null }

    override suspend fun load(url: String): LoadResponse {
        val resolved = canonical(url) ?: throw IllegalArgumentException("Film fuori dal sito CB01")
        val doc = Jsoup.parse(html(resolved), resolved)
        val title = doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Film"
        val poster = catalogue.posterFor(url) ?: catalogue.posterFor(resolved) ?: PosterFinder.fromDocument(doc, title)
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
        return newMovieLoadResponse(title, resolved, TvType.Movie, resolved) {
            posterUrl = poster
            plot = description.take(950) + "\n\nI collegamenti video sono disponibili soltanto se il sito espone player compatibili con CloudStream."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val resolved = canonical(data) ?: return false
        val document = Jsoup.parse(html(resolved), resolved)
        // Standard embeds only: do not automate clicks, bypass ads, CAPTCHA, DRM or access checks.
        val players = document.select("iframe[src], iframe[data-src]")
            .flatMap { listOf(it.absUrl("src"), it.absUrl("data-src")) }
            .filter { safeHttps(it) && it != resolved && !official(it) }
            .distinct().take(12)
        var count = 0
        val seen = mutableSetOf<String>()
        for (player in players) {
            try {
                loadExtractor(player, resolved, subtitleCallback) { video ->
                    if (safeHttps(video.url) && seen.add(video.url)) {
                        callback(video)
                        count++
                    }
                }
            } catch (_: Exception) { /* Unsupported host: try next ordinary embed. */ }
        }
        return count > 0
    }
}
