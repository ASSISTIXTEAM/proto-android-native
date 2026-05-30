package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.UserHit
import org.assistix.proto.nativeapp.data.resolveDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDmScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    authToken: String?,
    onBack: () -> Unit,
    onOpenChat: (Int, String, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    var busyId by remember { mutableStateOf(0) }

    LaunchedEffect(search) {
        if (search.trim().length < 1) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        val t = session.token() ?: authToken ?: return@LaunchedEffect
        hits = withContext(Dispatchers.IO) { api.searchUsers(t, search.trim()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.newDm) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(UiStrings.searchNick) },
                singleLine = true,
                shape = ProtoShapes.field,
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(hits, key = { it.id }) { u ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = busyId == 0) {
                                scope.launch {
                                    val t = session.token() ?: authToken ?: return@launch
                                    busyId = u.id
                                    val cid = withContext(Dispatchers.IO) { api.startDm(t, u.id) }
                                    busyId = 0
                                    if (cid != null && cid > 0) {
                                        onOpenChat(cid, resolveDisplayName(u.displayName, u.nick), u.id)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProtoAvatar(u.avatarUploadId, resolveDisplayName(u.displayName, u.nick), 48.dp, api, authToken)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            DisplayNameWithEmoji(resolveDisplayName(u.displayName, u.nick), u.statusEmoji)
                            Text(
                                "@${u.nick}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (busyId == u.id) {
                            Text("…", fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                }
            }
        }
    }
}
