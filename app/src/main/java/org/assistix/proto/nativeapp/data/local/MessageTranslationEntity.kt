package org.assistix.proto.nativeapp.data.local

import androidx.room.Entity

@Entity(
    tableName = "message_translations",
    primaryKeys = ["messageId", "targetLang"],
)
data class MessageTranslationEntity(
    val messageId: Long,
    val targetLang: String,
    val sourceHash: String,
    val translatedText: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
