package org.assistix.proto.nativeapp.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import org.assistix.proto.nativeapp.ProtoApplication

object ProtoHaptics {
    @Composable
    fun rememberSender(): (HapticKind) -> Unit {
        val view = LocalView.current
        val ctx = LocalContext.current
        val app = ctx.applicationContext as ProtoApplication
        val enabled by app.prefs.hapticFeedback.collectAsState(initial = true)
        return remember(view, enabled) {
            { kind ->
                if (enabled) perform(view, kind)
            }
        }
    }

    fun perform(view: View, kind: HapticKind) {
        val constant =
            when (kind) {
                HapticKind.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
                HapticKind.Toggle -> HapticFeedbackConstants.CONTEXT_CLICK
                HapticKind.Action, HapticKind.Send -> HapticFeedbackConstants.CONFIRM
                HapticKind.SwipeReveal ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.GESTURE_START
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                HapticKind.Reaction -> HapticFeedbackConstants.KEYBOARD_TAP
                HapticKind.Error -> HapticFeedbackConstants.REJECT
            }
        view.performHapticFeedback(constant)
    }
}

enum class HapticKind {
    /** Light tap — buttons, chips */
    Tap,
    /** Tab / switch / selection */
    Toggle,
    /** Confirmed action — send, pin, mute, reply swipe */
    Action,
    /** Swipe crossed threshold and action is visible */
    SwipeReveal,
    /** Reactions, small UI ticks */
    Reaction,
    /** Errors, limits */
    Error,
    /** @deprecated use [Action] */
    Send,
}
