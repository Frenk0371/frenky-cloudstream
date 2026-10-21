package com.frenky.cloudstream

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Finds absolute artwork URLs from WordPress-style lazy-loaded image markup.
 * Does not guess poster URLs or use unrelated sidebar / advertisement images.
 */
object PosterFinder {
    private val attributes = listOf("data-src", "data-original", "data-lazy-src", "data-srcset", "srcset", "src")

    private fun usable(raw: String): Boolean = try {
        val uri = URI(raw)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            !raw.contains("placeholder", true) && !raw.contains("blank.gif", true) &&
            !raw.contains("data:image", true) &&
            !raw.endsWith(".svg", true)
    } catch (_: Exception) { false }

    private fun candidates(img: Element): Sequence<String> = attributes.asSequence()
        .flatMap { attr ->
            if (!img.hasAttr(attr)) emptySequence()
            else if (attr.endsWith("srcset")) {
                img.attr(attr).split(",").asSequence()
                    .map { it.trim().substringBefore(" ").trim() }
                    .map { img.baseUri().let { base -> runCatching { URI(base).resolve(it).toString() }.getOrDefault("") } }
            } else sequenceOf(img.absUrl(attr))
        }.filter(::usable)

    fun fromDocument(doc: Document, title: String = ""): String? {
        val meta = listOf(
            "meta[property=og:image]", "meta[name=twitter:image]",
            "meta[property=twitter:image]", "meta[itemprop=image]"
        ).asSequence().mapNotNull { doc.selectFirst(it)?.absUrl("content") }
            .firstOrNull(::usable)
        if (meta != null) return meta

        val imgs = doc.select("article img, .entry-content img, .post-content img, .post img, main img")
        val titleWords = title.lowercase().replace(Regex("""[^\p{L}\p{N} ]+"""), " ")
            .split(" ").filter { it.length >= 4 && it !in setOf("streaming", "stagione", "episodi") }
        val ranked = imgs.sortedByDescending { img ->
            val alt = (img.attr("alt") + " " + img.attr("title")).lowercase()
            val titleMatch = titleWords.count { it in alt }
            val coverHint = if (img.className().contains("poster", true) ||
                img.className().contains("cover", true) ||
                img.parent()?.className()?.contains("poster", true) == true) 10 else 0
            titleMatch * 4 + coverHint
        }
        return ranked.asSequence().mapNotNull { candidates(it).firstOrNull() }.firstOrNull()
    }

    fun fromSearchCard(anchor: Element, card: Element?): String? {
        val images = sequenceOf(anchor.selectFirst("img"), card?.selectFirst("img"),
            anchor.parent()?.selectFirst("img")).filterNotNull()
        return images.mapNotNull { image: Element -> candidates(image).firstOrNull() }.firstOrNull()
    }
}
