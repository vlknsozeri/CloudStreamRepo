package com.fsociety

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class DiziBoxProvider : MainAPI() {
    override var mainUrl = "https://www.dizibox.tv"
    override var name = "DiziBox"
    override val supportedTypes = setOf(TvType.TvSeries)
    override var lang = "tr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/diziler/" to "Tüm Diziler",
        "$mainUrl/trend-diziler/" to "Trend Diziler",
        "$mainUrl/tum-bolumler/" to "Son Eklenen Bölümler"
    )

    private val diziBoxCookies = mapOf(
        "LockUser" to "true",
        "isTrustedUser" to "true",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page > 1) "${request.data}page/$page/" else request.data
        val document = app.get(targetUrl, headers = Utils.defaultHeaders, cookies = diziBoxCookies).document

        val homeItems = document.select("article, .film-kutu, .movie-item, a.poster, .dizi-item").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, homeItems, hasNext = homeItems.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl, headers = Utils.defaultHeaders, cookies = diziBoxCookies).document

        return document.select("article, .film-kutu, .search-result, a.poster").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a[href]") ?: return null
        val href = fixUrlNull(linkElement.attr("href")) ?: return null
        val title = this.selectFirst(".title, h2, h3, h4")?.text()
            ?: this.selectFirst("img")?.attr("alt")
            ?: return null

        val img = this.selectFirst("img")
        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")?.takeIf { it.isNotBlank() }
        )

        return newTvSeriesSearchResponse(title.cleanTitle(), href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = Utils.defaultHeaders, cookies = diziBoxCookies).document

        val title = document.selectFirst("h1, .dizi-adi, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        } ?: "Başlıksız"

        val posterUrl = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst(".poster img, .dizi-afis img")?.attr("data-src")
                ?: document.selectFirst(".poster img, .dizi-afis img")?.attr("src")
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst(".ozet, .description, .entry-content p")?.text()

        val episodes = document.select(".bolum-item, a[href*='-sezon-'][href*='-bolum'], .episodes-list a").mapNotNull { ep ->
            val epHref = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
            val epName = ep.selectFirst(".title, .bolum-adi")?.text() ?: ep.text().ifBlank { "Bölüm" }

            val seasonNum = Regex("""(\d+)[-.]sezon""").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val episodeNum = Regex("""(\d+)[-.]bolum""").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1

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
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = Utils.defaultHeaders, cookies = diziBoxCookies).document
        var foundAny = false

        // 1. Ana video oynatıcı alanındaki iframe
        val mainIframe = document.selectFirst("div#video-area iframe, .video-player iframe, iframe[src]")?.attr("src")
        if (!mainIframe.isNullOrBlank()) {
            val cleanUrl = if (mainIframe.startsWith("//")) "https:$mainIframe" else mainIframe
            val loaded = loadExtractor(cleanUrl, "$mainUrl/", subtitleCallback, callback)
            if (loaded) foundAny = true
        }

        // 2. Alternatif video player seçenekleri (Toolbar dropdown)
        document.select("div.video-toolbar option[value], select.source-select option[value]").forEach { opt ->
            val altUrl = fixUrlNull(opt.attr("value")) ?: return@forEach
            try {
                val subDoc = app.get(altUrl, headers = Utils.defaultHeaders, cookies = diziBoxCookies).document
                val altIframe = subDoc.selectFirst("div#video-area iframe, iframe[src]")?.attr("src")
                if (!altIframe.isNullOrBlank()) {
                    val cleanUrl = if (altIframe.startsWith("//")) "https:$altIframe" else altIframe
                    val loaded = loadExtractor(cleanUrl, "$mainUrl/", subtitleCallback, callback)
                    if (loaded) foundAny = true
                }
            } catch (_: Exception) {}
        }

        val directFound = Utils.extractAllVideoSources(document, data, subtitleCallback, callback)
        if (directFound) foundAny = true

        return foundAny
    }

    private fun String.cleanTitle(): String {
        return this.replace("izle", "", ignoreCase = true)
            .replace("Türkçe Dublaj", "", ignoreCase = true)
            .replace("Türkçe Altyazılı", "", ignoreCase = true)
            .trim()
    }
}
