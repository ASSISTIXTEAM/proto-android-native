package org.assistix.proto.nativeapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import org.assistix.proto.nativeapp.data.ProtoAppNavigation
import org.assistix.proto.nativeapp.data.ProtoPersistentStorage
import org.assistix.proto.nativeapp.data.ProtoHosts
import org.assistix.proto.nativeapp.data.ProtoShareIntentParser
import org.assistix.proto.nativeapp.data.ProtoShareState
import org.assistix.proto.nativeapp.data.ProtoThemeMode
import org.assistix.proto.nativeapp.update.AppUpdatePhase
import org.assistix.proto.nativeapp.ui.AppUpdatePromptDialog
import org.assistix.proto.nativeapp.ui.MandatoryUpdateScreen
import org.assistix.proto.nativeapp.ui.CallOverlay
import org.assistix.proto.nativeapp.ui.ProtoNavHost
import org.assistix.proto.nativeapp.ui.ProtoTheme
import org.assistix.proto.nativeapp.ui.l10n.AppLocale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ProtoApplication
        handleCallIntent(intent, app)
        handleOpenChatIntent(intent)
        handleDeepLinkIntent(intent)
        handleShareIntent(intent)
        handleUpdateAction(intent, app)
        setContent {
            ProtoRootContent(app)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallIntent(intent, application as ProtoApplication)
        handleOpenChatIntent(intent)
        handleDeepLinkIntent(intent)
        handleShareIntent(intent)
        handleUpdateAction(intent, application as ProtoApplication)
    }

    private fun handleUpdateAction(intent: Intent?, app: ProtoApplication) {
        val action = intent?.getStringExtra(EXTRA_UPDATE_ACTION) ?: return
        intent.removeExtra(EXTRA_UPDATE_ACTION)
        lifecycleScope.launch {
            when (action) {
                org.assistix.proto.nativeapp.update.ProtoUpdateNotifier.ACTION_DOWNLOAD -> {
                    var info = app.prefs.getCachedUpdateInfo()
                    if (info == null) {
                        app.appUpdate.refresh(silent = false)
                        info = app.prefs.getCachedUpdateInfo()
                    }
                    info?.let { app.appUpdate.downloadUpdate(it, silent = false) }
                }
                org.assistix.proto.nativeapp.update.ProtoUpdateNotifier.ACTION_INSTALL -> {
                    app.appUpdate.installPendingApk()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val app = application as? ProtoApplication ?: return
        lifecycleScope.launch {
            app.chatLocalPrefs.lockVault()
        }
    }

    private fun handleOpenChatIntent(intent: Intent?) {
        val cid = intent?.getIntExtra("open_conversation_id", -1) ?: -1
        if (cid > 0) {
            ProtoAppNavigation.setPending(
                conversationId = cid,
                title = intent?.getStringExtra("open_conversation_title") ?: "",
                kind = intent?.getStringExtra("open_conversation_kind") ?: "dm",
                peerUserId = intent?.getIntExtra("open_conversation_peer_id", 0) ?: 0,
            )
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val code = inviteCodeFromUri(uri) ?: return
        ProtoAppNavigation.setPendingInvite(code)
    }

    private fun inviteCodeFromUri(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme == "proto") {
            when (uri.host) {
                "l" -> {
                    val seg = uri.pathSegments.firstOrNull() ?: return null
                    return seg.takeIf { it.matches(Regex("[a-f0-9]{16}", RegexOption.IGNORE_CASE)) }?.lowercase()
                }
                "u" -> {
                    val nick = uri.pathSegments.firstOrNull()?.trim()?.removePrefix("@") ?: return null
                    if (nick.matches(Regex("[A-Za-z0-9_]{2,32}"))) {
                        ProtoAppNavigation.queueProfileNick(nick)
                    }
                    return null
                }
                "channel" -> {
                    val nick = uri.pathSegments.firstOrNull()?.trim()?.removePrefix("@") ?: return null
                    if (nick.matches(Regex("[a-z][a-z0-9_]{2,31}", RegexOption.IGNORE_CASE))) {
                        ProtoAppNavigation.queueChannel(nick.lowercase(), autoSubscribe = true)
                    }
                    return null
                }
            }
        }
        if ((uri.scheme == "https" || uri.scheme == "http") && ProtoHosts.isWebLinkHost(host)) {
            val pairId = uri.getQueryParameter("p")?.trim().orEmpty()
            val secret = uri.getQueryParameter("s")?.trim().orEmpty()
            if (pairId.isNotEmpty() && secret.isNotEmpty()) {
                ProtoAppNavigation.queueDeviceLink(pairId, secret)
                return null
            }
            val parts = uri.pathSegments
            if (parts.isNotEmpty() && parts[0] == "app") {
                val p = uri.getQueryParameter("p")?.trim().orEmpty()
                val s = uri.getQueryParameter("s")?.trim().orEmpty()
                if (p.isNotEmpty() && s.isNotEmpty()) {
                    ProtoAppNavigation.queueDeviceLink(p, s)
                    return null
                }
            }
            if (parts.size >= 2 && parts[0] == "l") {
                val code = parts[1]
                if (code.matches(Regex("[a-f0-9]{16}", RegexOption.IGNORE_CASE))) return code.lowercase()
            }
            if (parts.size >= 2 && parts[0] == "u") {
                val nick = parts[1].removePrefix("@")
                if (nick.matches(Regex("[A-Za-z0-9_]{2,32}"))) {
                    ProtoAppNavigation.queueProfileNick(nick)
                }
            }
            if (parts.size >= 2 && parts[0] == "c") {
                val nick = parts[1].removePrefix("@")
                if (nick.matches(Regex("[a-z][a-z0-9_]{2,31}", RegexOption.IGNORE_CASE))) {
                    ProtoAppNavigation.queueChannel(nick.lowercase(), autoSubscribe = true)
                }
            }
        }
        return null
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val isShareAction =
            action == Intent.ACTION_SEND ||
                action == Intent.ACTION_SEND_MULTIPLE ||
                action == ACTION_SHARE_TO_CHAT
        if (!isShareAction) return
        val payload = ProtoShareIntentParser.parse(this, intent) ?: return
        val targetCid = intent.getIntExtra(EXTRA_SHARE_CONVERSATION_ID, -1)
        if (targetCid > 0) {
            ProtoShareState.queueDirect(targetCid, payload)
        } else {
            ProtoShareState.start(payload)
        }
        intent.action = null
    }

    companion object {
        const val EXTRA_UPDATE_ACTION = "proto_update_action"
        const val ACTION_SHARE_TO_CHAT = "org.assistix.proto.SHARE_TO_CHAT"
        const val EXTRA_SHARE_CONVERSATION_ID = "share_conversation_id"
    }

    private fun handleCallIntent(intent: Intent?, app: ProtoApplication) {
        val incomingCid = intent?.getIntExtra("incoming_call_cid", -1) ?: -1
        if (incomingCid > 0) {
            runCatching { app.calls.prioritizeIncomingPoll(incomingCid) }
        }
    }
}

@Composable
private fun ProtoRootContent(app: ProtoApplication) {
    val themeMode by app.themeStore.mode.collectAsState(initial = ProtoThemeMode.DARK)
    val callState by app.calls.state.collectAsState()
    val authToken by app.session.tokenFlow.collectAsState(initial = null)
    val updatePhase by app.appUpdate.phase.collectAsState()
    val mandatoryInfo by app.appUpdate.mandatoryBlock.collectAsState()
    var showUpdatePrompt by remember { mutableStateOf(false) }
    var promptInfo by remember {
        mutableStateOf<org.assistix.proto.nativeapp.update.AppUpdateInfo?>(null)
    }
    var pendingAfterPerm by remember { mutableStateOf<(() -> Unit)?>(null) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        app.appUpdate.refresh(silent = true)
        if (!ProtoPersistentStorage.isRootReady(context) && ProtoPersistentStorage.needsAllFilesAccess(context)) {
            ProtoPersistentStorage.openAllFilesAccessSettings(context)
        }
    }

    LaunchedEffect(mandatoryInfo, updatePhase) {
        if (mandatoryInfo != null) {
            app.appUpdate.tryMandatoryInstallIfReady()
        }
    }

    LaunchedEffect(mandatoryInfo) {
        val info = mandatoryInfo ?: return@LaunchedEffect
        while (true) {
            delay(5 * 60 * 1000L)
            app.appUpdate.refresh(silent = true)
            if (app.appUpdate.mandatoryBlock.value == null) break
        }
    }

    LaunchedEffect(authToken, updatePhase, mandatoryInfo) {
        if (mandatoryInfo != null) return@LaunchedEffect
        if (authToken.isNullOrBlank()) return@LaunchedEffect
        when (val p = updatePhase) {
            is AppUpdatePhase.Available -> {
                if (app.appUpdate.shouldShowPrompt(p.info)) {
                    promptInfo = p.info
                    showUpdatePrompt = true
                }
            }
            is AppUpdatePhase.Ready -> {
                val info = p.info
                if (app.appUpdate.shouldShowPrompt(info)) {
                    promptInfo = info
                    showUpdatePrompt = true
                }
            }
            else -> Unit
        }
    }
    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                pendingAfterPerm?.invoke()
            }
            pendingAfterPerm = null
        }
    val blockingUpdate = mandatoryInfo
    if (blockingUpdate != null) {
        ProtoTheme(themeMode) {
            AppLocale.Provide {
                MandatoryUpdateScreen(app.appUpdate, blockingUpdate)
            }
        }
        return
    }

    ProtoTheme(themeMode) {
        AppLocale.Provide {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    ProtoNavHost(
                        app = app,
                        onNeedCallPermissions = { afterGranted ->
                            pendingAfterPerm = afterGranted
                            permLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.MODIFY_AUDIO_SETTINGS,
                                ),
                            )
                        },
                    )
                    if (callState.active && !callState.incoming) {
                        CallOverlay(
                            state = callState,
                            api = app.api,
                            token = authToken,
                            onAccept = { runCatching { app.calls.acceptIncoming() } },
                            onDecline = { runCatching { app.calls.declineIncoming() } },
                            onHangup = { runCatching { app.calls.hangup() } },
                            onToggleMute = { runCatching { app.calls.toggleMute() } },
                            onToggleSpeaker = { runCatching { app.calls.toggleSpeaker() } },
                            onToggleVideo = { runCatching { app.calls.toggleVideo() } },
                            onFlipCamera = { runCatching { app.calls.flipCamera() } },
                            onSendReaction = { emoji -> runCatching { app.calls.sendCallReaction(emoji) } },
                            calls = app.calls,
                        )
                    }
                }
            }
        }
    }
    val info = promptInfo
    if (showUpdatePrompt && info != null) {
        AppUpdatePromptDialog(
            updateManager = app.appUpdate,
            info = info,
            onDismiss = { showUpdatePrompt = false },
        )
    }
}
