package com.frenky.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * Curated, freely shareable open movies from the official Blender video server.
 *
 * Media URLs are discovered at playback time via Blender's official PeerTube API.
 * The catalogue lists potential titles; availability depends on the publisher.
 * No unrelated site, credentials, DRM or access controls are involved.
 */
class FreeCinemaProvider : MainAPI() {
    override var mainUrl = "https://video.blender.org"
    override var name = "Frenky Cinema Libero"
    override var lang = "it"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    // A curated list of names only; never claim any static video URL exists.
    private data class Film(
        val title: String, val year: Int, val description: String, val tags: String = ""
    ) {
        val pageUrl: String get() = "https://video.blender.org/search?search=" +
            URLEncoder.encode(title, "UTF-8")
    }

    private val films = listOf(
        Film("Big Buck Bunny", 2008, "Un coniglio gigante, gentile ma determinato, si ribella agli scherzi di tre animali del bosco.", "animazione commedia coniglio"),
        Film("Sintel", 2010, "Una giovane guerriera attraversa un mondo fantastico alla ricerca del suo amico drago.", "animazione fantasy avventura drago"),
        Film("Tears of Steel", 2012, "Ad Amsterdam, un gruppo tenta di cambiare un evento del passato per fermare un futuro dominato dai robot.", "fantascienza sci-fi robot vfx"),
        Film("Spring", 2019, "Una giovane pastora e il suo cane incontrano spiriti antichi in un viaggio attraverso le stagioni.", "animazione fantasy primavera"),
        Film("Elephants Dream", 2006, "Due personaggi esplorano un mondo meccanico surreale nel primo Open Movie della Blender Foundation.", "animazione surreale fantascienza"),
        Film("Coffee Run", 2020, "Una giovane donna ripercorre ricordi agrodolci della sua relazione, spinta dal ritmo di un caffè.", "animazione commedia romantico"),
        Film("Cosmos Laundromat - First Cycle", 2015, "Un animale scoraggiato incontra una creatura insolita che gli propone una trasformazione inaspettata.", "animazione fantasy"),
        Film("Agent 327 - Operation Barbershop", 2017, "Un agente segreto entra in un negozio di barbiere e scopre un mistero molto più grande.", "animazione spionaggio azione"),
        Film("Caminandes 2 - Gran Dillama", 2013, "Un lama ostinato cerca di superare un ostacolo sulle montagne della Patagonia.", "animazione commedia lama"),
        Film("Caminandes 3 - Llamigos", 2016, "Due animali condividono una piccola avventura nella Patagonia innevata.", "animazione commedia lama"),
        Film("Glass Half", 2015, "Due critici d'arte discutono di un dipinto in una commedia animata.", "animazione commedia arte"),
        Film("Sprite Fright", 2021, "Un gruppo di ragazzi scopre che alcune piccole creature del bosco sono meno innocue del previsto.", "animazione horror commedia"),
        Film("Charge", 2022, "In un mondo con poca energia, un uomo entra in una fabbrica di batterie e incontra un robot di sicurezza.", "animazione fantascienza azione robot"),
        Film("Wing It!", 2023, "Un ingegnere e un aspirante pilota si ritrovano in un'avventura spaziale a bordo di un razzo fuori controllo.", "animazione commedia fantascienza razzo")
    )

    // Artwork is optional: a failed metadata lookup must never block playback.
    // Negative responses are cached briefly to avoid hammering the official API.
    private data class FilmMetadata(val poster: String?, val minutes: Int?)
    private data class CachedMetadata(val until: Long, val value: FilmMetadata)
    private val metadataCache = ConcurrentHashMap<String, CachedMetadata>()

    private fun response(film: Film, metadata: FilmMetadata): SearchResponse =
        newMovieSearchResponse(film.title, film.pageUrl, TvType.Movie) {
            this.year = film.year
            this.posterUrl = metadata.poster
        }

