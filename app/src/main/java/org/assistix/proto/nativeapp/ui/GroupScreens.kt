package org.assistix.proto.nativeapp.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.GroupDetail
import org.assistix.proto.nativeapp.data.GroupMember
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.UserHit
import org.assistix.proto.nativeapp.data.resolveDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    authToken: String?,
    onBack: () -> Unit,
    onCreated: (conversationId: Int, title: String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    val picked = remember { mutableStateListOf<UserHit>() }

    LaunchedEffect(search) {
        if (search.trim().length < 1) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        val t = session.token() ?: return@LaunchedEffect
        hits = withContext(Dispatchers.IO) { api.searchUsers(t, search.trim()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.newGroup) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                title,
                { title = it },
                label = { Text(UiStrings.groupTitle) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = ProtoShapes.field,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                search,
                { search = it },
                label = { Text(UiStrings.addMembers) },
                placeholder = { Text(UiStrings.searchNick) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = ProtoShapes.field,
            )
            if (picked.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(UiStrings.selectedMembers(picked.size), style = MaterialTheme.typography.labelMedium)
                picked.forEach { u ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProtoAvatar(u.avatarUploadId, resolveDisplayName(u.displayName, u.nick), 36.dp, api, authToken)
                        Spacer(Modifier.size(8.dp))
                        Text(resolveDisplayName(u.displayName, u.nick), Modifier.weight(1f))
                        TextButton(onClick = { picked.removeAll { it.id == u.id } }) { Text(UiStrings.remove) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(hits.filter { h -> picked.none { it.id == h.id } }, key = { it.id }) { u ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (picked.none { it.id == u.id }) picked.add(u) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProtoAvatar(u.avatarUploadId, resolveDisplayName(u.displayName, u.nick), 44.dp, api, authToken)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(resolveDisplayName(u.displayName, u.nick), fontWeight = FontWeight.Medium)
                            Text("@${u.nick}", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = ProtoOrange)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                }
            }
            ProtoPrimaryButton(
                if (busy) "…" else UiStrings.createGroup,
                {
                    if (busy) return@ProtoPrimaryButton
                    if (title.trim().isEmpty() || picked.isEmpty()) {
                        Toast.makeText(ctx, UiStrings.groupNeedTitleMembers, Toast.LENGTH_SHORT).show()
                        return@ProtoPrimaryButton
                    }
                    scope.launch {
                        val t = session.token() ?: authToken
                        if (t == null) return@launch
                        busy = true
                        val cid =
                            withContext(Dispatchers.IO) {
                                api.createGroup(t, title.trim(), picked.map { it.id })
                            }
                        busy = false
                        if (cid != null && cid > 0) {
                            onCreated(cid, title.trim())
                        } else {
                            Toast.makeText(ctx, UiStrings.groupCreateFailed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    authToken: String?,
    conversationId: Int,
    conversationKind: String = "group",
    initialTitle: String,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    onRenamed: (String) -> Unit,
    onOpenProfile: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<GroupDetail?>(null) }
    var title by remember { mutableStateOf(initialTitle) }
    var editTitle by remember { mutableStateOf(false) }
    var addSearch by remember { mutableStateOf("") }
    var addHits by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var myId by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { myId = session.userId() }

    fun reload() {
        scope.launch {
            val t = session.token() ?: return@launch
            loading = true
            detail = withContext(Dispatchers.IO) { api.groupDetail(t, conversationId) }
            detail?.title?.takeIf { it.isNotBlank() }?.let { title = it }
            loading = false
        }
    }

    LaunchedEffect(conversationId) { reload() }

    LaunchedEffect(addSearch, showAdd) {
        if (!showAdd || addSearch.trim().length < 1) {
            addHits = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        val t = session.token() ?: return@LaunchedEffect
        addHits = withContext(Dispatchers.IO) { api.searchUsers(t, addSearch.trim()) }
    }

    val d = detail
    val myRole = d?.members?.firstOrNull { it.user.id == myId }?.role.orEmpty()
    val canManage = myRole == "owner" || myRole == "admin"
    val isOwner = myRole == "owner"
    val isChannel = conversationKind == "channel"
    val linkKind = if (isChannel) "channel" else "group"
    val shareLinkLabel = if (isChannel) UiStrings.shareChannelLink else UiStrings.shareGroupLink

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.groupInfo) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        if (loading && d == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isChannel) Icons.Default.Campaign else Icons.Default.Group,
                    contentDescription = null,
                    tint = ProtoOrange,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(UiStrings.groupMembersCount(d?.members?.size ?: 0), style = MaterialTheme.typography.bodySmall)
                }
            if (canManage) {
                TextButton(onClick = { editTitle = true }) { Text(UiStrings.renameGroup) }
            }
        }
            if (canManage) {
                Spacer(Modifier.height(8.dp))
                ProtoGhostButton(
                    shareLinkLabel,
                    {
                        scope.launch {
                            val t = session.token() ?: return@launch
                            val link =
                                withContext(Dispatchers.IO) {
                                    api.createPublicLink(t, linkKind, conversationId)
                                }
                            if (link != null) {
                                val clip = android.content.ClipData.newPlainText("proto", link.url)
                                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(clip)
                                Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
                ProtoGhostButton(
                    UiStrings.share,
                    {
                        scope.launch {
                            val t = session.token() ?: return@launch
                            val link =
                                withContext(Dispatchers.IO) {
                                    api.createPublicLink(t, linkKind, conversationId)
                                }
                            if (link != null) {
                                sharePlainText(ctx, shareLinkLabel, link.url)
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
            if (canManage && !isChannel) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showAdd = true }) { Text(UiStrings.addMembers) }
            }
            if (!isChannel || canManage) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(d?.members.orEmpty(), key = { it.user.id }) { m ->
                    MemberRow(
                        member = m,
                        api = api,
                        token = authToken,
                        isOwner = isOwner,
                        myRole = myRole,
                        myId = myId,
                        onOpenProfile = onOpenProfile,
                        onSetRole = { role ->
                            scope.launch {
                                val t = session.token() ?: return@launch
                                val ok = withContext(Dispatchers.IO) { api.groupSetRole(t, conversationId, m.user.id, role) }
                                if (ok) reload() else Toast.makeText(ctx, UiStrings.permissionDenied, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRemove = {
                            scope.launch {
                                val t = session.token() ?: return@launch
                                val ok = withContext(Dispatchers.IO) { api.groupRemoveMember(t, conversationId, m.user.id) }
                                if (ok) reload() else Toast.makeText(ctx, UiStrings.permissionDenied, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.25f))
                }
            }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    UiStrings.channelMembersAdminOnly,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    scope.launch {
                        val t = session.token() ?: return@launch
                        val ok = withContext(Dispatchers.IO) { api.groupRemoveMember(t, conversationId, myId) }
                        if (ok) onLeft() else Toast.makeText(ctx, UiStrings.permissionDenied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(UiStrings.leaveGroup, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (editTitle) {
        var newTitle by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { editTitle = false },
            title = { Text(UiStrings.renameGroup) },
            text = {
                OutlinedTextField(newTitle, { newTitle = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val t = session.token() ?: return@launch
                            val ok = withContext(Dispatchers.IO) { api.groupRename(t, conversationId, newTitle) }
                            if (ok) {
                                title = newTitle.trim()
                                onRenamed(title)
                                editTitle = false
                                reload()
                            } else {
                                Toast.makeText(ctx, UiStrings.permissionDenied, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) { Text(UiStrings.save) }
            },
            dismissButton = { TextButton(onClick = { editTitle = false }) { Text(UiStrings.cancel) } },
        )
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(UiStrings.addMembers) },
            text = {
                Column {
                    OutlinedTextField(addSearch, { addSearch = it }, placeholder = { Text(UiStrings.searchNick) }, singleLine = true)
                    addHits.take(6).forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    val t = session.token() ?: return@launch
                                    val n = withContext(Dispatchers.IO) { api.groupAddMembers(t, conversationId, listOf(u.id)) }
                                    if (n > 0) {
                                        Toast.makeText(ctx, UiStrings.memberAdded, Toast.LENGTH_SHORT).show()
                                        reload()
                                        showAdd = false
                                    }
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(resolveDisplayName(u.displayName, u.nick), Modifier.weight(1f))
                            Icon(Icons.Default.Check, null, tint = ProtoOrange)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text(UiStrings.close) } },
        )
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    api: ProtoApi,
    token: String?,
    isOwner: Boolean,
    myRole: String,
    myId: Int,
    onOpenProfile: (Int) -> Unit,
    onSetRole: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val name = resolveDisplayName(member.user.displayName, member.user.nick)
    val roleLabel =
        when (member.role) {
            "owner" -> UiStrings.roleOwner
            "admin" -> UiStrings.roleAdmin
            else -> UiStrings.roleMember
        }
    val canKick =
        member.user.id != myId &&
            member.role != "owner" &&
            (isOwner || (myRole == "admin" && member.role == "member"))

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = member.user.id != myId) { onOpenProfile(member.user.id) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProtoAvatar(member.user.avatarUploadId, name, 48.dp, api, token)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text("@${member.user.nick} · $roleLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isOwner && member.role != "owner") {
            TextButton(onClick = { menu = true }) { Text(UiStrings.manageRole) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (member.role != "admin") {
                    DropdownMenuItem(text = { Text(UiStrings.makeAdmin) }, onClick = { menu = false; onSetRole("admin") })
                }
                if (member.role == "admin") {
                    DropdownMenuItem(text = { Text(UiStrings.removeAdmin) }, onClick = { menu = false; onSetRole("member") })
                }
            }
        }
        if (canKick) {
            TextButton(onClick = onRemove) {
                Text(UiStrings.remove, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
