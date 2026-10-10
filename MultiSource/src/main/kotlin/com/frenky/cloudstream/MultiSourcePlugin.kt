package com.frenky.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MultiSourcePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AuthorizedDemoProvider())
        SourceList.all.forEach { source ->
            registerMainAPI(ConfigurableSourceProvider(source))
        }
    }
}
