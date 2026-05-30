package org.assistix.proto.nativeapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "assistix_messages",
    foreignKeys = [
        ForeignKey(
            entity = AssistixThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId"), Index("createdAt")],
)
data class AssistixMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val role: String,
    val text: String,
    val createdAt: Long,
)
