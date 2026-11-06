package com.gatefocus.app

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView

class PlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title") ?: "GATE lecture"
        val videoId = intent.getStringExtra("video_id") ?: return finish()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply { text = title; textSize = 20f; setPadding(20, 24, 20, 16) })
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        // Embed only the video ID saved in the user's curated list; never open the YouTube feed.
        web.loadDataWithBaseURL("https://www.youtube-nocookie.com", """
            <!doctype html><html><meta name="viewport" content="width=device-width, initial-scale=1">
            <body style="margin:0;background:#000"><iframe width="100%" height="260"
            src="https://www.youtube-nocookie.com/embed/$videoId?rel=0&modestbranding=1"
            title="GATE lecture" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe></body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
