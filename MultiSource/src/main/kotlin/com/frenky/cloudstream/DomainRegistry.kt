package com.frenky.cloudstream

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.net.URI

object DomainRegistry {
    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/main/config/domains.json"

    suspend fun resolve(sourceId: String, fallback: String): String {
        return try {
            val response = app.get(CONFIG_URL, timeout = 10)
            val list = JSONObject(response.text).getJSONArray("sources")
            for (i in 0 until list.length()) {
                val obj = list.getJSONObject(i)
                if (obj.getString("id") != sourceId) continue
                val candidate = obj.getString("current_url")
                val parsed = URI(candidate)
                if (parsed.scheme.equals("https", ignoreCase = true) &&
                    !parsed.host.isNullOrBlank() && parsed.userInfo == null &&
                    (parsed.port == -1 || parsed.port == 443)) {
                    return candidate
                }
                break
            }
            fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
