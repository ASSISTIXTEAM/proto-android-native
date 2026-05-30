package org.assistix.proto.nativeapp.ui



import android.content.ClipData

import android.content.ClipboardManager

import android.content.Context

import android.widget.Toast

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.ContentCopy

import androidx.compose.material.icons.filled.Settings

import androidx.compose.material.icons.filled.Poll

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.HorizontalDivider

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.material3.TopAppBar

import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.derivedStateOf

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.runtime.snapshotFlow

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import coil.compose.AsyncImage

import kotlinx.coroutines.Dispatchers

import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.TextButton
import java.io.File
import org.assistix.proto.nativeapp.R
import org.assistix.proto.nativeapp.data.ChannelPostMeta

import org.assistix.proto.nativeapp.data.ChannelFeedPost

import org.assistix.proto.nativeapp.data.ChannelHit
import org.assistix.proto.nativeapp.data.ConvItem

import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.openChannelMediaViewer

import org.assistix.proto.nativeapp.data.ProtoSessionStore

import org.assistix.proto.nativeapp.ui.l10n.AppLocale

import java.text.SimpleDateFormat

import java.util.Date

import java.util.Locale



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun ChannelFeedScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    conversationId: Int,
    title: String,
    onBack: () -> Unit,
    conversations: org.assistix.proto.nativeapp.data.ProtoConversationRepository? = null,
    onOpenManage: () -> Unit = {},
    onCreatePoll: () -> Unit = {},
) {

    val ctx = LocalContext.current

    val scope = rememberCoroutineScope()

    val token by session.tokenFlow.collectAsState(initial = null)

    val languageCode by AppLocale.currentCode()

    val feedLang =

        remember(languageCode) {

            when (languageCode.lowercase().take(2)) {

                "en", "it", "ru" -> languageCode.lowercase().take(2)

                else -> "en"

            }

        }

    var loading by remember { mutableStateOf(true) }

    var channel by remember { mutableStateOf<ChannelHit?>(null) }

    var posts by remember { mutableStateOf<List<ChannelFeedPost>>(emptyList()) }

    var subscribeBusy by remember { mutableStateOf(false) }

    var myUserId by remember { mutableStateOf(0) }

    var feedSearchOpen by remember { mutableStateOf(false) }

    var feedSearch by remember { mutableStateOf("") }

    var feedSearchApplied by remember { mutableStateOf("") }
    var showShareSheet by remember { mutableStateOf(false) }
    var chatList by remember { mutableStateOf<List<ConvItem>>(emptyList()) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var listRefreshing by remember { mutableStateOf(false) }
    var postDraft by remember { mutableStateOf("") }
    var postBusy by remember { mutableStateOf(false) }
    var postImageUploadId by remember { mutableStateOf<String?>(null) }
    var showOriginalPostIds by remember { mutableStateOf(setOf<Long>()) }
    val listState = rememberLazyListState()
    val swipeState = rememberSwipeRefreshState(listRefreshing)

    LaunchedEffect(conversations) {
        val repo = conversations ?: return@LaunchedEffect
        repo.observeConversations().collect { chatList = it }
    }



    val displayPosts by remember(posts) {

        derivedStateOf { posts.sortedByDescending { it.id } }

    }



    LaunchedEffect(Unit) {

        myUserId = session.userId()

    }



    suspend fun loadFeed(search: String = feedSearchApplied, before: Long = 0, append: Boolean = false) {
        val t = token ?: return
        if (!append) loading = true
        val res =
            withContext(Dispatchers.IO) {
                api.fetchChannelFeed(
                    t,
                    conversationId,
                    feedLang,
                    before = before,
                    searchQuery = search,
                    myUserId = myUserId,
                )
            }
        loading = false
        listRefreshing = false
        loadingMore = false
        if (res != null) {
            channel = res.channel
            hasMore = res.hasMore
            posts =
                if (append) {
                    (posts + res.posts).distinctBy { it.id }.sortedByDescending { it.id }
                } else {
                    res.posts.sortedByDescending { it.id }
                }
            if (!append && search.isBlank() && res.posts.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    api.recordChannelPostViews(t, conversationId, res.posts.map { it.id })
                }
            }
        }
    }

    suspend fun loadMore() {
        if (!hasMore || loadingMore || loading || feedSearchApplied.isNotBlank()) return
        val oldest = posts.minOfOrNull { it.id } ?: return
        loadingMore = true
        loadFeed(before = oldest, append = true)
    }



    LaunchedEffect(conversationId, token, feedLang) {

        feedSearch = ""

        feedSearchApplied = ""

        feedSearchOpen = false

        loadFeed("")

    }



    LaunchedEffect(feedSearch) {

        if (!feedSearchOpen) return@LaunchedEffect

        delay(350)

        val q = feedSearch.trim()

        if (q == feedSearchApplied) return@LaunchedEffect

        feedSearchApplied = q

        loadFeed(q)

    }



    LaunchedEffect(listState, displayPosts, token, hasMore) {
        if (displayPosts.isEmpty() || token.isNullOrBlank()) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            val visible =
                layout.visibleItemsInfo.mapNotNull { info ->
                    val idx = info.index - 1
                    if (idx >= 0 && idx < displayPosts.size) displayPosts[idx].id else null
                }
            last to visible
        }.collect { (lastIndex, ids) ->
            if (ids.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    api.recordChannelPostViews(token!!, conversationId, ids)
                }
            }
            if (lastIndex >= displayPosts.size - 2) {
                loadMore()
            }
        }
    }



    val pickPostImage =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val t = token ?: return@rememberLauncherForActivityResult
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                postBusy = true
                try {
                    val tmp = File.createTempFile("ch_post_", ".jpg", ctx.cacheDir)
                    ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    val id = withContext(Dispatchers.IO) { api.uploadFile(t, tmp, "image/jpeg") }
                    tmp.delete()
                    postImageUploadId = id
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message ?: UiStrings.genericError, Toast.LENGTH_SHORT).show()
                } finally {
                    postBusy = false
                }
            }
        }

    suspend fun publishPost() {
        val t = token ?: return
        val text = postDraft.trim()
        if (text.isEmpty() && postImageUploadId.isNullOrBlank()) return
        postBusy = true
        val ok =
            withContext(Dispatchers.IO) {
                api.publishChannelPost(t, conversationId, text, postImageUploadId)
            }
        postBusy = false
        if (ok) {
            postDraft = ""
            postImageUploadId = null
            loadFeed(feedSearchApplied)
            Toast.makeText(ctx, UiStrings.channelPostPublished, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
        }
    }

    val ch = channel

    val subscribed = ch?.subscribed == true



    Scaffold(

        bottomBar = {
            if (ch?.canPost == true && subscribed) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (!postImageUploadId.isNullOrBlank()) {
                        Text(
                            UiStrings.channelPostImageAttached,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = { pickPostImage.launch("image/*") }, enabled = !postBusy) {
                            Icon(Icons.Default.Image, contentDescription = UiStrings.channelPostAddImage)
                        }
                        OutlinedTextField(
                            value = postDraft,
                            onValueChange = { postDraft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(UiStrings.channelPostPlaceholder) },
                            minLines = 1,
                            maxLines = 6,
                            shape = ProtoShapes.field,
                        )
                        TextButton(
                            onClick = { scope.launch { publishPost() } },
                            enabled = !postBusy && (postDraft.isNotBlank() || !postImageUploadId.isNullOrBlank()),
                        ) {
                            Text(if (postBusy) "…" else UiStrings.channelPostPublish)
                        }
                    }
                }
            }
        },

        topBar = {

            TopAppBar(

                title = {

                    if (feedSearchOpen && subscribed) {

                        OutlinedTextField(

                            value = feedSearch,

                            onValueChange = { feedSearch = it },

                            modifier = Modifier.fillMaxWidth(),

                            placeholder = { Text(UiStrings.channelSearchPostsHint, maxLines = 1) },

                            singleLine = true,

                        )

                    } else {

                        Text(ch?.title ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    }

                },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)

                    }

                },

                actions = {

                    if (ch != null) {
                        IconButton(onClick = { showShareSheet = true }) {
                            Icon(Icons.Default.Share, contentDescription = UiStrings.channelShareTitle)
                        }
                    }

                    if (subscribed) {

                        IconButton(

                            onClick = {

                                feedSearchOpen = !feedSearchOpen

                                if (!feedSearchOpen) {

                                    feedSearch = ""

                                    if (feedSearchApplied.isNotBlank()) {

                                        feedSearchApplied = ""

                                        scope.launch { loadFeed("") }

                                    }

                                }

                            },

                        ) {

                            Icon(

                                if (feedSearchOpen) Icons.Default.Close else Icons.Default.Search,

                                contentDescription = UiStrings.channelSearchPosts,

                            )

                        }

                    }

                    if (ch?.canPost == true) {

                        IconButton(onClick = onCreatePoll) {

                            Icon(Icons.Default.Poll, contentDescription = UiStrings.createPoll)

                        }

                        IconButton(onClick = onOpenManage) {

                            Icon(Icons.Default.Settings, contentDescription = UiStrings.channelEditTitle)

                        }

                    }

                },

                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),

            )

        },

    ) { pad ->

        if (loading && posts.isEmpty()) {

            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {

                CircularProgressIndicator(color = ProtoOrange)

            }

            return@Scaffold

        }



        if (!subscribed && ch != null) {

            Column(

                Modifier

                    .fillMaxSize()

                    .padding(pad)

                    .padding(24.dp),

                horizontalAlignment = Alignment.CenterHorizontally,

                verticalArrangement = Arrangement.Center,

            ) {

                ChannelProfileHeader(ch, api, token, ctx)

                Spacer(Modifier.height(24.dp))

                ChannelSubscribeBar(

                    description = ch.description,

                    subscriberCount = ch.subscriberCount,

                    busy = subscribeBusy,

                    onSubscribe = {

                        val t = token ?: return@ChannelSubscribeBar

                        scope.launch {

                            subscribeBusy = true

                            val ok = withContext(Dispatchers.IO) { api.subscribeChannel(t, conversationId) }

                            subscribeBusy = false

                            if (ok) loadFeed() else Toast.makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT).show()

                        }

                    },

                )

            }

            return@Scaffold

        }



        SwipeRefresh(
            state = swipeState,
            onRefresh = {
                scope.launch {
                    listRefreshing = true
                    hasMore = true
                    loadFeed(feedSearchApplied)
                }
            },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {

            item(key = "header") {

                ch?.let {

                    ChannelProfileHeader(it, api, token, ctx)

                    Spacer(Modifier.height(8.dp))

                }

            }

            if (feedSearchApplied.isNotBlank() && displayPosts.isEmpty() && !loading) {

                item(key = "search-empty") {

                    Text(

                        UiStrings.channelPostsNoResults,

                        modifier = Modifier.fillMaxWidth().padding(32.dp),

                        style = MaterialTheme.typography.bodyLarge,

                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                        textAlign = TextAlign.Center,

                    )

                }

            }

            displayPosts.forEachIndexed { index, post ->

                val day = formatFeedDay(post.createdAt)

                val prevDay =

                    displayPosts.getOrNull(index - 1)?.let { formatFeedDay(it.createdAt) }

                if (index == 0 || day != prevDay) {

                    item(key = "day-$day-$index") {

                        Text(

                            day,

                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),

                            style = MaterialTheme.typography.labelLarge,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            textAlign = TextAlign.Center,

                        )

                    }

                }

                item(key = post.id) {

                    ChannelFeedPostCard(

                        post = post,

                        token = token,

                        api = api,

                        myUserId = myUserId,

                        displayText =
                            if (post.id in showOriginalPostIds || post.translation.isNullOrBlank()) {
                                post.displayText.ifBlank { post.postMeta?.text ?: post.pollMeta?.question.orEmpty() }
                            } else {
                                post.translation!!
                            },

                        showTranslationBadge = feedLang.isNotBlank() && post.translation != null && post.id !in showOriginalPostIds,

                        onToggleTranslation = {
                            showOriginalPostIds =
                                if (post.id in showOriginalPostIds) {
                                    showOriginalPostIds - post.id
                                } else {
                                    showOriginalPostIds + post.id
                                }
                        },

                        onPollVote = { optionIndex ->
                            val t = token ?: return@ChannelFeedPostCard
                            scope.launch {
                                val ok =
                                    withContext(Dispatchers.IO) {
                                        api.pollVote(t, conversationId, post.id, optionIndex)
                                    }
                                if (ok) loadFeed(feedSearchApplied)
                            }
                        },
                        onToggleLike = {
                            val t = token ?: return@ChannelFeedPostCard
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    api.toggleReaction(t, conversationId, post.id, "👍")
                                }
                                loadFeed(feedSearchApplied)
                            }
                        },
                        onOpenMedia = { openChannelMediaViewer(displayPosts, post.id, title) },
                    )

                    Spacer(Modifier.height(12.dp))

                }

            }
            if (loadingMore) {
                item(key = "loading-more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = ProtoOrange)
                    }
                }
            }
        }
        }
    }

    if (showShareSheet && ch != null) {
        ChannelShareSheet(
            channel = ch,
            chats = chatList,
            excludeConversationId = conversationId,
            api = api,
            token = token,
            onDismiss = { showShareSheet = false },
        )
    }

}



