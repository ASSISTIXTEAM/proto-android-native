package org.assistix.proto.nativeapp.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.assistix.proto.nativeapp.MainActivity

object ProtoShareIntentParser {
    fun parse(context: Context, intent: Intent): ProtoSharePayload? {
        val action = intent.action ?: return null
        return when (action) {
            Intent.ACTION_SEND -> parseSend(context, intent)
            Intent.ACTION_SEND_MULTIPLE -> parseSendMultiple(intent)
            MainActivity.ACTION_SHARE_TO_CHAT -> parseSend(context, intent)
            else -> null
        }
    }

    private fun parseSend(context: Context, intent: Intent): ProtoSharePayload? {
        val type = intent.type?.lowercase().orEmpty()
        val text =
            sequenceOf(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
            )
                .mapNotNull { it?.trim()?.takeIf { t -> t.isNotEmpty() } }
                .firstOrNull()
        val streamUri = intent.streamUri()
        return when {
            streamUri != null && (type.startsWith("image/") || type.startsWith("video/")) -> {
                ProtoSharePayload(text = text, imageUris = listOf(streamUri))
            }
            !text.isNullOrBlank() -> ProtoSharePayload(text = text)
            streamUri != null -> ProtoSharePayload(imageUris = listOf(streamUri))
            else -> null
        }
    }

    private fun parseSendMultiple(intent: Intent): ProtoSharePayload? {
        val uris =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            } ?: return null
        if (uris.isEmpty()) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        return ProtoSharePayload(text = text, imageUris = uris)
    }

    private fun Intent.streamUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
