package org.assistix.proto.nativeapp.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Durable JSON cache under Documents/PROTO/offline — survives process death and works offline.
 */
class ProtoOfflineVault(private val context: Context) {
    private fun root(): File = File(ProtoPersistentStorage.rootDir(context), "offline").apply { mkdirs() }

    private fun meFile(): File = File(root(), "me_profile.json")

    private fun userFile(userId: Int): File = File(File(root(), "users"), "user_$userId.json").apply { parentFile?.mkdirs() }

    fun readMe(): ProtoApi.MeLoad? {
        val f = meFile()
        if (!f.isFile) return null
        return runCatching {
            val j = JSONObject(f.readText())
            val u = j.optJSONObject("user") ?: return null
            ProtoApi.MeLoad(
                profile = parseMeProfile(u),
                restriction = AccountRestriction.fromJson(j.optJSONObject("restriction")),
            )
        }.getOrNull()
    }

    fun writeMe(load: ProtoApi.MeLoad) {
        val j =
            JSONObject()
                .put("saved_at", System.currentTimeMillis())
                .put("user", meToJson(load.profile))
        load.restriction?.let { j.put("restriction", restrictionToJson(it)) }
        writeAtomic(meFile(), j.toString())
    }

    fun readUser(userId: Int): UserProfile? {
        if (userId <= 0) return null
        val f = userFile(userId)
        if (!f.isFile) return null
        return runCatching {
            val j = JSONObject(f.readText())
            parseUserProfile(j.optJSONObject("user") ?: return null)
        }.getOrNull()
    }

    fun writeUser(profile: UserProfile) {
        if (profile.id <= 0) return
        val j =
            JSONObject()
                .put("saved_at", System.currentTimeMillis())
                .put("user", userToJson(profile))
        writeAtomic(userFile(profile.id), j.toString())
    }

    private fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    private fun meToJson(p: MeProfile): JSONObject =
        JSONObject()
            .put("id", p.id)
            .put("nick", p.nick)
            .put("email", p.email)
            .put("display_name", p.displayName)
            .put("bio", p.bio)
            .put("status_text", p.statusText)
            .put("status_emoji", p.statusEmoji)
            .put("avatar_upload_id", p.avatarUploadId.orEmpty())

    private fun userToJson(u: UserProfile): JSONObject =
        JSONObject()
            .put("id", u.id)
            .put("nick", u.nick)
            .put("display_name", u.displayName)
            .put("bio", u.bio)
            .put("status_text", u.statusText)
            .put("status_emoji", u.statusEmoji)
            .put("avatar_upload_id", u.avatarUploadId.orEmpty())
            .put("last_seen", u.lastSeenSec)
            .put("can_report", u.canReport)
            .put("can_block", u.canBlock)
            .put("can_message", u.canMessage)

    private fun parseMeProfile(u: JSONObject): MeProfile =
        MeProfile(
            id = u.optInt("id"),
            nick = u.optString("nick", ""),
            email = u.optString("email", ""),
            displayName = u.optString("display_name", ""),
            bio = u.optString("bio", ""),
            statusText = u.optString("status_text", ""),
            statusEmoji = u.optString("status_emoji", ""),
            avatarUploadId = u.optString("avatar_upload_id", "").ifBlank { null },
        )

    private fun parseUserProfile(u: JSONObject): UserProfile =
        UserProfile(
            id = u.optInt("id"),
            nick = u.optString("nick", "").ifBlank { "user${u.optInt("id")}" },
            displayName = u.optString("display_name", ""),
            bio = u.optString("bio", ""),
            statusText = u.optString("status_text", ""),
            statusEmoji = u.optString("status_emoji", ""),
            avatarUploadId = u.optString("avatar_upload_id", "").ifBlank { null },
            lastSeenSec = u.optLong("last_seen", 0).coerceAtLeast(0),
            moderationPublic = AccountRestriction.fromJson(u.optJSONObject("moderation_public")),
            canReport = u.optBoolean("can_report", true),
            canBlock = u.optBoolean("can_block", true),
            canMessage = u.optBoolean("can_message", true),
        )

    private fun restrictionToJson(r: AccountRestriction): JSONObject =
        JSONObject()
            .put("active", r.isActive)
            .put("kind", r.kind)
            .put("reason", r.reason)
            .put("public_note", r.publicNote)
            .put("until", r.untilSec)
            .put("no_appeal", r.noAppeal)
}
