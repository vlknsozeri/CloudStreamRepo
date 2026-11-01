package com.fsociety

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TurkishPlugin : Plugin() {
    override fun load(context: Context) {
        // CloudStream'e Türkçe sağlayıcıları kaydet
        registerMainAPI(DizipalProvider())
        registerMainAPI(HdFilmCehennemiProvider())
        registerMainAPI(DiziBoxProvider())
        registerMainAPI(YabanciDiziProvider())
    }
}
