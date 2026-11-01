package com.fsociety

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class DizipalProvider : MainAPI() {
    override var mainUrl = "https://dizipal2145.com"
    override var name = "Dizipal"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "tr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/filmler" to "Son Eklenen Filmler",
        "$mainUrl/diziler" to "Popüler Diziler",
        "$mainUrl/trend" to "Trend İçerikler",
        "$mainUrl/anime" to "Anime Serileri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page > 1) "${request.data}?page=$page" else request.data
        val document = app.get(targetUrl, headers = Utils.defaultHeaders).document

        val homeItems = document.select(".content-card, li.content-card").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, homeItems, hasNext = homeItems.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?q=$query"
        val document = app.get(searchUrl, headers = Utils.defaultHeaders).document

        return document.select(".content-card, li.content-card, .search-result-item").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a.card-link, a") ?: return null
        val href = fixUrlNull(linkElement.attr("href")) ?: return null

        val title = this.selectFirst(".card-title, h3, h2")?.text()
            ?: this.selectFirst("img")?.attr("alt")
            ?: return null

        val img = this.selectFirst("img")
        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
                ?: img?.attr("src")?.takeIf { !it.startsWith("data:") }
        )

        val isTvSeries = href.contains("/dizi/")
        val isAnime = href.contains("/anime/")

        val tvType = when {
            isAnime -> TvType.Anime
            isTvSeries -> TvType.TvSeries
            else -> TvType.Movie
        }

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

        val title = document.selectFirst("h1.card-title, h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        } ?: "Başlıksız"

        val posterUrl = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst(".card-poster img, .poster img")?.attr("data-src")
                ?: document.selectFirst(".card-poster img, .poster img")?.attr("src")
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst(".description, .plot, .summary")?.text()

        val isTvSeries = url.contains("/dizi/")

        if (isTvSeries) {
            val episodes = document.select(".episode-item, a[href*='/bolum-'], a[href*='/sezon-']").mapNotNull { ep ->
                val epHref = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                val epName = ep.selectFirst(".episode-name, .title")?.text() ?: ep.text().ifBlank { "Bölüm" }

                // Sezon ve bölüm numarasını URL veya metinden yakala
                val seasonMatch = Regex("""sezon-(\d+)""").find(epHref)
                val episodeMatch = Regex("""bolum-(\d+)""").find(epHref)

                val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                newEpisode(epHref) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = episodeNum
                }
            }

            return newTvSeriesLoadResponse(title.cleanTitle(), url, TvType.TvSeries, episodes) {
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
        return Utils.extractAllVideoSources(document, data, subtitleCallback, callback)
    }

    private fun String.cleanTitle(): String {
        return this.replace("izle", "", ignoreCase = true)
            .replace("Türkçe Dublaj", "", ignoreCase = true)
            .replace("Türkçe Altyazılı", "", ignoreCase = true)
            .trim()
    }
}
