package com.frenky.cloudstream

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Fetches the public registry from our GitHub repository.  This does not scrape
 * video websites, bypass access controls, or execute data from the registry.
 */
object DomainRegistry {
    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/main/config/domains.json"

    suspend fun resolve(sourceId: String, fallback: String): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(CONFIG_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return fallback
            }
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText().take(150000)
            }
            val sources = JSONObject(payload).getJSONArray("sources")
            for (i in 0 until sources.length()) {
                val source = sources.getJSONObject(i)
                if (source.optString("id") != sourceId) continue
                val candidate = source.optString("current_url")
                val uri = URI(candidate)
                if (uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null &&
                    (uri.port == -1 || uri.port == 443)) {
                    return candidate
                }
                break
            }
            fallback
        } catch (_: Exception) {
            fallback
        } finally {
            connection?.disconnect()
        }
    }
}
