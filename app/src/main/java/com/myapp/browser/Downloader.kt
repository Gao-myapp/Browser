package com.myapp.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast

class Downloader(private val mContext: Context) {

    private lateinit var mDownloadManager: DownloadManager
    private var mDownloadId: Long = -1

    fun startDownload(url: String, fileName: String) {
        // under android 10, it needs storage permission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !Utils.hasStoragePermission(mContext)) {
            Log.w(TAG, "Failed to download file: no storage permission")
            Toast.makeText(mContext, R.string.download_storage_permission_error, Toast.LENGTH_LONG).show()
            return
        }
        mDownloadManager = mContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val scheme = Uri.parse(url).scheme
        if (!listOf("http", "https").contains(scheme)) { // fix: java.lang.IllegalArgumentException: Can only download HTTP/HTTPS URIs
            Log.w(TAG, "Download error: not supported scheme: $scheme.")
            Toast.makeText(mContext, mContext.getString(R.string.download_failed_msg, fileName), Toast.LENGTH_SHORT).show()
            return
        }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
            setAllowedOverRoaming(true)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                setVisibleInDownloadsUi(true)
            }
        }

        try {
            mDownloadId = mDownloadManager.enqueue(request)
            Toast.makeText(mContext, mContext.getString(R.string.download_start_msg, fileName), Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Download started: $fileName, ID: $mDownloadId")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(mContext, mContext.getString(R.string.download_failed_msg, fileName), Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Download error")
        }
    }
    companion object {
        private const val TAG = "BrowserDownloader"
    }
}