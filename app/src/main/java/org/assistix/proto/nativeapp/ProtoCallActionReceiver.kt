package org.assistix.proto.nativeapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Кнопки «Принять» / «Отклонить» в уведомлении о звонке. */
class ProtoCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? ProtoApplication ?: return
        val cid = intent.getIntExtra(EXTRA_CID, -1)
        Log.d(TAG, "action=$action cid=$cid")
        when (action) {
            ACTION_DECLINE -> app.calls.declineIncoming()
            ACTION_ANSWER -> {
                if (cid > 0) app.calls.prioritizeIncomingPoll(cid)
                val open =
                    Intent(context, IncomingCallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(IncomingCallActivity.EXTRA_CID, cid)
                    }
                context.startActivity(open)
                app.calls.acceptIncoming()
            }
        }
    }

    companion object {
        private const val TAG = "ProtoCallAction"
        const val ACTION_ANSWER = "org.assistix.proto.CALL_ANSWER"
        const val ACTION_DECLINE = "org.assistix.proto.CALL_DECLINE"
        const val EXTRA_CID = "conversation_id"
    }
}
