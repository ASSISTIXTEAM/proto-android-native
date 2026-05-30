package org.assistix.proto.nativeapp.ui



import android.widget.Toast

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import org.assistix.proto.nativeapp.data.ProtoApi

import org.assistix.proto.nativeapp.data.ProtoSessionStore



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun CreateChannelScreen(

    session: ProtoSessionStore,

    api: ProtoApi,

    authToken: String?,

    onBack: () -> Unit,

    onCreated: (conversationId: Int, title: String) -> Unit,

) {

    val ctx = LocalContext.current

    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }

    var nick by remember { mutableStateOf("") }

    var description by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text(UiStrings.newChannel) },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)

                    }

                },

            )

        },

    ) { pad ->

        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {

            Text(

                UiStrings.newChannelHint,

                modifier = Modifier.padding(bottom = 12.dp),

                style = MaterialTheme.typography.bodyMedium,

                color = MaterialTheme.colorScheme.onSurfaceVariant,

            )

            OutlinedTextField(

                value = title,

                onValueChange = { title = it },

                label = { Text(UiStrings.channelTitle) },

                modifier = Modifier.fillMaxWidth(),

                singleLine = true,

                shape = ProtoShapes.field,

            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(

                value = nick,

                onValueChange = { nick = it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase() },

                label = { Text(UiStrings.channelNick) },

                placeholder = { Text("proto_news") },

                modifier = Modifier.fillMaxWidth(),

                singleLine = true,

                shape = ProtoShapes.field,

            )

            Text(

                UiStrings.channelNickHint,

                style = MaterialTheme.typography.labelSmall,

                color = MaterialTheme.colorScheme.onSurfaceVariant,

                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),

            )

            OutlinedTextField(

                value = description,

                onValueChange = { description = it },

                label = { Text(UiStrings.channelDescription) },

                modifier = Modifier.fillMaxWidth(),

                minLines = 2,

                maxLines = 4,

                shape = ProtoShapes.field,

            )

            Spacer(Modifier.height(20.dp))

            ProtoPrimaryButton(

                if (busy) "…" else UiStrings.createChannel,

                {

                    if (busy) return@ProtoPrimaryButton

                    val name = title.trim()

                    val n = nick.trim().lowercase()

                    if (name.isEmpty()) {

                        Toast.makeText(ctx, UiStrings.channelNeedTitle, Toast.LENGTH_SHORT).show()

                        return@ProtoPrimaryButton

                    }

                    if (n.length < 3) {

                        Toast.makeText(ctx, UiStrings.channelNeedNick, Toast.LENGTH_SHORT).show()

                        return@ProtoPrimaryButton

                    }

                    scope.launch {

                        val t = session.token() ?: authToken ?: return@launch

                        busy = true

                        val cid = withContext(Dispatchers.IO) { api.createChannel(t, name, n, description.trim()) }

                        busy = false

                        if (cid != null && cid > 0) {

                            onCreated(cid, name)

                        } else {

                            Toast.makeText(ctx, UiStrings.channelCreateFailed, Toast.LENGTH_SHORT).show()

                        }

                    }

                },

            )

        }

    }

}

