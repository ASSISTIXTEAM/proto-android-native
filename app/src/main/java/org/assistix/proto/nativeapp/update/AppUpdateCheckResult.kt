package org.assistix.proto.nativeapp.update

sealed class AppUpdateCheckResult {
    data object UpToDate : AppUpdateCheckResult()

    data class Available(val info: AppUpdateInfo) : AppUpdateCheckResult()

    /** kind: network | server | http */
    data class Failed(val kind: String, val httpCode: Int? = null) : AppUpdateCheckResult()
}
