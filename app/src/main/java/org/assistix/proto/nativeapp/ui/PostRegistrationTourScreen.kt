package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.ui.l10n.AppLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostRegistrationTourScreen(
    onFinished: () -> Unit,
) {
    val lang by AppLocale.currentCode()
    val slides =
        remember(lang) {
            listOf(
                TourSlide(Icons.Default.Chat, UiStrings.postRegTourChatsTitle, UiStrings.postRegTourChatsBody),
                TourSlide(Icons.Default.Add, UiStrings.postRegTourFabTitle, UiStrings.postRegTourFabBody, accent = true),
                TourSlide(Icons.Default.QrCodeScanner, UiStrings.postRegTourQrTitle, UiStrings.postRegTourQrBody, accent = true),
                TourSlide(Icons.Default.Settings, UiStrings.postRegTourSettingsTitle, UiStrings.postRegTourSettingsBody),
            )
        }
    var page by remember { mutableIntStateOf(0) }
    val slide = slides[page]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.postRegTourWelcome) },
                actions = {
                    TextButton(onClick = onFinished) { Text(UiStrings.postRegTourSkip) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slides.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == page) ProtoOrange
                                else MaterialTheme.colorScheme.onSurface.copy(0.2f),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (slide.accent) ProtoOrange.copy(0.35f) else MaterialTheme.colorScheme.primary.copy(0.2f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    slide.icon,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = if (slide.accent) ProtoOrange else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(slide.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                slide.body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (page < slides.lastIndex) {
                        page++
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (page < slides.lastIndex) UiStrings.postRegTourNext else UiStrings.postRegTourDone)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class TourSlide(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val accent: Boolean = false,
)
