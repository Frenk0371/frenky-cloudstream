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
        Source("eurostream", "Eurostream", "https://eurostream.pics/", SearchMode.WORDPRESS, true)
    )
}
