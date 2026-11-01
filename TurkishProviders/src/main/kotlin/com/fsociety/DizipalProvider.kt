package com.fsociety

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
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
            val episodes = document.select(".detail-episode-item, .episode-item, a[href*='/bolum/'], a[href*='/sezon-'], a[href*='-bolum']").mapNotNull { ep ->
                val epHref = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                val epName = ep.selectFirst(".detail-episode-name, .episode-name, .title")?.text() ?: ep.text().ifBlank { "Bölüm" }

                val seasonMatch = Regex("""(\d+)[-.]sezon""").find(epHref) ?: Regex("""sezon[-.](\d+)""").find(epHref)
                val episodeMatch = Regex("""(\d+)[-.]bolum""").find(epHref) ?: Regex("""bolum[-.](\d+)""").find(epHref)

                val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

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

        val iframe = document.selectFirst(".series-player-container iframe, div#vast_new iframe, div#video-area iframe, iframe[src]")?.attr("src")

        if (!iframe.isNullOrBlank()) {
            val cleanIframe = when {
                iframe.startsWith("//") -> "https:$iframe"
                iframe.startsWith("/") -> "${mainUrl}$iframe"
                else -> iframe
            }

            try {
                val iSource = app.get(cleanIframe, referer = "$mainUrl/", headers = Utils.defaultHeaders).text
                val m3uLink = Regex("""file:\s*"([^"]+)"""").find(iSource)?.groupValues?.get(1)
                    ?: Regex("""(https?:[^\s"']+\.m3u8[^\s"']*)""").find(iSource)?.groupValues?.get(1)

                if (!m3uLink.isNullOrBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = "Dizipal",
                            name = "Dizipal (HLS)",
                            url = m3uLink,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                    return true
                }
            } catch (_: Exception) {}

            return loadExtractor(cleanIframe, "$mainUrl/", subtitleCallback, callback)
        }

        return Utils.extractAllVideoSources(document, data, subtitleCallback, callback)
    }

    private fun String.cleanTitle(): String {
        return this.replace("izle", "", ignoreCase = true)
            .replace("Türkçe Dublaj", "", ignoreCase = true)
            .replace("Türkçe Altyazılı", "", ignoreCase = true)
            .trim()
    }
}
