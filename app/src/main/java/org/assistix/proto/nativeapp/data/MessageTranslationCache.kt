package org.assistix.proto.nativeapp.data

import org.assistix.proto.nativeapp.data.local.MessageTranslationEntity
import org.assistix.proto.nativeapp.data.local.ProtoDao
import java.security.MessageDigest

class MessageTranslationCache(private val dao: ProtoDao) {
    fun sourceHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    suspend fun get(messageId: Long, targetLang: String, sourceText: String): String? {
        if (messageId <= 0L || sourceText.isBlank()) return null
        val row = dao.translation(messageId, targetLang) ?: return null
        val hash = sourceHash(sourceText)
        if (row.sourceHash != hash) {
            dao.deleteTranslation(messageId, targetLang)
            return null
        }
        return row.translatedText.takeIf { it.isNotBlank() }
    }

    suspend fun put(messageId: Long, targetLang: String, sourceText: String, translated: String) {
        if (messageId <= 0L || translated.isBlank()) return
        dao.upsertTranslation(
            MessageTranslationEntity(
                messageId = messageId,
                targetLang = targetLang,
                sourceHash = sourceHash(sourceText),
                translatedText = translated,
            ),
        )
    }

    suspend fun hydrate(
        messages: List<MsgItem>,
        targetLang: String,
    ): Map<Long, String> {
        val ids =
            messages
                .filter { !it.mine && !it.isE2e && it.messageType == "text" && it.id > 0 && it.body.isNotBlank() }
                .map { it.id }
        if (ids.isEmpty()) return emptyMap()
        val byId = messages.associateBy { it.id }
        val rows = dao.translationsForMessages(ids, targetLang)
        val out = mutableMapOf<Long, String>()
        for (row in rows) {
            val msg = byId[row.messageId] ?: continue
            val source = msg.bodyRaw.ifBlank { msg.body }
            if (row.sourceHash != sourceHash(source)) {
                dao.deleteTranslation(row.messageId, targetLang)
                continue
            }
            out[row.messageId] = row.translatedText
        }
        return out
    }
}
