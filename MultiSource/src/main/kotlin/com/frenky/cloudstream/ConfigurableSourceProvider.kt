package com.frenky.cloudstream

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL

/**
 * Metadata-only catalogue integration. No embed extraction, stream playback
 * or DRM/access-control circumvention is implemented.
 */
class ConfigurableSourceProvider(private val source: Source) : MainAPI() {
    override var mainUrl: String = source.fallbackUrl
    override var name: String = source.displayName
    override var lang: String = "it"
    override val supportedTypes = if (source.isSeries) setOf(TvType.TvSeries)
        else setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim().take(80)
        if (term.length < 2 || source.searchMode == SearchMode.NONE) return emptyList()
        mainUrl = DomainRegistry.resolve(source.id, source.fallbackUrl)
        val sep = if (mainUrl.contains("?")) "&" else "?"
        val encoded = URLEncoder.encode(term, "UTF-8")
        val searchUrl = when (source.searchMode) {
            SearchMode.WORDPRESS -> "${mainUrl.trimEnd('/')}/?s=$encoded"
            SearchMode.DLE_GET -> "${mainUrl.trimEnd('/')}/?do=search&subaction=search&story=$encoded"
            SearchMode.NONE -> return emptyList()
        }
        return try {
            val doc = Jsoup.parse(getHtml(searchUrl), mainUrl)
            val baseHost = URI(mainUrl).host?.removePrefix("www.") ?: return emptyList()
            val seen = HashSet<String>()
            doc.select("a[href]")
                .mapNotNull { a ->
                    val title = listOf(
                        a.attr("title"),
                        a.text(),
                        a.selectFirst("img[alt]")?.attr("alt").orEmpty()
                    ).firstOrNull { it.contains(term, ignoreCase = true) }
                        ?.trim()?.take(160) ?: return@mapNotNull null
                    val url = a.absUrl("href")
                    val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
                    if (!uri.scheme.equals("https", true) ||
                        uri.host?.removePrefix("www.") != baseHost ||
                        uri.path.isNullOrBlank() || uri.path == "/" ||
                        !seen.add(url)) return@mapNotNull null
                    val type = if (source.isSeries || uri.path.contains("serie", ignoreCase = true))
                        TvType.TvSeries else TvType.Movie
                    newMovieSearchResponse(title, url, type) {
                        val img = a.selectFirst("img")
                        val imgUrl = img?.absUrl("data-src")?.takeIf { it.startsWith("https://") }
                            ?: img?.absUrl("src")?.takeIf { it.startsWith("https://") }
                        this.posterUrl = imgUrl
                    }
                }.take(40)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val expected = URI(mainUrl).host?.removePrefix("www.")
        val itemHost = URI(url).host?.removePrefix("www.")
        if (expected == null || expected != itemHost || !url.startsWith("https://"))
            throw IllegalArgumentException("URL fuori dal provider")
        val doc: Document = Jsoup.parse(getHtml(url), url)
        val title = doc.selectFirst("h1")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Scheda del catalogo"
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst("meta[name=description]")?.attr("content")
            ?: ""
        val plotText = (description.take(600) +
            "\n\nScheda informativa: questa versione non include collegamenti video o riproduzione.").trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?.takeIf { it.startsWith("https://") }
        val isSeries = source.isSeries || url.contains("serie", ignoreCase = true)
        return if (isSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                this.posterUrl = poster
                this.plot = plotText
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, "") {
                this.posterUrl = poster
                this.plot = plotText
                this.comingSoon = true
            }
        }
    }

    private suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FrenkyCatalogue/1.0)")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            if (connection.responseCode != 200) return@withContext ""
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(8192)
                var total = 0
                while (total < 600_000) {
                    val length = input.read(chunk, 0, minOf(chunk.size, 600_000 - total))
                    if (length <= 0) break
                    out.write(chunk, 0, length)
                    total += length
                }
            }
            out.toString(Charsets.UTF_8.name())
        } finally {
            connection?.disconnect()
        }
    }
}
