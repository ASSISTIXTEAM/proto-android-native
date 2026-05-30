package org.assistix.proto.nativeapp.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.ProtoApplication
import org.assistix.proto.nativeapp.data.AuthResult
import org.assistix.proto.nativeapp.data.ConvItem
import org.assistix.proto.nativeapp.data.ProtoChatLocalPrefs
import org.assistix.proto.nativeapp.data.ProtoActiveChat
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoClientPrefsSync
import org.assistix.proto.nativeapp.data.resolveDisplayName
import org.assistix.proto.nativeapp.data.ProtoEventHub
import org.assistix.proto.nativeapp.data.ProtoTypingHub
import kotlinx.coroutines.flow.first
import org.assistix.proto.nativeapp.data.ProtoAppNavigation
import org.assistix.proto.nativeapp.data.ProtoChatSelectionState
import org.assistix.proto.nativeapp.data.ProtoForwardState
import org.assistix.proto.nativeapp.data.ProtoMediaViewerState
import org.assistix.proto.nativeapp.data.ProtoMediaCompressor
import org.assistix.proto.nativeapp.data.AlbumItem
import org.assistix.proto.nativeapp.data.ProtoSharePayload
import org.assistix.proto.nativeapp.data.ProtoShareShortcuts
import org.assistix.proto.nativeapp.data.ProtoShareState
import org.assistix.proto.nativeapp.data.ProtoEventCursorStore
import org.assistix.proto.nativeapp.data.ProtoRealtimeCatchUp
import org.assistix.proto.nativeapp.data.ProtoRealtimeHub
import org.assistix.proto.nativeapp.data.ProtoUnifiedRealtime
import java.util.Calendar
import org.assistix.proto.nativeapp.data.AccountRestriction
import org.assistix.proto.nativeapp.data.ProtoPendingVerificationStore
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.performProtoLogout
import org.assistix.proto.nativeapp.data.ChannelHit
import org.assistix.proto.nativeapp.data.MessageSearchHit
import org.assistix.proto.nativeapp.data.UserHit

