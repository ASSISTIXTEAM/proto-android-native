package org.assistix.proto.nativeapp.data

import org.json.JSONObject

data class AssistixRateLimit(
    val limit: Int,
    val used: Int = 0,
    val remaining: Int,
    val resetInSec: Int,
    val windowSec: Int = 3600,
    val unit: String = "requests",
) {
    fun isExhausted(): Boolean = limit > 0 && remaining <= 0

    val isTokenBudget: Boolean
        get() = unit == "tokens"
}

fun parseAssistixRateLimit(j: JSONObject?): AssistixRateLimit? {
    val o = j?.optJSONObject("rate_limit") ?: return null
    val limit = o.optInt("limit", 0)
    if (limit <= 0) return null
    val used = o.optInt("used", -1).let { if (it >= 0) it else (limit - o.optInt("remaining", 0)).coerceIn(0, limit) }
    return AssistixRateLimit(
        limit = limit,
        used = used,
        remaining = o.optInt("remaining", 0).coerceAtLeast(0),
        resetInSec = o.optInt("reset_in_sec", 0).coerceAtLeast(0),
        windowSec = o.optInt("window_sec", 0).coerceAtLeast(0),
        unit = o.optString("unit", "requests").ifBlank { "requests" },
    )
}
