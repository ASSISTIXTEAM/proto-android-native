package org.assistix.proto.nativeapp.data

data class MediaViewerItem(
    val uploadId: String? = null,
    val imageUrl: String? = null,
    val mime: String? = null,
    val name: String? = null,
    val kind: String,
    val caption: String = "",
    val messageId: Long = 0L,
) {
    val isVideo: Boolean get() = kind == "video"
    val isImage: Boolean get() = kind == "image"
}

fun MsgItem.toMediaViewerItems(): List<MediaViewerItem> {
    albumMeta?.items?.let { items ->
        return items.map { item ->
            MediaViewerItem(
                uploadId = item.uploadId,
                mime = item.mime,
                name = item.name,
                kind = mediaKindFromMime(item.mime, item.name) ?: "image",
                caption = AlbumMeta.captionFromJson(bodyRaw),
                messageId = id,
            )
        }
    }
    val up = normalizeUploadId(mediaUploadId) ?: return emptyList()
    if (!isGalleryMedia()) return emptyList()
    val kind = mediaKind ?: mediaKindFromMime(mediaMime, mediaName) ?: "image"
    return listOf(
        MediaViewerItem(
            uploadId = up,
            mime = mediaMime,
            name = mediaName,
            kind = kind,
            caption = if (shouldShowMediaCaption()) bodyRaw else "",
            messageId = id,
        ),
    )
}

fun buildChatMediaGallery(messages: List<MsgItem>): List<MediaViewerItem> =
    messages.flatMap { it.toMediaViewerItems() }

fun openChatMediaViewer(
    messages: List<MsgItem>,
    messageId: Long,
    uploadId: String?,
    chatTitle: String,
) {
    val items = buildChatMediaGallery(messages)
    if (items.isEmpty()) return
    val idx =
        items.indexOfFirst { item ->
            item.messageId == messageId &&
                (uploadId.isNullOrBlank() || item.uploadId == uploadId)
        }.coerceAtLeast(0)
    val source = messages.firstOrNull { it.id == messageId }
    ProtoMediaViewerState.open(items, idx, chatTitle, source)
}

fun openSingleMediaViewer(
    item: MediaViewerItem,
    fromLabel: String = "",
    sourceMessage: MsgItem? = null,
) {
    ProtoMediaViewerState.open(listOf(item), 0, fromLabel, sourceMessage)
}

fun buildChannelMediaGallery(posts: List<org.assistix.proto.nativeapp.data.ChannelFeedPost>): List<MediaViewerItem> =
    posts.mapNotNull { post ->
        val meta = post.postMeta ?: return@mapNotNull null
        val hasImg = !meta.imageUrl.isNullOrBlank() || !meta.imageUploadId.isNullOrBlank()
        if (!hasImg) return@mapNotNull null
        MediaViewerItem(
            uploadId = meta.imageUploadId,
            imageUrl = meta.imageUrl,
            mime = "image/jpeg",
            kind = "image",
            caption = meta.text,
            messageId = post.id,
        )
    }

fun openChannelMediaViewer(
    posts: List<org.assistix.proto.nativeapp.data.ChannelFeedPost>,
    postId: Long,
    channelTitle: String,
) {
    val items = buildChannelMediaGallery(posts)
    if (items.isEmpty()) return
    val idx = items.indexOfFirst { it.messageId == postId }.coerceAtLeast(0)
    ProtoMediaViewerState.open(items, idx, channelTitle, null)
}
