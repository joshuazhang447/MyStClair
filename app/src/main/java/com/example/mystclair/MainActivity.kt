package com.example.mystclair

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.SystemBarStyle
import android.graphics.Color


class MainActivity : AppCompatActivity() {

    private lateinit var webViewMain: WebView
    private lateinit var webViewSub: WebView
    private lateinit var webViewApps: WebView
    private lateinit var btnMain: Button
    private lateinit var btnSub: Button
    private lateinit var btnMsApps: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnRefresh: ImageButton

    //File Upload handling
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>

    //Constants
    private val MAIN_URL = "https://stclairconnect.sharepoint.com/sites/mystclair"
    private var isRefreshing = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                Color.parseColor("#00693C")
            )
        )
        setContentView(R.layout.activity_main)

        //Initialize the file picker launcher
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val results = if (data != null) {
                    val clipData = data.clipData
                    if (clipData != null) {
                        val uris = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                        uris
                    } else {
                        data.data?.let { arrayOf(it) }
                    }
                } else null
                filePathCallback?.onReceiveValue(results)
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        webViewMain = findViewById(R.id.webViewMain)
        webViewSub = findViewById(R.id.webViewSub)
        webViewApps = findViewById(R.id.webViewApps)
        btnMain = findViewById(R.id.btnMain)
        btnSub = findViewById(R.id.btnSub)
        btnMsApps = findViewById(R.id.btnMsApps)
        btnBack = findViewById(R.id.btnBack)
        btnRefresh = findViewById(R.id.btnRefresh)

        setupWebViews()
        setupButtons()
        setupBackHandler()

        //Load initial st clair url
        webViewMain.loadUrl(MAIN_URL)
        updateSubButtonState(false)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    //Sub is visible and can go back, navigate back in Sub
                    webViewSub.visibility == View.VISIBLE && webViewSub.canGoBack() -> {
                        webViewSub.goBack()
                    }
                    //Sub is visible but can't go back, switch to Main
                    webViewSub.visibility == View.VISIBLE -> {
                        showMain()
                    }
                    //Apps is visible and can go back
                    webViewApps.visibility == View.VISIBLE && webViewApps.canGoBack() -> {
                        webViewApps.goBack()
                    }
                     //Apps is visible but can't go back, switch to Main
                    webViewApps.visibility == View.VISIBLE -> {
                        showMain()
                    }
                    //Main is visible and can go back, navigate back in Main
                    webViewMain.visibility == View.VISIBLE && webViewMain.canGoBack() -> {
                        webViewMain.goBack()
                    }
                    //Else exit the app
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun setupWebViews() {
        val chromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                if (intent == null) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        //Shared DownloadListener for both WebViews
        val downloadListener = { url: String, userAgent: String, contentDisposition: String, mimeType: String, _: Long ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType))
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(applicationContext, "Downloading File...", Toast.LENGTH_LONG).show()
        }

        webViewMain.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(false) // Explicitly disable multiple windows for Main
            // Revert to default (Mobile) User Agent as requested, relying on URL blocking to stop redirects
        }

        webViewMain.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("market://") || url.startsWith("intent://") || url.contains("play.google.com")) {
                    // Block App Store redirects
                    return true
                }

                if (isRefreshing) return false

                val hitTestResult = view?.hitTestResult
                val type = hitTestResult?.type ?: WebView.HitTestResult.UNKNOWN_TYPE
                val isClick = type == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
                              type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                
                if (!isClick) return false

                // Strict Whitelist: Only allow Main Site, Microsoft Login, and College SSO (sts1) in the Main WebView
                val lowerUrl = url.lowercase()
                val isMyStClair = lowerUrl.startsWith(MAIN_URL.lowercase())
                val isLogin = lowerUrl.contains("login.microsoftonline.com")
                // Specific check for the Auth server (sts1), excluding LMS and others
                // User requested strict prefix check
                val isCollegeLogin = lowerUrl.startsWith("https://sts1.stclaircollege.ca")
                
                return if (isMyStClair || isLogin || isCollegeLogin) {
                    false
                } else {
                    // Everything else (IP verification, other SharePoint sites, etc.) goes to Sub
                    switchToSub(url)
                    true 
                }
            }
        }
        webViewMain.webChromeClient = chromeClient
        webViewMain.setDownloadListener(downloadListener)

        webViewSub.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(true) // Enable to catch new window requests
        }

        webViewSub.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                //Navigate internally within Sub WebView
                return false
            }
        }
        
        //Custom WebChromeClient for Sub that handles new tab requests
        webViewSub.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                if (intent == null) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                //Temporary WebView to capture the actual URL being requested
                val tempWebView = WebView(this@MainActivity)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url.toString()
                        openInCustomTab(url)
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
        webViewSub.setDownloadListener(downloadListener)

        // webViewApps configuration (Desktop User Agent)
        webViewApps.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(true) // Enable to catch new window requests
            // Force Desktop User Agent for this WebView only
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        webViewApps.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                 val url = request?.url.toString()
                 // Block App Store redirects here as well, just in case
                if (url.startsWith("market://") || url.startsWith("intent://") || url.contains("play.google.com")) {
                    return true
                }
                // Keep navigation within this WebView for the Apps dashboard
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject JavaScript to remove the "Install apps" button
                // We use a MutationObserver to catch it even if it loads dynamically
                val js = """
                    (function() {
                        function removeInstallButton() {
                            var buttons = document.querySelectorAll('button[aria-label="Install apps"]');
                            buttons.forEach(function(btn) { 
                                btn.remove(); 
                                console.log('Removed Install apps button');
                            });
                        }
                        
                        // Run immediately
                        removeInstallButton();

                        // Observe changes to body to catch lazy loading
                        var observer = new MutationObserver(function(mutations) {
                            removeInstallButton();
                        });
                        
                        if (document.body) {
                            observer.observe(document.body, { childList: true, subtree: true });
                        }
                    })();
                """.trimIndent()
                view?.evaluateJavascript(js, null)
            }
        }

        // Custom WebChromeClient for Apps that handles new tab requests and file uploads
        webViewApps.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                if (intent == null) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // Temporary WebView to capture the actual URL being requested
                val tempWebView = WebView(this@MainActivity)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url.toString()
                        openInCustomTab(url)
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
        
        webViewApps.setDownloadListener(downloadListener)
    }

    private fun openInCustomTab(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }

    private fun setupButtons() {
        btnMain.setOnClickListener { 
            // Just switch to Main view, do not reload
            showMain() 
        }
        btnSub.setOnClickListener { showSub() }
        btnMsApps.setOnClickListener {
            // Load MS Apps in dedicated WebView with Desktop UA
            webViewApps.loadUrl("https://m365.cloud.microsoft/apps?auth=2&home=1")
            showApps()
        }
        
        btnBack.setOnClickListener {
            if (webViewSub.visibility == View.VISIBLE && webViewSub.canGoBack()) {
                webViewSub.goBack()
            } else if (webViewApps.visibility == View.VISIBLE && webViewApps.canGoBack()) {
                webViewApps.goBack()
            } else if (webViewMain.visibility == View.VISIBLE && webViewMain.canGoBack()) {
                webViewMain.goBack()
            }
        }

        btnRefresh.setOnClickListener {
            isRefreshing = true
            // Reset to Home (Menu)
            webViewMain.loadUrl(MAIN_URL)
            // Clear other WebViews
            webViewSub.loadUrl("about:blank")
            webViewApps.loadUrl("about:blank") // Optional: clear Apps too so it reloads fresh next time
            
            // Reset Info button state
            updateSubButtonState(false)
            
            // Navigate to Main view
            showMain()
            
            webViewMain.postDelayed({ isRefreshing = false }, 1000)
        }
    }

    private fun switchToSub(url: String) {
        webViewSub.loadUrl(url)
        updateSubButtonState(true)
        showSub()
    }
    
    private fun updateSubButtonState(enabled: Boolean) {
        btnSub.isEnabled = enabled
    }

    private fun showMain() {
        webViewMain.visibility = View.VISIBLE
        webViewSub.visibility = View.GONE
        webViewApps.visibility = View.GONE
    }

    private fun showSub() {
        webViewMain.visibility = View.GONE
        webViewSub.visibility = View.VISIBLE
        webViewApps.visibility = View.GONE
    }

    private fun showApps() {
        webViewMain.visibility = View.GONE
        webViewSub.visibility = View.GONE
        webViewApps.visibility = View.VISIBLE
    }
}