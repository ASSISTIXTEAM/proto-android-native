package org.assistix.proto.nativeapp.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/** Launcher icon badge without external ShortcutBadger dependency. */
object ProtoUnreadBadge {
    fun apply(context: Context, count: Int) {
        val c = count.coerceAtLeast(0)
        val ok =
            trySamsung(context, c) ||
                trySony(context, c) ||
                tryHuawei(context, c) ||
                tryXiaomi(context, c) ||
                tryOppo(context, c) ||
                tryVivo(context, c) ||
                tryHtc(context, c)
        if (!ok && c <= 0) {
            runCatching { trySamsung(context, 0) }
        }
    }

    private fun trySamsung(context: Context, count: Int): Boolean =
        send(
            context,
            Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", count)
                putExtra("badge_count_package_name", context.packageName)
                putExtra("badge_count_class_name", launcherClass(context))
            },
        )

    private fun trySony(context: Context, count: Int): Boolean =
        send(
            context,
            Intent("com.sonyericsson.home.action.UPDATE_BADGE").apply {
                putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", context.packageName)
                putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", launcherClass(context))
                putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", count.toString())
                putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", count > 0)
            },
        )

    private fun tryHuawei(context: Context, count: Int): Boolean =
        send(
            context,
            Intent("com.huawei.android.launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM").apply {
                putExtra("packageName", context.packageName)
                putExtra("className", launcherClass(context))
                putExtra("number", count)
            },
        )

    private fun tryXiaomi(context: Context, count: Int): Boolean =
        send(
            context,
            Intent("android.intent.action.APPLICATION_MESSAGE_UPDATE").apply {
                putExtra(
                    "android.intent.extra.update_application_component_name",
                    ComponentName(context.packageName, launcherClass(context)).flattenToString(),
                )
                putExtra("android.intent.extra.update_application_message_text", if (count > 0) count.toString() else "")
            },
        )

    private fun tryOppo(context: Context, count: Int): Boolean {
        val extras = Bundle().apply {
            putInt("app_badge_count", count)
            putString("app_badge_packageName", context.packageName)
        }
        return send(context, Intent("com.oppo.unsettledevent").putExtras(extras)) ||
            send(
                context,
                Intent("com.oppo.unsettledevent").apply {
                    putExtra("pakeageName", context.packageName)
                    putExtra("number", count)
                    putExtra("upgradeNumber", count)
                },
            )
    }

    private fun tryVivo(context: Context, count: Int): Boolean =
        send(
            context,
            Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM").apply {
                putExtra("packageName", context.packageName)
                putExtra("className", launcherClass(context))
                putExtra("notificationNum", count)
            },
        )

    private fun tryHtc(context: Context, count: Int): Boolean =
        send(
            context,
            Intent("com.htc.launcher.action.UPDATE_SHORTCUT").apply {
                putExtra("packagename", context.packageName)
                putExtra("count", count)
            },
        )

    private fun launcherClass(context: Context): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
        val mine = list.firstOrNull { it.activityInfo.packageName == context.packageName }
        return mine?.activityInfo?.name ?: "${context.packageName}.MainActivity"
    }

    private fun send(context: Context, intent: Intent): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.sendBroadcast(intent)
            } else {
                @Suppress("DEPRECATION")
                context.sendBroadcast(intent)
            }
            true
        }.getOrDefault(false)
}
