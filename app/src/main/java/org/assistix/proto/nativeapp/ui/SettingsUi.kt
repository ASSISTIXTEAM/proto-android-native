package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsVersionFooter(versionName: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            UiStrings.settingsVersion(versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SettingsGroupLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = ProtoOrange,
        letterSpacing = 0.8.sp,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 10.dp),
    )
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SettingsGroupLabel(title)
        SettingsGlassGroup(content = content)
    }
}

@Composable
fun SettingsGlassGroup(content: @Composable () -> Unit) {
    ProfileGlassCard(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.14f),
    )
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = ProtoHaptics.rememberSender()
    Row(
        modifier
            .fillMaxWidth()
            .clickable {
                haptic(HapticKind.Toggle)
                onCheckedChange(!checked)
            }
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic(HapticKind.Toggle)
                onCheckedChange(it)
            },
            modifier = Modifier.padding(start = 8.dp),
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ProtoOrange,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
    }
}

@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    danger: Boolean = false,
) {
    val haptic = ProtoHaptics.rememberSender()
    Row(
        modifier
            .fillMaxWidth()
            .clip(ProtoShapes.field)
            .clickable {
                haptic(HapticKind.Tap)
                onClick()
            }
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (iconRes != null || icon != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(ProtoShapes.button)
                    .background(
                        if (danger) MaterialTheme.colorScheme.error.copy(0.12f)
                        else ProtoOrange.copy(0.12f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    iconRes != null ->
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    icon != null ->
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (danger) MaterialTheme.colorScheme.error else ProtoOrange,
                            modifier = Modifier.size(26.dp),
                        )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
fun SettingsProtoAiCard() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(ProtoShapes.button)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(ProtoOrange, ProtoOrange.copy(0.6f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Psychology, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                UiStrings.settingsAssistixSection,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                UiStrings.settingsAssistixProtoHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsAnimatedGroup(
    index: Int,
    visible: Boolean,
    reduceMotion: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(ProtoMotion.fade(reduceMotion, 280)),
        exit = fadeOut(ProtoMotion.fade(reduceMotion, 120)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        ) {
            content()
        }
    }
}
