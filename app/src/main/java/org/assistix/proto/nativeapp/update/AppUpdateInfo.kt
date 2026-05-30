package org.assistix.proto.nativeapp.update

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkDirectUrl: String = "",
    val apkSha256: String,
    val apkSizeBytes: Long,
    val changelog: String,
    val force: Boolean,
    val blockApp: Boolean = false,
    val requiredMessage: String = "",
    val minVersionCode: Int = 1,
    val helpUrl: String = "https://proto.su/download.html#help-install",
    val supportEmail: String = "team@proto.su",
) {
    fun isNewerThan(installedVersionCode: Int): Boolean =
        versionCode > installedVersionCode && apkUrl.isNotBlank()

  /** Блок только если реально нужно обновление (иначе — вечный экран после установки APK). */
    fun isMandatoryFor(installedVersionCode: Int): Boolean {
        if (installedVersionCode >= versionCode && installedVersionCode >= minVersionCode) {
            return false
        }
        if (!blockApp) return false
        return isNewerThan(installedVersionCode) || installedVersionCode < minVersionCode
    }

    fun satisfiesInstalled(installedVersionCode: Int): Boolean =
        installedVersionCode >= versionCode && installedVersionCode >= minVersionCode
}

sealed class AppUpdatePhase {
    data object Idle : AppUpdatePhase()

    data object Checking : AppUpdatePhase()

    data class Available(val info: AppUpdateInfo) : AppUpdatePhase()

    data class Downloading(val info: AppUpdateInfo, val progress: Float) : AppUpdatePhase()

    data class Ready(val info: AppUpdateInfo, val apkFileName: String) : AppUpdatePhase()

    data object UpToDate : AppUpdatePhase()

    data class Error(val message: String) : AppUpdatePhase()
}
