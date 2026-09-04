package com.myapp.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
import kotlin.collections.contains

class MainActivity : Activity() {

    private lateinit var mTopBar: LinearLayout
    private lateinit var mNavigationBar: LinearLayout

    private lateinit var mUrlInput: EditText
    private lateinit var mGoButton: ImageButton
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mBackButton: ImageButton
    private lateinit var mForwardButton: ImageButton
    private lateinit var mRefreshButton: ImageButton
    private lateinit var mHomeButton: ImageButton
    private lateinit var mMenuButton: ImageButton
    private lateinit var mDownloader: Downloader

    private lateinit var mTabsButton: ImageButton
    private val mTabs = mutableListOf<BrowserTab>()
    private var mCurrentTabIndex = 0
    private lateinit var mTabContainer: FrameLayout
    private var mIsMediaDetectionEnabled = false
    private val mProcessedRequestsCache = ProcessedRequestCache(mMaxSize = 200)
    // For fullscreen
    private var mCustomView: View? = null
    private var mCustomViewCallback: WebChromeClient.CustomViewCallback? = null

    enum class UserAgentType(val type: Int) {
        DEFAULT(0),
        CHROME_DESKTOP(1),
        FIREFOX_DESKTOP(2),
        SAFARI_IOS(3)
    }
    private var mCurrentUserAgentType = UserAgentType.DEFAULT
    private val mDefaultUserAgent: String = WebSettings.getDefaultUserAgent(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContentView(R.layout.main)
        mTopBar = findViewById(R.id.top_bar)
        mNavigationBar = findViewById(R.id.navigation_bar)
        mUrlInput = findViewById(R.id.url_input)
        mGoButton = findViewById(R.id.go_button)
        mProgressBar = findViewById(R.id.progress_bar)
        mBackButton = findViewById(R.id.back_button)
        mForwardButton = findViewById(R.id.forward_button)
        mRefreshButton = findViewById(R.id.refresh_button)
        mHomeButton = findViewById(R.id.home_button)
        mMenuButton = findViewById(R.id.menu_button)
        mTabContainer = findViewById(R.id.tab_container)
        mTabsButton = findViewById(R.id.tabs_button)
        mDownloader = Downloader(this)
        setupTabs()
        setupUrlBar()
        setupNavigationBar()
        loadPreference()
        val intentUrl = intent?.data?.toString()
        createNewTab(if (intentUrl.isNullOrEmpty())
            HOME_URL
        else
            intentUrl)
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            val url = it.data?.toString()
            if (!url.isNullOrEmpty()) {
                Log.d(TAG, "Load webview with intent data")
                createNewTab(url)
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        mTabs.forEach {
            it.destroy()
            mTabContainer.removeView(it.mWebView)
        }
        mTabs.clear()
        mProcessedRequestsCache.clear()
    }
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        getCurrentTab()?.pause() // pause the webview to save battery
    }
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        getCurrentTab()?.resume() // resume the webview
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings(webView: WebView) {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = false // don't allow webview to access file://
                cacheMode = WebSettings.LOAD_DEFAULT
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // allow mix content for some old web pages
                setSupportMultipleWindows(true)
                setSupportZoom(true)
                setLayerType(View.LAYER_TYPE_HARDWARE, null) // hardware accel
            }
            setOnLongClickListener { view ->
                val hitTestResult = (view as WebView).hitTestResult
                when (hitTestResult.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE,
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                    WebView.HitTestResult.IMAGE_TYPE -> { // You can copy link url by long press it
                        val url = hitTestResult.extra
                        if (!url.isNullOrEmpty()) {
                            showLinkCopyDialog(url)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                showDownloadDialog(url, Utils.extractFileName(url, contentDisposition))
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let {
                        if (schemeSupported(it)) {
                            return false
                        } else {
                            Log.d(TAG, "Didn't load url: not supported url scheme")
                        }
                    }
                    return true
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    mProcessedRequestsCache.clear()
                    url?.let {
                        if (isCurrentWebView(view))
                            mUrlInput.setText(it)
                    }
                }
                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    Log.w(TAG, "Website ssl error: ${error?.primaryError}")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.security_warning))
                        .setMessage(getString(R.string.ssl_dialog_warning, error?.let {
                            when (it.primaryError) {
                                SslError.SSL_UNTRUSTED -> getString(R.string.ssl_untrusted)
                                SslError.SSL_EXPIRED -> getString(R.string.ssl_expired)
                                SslError.SSL_IDMISMATCH -> getString(R.string.ssl_id_mismatch)
                                SslError.SSL_NOTYETVALID -> getString(R.string.ssl_not_yet_valid)
                                SslError.SSL_INVALID -> getString(R.string.ssl_invalid)
                                else -> getString(R.string.ssl_unknown_error, it.primaryError)
                            }
                        } ?: getString(R.string.unknown_error)))
                        .setPositiveButton(getString(R.string.continue_text)) { _, _ ->
                            Log.d(TAG, "User continue when received ssl error")
                            handler?.proceed()
                        }
                        .setNegativeButton(getString(R.string.cancel_button_text)) { _, _ ->
                            Log.d(TAG, "User canceled when received ssl error")
                            handler?.cancel()
                        }
                        .show()
                }
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    if (!mIsMediaDetectionEnabled || request?.method != "GET")
                        return super.shouldInterceptRequest(view, request)
                    val url = request.url.toString()
                    if (mProcessedRequestsCache.contains(url)) {
                        Log.d(TAG, "Request already processed, skipping: $url")
                        return super.shouldInterceptRequest(view, request)
                    }
                    if (isMediaFileByUrl(url)) {
                        Log.d(TAG, "Media file detected by url: $url")
                        if (mProcessedRequestsCache.add(url)) {
                            showDownloadDialog(url, Utils.extractFileName(url, null))
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (isCurrentWebView(view)) {
                        mProgressBar.apply {
                            if (newProgress < 100) {
                                visibility = View.VISIBLE
                                progress = newProgress
                            } else {
                                visibility = View.INVISIBLE
                            }
                        }
                    }
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    title?.let {
                        mTabs.forEach { tab ->
                            if (tab.mWebView == view) {
                                tab.mTitle = it
                                if (isCurrentWebView(view)) {
                                    setTitle(it)
                                }
                                return
                            }
                        }
                    }
                }
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    Log.d(TAG, "Create new tab: target=_blank")
                    val webView = WebView(this@MainActivity)
                    setupWebViewSettings(webView)
                    webView.settings.userAgentString = Utils.getUserAgentString(mCurrentUserAgentType, mDefaultUserAgent)
                    val tab = BrowserTab(mUrl = HOME_URL)
                    tab.mWebView = webView
                    mTabContainer.addView(webView)
                    mTabs.add(tab)
                    switchToTab(mTabs.size - 1)
                    try {
                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                        transport?.webView = webView
                        resultMsg?.sendToTarget()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onCreateWindow", e)
                        return false
                    }
                    return true
                }
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    Log.d(TAG, "onShowCustomView: Fullscreen requested")
                    if (mCustomView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    mCustomView = view
                    mCustomViewCallback = callback
                    val currentTab = getCurrentTab()
                    val webView = currentTab?.mWebView
                    if (webView != null) {
                        mTabContainer.removeView(webView)
                    }
                    view?.let {
                        it.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        mTabContainer.addView(it)
                    }
                    hideAppUI()
                    enterFullScreen()
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                override fun onHideCustomView() {
                    Log.d(TAG, "onHideCustomView: Exiting fullscreen")
                    mCustomView?.let { customView ->
                        mTabContainer.removeView(customView)
                        customView.visibility = View.GONE
                        val currentTab = getCurrentTab()
                        currentTab?.mWebView?.let { webView ->
                            mTabContainer.addView(webView)
                        }
                        mCustomView = null
                        mCustomViewCallback?.onCustomViewHidden()
                        mCustomViewCallback = null
                        showAppUI()
                        exitFullScreen()
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
    }
    private fun enterFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars())
                controller.hide(WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }
    private fun exitFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.show(WindowInsets.Type.statusBars())
                controller.show(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    private fun hideAppUI() {
        mTopBar.visibility = View.GONE
        mNavigationBar.visibility = View.GONE
        mProgressBar.visibility = View.GONE
    }
    private fun showAppUI() {
        mTopBar.visibility = View.VISIBLE
        mNavigationBar.visibility = View.VISIBLE
        mProgressBar.visibility = View.INVISIBLE
    }
    private fun isCurrentWebView(view: WebView?): Boolean {
        if (mCurrentTabIndex < mTabs.size) {
            val currentTab = mTabs[mCurrentTabIndex]
            if (currentTab.mWebView == view) {
                return true
            }
        }
        return false
    }
    private fun setupTabs() {
        mTabsButton.setOnClickListener { view ->
            showTabsPopupMenu(view)
        }
    }
    private fun showTabsPopupMenu(view: View) {
        Log.d(TAG, "Show tabs popup menu")
        if (mTabs.isEmpty())
            return
        val popupMenu = PopupMenu(this, view)
        val menu = popupMenu.menu
        mTabs.forEachIndexed { index, tab ->
            val title = tab.mTitle.ifEmpty {
                getString(R.string.new_tab_text)
            }
            menu.add(0, index, index, getString(R.string.tabs_title_text, index + 1, title))
        }
        menu.add(0, -1, mTabs.size, getString(R.string.new_tab_text))
        if (mTabs.size > 1) {
            menu.add(0, -2, mTabs.size + 1, getString(R.string.close_current_tab_text))
        }
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                -1 -> {
                    createNewTab(HOME_URL)
                    true
                }
                -2 -> {
                    closeCurrentTab()
                    true
                }
                else -> {
                    switchToTab(menuItem.itemId)
                    true
                }
            }
        }
        popupMenu.show()
    }
    private fun createNewTab(url: String): BrowserTab {
        Log.d(TAG, "Create new tab with url: $url")
        val tab = BrowserTab(mUrl = url)
        val webView = tab.createWebView(this)
        setupWebViewSettings(webView)
        webView.settings.userAgentString = Utils.getUserAgentString(mCurrentUserAgentType, mDefaultUserAgent)
        mTabContainer.addView(webView)
        mTabs.add(tab)
        switchToTab(mTabs.size - 1)
        tab.loadUrl(url)
        return tab
    }
    private fun switchToTab(index: Int) {
        Log.d(TAG, "Switch to tab $index")
        if (index < 0 || index >= mTabs.size)
            return
        mTabs.forEach {
            it.setActive(false)
        }
        mCurrentTabIndex = index
        val currentTab = mTabs[index]
        currentTab.setActive(true)
        mUrlInput.setText(currentTab.getCurrentUrl())
        setTitle(currentTab.mTitle)
    }
    private fun updateUrlText(url: String) {
        mUrlInput.setText(url)
    }
    private fun closeCurrentTab() {
        Log.d(TAG, "Close current tab")
        if (mTabs.size <= 1) {
            updateUrlText(HOME_URL)
            mTabs[0].loadUrl(HOME_URL)
            return
        }
        val tab = mTabs.removeAt(mCurrentTabIndex)
        mTabContainer.removeView(tab.mWebView)
        tab.destroy()
        switchToTab(if (mCurrentTabIndex >= mTabs.size)
            mCurrentTabIndex - 1
        else
            mCurrentTabIndex)
    }
    private fun applyUserAgent(type: UserAgentType) {
        Log.d(TAG, "Switch User-Agent to: ${type.type}")
        mCurrentUserAgentType = type
        val ua = Utils.getUserAgentString(type, mDefaultUserAgent)
        mTabs.forEach { tab ->
            tab.mWebView?.settings?.userAgentString = ua
            tab.reload()
        }
    }

