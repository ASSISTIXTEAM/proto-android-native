package org.assistix.proto.nativeapp.ui



import android.widget.Toast

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.mutableStateMapOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.ui.platform.LocalContext

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import org.assistix.proto.nativeapp.data.MessageTranslationCache

import org.assistix.proto.nativeapp.data.MsgItem

import org.assistix.proto.nativeapp.data.ProtoApi



class ChatTranslationState {

    val translated = mutableStateMapOf<Long, String>()

    val showOriginal = mutableStateMapOf<Long, Boolean>()

    val translatingAll = androidx.compose.runtime.mutableStateOf(false)

}



@Composable

fun rememberChatTranslationState(): ChatTranslationState = remember { ChatTranslationState() }



fun canTapTranslate(msg: MsgItem): Boolean =

    !msg.mine && !msg.isE2e && msg.messageType == "text" && msg.body.isNotBlank() && msg.id > 0



fun toggleMessageTranslation(msg: MsgItem, state: ChatTranslationState): Boolean {

    if (!canTapTranslate(msg)) return false

    if (state.translated[msg.id] == null) return false

    state.showOriginal[msg.id] = state.showOriginal[msg.id] != true

    return true

}



suspend fun translateMessage(

    api: ProtoApi,

    token: String,

    msg: MsgItem,

    targetLang: String,

    state: ChatTranslationState,

    cache: MessageTranslationCache? = null,

): Boolean {

    if (!canTapTranslate(msg)) return false

    val source = msg.bodyRaw.ifBlank { msg.body }

    val cached = cache?.get(msg.id, targetLang, source)

    if (!cached.isNullOrBlank()) {

        state.translated[msg.id] = cached

        state.showOriginal[msg.id] = false

        return true

    }

    val existing = state.translated[msg.id]

    if (!existing.isNullOrBlank()) {

        state.showOriginal[msg.id] = false

        return true

    }

    val reply = withContext(Dispatchers.IO) { api.assistixTranslate(token, source, targetLang) }

    if (reply.ok && reply.text.isNotBlank()) {

        state.translated[msg.id] = reply.text

        state.showOriginal[msg.id] = false

        cache?.put(msg.id, targetLang, source, reply.text)

        return true

    }

    return false

}



suspend fun translateAllVisible(

    api: ProtoApi,

    token: String,

    messages: List<MsgItem>,

    targetLang: String,

    state: ChatTranslationState,

    cache: MessageTranslationCache? = null,

): Int {

    var count = 0

    val pending = mutableListOf<Pair<Long, String>>()

    for (msg in messages) {

        if (!canTapTranslate(msg)) continue

        if (state.translated[msg.id] != null) continue

        val source = msg.bodyRaw.ifBlank { msg.body }

        val cached = cache?.get(msg.id, targetLang, source)

        if (!cached.isNullOrBlank()) {

            state.translated[msg.id] = cached

            state.showOriginal[msg.id] = false

            count++

        } else {

            pending.add(msg.id to source)

        }

    }

    if (pending.isEmpty()) return count

    val batch = withContext(Dispatchers.IO) { api.assistixTranslateBatch(token, pending, targetLang) }

    batch.forEach { (id, text) ->

        state.translated[id] = text

        state.showOriginal[id] = false

        val source = pending.firstOrNull { it.first == id }?.second

        if (source != null) {

            cache?.put(id, targetLang, source, text)

        }

        count++

    }

    return count

}



@Composable

fun HydrateTranslationsEffect(

    messages: List<MsgItem>,

    languageCode: String,

    state: ChatTranslationState,

    cache: MessageTranslationCache,

) {

    LaunchedEffect(messages.size, languageCode) {

        val hydrated = withContext(Dispatchers.IO) { cache.hydrate(messages, languageCode) }

        hydrated.forEach { (id, text) ->

            if (state.translated[id].isNullOrBlank()) {

                state.translated[id] = text

            }

        }

    }

}



@Composable

fun AutoTranslateEffect(

    enabled: Boolean,

    token: String?,

    api: ProtoApi,

    messages: List<MsgItem>,

    languageCode: String,

    state: ChatTranslationState,

    cache: MessageTranslationCache? = null,

) {

    val scope = rememberCoroutineScope()

    val ctx = LocalContext.current

    LaunchedEffect(enabled, token, messages.size, languageCode) {

        if (!enabled || token.isNullOrBlank()) return@LaunchedEffect

        val t = token

        val n = translateAllVisible(api, t, messages, languageCode, state, cache)

        if (n > 0) {

            Toast.makeText(ctx, UiStrings.translateChatDone.format(n), Toast.LENGTH_SHORT).show()

        }

    }

}



fun displayBody(msg: MsgItem, state: ChatTranslationState): String {

    if (msg.id <= 0) return msg.body

    if (state.showOriginal[msg.id] == true) return msg.body

    return state.translated[msg.id]?.takeIf { it.isNotBlank() } ?: msg.body

}