    private suspend fun decorated(items: List<Film>): List<SearchResponse> = supervisorScope {
        // Keep concurrent API requests bounded; preserve film ordering.
        val limiter = Semaphore(6)
        items.map { film ->
            async {
                val metadata = limiter.withPermit { officialMetadata(film) }
                response(film, metadata)
            }
        }.awaitAll()
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        return newHomePageResponse(
            "Blender Open Movies - 14 titoli",
            if (page == 1) decorated(films) else emptyList(),
            false
        )
    }

    private fun normalized(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    override suspend fun search(query: String): List<SearchResponse> {
        val tokens = normalized(query).split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return films.filter { f ->
            val searchable = normalized("${f.title} ${f.description} ${f.tags} ${f.year}")
            tokens.all { searchable.contains(it) }
        }.let { decorated(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val film = films.firstOrNull { it.pageUrl == url }
            ?: throw IllegalArgumentException("Film non presente nel catalogo libero")
        val metadata = officialMetadata(film)
        return newMovieLoadResponse(
            film.title, film.pageUrl, TvType.Movie, film.title
        ) {
            year = film.year
            posterUrl = metadata.poster
            duration = metadata.minutes
            tags = film.tags.split(" ").filter { it.isNotBlank() }
            plot = film.description +
                "\n\nFonte: Blender Studio / Blender Foundation, Open Movies." +
                " Il flusso viene recuperato dall'API ufficiale al momento della riproduzione, se disponibile." +
                " Film disponibili nell'audio originale; il doppiaggio italiano non è garantito." +
                " Per licenza e attribuzioni, consulta la pagina ufficiale e i titoli di coda."
        }
    }

    /**
     * PeerTube's publicly documented REST API supplies real video IDs and
     * media URLs. No hardcoded UUID or guessed /static/webseed path is used.
     */
    private suspend fun fetchJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        var con: HttpURLConnection? = null
        try {
            con = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FrenkyCinemaLibero/7")
            }
            if (con.responseCode != 200) return@withContext null
            val body = con.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(1_000_000)
            }
            JSONObject(body)
        } catch (_: Exception) {
            null
        } finally {
            con?.disconnect()
        }
    }

    private fun officialUrl(raw: String): Boolean = try {
        val uri = URI(raw)
        uri.scheme.equals("https", true) &&
            uri.host.equals("video.blender.org", true) &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    } catch (_: Exception) { false }

