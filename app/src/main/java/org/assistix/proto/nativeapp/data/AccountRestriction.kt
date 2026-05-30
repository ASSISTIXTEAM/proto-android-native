package org.assistix.proto.nativeapp.data

import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class AccountRestriction(
    val kind: String,
    val reason: String,
    val publicNote: String,
    val untilSec: Long,
    val noAppeal: Boolean = true,
) {
    val isActive: Boolean get() = kind == "banned" || (kind == "suspended" && untilSec > System.currentTimeMillis() / 1000)

    fun untilLabel(): String? {
        if (untilSec <= 0L) return null
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(untilSec * 1000))
    }

    companion object {
        fun fromJson(o: JSONObject?): AccountRestriction? {
            if (o == null || !o.optBoolean("active", false)) return null
            val kind = o.optString("kind", "suspended")
            return AccountRestriction(
                kind = kind,
                reason = o.optString("reason", ""),
                publicNote = o.optString("public_note", ""),
                untilSec = o.optLong("until", 0),
                noAppeal = o.optBoolean("no_appeal", true),
            )
        }
    }
}

fun JSONObject.optRestriction(key: String): AccountRestriction? =
    AccountRestriction.fromJson(optJSONObject(key))

/** Internal — do not surface founder status in UI. */
fun isFounderNick(nick: String): Boolean = nick.equals("ax10m", ignoreCase = true)
