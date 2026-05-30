package org.assistix.proto.nativeapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Device-local copy of chat media — survives server relay purge. */
@Entity(tableName = "media_local")
data class MediaLocalEntity(
    @PrimaryKey val uploadId: String,
    val localPath: String,
    val mime: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val savedAt: Long = System.currentTimeMillis(),
)
