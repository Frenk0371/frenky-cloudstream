package com.frenky.cloudstream

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.webkit.WebChromeClient
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

    /**
     * User-controlled page inspection after an ordinary, manual website check.
     * No CAPTCHA automation, cookie export, or background challenge replay.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun readVerifiedPage(activity: Activity, pageUrl: String, requireEpisodes: Boolean = true): String? =
        withContext(Dispatchers.Main) {
            if (!isPublicHttps(pageUrl) || activity.isFinishing ||
                (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return@withContext null
            suspendCancellableCoroutine { continuation ->
                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8)
                }
                val instructions = TextView(activity).apply {
                    text = "Completa personalmente l'eventuale verifica del sito. Quando gli episodi sono visibili, il caricamento prosegue automaticamente."
                    textSize = 14f
                }
                val confirm = Button(activity).apply {
                    text = "Leggi episodi"
                    isEnabled = false
                }
                var inspectPage: () -> Unit = {}
                var completed = false
                val browser = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.WHITE)
                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.post { view.invalidate() }
                            view?.postDelayed({ view.invalidate() }, 350L)
                            view?.postDelayed({ view.invalidate() }, 1200L)
                            instructions.text = "Pagina caricata. Completa eventuali verifiche e controlla che siano visibili le stagioni."
                            confirm.isEnabled = true
                            view?.postDelayed({ if (!completed) inspectPage() }, 800L)
                            view?.postDelayed({ if (!completed) inspectPage() }, 3000L)
                            view?.postDelayed({ if (!completed) inspectPage() }, 6000L)
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                instructions.text = "Caricamento fallito: controlla la connessione e riprova."
                                confirm.isEnabled = false
                            }
                        }
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url?.toString() ?: return true
                            return !isPublicHttps(target)
                        }
                    }
                }
                val refresh = Button(activity).apply {
                    text = "Aggiorna visualizzazione"
                    setOnClickListener {
                        browser.visibility = android.view.View.INVISIBLE
                        browser.post {
                            browser.visibility = android.view.View.VISIBLE
                            browser.requestLayout()
                            browser.invalidate()
                        }
                    }
                }
                layout.addView(instructions)
                layout.addView(refresh)
                layout.addView(browser, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                layout.addView(confirm)
                val dialog = AlertDialog.Builder(activity).setView(layout)
                    .setNegativeButton("Annulla") { d, _ -> d.dismiss() }.create()
                fun finish(html: String?) {
                    if (continuation.isActive) continuation.resume(html)
                    dialog.dismiss()
                }
                inspectPage = inspect@{
                    val actualHost = runCatching {
                        URI(browser.url).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
                    }.getOrNull()
                    val expectedHost = URI(pageUrl).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
                    if (actualHost != expectedHost) {
                        instructions.text = "Torna alla pagina originale della serie prima di continuare."
                        return@inspect
                    }
                    browser.evaluateJavascript(
                        """(function() {
                            document.querySelectorAll('img').forEach(function(img) {
                                var source = img.currentSrc || img.src;
                                if (source && /^https:\\/\\//i.test(source)) {
                                    img.setAttribute('data-frenky-current-src', source);
                                }
                            });
                            return document.documentElement.outerHTML;
                        })()"""
                    ) { encoded ->
                        val html = runCatching {
                            org.json.JSONTokener(encoded).nextValue() as? String
                        }.getOrNull()
                        if (html.isNullOrBlank() || html.length > 1_500_000 ||
                            listOf("cf-turnstile", "checking your browser", "verifica di sicurezza",
                                "stiamo verificando", "just a moment").any { marker ->
                                html.contains(marker, ignoreCase = true)
                            }) {
                            instructions.text = "Pagina non disponibile. Completa la verifica e riprova."
                        } else if (requireEpisodes && !Regex("""(?i)\b[0-9]{1,2}\s*[x×]\s*[0-9]{1,3}\b""").containsMatchIn(org.jsoup.Jsoup.parse(html).text())) {
                            instructions.text = "Attendo gli episodi. Completa l'eventuale verifica del sito."
                        } else if (!requireEpisodes && org.jsoup.Jsoup.parse(html).select("article, .post, .item, .result, .search-result").isEmpty()) {
                            instructions.text = "Attendo i risultati della ricerca. Completa la verifica se richiesta."
                        } else {
                            completed = true
                            finish(html)
                        }
                    }
                }
                confirm.setOnClickListener { inspectPage() }
                dialog.setOnDismissListener {
                    browser.stopLoading()
                    browser.destroy()
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    activity.runOnUiThread { if (dialog.isShowing) dialog.dismiss() }
                }
                dialog.show()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                dialog.window?.clearFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                )
                browser.requestFocus()
                browser.loadUrl(pageUrl)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun open(activity: Activity, initialUrl: String): SelectedMedia? =
        withContext(Dispatchers.Main) {
            if (!isPublicHttps(initialUrl) || activity.isFinishing ||
                (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return@withContext null

            suspendCancellableCoroutine { continuation ->
                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(4, 2, 4, 2)
                }
                val status = TextView(activity).apply {
                    text = "Chiudi manualmente l'eventuale pubblicità e avvia il video. Nessun collegamento rilevato."
                    setPadding(4, 2, 4, 2)
                    textSize = 12f
                    maxLines = 2
                }
                val accept = Button(activity).apply {
                    text = "Usa il video rilevato"
                    isEnabled = false
                }
                val browser = WebView(activity).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
                }

                // The WebView is hosted inside an AlertDialog. Explicitly keep its
                // window eligible for IME input, and let touches retain native
                // WebView handling (including HTML input focus).
                browser.setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        view.requestFocusFromTouch()
                        view.post {
                            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                                as? InputMethodManager
                            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                    false
                }

                var captured: SelectedMedia? = null
                val dialog = AlertDialog.Builder(activity)
                    .setView(layout)
                    .setNegativeButton("Annulla") { d, _ -> d.dismiss() }
                    .create()

                fun finish(selection: SelectedMedia?) {
                    if (continuation.isActive) continuation.resume(selection)
                    dialog.dismiss()
                }

                browser.webChromeClient = object : WebChromeClient() {
                    override fun onRequestFocus(view: WebView?) {
                        view?.requestFocus()
                    }
                }
                // Some tablet WebViews render a blank frame until the screen wakes.
                // Request redraws after navigation without reloading the page,
                // so CAPTCHA state, cookies and playback handling stay intact.
                fun refreshRendering(view: WebView?) {
                    view ?: return
                    view.post { view.invalidate() }
                    view.postDelayed({ view.invalidate() }, 350L)
                    view.postDelayed({ view.invalidate() }, 1200L)
                }

                browser.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        refreshRendering(view)
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        refreshRendering(view)
                    }
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
                // Give the embedded page a real, screen-sized WebView area.
                // The site may have its own small content panel; this changes only
                // the Android container, not the website viewport or media requests.
                browser.setBackgroundColor(android.graphics.Color.WHITE)
                // A manual compositor nudge without navigation or CAPTCHA reload.
                val refreshButton = Button(activity).apply {
                    text = "Aggiorna visualizzazione"
                    setOnClickListener {
                        browser.visibility = android.view.View.INVISIBLE
                        browser.post {
                            browser.visibility = android.view.View.VISIBLE
                            browser.requestLayout()
                            browser.invalidate()
                        }
                    }
                }
                layout.addView(refreshButton, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                layout.addView(status, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                layout.addView(browser, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                layout.addView(accept, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                dialog.window?.clearFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                )
                dialog.window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                )
                browser.requestFocus()
                browser.loadUrl(initialUrl)
            }
        }
}
