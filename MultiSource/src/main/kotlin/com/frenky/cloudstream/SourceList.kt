package com.frenky.cloudstream

data class Source(val id: String, val displayName: String, val fallbackUrl: String)

object SourceList {
    val all = listOf(
        Source("altadefinizione", "AltaDefinizione (bozza)", "https://altadefinizionex.surf/"),
        Source("eurostream", "Eurostream (bozza)", "https://eurostream.pics/"),
        Source("guardaserie", "GuardaSerie (bozza)", "https://guardaseriex.click/"),
        Source("streamingcommunity", "StreamingCommunity (bozza)", "https://streamingcommunityz.rip/"),
        Source("ilgenio", "Il Genio dello Streaming (bozza)", "https://il-geniodellostreaming.vip/"),
        Source("cb01", "CB01 (bozza)", "https://cbin99.mobi/casa/")
    )
}