    private fun imageFromOfficialPath(value: String): String? {
        val url = when {
            value.startsWith("/") && !value.startsWith("//") -> mainUrl + value
            value.startsWith("https://") -> value
            else -> return null
        }
        if (!officialUrl(url)) return null
        val path = URI(url).path.lowercase(Locale.ROOT)
        return url.takeIf {
            path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".png") || path.endsWith(".webp")
        }
    }

    /**
     * Lightweight, non-authoritative artwork and duration lookup.
     * The trusted filter intentionally mirrors findRealLinks().
     * The proven v7 playback resolution remains untouched.
     */
    private suspend fun officialMetadata(film: Film): FilmMetadata {
        val now = System.currentTimeMillis()
        metadataCache[film.title]?.takeIf { it.until > now }?.let { return it.value }
        val query = URLEncoder.encode(film.title, "UTF-8")
        val response = fetchJson(mainUrl + "/api/v1/search/videos?search=" +
            query + "&count=30")
        val data = response?.optJSONArray("data")
        val expected = normalized(film.title)
        var best: JSONObject? = null
        if (data != null) for (i in 0 until data.length()) {
            val entry = data.optJSONObject(i) ?: continue
            if (!entry.optJSONObject("account")?.optString("name").equals("blender", true))
                continue
            val channel = entry.optJSONObject("channel")?.optString("displayName").orEmpty()
            if (!channel.contains("Blender Open Movies", true)) continue
            val name = normalized(entry.optString("name"))
            if (name != expected && !name.startsWith(expected + " ")) continue
            if (entry.optInt("duration", 0) < 90) continue
            if (best == null || name == expected) {
                best = entry
                if (name == expected) break
            }
        }
        val poster = best?.let { entry ->
            imageFromOfficialPath(entry.optString("previewPath"))
                ?: imageFromOfficialPath(entry.optString("thumbnailPath"))
        }
        val seconds = best?.optInt("duration", 0) ?: 0
        val metadata = FilmMetadata(poster, if (seconds > 0) (seconds + 59) / 60 else null)
        metadataCache[film.title] = CachedMetadata(
            now + if (best == null) 3 * 60_000L else 30 * 60_000L,
            metadata
        )
        return metadata
    }

    private data class Link(
        val url: String, val type: ExtractorLinkType, val quality: Int
    )

    private fun addPlayableFiles(obj: JSONObject, out: MutableList<Link>) {
        val items = obj.optJSONArray("files") ?: return
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val url = item.optString("fileUrl")
            if (!officialUrl(url) || !URI(url).path.endsWith(".mp4", true)) continue
            val label = item.optJSONObject("resolution")?.optString("label").orEmpty()
            val q = Regex("[0-9]{3,4}").find(label)?.value?.toIntOrNull()
                ?: Qualities.Unknown.value
            out.add(Link(url, ExtractorLinkType.VIDEO, q))
        }
    }

    private suspend fun findRealLinks(film: Film): List<Link> {
        val query = URLEncoder.encode(film.title, "UTF-8")
        val response = fetchJson(mainUrl + "/api/v1/search/videos?search=" +
            query + "&count=30") ?: return emptyList()
        val matches = response.optJSONArray("data") ?: return emptyList()
        val expected = normalized(film.title)
        val ids = mutableListOf<Pair<Int, String>>()
        for (i in 0 until matches.length()) {
            val video = matches.optJSONObject(i) ?: continue
            val account = video.optJSONObject("account")?.optString("name").orEmpty()
            val channel = video.optJSONObject("channel")?.optString("displayName").orEmpty()
            if (!account.equals("blender", true) ||
                !channel.contains("Blender Open Movies", true)) continue
            val title = normalized(video.optString("name"))
            if (title != expected && !title.startsWith(expected + " ")) continue
            if (video.optInt("duration") < 90) continue
            val uuid = video.optString("uuid")
            if (!Regex("[a-fA-F0-9-]{36}").matches(uuid)) continue
            ids.add((if (title == expected) 0 else 1) to uuid)
        }
        for ((_, id) in ids.sortedBy { it.first }.take(3)) {
            val info = fetchJson(mainUrl + "/api/v1/videos/" + id) ?: continue
            val links = mutableListOf<Link>()
            addPlayableFiles(info, links)
            val playlists = info.optJSONArray("streamingPlaylists")
            if (playlists != null) {
                for (i in 0 until playlists.length()) {
                    val item = playlists.optJSONObject(i) ?: continue
                    val manifest = item.optString("playlistUrl")
                    if (officialUrl(manifest) &&
                        URI(manifest).path.endsWith(".m3u8", true)) {
                        links.add(Link(manifest, ExtractorLinkType.M3U8,
                            Qualities.Unknown.value))
                    }
                    addPlayableFiles(item, links)
                }
            }
            if (links.isNotEmpty()) return links.distinctBy { it.url }
        }
        return emptyList()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val film = films.firstOrNull { it.title == data } ?: return false
        val links = findRealLinks(film)
        // Keep adaptive HLS available even when multiple MP4 renditions are present.
        // MP4 files remain selectable fallbacks if the device cannot play HLS.
        val preferred = links.filter { it.type == ExtractorLinkType.M3U8 }.take(1) +
            links.filter { it.type == ExtractorLinkType.VIDEO }
                .sortedByDescending { it.quality }.take(4)
        for (item in preferred) {
            callback(newExtractorLink(
                source = name,
                name = film.title + " - Blender " +
                    (if (item.quality == Qualities.Unknown.value) "Auto"
                     else "${item.quality}p") +
                    (if (item.type == ExtractorLinkType.M3U8) " HLS" else " MP4"),
                url = item.url, type = item.type
            ) {
                quality = item.quality
            })
        }
        return links.isNotEmpty()
    }
}
