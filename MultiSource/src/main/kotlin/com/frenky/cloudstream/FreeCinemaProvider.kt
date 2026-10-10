package com.frenky.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.text.Normalizer
import java.util.Locale

/**
 * Curated, freely shareable open movies from the official Blender video server.
 *
 * Unlike the metadata-only HTML catalogues this provider exposes authorized,
 * directly playable MP4 files, not embeds or extracted links from unrelated sites.
 * It doesn't bypass DRM, logins or access controls.
 */
class FreeCinemaProvider : MainAPI() {
    override var mainUrl = "https://video.blender.org"
    override var name = "Frenky Cinema Libero"
    override var lang = "it"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    private data class Film(
        val title: String,
        val year: Int,
        val videoId: String,
        val streamUrl: String,
        val description: String,
        val tags: String = ""
    ) {
        val pageUrl: String get() = "https://video.blender.org/w/$videoId"
    }

    /**
     * MP4 media URLs from video.blender.org, as published in the public
     * Blender Open Movies directory. Only the official video.blender.org host.
     */
    private val films = listOf(
        Film("Big Buck Bunny", 2008,
            "bf1f3fb5-b119-4f9f-9930-8e20e892b898",
            "https://video.blender.org/static/webseed/bf1f3fb5-b119-4f9f-9930-8e20e892b898-720.mp4",
            "Un coniglio gigante, gentile ma determinato, si ribella agli scherzi di tre animali del bosco.",
            "animazione commedia coniglio"),
        Film("Sintel", 2010,
            "0eb052d0-fd51-43e6-aa33-ecdbf77a5d40",
            "https://video.blender.org/static/webseed/0eb052d0-fd51-43e6-aa33-ecdbf77a5d40-720.mp4",
            "Una giovane guerriera attraversa un mondo fantastico alla ricerca del suo amico drago.",
            "animazione fantasy avventura drago"),
        Film("Tears of Steel", 2012,
            "8533ea43-4271-4a57-9694-e9d0b35e1aa1",
            "https://video.blender.org/static/webseed/8533ea43-4271-4a57-9694-e9d0b35e1aa1-720.mp4",
            "Ad Amsterdam, un gruppo tenta di cambiare un evento del passato per fermare un futuro dominato dai robot.",
            "fantascienza sci-fi robot vfx"),
        Film("Spring", 2019,
            "3d95fb3d-c866-42c8-9db1-fe82f48ccb95",
            "https://video.blender.org/static/webseed/3d95fb3d-c866-42c8-9db1-fe82f48ccb95-720.mp4",
            "Una giovane pastora e il suo cane incontrano spiriti antichi in un viaggio attraverso le stagioni.",
            "animazione fantasy primavera"),
        Film("Elephants Dream", 2006,
            "cccc3e60-0291-4ecc-aa56-39b2e2c7d0d5",
            "https://video.blender.org/static/webseed/cccc3e60-0291-4ecc-aa56-39b2e2c7d0d5-720.mp4",
            "Due personaggi esplorano un mondo meccanico surreale nel primo Open Movie della Blender Foundation.",
            "animazione surreale fantascienza"),
        Film("Coffee Run", 2020,
            "ff8fe61b-026f-4f07-b66b-2a790d6f6ab1",
            "https://video.blender.org/static/webseed/ff8fe61b-026f-4f07-b66b-2a790d6f6ab1-720.mp4",
            "Una giovane donna ripercorre ricordi agrodolci della sua relazione, spinta dal ritmo di un caffè.",
            "animazione commedia romantico"),
        Film("Cosmos Laundromat - First Cycle", 2015,
            "f507dfdc-e73e-45a4-9778-d758cbe1ce96",
            "https://video.blender.org/static/webseed/f507dfdc-e73e-45a4-9778-d758cbe1ce96-720.mp4",
            "Un animale scoraggiato incontra una creatura insolita che gli propone una trasformazione inaspettata.",
            "animazione fantasy"),
        Film("Agent 327 - Operation Barbershop", 2017,
            "264ff760-803e-430e-8d81-15648e904183",
            "https://video.blender.org/static/webseed/264ff760-803e-430e-8d81-15648e904183-720.mp4",
            "Un agente segreto entra in un negozio di barbiere e scopre un mistero molto più grande.",
            "animazione spionaggio azione"),
        Film("Caminandes 2 - Gran Dillama", 2013,
            "fb70d459-48d2-4db5-adba-813c84f9200a",
            "https://video.blender.org/static/webseed/fb70d459-48d2-4db5-adba-813c84f9200a-720.mp4",
            "Un lama ostinato cerca di superare un ostacolo sulle montagne della Patagonia.",
            "animazione commedia lama"),
        Film("Caminandes 3 - Llamigos", 2016,
            "23f3ef79-15dc-44c5-aa45-cf92e78a4509",
            "https://video.blender.org/static/webseed/23f3ef79-15dc-44c5-aa45-cf92e78a4509-720.mp4",
            "Due animali condividono una piccola avventura nella Patagonia innevata.",
            "animazione commedia lama"),
        Film("Glass Half", 2015,
            "64222c8a-c4c7-4b3b-9850-7fb2078edcf6",
            "https://video.blender.org/static/webseed/64222c8a-c4c7-4b3b-9850-7fb2078edcf6-720.mp4",
            "Due critici d'arte discutono di un dipinto in una commedia animata.",
            "animazione commedia arte"),
        Film("Sprite Fright", 2021,
            "a69d68a5-a0e0-4a80-9d66-49f093c97aaf",
            "https://video.blender.org/download/videos/a69d68a5-a0e0-4a80-9d66-49f093c97aaf-720.mp4",
            "Un gruppo di ragazzi scopre che alcune piccole creature del bosco sono meno innocue del previsto.",
            "animazione horror commedia")
    )

    private fun response(film: Film): SearchResponse =
        newMovieSearchResponse(film.title, film.pageUrl, TvType.Movie) {
            this.year = film.year
        }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        return newHomePageResponse(
            "Film completi - Blender Open Movies",
            if (page == 1) films.map(::response) else emptyList(),
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
        }.map(::response)
    }

    override suspend fun load(url: String): LoadResponse {
        val film = films.firstOrNull { it.pageUrl == url }
            ?: throw IllegalArgumentException("Film non presente nel catalogo libero")
        return newMovieLoadResponse(
            film.title, film.pageUrl, TvType.Movie, film.videoId
        ) {
            year = film.year
            plot = film.description +
                "\\n\\nFonte: Blender Studio / Blender Foundation, Open Movies." +
                " Contenuto pubblicato sul server ufficiale video.blender.org." +
                " Film disponibili nell'audio originale; il doppiaggio italiano non è garantito." +
                " Per licenza e attribuzioni, consulta la pagina ufficiale e i titoli di coda."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val film = films.firstOrNull { it.videoId == data } ?: return false
        // Only approved HTTPS MP4 links on the official Blender Video host.
        if (!film.streamUrl.startsWith("https://video.blender.org/") ||
            !film.streamUrl.endsWith(".mp4")) return false

        callback(newExtractorLink(
            source = name,
            name = "${film.title} - Blender 720p",
            url = film.streamUrl,
            type = ExtractorLinkType.VIDEO
        ) {
            quality = Qualities.P720.value
        })
        return true
    }
}
