package com.frenky.cloudstream

enum class SearchMode { NONE, WORDPRESS, WORDPRESS_ROOT, DLE_GET }

data class Source(
    val id: String,
    val displayName: String,
    val fallbackUrl: String,
    val searchMode: SearchMode = SearchMode.NONE,
    val isSeries: Boolean = false
)

object SourceList {
    val all = listOf(
        Source("altadefinizione", "AltaDefinizione (non verificato)", "https://altadefinizionex.surf/"),
        Source("eurostream", "Eurostream", "https://eurostreamings.yachts/", SearchMode.WORDPRESS, true),
        Source("guardaserie", "GuardaSerie", "https://guardaseriex.click/", SearchMode.DLE_GET, true),
        Source("streamingcommunity", "StreamingCommunity (richiede JavaScript)", "https://streamingcommunityz.rip/"),
        Source("ilgenio", "Il Genio dello Streaming", "https://il-geniodellostreaming.vip/", SearchMode.DLE_GET),
        Source("cb01", "CB01", "https://cineblog001.tattoo/", SearchMode.WORDPRESS_ROOT)
    )
}
