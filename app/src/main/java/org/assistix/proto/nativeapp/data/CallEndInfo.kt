package org.assistix.proto.nativeapp.data

data class CallEndInfo(
    val conversationId: Int,
    val peerLabel: String,
    val withVideo: Boolean,
    val incoming: Boolean,
    val answered: Boolean,
    val durationSec: Int,
    /** answered | missed | declined | cancelled */
    val status: String,
)
