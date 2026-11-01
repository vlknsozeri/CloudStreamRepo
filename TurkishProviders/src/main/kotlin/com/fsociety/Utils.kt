package com.fsociety

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document

object Utils {
    val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Sec-Ch-Ua" to "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"",
        "Sec-Ch-Ua-Mobile" to "?0",
        "Sec-Ch-Ua-Platform" to "\"Windows\""
    )

    /**
     * Sık domain değiştiren siteler için son yönlendirilen aktif adresi tespit eder.
     */
    suspend fun resolveActiveDomain(fallbackUrl: String): String {
        return try {
            val response = app.get(fallbackUrl, headers = defaultHeaders)
            val current = response.url.trimEnd('/')
            if (current.startsWith("http")) current else fallbackUrl
        } catch (_: Exception) {
            fallbackUrl
        }
    }

    /**
     * Sayfadaki tüm iframe, embed ve m3u8 kaynaklarını ayrıştırarak CloudStream oynatıcısına aktarır.
     */
    suspend fun extractAllVideoSources(
        document: Document,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false

        // 1. Sayfa içindeki iframe veya embed player bağlantılarını çöz
        val iframes = document.select("iframe[src], iframe[data-src]").mapNotNull {
            val src = it.attr("src").ifEmpty { it.attr("data-src") }
            if (src.isNotBlank()) src else null
        }

        for (iframe in iframes) {
            val cleanUrl = when {
                iframe.startsWith("//") -> "https:$iframe"
                iframe.startsWith("/") -> "$referer$iframe"
                else -> iframe
            }

            // CloudStream'in yerleşik Video Extractor motorunu çağır (Vidmoly, Streamtape, VOE vb.)
            val loaded = loadExtractor(cleanUrl, referer, subtitleCallback, callback)
            if (loaded) foundAny = true
        }

        // 2. HTML kaynağındaki direkt m3u8 (HLS) yayın adreslerini tara
        val htmlContent = document.html()
        val m3u8Regex = Regex("""(https?:\\?/\\?/[^"'\s]+\.m3u8[^"'\s]*)""")
        for (match in m3u8Regex.findAll(htmlContent)) {
            val streamUrl = match.groupValues[1].replace("\\/", "/")
            callback(
                newExtractorLink(
                    source = "Direkt Akış",
                    name = "HLS Akışı (Reklamsız)",
                    url = streamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer
                    this.quality = Qualities.P1080.value
                }
            )
            foundAny = true
        }

        return foundAny
    }
}
