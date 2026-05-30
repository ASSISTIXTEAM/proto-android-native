package org.assistix.proto.nativeapp

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
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
import org.assistix.proto.nativeapp.data.ProtoCrashReporter
import org.assistix.proto.nativeapp.data.ProtoCacheManager
import org.assistix.proto.nativeapp.data.ProtoMediaResolver
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
    lateinit var cellsManager: org.assistix.proto.nativeapp.data.cells.ProtoCellsManager
    lateinit var cellsP2p: org.assistix.proto.nativeapp.data.cells.ProtoCellsP2pManager
    lateinit var mediaResolver: ProtoMediaResolver
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

    private val scopeHandler =
        CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "applicationScope error", e)
        }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + scopeHandler)

    override fun onCreate() {
        super.onCreate()
        try {
            initApplication()
        } catch (e: Exception) {
            Log.e(TAG, "initApplication failed — minimal fallback", e)
            ensureMinimalServices()
        }
    }

    private fun initApplication() {
        runCatching { ProtoPersistentStorage.initAndMigrate(this) }
            .onFailure { e -> Log.w(TAG, "storage init", e) }
        org.assistix.proto.nativeapp.data.ProtoLinkPreviewCache.attach(this)
        ProtoApiOrigin.init(this)
        ProtoApiOrigin.clearPreferredIfBroken(this)

        session = ProtoSessionStore(this)
        pendingVerification = ProtoPendingVerificationStore(this)
        api = ProtoApi(this)
        prefs = ProtoAppPreferences(this)
        ProtoCrashReporter.install(this, applicationScope, prefs)
        chatLocalPrefs = ProtoChatLocalPrefs(this)
        draftPrefs = ProtoDraftPrefs(this)
        offlineVault = org.assistix.proto.nativeapp.data.ProtoOfflineVault(this)
        themeStore = ProtoThemeStore(this)
        notifier = ProtoNotifier(this)
        network = ProtoNetworkMonitor(this)
        network.attach(applicationScope)

        profileCache = org.assistix.proto.nativeapp.data.ProtoProfileCache(offlineVault, api)
        connectivity = org.assistix.proto.nativeapp.data.ProtoConnectivityAdvisor(this, network, api)
        cache = ProtoCacheManager(this)
        val cellsP2p = org.assistix.proto.nativeapp.data.cells.ProtoCellsP2pManager(this, api, network) { if (::calls.isInitialized) calls else null }
        cellsManager =
            org.assistix.proto.nativeapp.data.cells.ProtoCellsManager(
                this,
                api,
                network,
                cellsP2p,
            ) {
                if (::session.isInitialized) {
                    kotlinx.coroutines.runBlocking { session.userId() }
                } else {
                    0
                }
            }

        val dao =
            runCatching { ProtoDatabase.get(this).dao() }
                .getOrElse {
                    Log.e(TAG, "database unavailable", it)
                    throw it
                }

        mediaResolver = ProtoMediaResolver(this, dao, cache, api, cellsManager)
        messages = ProtoMessageRepository(dao, api)
        conversations = ProtoConversationRepository(dao, api)
        assistixChat = AssistixChatRepository(dao)
        stt = ProtoSttCoordinator(this, api, network, prefs, notifier)
        sttQueue = ProtoSttQueue(this, applicationScope, stt, api, cache, prefs)
        cachePrefetch = ProtoCachePrefetcher(dao, cache, api, messages, conversations, mediaResolver)
        appUpdate = ProtoAppUpdateManager(this, api, prefs)
        val callMgr = ProtoCallManager(applicationContext, api, notifier, prefs)
        calls = callMgr
        callMgr.onCallEnded = { info -> applicationScope.launch { logCall(info) } }

        applicationScope.launch {
            runCatching { draftPrefs.ensureRecovered() }
            runCatching { connectivity.refresh() }
        }
        applicationScope.launch {
            network.onlineFlow.collect { online ->
                if (online) runCatching { connectivity.refresh() }
            }
        }
        runCatching { setupImageLoader() }.onFailure { e -> Log.w(TAG, "ImageLoader setup failed", e) }
        WidgetRefreshScheduler.schedulePeriodic(this)
        AppUpdateScheduler.schedule(this)
        applicationScope.launch {
            delay(2500)
            runCatching { appUpdate.refresh(silent = true) }
        }
        applicationScope.launch {
            delay(8000)
            runCatching { stt.ensurePackHealthy(applicationScope) }
        }
        applicationScope.launch {
            delay(20_000)
            runCatching { stt.warmupModel() }
        }
        startBackgroundJobs()
    }

    /** Last-resort init so UI never crashes on lateinit. */
    private fun ensureMinimalServices() {
        if (!::network.isInitialized) network = ProtoNetworkMonitor(this)
        if (!::api.isInitialized) api = ProtoApi(this)
        if (!::session.isInitialized) session = ProtoSessionStore(this)
        if (!::prefs.isInitialized) prefs = ProtoAppPreferences(this)
        if (!::themeStore.isInitialized) themeStore = ProtoThemeStore(this)
        if (!::notifier.isInitialized) notifier = ProtoNotifier(this)
        if (!::cellsP2p.isInitialized) {
            cellsP2p = org.assistix.proto.nativeapp.data.cells.ProtoCellsP2pManager(this, api, network) { if (::calls.isInitialized) calls else null }
        }
        if (!::cellsManager.isInitialized) {
            cellsManager =
                org.assistix.proto.nativeapp.data.cells.ProtoCellsManager(
                    this,
                    api,
                    network,
                    cellsP2p,
                ) {
                if (::session.isInitialized) {
                    kotlinx.coroutines.runBlocking { session.userId() }
                } else {
                    0
                }
            }
        }
        if (!::cache.isInitialized) cache = ProtoCacheManager(this)
        if (!::pendingVerification.isInitialized) pendingVerification = ProtoPendingVerificationStore(this)
        if (!::chatLocalPrefs.isInitialized) chatLocalPrefs = ProtoChatLocalPrefs(this)
        if (!::draftPrefs.isInitialized) draftPrefs = ProtoDraftPrefs(this)
        if (!::offlineVault.isInitialized) offlineVault = org.assistix.proto.nativeapp.data.ProtoOfflineVault(this)
        if (!::profileCache.isInitialized) {
            profileCache = org.assistix.proto.nativeapp.data.ProtoProfileCache(offlineVault, api)
        }
        if (!::connectivity.isInitialized) {
            connectivity = org.assistix.proto.nativeapp.data.ProtoConnectivityAdvisor(this, network, api)
        }
        runCatching {
            val dao = ProtoDatabase.get(this).dao()
            if (!::mediaResolver.isInitialized) {
                mediaResolver = ProtoMediaResolver(this, dao, cache, api, cellsManager)
            }
            if (!::messages.isInitialized) messages = ProtoMessageRepository(dao, api)
            if (!::conversations.isInitialized) conversations = ProtoConversationRepository(dao, api)
            if (!::assistixChat.isInitialized) assistixChat = AssistixChatRepository(dao)
            if (!::cachePrefetch.isInitialized) {
                cachePrefetch = ProtoCachePrefetcher(dao, cache, api, messages, conversations, mediaResolver)
            }
        }.onFailure { e -> Log.e(TAG, "minimal db layer failed", e) }
        if (!::stt.isInitialized) stt = ProtoSttCoordinator(this, api, network, prefs, notifier)
        if (!::sttQueue.isInitialized) {
            sttQueue = ProtoSttQueue(this, applicationScope, stt, api, cache, prefs)
        }
        if (!::appUpdate.isInitialized) appUpdate = ProtoAppUpdateManager(this, api, prefs)
        if (!::calls.isInitialized) {
            val callMgr = ProtoCallManager(applicationContext, api, notifier, prefs)
            calls = callMgr
            callMgr.onCallEnded = { info -> applicationScope.launch { logCall(info) } }
        }
        if (::messages.isInitialized && ::conversations.isInitialized) {
            startBackgroundJobs()
        }
    }

    private fun startBackgroundJobs() {
        if (!::prefs.isInitialized || !::session.isInitialized || !::network.isInitialized) return
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
        if (!::messages.isInitialized || !::conversations.isInitialized || !::cachePrefetch.isInitialized) return
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
                delay(120_000)
                runCatching {
                    val token = session.token() ?: return@runCatching
                    if (network.checkOnline()) {
                        cellsManager.runMaintenance(token)
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
        if (!::messages.isInitialized) return
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
        runCatching { messages.insertCallLog(token, info.conversationId, meta, mine = !info.incoming) }
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
