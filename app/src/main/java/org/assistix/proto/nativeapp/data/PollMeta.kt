package org.assistix.proto.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject

data class PollMeta(
    val question: String,
    val options: List<String>,
    val allowMultiple: Boolean,
    val anonymous: Boolean = false,
    val closesAt: Long = 0,
    val votes: Map<Int, List<Int>>,
) {
    fun isClosed(nowSec: Long = System.currentTimeMillis() / 1000): Boolean = closesAt > 0 && nowSec > closesAt
    fun totalVotes(): Int = votes.values.sumOf { it.size }

    fun countForOption(index: Int): Int = votes.values.count { index in it }

    fun mySelections(myUserId: Int): Set<Int> = votes[myUserId]?.toSet() ?: emptySet()

    fun toJson(): String =
        JSONObject()
            .put("type", "poll")
            .put("question", question)
            .put("options", JSONArray(options))
            .put("allow_multiple", allowMultiple)
            .put("votes", votesToJson())
            .toString()

    private fun votesToJson(): JSONObject {
        val o = JSONObject()
        votes.forEach { (uid, opts) -> o.put(uid.toString(), JSONArray(opts)) }
        return o
    }

    companion object {
        fun fromJson(raw: String): PollMeta? {
            return try {
                val trimmed = raw.trim()
                val j = JSONObject(trimmed)
                val pollObj =
                    when {
                        j.optString("type") == "poll" -> j
                        j.has("proto_poll") -> j.getJSONObject("proto_poll").let { wrap ->
                            JSONObject()
                                .put("type", "poll")
                                .put("question", wrap.optString("question", ""))
                                .put("options", wrap.optJSONArray("options") ?: JSONArray())
                                .put("allow_multiple", wrap.optBoolean("allow_multiple", false))
                                .put("anonymous", wrap.optBoolean("anonymous", false))
                                .put("closes_at", wrap.optLong("closes_at", 0))
                                .put("votes", wrap.optJSONObject("votes") ?: JSONObject())
                        }
                        else -> return null
                    }
                if (pollObj.optString("type") != "poll") return null
                val opts = pollObj.optJSONArray("options") ?: return null
                val options = (0 until opts.length()).mapNotNull { i -> opts.optString(i).takeIf { it.isNotBlank() } }
                if (options.size < 2) return null
                val votes = mutableMapOf<Int, List<Int>>()
                val vObj = pollObj.optJSONObject("votes")
                if (vObj != null) {
                    vObj.keys().forEach { key ->
                        val uid = key.toIntOrNull() ?: return@forEach
                        val arr = vObj.optJSONArray(key)
                        if (arr != null) {
                            val list = (0 until arr.length()).mapNotNull { i -> arr.optInt(i).takeIf { it >= 0 } }
                            if (list.isNotEmpty()) votes[uid] = list
                        } else {
                            val single = vObj.optInt(key, -1)
                            if (single >= 0) votes[uid] = listOf(single)
                        }
                    }
                }
                PollMeta(
                    question = pollObj.optString("question", ""),
                    options = options,
                    allowMultiple = pollObj.optBoolean("allow_multiple", false),
                    anonymous = pollObj.optBoolean("anonymous", false),
                    closesAt = pollObj.optLong("closes_at", 0),
                    votes = votes,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
