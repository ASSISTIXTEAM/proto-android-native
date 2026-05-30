package org.assistix.proto.nativeapp.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ChannelHit
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoSessionStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelManageScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    authToken: String?,
    conversationId: Int,
    initialTitle: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenMembers: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf<ChannelHit?>(null) }
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf("") }
    var pendingAvatarId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversationId, authToken) {
        val t = authToken ?: return@LaunchedEffect
        loading = true
        val ch = withContext(Dispatchers.IO) { api.channelByConversation(t, conversationId) }
        channel = ch
        ch?.title?.takeIf { it.isNotBlank() }?.let { title = it }
        description = ch?.description.orEmpty()
        loading = false
    }

    val pickAvatar =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val t = authToken ?: return@rememberLauncherForActivityResult
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val tmp = File.createTempFile("ch_av_", ".jpg", ctx.cacheDir)
                    ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    val id = withContext(Dispatchers.IO) { api.uploadFile(t, tmp, "image/jpeg") }
                    tmp.delete()
                    if (id != null) {
                        pendingAvatarId = id
                        Toast.makeText(ctx, UiStrings.channelAvatarUpdated, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message ?: UiStrings.genericError, Toast.LENGTH_SHORT).show()
                }
            }
        }

    fun save() {
        if (busy) return
        val t = authToken ?: return
        if (title.trim().isEmpty()) {
            Toast.makeText(ctx, UiStrings.channelNeedTitle, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            busy = true
            val updated =
                withContext(Dispatchers.IO) {
                    api.updateChannel(t, conversationId, title, description, pendingAvatarId)
                }
            busy = false
            if (updated != null) {
                channel = updated
                pendingAvatarId = null
                val newTitle = updated.title.ifBlank { title }
                title = newTitle
                description = updated.description
                Toast.makeText(ctx, UiStrings.channelSaved, Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val ch = channel
    val avatarId = pendingAvatarId ?: ch?.avatarUploadId
    val token = authToken

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.channelEditTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        if (loading && ch == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ProtoOrange)
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.clickable(enabled = !busy) { pickAvatar.launch("image/*") },
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        ProtoAvatar(
                            uploadId = avatarId,
                            displayName = title,
                            size = 96.dp,
                            api = api,
                            token = token,
                        )
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = UiStrings.channelEditAvatar,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        UiStrings.channelEditAvatar,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(enabled = !busy) { pickAvatar.launch("image/*") },
                    )
                    if (!ch?.nick.isNullOrBlank()) {
                        Text(
                            "@${ch?.nick}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(UiStrings.channelTitle) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                shape = ProtoShapes.field,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(UiStrings.channelDescription) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = !busy,
                shape = ProtoShapes.field,
            )
            Spacer(Modifier.height(20.dp))
            if (!busy) {
                ProtoPrimaryButton(
                    UiStrings.channelEditSave,
                    onClick = { save() },
                    Modifier.fillMaxWidth(),
                )
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProtoOrange)
                }
            }
            Spacer(Modifier.height(12.dp))
            ProtoGhostButton(UiStrings.channelProfile, onOpenMembers, Modifier.fillMaxWidth())
            if (ch?.verified == true) {
                Spacer(Modifier.height(8.dp))
                Text(
                    UiStrings.channelVerifiedTooltip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
