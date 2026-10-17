package com.custom.mai400pro

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var exoPlayer: ExoPlayer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Force Samsung Galaxy S26 Ultra to route traffic through Dashcam Wi-Fi
        bindToDashcamWifi()

        // 2. Setup Fullscreen WebView
        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // 3. Connect HTML window.AndroidBridge.playRtsp() to Native ExoPlayer
        webView.addJavascriptInterface( object {
            @JavascriptInterface
            fun playRtsp(rtspUrl: String) {
                runOnUiThread { showNativeRtspDialog(rtspUrl) }
            }
        }, "AndroidBridge")

        // 4. Enable MP4 / JPG File Downloads to Phone's Downloads Folder
        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setTitle(fileName)
                request.setDescription("Downloading from 70mai A400 Pro...")
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "Downloading $fileName to Downloads...", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // 5. Load your HTML UI
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun showNativeRtspDialog(rtspUrl: String) {
        val playerView = PlayerView(this)
        playerView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            700
        )

        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(rtspUrl))
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        }

        AlertDialog.Builder(this)
            .setTitle("70mai A400 Pro Live View")
            .setView(playerView)
            .setPositiveButton("Close Stream") { dialog, _ ->
                exoPlayer?.release()
                exoPlayer = null
                dialog.dismiss()
            }
            .setOnCancelListener {
                exoPlayer?.release()
                exoPlayer = null
            }
            .show()
    }

    private fun bindToDashcamWifi() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                cm.bindProcessToNetwork(network)
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                cm.bindProcessToNetwork(null)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
