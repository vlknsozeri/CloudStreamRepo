package com.fsociety

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class YabanciDiziProvider : MainAPI() {
    override var mainUrl = "https://yabancidizi.news"
    override var name = "Yabancı Dizi"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override var lang = "tr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/dizi-izle" to "Popüler Yabancı Diziler",
        "$mainUrl/trend" to "Haftanın Trendleri",
        "$mainUrl/film-izle" to "Yabancı Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page > 1) "${request.data}?sayfa=$page" else request.data
        val document = app.get(targetUrl, headers = Utils.defaultHeaders).document

        val homeItems = document.select(".item, .dizi-box, .movie-item, article, .content-card").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, homeItems, hasNext = homeItems.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/ara?q=$query"
        val document = app.get(searchUrl, headers = Utils.defaultHeaders).document

        return document.select(".item, .dizi-box, .movie-item, .search-result").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a[href]") ?: return null
        val href = fixUrlNull(linkElement.attr("href")) ?: return null

        val title = this.selectFirst(".title, h2, h3, .name")?.text()
            ?: this.selectFirst("img")?.attr("alt")
            ?: return null

        val img = this.selectFirst("img")
        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
                ?: img?.attr("src")?.takeIf { !it.startsWith("data:") }
        )

        val isTvSeries = !href.contains("/film")
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

        val title = document.selectFirst("h1, .dizi-name, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        } ?: "Başlıksız"

        val posterUrl = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst(".poster img, .cover img")?.attr("data-src")
                ?: document.selectFirst(".poster img, .cover img")?.attr("src")
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst(".summary, .ozet, .description")?.text()

        val isTvSeries = !url.contains("/film")

        if (isTvSeries) {
            val episodes = document.select(".episodes a, .bolumler a, a[href*='/sezon-'], a[href*='/bolum-']").mapNotNull { ep ->
                val epHref = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                val epName = ep.selectFirst(".name, .ep-title")?.text() ?: ep.text().ifBlank { "Bölüm" }

                val seasonNum = Regex("""sezon-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = Regex("""bolum-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                newEpisode(epHref) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = episodeNum
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
        return Utils.extractAllVideoSources(document, data, subtitleCallback, callback)
    }

    private fun String.cleanTitle(): String {
        return this.replace("izle", "", ignoreCase = true)
            .replace("Türkçe Dublaj", "", ignoreCase = true)
            .replace("Türkçe Altyazılı", "", ignoreCase = true)
            .trim()
    }
}
