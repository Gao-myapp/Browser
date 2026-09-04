package com.myapp.browser

import android.app.AlertDialog
import android.content.Context

object DialogManager {
    fun showDialogWithTitleTextOK(context: Context, title: String, msg: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(context.getString(R.string.ok_text)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
}