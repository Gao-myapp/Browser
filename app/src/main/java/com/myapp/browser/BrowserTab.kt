package com.myapp.browser

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.webkit.WebViewClient

class BrowserTab(
    var mUrl: String = "about:blank",
    var mTitle: String = ""
) {
    var mWebView: WebView? = null
    var mIsActive: Boolean = false

    fun createWebView(context: Context): WebView {
        Log.d(TAG, "Create webview")
        val wv = WebView(context).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        mWebView = wv
        return wv
    }

    fun getCurrentUrl(): String {
        return mWebView?.url ?: mUrl
    }

    fun setActive(active: Boolean) {
        mIsActive = active
        mWebView?.apply {
            if (active) {
                visibility = View.VISIBLE
                onResume()
            } else {
                visibility = View.GONE
                onPause()
            }
        }
    }

    fun destroy() {
        Log.d(TAG, "Destroy tab")
        mWebView?.apply {
            stopLoading()
            try {
                onPause()
            } catch (e: Exception) {
                Log.d(TAG, "Failed to call onPause, maybe it's already called? ${e.message}")
            }
            clearHistory()
            clearCache(true)
            webViewClient = EMPTY_WEB_VIEW_CLIENT
            webChromeClient = null
            setDownloadListener(null)
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            destroy() // WebView.destroy()
        }
        mWebView = null
    }
    fun pause() {
        mWebView?.apply {
            if (mIsActive) {
                onPause()
                Log.d(TAG, "WebView paused")
            }
        }
    }
    fun resume() {
        mWebView?.apply {
            if (mIsActive) {
                onResume()
                Log.d(TAG, "WebView resumed")
            }
        }
    }
    fun loadUrl(url: String) {
        mUrl = url
        mWebView?.loadUrl(url)
    }

    fun reload() {
        mWebView?.reload()
    }

    fun goBack(): Boolean {
        return if (canGoBack()) {
            mWebView?.goBack()
            true
        } else {
            false
        }
    }

    fun goForward(): Boolean {
        return if (canGoForward()) {
            mWebView?.goForward()
            true
        } else {
            false
        }
    }

    fun canGoBack(): Boolean = mWebView?.canGoBack() ?: false
    fun canGoForward(): Boolean = mWebView?.canGoForward() ?: false

    companion object {
        private const val TAG = "BrowserTab"
        private val EMPTY_WEB_VIEW_CLIENT = object : WebViewClient() {}
    }
}