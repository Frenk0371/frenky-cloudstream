package com.frenky.cloudstream

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Experimental, user-operated browser for ordinary public player pages.
 * It does not skip countdowns, ads, or access checks, nor inject JavaScript.
 * A stream is passed to CloudStream only after an explicit user confirmation.
 */
internal object ManualMediaBrowser {
    data class SelectedMedia(
        val url: String,
        val referer: String,
        val cookie: String?
    )

    private fun isPublicHttps(url: String): Boolean = try {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        uri.scheme.equals("https", true) && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) && host != "localhost" &&
            !host.endsWith(".local") && !host.endsWith(".localhost") &&
            !host.contains(":") && !host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))
    } catch (_: Exception) { false }

    private fun isMedia(url: String): Boolean {
        val path = runCatching { URI(url).path.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp4")
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun open(activity: Activity, initialUrl: String): SelectedMedia? =
        withContext(Dispatchers.Main) {
            if (!isPublicHttps(initialUrl) || activity.isFinishing ||
                (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return@withContext null

            suspendCancellableCoroutine { continuation ->
                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12, 8, 12, 8)
                }
                val status = TextView(activity).apply {
                    text = "Chiudi manualmente l'eventuale pubblicità e avvia il video. Nessun collegamento rilevato."
                    setPadding(8, 8, 8, 8)
                }
                val accept = Button(activity).apply {
                    text = "Usa il video rilevato"
                    isEnabled = false
                }
                val browser = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
                }

                var captured: SelectedMedia? = null
                val dialog = AlertDialog.Builder(activity)
                    .setTitle("Player nel browser (sperimentale)")
                    .setView(layout)
                    .setNegativeButton("Annulla") { d, _ -> d.dismiss() }
                    .create()

                fun finish(selection: SelectedMedia?) {
                    if (continuation.isActive) continuation.resume(selection)
                    dialog.dismiss()
                }

                browser.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        val target = request?.url?.toString() ?: return true
                        return !isPublicHttps(target)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?, request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val target = request?.url?.toString() ?: return null
                        if (isPublicHttps(target) && isMedia(target)) {
                            val source = request.requestHeaders.entries.firstOrNull {
                                it.key.equals("referer", true)
                            }?.value?.takeIf(::isPublicHttps) ?: initialUrl
                            activity.runOnUiThread {
                                if (!dialog.isShowing) return@runOnUiThread
                                // This can be an ad video; never autoplay the first match.
                                val cookie = CookieManager.getInstance().getCookie(target)
                                captured = SelectedMedia(target, source, cookie)
                                status.text = "Trovato un possibile video su " +
                                    (runCatching { URI(target).host }.getOrNull() ?: "server") +
                                    ". Conferma soltanto dopo aver aperto l'episodio."
                                accept.isEnabled = true
                            }
                        }
                        return null // Let the browser perform the actual request.
                    }
                }
                layout.addView(status, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                layout.addView(accept, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                layout.addView(browser, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

                accept.setOnClickListener { captured?.let(::finish) }
                dialog.setOnDismissListener {
                    browser.stopLoading()
                    browser.loadUrl("about:blank")
                    browser.destroy()
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    activity.runOnUiThread { if (dialog.isShowing) dialog.dismiss() }
                }
                dialog.show()
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                browser.loadUrl(initialUrl)
            }
        }
}
