package com.oxgtech.bubbleshooterpro

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.oxgtech.bubbleshooterpro.ui.theme.BubbleShooterProTheme

class MainActivity : ComponentActivity() {

    private var webViewRef: WebView? = null
    private var interstitialAd: InterstitialAd? = null
    private var hasShownInterstitialOnce: Boolean = false
    private var interstitialTimerJob: Job? = null
    private var pendingShowAfterLoad: Boolean = false

    companion object {
        private const val ASSET_URL = "file:///android_asset/bubble-shooter.html"
        // Production AdMob ad unit IDs
        private const val BANNER_AD_UNIT_ID = "ca-app-pub-1613534711045659/4666297143"
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1613534711045659/5726536878"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        // Initialize Google Mobile Ads SDK
        MobileAds.initialize(this) {}
        loadInterstitial()

        setContent {
            BubbleShooterProTheme {
                var isOnline by remember { mutableStateOf(hasInternetConnection()) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (isOnline) {
                                WebViewContainer(
                                    onWebViewReady = { webViewRef = it },
                                    onFirstPageLoaded = { scheduleInterstitialWithDelayOnce(30_000L) }
                                )
                            } else {
                                OfflineScreen(
                                    onRetry = {
                                        if (hasInternetConnection()) {
                                            isOnline = true
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Still offline. Check your connection.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }

                        BannerAd(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        )
                    }
                }
            }
        }
    }

    private fun scheduleInterstitialWithDelayOnce(delayMs: Long) {
        if (hasShownInterstitialOnce) return
        if (interstitialTimerJob?.isActive == true) return
        interstitialTimerJob = lifecycleScope.launch {
            delay(delayMs)
            if (hasShownInterstitialOnce) return@launch
            val ad = interstitialAd
            if (ad != null) {
                showInterstitial()
                hasShownInterstitialOnce = true
            } else {
                pendingShowAfterLoad = true
                loadInterstitial()
            }
        }
    }

    private fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    if (pendingShowAfterLoad && !hasShownInterstitialOnce) {
                        pendingShowAfterLoad = false
                        showInterstitial()
                        hasShownInterstitialOnce = true
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun showInterstitial() {
        val ad = interstitialAd ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                interstitialAd = null
            }
        }
        ad.show(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun WebViewContainer(
        onWebViewReady: (WebView) -> Unit,
        onFirstPageLoaded: () -> Unit
    ) {
        val activity = this
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        settings.safeBrowsingEnabled = true
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url
                            val scheme = url.scheme ?: ""
                            return if (scheme == "http" || scheme == "https" || scheme == "file") {
                                false
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, url)
                                    startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(activity, "No app found to open this link.", Toast.LENGTH_SHORT).show()
                                }
                                true
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            onFirstPageLoaded()
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            super.onReceivedError(view, request, error)
                            Toast.makeText(activity, "Page load error: ${error.description}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    webChromeClient = WebChromeClient()
                    loadUrl(ASSET_URL)
                    onWebViewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    @Composable
    private fun BannerAd(modifier: Modifier = Modifier) {
        AndroidView(
            factory = { context ->
                AdView(context).apply {
                    adUnitId = BANNER_AD_UNIT_ID
                    setAdSize(AdSize.BANNER)
                    loadAd(AdRequest.Builder().build())
                }
            },
            modifier = modifier
        )
    }

    @Composable
    private fun OfflineScreen(onRetry: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "No Internet Connection", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Please connect to the internet to continue.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { onRetry() }) { Text("Retry") }
        }
    }

    private fun hasInternetConnection(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            info != null && info.isConnected
        }
    }

    override fun onDestroy() {
        interstitialTimerJob?.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            super.onBackPressed()
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewOffline() {
        BubbleShooterProTheme {
            OfflineScreen(onRetry = {})
        }
    }
}