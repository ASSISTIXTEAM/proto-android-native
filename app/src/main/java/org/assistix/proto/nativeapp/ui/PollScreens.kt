package org.assistix.proto.nativeapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoSessionStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePollScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    conversationId: Int,
    groupTitle: String,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var optionsText by remember { mutableStateOf("") }
    var allowMultiple by remember { mutableStateOf(false) }
    var anonymous by remember { mutableStateOf(false) }
    var deadline24h by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.createPoll) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(groupTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ProtoOrange)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                question,
                { question = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(UiStrings.pollQuestion) },
                singleLine = false,
                minLines = 2,
                shape = ProtoShapes.field,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                optionsText,
                { optionsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(UiStrings.pollOptions) },
                minLines = 4,
                shape = ProtoShapes.field,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(UiStrings.pollAllowMultiple, Modifier.weight(1f))
                Switch(checked = allowMultiple, onCheckedChange = { allowMultiple = it })
            }
            Spacer(Modifier.height(24.dp))
            if (busy) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else {
                ProtoPrimaryButton(
                    UiStrings.pollSend,
                    {
                        val opts = optionsText.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        if (question.trim().length < 2 || opts.size < 2) {
                            Toast.makeText(ctx, UiStrings.groupNeedTitleMembers, Toast.LENGTH_SHORT).show()
                            return@ProtoPrimaryButton
                        }
                        scope.launch {
                            val t = session.token() ?: return@launch
                            busy = true
                            val id =
                                withContext(Dispatchers.IO) {
                                    val closes =
                                        if (deadline24h) {
                                            System.currentTimeMillis() / 1000 + 86400
                                        } else {
                                            0L
                                        }
                                    api.createPoll(t, conversationId, question.trim(), opts, allowMultiple, anonymous, closes)
                                }
                            busy = false
                            if (id != null) {
                                onCreated()
                            } else {
                                Toast.makeText(ctx, UiStrings.saveFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
