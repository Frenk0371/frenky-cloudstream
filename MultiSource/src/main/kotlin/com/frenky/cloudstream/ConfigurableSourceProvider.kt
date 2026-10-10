package com.frenky.cloudstream

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

/** Placeholder only: requires permitted catalogue adapters for search/playback. */
class ConfigurableSourceProvider(private val source: Source) : MainAPI() {
    override var mainUrl: String = source.fallbackUrl
    override var name: String = source.displayName
    override var lang: String = "it"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        mainUrl = DomainRegistry.resolve(source.id, source.fallbackUrl)
        return emptyList()
    }
}
