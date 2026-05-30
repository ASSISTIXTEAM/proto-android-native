package org.assistix.proto.nativeapp.data

import java.io.File

/** Local media waiting for user confirm before upload/send. */
sealed class PendingOutgoingMedia {
    abstract fun files(): List<File>

    fun discard() {
        files().forEach { runCatching { it.delete() } }
    }

    data class Single(
        val file: File,
        val mime: String,
        val displayName: String,
    ) : PendingOutgoingMedia() {
        override fun files(): List<File> = listOf(file)
    }

    data class Album(
        val files: List<File>,
        val mime: String = "image/jpeg",
    ) : PendingOutgoingMedia() {
        override fun files(): List<File> = files
    }

    data class Voice(
        val file: File,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long? = null,
    ) : PendingOutgoingMedia() {
        override fun files(): List<File> = listOf(file)
    }
}
