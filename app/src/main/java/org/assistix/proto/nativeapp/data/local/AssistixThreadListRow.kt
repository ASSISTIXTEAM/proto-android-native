package org.assistix.proto.nativeapp.data.local

import androidx.room.ColumnInfo

data class AssistixThreadListRow(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "") val lastPreview: String,
)