@Composable

private fun ChannelProfileHeader(

    ch: ChannelHit,

    api: ProtoApi,

    token: String?,

    ctx: Context,

) {

    val isProto = ch.nick.equals("proto", ignoreCase = true)

    Column(

        Modifier

            .fillMaxWidth()

            .clip(RoundedCornerShape(20.dp))

            .background(MaterialTheme.colorScheme.surface)

            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

    ) {

        Surface(

            modifier = Modifier.size(if (isProto) 80.dp else 88.dp),

            shape = CircleShape,

            color = if (isProto) Color.White else MaterialTheme.colorScheme.primaryContainer,

            shadowElevation = if (isProto) 0.dp else 2.dp,

        ) {

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {

                if (isProto) {

                    AsyncImage(

                        model = R.mipmap.ic_launcher,

                        contentDescription = ch.title,

                        modifier = Modifier.size(56.dp),

                        contentScale = ContentScale.Fit,

                    )

                } else if (ch.avatarUploadId != null && token != null) {

                    AsyncImage(

                        model = api.mediaUrl(ch.avatarUploadId),

                        contentDescription = ch.title,

                        modifier = Modifier.fillMaxSize().clip(CircleShape),

                        contentScale = ContentScale.Crop,

                    )

                } else {

                    Text(

                        ch.title.take(1).uppercase(),

                        style = MaterialTheme.typography.headlineMedium,

                        fontWeight = FontWeight.Bold,

                    )

                }

            }

        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(ch.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            if (ch.verified) {

                Spacer(Modifier.size(6.dp))

                VerifiedBadge(showTooltip = true)

            }

        }

        if (ch.nick.isNotBlank()) {

            val nickLine = "@${ch.nick}"

            Row(

                modifier =

                    Modifier

                        .clip(RoundedCornerShape(8.dp))

                        .clickable {

                            val clip = ClipData.newPlainText("channel_nick", nickLine)

                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)

                                .setPrimaryClip(clip)

                            Toast.makeText(ctx, UiStrings.nickCopied, Toast.LENGTH_SHORT).show()

                        }

                        .padding(horizontal = 8.dp, vertical = 4.dp),

                verticalAlignment = Alignment.CenterVertically,

            ) {

                Text(

                    nickLine,

                    style = MaterialTheme.typography.bodyMedium,

                    color = MaterialTheme.colorScheme.primary,

                    fontWeight = FontWeight.Medium,

                )

                Spacer(Modifier.size(6.dp))

                Icon(

                    Icons.Default.ContentCopy,

                    contentDescription = UiStrings.nickCopied,

                    modifier = Modifier.size(16.dp),

                    tint = MaterialTheme.colorScheme.onSurfaceVariant,

                )

            }

        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

            Text(
                UiStrings.channelSubscribersFmt(ch.subscriberCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (ch.postCount > 0) {
                Text(
                    UiStrings.channelPostsFmt(ch.postCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ch.pollCount > 0) {
                Text(
                    UiStrings.channelPollsFmt(ch.pollCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ch.openCount > 0) {

                Text(

                    UiStrings.channelOpensFmt(ch.openCount),

                    style = MaterialTheme.typography.labelLarge,

                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                )

            }

        }

        if (ch.description.isNotBlank()) {

            Spacer(Modifier.height(10.dp))

            Text(

                ch.description,

                style = MaterialTheme.typography.bodyMedium,

                textAlign = TextAlign.Center,

                color = MaterialTheme.colorScheme.onSurfaceVariant,

            )

        }

    }

}



@Composable

private fun ChannelFeedPostCard(
    post: ChannelFeedPost,
    token: String?,
    api: ProtoApi,
    myUserId: Int,
    displayText: String,
    showTranslationBadge: Boolean,
    onToggleTranslation: () -> Unit = {},
    onPollVote: (Int) -> Unit,
    onToggleLike: () -> Unit,
    onOpenMedia: () -> Unit = {},
) {
    val likeReaction = post.reactions.firstOrNull { it.emoji == "👍" }
    val likeCount = likeReaction?.count ?: 0
    val liked = likeReaction?.mine == true

    Column(

        Modifier

            .fillMaxWidth()

            .padding(horizontal = 12.dp)

            .clip(RoundedCornerShape(14.dp))

            .background(MaterialTheme.colorScheme.surface)

            .padding(horizontal = 16.dp, vertical = 14.dp),

    ) {

        if (post.pollMeta != null) {

            PollBubble(poll = post.pollMeta, myUserId = myUserId, onVote = onPollVote)

        } else {
            val meta = post.postMeta
            val hasImage =
                meta != null &&
                    (!meta.imageUrl.isNullOrBlank() || (meta.imageUploadId != null && !token.isNullOrBlank()))
            if (hasImage && meta != null) {
                ChannelPostCard(post = meta, token = token, api = api, onOpenMedia = onOpenMedia)
            } else if (displayText.isNotBlank()) {

                LinkifiedMessageText(

                    text = displayText,

                    color = MaterialTheme.colorScheme.onSurface,

                    linkColor = MaterialTheme.colorScheme.primary,

                    onLinkClick = {},

                    highlightMentions = false,

                    mentionColor = MaterialTheme.colorScheme.tertiary,

                    modifier = Modifier.widthIn(max = 520.dp),

                )

            }

        }

        Spacer(Modifier.height(10.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Spacer(Modifier.height(8.dp))

        Row(

            Modifier.fillMaxWidth(),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically,

        ) {

            Text(

                formatFeedTime(post.createdAt),

                style = MaterialTheme.typography.labelSmall,

                color = MaterialTheme.colorScheme.onSurfaceVariant,

            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showTranslationBadge) {
                    Text(
                        UiStrings.channelTranslatedBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onToggleTranslation() },
                    )
                } else if (post.translation != null && post.translation.isNotBlank()) {
                    Text(
                        UiStrings.showOriginal,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onToggleTranslation() },
                    )
                }
                Text(
                    UiStrings.channelViewsFmt(post.viewCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (post.pollMeta == null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onToggleLike,
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (liked) ProtoOrange.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("👍", style = MaterialTheme.typography.labelLarge)
                        if (likeCount > 0) {
                            Text(
                                likeCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (post.reactions.isNotEmpty()) {
                ReactionChipsRow(
                    reactions = post.reactions.filter { it.emoji != "👍" },
                    textColor = MaterialTheme.colorScheme.onSurface,
                    onToggle = { onToggleLike() },
                )
            }
        }
    }
}



private fun formatFeedDay(ts: Long): String {

    val fmt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

    return fmt.format(Date(ts))

}



private fun formatFeedTime(ts: Long): String {

    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    return fmt.format(Date(ts))

}


