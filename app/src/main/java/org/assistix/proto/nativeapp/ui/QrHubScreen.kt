package org.assistix.proto.nativeapp.ui

import android.widget.Toast
import org.assistix.proto.nativeapp.data.ProtoHosts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoSessionStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrHubScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var profileUrl by remember { mutableStateOf<String?>(null) }
    var showMyCode by remember { mutableStateOf(false) }
    val qrBitmap = remember(profileUrl) { profileUrl?.let { ProtoQrEncoder.encode(it, 420) } }
    val expandInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        loading = true
        val t = session.token()
        if (t != null) {
            val link = withContext(Dispatchers.IO) { api.createPublicLink(t, "profile") }
            profileUrl = link?.url ?: ProtoHosts.profileUrl("me")
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        ProtoBrandBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(UiStrings.qrHubTitle, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                )
            },
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileGlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = ProtoOrange, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            UiStrings.scanQrMinimalHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        ProtoPrimaryButton(UiStrings.qrHubScan, onOpenScanner, Modifier.fillMaxWidth())
                    }
                }
                ProfileGlassCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = expandInteraction,
                            onClick = { showMyCode = !showMyCode },
                        ),
                ) {
                    RowBetweenLabel(UiStrings.qrHubMyCode, if (showMyCode) "−" else "+")
                    if (showMyCode) {
                        Spacer(Modifier.height(14.dp))
                        if (loading) {
                            CircularProgressIndicator(color = ProtoOrange, modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            qrBitmap?.let { bmp ->
                                Image(
                                    bmp.asImageBitmap(),
                                    contentDescription = UiStrings.qrHubMyCode,
                                    modifier =
                                        Modifier
                                            .size(200.dp)
                                            .align(Alignment.CenterHorizontally),
                                )
                            }
                            profileUrl?.let { url ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    url,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ProtoGhostButton(
                                        UiStrings.copyLink,
                                        {
                                            val clip = android.content.ClipData.newPlainText("proto", url)
                                            (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                                .setPrimaryClip(clip)
                                            Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                                        },
                                        Modifier.weight(1f),
                                    )
                                    ProtoGhostButton(
                                        UiStrings.share,
                                        { sharePlainText(ctx, UiStrings.qrHubTitle, url) },
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowBetweenLabel(title: String, action: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(action, color = ProtoOrange, fontWeight = FontWeight.Bold)
    }
}
