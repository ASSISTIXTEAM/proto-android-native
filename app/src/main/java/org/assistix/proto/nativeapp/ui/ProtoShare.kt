package org.assistix.proto.nativeapp.ui

import android.content.Context
import android.content.Intent

fun sharePlainText(ctx: Context, subject: String, text: String) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    ctx.startActivity(Intent.createChooser(intent, subject))
}
