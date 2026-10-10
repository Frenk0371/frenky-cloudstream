package com.frenky.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * Native-player smoke tests using openly shareable sample clips.
 * Kept separate from metadata-only third-party catalogue providers.
 * No website scraping, video extraction, access-control bypass or DRM.
 */
class AuthorizedDemoProvider : MainAPI() {
    override var mainUrl = "https://www.w3schools.com"
    override var name = "Frenky - Test Player"
    override var lang = "it"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    private data class TestClip(
        val title: String,
        val url: String,
        val description: String
    )

    // Old Google GTV samples may return HTTP 403. Independent hosts make
    // connectivity/player troubleshooting possible without altering catalogues.
    private val clips = listOf(
        TestClip(
            "Big Buck Bunny - prova 10 secondi",
            "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4",
            "Clip di prova tratto da Big Buck Bunny. (c) Blender Foundation, CC BY 3.0."
        ),
        TestClip(
            "Video alternativo - W3Schools",
            "https://www.w3schools.com/html/mov_bbb.mp4",
            "Secondo server indipendente per collaudare la riproduzione MP4."
        ),
        TestClip(
            "Flower - prova video MDN",
            "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
            "Video di esempio CC0 di MDN per collaudare il lettore MP4."
        )
    )

    private fun response(clip: TestClip): SearchResponse =
        newMovieSearchResponse(clip.title, clip.url, TvType.Movie) {}

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val available = if (page == 1) clips.map(::response) else emptyList()
        return newHomePageResponse("Prove di riproduzione", available, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        if ("test".contains(trimmed, true) || "prova".contains(trimmed, true) ||
            "player".contains(trimmed, true)) return clips.map(::response)
        return clips.filter { it.title.contains(trimmed, true) }.map(::response)
    }

    override suspend fun load(url: String): LoadResponse {
        val clip = clips.firstOrNull { it.url == url }
            ?: throw IllegalArgumentException("URL non autorizzato per il player di test")
        return newMovieLoadResponse(clip.title, clip.url, TvType.Movie, clip.url) {
            plot = clip.description + " Riproduzione diretta nel player interno di CloudStream."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val clip = clips.firstOrNull { it.url == data } ?: return false
        callback(
            newExtractorLink(
                source = name,
                name = clip.title,
                url = clip.url,
                type = ExtractorLinkType.VIDEO
            ) {
                quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
