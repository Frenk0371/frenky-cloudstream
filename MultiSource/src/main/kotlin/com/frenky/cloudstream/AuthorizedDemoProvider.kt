package com.frenky.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * Reproducible native-player smoke test using a freely licensed Blender Open Movie.
 * Kept separate from the metadata-only third-party catalogue providers.
 * No scraping, video extraction, DRM circumvention, or third-party authentication.
 */
class AuthorizedDemoProvider : MainAPI() {
    override var mainUrl = "https://storage.googleapis.com"
    override var name = "Frenky - Test Player"
    override var lang = "it"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    private val demoUrl =
        "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val films = if (page == 1) listOf(demoSearchResponse()) else emptyList()
        return newHomePageResponse("Test lettore interno", films, false)
    }

    private fun demoSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            "Big Buck Bunny - video di prova", demoUrl, TvType.Movie
        ) {}
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        return if (q.isNotBlank() &&
            ("big buck bunny".contains(q, ignoreCase = true) ||
             "test player".contains(q, ignoreCase = true) ||
             "video di prova".contains(q, ignoreCase = true))
        ) listOf(demoSearchResponse()) else emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        require(url == demoUrl) { "URL non autorizzato per il player di test" }
        return newMovieLoadResponse(
            "Big Buck Bunny - video di prova", demoUrl, TvType.Movie, demoUrl
        ) {
            plot = "Video dimostrativo aperto di Blender Foundation. " +
                "Serve esclusivamente a verificare la riproduzione nativa in CloudStream."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data != demoUrl) return false
        callback(
            newExtractorLink(
                source = name,
                name = "Big Buck Bunny - MP4",
                url = demoUrl,
                type = ExtractorLinkType.VIDEO
            ) {
                quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