    private fun setupUrlBar() {
        mGoButton.setOnClickListener {
            loadUrlFromInput()
        }
        mUrlInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                loadUrlFromInput()
                true
            } else {
                false
            }
        }
        mUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                Utils.hideKeyboard(this, mUrlInput)
            }
        }
    }
    private fun getCurrentTab(): BrowserTab? {
        return mTabs.getOrNull(mCurrentTabIndex)
    }
    private fun setupNavigationBar() {
        mBackButton.setOnClickListener {
            getCurrentTab()?.goBack()
        }

        mForwardButton.setOnClickListener {
            getCurrentTab()?.goForward()
        }

        mRefreshButton.setOnClickListener {
            getCurrentTab()?.reload()
        }

        mHomeButton.setOnClickListener {
            updateUrlText(HOME_URL)
            getCurrentTab()?.loadUrl(HOME_URL)
        }

        mMenuButton.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }
    private fun loadUrlFromInput() {
        var url = mUrlInput.text.toString().trim()
        Log.d(TAG, "Load url from input, url: $url")
        if (url.isEmpty()) {
            Log.d(TAG, "Url empty, return")
            return
        }
        Utils.hideKeyboard(this, mUrlInput)
        mUrlInput.clearFocus()
        val currentTab = getCurrentTab()
        currentTab?.mWebView?.requestFocus()
        if (!schemeSupported(url)) {
            Log.d(TAG, "Scheme not supported, add http: to url")
            url = "http://$url"
        }
        updateUrlText(url)
        currentTab?.loadUrl(url)
    }
    private fun showDownloadDialog(url: String, fileName: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.download_dialog_title))
                .setMessage(getString(R.string.download_dialog_text, fileName, url))
                .setPositiveButton(getString(R.string.download_accept_button_text)) { _, _ ->
                    mDownloader.startDownload(url, fileName)
                }
                .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ ->
                    Log.d(TAG, "Download cancelled")
                    dialog.dismiss()
                }
                .setNeutralButton(getString(R.string.copy_url_button_text)) { _, _ ->
                    Log.d(TAG, "Copy url: $url")
                    Utils.copyTextToClipboard(
                        this,
                        url,
                        getString(R.string.url_copied_text)
                    )
                }
                .setCancelable(true)
                .show()
        }
    }
    private fun schemeSupported(url: String): Boolean {
        return SUPPORTED_URL_SCHEMES.contains(Uri.parse(url).scheme)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                Log.d(TAG, "Key event: back")
                if (getCurrentTab()?.goBack() == true) {
                    true
                } else if (mTabs.size > 1) {
                    closeCurrentTab()
                    true
                } else {
                    finishAndRemoveTask()
                    true
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showPopupMenu(view: View) {
        Log.d(TAG, "Show popup menu")
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.main_menu, popupMenu.menu)
        val menu = popupMenu.menu
        val mediaDetectionItem = menu.findItem(R.id.menu_media_detection)
        mediaDetectionItem?.isChecked = mIsMediaDetectionEnabled
        when (mCurrentUserAgentType) {
            UserAgentType.DEFAULT -> menu.findItem(R.id.ua_default)?.isChecked = true
            UserAgentType.CHROME_DESKTOP -> menu.findItem(R.id.ua_chrome_desktop)?.isChecked = true
            UserAgentType.FIREFOX_DESKTOP -> menu.findItem(R.id.ua_firefox_desktop)?.isChecked = true
            UserAgentType.SAFARI_IOS -> menu.findItem(R.id.ua_safari_ios)?.isChecked = true
        }
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_download -> {
                    showMenuDownloadFileDialog()
                    true
                }

                R.id.menu_media_detection -> {
                    mIsMediaDetectionEnabled = !mIsMediaDetectionEnabled
                    saveMediaDetectionPreference(mIsMediaDetectionEnabled)
                    menuItem.isChecked = mIsMediaDetectionEnabled
                    true
                }
                R.id.ua_default -> {
                    applyUserAgent(UserAgentType.DEFAULT)
                    true
                }
                R.id.ua_chrome_desktop -> {
                    applyUserAgent(UserAgentType.CHROME_DESKTOP)
                    true
                }
                R.id.ua_firefox_desktop -> {
                    applyUserAgent(UserAgentType.FIREFOX_DESKTOP)
                    true
                }
                R.id.ua_safari_ios -> {
                    applyUserAgent(UserAgentType.SAFARI_IOS)
                    true
                }
                R.id.menu_current_user_agent -> {
                    val ua = getCurrentTab()?.mWebView?.settings?.userAgentString ?: "Unknown"
                    DialogManager.showDialogWithTitleTextOK(
                        this,
                        getString(R.string.menu_user_agent_title),
                        ua
                    )
                    true
                }
                R.id.menu_view_source_code -> {
                    Log.d(TAG, "View source code")
                    getCurrentTab()?.let {
                        createNewTab(
                            "view-source:${it.getCurrentUrl()}"
                        )
                    }
                    true
                }
                R.id.menu_about -> {
                    DialogManager.showDialogWithTitleTextOK(
                        this,
                        getString(R.string.menu_about_text),
                        getString(
                            R.string.about_desc,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
                                packageManager.getPackageInfo(
                                    packageName,
                                    PackageManager.PackageInfoFlags.of(0)
                                ).versionName
                            } else {
                                @Suppress("DEPRECATION")
                                packageManager.getPackageInfo(packageName, 0).versionName
                            }
                        )
                    )
                    true
                }
                R.id.menu_exit -> {
                    Log.d(TAG, "Exit app")
                    finishAndRemoveTask()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }
    private fun showMenuDownloadFileDialog() {
        Log.d(TAG, "Show download file dialog by clicking menu")
        val input = EditText(this)
        input.hint = getString(R.string.download_dialog_hint)
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.download_dialog_title))
            .setView(input)
            .setPositiveButton(getString(R.string.download_accept_button_text)) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    val fileName = Utils.extractFileName(url, null)
                    mDownloader.startDownload(url, fileName)
                } else {
                    Log.w(TAG, "Download url empty, return")
                    Toast.makeText(this, getString(R.string.download_failed_msg, ""), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ ->
                dialog.dismiss()
                Log.d(TAG, "Download dialog canceled")
            }
            .setCancelable(true)
            .show()
    }
    private fun showLinkCopyDialog(url: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.link_dialog_title))
            .setMessage(getString(R.string.link_dialog_text, url))
            .setPositiveButton(getString(R.string.copy_url_button_text)) { _, _ ->
                Log.d(TAG, "Copy link url: $url")
                Utils.copyTextToClipboard(
                    this,
                    url,
                    getString(R.string.url_copied_text)
                )
            }
            .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.open_link_text)) { _, _ ->
                Log.d(TAG, "Open link: $url")
                createNewTab(url)
            }
            .setCancelable(true)
            .show()
    }

    private fun isMediaFileByUrl(url: String): Boolean {
        return try {
            val cleanUrl = url.substringBefore('?').substringBefore('#')
            val fileName = cleanUrl.substringAfterLast("/", "")
            if (fileName.isEmpty())
                return false
            val extension = fileName.substringAfterLast(".", "").lowercase()
            extension.isNotEmpty() && MEDIA_EXTENSIONS.contains(extension)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(TAG, "Error checking media file by url")
            false
        }
    }

    private fun loadPreference() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        mIsMediaDetectionEnabled = prefs.getBoolean(KEY_MEDIA_DETECTION, false)
    }

    private fun saveMediaDetectionPreference(enabled: Boolean) {
        Log.d(TAG, "Save media detection option")
        mIsMediaDetectionEnabled = enabled
        val editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        editor.putBoolean(KEY_MEDIA_DETECTION, enabled).apply()
    }
    companion object {
        private const val TAG = "Browser"
        const val HOME_URL = "about:blank"
        private val SUPPORTED_URL_SCHEMES = listOf(
            "http", "https", "about", "javascript",
            "data", "ftp", "file", "view-source"
        )
        private val MEDIA_EXTENSIONS = listOf(
            "m3u8", "mp4", "mp3", "mpeg", "m4a",
            "m4s", "webm", "ogg", "wav", "flv",
            "avi", "mkv", "aac", "flac"
        )
        private const val PREF_NAME = "browser_prefs"
        private const val KEY_MEDIA_DETECTION = "media_detection_enabled"
    }
}
