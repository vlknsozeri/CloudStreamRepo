package com.fsociety

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class HdFilmCehennemiProvider : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HdFilmCehennemi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "tr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/category/film-izle-2/" to "Son Eklenen Filmler",
        "$mainUrl/yabancidiziizle-8/" to "Yabancı Diziler",
        "$mainUrl/populer-filmler-1/" to "Popüler Filmler",
        "$mainUrl/en-cok-begenilen-filmleri-izle-1/" to "En Beğenilenler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page > 1) "${request.data}page/$page/" else request.data
        val document = app.get(targetUrl, headers = Utils.defaultHeaders).document

        val homeItems = document.select("a.poster, .slider-slide a.poster, .movie-item a").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, homeItems, hasNext = homeItems.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/$query/"
        val document = app.get(searchUrl, headers = Utils.defaultHeaders).document

        return document.select("a.poster, .poster, .search-results a").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.attr("href").ifEmpty { this.selectFirst("a")?.attr("href") }) ?: return null
        val title = this.attr("title").ifEmpty {
            this.selectFirst("img")?.attr("alt") ?: this.selectFirst(".title, h2, h3")?.text()
        } ?: return null

        val img = this.selectFirst("img")
        val posterUrl = fixUrlNull(
            img?.attr("src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("srcset")?.split(" ")?.firstOrNull()
        )

        val isTvSeries = href.contains("/dizi/")
        val tvType = if (isTvSeries) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.Movie) {
            newMovieSearchResponse(title.cleanTitle(), href, tvType) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title.cleanTitle(), href, tvType) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = Utils.defaultHeaders).document

        val title = document.selectFirst("h1, .film-title, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        } ?: "Başlıksız"

        val posterUrl = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst(".poster img, .poster-wrapper img")?.attr("src")
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst(".ozet, .description, .film-content")?.text()

        val isTvSeries = url.contains("/dizi/")

        if (isTvSeries) {
            val episodes = mutableListOf<Episode>()

            // 1. JSON-LD içinden tarama
            val jsonLd = document.select("script[type='application/ld+json']").html()
            val epMatches = Regex(""""name":\s*"([^"]+)",\s*"url":\s*"([^"]+)"""").findAll(jsonLd)
            for (match in epMatches) {
                val epName = match.groupValues[1]
                val epUrl = match.groupValues[2]

                val seasonNum = Regex("""(\d+)\.\s*Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = Regex("""(\d+)\.\s*Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                episodes.add(
                    newEpisode(epUrl) {
                        this.name = epName
                        this.season = seasonNum
                        this.episode = episodeNum
                    }
                )
            }

            // 2. HTML bağlantılarından tarama (Fallback)
            if (episodes.isEmpty()) {
                document.select("a[href*='/sezon-'][href*='/bolum-']").forEach { link ->
                    val epHref = fixUrlNull(link.attr("href")) ?: return@forEach
                    val epName = link.text().ifBlank { "Bölüm" }

                    val seasonNum = Regex("""sezon-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val episodeNum = Regex("""bolum-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                    episodes.add(
                        newEpisode(epHref) {
                            this.name = epName
                            this.season = seasonNum
                            this.episode = episodeNum
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(title.cleanTitle(), url, TvType.TvSeries, episodes.distinctBy { it.data }) {
                this.posterUrl = posterUrl
                this.plot = description
            }
        } else {
            return newMovieLoadResponse(title.cleanTitle(), url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = Utils.defaultHeaders).document
        var foundAny = false

        // 1. Alternatif video kaynaklarını (data-video butonları) API üzerinden çöz
        val buttons = document.select("button.alternative-link, div.alternative-links button")
        for (button in buttons) {
            val videoId = button.attr("data-video").trim()
            val sourceName = button.text().replace("(HDrip Xbet)", "").trim().ifBlank { "HdFilmCehennemi" }

            if (videoId.isNotBlank()) {
                try {
                    val apiResponse = app.get(
                        "${mainUrl}/video/$videoId/",
                        headers = mapOf(
                            "Content-Type" to "application/json",
                            "X-Requested-With" to "fetch"
                        ),
                        referer = data
                    ).text

                    val iframeSrc = Regex("""(?:data-src|src)=\\"([^"]+)""").find(apiResponse)?.groupValues?.get(1)?.replace("\\", "")

                    if (!iframeSrc.isNullOrBlank()) {
                        val finalIframe = when {
                            iframeSrc.startsWith("//") -> "https:$iframeSrc"
                            iframeSrc.startsWith("/") -> "${mainUrl}$iframeSrc"
                            iframeSrc.contains("?rapidrame_id=") -> "${mainUrl}/playerr/" + iframeSrc.substringAfter("?rapidrame_id=")
                            else -> iframeSrc
                        }

                        // Yerleşik CloudStream extractorlarını dene (Rapidrame, Vidmoly vb.)
                        val loaded = loadExtractor(finalIframe, "${mainUrl}/", subtitleCallback, callback)
                        if (loaded) foundAny = true

                        // Gömülü player sayfasındaki JSON-LD veya m3u8 akışını tara
                        try {
                            val playerHtml = app.get(finalIframe, referer = "${mainUrl}/", headers = Utils.defaultHeaders).text
                            val contentUrl = Regex(""""contentUrl":\s*"([^"]+)"""").find(playerHtml)?.groupValues?.get(1)
                            if (!contentUrl.isNullOrBlank()) {
                                callback.invoke(
                                    newExtractorLink(
                                        source = sourceName,
                                        name = "$sourceName (HLS)",
                                        url = contentUrl,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = "${mainUrl}/"
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                                foundAny = true
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Sayfada doğrudan var olan iframe veya video kaynaklarını tara
        val directFound = Utils.extractAllVideoSources(document, data, subtitleCallback, callback)
        if (directFound) foundAny = true

        return foundAny
    }

    private fun String.cleanTitle(): String {
        return this.replace("izle", "", ignoreCase = true)
            .replace("Türkçe Dublaj", "", ignoreCase = true)
            .replace("Full HD", "", ignoreCase = true)
            .replace("Altyazılı", "", ignoreCase = true)
            .trim()
    }
}