@Composable
fun ProtoNavHost(
    app: ProtoApplication,
    onNeedCallPermissions: (afterGranted: () -> Unit) -> Unit,
) {
    val session = app.session
    val pendingVerification = app.pendingVerification
    val api = app.api
    val messages = app.messages
    val calls = app.calls
    var navEpoch by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var viewingUserId by remember { mutableStateOf<Int?>(null) }
    val authToken by session.tokenFlow.collectAsState(initial = null)
    val sessionUserId by session.userIdFlow.collectAsState(initial = 0)
    var bootstrapComplete by remember { mutableStateOf(false) }
    var onboardingDone by remember { mutableStateOf(false) }
    var seededToken by remember { mutableStateOf<String?>(null) }
    /** Мгновенно скрывает «home» до обновления DataStore после [performProtoLogout]. */
    var forcedSignedOut by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var pendingBoot by remember { mutableStateOf<ProtoPendingVerificationStore.Pending?>(null) }
    var pendingLive by remember { mutableStateOf<ProtoPendingVerificationStore.Pending?>(null) }
    LaunchedEffect(app) {
        onboardingDone =
            runCatching { app.prefs.onboardingComplete.first() }.getOrElse { false }
        seededToken = runCatching { session.token() }.getOrNull()
        val loaded = runCatching { pendingVerification.load() }.getOrNull()
        pendingBoot = loaded
        pendingLive = loaded
        bootstrapComplete = true
    }

    LaunchedEffect(authToken) {
        if (authToken.isNullOrBlank()) {
            seededToken = null
            if (forcedSignedOut) {
                forcedSignedOut = false
            }
        }
    }

    if (!bootstrapComplete) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    val effectivePending = pendingLive ?: pendingBoot
    val resolvedToken =
        when {
            forcedSignedOut -> null
            effectivePending != null -> null
            else -> authToken ?: seededToken
        }
    val hasSession = !resolvedToken.isNullOrBlank()

    val nav =
        key(hasSession, navEpoch) {
            rememberNavController()
        }

    fun openEmailVerification(userId: Int, emailHint: String, serverMessage: String) {
        scope.launch {
            if (userId < 1) return@launch
            session.clear()
            seededToken = null
            pendingVerification.save(userId, emailHint, serverMessage)
            val pending = ProtoPendingVerificationStore.Pending(userId, emailHint, serverMessage)
            pendingLive = pending
            val route = "verify-email/$userId"
            nav.navigate(route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val pendingStart = effectivePending?.takeIf { it.userId > 0 }
    val startRoute =
        when {
            pendingStart != null -> "verify-email/${pendingStart.userId}"
            hasSession -> "home"
            !onboardingDone -> "onboarding"
            else -> "auth-hub"
        }

    var accountRestriction by remember { mutableStateOf<AccountRestriction?>(null) }
    LaunchedEffect(resolvedToken) {
        val t = resolvedToken
        if (t.isNullOrBlank()) {
            accountRestriction = null
            return@LaunchedEffect
        }
        val load = withContext(Dispatchers.IO) { app.profileCache.loadMe(t) }
        accountRestriction = load?.restriction?.takeIf { it.isActive }
    }

    val ctx = LocalContext.current
    val eventCursor = remember(ctx) { ProtoEventCursorStore(ctx) }
    val realtimeHandlers =
        remember(session, app, api, calls) {
            ProtoUnifiedRealtime.Handlers(
                onChatEvent = { ProtoEventHub.bump() },
                onTyping = { cid, uid ->
                    ProtoTypingHub.update(cid, ProtoTypingHub.userIds + uid)
                },
                onPeerRead = { cid, through, readAt, readerId ->
                    app.applicationScope.launch(Dispatchers.IO) {
                        if (readerId != session.userId() && cid == ProtoActiveChat.conversationId) {
                            app.messages.applyPeerRead(cid, through, readAt)
                        }
                    }
                },
                onWebrtc = { cid ->
                    calls.prioritizeIncomingPoll(cid)
                },
                onPin = { _, _ -> ProtoEventHub.bump() },
            )
        }
    val realtime = remember(ctx, sessionUserId) {
        ProtoRealtimeHub(context = ctx, userIdProvider = { sessionUserId }) { raw ->
            ProtoUnifiedRealtime.dispatch(raw, realtimeHandlers)
            when (raw.optString("type")) {
                "media_relay_request" -> {
                    val data = raw.optJSONObject("data") ?: return@ProtoRealtimeHub
                    val uploadId = data.optString("upload_id", "").trim()
                    val cid = data.optInt("conversation_id", 0)
                    val requester = data.optInt("requester_id", 0)
                    if (uploadId.isBlank() || cid <= 0 || requester == sessionUserId) return@ProtoRealtimeHub
                    app.applicationScope.launch(Dispatchers.IO) {
                        val t = session.token() ?: return@launch
                        app.mediaResolver.relayIfLocal(t, uploadId, cid)
                    }
                    return@ProtoRealtimeHub
                }
                "media_relay_ready" -> {
                    ProtoEventHub.bump()
                    return@ProtoRealtimeHub
                }
                "cell_blob_published", "cell_repair_request" -> {
                    val data = raw.optJSONObject("data") ?: return@ProtoRealtimeHub
                    app.applicationScope.launch(Dispatchers.IO) {
                        val t = session.token() ?: return@launch
                        app.cellsManager.syncMyHolds(t)
                        if (raw.optString("type") == "cell_repair_request") {
                            val blobId = data.optString("blob_id", "")
                            val cid = data.optInt("conversation_id", 0)
                            val missing = data.optJSONArray("missing_indices") ?: return@launch
                            val row = app.messages.findLocalMedia(blobId) ?: return@launch
                            val indices = (0 until missing.length()).mapNotNull { missing.optInt(it).takeIf { i -> i >= 0 } }
                            app.cellsManager.repairFromLocal(
                                t,
                                blobId,
                                cid,
                                File(row.localPath),
                                row.mime,
                                indices,
                            )
                        }
                    }
                    ProtoEventHub.bump()
                    return@ProtoRealtimeHub
                }
            }
            if (raw.optString("type") != "message") return@ProtoRealtimeHub
            if (ProtoRealtimeCatchUp.active) return@ProtoRealtimeHub
            val data = raw.optJSONObject("data") ?: return@ProtoRealtimeHub
            val cid = data.optInt("conversation_id", 0)
            if (cid <= 0 || cid == ProtoActiveChat.conversationId) return@ProtoRealtimeHub
            val messageId = data.optLong("message_id", 0)
            val senderId = data.optInt("sender_id", 0)
            val preview = data.optString("preview", UiStrings.newMessage)
            var senderName = data.optString("sender_name", "").trim().takeIf { it.isNotBlank() }
            val avatarRaw = data.optString("sender_avatar_upload_id", "")
            val avatarId =
                avatarRaw.takeIf { it.isNotBlank() && !avatarRaw.equals("null", ignoreCase = true) }
            app.applicationScope.launch(Dispatchers.IO) {
                val t = session.token() ?: return@launch
                if (senderId > 0 && senderId == sessionUserId) return@launch
                if (messageId > 0) {
                    val lastRead = app.conversations.myLastReadMessageId(cid)
                    if (messageId <= lastRead) return@launch
                }
                if (app.calls.state.value.active) return@launch
                val muted = app.chatLocalPrefs.mutedIds.first()
                if (cid in muted) return@launch
                if (data.optString("conversation_kind") == "group" && app.prefs.notifyMentionsOnly.first()) {
                    val mentions = data.optJSONArray("mention_user_ids")
                    var mentioned = false
                    if (mentions != null) {
                        for (i in 0 until mentions.length()) {
                            if (mentions.optInt(i) == sessionUserId) {
                                mentioned = true
                                break
                            }
                        }
                    }
                    if (!mentioned) return@launch
                }
                if (!app.prefs.messageNotifications.first()) return@launch
                if (senderName.isNullOrBlank() && senderId > 0) {
                    val u = api.userById(t, senderId)
                    senderName =
                        u?.displayName?.takeIf { it.isNotBlank() }
                            ?: u?.nick?.takeIf { it.isNotBlank() }
                }
                val title = senderName?.takeIf { it.isNotBlank() } ?: UiStrings.newMessage
                val avatar = avatarId?.let { id -> app.notifier.loadAvatarBitmap(api, t, id) }
                app.notifier.notifyMessage(title, preview, cid, avatar)
            }
        }
    }

    LaunchedEffect(resolvedToken, onboardingDone) {
        if (resolvedToken.isNullOrBlank() && onboardingDone) {
            val route = nav.currentBackStackEntry?.destination?.route.orEmpty()
            val onVerifyEmail = route.startsWith("verify-email")
            if (
                route.isNotBlank() &&
                route != "auth-hub" &&
                route != "onboarding" &&
                !onVerifyEmail
            ) {
                nav.navigate("auth-hub") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(resolvedToken, effectivePending?.userId) {
        val t = resolvedToken
        if (t.isNullOrBlank() || effectivePending != null) {
            realtime.stop()
            calls.clearSession()
            return@LaunchedEffect
        }
        val uid = withContext(Dispatchers.IO) { session.userId() }
        calls.bindSession(t, uid)
        ProtoRealtimeCatchUp.active = true
        withContext(Dispatchers.IO) {
            app.conversations.syncFromServer(t)
            val pushTok = org.assistix.proto.nativeapp.data.ProtoPushToken.fetch()
            api.registerPush(t, pushTok ?: "proto-android-offline-${session.userId()}")
        }
        realtime.start(t)
        delay(12_000)
        ProtoRealtimeCatchUp.active = false
        try {
            awaitCancellation()
        } finally {
            realtime.stop()
        }
    }

    fun leaveOnboarding() {
        onboardingDone = true
        scope.launch { app.prefs.setOnboardingComplete() }
        app.stt.startBackgroundPackDownload(app.applicationScope, delayMs = 15_000L)
        scope.launch {
            kotlinx.coroutines.delay(18_000)
            runCatching { app.stt.warmupModel() }
        }
        if (!nav.popBackStack()) {
            nav.navigate("auth-hub") {
                popUpTo("onboarding") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val openOnboarding = {
        nav.navigate("onboarding") { launchSingleTop = true }
    }

    fun performSignOut() {
        val tokenSnapshot = resolvedToken
        signingOut = true
        forcedSignedOut = true
        seededToken = null
        accountRestriction = null
        pendingLive = null
        pendingBoot = null
        viewingUserId = null
        navEpoch++
        ProtoAppNavigation.clearPending()
        ProtoForwardState.clear()
        ProtoMediaViewerState.close()
        ProtoShareState.clear()
        ProtoChatSelectionState.active = false
        ProtoActiveChat.conversationId = 0
        scope.launch {
            try {
                performProtoLogout(
                    tokenSnapshot,
                    api,
                    realtime,
                    calls,
                    messages,
                    app.notifier,
                    session,
                    pendingVerification,
                    app.conversations,
                    eventCursor,
                    app.applicationContext,
                )
            } finally {
                signingOut = false
            }
        }
    }

    val activeRestriction = accountRestriction?.takeIf { it.isActive }
    if (hasSession && activeRestriction != null) {
        SuspensionScreen(activeRestriction, onLogout = { performSignOut() })
        return
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = startRoute) {
            composable("onboarding") {
                OnboardingFlow(
                    prefs = app.prefs,
                    themeStore = app.themeStore,
                    stt = app.stt,
                    appScope = app.applicationScope,
                    onFinished = { leaveOnboarding() },
                    onSkip = { leaveOnboarding() },
                )
            }
            composable("auth-hub") {
                AuthAccountHubScreen(
                    onCreateAccount = { nav.navigate("register-wizard") },
                    onSignIn = { nav.navigate("login") },
                    onShowOnboarding = openOnboarding,
                )
            }
            composable("post-registration-tour") {
                PostRegistrationTourScreen(
                    onFinished = {
                        scope.launch {
                            app.prefs.setPostRegistrationTourDone()
                            nav.navigate("home") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable("register-wizard") {
                RegisterAccountWizard(
                    api = api,
                    session = session,
                    prefs = app.prefs,
                    onBack = { nav.popBackStack() },
                    onRegisteredAndVerified = {
                        scope.launch {
                            pendingVerification.clear()
                            pendingLive = null
                            val tourDone = app.prefs.isPostRegistrationTourDone()
                            if (tourDone) {
                                nav.navigate("home") {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                nav.navigate("post-registration-tour") {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onPendingEmailVerification = { userId, emailHint, serverMessage ->
                        openEmailVerification(userId, emailHint, serverMessage)
                    },
                )
            }
            composable("login") {
                AuthFormScreen(
                    title = UiStrings.signInTitle,
                    submitLabel = UiStrings.signIn,
                    showEmail = false,
                    showForgotPassword = true,
                    onBack = { nav.popBackStack() },
                    onOpenOnboarding = openOnboarding,
                    onForgotPassword = { loginHint ->
                        nav.currentBackStackEntry?.savedStateHandle?.set("loginHint", loginHint)
                        nav.navigate("forgot-password")
                    },
                    onSubmit = { login, pass, _, _, setError ->
                        when (val r = withContext(Dispatchers.IO) { api.login(login, pass) }) {
                            is AuthResult.Ok -> {
                                session.save(r.token, r.userId, r.nick)
                                nav.navigate("home") { popUpTo("auth-hub") { inclusive = true } }
                            }
                            is AuthResult.PendingEmailVerification -> {
                                openEmailVerification(r.userId, r.emailHint, r.message)
                            }
                            is AuthResult.Fail -> setError(r.message)
                            else -> setError(UiStrings.genericError)
                        }
                    },
                )
            }
            composable("register") {
                AuthFormScreen(
                    title = UiStrings.registerTitle,
                    submitLabel = UiStrings.createAccount,
                    showEmail = true,
                    emailRequired = true,
                    showDisplayName = true,
                    showPolicyConsent = true,
                    onBack = { nav.popBackStack() },
                    onOpenOnboarding = openOnboarding,
                    onSubmit = { nick, pass, email, displayName, setError ->
                        if (displayName.trim().isEmpty()) {
                            setError(UiStrings.displayNameRequired)
                            return@AuthFormScreen
                        }
                        if (email.trim().isEmpty()) {
                            setError(UiStrings.emailRequired)
                            return@AuthFormScreen
                        }
                        val name = resolveDisplayName(displayName, nick)
                        when (
                            val r =
                                withContext(Dispatchers.IO) {
                                    api.register(nick, pass, email, name, true, "")
                                }
                        ) {
                            is AuthResult.PendingEmailVerification -> {
                                openEmailVerification(r.userId, r.emailHint.ifBlank { email }, r.message)
                            }
                            is AuthResult.Ok -> setError(UiStrings.verifyEmailRequiredBanner)
                            is AuthResult.Fail -> setError(r.message)
                            else -> setError(UiStrings.genericError)
                        }
                    },
                )
            }
            composable(
                route = "verify-email/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.IntType }),
            ) { entry ->
                val userId = entry.arguments?.getInt("userId") ?: 0
                var emailHint by remember { mutableStateOf("") }
                var verifyInfo by remember { mutableStateOf("") }
                LaunchedEffect(userId) {
                    val p = pendingVerification.load()
                    if (p != null && (userId < 1 || p.userId == userId)) {
                        emailHint = p.emailHint
                        verifyInfo = p.message
                    }
                }
                VerifyEmailScreen(
                    userId = userId,
                    emailHint = emailHint,
                    serverMessage = verifyInfo,
                    api = api,
                    session = session,
                    onVerified = {
                        scope.launch {
                            pendingVerification.clear()
                            pendingLive = null
                            seededToken = null
                        }
                        nav.navigate("home") {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        scope.launch {
                            pendingVerification.clear()
                            pendingLive = null
                            seededToken = null
                        }
                        nav.navigate("auth-hub") {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable("forgot-password") {
                val loginHint =
                    nav.previousBackStackEntry?.savedStateHandle?.get<String>("loginHint")
                        ?: nav.currentBackStackEntry?.savedStateHandle?.get<String>("loginHint")
                        ?: ""
                ForgotPasswordScreen(
                    api = api,
                    initialLogin = loginHint,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("home") {
                val homeCtx = LocalContext.current
                var tab by remember { mutableStateOf(MainTab.Chats) }
                LaunchedEffect(ProtoShareState.active) {
                    if (ProtoShareState.active) {
                        tab = MainTab.Chats
                        val route = nav.currentBackStackEntry?.destination?.route
                        if (route?.startsWith("chat/") == true) {
                            nav.popBackStack()
                        }
                    }
                }
                var chatsSearchOpen by remember { mutableStateOf(false) }
                var chatsSearch by remember { mutableStateOf("") }
                var chatsReloadTick by remember { mutableStateOf(0) }
                var assistixHomeReset by remember { mutableIntStateOf(0) }
                val haptic = ProtoHaptics.rememberSender()
                val reduceMotion by app.prefs.reduceMotionEnabled.collectAsState(initial = false)
                val textScale by app.prefs.textSizeScale.collectAsState(initial = 1f)
                val languageCode by app.prefs.languageCodeFlow.collectAsState(initial = "en")
                var showWhatsNew115 by remember { mutableStateOf(false) }
                LaunchedEffect(authToken) {
                    if (!authToken.isNullOrBlank() && !app.prefs.hasSeenWhatsNew115()) {
                        showWhatsNew115 = true
                    }
                }
                if (showWhatsNew115) {
                    ProtoWhatsNewDialog(
                        title = UiStrings.whatsNew115Title,
                        bullets = UiStrings.whatsNew115Bullets,
                        onDismiss = {
                            scope.launch {
                                app.prefs.setSeenWhatsNew115()
                                showWhatsNew115 = false
                            }
                        },
                    )
                }
                var showProtoSubscribeDialog by remember { mutableStateOf(false) }
                var protoOfficialChannel by remember { mutableStateOf<ChannelHit?>(null) }
                var protoSubscribeBusy by remember { mutableStateOf(false) }
                var protoChannelSubscribed by remember { mutableStateOf(true) }
                LaunchedEffect(authToken, chatsReloadTick) {
                    val t = authToken ?: return@LaunchedEffect
                    val official = withContext(Dispatchers.IO) { api.channelByNick(t, "proto") }
                    if (official != null && !official.subscribed) {
                        withContext(Dispatchers.IO) { api.subscribeChannel(t, official.conversationId) }
                        app.conversations.syncFromServer(t)
                    }
                    protoChannelSubscribed = official?.subscribed != false || official != null
                    app.prefs.setProtoChannelSubscribePromptDone()
                    protoOfficialChannel = official
                    val profileNick = org.assistix.proto.nativeapp.data.ProtoAppNavigation.consumeProfileNick()
                    if (profileNick != null) {
                        tab = MainTab.Chats
                        val hits = withContext(Dispatchers.IO) { api.searchUsers(t, profileNick) }
                        val user = hits.firstOrNull { it.nick.equals(profileNick, ignoreCase = true) } ?: hits.firstOrNull()
                        if (user != null) {
                            val cid = withContext(Dispatchers.IO) { api.startDm(t, user.id) }
                            if (cid != null) {
                                val title = resolveDisplayName(user.displayName, user.nick)
                                val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                                nav.navigate("chat/$cid/$enc/dm/${user.id}") { launchSingleTop = true }
                                return@LaunchedEffect
                            }
                        }
                    }
                    val deviceLink = org.assistix.proto.nativeapp.data.ProtoAppNavigation.consumeDeviceLink()
                    if (deviceLink != null) {
                        tab = MainTab.Chats
                        val (pairId, secret) = deviceLink
                        val (ok, _) = withContext(Dispatchers.IO) { api.approveDeviceLink(t, pairId, secret) }
                        if (ok) {
                            android.widget.Toast.makeText(homeCtx, UiStrings.linkWebApproved, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(homeCtx, UiStrings.linkQrInvalid, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return@LaunchedEffect
                    }
                    if (org.assistix.proto.nativeapp.data.ProtoAppNavigation.consumeOpenQrScan()) {
                        tab = MainTab.Chats
                        nav.navigate("qr_scan") { launchSingleTop = true }
                        return@LaunchedEffect
                    }
                    val channelOpen = org.assistix.proto.nativeapp.data.ProtoAppNavigation.consumeChannel()
                    if (channelOpen != null) {
                        val (chNick, autoSub) = channelOpen
                        tab = MainTab.Chats
                        val ch = withContext(Dispatchers.IO) { api.channelByNick(t, chNick) }
                        if (ch != null && ch.conversationId > 0) {
                            if (autoSub && !ch.subscribed) {
                                withContext(Dispatchers.IO) { api.subscribeChannel(t, ch.conversationId) }
                                app.conversations.syncFromServer(t)
                            }
                            val enc = URLEncoder.encode(ch.title.ifBlank { ch.nick }, StandardCharsets.UTF_8.name())
                            nav.navigate("channel_feed/${ch.conversationId}/$enc") { launchSingleTop = true }
                            return@LaunchedEffect
                        }
                    }
                    val invite = org.assistix.proto.nativeapp.data.ProtoAppNavigation.consumeInviteCode()
                    if (invite != null) {
                        val joined =
                            withContext(Dispatchers.IO) { api.joinPublicLink(t, invite) }
                        if (joined != null && joined.conversationId > 0) {
                            tab = MainTab.Chats
                            withContext(Dispatchers.IO) { app.conversations.syncFromServer(t) }
                            val chats = app.conversations.observeConversations().first()
                            val title =
                                chats.firstOrNull { it.id == joined.conversationId }?.title?.takeIf { it.isNotBlank() }
                                    ?: UiStrings.chatDefault
                            val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                            Toast.makeText(homeCtx, UiStrings.inviteJoined, Toast.LENGTH_SHORT).show()
                            nav.navigate(
                                "chat/${joined.conversationId}/$enc/${joined.kind}/${joined.peerUserId}",
                            ) {
                                launchSingleTop = true
                            }
                            return@LaunchedEffect
                        }
                    }
                    val req = org.assistix.proto.nativeapp.data.ProtoAppNavigation.consumeOpenChat() ?: return@LaunchedEffect
                    tab = MainTab.Chats
                    val vaultIds = withContext(Dispatchers.IO) { app.chatLocalPrefs.vaultIds.first() }
                    val vaultUnlocked = withContext(Dispatchers.IO) { app.chatLocalPrefs.vaultUnlocked.first() }
                    if (req.conversationId in vaultIds && !vaultUnlocked) {
                        org.assistix.proto.nativeapp.data.ProtoAppNavigation.pendingVaultGate = req
                        return@LaunchedEffect
                    }
                    val enc = URLEncoder.encode(req.title, StandardCharsets.UTF_8.name())
                    nav.navigate("chat/${req.conversationId}/$enc/${req.kind}/${req.peerUserId}")
                }
                val density = LocalDensity.current
                val topTitle =
                    when (tab) {
                        MainTab.Settings -> UiStrings.settings
                        MainTab.Chats -> UiStrings.chats
                        MainTab.Assistix -> UiStrings.assistixAi
                        MainTab.Profile -> UiStrings.profile
                    }
                val hideChatsAppTopBar =
                    tab == MainTab.Profile ||
                        (
                            tab == MainTab.Chats &&
                                (
                                    ProtoForwardState.active ||
                                        ProtoShareState.active ||
                                        org.assistix.proto.nativeapp.data.ProtoChatSelectionState.active
                                )
                        )
                val languageUiKey by org.assistix.proto.nativeapp.ui.l10n.AppLocale.currentCode()
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = textScale.coerceIn(0.85f, 1.25f)),
                ) {
                Scaffold(
                    topBar = {
                        if (!hideChatsAppTopBar) {
                        AnimatedContent(
                            targetState = topTitle,
                            transitionSpec = {
                                fadeIn(ProtoMotion.fade(reduceMotion, 280)) togetherWith fadeOut(ProtoMotion.fade(reduceMotion, 180))
                            },
                            label = "settingsTitle",
                        ) { title ->
                            ProtoAppTopBar(
                                title = title,
                                showSearch = tab == MainTab.Chats,
                                searchExpanded = chatsSearchOpen,
                                searchQuery = chatsSearch,
                                onSearchToggle = {
                                    chatsSearchOpen = !chatsSearchOpen
                                    if (!chatsSearchOpen) chatsSearch = ""
                                },
                                onSearchQueryChange = { chatsSearch = it },
                                actions = {
                                    if (tab == MainTab.Chats) {
                                        IconButton(
                                            onClick = {
                                                haptic(HapticKind.Tap)
                                                chatsReloadTick++
                                            },
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = UiStrings.refreshChats)
                                        }
                                    }
                                },
                            )
                        }
                        }
                    },
                    bottomBar = { ProtoBottomBar(selected = tab, onSelect = { tab = it }) },
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            ProtoConnectivityBanner(advisor = app.connectivity)
                        AnimatedContent(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            targetState = tab,
                            transitionSpec = {
                                fadeIn(ProtoMotion.fade(reduceMotion, 320)) togetherWith fadeOut(ProtoMotion.fade(reduceMotion, 200))
                            },
                            label = "mainTab",
                        ) { current ->
                        Box(Modifier.fillMaxSize()) {
                        androidx.compose.runtime.key(languageUiKey) {
                        when (current) {
                            MainTab.Settings ->
                                SettingsTab(
                                    session = session,
                                    themeStore = app.themeStore,
                                    prefs = app.prefs,
                                    stt = app.stt,
                                    updateManager = app.appUpdate,
                                    onOpenDevices = { nav.navigate("devices") },
                                    onOpenCache = { nav.navigate("cache_settings") },
                                    onOpenDataStorage = { nav.navigate("data_storage") },
                                    onOpenCells = { nav.navigate("proto_cells") },
                                    onOpenOnboarding = openOnboarding,
                                    onOpenQrHub = { nav.navigate("qr_hub") },
                                    onLogout = { performSignOut() },
                                )
                            MainTab.Chats ->
                                ChatListScreen(
                                    session = session,
                                    api = api,
                                    conversations = app.conversations,
                                    network = app.network,
                                    authToken = authToken,
                                    chatLocalPrefs = app.chatLocalPrefs,
                                    draftPrefs = app.draftPrefs,
                                    search = chatsSearch,
                                    searchActive = chatsSearchOpen && chatsSearch.isNotBlank(),
                                    reloadTick = chatsReloadTick,
                                    showProtoSubscribeBanner = !protoChannelSubscribed,
                                    protoSubscribeBusy = protoSubscribeBusy,
                                    onSubscribeProto = {
                                        scope.launch {
                                            val t = authToken ?: return@launch
                                            val official =
                                                protoOfficialChannel
                                                    ?: withContext(Dispatchers.IO) { api.channelByNick(t, "proto") }
                                                    ?: return@launch
                                            protoSubscribeBusy = true
                                            val ok =
                                                withContext(Dispatchers.IO) {
                                                    api.subscribeChannel(t, official.conversationId)
                                                }
                                            protoSubscribeBusy = false
                                            if (ok) {
                                                app.prefs.setProtoChannelSubscribePromptDone()
                                                protoChannelSubscribed = true
                                                showProtoSubscribeDialog = false
                                                withContext(Dispatchers.IO) { app.conversations.syncFromServer(t) }
                                                chatsReloadTick++
                                            } else {
                                                Toast.makeText(homeCtx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onOpenProtoChannel = {
                                        scope.launch {
                                            val t = authToken ?: return@launch
                                            val ch =
                                                withContext(Dispatchers.IO) { api.channelByNick(t, "proto") }
                                                    ?: return@launch
                                            val enc =
                                                URLEncoder.encode(
                                                    ch.title.ifBlank { ch.nick },
                                                    StandardCharsets.UTF_8.name(),
                                                )
                                            nav.navigate("channel_feed/${ch.conversationId}/$enc") {
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onStartDmSearch = { nav.navigate("new_dm") },
                                    onNewGroup = { nav.navigate("group_create") },
                                    onNewChannel = { nav.navigate("channel_create") },
                                    onRefreshChats = { chatsReloadTick++ },
                                    onOpenAssistix = { tab = MainTab.Assistix },
                                    onOpenQrHub = { nav.navigate("qr_hub") },
                                    onScanQr = { nav.navigate("qr_scan") },
                                    onOpenDevices = { nav.navigate("devices") },
                                    onCreatePollInGroup = { cid, title ->
                                        val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                                        nav.navigate("poll_create/$cid/$enc")
                                    },
                                    onGroupCall = { cid, title ->
                                        val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                                        nav.navigate("chat/$cid/$enc/group/0")
                                        scope.launch {
                                            val t = session.token() ?: return@launch
                                            calls.startGroupCall(t, cid, title)
                                        }
                                    },
                                    onOpenChat = { id, title, kind, peerId ->
                                        navigateToConversation(nav, id, title, kind, peerId)
                                    },
                                )
                            MainTab.Assistix ->
                                AssistixAiTab(
                                    token = authToken,
                                    api = api,
                                    languageCode = languageCode,
                                    deviceLanguage = java.util.Locale.getDefault().language,
                                    assistixChat = app.assistixChat,
                                    stt = app.stt,
                                    homeResetTick = assistixHomeReset,
                                )
                            MainTab.Profile ->
                                MyProfileTab(
                                    session = session,
                                    api = api,
                                    profileCache = app.profileCache,
                                    reduceMotion = reduceMotion,
                                )
                        }
                        }
                        }
                        }
                        }
                    }
                }
                if (showProtoSubscribeDialog && protoOfficialChannel != null) {
                    val official = protoOfficialChannel!!
                    ProtoOfficialChannelSubscribeDialog(
                        channel = official,
                        busy = protoSubscribeBusy,
                        onSubscribe = {
                            scope.launch {
                                val t = authToken ?: return@launch
                                protoSubscribeBusy = true
                                val ok =
                                    withContext(Dispatchers.IO) {
                                        api.subscribeChannel(t, official.conversationId)
                                    }
                                protoSubscribeBusy = false
                                if (ok) {
                                    app.prefs.setProtoChannelSubscribePromptDone()
                                    protoChannelSubscribed = true
                                    showProtoSubscribeDialog = false
                                    withContext(Dispatchers.IO) { app.conversations.syncFromServer(t) }
                                    chatsReloadTick++
                                } else {
                                    Toast.makeText(homeCtx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDismiss = {
                            scope.launch {
                                app.prefs.setProtoChannelSubscribePromptDone()
                                showProtoSubscribeDialog = false
                            }
                        },
                    )
                }
                }
            }
            composable(
                route = "chat/{cid}/{title}/{kind}/{peerId}",
                arguments =
                    listOf(
                        navArgument("cid") { type = NavType.IntType },
                        navArgument("title") { type = NavType.StringType },
                        navArgument("kind") { type = NavType.StringType; defaultValue = "dm" },
                        navArgument("peerId") { type = NavType.IntType; defaultValue = 0 },
                    ),
            ) { entry ->
                val cid = entry.arguments?.getInt("cid") ?: 0
                val titleEnc = entry.arguments?.getString("title") ?: UiStrings.chatDefault
                val kind = entry.arguments?.getString("kind") ?: "dm"
                val isSaved = kind == "saved"
                val peerId = entry.arguments?.getInt("peerId") ?: 0
                val title = URLDecoder.decode(titleEnc, StandardCharsets.UTF_8.name())
                ChatScreen(
                    session = session,
                    api = api,
                    messages = messages,
                    calls = calls,
                    stt = app.stt,
                    prefs = app.prefs,
                    chatLocalPrefs = app.chatLocalPrefs,
                    draftPrefs = app.draftPrefs,
                    conversationId = cid,
                    title = if (isSaved) UiStrings.savedMessages else title,
                    isGroup = kind == "group",
                    isSaved = isSaved,
                    peerUserId = peerId,
                    onBack = { nav.popBackStack() },
                    onNeedCallPermissions = onNeedCallPermissions,
                    onOpenProfile = { uid -> if (uid > 0) viewingUserId = uid },
                    onOpenGroupManage = {
                        val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                        nav.navigate("group_manage/$cid/$enc/group")
                    },
                    onCreatePoll = {
                        val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                        nav.navigate("poll_create/$cid/$enc")
                    },
                    onGroupCall = {
                        scope.launch {
                            val t = session.token() ?: return@launch
                            calls.startGroupCall(t, cid, title)
                        }
                    },
                    onOpenChannelNick = { nick ->
                        scope.launch {
                            val t = session.token() ?: return@launch
                            val ch = withContext(Dispatchers.IO) { api.channelByNick(t, nick) } ?: return@launch
                            if (!ch.subscribed && ch.conversationId > 0) {
                                withContext(Dispatchers.IO) { api.subscribeChannel(t, ch.conversationId) }
                                app.conversations.syncFromServer(t)
                            }
                            val enc = URLEncoder.encode(ch.title.ifBlank { ch.nick }, StandardCharsets.UTF_8.name())
                            nav.navigate("channel_feed/${ch.conversationId}/$enc") { launchSingleTop = true }
                        }
                    },
                )
            }
            composable(
                route = "channel_feed/{cid}/{title}",
                arguments =
                    listOf(
                        navArgument("cid") { type = NavType.IntType },
                        navArgument("title") { type = NavType.StringType },
                    ),
            ) { entry ->
                val cid = entry.arguments?.getInt("cid") ?: 0
                val titleEnc = entry.arguments?.getString("title") ?: ""
                val feedTitle = URLDecoder.decode(titleEnc, StandardCharsets.UTF_8.name())
                ChannelFeedScreen(
                    session = session,
                    api = api,
                    conversations = app.conversations,
                    conversationId = cid,
                    title = feedTitle,
                    onBack = { nav.popBackStack() },
                    onOpenManage = {
                        val enc = URLEncoder.encode(feedTitle, StandardCharsets.UTF_8.name())
                        nav.navigate("channel_manage/$cid/$enc")
                    },
                    onCreatePoll = {
                        val enc = URLEncoder.encode(feedTitle, StandardCharsets.UTF_8.name())
                        nav.navigate("poll_create/$cid/$enc")
                    },
                )
            }
            composable(
                route = "channel_manage/{cid}/{title}",
                arguments =
                    listOf(
                        navArgument("cid") { type = NavType.IntType },
                        navArgument("title") { type = NavType.StringType },
                    ),
            ) { entry ->
                val cid = entry.arguments?.getInt("cid") ?: 0
                val titleEnc = entry.arguments?.getString("title") ?: ""
                val manageTitle = URLDecoder.decode(titleEnc, StandardCharsets.UTF_8.name())
                ChannelManageScreen(
                    session = session,
                    api = api,
                    authToken = authToken,
                    conversationId = cid,
                    initialTitle = manageTitle,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                    onOpenMembers = {
                        val enc = URLEncoder.encode(manageTitle, StandardCharsets.UTF_8.name())
                        nav.navigate("group_manage/$cid/$enc/channel")
                    },
                )
            }
            composable("devices") {
                DevicesScreen(
                    session = session,
                    api = api,
                    onBack = { nav.popBackStack() },
                    onScanQr = { nav.navigate("devices_scan") },
                )
            }
            composable("cache_settings") {
                CacheSettingsScreen(
                    onBack = { nav.popBackStack() },
                    onChatsCacheCleared = { org.assistix.proto.nativeapp.data.ProtoEventHub.bump() },
                )
            }
            composable("data_storage") {
                ProtoDataStorageScreen(onBack = { nav.popBackStack() })
            }
            composable("proto_cells") {
                ProtoCellsScreen(onBack = { nav.popBackStack() })
            }
            composable("devices_scan") {
                LinkQrScannerScreen(
                    session = session,
                    api = api,
                    onBack = { nav.popBackStack() },
                    onLinked = { nav.popBackStack() },
                )
            }
            composable("qr_hub") {
                QrHubScreen(
                    session = session,
                    api = api,
                    onBack = { nav.popBackStack() },
                    onOpenScanner = { nav.navigate("qr_scan") },
                )
            }
            composable("qr_scan") {
                UniversalQrScannerScreen(
                    session = session,
                    api = api,
                    onBack = { nav.popBackStack() },
                    onDeviceLinked = { nav.popBackStack() },
                    onOpenChat = { cid, title, kind, peerId ->
                        navigateToConversation(nav, cid, title, kind, peerId) {
                            popUpTo("home")
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = "poll_create/{cid}/{title}",
                arguments =
                    listOf(
                        navArgument("cid") { type = NavType.IntType },
                        navArgument("title") { type = NavType.StringType },
                    ),
            ) { entry ->
                val cid = entry.arguments?.getInt("cid") ?: 0
                val titleEnc = entry.arguments?.getString("title") ?: ""
                val groupTitle = URLDecoder.decode(titleEnc, StandardCharsets.UTF_8.name())
                CreatePollScreen(
                    session = session,
                    api = api,
                    conversationId = cid,
                    groupTitle = groupTitle,
                    onBack = { nav.popBackStack() },
                    onCreated = {
                        val enc = URLEncoder.encode(groupTitle, StandardCharsets.UTF_8.name())
                        scope.launch {
                            val t = session.token()
                            val isChannel =
                                t != null &&
                                    withContext(Dispatchers.IO) {
                                        api.channelByConversation(t, cid) != null
                                    }
                            if (isChannel) {
                                nav.navigate("channel_feed/$cid/$enc") {
                                    popUpTo("poll_create/$cid/$titleEnc") { inclusive = true }
                                }
                            } else {
                                nav.navigate("chat/$cid/$enc/group/0") {
                                    popUpTo("poll_create/$cid/$titleEnc") { inclusive = true }
                                }
                            }
                        }
                    },
                )
            }
            composable("group_create") {
                CreateGroupScreen(
                    session = session,
                    api = api,
                    authToken = authToken,
                    onBack = { nav.popBackStack() },
                    onCreated = { id, groupTitle ->
                        val enc = URLEncoder.encode(groupTitle, StandardCharsets.UTF_8.name())
                        nav.navigate("chat/$id/$enc/group/0") {
                            popUpTo("group_create") { inclusive = true }
                        }
                    },
                )
            }
            composable("new_dm") {
                NewDmScreen(
                    session = session,
                    api = api,
                    authToken = authToken,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { cid, title, peerId ->
                        val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                        nav.navigate("chat/$cid/$enc/dm/$peerId") {
                            popUpTo("new_dm") { inclusive = true }
                        }
                    },
                )
            }
            composable("channel_create") {
                CreateChannelScreen(
                    session = session,
                    api = api,
                    authToken = authToken,
                    onBack = { nav.popBackStack() },
                    onCreated = { id, channelTitle ->
                        val enc = URLEncoder.encode(channelTitle, StandardCharsets.UTF_8.name())
                        nav.navigate("channel_feed/$id/$enc") {
                            popUpTo("channel_create") { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = "group_manage/{cid}/{title}/{convKind}",
                arguments =
                    listOf(
                        navArgument("cid") { type = NavType.IntType },
                        navArgument("title") { type = NavType.StringType },
                        navArgument("convKind") { type = NavType.StringType; defaultValue = "group" },
                    ),
            ) { entry ->
                val cid = entry.arguments?.getInt("cid") ?: 0
                val titleEnc = entry.arguments?.getString("title") ?: ""
                val convKind = entry.arguments?.getString("convKind") ?: "group"
                val groupTitle = URLDecoder.decode(titleEnc, StandardCharsets.UTF_8.name())
                GroupManageScreen(
                    session = session,
                    api = api,
                    authToken = authToken,
                    conversationId = cid,
                    conversationKind = convKind,
                    initialTitle = groupTitle,
                    onBack = { nav.popBackStack() },
                    onLeft = {
                        nav.popBackStack()
                        nav.popBackStack()
                    },
                    onRenamed = { },
                    onOpenProfile = { uid -> if (uid > 0) viewingUserId = uid },
                )
            }
        }

        val profileUid = viewingUserId
        if (hasSession && profileUid != null && profileUid > 0) {
            UserProfileSheet(
                userId = profileUid,
                api = api,
                profileCache = app.profileCache,
                token = authToken,
                onDismiss = { viewingUserId = null },
                onMessage = { uid, title, peerId ->
                    scope.launch {
                        val t = authToken ?: return@launch
                        val cid = withContext(Dispatchers.IO) { api.startDm(t, peerId) }
                        if (cid != null && cid > 0) {
                            viewingUserId = null
                            navigateToConversation(nav, cid, title, "dm", peerId)
                        }
                    }
                },
            )
        }
        if (ProtoMediaViewerState.active) {
            ProtoMediaViewerOverlay(token = authToken, api = api)
        }
        if (signingOut) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ProtoOrange)
                    Text(
                        UiStrings.signingOut,
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthFormScreen(
    title: String,
    submitLabel: String,
    showEmail: Boolean,
    emailRequired: Boolean = false,
    showDisplayName: Boolean = false,
    showForgotPassword: Boolean = false,
    showPolicyConsent: Boolean = false,
    onBack: () -> Unit,
    onOpenOnboarding: (() -> Unit)? = null,
    onForgotPassword: ((loginHint: String) -> Unit)? = null,
    onSubmit: suspend (login: String, password: String, email: String, displayName: String, setError: (String) -> Unit) -> Unit,
) {
    var login by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var policyAccepted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(20.dp)) {
            OutlinedTextField(
                login,
                { login = it },
                label = { Text(if (showEmail) UiStrings.nick else UiStrings.nickOrEmail) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = ProtoShapes.field,
            )
            if (showDisplayName) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    displayName,
                    { displayName = it },
                    label = { Text(UiStrings.displayName) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = ProtoShapes.field,
                )
            }
            if (showEmail) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    email,
                    { email = it },
                    label = { Text(if (emailRequired) UiStrings.emailRequired else UiStrings.emailOptional) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = ProtoShapes.field,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                pass,
                { pass = it },
                label = { Text(UiStrings.password) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = ProtoShapes.field,
            )
            err?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (showPolicyConsent) {
                Spacer(Modifier.height(12.dp))
                ProtoPolicyConsentCard(
                    checked = policyAccepted,
                    onCheckedChange = { policyAccepted = it },
                    enabled = !busy,
                )
            }
            Spacer(Modifier.height(20.dp))
            ProtoPrimaryButton(
                if (busy) "…" else submitLabel,
                {
                    if (busy) return@ProtoPrimaryButton
                    if (showPolicyConsent && !policyAccepted) {
                        err = UiStrings.policyMustAccept
                        return@ProtoPrimaryButton
                    }
                    scope.launch {
                        busy = true
                        err = null
                        try {
                            onSubmit(login, pass, email, displayName) { msg -> err = msg }
                        } catch (e: Exception) {
                            err = e.message ?: UiStrings.genericError
                        } finally {
                            busy = false
                        }
                    }
                },
                Modifier.fillMaxWidth(),
            )
            if (showForgotPassword && onForgotPassword != null) {
                TextButton(
                    onClick = { onForgotPassword(login.trim()) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(UiStrings.forgotPasswordTitle, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (onOpenOnboarding != null) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onOpenOnboarding,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        UiStrings.showOnboardingAgain,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatListScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    conversations: org.assistix.proto.nativeapp.data.ProtoConversationRepository,
    network: org.assistix.proto.nativeapp.data.ProtoNetworkMonitor,
    authToken: String?,
    chatLocalPrefs: ProtoChatLocalPrefs,
    draftPrefs: org.assistix.proto.nativeapp.data.ProtoDraftPrefs,
    search: String,
    searchActive: Boolean,
    reloadTick: Int,
    onStartDmSearch: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onRefreshChats: () -> Unit,
    onOpenAssistix: () -> Unit,
    onOpenQrHub: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onCreatePollInGroup: (Int, String) -> Unit,
    onGroupCall: (Int, String) -> Unit,
    onOpenChat: (Int, String, String, Int) -> Unit,
    showProtoSubscribeBanner: Boolean = false,
    protoSubscribeBusy: Boolean = false,
    onSubscribeProto: () -> Unit = {},
    onOpenProtoChannel: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    val chats by conversations.observeConversations().collectAsState(initial = emptyList())
    val online by network.isOnline.collectAsState(initial = network.checkOnline())
    var searchHits by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    var channelHits by remember { mutableStateOf<List<org.assistix.proto.nativeapp.data.ChannelHit>>(emptyList()) }
    var messageHits by remember { mutableStateOf<List<MessageSearchHit>>(emptyList()) }
    var assistixSearchAnswer by remember { mutableStateOf("") }
    var assistixSearchBusy by remember { mutableStateOf(false) }
    var searchTab by remember { mutableStateOf(0) }
    var showFabMenu by remember { mutableStateOf(false) }
    var fabOtherDeviceCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(showFabMenu, authToken) {
        if (!showFabMenu || authToken.isNullOrBlank()) return@LaunchedEffect
        fabOtherDeviceCount =
            withContext(Dispatchers.IO) {
                val t = session.token() ?: return@withContext 0
                api.listDevices(t).count { !it.current && !it.revoked }
            }
    }
    var pickPollGroup by remember { mutableStateOf(false) }
    var pickGroupCall by remember { mutableStateOf(false) }
    var unreadOnly by remember { mutableStateOf(false) }
    var channelsOnly by remember { mutableStateOf(false) }
    var archiveOnly by remember { mutableStateOf(false) }
    var mutedOnly by remember { mutableStateOf(false) }
    var draftsOnly by remember { mutableStateOf(false) }
    var starredOnly by remember { mutableStateOf(false) }
    var groupsOnly by remember { mutableStateOf(false) }
    var pinnedOnly by remember { mutableStateOf(false) }
    var notesOnly by remember { mutableStateOf(false) }
    val archivedIds by chatLocalPrefs.archivedIds.collectAsState(initial = emptySet())
    val starredConversationIds by chatLocalPrefs.starredConversationIds().collectAsState(initial = emptySet())
    val noteConversationIds by chatLocalPrefs.noteConversationIds.collectAsState(initial = emptySet())
    val recentOpenIds by chatLocalPrefs.recentOpenIds().collectAsState(initial = emptyList())
    val pinnedIds by chatLocalPrefs.pinnedIds.collectAsState(initial = emptySet())
    val mutedIds by chatLocalPrefs.mutedIds.collectAsState(initial = emptySet())
    val draftIds by draftPrefs.draftConversationIds.collectAsState(initial = emptySet())
    val folders by chatLocalPrefs.folders.collectAsState(initial = emptyList())
    var activeFolderId by remember { mutableStateOf<String?>(null) }
    var showFolderEditor by remember { mutableStateOf(false) }
    var editingFolderId by remember { mutableStateOf<String?>(null) }
    var folderMenuId by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<org.assistix.proto.nativeapp.data.ChatFolder?>(null) }
    var folderNameDraft by remember { mutableStateOf("") }
    var folderPickIds by remember { mutableStateOf(setOf<Int>()) }
    var folderColorDraft by remember { mutableStateOf(0) }
    var chatSelectionMode by remember { mutableStateOf(false) }
    var selectedChatIds by remember { mutableStateOf(setOf<Int>()) }
    org.assistix.proto.nativeapp.data.ProtoChatSelectionState.active = chatSelectionMode
    var showVaultPinDialog by remember { mutableStateOf(false) }
    var vaultPinDraft by remember { mutableStateOf("") }
    var pendingVaultLock by remember { mutableStateOf(false) }
    var vaultUnlockMode by remember { mutableStateOf(false) }
    var vaultOnly by remember { mutableStateOf(false) }
    var pendingOpenChat by remember { mutableStateOf<PendingVaultChat?>(null) }
    val vaultIds by chatLocalPrefs.vaultIds.collectAsState(initial = emptySet())
    val vaultUnlocked by chatLocalPrefs.vaultUnlocked.collectAsState(initial = false)
    var myUserId by remember { mutableStateOf(0) }
    val ctx = LocalContext.current
    val haptic = ProtoHaptics.rememberSender()
    LaunchedEffect(Unit) { myUserId = session.userId() }

    LaunchedEffect(Unit) {
        val gate = org.assistix.proto.nativeapp.data.ProtoAppNavigation.consumeVaultGate() ?: return@LaunchedEffect
        pendingOpenChat = PendingVaultChat(gate.conversationId, gate.title, gate.kind, gate.peerUserId)
        vaultUnlockMode = true
        pendingVaultLock = false
        showVaultPinDialog = true
    }

    BackHandler(chatSelectionMode) {
        chatSelectionMode = false
        selectedChatIds = emptySet()
    }

    val forwardActive = ProtoForwardState.active
    val forwardMsg = ProtoForwardState.message
    val forwardSelected = ProtoForwardState.selectedChatIds
    val shareActive = ProtoShareState.active
    val sharePayload = ProtoShareState.payload
    val shareSelected = ProtoShareState.selectedChatIds
    val sharePreview =
        remember(sharePayload) {
            when {
                !sharePayload?.text.isNullOrBlank() -> sharePayload.text!!.trim().take(72)
                sharePayload?.imageUris?.isNotEmpty() == true ->
                    UiStrings.shareModeImagesFmt(sharePayload.imageUris.size)
                else -> ""
            }
        }
    val app = ctx.applicationContext as org.assistix.proto.nativeapp.ProtoApplication
    val compactList by app.prefs.compactChatList.collectAsState(initial = false)
    val sortUnreadFirst by app.prefs.sortUnreadFirst.collectAsState(initial = false)
    val chatRowPadV = if (compactList) 9.dp else 13.dp

    BackHandler(forwardActive) {
        ProtoForwardState.clear()
    }

    BackHandler(shareActive) {
        ProtoShareState.clear()
    }

    suspend fun sharePayloadToChats(targetIds: Set<Int>, data: ProtoSharePayload) {
        if (data.isEmpty || targetIds.isEmpty()) return
        val t = session.token()
        if (t == null) {
            Toast.makeText(ctx, UiStrings.shareFailed, Toast.LENGTH_SHORT).show()
            return
        }
        val uid = session.userId()
        var okCount = 0
        var totalOps = 0
        targetIds.forEach { cid ->
            val text = data.text?.trim().orEmpty()
            if (text.isNotEmpty()) {
                totalOps++
                val sent =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            app.messages.sendText(t, cid, uid, text, isE2e = false, replyTo = null)
                        }
                    }.isSuccess
                if (sent) okCount++
            }
            val uris = data.imageUris
            if (uris.isEmpty()) return@forEach

            suspend fun uploadShareImage(idx: Int, uri: android.net.Uri): AlbumItem? {
                val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                val name = "share_${cid}_${System.currentTimeMillis()}_$idx"
                val prepared =
                    withContext(Dispatchers.IO) {
                        ProtoMediaCompressor.prepareUploadUri(ctx, uri, mime, name)
                    } ?: return null
                if (prepared.first.length() > ProtoMediaCompressor.MAX_UPLOAD_BYTES) return null
                val uploadId = withContext(Dispatchers.IO) { api.uploadFile(t, prepared.first, prepared.second) }
                    ?: return null
                withContext(Dispatchers.IO) {
                    app.mediaResolver.persistOutgoing(uploadId, prepared.first, prepared.second, name)
                }
                return AlbumItem(uploadId, prepared.second, name)
            }

            if (uris.size >= 2) {
                totalOps++
                val albumItems = mutableListOf<AlbumItem>()
                uris.forEachIndexed { idx, uri ->
                    uploadShareImage(idx, uri)?.let { albumItems.add(it) }
                }
                when {
                    albumItems.size >= 2 -> {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                app.messages.sendAlbum(t, cid, albumItems, text)
                            }
                        }.onSuccess { okCount++ }
                    }
                    albumItems.size == 1 -> {
                        val item = albumItems.first()
                        runCatching {
                            withContext(Dispatchers.IO) {
                                app.messages.sendMedia(
                                    t,
                                    cid,
                                    item.uploadId,
                                    item.mime ?: "image/jpeg",
                                    item.name ?: "share.jpg",
                                    text,
                                )
                            }
                        }.onSuccess { okCount++ }
                    }
                }
            } else {
                uris.forEachIndexed { idx, uri ->
                    totalOps++
                    val item = uploadShareImage(idx, uri) ?: return@forEachIndexed
                    runCatching {
                        withContext(Dispatchers.IO) {
                            app.messages.sendMedia(
                                t,
                                cid,
                                item.uploadId,
                                item.mime ?: "image/jpeg",
                                item.name ?: "share.jpg",
                                text,
                            )
                        }
                    }.onSuccess { okCount++ }
                }
            }
        }
        val msg =
            when {
                totalOps == 0 -> UiStrings.shareFailed
                okCount >= totalOps -> UiStrings.sharedOk
                okCount > 0 -> UiStrings.sharePartialFmt(okCount, totalOps)
                else -> UiStrings.shareFailed
            }
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        ProtoShareState.clear()
    }

    suspend fun shareToChats(targetIds: Set<Int>) {
        val data = ProtoShareState.payload ?: return
        sharePayloadToChats(targetIds, data)
    }

    suspend fun forwardToChats(targetIds: Set<Int>) {
        val toForward = ProtoForwardState.messages
        if (toForward.isEmpty() || targetIds.isEmpty()) return
        val t = session.token()
        if (t == null) {
            Toast.makeText(ctx, UiStrings.forwardFailed, Toast.LENGTH_SHORT).show()
            return
        }
        val totalOps = toForward.size * targetIds.size
        var okCount = 0
        targetIds.forEach { cid ->
            toForward.forEach { fwd ->
                val ok =
                    withContext(Dispatchers.IO) {
                        app.messages.forwardMessage(
                            t,
                            cid,
                            fwd,
                            ProtoForwardState.fromLabel,
                            session.userId(),
                        )
                    }
                if (ok) okCount++
            }
        }
        val msg =
            if (okCount == totalOps) {
                UiStrings.forwarded
            } else {
                UiStrings.forwardPartialFmt(okCount, totalOps)
            }
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        ProtoForwardState.clear()
    }

    var listRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = false)
    LaunchedEffect(listRefreshing) {
        swipeRefreshState.isRefreshing = listRefreshing
    }
    LaunchedEffect(chats.sumOf { it.unreadCount }) {
        org.assistix.proto.nativeapp.data.ProtoUnreadBadge.apply(ctx, chats.sumOf { it.unreadCount })
    }

    val hasRealChats = chats.any { it.kind != "saved" }
    fun openChatItem(c: ConvItem, title: String) {
        if (c.id in vaultIds && !vaultUnlocked) {
            pendingOpenChat = PendingVaultChat(c.id, title, c.kind, c.peerUserId)
            vaultUnlockMode = true
            pendingVaultLock = false
            showVaultPinDialog = true
            return
        }
        scope.launch { chatLocalPrefs.recordRecentOpen(c.id) }
        onOpenChat(c.id, title, c.kind, c.peerUserId)
    }

    fun clearAllListFilters() {
        activeFolderId = null
        unreadOnly = false
        channelsOnly = false
        vaultOnly = false
        archiveOnly = false
        mutedOnly = false
        draftsOnly = false
        starredOnly = false
        groupsOnly = false
        pinnedOnly = false
        notesOnly = false
    }

    fun openArchiveFolder() {
        clearAllListFilters()
        archiveOnly = true
    }

    val chatListState = rememberLazyListState()
    var archivePullPx by remember { mutableFloatStateOf(0f) }
    val archiveDensity = LocalDensity.current
    val archiveOpenThresholdPx = with(archiveDensity) { 128.dp.toPx() }
    val archivePullConnection =
        remember(chatListState, archiveOnly, searchActive) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (archiveOnly || searchActive) return Offset.Zero
                    if (available.y < 0f && archivePullPx > 0f) {
                        val consumed = minOf(-available.y, archivePullPx)
                        archivePullPx -= consumed
                        return Offset(0f, -consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (archiveOnly || searchActive) return Offset.Zero
                    val atTop =
                        chatListState.firstVisibleItemIndex == 0 &&
                            chatListState.firstVisibleItemScrollOffset == 0
                    if (!atTop) {
                        if (archivePullPx > 0f) archivePullPx = 0f
                        return Offset.Zero
                    }
                    if (available.y > 0f) {
                        val pull = available.y * 0.5f
                        archivePullPx = (archivePullPx + pull).coerceAtMost(archiveOpenThresholdPx * 1.35f)
                        return Offset(0f, pull)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!archiveOnly && archivePullPx >= archiveOpenThresholdPx * 0.88f) {
                        haptic(HapticKind.Action)
                        openArchiveFolder()
                    }
                    archivePullPx = 0f
                    return Velocity.Zero
                }
            }
        }

    BackHandler(archiveOnly && !chatSelectionMode && !forwardActive && !shareActive) {
        clearAllListFilters()
    }

    val anyListFilter =
        unreadOnly || channelsOnly || archiveOnly || mutedOnly || draftsOnly ||
            starredOnly || groupsOnly || pinnedOnly || notesOnly || vaultOnly ||
            activeFolderId != null

    val sortedChats =
        remember(
            chats.size,
            pinnedIds,
            mutedIds,
            unreadOnly,
            channelsOnly,
            archiveOnly,
            mutedOnly,
            draftsOnly,
            starredOnly,
            starredConversationIds,
            groupsOnly,
            pinnedOnly,
            notesOnly,
            noteConversationIds,
            sortUnreadFirst,
            draftIds,
            archivedIds,
            vaultOnly,
            vaultUnlocked,
            vaultIds,
            activeFolderId,
            folders,
            chats.toList(),
        ) {
            var base =
                when {
                    archiveOnly -> chats.filter { it.id in archivedIds }
                    draftsOnly -> chats.filter { it.id in draftIds && it.id !in archivedIds }
                    mutedOnly -> chats.filter { it.id in mutedIds && it.id !in archivedIds }
                    starredOnly -> chats.filter { it.id in starredConversationIds && it.id !in archivedIds }
                    groupsOnly -> chats.filter { it.kind == "group" && it.id !in archivedIds }
                    pinnedOnly -> chats.filter { it.id in pinnedIds && it.id !in archivedIds }
                    notesOnly -> chats.filter { it.id in noteConversationIds && it.id !in archivedIds }
                    channelsOnly -> chats.filter { it.kind == "channel" && it.id !in archivedIds }
                    unreadOnly -> chats.filter { it.unreadCount > 0 && it.id !in archivedIds }
                    else -> chats.filter { it.kind == "saved" || it.id !in archivedIds }
                }
            val folder = folders.firstOrNull { it.id == activeFolderId }
            if (folder != null) {
                base = base.filter { it.id in folder.conversationIds }
            }
            base =
                when {
                    vaultOnly -> base.filter { it.id in vaultIds }
                    vaultIds.isNotEmpty() && !vaultUnlocked -> base.filter { it.id !in vaultIds }
                    else -> base
                }
            val sortComparator =
                if (sortUnreadFirst) {
                    compareByDescending<ConvItem> { it.unreadCount > 0 }
                        .thenByDescending { it.kind == "saved" }
                        .thenByDescending { it.id in pinnedIds }
                        .thenByDescending { it.updatedAt }
                } else {
                    compareByDescending<ConvItem> { it.kind == "saved" }
                        .thenByDescending { it.id in pinnedIds }
                        .thenByDescending { it.updatedAt }
                }
            base.sortedWith(sortComparator)
        }
    val savedConv = chats.firstOrNull { it.kind == "saved" }

    fun reload() {
        scope.launch {
            val t = session.token() ?: return@launch
            if (chats.isEmpty() && online) loading = true
            if (online) {
                withContext(Dispatchers.IO) { conversations.syncFromServer(t) }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
        val t = session.token()
        if (t != null) {
            val app = ctx.applicationContext as org.assistix.proto.nativeapp.ProtoApplication
            ProtoClientPrefsSync.pull(t, api, chatLocalPrefs, draftPrefs, app.prefs)
        }
    }
    LaunchedEffect(chats.size, authToken) {
        val t = authToken ?: return@LaunchedEffect
        ProtoShareShortcuts.sync(ctx, chats, api, t)
        val direct = ProtoShareState.consumeDirect() ?: return@LaunchedEffect
        sharePayloadToChats(setOf(direct.first), direct.second)
    }
    LaunchedEffect(ProtoEventHub.tick) { reload() }
    LaunchedEffect(reloadTick) { if (reloadTick > 0) reload() }

    suspend fun saveFoldersSynced(next: List<org.assistix.proto.nativeapp.data.ChatFolder>) {
        chatLocalPrefs.saveFolders(next)
        val t = session.token() ?: return
        ProtoClientPrefsSync.pushFolders(t, api, next)
    }

    LaunchedEffect(search, searchActive, searchTab) {
        if (!searchActive || search.trim().length < 1) {
            searchHits = emptyList()
            channelHits = emptyList()
            messageHits = emptyList()
            assistixSearchAnswer = ""
            assistixSearchBusy = false
            return@LaunchedEffect
        }
        delay(280)
        val t = session.token() ?: return@LaunchedEffect
        when (searchTab) {
            0 -> {
                searchHits = withContext(Dispatchers.IO) { api.searchUsers(t, search.trim()) }
                channelHits = emptyList()
                messageHits = emptyList()
                assistixSearchAnswer = ""
                assistixSearchBusy = false
            }
            1 -> {
                channelHits = withContext(Dispatchers.IO) { api.searchChannels(t, search.trim()) }
                searchHits = emptyList()
                messageHits = emptyList()
                assistixSearchAnswer = ""
                assistixSearchBusy = false
            }
            3 -> {
                searchHits = emptyList()
                channelHits = emptyList()
                messageHits = emptyList()
                assistixSearchBusy = true
                val res = withContext(Dispatchers.IO) { api.globalSearch(t, search.trim()) }
                val hits =
                    res.messages.map { m ->
                        "${m.conversationTitle}: ${m.bodySnippet}"
                    }
                val reply =
                    withContext(Dispatchers.IO) {
                        api.assistixRequest(
                            token = t,
                            action = "search_messages",
                            text = search.trim(),
                            searchHits = hits,
                            language = java.util.Locale.getDefault().language,
                        )
                    }
                assistixSearchAnswer =
                    if (reply.ok) {
                        reply.text
                    } else {
                        reply.message.orEmpty().ifBlank { UiStrings.assistixError }
                    }
                assistixSearchBusy = false
            }
            else -> {
                val res = withContext(Dispatchers.IO) { api.globalSearch(t, search.trim()) }
                messageHits = res.messages
                searchHits = emptyList()
                channelHits = emptyList()
                assistixSearchAnswer = ""
                assistixSearchBusy = false
            }
        }
    }

    val groupChats = chats.filter { it.kind == "group" }
    var queuedOutbox by remember { mutableIntStateOf(0) }
    LaunchedEffect(online) {
        while (isActive) {
            queuedOutbox = withContext(Dispatchers.IO) { app.messages.pendingOutboxCount() }
            delay(2500)
        }
    }
    val recentFabChats =
        remember(chats, recentOpenIds) {
            val byId = chats.associateBy { it.id }
            recentOpenIds
                .mapNotNull { id -> byId[id]?.let { it.id to (it.peerDisplayName.ifBlank { it.title }) } }
                .take(3)
                .ifEmpty {
                    chats
                        .filter { it.kind != "saved" && it.updatedAt > 0L }
                        .sortedByDescending { it.updatedAt }
                        .take(3)
                        .map { it.id to it.title }
                }
        }

    Column(Modifier.fillMaxSize()) {
        ProtoOfflineBanner(offline = !online, queuedCount = queuedOutbox)
        if (forwardActive && forwardMsg != null) {
            ForwardModeBar(
                messageCount = ProtoForwardState.messages.size,
                fromLabel = ProtoForwardState.fromLabel,
                selectedCount = forwardSelected.size,
                onCancel = { ProtoForwardState.clear() },
                onSendMulti = { scope.launch { forwardToChats(forwardSelected) } },
            )
        }
        if (shareActive && sharePayload != null) {
            ShareModeBar(
                preview = sharePreview,
                selectedCount = shareSelected.size,
                onCancel = { ProtoShareState.clear() },
                onSendMulti = { scope.launch { shareToChats(shareSelected) } },
                imagePreviewUri = sharePayload.imageUris.firstOrNull(),
                imageCount = sharePayload.imageUris.size,
            )
        }
        if (chatSelectionMode) {
            ChatSelectionTopBar(
                selectedCount = selectedChatIds.size,
                onCancel = {
                    chatSelectionMode = false
                    selectedChatIds = emptySet()
                },
                onPin = {
                    scope.launch {
                        selectedChatIds.forEach { id ->
                            if (!chatLocalPrefs.togglePin(id)) {
                                haptic(HapticKind.Error)
                                Toast.makeText(ctx, UiStrings.maxPins, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                        }
                        chatSelectionMode = false
                        selectedChatIds = emptySet()
                    }
                },
                onMute = {
                    scope.launch {
                        selectedChatIds.forEach { chatLocalPrefs.toggleMute(it) }
                        chatSelectionMode = false
                        selectedChatIds = emptySet()
                    }
                },
                onLock = {
                    pendingVaultLock = true
                    scope.launch {
                        val hasPin = chatLocalPrefs.hasVaultPinFlow().first()
                        if (hasPin) {
                            selectedChatIds.forEach { chatLocalPrefs.toggleVault(it) }
                            chatSelectionMode = false
                            selectedChatIds = emptySet()
                            pendingVaultLock = false
                        } else {
                            showVaultPinDialog = true
                        }
                    }
                },
                archiveLabel = if (archiveOnly) UiStrings.chatMultiUnarchive else UiStrings.chatMultiArchive,
                onArchive = {
                    scope.launch {
                        selectedChatIds.forEach { chatLocalPrefs.toggleArchive(it) }
                        chatSelectionMode = false
                        selectedChatIds = emptySet()
                    }
                },
                onMarkRead = {
                    scope.launch {
                        val t = session.token() ?: return@launch
                        selectedChatIds.forEach { id ->
                            val c = chats.firstOrNull { it.id == id } ?: return@forEach
                            if (c.lastMessageId > 0 && c.unreadCount > 0) {
                                withContext(Dispatchers.IO) {
                                    api.markRead(t, c.id, c.lastMessageId)
                                }
                            }
                        }
                        reload()
                        chatSelectionMode = false
                        selectedChatIds = emptySet()
                    }
                },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                    if (searchActive && search.trim().isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = searchTab == 0,
                                onClick = { searchTab = 0 },
                                label = { Text(UiStrings.searchTabUsers) },
                            )
                            FilterChip(
                                selected = searchTab == 1,
                                onClick = { searchTab = 1 },
                                label = { Text(UiStrings.searchTabChannels) },
                            )
                            FilterChip(
                                selected = searchTab == 2,
                                onClick = { searchTab = 2 },
                                label = { Text(UiStrings.searchTabMessages) },
                            )
                            FilterChip(
                                selected = searchTab == 3,
                                onClick = { searchTab = 3 },
                                label = { Text(UiStrings.searchTabAssistix) },
                            )
                        }
                    }
                    if (searchTab == 3 && searchActive) {
                        ProtoSurfaceCard(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(UiStrings.assistixAi, fontWeight = FontWeight.Bold, color = ProtoOrange)
                                Spacer(Modifier.size(8.dp))
                                if (assistixSearchBusy) {
                                    CircularProgressIndicator(Modifier.size(28.dp), color = ProtoOrange)
                                } else if (assistixSearchAnswer.isNotBlank()) {
                                    Text(assistixSearchAnswer, style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Text(
                                        UiStrings.assistixHint,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else if (searchHits.isNotEmpty()) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(searchHits, key = { it.id }) { u ->
                                ProtoSurfaceCard(
                                    onClick = {
                                        scope.launch {
                                            val t = session.token() ?: return@launch
                                            val cid = withContext(Dispatchers.IO) { api.startDm(t, u.id) } ?: return@launch
                                            onOpenChat(cid, resolveDisplayName(u.displayName, u.nick), "dm", u.id)
                                        }
                                    },
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ProtoAvatar(u.avatarUploadId, u.displayName, 48.dp, api, authToken)
                                        Spacer(Modifier.size(12.dp))
                                        Column {
                                            DisplayNameWithEmoji(resolveDisplayName(u.displayName, u.nick), u.statusEmoji)
                                            Text(
                                                "@${u.nick}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (messageHits.isNotEmpty()) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(messageHits, key = { it.id }) { hit ->
                                ProtoSurfaceCard(
                                    onClick = {
                                        val enc =
                                            URLEncoder.encode(
                                                hit.conversationTitle.ifBlank { "Chat" },
                                                StandardCharsets.UTF_8.name(),
                                            )
                                        onOpenChat(hit.conversationId, hit.conversationTitle, hit.conversationKind, 0)
                                    },
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                    ) {
                                        Text(
                                            hit.conversationTitle.ifBlank { "Chat" },
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            hit.bodySnippet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (channelHits.isNotEmpty()) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(channelHits, key = { it.conversationId }) { ch ->
                                var channelSubBusy by remember(ch.conversationId) { mutableStateOf(false) }
                                ProtoSurfaceCard(
                                    onClick = {
                                        onOpenChat(ch.conversationId, ch.title.ifBlank { ch.nick }, "channel", 0)
                                    },
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ProtoAvatar(ch.avatarUploadId, ch.title, 48.dp, api, authToken)
                                        Spacer(Modifier.size(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(ch.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                if (ch.verified) {
                                                    Spacer(Modifier.size(4.dp))
                                                    VerifiedBadge(showTooltip = true)
                                                }
                                            }
                                            Text(
                                                "@${ch.nick}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (ch.description.isNotBlank()) {
                                                Text(
                                                    ch.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        if (!ch.subscribed) {
                                            ChannelSubscribeChip(
                                                busy = channelSubBusy,
                                                onSubscribe = {
                                                    scope.launch {
                                                        val t = session.token() ?: return@launch
                                                        channelSubBusy = true
                                                        val ok =
                                                            withContext(Dispatchers.IO) {
                                                                api.subscribeChannel(t, ch.conversationId)
                                                            }
                                                        channelSubBusy = false
                                                        if (ok) {
                                                            conversations.syncFromServer(t)
                                                            onRefreshChats()
                                                        } else {
                                                            Toast.makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (loading && chats.isEmpty()) {
                        Column(Modifier.fillMaxSize()) {
                            repeat(7) { ChatListSkeletonRow() }
                        }
                    } else if (!hasRealChats) {
                        Column(Modifier.fillMaxSize()) {
                            savedConv?.let { c ->
                                ProtoSurfaceCard(
                                    onClick = { onOpenChat(c.id, UiStrings.savedMessages, c.kind, c.peerUserId) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = chatRowPadV),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(52.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Icon(
                                                    Icons.Default.Bookmark,
                                                    contentDescription = UiStrings.savedMessages,
                                                    tint = ProtoOrange,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.size(12.dp))
                                        Column {
                                            DisplayNameWithEmoji(UiStrings.savedMessages, "", maxLines = 1)
                                            Text(
                                                c.preview.ifBlank { UiStrings.noMessages },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                            ProtoChatsEmptyState(
                                onOpenSearch = onStartDmSearch,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected =
                                    activeFolderId == null &&
                                        !unreadOnly &&
                                        !channelsOnly &&
                                        !archiveOnly &&
                                        !mutedOnly &&
                                        !draftsOnly &&
                                        !starredOnly &&
                                        !groupsOnly &&
                                        !pinnedOnly &&
                                        !notesOnly,
                                onClick = {
                                    haptic(HapticKind.Tap)
                                    clearAllListFilters()
                                },
                                label = { Text(UiStrings.filterAll) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ProtoOrange.copy(0.22f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                            if (vaultIds.isNotEmpty()) {
                                FilterChip(
                                    selected = vaultOnly && !unreadOnly && activeFolderId == null,
                                    onClick = {
                                        haptic(HapticKind.Tap)
                                        vaultOnly = true
                                        unreadOnly = false
                                        mutedOnly = false
                                        draftsOnly = false
                                        starredOnly = false
                                        groupsOnly = false
                                        pinnedOnly = false
                                        archiveOnly = false
                                        channelsOnly = false
                                        activeFolderId = null
                                        if (!vaultUnlocked) {
                                            vaultUnlockMode = true
                                            pendingVaultLock = false
                                            pendingOpenChat = null
                                            showVaultPinDialog = true
                                        }
                                    },
                                    label = {
                                        Text(
                                            if (vaultIds.size > 0) {
                                                "${UiStrings.filterVault} (${vaultIds.size})"
                                            } else {
                                                UiStrings.filterVault
                                            },
                                        )
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ProtoOrange.copy(0.22f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                            FilterChip(
                                selected = channelsOnly,
                                onClick = {
                                    haptic(HapticKind.Tap)
                                    channelsOnly = true
                                    unreadOnly = false
                                    vaultOnly = false
                                    mutedOnly = false
                                    draftsOnly = false
                                    starredOnly = false
                                    groupsOnly = false
                                    pinnedOnly = false
                                    archiveOnly = false
                                    activeFolderId = null
                                },
                                label = { Text(UiStrings.channelsOnlyFilter) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ProtoOrange.copy(0.22f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                            FilterChip(
                                selected = unreadOnly,
                                onClick = {
                                    haptic(HapticKind.Tap)
                                    unreadOnly = true
                                    channelsOnly = false
                                    vaultOnly = false
                                    archiveOnly = false
                                    mutedOnly = false
                                    draftsOnly = false
                                    starredOnly = false
                                    groupsOnly = false
                                    pinnedOnly = false
                                    activeFolderId = null
                                },
                                label = {
                                    val n = chats.sumOf { it.unreadCount }
                                    Text(if (n > 0) "${UiStrings.filterUnread} ($n)" else UiStrings.filterUnread)
                                },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ProtoOrange.copy(0.22f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                            if (archivedIds.isNotEmpty()) {
                                FilterChip(
                                    selected = archiveOnly,
                                    onClick = {
                                        haptic(HapticKind.Tap)
                                        archiveOnly = true
                                        unreadOnly = false
                                        channelsOnly = false
                                        vaultOnly = false
                                        mutedOnly = false
                                        draftsOnly = false
                                        starredOnly = false
                                        groupsOnly = false
                                        pinnedOnly = false
                                        activeFolderId = null
                                    },
                                    label = {
                                        Text(
                                            if (archivedIds.isNotEmpty()) {
                                                "${UiStrings.filterArchive} (${archivedIds.size})"
                                            } else {
                                                UiStrings.filterArchive
                                            },
                                        )
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ProtoOrange.copy(0.22f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                            if (mutedIds.isNotEmpty()) {
                                FilterChip(
                                    selected = mutedOnly,
                                    onClick = {
                                        haptic(HapticKind.Tap)
                                        mutedOnly = true
                                        draftsOnly = false
                                        starredOnly = false
                                        groupsOnly = false
                                        pinnedOnly = false
                                        unreadOnly = false
                                        channelsOnly = false
                                        vaultOnly = false
                                        archiveOnly = false
                                        activeFolderId = null
                                    },
                                    label = { Text("${UiStrings.filterMuted} (${mutedIds.size})") },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ProtoOrange.copy(0.22f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                            if (draftIds.isNotEmpty()) {
                                FilterChip(
                                    selected = draftsOnly,
                                    onClick = {
                                        haptic(HapticKind.Tap)
                                        draftsOnly = true
                                        mutedOnly = false
                                        starredOnly = false
                                        groupsOnly = false
                                        pinnedOnly = false
                                        unreadOnly = false
                                        channelsOnly = false
                                        vaultOnly = false
                                        archiveOnly = false
                                        activeFolderId = null
                                    },
                                    label = { Text("${UiStrings.filterDrafts} (${draftIds.size})") },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ProtoOrange.copy(0.22f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                            if (starredConversationIds.isNotEmpty()) {
                                FilterChip(
                                    selected = starredOnly,
                                    onClick = {
                                        haptic(HapticKind.Tap)
                                        starredOnly = true
                                        draftsOnly = false
                                        mutedOnly = false
                                        groupsOnly = false
                                        pinnedOnly = false
                                        unreadOnly = false
                                        channelsOnly = false
                                        vaultOnly = false
                                        archiveOnly = false
                                        activeFolderId = null
                                    },
                                    label = { Text("${UiStrings.filterStarred} (${starredConversationIds.size})") },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ProtoOrange.copy(0.22f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                            if (pinnedIds.isNotEmpty()) {
                                FilterChip(
                                    selected = pinnedOnly,
                                    onClick = {
                                        haptic(HapticKind.Tap)
                                        pinnedOnly = true
                                        starredOnly = false
                                        draftsOnly = false
                                        mutedOnly = false
                                        groupsOnly = false
                                        unreadOnly = false
                                        channelsOnly = false
                                        vaultOnly = false
                                        archiveOnly = false
                                        activeFolderId = null
                                    },
                                    label = { Text("${UiStrings.filterPinned} (${pinnedIds.size})") },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ProtoOrange.copy(0.22f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                            FilterChip(
                                selected = groupsOnly,
                                onClick = {
                                    haptic(HapticKind.Tap)
                                    clearAllListFilters()
                                    groupsOnly = true
                                },
                                label = { Text(UiStrings.filterGroups) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ProtoOrange.copy(0.22f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                            if (noteConversationIds.isNotEmpty()) {
                                FilterChip(
                                    selected = notesOnly,
                                    onClick = {
                                        haptic(HapticKind.Tap)
                                        clearAllListFilters()
                                        notesOnly = true
                                    },
                                    label = { Text("${UiStrings.filterNotes} (${noteConversationIds.size})") },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ProtoOrange.copy(0.22f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                            folders.forEach { folder ->
                                val folderUnread = chats.filter { it.id in folder.conversationIds }.sumOf { it.unreadCount }
                                FolderFilterChip(
                                    label =
                                        if (folderUnread > 0) {
                                            "${folder.name} ($folderUnread)"
                                        } else {
                                            folder.name
                                        },
                                    selected = activeFolderId == folder.id,
                                    accentColor = FolderColors.colorFor(folder.colorId),
                                    multiSelected = forwardActive && folder.conversationIds.any { it in forwardSelected },
                                    onClick = {
                                        haptic(HapticKind.Tap)
                                        clearAllListFilters()
                                        activeFolderId = folder.id
                                    },
                                    onLongClick = {
                                        haptic(HapticKind.Action)
                                        folderMenuId = folder.id
                                    },
                                )
                            }
                            FilterChip(
                                selected = false,
                                onClick = {
                                    haptic(HapticKind.Tap)
                                    editingFolderId = null
                                    folderNameDraft = ""
                                    folderPickIds = emptySet()
                                    folderColorDraft = 1
                                    showFolderEditor = true
                                },
                                label = { Text("+ ${UiStrings.newFolder}") },
                            )
                        }
                        SwipeRefresh(
                            state = swipeRefreshState,
                            onRefresh = {
                                scope.launch {
                                    listRefreshing = true
                                    reload()
                                    listRefreshing = false
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier.fillMaxSize().nestedScroll(archivePullConnection),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (archiveOnly) {
                                item(key = "archive-back") {
                                    ProtoGhostButton(
                                        UiStrings.backFromArchive,
                                        {
                                            haptic(HapticKind.Tap)
                                            clearAllListFilters()
                                        },
                                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    )
                                }
                            }
                            if (!archiveOnly && !searchActive) {
                                item(key = "archive-folder") {
                                    val reveal = (archivePullPx / archiveOpenThresholdPx).coerceIn(0f, 1f)
                                    if (archivedIds.isNotEmpty() || reveal > 0.04f) {
                                        ChatArchiveFolderRow(
                                            archivedCount = archivedIds.size,
                                            pullReveal = reveal,
                                            pullHint =
                                                if (reveal > 0.88f) {
                                                    UiStrings.archivePullRelease
                                                } else {
                                                    UiStrings.archivePullHint
                                                },
                                            onOpenArchive = {
                                                haptic(HapticKind.Tap)
                                                openArchiveFolder()
                                            },
                                        )
                                    }
                                }
                            }
                            if (showProtoSubscribeBanner && !searchActive) {
                                item(key = "proto-subscribe-banner") {
                                    ProtoSurfaceCard(onClick = onOpenProtoChannel) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    UiStrings.channelOfficialSubscribeBanner,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                                Text(
                                                    "@proto",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            VerifiedBadge(showTooltip = true)
                                            ChannelSubscribeChip(
                                                busy = protoSubscribeBusy,
                                                onSubscribe = onSubscribeProto,
                                            )
                                        }
                                    }
                                }
                            }
                            if (sortedChats.isEmpty() && anyListFilter && hasRealChats) {
                                item(key = "filter-empty") {
                                    ProtoChatsFilteredEmptyState(
                                        onClearFilters = {
                                            haptic(HapticKind.Tap)
                                            clearAllListFilters()
                                        },
                                    )
                                }
                            }
                            items(sortedChats, key = { it.id }) { c ->
                                val isPinned = c.id in pinnedIds
                                val isMuted = c.id in mutedIds
                                val picked = c.id in selectedChatIds
                                val fwdPicked = c.id in forwardSelected
                                val sharePicked = c.id in shareSelected
                                ChatListSwipeRow(
                                    pinned = isPinned,
                                    muted = isMuted,
                                    unreadCount = c.unreadCount,
                                    onPin = {
                                        scope.launch {
                                            if (!chatLocalPrefs.togglePin(c.id)) {
                                                haptic(HapticKind.Error)
                                                Toast.makeText(ctx, UiStrings.maxPins, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onMute = { scope.launch { chatLocalPrefs.toggleMute(c.id) } },
                                    onMarkRead =
                                        if (!shareActive && !forwardActive && c.unreadCount > 0 && c.lastMessageId > 0) {
                                            {
                                                scope.launch {
                                                    val t = session.token() ?: return@launch
                                                    withContext(Dispatchers.IO) {
                                                        api.markRead(t, c.id, c.lastMessageId)
                                                    }
                                                    reload()
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                    onArchive =
                                        if (!shareActive && !forwardActive && !chatSelectionMode) {
                                            {
                                                scope.launch { chatLocalPrefs.toggleArchive(c.id) }
                                            }
                                        } else {
                                            null
                                        },
                                    archiveSwipeLabel =
                                        if (c.id in archivedIds) {
                                            UiStrings.swipeUnarchive
                                        } else {
                                            UiStrings.swipeArchive
                                        },
                                ) {
                                val savedChat = c.kind == "saved"
                                val openTitle = if (savedChat) UiStrings.savedMessages else c.title
                                Box(
                                    Modifier.combinedClickable(
                                        onClick = {
                                            when {
                                                shareActive -> {
                                                    if (shareSelected.isNotEmpty()) {
                                                        ProtoShareState.toggleTarget(c.id)
                                                    } else {
                                                        scope.launch {
                                                            shareToChats(setOf(c.id))
                                                            if (!ProtoShareState.active) {
                                                                openChatItem(c, openTitle)
                                                            }
                                                        }
                                                    }
                                                }
                                                forwardActive -> {
                                                    if (forwardSelected.isNotEmpty()) {
                                                        ProtoForwardState.toggleTarget(c.id)
                                                    } else {
                                                        scope.launch {
                                                            forwardToChats(setOf(c.id))
                                                            if (!ProtoForwardState.active) {
                                                                openChatItem(c, openTitle)
                                                            }
                                                        }
                                                    }
                                                }
                                                chatSelectionMode -> {
                                                    selectedChatIds =
                                                        if (picked) selectedChatIds - c.id else selectedChatIds + c.id
                                                }
                                                else -> openChatItem(c, openTitle)
                                            }
                                        },
                                        onLongClick = {
                                            if (savedChat && !forwardActive && !shareActive) return@combinedClickable
                                            haptic(HapticKind.Action)
                                            when {
                                                shareActive -> ProtoShareState.toggleTarget(c.id)
                                                forwardActive -> ProtoForwardState.toggleTarget(c.id)
                                                !chatSelectionMode -> {
                                                    chatSelectionMode = true
                                                    selectedChatIds = setOf(c.id)
                                                }
                                            }
                                        },
                                    ),
                                ) {
                                ProtoSurfaceCard(onClick = null) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = chatRowPadV),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (chatSelectionMode || (forwardActive && fwdPicked) || (shareActive && sharePicked)) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint =
                                                    if (picked || fwdPicked || sharePicked) {
                                                        ProtoOrange
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                    },
                                                modifier = Modifier.padding(end = 8.dp),
                                            )
                                        }
                                        if (savedChat) {
                                            Surface(
                                                modifier = Modifier.size(52.dp),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                    Icon(
                                                        Icons.Default.Bookmark,
                                                        contentDescription = UiStrings.savedMessages,
                                                        tint = ProtoOrange,
                                                        modifier = Modifier.size(28.dp),
                                                    )
                                                }
                                            }
                                        } else {
                                        val avatarLabel =
                                            when {
                                                c.kind == "group" || c.kind == "channel" -> c.title
                                                c.peerDisplayName.isNotBlank() -> c.peerDisplayName
                                                else -> c.title
                                            }
                                        val avatarId =
                                            if (c.kind == "channel") c.channelAvatarUploadId else c.peerAvatarUploadId
                                        ProtoAvatar(avatarId, avatarLabel, 52.dp, api, authToken)
                                        }
                                        Spacer(Modifier.size(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                val listTitle =
                                                    when {
                                                        savedChat -> UiStrings.savedMessages
                                                        c.kind == "channel" -> c.title
                                                        c.kind == "group" -> c.title
                                                        c.peerDisplayName.isNotBlank() -> c.peerDisplayName
                                                        else -> c.title
                                                    }
                                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                    if (c.id in vaultIds) {
                                                        Icon(
                                                            Icons.Default.Lock,
                                                            null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                                        )
                                                    }
                                                    if (isPinned) {
                                                        Icon(
                                                            Icons.Default.PushPin,
                                                            null,
                                                            tint = ProtoOrange,
                                                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                                        )
                                                    }
                                                    DisplayNameWithEmoji(
                                                        listTitle,
                                                        c.peerStatusEmoji,
                                                        maxLines = 1,
                                                    )
                                                    if (c.kind == "channel" && c.channelVerified) {
                                                        Spacer(Modifier.size(4.dp))
                                                        VerifiedBadge(Modifier.size(16.dp), showTooltip = true)
                                                    }
                                                }
                                                if (c.updatedAt > 0) {
                                                    Text(
                                                        formatChatListTime(c.updatedAt),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            if (c.kind == "channel" && c.channelNick.isNotBlank()) {
                                                Text(
                                                    "@${c.channelNick}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                            val previewText =
                                                buildString {
                                                    if (isMuted) append("🔕 ")
                                                    if (c.id in draftIds) append("✎ ")
                                                    if (c.id in noteConversationIds) append("📝 ")
                                                    append(previewForList(c, myUserId))
                                                }
                                            Text(
                                                previewText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (c.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                                color =
                                                    if (c.unreadCount > 0) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (c.unreadCount > 0) {
                                            Spacer(Modifier.size(8.dp))
                                            val badgeText = if (c.unreadCount > 99) "99+" else c.unreadCount.toString()
                                            Box(
                                                Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(ProtoOrange),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    badgeText,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                                }
                                }
                            }
                        }
                        }
                    }
                }
        if (showVaultPinDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showVaultPinDialog = false
                    pendingVaultLock = false
                    vaultUnlockMode = false
                    pendingOpenChat = null
                    if (!vaultUnlocked) vaultOnly = false
                },
                title = {
                    Text(
                        if (vaultUnlockMode && !pendingVaultLock) {
                            UiStrings.vaultUnlockTitle
                        } else {
                            UiStrings.enterVaultPin
                        },
                    )
                },
                text = {
                    OutlinedTextField(
                        value = vaultPinDraft,
                        onValueChange = { vaultPinDraft = it.filter { ch -> ch.isDigit() }.take(6) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (vaultUnlockMode && !pendingVaultLock) {
                                    val ok = chatLocalPrefs.unlockVault(vaultPinDraft)
                                    if (!ok) {
                                        haptic(HapticKind.Error)
                                        Toast.makeText(ctx, UiStrings.vaultWrongPin, Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    pendingOpenChat?.let { p ->
                                        onOpenChat(p.id, p.title, p.kind, p.peerId)
                                    }
                                    pendingOpenChat = null
                                    vaultUnlockMode = false
                                    showVaultPinDialog = false
                                    vaultPinDraft = ""
                                    haptic(HapticKind.Action)
                                    return@launch
                                }
                                chatLocalPrefs.setVaultPin(vaultPinDraft)
                                if (pendingVaultLock) {
                                    selectedChatIds.forEach { chatLocalPrefs.toggleVault(it) }
                                }
                                showVaultPinDialog = false
                                pendingVaultLock = false
                                vaultUnlockMode = false
                                chatSelectionMode = false
                                selectedChatIds = emptySet()
                                vaultPinDraft = ""
                                Toast.makeText(ctx, UiStrings.vaultPinSet, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) { Text(if (vaultUnlockMode && !pendingVaultLock) UiStrings.unlock else UiStrings.save) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showVaultPinDialog = false
                        pendingVaultLock = false
                        vaultUnlockMode = false
                        pendingOpenChat = null
                        if (!vaultUnlocked) vaultOnly = false
                    }) { Text(UiStrings.cancel) }
                },
            )
        }
            val (fabTiles, fabSections) =
                remember(
                    onStartDmSearch,
                    onNewGroup,
                    onNewChannel,
                    onRefreshChats,
                    onOpenAssistix,
                    onOpenQrHub,
                    onScanQr,
                    onOpenDevices,
                    fabOtherDeviceCount,
                    recentFabChats,
                    chats,
                ) {
                    buildChatsFabMenu(
                        otherDeviceCount = fabOtherDeviceCount,
                        recentChats = recentFabChats,
                        onRecentChat = { id ->
                            chats.firstOrNull { it.id == id }?.let { openChatItem(it, it.title) }
                        },
                        onScanQr = onScanQr,
                        onOpenQrHub = onOpenQrHub,
                        onOpenDevices = onOpenDevices,
                        onNewDm = onStartDmSearch,
                        onNewGroup = onNewGroup,
                        onNewChannel = onNewChannel,
                        onRefresh = onRefreshChats,
                        onOpenAssistix = onOpenAssistix,
                        onNewPoll = { pickPollGroup = true },
                        onGroupCall = { pickGroupCall = true },
                        onMarkAllRead = {
                            scope.launch {
                                val t = session.token() ?: return@launch
                                var n = 0
                                chats.filter { it.unreadCount > 0 && it.lastMessageId > 0 }.forEach { c ->
                                    withContext(Dispatchers.IO) { api.markRead(t, c.id, c.lastMessageId) }
                                    n++
                                }
                                reload()
                                if (n > 0) {
                                    Toast.makeText(ctx, UiStrings.markAllReadDoneFmt(n), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                }
            ProtoChatsFabMenu(
                expanded = showFabMenu,
                onExpandedChange = { showFabMenu = it },
                quickTiles = fabTiles,
                sections = fabSections,
                hapticsEnabled = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (pickPollGroup) {
            GroupPickerDialog(
                title = UiStrings.pickGroupForPoll,
                groups = groupChats,
                onDismiss = { pickPollGroup = false },
                onPick = { c ->
                    pickPollGroup = false
                    onCreatePollInGroup(c.id, c.title)
                },
            )
        }
        if (pickGroupCall) {
            GroupPickerDialog(
                title = UiStrings.groupCall,
                groups = groupChats,
                onDismiss = { pickGroupCall = false },
                onPick = { c ->
                    pickGroupCall = false
                    onGroupCall(c.id, c.title)
                },
            )
        }
        folderMenuId?.let { menuId ->
            val menuFolder = folders.firstOrNull { it.id == menuId }
            if (menuFolder != null) {
                val menuIndex = folders.indexOfFirst { it.id == menuId }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { folderMenuId = null },
                    title = { Text(menuFolder.name, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    editingFolderId = menuFolder.id
                                    folderNameDraft = menuFolder.name
                                    folderPickIds = menuFolder.conversationIds
                                    folderColorDraft = menuFolder.colorId
                                    showFolderEditor = true
                                    folderMenuId = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(UiStrings.folderEdit) }
                            TextButton(
                                onClick = {
                                    if (menuIndex > 0) {
                                        val next = folders.toMutableList()
                                        val tmp = next[menuIndex - 1]
                                        next[menuIndex - 1] = next[menuIndex]
                                        next[menuIndex] = tmp
                                        scope.launch { saveFoldersSynced(next) }
                                    }
                                    folderMenuId = null
                                },
                                enabled = menuIndex > 0,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(UiStrings.folderMoveUp) }
                            TextButton(
                                onClick = {
                                    if (menuIndex >= 0 && menuIndex < folders.lastIndex) {
                                        val next = folders.toMutableList()
                                        val tmp = next[menuIndex + 1]
                                        next[menuIndex + 1] = next[menuIndex]
                                        next[menuIndex] = tmp
                                        scope.launch { saveFoldersSynced(next) }
                                    }
                                    folderMenuId = null
                                },
                                enabled = menuIndex >= 0 && menuIndex < folders.lastIndex,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(UiStrings.folderMoveDown) }
                            TextButton(
                                onClick = {
                                    folderToDelete = menuFolder
                                    folderMenuId = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(UiStrings.folderDelete, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { folderMenuId = null }) { Text(UiStrings.cancel) }
                    },
                )
            }
        }
        folderToDelete?.let { doomed ->
            ChatFolderDeleteDialog(
                folderName = doomed.name,
                onDismiss = { folderToDelete = null },
                onConfirm = {
                    scope.launch {
                        saveFoldersSynced(folders.filter { it.id != doomed.id })
                        if (activeFolderId == doomed.id) activeFolderId = null
                        folderToDelete = null
                    }
                },
            )
        }
        if (showFolderEditor) {
            ChatFolderEditorDialog(
                title = if (editingFolderId != null) UiStrings.folderEdit else UiStrings.newFolder,
                folderName = folderNameDraft,
                onFolderNameChange = { folderNameDraft = it },
                colorId = folderColorDraft,
                onColorIdChange = { folderColorDraft = it },
                chats = chats,
                pickedIds = folderPickIds,
                onToggleChat = { id, checked ->
                    folderPickIds = if (checked) folderPickIds + id else folderPickIds - id
                },
                onDismiss = {
                    showFolderEditor = false
                    editingFolderId = null
                },
                onSave = {
                    val name = folderNameDraft.trim()
                    if (name.isEmpty()) return@ChatFolderEditorDialog
                    scope.launch {
                        val editId = editingFolderId
                        val next =
                            if (editId != null) {
                                folders.map { f ->
                                    if (f.id == editId) {
                                        org.assistix.proto.nativeapp.data.ChatFolder(
                                            editId,
                                            name,
                                            folderPickIds,
                                            folderColorDraft.coerceIn(0, 9),
                                        )
                                    } else {
                                        f
                                    }
                                }
                            } else {
                                val id = "f_${System.currentTimeMillis()}"
                                activeFolderId = id
                                folders +
                                    org.assistix.proto.nativeapp.data.ChatFolder(
                                        id,
                                        name,
                                        folderPickIds,
                                        folderColorDraft.coerceIn(0, 9),
                                    )
                            }
                        saveFoldersSynced(next)
                        showFolderEditor = false
                        editingFolderId = null
                    }
                },
            )
        }
    }
}

@Composable
private fun GroupPickerDialog(
    title: String,
    groups: List<ConvItem>,
    onDismiss: () -> Unit,
    onPick: (ConvItem) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = ProtoShapes.dialog,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
        text = {
            if (groups.isEmpty()) {
                Text(UiStrings.groupNeedTitleMembers, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(
                    Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groups.forEach { g ->
                        ProtoSurfaceCard(onClick = { onPick(g) }) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Groups, null, tint = ProtoOrange)
                                Spacer(Modifier.size(12.dp))
                                Text(g.title, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(UiStrings.close, color = MaterialTheme.colorScheme.primary) }
        },
    )
}

private data class PendingVaultChat(
    val id: Int,
    val title: String,
    val kind: String,
    val peerId: Int,
)

private fun navigateToConversation(
    nav: androidx.navigation.NavHostController,
    id: Int,
    title: String,
    kind: String,
    peerId: Int = 0,
    builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {},
) {
    val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
    if (kind == "channel") {
        nav.navigate("channel_feed/$id/$enc", builder)
    } else {
        nav.navigate("chat/$id/$enc/$kind/$peerId", builder)
    }
}

private fun formatChatListTime(ms: Long): String {
    if (ms <= 0L) return ""
    val msgCal = Calendar.getInstance().apply { timeInMillis = ms }
    val nowCal = Calendar.getInstance()
    val sameDay =
        msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    if (sameDay) {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    }
    val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
    if (msgCal.after(weekAgo)) {
        return SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
    }
    return SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(ms))
}
