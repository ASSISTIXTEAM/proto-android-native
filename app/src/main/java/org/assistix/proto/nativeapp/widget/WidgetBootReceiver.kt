package org.assistix.proto.nativeapp.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        WidgetRefreshScheduler.enqueueNow(context)
        WidgetRefreshScheduler.schedulePeriodic(context)
    }
}
