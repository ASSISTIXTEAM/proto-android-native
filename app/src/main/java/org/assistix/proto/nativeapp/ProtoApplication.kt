package org.assistix.proto.nativeapp

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.assistix.proto.nativeapp.data.CallEndInfo
import org.assistix.proto.nativeapp.data.CallMeta
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoApiOrigin
import org.assistix.proto.nativeapp.data.ProtoAppPreferences
import org.assistix.proto.nativeapp.data.ProtoCallGateway
import org.assistix.proto.nativeapp.data.ProtoCallManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.assistix.proto.nativeapp.data.AssistixChatRepository
import org.assistix.proto.nativeapp.data.ProtoCachePrefetcher
import org.assistix.proto.nativeapp.data.ProtoCacheManager
import org.assistix.proto.nativeapp.data.ProtoChatBackup
import org.assistix.proto.nativeapp.data.ProtoChatLocalPrefs
import org.assistix.proto.nativeapp.data.ProtoConversationRepository
import org.assistix.proto.nativeapp.data.ProtoDraftPrefs
import org.assistix.proto.nativeapp.data.ProtoMessageRepository
import org.assistix.proto.nativeapp.data.ProtoNetworkMonitor
import org.assistix.proto.nativeapp.data.ProtoNotifier
import org.assistix.proto.nativeapp.data.ProtoPendingVerificationStore
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.ProtoPersistentStorage
import org.assistix.proto.nativeapp.data.ProtoSttCoordinator
import org.assistix.proto.nativeapp.data.ProtoSttQueue
import org.assistix.proto.nativeapp.data.ProtoThemeStore
import org.assistix.proto.nativeapp.data.local.ProtoDatabase
import org.assistix.proto.nativeapp.ui.UiStrings
import org.assistix.proto.nativeapp.ui.l10n.AppLocale
import org.assistix.proto.nativeapp.update.AppUpdateScheduler
import org.assistix.proto.nativeapp.update.ProtoAppUpdateManager
import org.assistix.proto.nativeapp.widget.WidgetRefreshScheduler
import org.assistix.proto.nativeapp.widget.WidgetRepository

