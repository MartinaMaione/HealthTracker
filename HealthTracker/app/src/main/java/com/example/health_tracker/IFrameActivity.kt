package com.example.health_tracker

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import androidx.activity.ComponentActivity

class IFrameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iframe)

        val iframeUrl = intent.getStringExtra("iframeUrl")

        // Verifica se iframeUrl non è nullo prima di chiamare loadUrl
        iframeUrl?.let {
            val webView: WebView = findViewById(R.id.webView)
            val webSettings: WebSettings = webView.settings
            webSettings.javaScriptEnabled = true
            webView.webChromeClient = WebChromeClient()
            val iframeCode = "<iframe width=\"100%\" height=\"100%\" frameborder=\"0\" src=\"$iframeUrl\"></iframe>"
            webView.loadData(iframeCode, "text/html", "utf-8")
            // back to main activity
            val backButton: Button = findViewById(R.id.backButton)
            backButton.setOnClickListener {
                finish() // Chiama la funzione di sistema per gestire il pulsante "Indietro"
            }
        } ?: run {
        }
    }
}
