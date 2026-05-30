package org.assistix.proto.nativeapp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assistix.proto.nativeapp.BuildConfig
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoAppPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class ProtoAppUpdateManager(
    private val context: Context,
    private val api: ProtoApi,
    private val prefs: ProtoAppPreferences,
) {
    private val _phase = MutableStateFlow<AppUpdatePhase>(AppUpdatePhase.Idle)
    val phase: StateFlow<AppUpdatePhase> = _phase.asStateFlow()

    private val _mandatoryBlock = MutableStateFlow<AppUpdateInfo?>(null)
    val mandatoryBlock: StateFlow<AppUpdateInfo?> = _mandatoryBlock.asStateFlow()

    private var lastAutoInstallAt = 0L
    private val downloadMutex = Mutex()
    private var activeDownloadVersion: Int? = null
    @Volatile private var displayedProgress = 0f
    @Volatile private var readyUpdateInfo: AppUpdateInfo? = null
    @Volatile private var lastNotifProgressPostMs = 0L
    @Volatile private var lastNotifiedProgress = -1f

    private val downloadClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    private val updateNotifier = ProtoUpdateNotifier(context)

    private fun updatesDir(): File = File(context.cacheDir, "updates").also { it.mkdirs() }

    fun installedVersionCode(): Int = BuildConfig.VERSION_CODE

    fun installedVersionName(): String = BuildConfig.VERSION_NAME

    fun pendingApkFile(): File? {
        val f = File(updatesDir(), APK_FILE_NAME)
        return f.takeIf { it.exists() && it.length() > 1024 }
    }

    private suspend fun syncMandatoryBlock(info: AppUpdateInfo?) {
        _mandatoryBlock.value = info?.takeIf { it.isMandatoryFor(installedVersionCode()) }
    }

    private suspend fun restoreMandatoryFromCache() {
        val cached = prefs.getCachedUpdateInfo() ?: return
        if (!cached.isMandatoryFor(installedVersionCode())) {
            clearMandatoryState()
            return
        }
        syncMandatoryBlock(cached)
        val pending = pendingApkFile()
        if (pending != null) {
            readyUpdateInfo = cached
            displayedProgress = 1f
            _phase.value = AppUpdatePhase.Ready(cached, pending.name)
        }
    }

    private suspend fun clearMandatoryState() {
        _mandatoryBlock.value = null
    }

    private suspend fun clearUpdateArtifacts() {
        clearMandatoryState()
        prefs.clearCachedUpdateInfo()
        pendingApkFile()?.delete()
        activeDownloadVersion = null
        displayedProgress = 0f
        readyUpdateInfo = null
        updateNotifier.cancel()
        _phase.value = AppUpdatePhase.UpToDate
    }

    private suspend fun postUpdateNotification(info: AppUpdateInfo, phase: UpdateNotifPhase) {
        val mandatory = info.isMandatoryFor(installedVersionCode())
        val notified = prefs.getUpdateNotifiedVersionCode()
        val shouldAlert = mandatory || info.force || notified < info.versionCode
        if (!shouldAlert && phase !is UpdateNotifPhase.Downloading && phase != UpdateNotifPhase.Ready) return
        updateNotifier.notifyUpdate(info, phase)
        if (phase is UpdateNotifPhase.Available || phase is UpdateNotifPhase.Mandatory || phase == UpdateNotifPhase.Ready) {
            if (notified < info.versionCode) {
                prefs.setUpdateNotifiedVersionCode(info.versionCode)
            }
        }
    }

    private fun setDownloadProgress(info: AppUpdateInfo, raw: Float) {
        val next = raw.coerceIn(0f, 1f)
        val smooth = maxOf(displayedProgress, next)
        if (smooth - displayedProgress < 0.004f && smooth < 0.995f) return
        displayedProgress = smooth
        _phase.value = AppUpdatePhase.Downloading(info, smooth)
        val now = System.currentTimeMillis()
        val step = (smooth * 100).toInt()
        val prevStep = (lastNotifiedProgress * 100).toInt()
        if (step > prevStep && (now - lastNotifProgressPostMs > 450 || smooth >= 0.99f)) {
            lastNotifProgressPostMs = now
            lastNotifiedProgress = smooth
            updateNotifier.notifyUpdate(info, UpdateNotifPhase.Downloading(smooth))
        }
    }

    suspend fun refresh(silent: Boolean = false) {
        if (_phase.value is AppUpdatePhase.Downloading) return

        val installed = installedVersionCode()
        prefs.getCachedUpdateInfo()?.let { cached ->
            if (cached.satisfiesInstalled(installed)) {
                clearUpdateArtifacts()
            }
        }

        restoreMandatoryFromCache()

        val pending = pendingApkFile()
        val pendingInfo = prefs.getCachedUpdateInfo()
        if (pending != null && pendingInfo != null && pendingInfo.isNewerThan(installedVersionCode())) {
            readyUpdateInfo = pendingInfo
            displayedProgress = 1f
            _phase.value = AppUpdatePhase.Ready(pendingInfo, pending.name)
            syncMandatoryBlock(pendingInfo)
            if (pendingInfo.isMandatoryFor(installedVersionCode())) {
                tryMandatoryInstallIfReady()
            }
            return
        }

        _phase.value = AppUpdatePhase.Checking

        val result =
            withContext(Dispatchers.IO) {
                api.checkAppUpdate(installedVersionCode(), installedVersionName())
            }

        prefs.setLastUpdateCheckAt(System.currentTimeMillis())

        when (result) {
            is AppUpdateCheckResult.UpToDate -> {
                clearUpdateArtifacts()
            }
            is AppUpdateCheckResult.Available -> {
                val info = result.info
                prefs.cacheUpdateInfo(info)
                syncMandatoryBlock(info)
                if (_phase.value !is AppUpdatePhase.Downloading) {
                    _phase.value = AppUpdatePhase.Available(info)
                }
                val mandatory = info.isMandatoryFor(installedVersionCode())
                postUpdateNotification(
                    info,
                    if (mandatory) UpdateNotifPhase.Mandatory else UpdateNotifPhase.Available,
                )
                val autoDl = mandatory || prefs.autoAppUpdate.first()
                if (autoDl && activeDownloadVersion != info.versionCode) {
                    downloadUpdate(info, silent = true)
                }
            }
            is AppUpdateCheckResult.Failed -> {
                restoreMandatoryFromCache()
                if (!silent) {
                    _phase.value = AppUpdatePhase.Error(result.kind)
                } else if (_phase.value !is AppUpdatePhase.Ready && _phase.value !is AppUpdatePhase.Downloading) {
                    _phase.value = AppUpdatePhase.Idle
                }
            }
        }
    }

    suspend fun downloadUpdate(info: AppUpdateInfo, silent: Boolean = false) {
        if (info.apkUrl.isBlank()) return

        downloadMutex.withLock {
            if (activeDownloadVersion == info.versionCode &&
                (_phase.value is AppUpdatePhase.Downloading || _phase.value is AppUpdatePhase.Ready)
            ) {
                return
            }
            activeDownloadVersion = info.versionCode
            if (_phase.value !is AppUpdatePhase.Downloading) {
                displayedProgress = 0f
                setDownloadProgress(info, 0.02f)
                postUpdateNotification(info, UpdateNotifPhase.Downloading(0.05f))
            }
        }

        val ok =
            withContext(Dispatchers.IO) {
                runCatching { downloadApk(info) }.getOrElse {
                    Log.e(TAG, "download failed", it)
                    false
                }
            }

        downloadMutex.withLock {
            if (activeDownloadVersion != info.versionCode) return@withLock
            if (ok) {
                prefs.cacheUpdateInfo(info)
                readyUpdateInfo = info
                displayedProgress = 1f
                _phase.value = AppUpdatePhase.Ready(info, APK_FILE_NAME)
                activeDownloadVersion = null
                postUpdateNotification(info, UpdateNotifPhase.Ready)
                if (info.isMandatoryFor(installedVersionCode())) {
                    tryMandatoryInstallIfReady()
                }
            } else {
                activeDownloadVersion = null
                if (!silent || info.isMandatoryFor(installedVersionCode())) {
                    _phase.value = AppUpdatePhase.Error("download_failed")
                } else if (displayedProgress < 0.05f) {
                    _phase.value = AppUpdatePhase.Available(info)
                }
            }
        }
    }

    fun tryMandatoryInstallIfReady(): Boolean {
        if (_mandatoryBlock.value == null) return false
        if (_phase.value !is AppUpdatePhase.Ready) return false
        if (!canInstallPackages()) return false
        val now = System.currentTimeMillis()
        if (now - lastAutoInstallAt < 10_000) return false
        lastAutoInstallAt = now
        return installPendingApk()
    }

    private fun downloadUrls(info: AppUpdateInfo): List<String> {
        val canonical = CANONICAL_APK_URL
        return listOf(canonical, info.apkUrl)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("download-apk") }
            .distinct()
            .ifEmpty { listOf(canonical) }
    }

    private fun downloadApk(info: AppUpdateInfo): Boolean {
        val urls = downloadUrls(info)
        val urlCount = urls.size.coerceAtLeast(1)
        urls.forEachIndexed { index, url ->
            val sliceStart = index.toFloat() / urlCount
            val sliceEnd = (index + 1).toFloat() / urlCount
            if (downloadApkFromUrl(info, url, sliceStart, sliceEnd)) {
                setDownloadProgress(info, 1f)
                return true
            }
        }
        return false
    }

    private fun downloadApkFromUrl(
        info: AppUpdateInfo,
        url: String,
        sliceStart: Float,
        sliceEnd: Float,
    ): Boolean {
        val dest = File(updatesDir(), APK_FILE_NAME)
        if (dest.exists()) dest.delete()

        val req =
            Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.android.package-archive,*/*")
                .header("User-Agent", "PROTO-Android/${installedVersionName()} (${installedVersionCode()})")
                .get()
                .build()
        return try {
            downloadClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "download HTTP ${res.code} from $url")
                    return false
                }
                val body = res.body ?: return false
                val contentType = res.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("json") || contentType.contains("html")) {
                    Log.w(TAG, "download wrong content-type=$contentType from $url")
                    return false
                }
                val expected = info.apkSizeBytes
                val total = if (expected > 0) expected else body.contentLength()
                val sliceSpan = (sliceEnd - sliceStart).coerceAtLeast(0.05f)

                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val local = (done.toFloat() / total).coerceIn(0f, 1f)
                                val global = sliceStart + local * sliceSpan
                                setDownloadProgress(info, global.coerceIn(0.02f, 0.99f))
                            }
                        }
                    }
                }

                if (!ProtoApkIntegrity.matchesExpected(dest, info.apkSha256, expected)) {
                    Log.w(
                        TAG,
                        "download integrity failed from $url size=${dest.length()} expected=$expected sha=${info.apkSha256}",
                    )
                    dest.delete()
                    return false
                }
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "download error from $url", e)
            false
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }

    fun installPendingApk(): Boolean {
        val file = pendingApkFile() ?: return false
        val cached = readyUpdateInfo
        if (cached != null &&
            !ProtoApkIntegrity.matchesExpected(file, cached.apkSha256, cached.apkSizeBytes)
        ) {
            Log.e(TAG, "refusing install: APK failed integrity check")
            file.delete()
            _phase.value = AppUpdatePhase.Error("apk_corrupt")
            return false
        }
        if (!ProtoApkIntegrity.isValidApk(file)) {
            file.delete()
            _phase.value = AppUpdatePhase.Error("apk_corrupt")
            return false
        }
        if (!canInstallPackages()) {
            openInstallPermissionSettings()
            return false
        }
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        context.startActivity(intent)
        return true
    }

    suspend fun dismissPrompt(versionCode: Int) {
        prefs.setDismissedUpdateVersion(versionCode)
    }

    suspend fun shouldShowPrompt(info: AppUpdateInfo): Boolean {
        if (info.isMandatoryFor(installedVersionCode())) return false
        if (!info.isNewerThan(installedVersionCode())) return false
        if (info.force) return true
        return prefs.getDismissedUpdateVersion() < info.versionCode
    }

    companion object {
        private const val TAG = "ProtoAppUpdate"
        const val APK_FILE_NAME = "proto-update.apk"
        const val CANONICAL_APK_URL = "https://proto.su/api/download-apk.php"
    }
}