class ProtoApplication : Application() {
    lateinit var session: ProtoSessionStore
    lateinit var pendingVerification: ProtoPendingVerificationStore
    lateinit var api: ProtoApi
    lateinit var messages: ProtoMessageRepository
    lateinit var conversations: ProtoConversationRepository
    lateinit var assistixChat: AssistixChatRepository
    lateinit var cache: ProtoCacheManager
    lateinit var cachePrefetch: ProtoCachePrefetcher
    lateinit var network: ProtoNetworkMonitor
    lateinit var calls: ProtoCallGateway
    lateinit var notifier: ProtoNotifier
    lateinit var themeStore: ProtoThemeStore
    lateinit var prefs: ProtoAppPreferences
    lateinit var chatLocalPrefs: ProtoChatLocalPrefs
    lateinit var draftPrefs: ProtoDraftPrefs
    lateinit var offlineVault: org.assistix.proto.nativeapp.data.ProtoOfflineVault
    lateinit var profileCache: org.assistix.proto.nativeapp.data.ProtoProfileCache
    lateinit var connectivity: org.assistix.proto.nativeapp.data.ProtoConnectivityAdvisor
    lateinit var stt: ProtoSttCoordinator
    lateinit var sttQueue: ProtoSttQueue
    lateinit var appUpdate: ProtoAppUpdateManager

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        runCatching {
            ProtoPersistentStorage.initAndMigrate(this)
            org.assistix.proto.nativeapp.data.ProtoLinkPreviewCache.attach(this)
            ProtoApiOrigin.init(this)
            ProtoApiOrigin.clearPreferredIfBroken(this)
            session = ProtoSessionStore(this)
            pendingVerification = ProtoPendingVerificationStore(this)
            api = ProtoApi(this)
            prefs = ProtoAppPreferences(this)
            chatLocalPrefs = ProtoChatLocalPrefs(this)
            draftPrefs = ProtoDraftPrefs(this)
            offlineVault = org.assistix.proto.nativeapp.data.ProtoOfflineVault(this)
            profileCache = org.assistix.proto.nativeapp.data.ProtoProfileCache(offlineVault, api)
            themeStore = ProtoThemeStore(this)
            notifier = ProtoNotifier(this)
            network = ProtoNetworkMonitor(this)
            network.attach(applicationScope)
            connectivity = org.assistix.proto.nativeapp.data.ProtoConnectivityAdvisor(this, network, api)
            applicationScope.launch {
                runCatching { draftPrefs.ensureRecovered() }
                runCatching { connectivity.refresh() }
            }
            applicationScope.launch {
                network.onlineFlow.collect { online ->
                    if (online) runCatching { connectivity.refresh() }
                }
            }
            cache = ProtoCacheManager(this)
            stt = ProtoSttCoordinator(this, api, network, prefs, notifier)
            sttQueue = ProtoSttQueue(this, applicationScope, stt, api, cache, prefs)
            val dao = ProtoDatabase.get(this).dao()
            messages = ProtoMessageRepository(dao, api)
            conversations = ProtoConversationRepository(dao, api)
            assistixChat = AssistixChatRepository(dao)
            cachePrefetch = ProtoCachePrefetcher(dao, cache, api, messages, conversations)
            appUpdate = ProtoAppUpdateManager(this, api, prefs)
            val callMgr = ProtoCallManager(applicationContext, api, notifier, prefs)
            calls = callMgr
            callMgr.onCallEnded = { info -> applicationScope.launch { logCall(info) } }
            runCatching { setupImageLoader() }.onFailure { e ->
                Log.w(TAG, "ImageLoader setup failed", e)
            }
            WidgetRefreshScheduler.schedulePeriodic(this)
            AppUpdateScheduler.schedule(this)
            applicationScope.launch {
                delay(2500)
                runCatching { appUpdate.refresh(silent = true) }
            }
            applicationScope.launch {
                delay(8000)
                stt.ensurePackHealthy(applicationScope)
            }
            applicationScope.launch {
                delay(20_000)
                runCatching { stt.warmupModel() }
            }
            startBackgroundJobs()
        }.onFailure { e ->
            Log.e(TAG, "onCreate failed", e)
        }
    }

    private fun startBackgroundJobs() {
        applicationScope.launch {
            runCatching {
                val initial = prefs.languageCodeFlow.first()
                AppLocale.setLanguage(initial)
                UiStrings.setLanguage(initial)
                prefs.languageCodeFlow
                    .catch { e -> Log.w(TAG, "language flow", e) }
                    .collect { code ->
                        AppLocale.setLanguage(code)
                        UiStrings.setLanguage(code)
                    }
            }
        }
        applicationScope.launch {
            network.onlineFlow.collect { online ->
                if (online) {
                    runCatching {
                        val token = session.token() ?: return@runCatching
                        messages.flushOutbox(token)
                        conversations.syncFromServer(token)
                        val uid = session.userId()
                        if (uid > 0) {
                            val autoDl = prefs.autoDownload.first()
                            if (autoDl && network.isOnWifi()) {
                                cachePrefetch.warmAll(token, uid, wifiOnly = false)
                            }
                        }
                    }
                }
            }
        }
        applicationScope.launch {
            session.tokenFlow.collect { token ->
                if (token.isNullOrBlank() || !network.checkOnline()) return@collect
                val uid = session.userId()
                if (uid <= 0) return@collect
                runCatching { cachePrefetch.warmAll(token, uid, wifiOnly = false) }
            }
        }
        applicationScope.launch {
            while (isActive) {
                delay(7000)
                runCatching {
                    val token = session.token() ?: return@runCatching
                    if (network.checkOnline()) {
                        messages.flushOutbox(token)
                    }
                }
            }
        }
        applicationScope.launch {
            while (isActive) {
                delay(45_000)
                runCatching {
                    if (session.token() != null) {
                        WidgetRepository.refresh(applicationContext)
                    }
                }
            }
        }
        applicationScope.launch {
            while (isActive) {
                delay(60_000)
                runCatching {
                    val token = session.token() ?: return@runCatching
                    ProtoChatBackup.runIfDue(
                        applicationContext,
                        ProtoDatabase.get(this@ProtoApplication).dao(),
                        api,
                        token,
                    )
                }
            }
        }
    }

    private suspend fun logCall(info: CallEndInfo) {
        val token = session.token() ?: return
        val status =
            when (info.status) {
                "answered", "missed", "declined", "cancelled" -> info.status
                else ->
                    when {
                        info.answered -> "answered"
                        info.incoming -> "missed"
                        else -> "cancelled"
                    }
            }
        val meta =
            CallMeta(
                direction = if (info.incoming) "in" else "out",
                status = status,
                video = info.withVideo,
                durationSec = info.durationSec,
                peerLabel = info.peerLabel,
            )
        messages.insertCallLog(token, info.conversationId, meta, mine = !info.incoming)
    }

    private fun setupImageLoader() {
        val coilDir = java.io.File(cache.photosDir, "coil").apply { mkdirs() }
        val loader =
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.15)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(coilDir)
                        .maxSizeBytes(96L * 1024 * 1024)
                        .build()
                }
                .build()
        coil.Coil.setImageLoader(loader)
    }

    companion object {
        private const val TAG = "ProtoApp"
    }
}
