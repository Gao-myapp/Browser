package com.myapp.browser
import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.content.ClipboardManager
import android.widget.Toast
import com.myapp.browser.MainActivity.UserAgentType
import java.io.File

object Utils {
    private const val TAG = "BrowserUtils"
    fun hideKeyboard(context: Context, input: EditText) {
        Log.d(TAG, "Hide keyboard")
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }
    fun extractFileName(url: String, contentDisposition: String?): String {
        contentDisposition?.let {
            val fileNameMatch = Regex("filename[^;=\\n]*=((['\"]).*?\\2|[^;\\n]*)").find(it)
            fileNameMatch?.let { match ->
                var fileName = match.groupValues[1].trim()
                if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
                    fileName = fileName.substring(1, fileName.length - 1)
                }
                return fileName
            }
        }
        return try {
            val uri = Uri.parse(url)
            val path = uri.path ?: ""
            val name = File(path).name
            if (name.isNotEmpty() && name.contains(".")) {
                name
            } else {
                "download_${System.currentTimeMillis()}.bin"
            }
        } catch (_: Exception) {
            "download_${System.currentTimeMillis()}.bin"
        }
    }
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else
            true
    }
    fun getUserAgentString(type: UserAgentType, default: String): String {
        return when (type) {
            UserAgentType.DEFAULT -> default
            UserAgentType.CHROME_DESKTOP ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            UserAgentType.FIREFOX_DESKTOP ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0"
            UserAgentType.SAFARI_IOS ->
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        }
    }
    fun copyTextToClipboard(
        context: Context,
        text: String,
        toastMessage: String
    ): Boolean {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText("copied_text", text)
            )
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Copy text $text successfully")
            true
        } catch (e: Exception) {
            Log.d(TAG, "Copy text $text failed")
            e.printStackTrace()
            false
        }
    }
}