package org.assistix.proto.nativeapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.assistix.proto.nativeapp.data.CallUiState
import org.assistix.proto.nativeapp.data.ProtoThemeMode
import org.assistix.proto.nativeapp.ui.DisplayNameWithEmoji
import org.assistix.proto.nativeapp.ui.ProtoAvatar
import org.assistix.proto.nativeapp.ui.ProtoTheme
import org.assistix.proto.nativeapp.ui.UiStrings

/** Полноэкранный входящий звонок поверх блокировки. */
class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLockScreenFlags()
        val app = application as ProtoApplication
        val cid = intent.getIntExtra(EXTRA_CID, -1)
        if (cid > 0) {
            runCatching { app.calls.prioritizeIncomingPoll(cid) }
        }
        setContent {
            val callState by app.calls.state.collectAsState()
            val token by app.session.tokenFlow.collectAsState(initial = null)
            ProtoTheme(mode = ProtoThemeMode.DARK) {
                IncomingCallScreen(
                    state = callState,
                    token = token,
                    api = app.api,
                    onAccept = { runCatching { app.calls.acceptIncoming() } },
                    onDecline = { runCatching { app.calls.declineIncoming() } },
                )
                LaunchedEffect(callState.active, callState.incoming) {
                    if (!callState.active) {
                        finish()
                        return@LaunchedEffect
                    }
                    if (callState.active && !callState.incoming) {
                        startActivity(
                            Intent(this@IncomingCallActivity, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            },
                        )
                        finish()
                    }
                }
            }
        }
    }

    private fun enableLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
    }

    companion object {
        const val EXTRA_CID = "conversation_id"
    }
}

@Composable
private fun IncomingCallScreen(
    state: CallUiState,
    token: String?,
    api: org.assistix.proto.nativeapp.data.ProtoApi,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    if (!state.active || !state.incoming) return
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF3D2914), Color(0xFF121212), Color(0xFF0A0A0A))),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                UiStrings.incomingCall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.withVideo) UiStrings.videoCall else UiStrings.audioCall,
                color = Color(0xFFAEAEB2),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))
            ProtoAvatar(
                uploadId = state.peerAvatarUploadId,
                displayName = state.peerLabel,
                size = 140.dp,
                api = api,
                token = token,
            )
            Spacer(Modifier.height(20.dp))
            DisplayNameWithEmoji(
                displayName = state.peerLabel,
                statusEmoji = state.peerStatusEmoji,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDecline,
                        modifier =
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3A3A3C)),
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = UiStrings.decline, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Text(UiStrings.decline, color = Color(0xFFAEAEB2), fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier =
                            Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            if (state.withVideo) Icons.Default.Videocam else Icons.Default.Phone,
                            contentDescription = UiStrings.accept,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Text(UiStrings.accept, color = Color(0xFFAEAEB2), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
